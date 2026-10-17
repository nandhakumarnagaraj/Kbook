package com.khanabook.saas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requirement 2.15: asserts the Baseline_Tag server image starts successfully
 * against a database migrated to the Integration_Codebase head version (V48).
 *
 * Gated on system property {@code baseline.image} (e.g. {@code kbook-server:baseline-tag})
 * because the Baseline_Tag image only exists after task 3.2. When absent the test is
 * skipped; CI supplies the property once the tag is cut.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "baseline.image", matches = ".+")
class BaselineTagServerStartsOnV48SchemaTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kbook_migration_test")
                    .withUsername("kbook")
                    .withPassword("kbook");

    private static String baselineImage() {
        return System.getProperty("baseline.image");
    }

    @Test
    void baselineTagServerBootsAgainstV48Schema() {
        GenericContainer<?> baseline = new GenericContainer<>(DockerImageName.parse(baselineImage()))
                .dependsOn(postgres)
                .withEnv("DB_URL", postgres.getJdbcUrl())
                .withEnv("DB_USERNAME", postgres.getUsername())
                .withEnv("DB_PASSWORD", postgres.getPassword())
                .withEnv("JWT_SECRET", "baseline-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
                .withEnv("GOOGLE_CLIENT_ID", "test-google-client-id")
                .withEnv("PAYMENT_CRYPTO_SECRET", "migration-payment-secret-32-bytes-minimum-xxxx")
                .withEnv("APP_BASE_URL", "https://test.khanabook.app")
                .withExposedPorts(8081)
                .waitingFor(new HttpWaitStrategy()
                        .forPath("/api/v1/actuator/health")
                        .forPort(8081)
                        .withStartupTimeout(Duration.ofMinutes(2)));

        baseline.start();
        try {
            String health;
            try {
                health = baseline.execInContainer("sh", "-c",
                                "wget -qO- http://localhost:8081/api/v1/actuator/health").getStdout();
            } catch (Exception e) {
                // Container still started and passed the HTTP wait strategy; health is asserted
                // by the HttpWaitStrategy itself, so a probe failure here only weakens the check.
                health = "";
            }
            assertThat(health).contains("\"status\":\"UP\"");
        } finally {
            baseline.stop();
        }
    }
}
