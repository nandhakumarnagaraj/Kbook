package com.khanabook.saas.service;

import com.khanabook.saas.entity.DeviceRegistrationRequest;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.repository.DeviceRegistrationRequestRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
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
        restaurantProfileRepository.save(profile);
    }

    @Test
    @DisplayName("Two concurrent approvals for the 5th slot: only one succeeds, final count = 5")
    void concurrentFifthSlotApproval_onlyOneSucceeds() throws Exception {
        // Create 4 active terminals (fill 4 of 5 slots)
        for (int i = 1; i <= 4; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    RESTAURANT_ID, 1L, "device-pre-" + i, "Model", "NEW_DEVICE", null);
            terminalManagementService.approveRequest(req.getId(), RESTAURANT_ID, 1L, "OWNER");
        }
        assertThat(terminalRepository.countByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE")).isEqualTo(4);

        // Create 2 pending requests that will race for the 5th slot
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

        // Final ACTIVE count is exactly 5 — never 6
        long finalActiveCount = terminalRepository.countByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE");
        assertThat(finalActiveCount).isEqualTo(5);

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
        assertThat(approved).hasSize(5); // 4 pre-approved + 1 winner
    }

    @Test
    @DisplayName("Eight concurrent approvals never exceed 5 active terminals (PostgreSQL locks)")
    void eightConcurrentApprovals_maxFiveActive() throws Exception {
        // Create 8 pending requests
        List<Long> requestIds = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    RESTAURANT_ID, 1L, "device-pg-" + i, "Model-" + i, "NEW_DEVICE", null);
            requestIds.add(req.getId());
        }

        // Approve all 8 concurrently
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CyclicBarrier barrier = new CyclicBarrier(8);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(8);

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

        // Exactly 5 succeeded
        assertThat(successes.get()).isEqualTo(5);
        assertThat(failures.get()).isEqualTo(3);

        // Final ACTIVE count is exactly 5
        long finalActiveCount = terminalRepository.countByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE");
        assertThat(finalActiveCount).isEqualTo(5);

        // No sixth ACTIVE terminal row
        List<RestaurantTerminal> activeTerminals = terminalRepository.findByRestaurantIdAndStatus(RESTAURANT_ID, "ACTIVE");
        assertThat(activeTerminals).hasSize(5);

        // All series unique
        Set<String> series = activeTerminals.stream()
                .map(RestaurantTerminal::getTerminalSeries)
                .collect(Collectors.toSet());
        assertThat(series).hasSize(5);
    }
}
