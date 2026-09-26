package com.khanabook.saas.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * FIX #13 — API Versioning Policy
 *
 * Reads the optional {@code X-App-Version} header sent by the Android client.
 * Does NOT reject requests from unversioned clients (backward compatible),
 * but logs the version so the server team can:
 *   1. Track which app versions are still active in production.
 *   2. Alert when a version below MIN_SUPPORTED_VERSION is detected.
 *   3. Return deprecation warnings in the response header.
 *
 * Enforcement strategy (graduated):
 *   Phase 1 (now)    — Log only. No client is rejected.
 *   Phase 2 (future) — Return X-Deprecation-Warning header for old versions.
 *   Phase 3 (future) — Return 410 Gone for versions below hard minimum.
 *
 * The Android client should send:
 *   X-App-Version: <versionCode>     (e.g.  42)
 *   X-App-Platform: android
 */
@Component
public class AppVersionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AppVersionFilter.class);

    /** Versions below this value will receive a deprecation warning header. */
    private static final int SOFT_MIN_VERSION = 30;

    /**
     * Hard cutoff (Phase 3): versioned clients below this value receive 410 Gone
     * and are NOT admitted to any /api path. 0 disables the cutoff. Set via
     * APP_MIN_SUPPORTED_VERSION env var in production once the fixed build
     * (which no longer gates signup on /auth/check-user) is live on the
     * Play Store — see issues.txt fix plan item 1.
     */
    @Value("${app.version.min-supported:0}")
    private int minSupportedVersion;

    /** Header the Android app sends (BuildConfig.VERSION_CODE). */
    public static final String HEADER_APP_VERSION  = "X-App-Version";
    public static final String HEADER_APP_PLATFORM = "X-App-Platform";

    /** Response header added for deprecated clients. */
    public static final String HEADER_DEPRECATION  = "X-Deprecation-Warning";

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
                                    @org.springframework.lang.NonNull HttpServletResponse response,
                                    @org.springframework.lang.NonNull FilterChain filterChain) throws ServletException, IOException {

        String versionStr  = request.getHeader(HEADER_APP_VERSION);
        String platform    = request.getHeader(HEADER_APP_PLATFORM);

        if (versionStr != null) {
            try {
                int versionCode = Integer.parseInt(versionStr.trim());
                String path = request.getRequestURI();

                if (minSupportedVersion > 0 && versionCode < minSupportedVersion) {
                    // Hard cutoff: the build is known-broken (e.g. v21 falsely
                    // reports "number already exists" at signup). Reject with a
                    // machine-readable error the app can surface as "please update".
                    log.warn("Blocked outdated client: platform={} version={} path={}",
                            platform, versionCode, path);
                    response.setStatus(HttpStatus.GONE.value());
                    response.setContentType("application/json");
                    response.setHeader(HEADER_DEPRECATION,
                            "Your app version (" + versionCode + ") is no longer supported.");
                    response.getWriter().write(
                            "{\"error\":\"UPGRADE_REQUIRED\",\"message\":\"This app version is no longer supported. Please update KhanaBook from the Play Store.\"}");
                    return; // do NOT continue the chain
                }

                if (versionCode < SOFT_MIN_VERSION) {
                    log.warn("Deprecated client detected: platform={} version={} path={}",
                            platform, versionCode, path);
                    response.setHeader(HEADER_DEPRECATION,
                            "Your app version (" + versionCode + ") is outdated. " +
                            "Please update KhanaBook from the Play Store to continue receiving support.");
                } else {
                    log.debug("Client version OK: platform={} version={}", platform, versionCode);
                }
            } catch (NumberFormatException e) {
                log.warn("Unparseable X-App-Version header: '{}' from path={}", versionStr, request.getRequestURI());
            }
        }
        // Never block — always continue the filter chain.
        filterChain.doFilter(request, response);
    }

    /** Skip non-API paths (actuator, static resources, etc.) */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}
