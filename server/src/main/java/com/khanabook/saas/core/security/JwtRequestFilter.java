package com.khanabook.saas.core.security;

import com.khanabook.saas.core.utility.JwtUtility;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

	@Value("${admin.allowed-ips:}")
	private String adminAllowedIpsRaw;

	private volatile List<String> cachedAdminAllowedIps = null;

	private List<String> getAdminAllowedIps() {
		if (cachedAdminAllowedIps == null && adminAllowedIpsRaw != null && !adminAllowedIpsRaw.isBlank()) {
			cachedAdminAllowedIps = Arrays.stream(adminAllowedIpsRaw.split(","))
					.map(String::trim)
					.filter(s -> !s.isBlank())
					.toList();
		}
		return cachedAdminAllowedIps;
	}

	private final JwtUtility jwtUtility;
	private final UserRepository userRepository;
	private final RestaurantProfileRepository restaurantProfileRepository;
	private final TokenBlocklistRepository tokenBlocklistRepository;
	private final TokenRevocationCache tokenRevocationCache;

	private String getClientIp(HttpServletRequest request) {
		// Use getRemoteAddr() which reflects the actual connection IP after
		// Spring's ForwardedHeaderFilter has processed trusted proxy headers.
		// Do NOT read X-Forwarded-For directly — it is trivially spoofable.
		return request.getRemoteAddr();
	}

	@Override
	protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
			@org.springframework.lang.NonNull HttpServletResponse response,
			@org.springframework.lang.NonNull FilterChain chain) throws ServletException, IOException {
		try {

		final String authorizationHeader = request.getHeader("Authorization");

		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			String jwt = authorizationHeader.substring(7);

			try {
				if (!jwtUtility.isTokenExpired(jwt)) {
					// Check token revocation — shared cache first (zero-latency for tokens
					// revoked since this JVM started), then DB fallback for older revocations.
					String jti = jwtUtility.extractJti(jwt);
					if (jti != null) {
						boolean revoked = tokenRevocationCache.isRevoked(jti);
						if (!revoked && tokenBlocklistRepository.existsByJti(jti)) {
							long expiresAt = jwtUtility.extractExpiration(jwt) != null
									? jwtUtility.extractExpiration(jwt).getTime()
									: System.currentTimeMillis() + 3600_000L;
							tokenRevocationCache.populateFromDb(jti, expiresAt);
							revoked = true;
						}
						if (revoked) {
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
							return;
						}
					}

					Long restaurantId = jwtUtility.extractRestaurantId(jwt);
					String username = jwtUtility.extractUsername(jwt);

					if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

						User user = userRepository.findByAnyIdentifier(username)
								.orElse(null);

						if (user != null && Boolean.TRUE.equals(user.getIsActive())) {
							// Reject tokens issued before a password reset
							if (user.getTokenInvalidatedAt() != null) {
								java.util.Date issuedAt = jwtUtility.extractIssuedAt(jwt);
								if (issuedAt != null && issuedAt.getTime() < user.getTokenInvalidatedAt()) {
									response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalidated by password reset");
									return;
								}
							}
							if (restaurantId != null) {
								TenantContext.setCurrentTenant(restaurantId);
							}

							TenantContext.setCurrentUserId(user.getId());

							String role = user.getRole().name();
							TenantContext.setCurrentRole(role);

							if (!"KBOOK_ADMIN".equals(role) && restaurantId != null
									&& restaurantProfileRepository.findByRestaurantId(restaurantId)
											.map(profile -> Boolean.TRUE.equals(profile.getIsSuspended()))
											.orElse(false)) {
								response.setStatus(HttpServletResponse.SC_FORBIDDEN);
								response.setContentType("application/json");
								response.getWriter().write(
										"{\"error\":\"BUSINESS_SUSPENDED\",\"message\":\"Business is suspended\"}");
								return;
							}

							// Device binding: a token is bound to the device it was issued for.
							// Both sides must be present to compare — the Android client always
							// sends X-Device-Id (AuthInterceptor), while browser clients such as
							// web admin send none, so the console is unaffected. Enforcing this
							// is what makes a copied token useless off its own device; without it
							// a lifted token works anywhere until it expires.
							String jwtDeviceId = jwtUtility.extractDeviceId(jwt);
							String headerDeviceId = request.getHeader("X-Device-Id");
							if (jwtDeviceId != null && !jwtDeviceId.isBlank()
									&& headerDeviceId != null && !headerDeviceId.isBlank()
									&& !jwtDeviceId.equals(headerDeviceId)) {
								logger.warn("Device binding mismatch — jwtDevice={} headerDevice={} user={} — rejecting",
										jwtDeviceId, headerDeviceId, username);
								response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
										"Token is bound to a different device");
								return;
							}

							// Admin IP allowlist — block admin from unauthorized IPs
							if ("KBOOK_ADMIN".equals(role) && getAdminAllowedIps() != null && !getAdminAllowedIps().isEmpty()) {
								String clientIp = getClientIp(request);
								if (getAdminAllowedIps().stream().noneMatch(ip -> ip.equals(clientIp))) {
									logger.warn("Admin blocked from IP={} user={}", clientIp, username);
									response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access not allowed from this IP");
									return;
								}
							}

							org.springframework.security.core.authority.SimpleGrantedAuthority authority = new org.springframework.security.core.authority.SimpleGrantedAuthority(
									"ROLE_" + role);

							UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
									user, null, java.util.Collections.singletonList(authority));
							authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
							SecurityContextHolder.getContext().setAuthentication(authToken);
						}
					}
				}
			} catch (Exception e) {
				logger.debug("JWT validation failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
			}

		}


			chain.doFilter(request, response);
		} finally {
			TenantContext.clear();
			// SecurityContext lifecycle is managed by Spring's SecurityContextHolderFilter.
			// Clearing it here breaks @Async authentication and Spring's built-in mechanisms.
		}
}

}
