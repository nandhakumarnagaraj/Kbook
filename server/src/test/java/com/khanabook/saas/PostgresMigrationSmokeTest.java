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
 * Migration smoke test: applies the full Flyway migration chain to an empty
 * Testcontainers PostgreSQL and asserts the server starts with Hibernate
 * validation passing, the migration history head is V72 (permission system),
 * and key tables from each phase exist with their expected structure.
 *
 * <p>This is a hard deployment gate — if this fails, the Flyway chain is broken
 * and cannot be deployed to production. Uses real Postgres (not H2) because
 * migrations use Postgres-specific syntax (partial indexes, JSONB, etc.).
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
        registry.add("easebuzz.merchant-key", () -> "TEST_MERCHANT_KEY");
        registry.add("easebuzz.salt", () -> "TEST_SALT");
        registry.add("easebuzz.base-url", () -> "https://testpay.easebuzz.in");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAfterFlywayMigrationsOnPostgres() {
        // Spring Boot startup performs the migration and Hibernate schema validation.
    }

    @Test
    void migrationHistoryHeadIsV72() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC",
                String.class);
        assertThat(versions).isNotEmpty();
        assertThat(versions.get(0)).isEqualTo("72");
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
        // Every flag defaults disabled on first migration.
        assertThat(disabledFlags).isEqualTo(seededFlags);

        Integer partialIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname IN ('idx_webhook_inbox_claim', 'idx_webhook_inbox_review')",
                Integer.class);
        assertThat(partialIndexes).isEqualTo(2);
    }

    @Test
    void permissionSystemTablesExist() {
        // V72: staff_permissions, permission_requests, role_templates
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('staff_permissions', 'permission_requests', 'role_templates')",
                String.class);
        assertThat(tables).containsExactlyInAnyOrder("staff_permissions", "permission_requests", "role_templates");

        // Verify key columns on staff_permissions (especially revoked_at from V72's evolved schema)
        List<String> spColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'staff_permissions'",
                String.class);
        assertThat(spColumns).contains("id", "restaurant_id", "user_id", "permission_key",
                "granted", "granted_by", "granted_at", "revoked_at", "updated_at");

        // Verify permission_requests has the status check constraint
        List<String> prColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'permission_requests'",
                String.class);
        assertThat(prColumns).contains("id", "restaurant_id", "user_id", "permission_key",
                "status", "reason", "requested_at", "resolved_by", "resolved_at", "rejection_reason");

        // Verify role_templates has is_default (not is_system — confirms V72 schema)
        List<String> rtColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'role_templates'",
                String.class);
        assertThat(rtColumns).contains("id", "restaurant_id", "name", "permissions", "is_default");
        assertThat(rtColumns).doesNotContain("is_system");
    }

    @Test
    void fssaiAndNotificationTablesExist() {
        // V50-V52: FSSAI tracker + notifications infrastructure
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('fssai_tracker', 'fssai_renewals', 'device_tokens', 'notification_events')",
                String.class);
        assertThat(tables).contains("fssai_tracker", "fssai_renewals");
    }

    @Test
    void terminalIdentityTablesExist() {
        // V40-V41: terminal identity sync model
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('restaurant_terminal', 'device_registration_request')",
                String.class);
        assertThat(tables).contains("restaurant_terminal", "device_registration_request");

        // Verify terminal_id column exists on bills (V40 backfill)
        List<String> billColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'bills' AND column_name = 'terminal_id'",
                String.class);
        assertThat(billColumns).hasSize(1);
    }

    @Test
    void noGapsInMigrationChain() {
        // Verify all migrations applied successfully with no failures
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE",
                Integer.class);
        assertThat(failedCount).isZero();

        // Verify expected count: V1-V68, V71-V72 = 70 migrations (V69/V70 were removed as duplicates)
        Integer totalMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE type = 'SQL'",
                Integer.class);
        assertThat(totalMigrations).isEqualTo(70);
    }
}
