package com.khanabook.saas.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Firebase Admin SDK initialisation.
 *
 * Supports two credential modes (checked in order):
 *
 * Mode A — Service account JSON file (preferred, blocked by some org policies):
 *   FIREBASE_CREDENTIALS_PATH = /path/to/service-account.json
 *
 * Mode B — OAuth2 refresh token (fallback when key creation is restricted by org policy):
 *   GOOGLE_OAUTH_CLIENT_ID     = <OAuth2 client id from GCP Console>
 *   GOOGLE_OAUTH_CLIENT_SECRET = <OAuth2 client secret>
 *   FIREBASE_REFRESH_TOKEN     = <refresh token from OAuth2 Playground>
 *
 * Also used by both modes:
 *   FIREBASE_PROJECT_ID        = new-khanabook-li  (optional, has default)
 *
 * If neither mode is configured the FirebaseApp bean is not created and
 * pushToRestaurant() will silently no-op (safe degradation).
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    @Bean
    public FirebaseApp firebaseApp() {
        String projectId = env("FIREBASE_PROJECT_ID", "new-khanabook-li");

        // ── Mode A: service account JSON file ──────────────────────────────
        String credPath = env("FIREBASE_CREDENTIALS_PATH", null);
        if (credPath != null && !credPath.isBlank()) {
            return initWithJsonFile(credPath, projectId);
        }

        // ── Mode B: OAuth2 refresh token ───────────────────────────────────
        String clientId     = env("GOOGLE_OAUTH_CLIENT_ID", null);
        String clientSecret = env("GOOGLE_OAUTH_CLIENT_SECRET", null);
        String refreshToken = env("FIREBASE_REFRESH_TOKEN", null);

        if (clientId != null && clientSecret != null && refreshToken != null) {
            return initWithRefreshToken(clientId, clientSecret, refreshToken, projectId);
        }

        // ── Neither configured ─────────────────────────────────────────────
        log.warn("Firebase not configured. Set FIREBASE_CREDENTIALS_PATH or " +
                 "(GOOGLE_OAUTH_CLIENT_ID + GOOGLE_OAUTH_CLIENT_SECRET + FIREBASE_REFRESH_TOKEN). " +
                 "Push notifications are DISABLED.");
        return null;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FirebaseApp initWithJsonFile(String path, String projectId) {
        log.info("Initialising Firebase with service account JSON: {}", path);
        try (FileInputStream fis = new FileInputStream(path)) {
            GoogleCredentials creds = GoogleCredentials
                    .fromStream(fis)
                    .createScoped(List.of(FCM_SCOPE));
            return buildApp(creds, projectId);
        } catch (IOException e) {
            log.error("Firebase init failed (JSON file): {}", e.getMessage());
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    private FirebaseApp initWithRefreshToken(String clientId, String clientSecret,
                                              String refreshToken, String projectId) {
        log.info("Initialising Firebase with OAuth2 refresh token (project={})", projectId);
        try {
            UserCredentials creds = UserCredentials.newBuilder()
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setRefreshToken(refreshToken)
                    .build();
            GoogleCredentials scoped = creds.createScoped(List.of(FCM_SCOPE));
            return buildApp(scoped, projectId);
        } catch (Exception e) {
            log.error("Firebase init failed (refresh token): {}", e.getMessage());
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }

    private FirebaseApp buildApp(GoogleCredentials creds, String projectId) {
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(creds)
                .setProjectId(projectId)
                .build();
        try {
            FirebaseApp existing = FirebaseApp.getInstance("khanabook");
            log.info("Firebase app 'khanabook' already initialised — reusing.");
            return existing;
        } catch (IllegalStateException e) {
            FirebaseApp app = FirebaseApp.initializeApp(options, "khanabook");
            log.info("Firebase app 'khanabook' initialised successfully (project={}).", projectId);
            return app;
        }
    }

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
