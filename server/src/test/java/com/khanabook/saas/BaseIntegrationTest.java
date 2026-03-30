package com.khanabook.saas;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      () -> "jdbc:h2:mem:khanabook_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");

        registry.add("JWT_SECRET",       () -> "integration-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("whatsapp.meta.fixed-otp", () -> "123456");

        registry.add("spring.flyway.enabled",             () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto",    () -> "create-drop");
    }
}
