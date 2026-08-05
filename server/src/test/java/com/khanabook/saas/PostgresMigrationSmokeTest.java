package com.khanabook.saas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration smoke test (Requirement 2.14): applies the full Flyway_Migration_Set
 * (V1-V48) to an empty Testcontainers PostgreSQL and asserts the server starts,
 * the migration history head is V48, and the Phase 2 tables exist with their
 * seed state (Requirements 30.3, 30.4, 30.9, 33.4).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgresMigrationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kbook_migration_test")
                    .withUsername("kbook")
                    .withPassword("kbook");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("JWT_SECRET", () -> "migration-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("PAYMENT_CRYPTO_SECRET", () -> "migration-payment-secret-32-bytes-minimum-xxxx");
        registry.add("APP_BASE_URL", () -> "https://test.khanabook.app");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAfterFlywayMigrationsOnPostgres() {
        // Spring Boot startup performs the migration and Hibernate schema validation.
    }

    @Test
    void migrationHistoryHeadIsV48() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC",
                String.class);
        assertThat(versions).isNotEmpty();
        assertThat(versions.get(0)).isEqualTo("48");
    }

    @Test
    void phase2TablesExistWithSeedState() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertThat(tables).contains("feature_flag", "feature_flag_override", "feature_flag_audit", "webhook_inbox");

        Integer seededFlags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_flag", Integer.class);
        assertThat(seededFlags).isGreaterThanOrEqualTo(8);

        Integer disabledFlags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_flag WHERE kill_switched = TRUE AND default_enabled = FALSE",
                Integer.class);
        // Requirement 30.9: every flag defaults disabled on first migration.
        assertThat(disabledFlags).isEqualTo(seededFlags);

        Integer partialIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname IN ('idx_webhook_inbox_claim', 'idx_webhook_inbox_review')",
                Integer.class);
        assertThat(partialIndexes).isEqualTo(2);
    }
}
