package com.khanabook.saas.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "FIREBASE_CREDENTIALS_PATH")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp() {
        String credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH");
        // Read project ID from env or fall back to the correct project name
        String projectId = System.getenv("FIREBASE_PROJECT_ID");
        if (projectId == null || projectId.isBlank()) {
            projectId = "new-khanabook-li";
        }

        try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setProjectId(projectId)
                .build();

            try {
                return FirebaseApp.getInstance("khanabook");
            } catch (IllegalStateException e) {
                return FirebaseApp.initializeApp(options, "khanabook");
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
            throw new RuntimeException("Firebase initialization failed", e);
        }
    }
}
