package com.khanabook.saas.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp() {
        String credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH");
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_PATH not set. Push notifications will be DISABLED.");
            // Return a dummy FirebaseApp for DI compatibility (no actual push will happen)
            try {
                return FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                        .setProjectId("khanabook-lite")
                        .build(),
                    "khanabook-dummy"
                );
            } catch (Exception e) {
                log.error("Failed to create dummy FirebaseApp: {}", e.getMessage());
                return null;
            }
        }

        try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setProjectId("khanabook-lite")
                .build();

            // Return existing app if already initialized, otherwise create new
            try {
                return FirebaseApp.getInstance("khanabook");
            } catch (IllegalStateException e) {
                return FirebaseApp.initializeApp(options, "khanabook");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
            return null;
        }
    }
}
