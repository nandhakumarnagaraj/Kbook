package com.khanabook.saas.service;

import com.khanabook.saas.feature.restaurants.service.TerminalManagementService;

import com.khanabook.saas.feature.notifications.data.DeviceRegistrationRequest;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.notifications.data.DeviceRegistrationRequestRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL-specific concurrency test for the 5-terminal limit.
 * Requires Docker — skipped when Docker is unavailable.
 *
 * Verifies that under real PostgreSQL row-level locking:
 * - Two concurrent approval transactions for the 5th slot result in exactly one success.
 * - Terminal series remain unique.
 * - No sixth ACTIVE terminal is ever created.
 * - The restaurant-level pessimistic lock serializes concurrent approvals.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TerminalManagementPostgresConcurrencyTest {

    private static final Long RESTAURANT_ID = 9500L;

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("kbook_concurrency_test")
                    .withUsername("kbook")
                    .withPassword("kbook");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("JWT_SECRET", () -> "concurrency-test-secret-64-chars-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-google-client-id");
        registry.add("PAYMENT_CRYPTO_SECRET", () -> "concurrency-payment-secret-32-bytes-min-xxxx");
        registry.add("APP_BASE_URL", () -> "https://test.khanabook.app");
    }

    @Autowired private TerminalManagementService terminalManagementService;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private RestaurantProfileRepository restaurantProfileRepository;
    @Autowired private DeviceRegistrationRequestRepository requestRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Clean slate for this restaurant
        jdbcTemplate.update("DELETE FROM device_registration_request WHERE restaurant_id = ?", RESTAURANT_ID);
        jdbcTemplate.update("DELETE FROM restaurant_terminal WHERE restaurant_id = ?", RESTAURANT_ID);
        jdbcTemplate.update("DELETE FROM restaurantprofiles WHERE restaurant_id = ?", RESTAURANT_ID);

        // Seed restaurant profile (lock target)
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(RESTAURANT_ID);
        profile.setLocalId(1L);
        profile.setDeviceId("SETUP_DEVICE");
        profile.setShopName("Concurrency Test Shop");
        profile.setCreatedAt(System.currentTimeMillis());
        profile.setUpdatedAt(System.currentTimeMillis());
        profile.setServerUpdatedAt(System.currentTimeMillis());
        profile.setLastResetDateProper(java.time.LocalDate.now());
        profile.setLogoVersion(0);
        profile.setUpiQrVersion(0);
        restaurantProfileRepository.save(profile);
    }

    @Test
    @DisplayName("Two concurrent approvals for the last slot: only one succeeds, final count = 3")
    void concurrentLastSlotApproval_onlyOneSucceeds() throws Exception {
        // V120 seeds PRO with max_terminals = 3, and an unprovisioned restaurant
        // resolves to PRO. Fill 2 of the 3 slots so one is left to race for.
        for (int i = 1; i <= 2; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    RESTAURANT_ID, 1L, "device-pre-" + i, "Model", "NEW_DEVICE", null);
            terminalManagementService.approveRequest(req.getId(), RESTAURANT_ID, 1L, "OWNER");
        }
        assertThat(terminalRepository.countByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE")).isEqualTo(2);

        // Create 2 pending requests that will race for the last slot
        DeviceRegistrationRequest reqA = terminalManagementService.createRegistrationRequest(
                RESTAURANT_ID, 1L, "device-race-A", "Model-A", "NEW_DEVICE", null);
        DeviceRegistrationRequest reqB = terminalManagementService.createRegistrationRequest(
                RESTAURANT_ID, 1L, "device-race-B", "Model-B", "NEW_DEVICE", null);

        // Use a barrier to ensure both threads start their approval at the same time
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger limitReached = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                terminalManagementService.approveRequest(reqA.getId(), RESTAURANT_ID, 1L, "OWNER");
                successes.incrementAndGet();
            } catch (ResponseStatusException e) {
                if (e.getBody().getDetail() != null && e.getBody().getDetail().contains("TERMINAL_LIMIT_REACHED")) {
                    limitReached.incrementAndGet();
                }
            } catch (Exception e) {
                limitReached.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        executor.submit(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                terminalManagementService.approveRequest(reqB.getId(), RESTAURANT_ID, 1L, "OWNER");
                successes.incrementAndGet();
            } catch (ResponseStatusException e) {
                if (e.getBody().getDetail() != null && e.getBody().getDetail().contains("TERMINAL_LIMIT_REACHED")) {
                    limitReached.incrementAndGet();
                }
            } catch (Exception e) {
                limitReached.incrementAndGet();
            } finally {
                done.countDown();
            }
        });

        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one succeeded, one was rejected
        assertThat(successes.get()).isEqualTo(1);
        assertThat(limitReached.get()).isEqualTo(1);

        // Final ACTIVE count is exactly 3 — never 4
        long finalActiveCount = terminalRepository.countByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE");
        assertThat(finalActiveCount).isEqualTo(3);

        // Terminal series are unique
        List<RestaurantTerminal> allTerminals = terminalRepository.findByRestaurantIdOrderByIdAsc(RESTAURANT_ID);
        Set<String> seriesSet = allTerminals.stream()
                .map(RestaurantTerminal::getTerminalSeries)
                .collect(Collectors.toSet());
        assertThat(seriesSet).hasSize(allTerminals.size());

        // The losing request should remain PENDING (not corrupted)
        List<DeviceRegistrationRequest> pending = requestRepository
                .findByRestaurantIdAndStatusOrderByRequestedAtDesc(RESTAURANT_ID, "PENDING");
        assertThat(pending).hasSize(1); // The loser remains PENDING

        List<DeviceRegistrationRequest> approved = requestRepository
                .findByRestaurantIdAndStatusOrderByRequestedAtDesc(RESTAURANT_ID, "APPROVED");
        assertThat(approved).hasSize(3); // 2 pre-approved + 1 winner
    }

    @Test
    @DisplayName("Six concurrent approvals never exceed the 3 PRO terminals (PostgreSQL locks)")
    void sixConcurrentApprovals_maxThreeActive() throws Exception {
        // Create 6 pending requests for a plan that allows 3
        List<Long> requestIds = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    RESTAURANT_ID, 1L, "device-pg-" + i, "Model-" + i, "NEW_DEVICE", null);
            requestIds.add(req.getId());
        }

        // Approve all 6 concurrently
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CyclicBarrier barrier = new CyclicBarrier(6);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(6);

        for (Long requestId : requestIds) {
            executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    terminalManagementService.approveRequest(requestId, RESTAURANT_ID, 1L, "OWNER");
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly 3 succeeded, 3 were rejected by the limit
        assertThat(successes.get()).isEqualTo(3);
        assertThat(failures.get()).isEqualTo(3);

        // Final ACTIVE count is exactly 3
        long finalActiveCount = terminalRepository.countByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE");
        assertThat(finalActiveCount).isEqualTo(3);

        // No fourth ACTIVE terminal row
        List<RestaurantTerminal> activeTerminals = terminalRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE");
        assertThat(activeTerminals).hasSize(3);

        // All series unique
        Set<String> series = activeTerminals.stream()
                .map(RestaurantTerminal::getTerminalSeries)
                .collect(Collectors.toSet());
        assertThat(series).hasSize(3);
    }
}
