package com.khanabook.saas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration smoke test: applies the full Flyway migration chain to an empty
 * Testcontainers PostgreSQL and asserts the server starts with Hibernate
 * validation passing, every migration on the classpath applied exactly once,
 * and key tables from each phase exist with their expected structure.
 *
 * <p>This is a hard deployment gate — if this fails, the Flyway chain is broken
 * and cannot be deployed to production. Uses real Postgres (not H2) because
 * migrations use Postgres-specific syntax (partial indexes, JSONB, etc.).
 *
 * <p>The chain assertions read the migration files off the classpath rather than
 * pinning a version or a count, so adding a migration does not require editing
 * this test, and a version collision is caught rather than tolerated.
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
    void migrationHistoryHeadIsHighestVersionOnClasspath() {
        List<String> onClasspath = migrationVersionsOnClasspath();
        assertThat(onClasspath).isNotEmpty();

        String head = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE "
                        + "ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        assertThat(head).isEqualTo(onClasspath.get(onClasspath.size() - 1));
    }

    @Test
    void phase2TablesExistWithSeedState() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        // V48 added feature_flags & webhook_inbox; V123 purged dead webhook_inbox table
        assertThat(tables).contains("feature_flag", "feature_flag_override", "feature_flag_audit");
        assertThat(tables).doesNotContain("webhook_inbox");

        Integer seededFlags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_flag", Integer.class);
        assertThat(seededFlags).isGreaterThanOrEqualTo(8);

        Integer disabledFlags = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feature_flag WHERE kill_switched = TRUE AND default_enabled = FALSE",
                Integer.class);
        // Every flag defaults disabled on first migration.
        assertThat(disabledFlags).isEqualTo(seededFlags);
    }

    @Test
    void permissionSystemTablesExist() {
        // V72 added custom permission tables; V124 purged them in favor of unified OWNER/SHOP_STAFF/KBOOK_ADMIN roles
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('staff_permissions', 'permission_requests', 'role_templates', 'restaurant_subscriptions', 'subscription_plans')",
                String.class);
        assertThat(tables).doesNotContain("staff_permissions", "permission_requests", "role_templates", "restaurant_subscriptions", "subscription_plans");

        // Verify users.role exists for unified staff role management
        List<String> userColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'users'",
                String.class);
        assertThat(userColumns).contains("id", "restaurant_id", "email", "role", "is_active", "pin_hash");
    }

    @Test
    void v131ActiveOrderAndSyncIndexesExist() {
        // V131: multi-column composite B-tree indexes for active orders & delta sync pulls
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexname IN (" +
                "'idx_bills_active_created', 'idx_bills_active_status_created', " +
                "'idx_menuitems_active_sync', 'idx_bill_payments_sync_pull')",
                String.class);
        assertThat(indexes).containsExactlyInAnyOrder(
                "idx_bills_active_created",
                "idx_bills_active_status_created",
                "idx_menuitems_active_sync",
                "idx_bill_payments_sync_pull"
        );
    }

    @Test
    void fssaiAndNotificationTablesExist() {
        // V50-V52 / V128: notification infrastructure active; dead fssai compliance tables purged
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('fssai_tracker', 'fssai_renewals', 'device_tokens', 'notification_events')",
                String.class);
        assertThat(tables).contains("device_tokens", "notification_events");
        assertThat(tables).doesNotContain("fssai_tracker", "fssai_renewals");
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
    void easebuzzTablesAndDeadBillColumnsPurged() {
        // V129: dead Easebuzz gateway tables and dead bill columns purged
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' " +
                "AND table_name IN ('chargebacks', 'easebuzz_payouts', 'easebuzz_refund_attempts', 'easebuzz_sub_merchant_webhook_event', 'easebuzz_webhook_events', 'webhook_retry_jobs', 'easebuzz_sub_merchant')",
                String.class);
        assertThat(tables).isEmpty();

        List<String> deadBillColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'bills' " +
                "AND column_name IN ('gateway_link_created_at', 'settled_amount', 'settled_at', 'commission_amount', 'refund_id', 'gateway_txn_id', 'gateway_status')",
                String.class);
        assertThat(deadBillColumns).isEmpty();
    }

    @Test
    void noGapsInMigrationChain() {
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE",
                Integer.class);
        assertThat(failedCount).isZero();

        // A row with a NULL checksum is permanently invisible to Flyway: DbValidate skips
        // it, so neither `flyway validate` nor app startup will ever object, and no future
        // migration re-applies it. Production carried exactly this — V82 was hand-applied
        // with checksum NULL and never received the index its own script creates — and it
        // survived 88 "Successfully validated" migrations plus two read-only audits
        // because nothing ever queried for it. Assert the absence of the condition, not
        // merely the absence of a mismatch.
        List<String> unvalidated = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE checksum IS NULL",
                String.class);
        assertThat(unvalidated)
                .as("migrations with a NULL checksum are never validated by Flyway; "
                        + "repair the ledger so they are, instead of hand-inserting rows")
                .isEmpty();

        // Every row should have been applied by the same migrator user. More than one
        // distinct value means at least one migration was hand-applied outside Flyway
        // (production carried exactly one, installed_by='manual'), which means nothing
        // executed that script and the ledger cannot vouch the schema matches it.
        List<String> installers = jdbcTemplate.queryForList(
                "SELECT DISTINCT installed_by FROM flyway_schema_history WHERE installed_by IS NOT NULL",
                String.class);
        assertThat(installers)
                .as("distinct flyway_schema_history.installed_by values; more than one means a "
                        + "migration was hand-applied and bypassed the migrator")
                .hasSize(1);

        // The chain is checked against the migration files actually on the classpath,
        // not a hardcoded total. A pinned count silently rotted at "78" while the schema
        // moved past it, so this gate reported on a version of the schema that stopped
        // existing ten migrations ago and would never have flagged a bad chain.
        List<String> onClasspath = migrationVersionsOnClasspath();
        assertThat(onClasspath).isNotEmpty();

        // Two files sharing a version is the failure that actually happened here: a new
        // V100 collided with the existing V100 and Flyway refused to resolve the chain.
        // assertThat(list) alone would not catch it, because Flyway collapses the pair
        // into a single resolved version.
        assertThat(onClasspath).doesNotHaveDuplicates();

        List<String> applied = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE",
                String.class);

        // Every file applied, and nothing applied that has no file. Gaps themselves are
        // legitimate (V69/V70 were removed as duplicates), so equality against the
        // classpath is the meaningful invariant rather than a contiguous run.
        assertThat(applied).containsExactlyInAnyOrderElementsOf(onClasspath);
    }

    /**
     * Every {@code V<n>__<name>.sql} shipped in the jar, ascending by numeric version.
     * Version, not filename, is the sort key: {@code V9} predates {@code V10}.
     */
    private static List<String> migrationVersionsOnClasspath() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/V*__*.sql");
        } catch (IOException e) {
            throw new IllegalStateException("Could not scan db/migration on the classpath", e);
        }
        return Arrays.stream(resources)
                .map(PostgresMigrationSmokeTest::versionOf)
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
    }

    private static String versionOf(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            throw new IllegalStateException("Migration resource has no filename: " + resource);
        }
        return filename.substring(1, filename.indexOf("__"));
    }
}
