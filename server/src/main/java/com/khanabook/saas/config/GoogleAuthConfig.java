package com.khanabook.saas.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class GoogleAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthConfig.class);

    @Value("${google.client.id:}")
    private String googleClientId;

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier() {
        if (googleClientId == null || googleClientId.isBlank() || googleClientId.contains("${")) {
            log.warn("GOOGLE_CLIENT_ID is not configured. Google login will be unavailable.");
            return null;
        }
        log.info("Google Authentication initialized with Client ID: ...{}",
                googleClientId.substring(Math.max(0, googleClientId.length() - 12)));
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }
}
