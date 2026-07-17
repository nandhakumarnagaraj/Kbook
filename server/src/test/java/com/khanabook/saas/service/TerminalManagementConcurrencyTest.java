package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.DeviceRegistrationRequest;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.DeviceRegistrationRequestRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: Concurrency tests for the 5-terminal limit enforcement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TerminalManagementConcurrencyTest extends BaseIntegrationTest {

    private static final AtomicLong RESTAURANT_ID_COUNTER = new AtomicLong(7001L);

    @Autowired
    private TerminalManagementService terminalManagementService;
    @Autowired
    private RestaurantTerminalRepository terminalRepository;
    @Autowired
    private DeviceRegistrationRequestRepository requestRepository;

    private Long restaurantId;

    @BeforeEach
    void setUp() {
        restaurantId = RESTAURANT_ID_COUNTER.getAndIncrement();
        String loginId = "concurrency-owner-" + restaurantId;
        if (userRepository.findByLoginId(loginId).isEmpty()) {
            persistUser(loginId, restaurantId, UserRole.OWNER);
        }
        TenantContext.setCurrentTenant(restaurantId);
        TenantContext.setCurrentRole("OWNER");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void concurrentApprovals_neverExceedFiveActiveTerminals() throws Exception {
        // Create 8 pending registration requests (more than the 5 limit)
        List<Long> requestIds = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    restaurantId, 1L, "device-conc-" + i, "Model-" + i, "NEW_DEVICE", null);
            requestIds.add(req.getId());
        }

        // Attempt to approve all 8 concurrently
        ExecutorService executor = Executors.newFixedThreadPool(8);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger limitReached = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(8);

        final Long testRestaurantId = restaurantId;
        for (Long requestId : requestIds) {
            executor.submit(() -> {
                try {
                    terminalManagementService.approveRequest(requestId, testRestaurantId, 1L, "OWNER");
                    successes.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getBody().getDetail() != null && e.getBody().getDetail().contains("TERMINAL_LIMIT_REACHED")) {
                        limitReached.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Other transactional exceptions (deadlock retries etc) — count as limit-reached
                    limitReached.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify: at most 5 terminals are ACTIVE
        long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        assertThat(activeCount).isLessThanOrEqualTo(5);

        // At least 3 should have been rejected
        assertThat(limitReached.get()).isGreaterThanOrEqualTo(3);
        // And exactly 5 should have succeeded
        assertThat(successes.get()).isEqualTo(5);
    }

    @Test
    void doubleApprovalSameRequest_secondAttemptIsRejected() {
        DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                restaurantId, 1L, "device-double-approve", "Model", "NEW_DEVICE", null);

        // First approval succeeds
        terminalManagementService.approveRequest(req.getId(), restaurantId, 1L, "OWNER");

        // Second approval of same request → CONFLICT
        try {
            terminalManagementService.approveRequest(req.getId(), restaurantId, 1L, "OWNER");
            assertThat(false).as("Should have thrown").isTrue();
        } catch (ResponseStatusException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(409);
        }
    }

    @Test
    void recoveryDoesNotConsumeSlot() {
        // Create 5 active terminals to fill the limit
        for (int i = 1; i <= 5; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    restaurantId, 1L, "device-full-" + i, "Model", "NEW_DEVICE", null);
            terminalManagementService.approveRequest(req.getId(), restaurantId, 1L, "OWNER");
        }

        long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        assertThat(activeCount).isEqualTo(5);

        // Deactivate one terminal and then recover it (should NOT fail with limit reached)
        List<RestaurantTerminal> terminals = terminalRepository.findByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        RestaurantTerminal target = terminals.get(0);
        terminalManagementService.deactivateTerminal(target.getId(), restaurantId);

        // Recovery: rebinds the same logical terminal to a new device — no new slot
        TerminalManagementService.ActivationResult result = terminalManagementService.recoverTerminal(
                target.getId(), restaurantId, "device-recovery-new", "OWNER");

        assertThat(result.terminal().getStatus()).isEqualTo("ACTIVE");
        assertThat(result.terminal().getDeviceId()).isEqualTo("device-recovery-new");
        // Still exactly 5 active
        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(5);
    }

    @Test
    void inactiveRecovery_belowLimit_succeeds() {
        // Create 4 active terminals
        for (int i = 1; i <= 4; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    restaurantId, 1L, "device-below-" + i, "Model", "NEW_DEVICE", null);
            terminalManagementService.approveRequest(req.getId(), restaurantId, 1L, "OWNER");
        }
        // Create a 5th and deactivate it
        DeviceRegistrationRequest fifthReq = terminalManagementService.createRegistrationRequest(
                restaurantId, 1L, "device-below-5", "Model", "NEW_DEVICE", null);
        terminalManagementService.approveRequest(fifthReq.getId(), restaurantId, 1L, "OWNER");
        List<RestaurantTerminal> all = terminalRepository.findByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        RestaurantTerminal toDeactivate = all.get(all.size() - 1);
        terminalManagementService.deactivateTerminal(toDeactivate.getId(), restaurantId);

        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(4);

        // Recovering the INACTIVE terminal should succeed (4 → 5)
        TerminalManagementService.ActivationResult result = terminalManagementService.recoverTerminal(
                toDeactivate.getId(), restaurantId, "device-reactivated", "OWNER");
        assertThat(result.terminal().getStatus()).isEqualTo("ACTIVE");
        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(5);
    }

    @Test
    void inactiveRecovery_atLimit_rejected() {
        // Create 5 active terminals + 1 inactive
        for (int i = 1; i <= 5; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    restaurantId, 1L, "device-atlim-" + i, "Model", "NEW_DEVICE", null);
            terminalManagementService.approveRequest(req.getId(), restaurantId, 1L, "OWNER");
        }
        DeviceRegistrationRequest sixthReq = terminalManagementService.createRegistrationRequest(
                restaurantId, 1L, "device-atlim-6", "Model", "NEW_DEVICE", null);
        // Approve the 6th — should fail because limit reached
        try {
            terminalManagementService.approveRequest(sixthReq.getId(), restaurantId, 1L, "OWNER");
            assertThat(false).as("Should have thrown TERMINAL_LIMIT_REACHED").isTrue();
        } catch (ResponseStatusException e) {
            // Expected
        }

        // Instead deactivate one, approve the 6th, then deactivate another
        List<RestaurantTerminal> active = terminalRepository.findByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        terminalManagementService.deactivateTerminal(active.get(0).getId(), restaurantId);
        // Now 4 active — approve the 6th request
        terminalManagementService.approveRequest(sixthReq.getId(), restaurantId, 1L, "OWNER");
        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(5);

        // Now try to recover the deactivated terminal — should FAIL (already at 5)
        try {
            terminalManagementService.recoverTerminal(
                    active.get(0).getId(), restaurantId, "device-overflow", "OWNER");
            assertThat(false).as("Should have thrown TERMINAL_LIMIT_REACHED").isTrue();
        } catch (ResponseStatusException e) {
            assertThat(e.getBody().getDetail()).contains("TERMINAL_LIMIT_REACHED");
        }

        // Active count remains 5 — never 6
        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(5);
    }

    @Test
    void activeReplacement_atLimit_succeeds() {
        // Create exactly 5 active terminals
        for (int i = 1; i <= 5; i++) {
            DeviceRegistrationRequest req = terminalManagementService.createRegistrationRequest(
                    restaurantId, 1L, "device-repl-" + i, "Model", "NEW_DEVICE", null);
            terminalManagementService.approveRequest(req.getId(), restaurantId, 1L, "OWNER");
        }
        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(5);

        // Replacing the device on an ACTIVE terminal should succeed (no count change)
        List<RestaurantTerminal> active = terminalRepository.findByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        TerminalManagementService.ActivationResult result = terminalManagementService.recoverTerminal(
                active.get(0).getId(), restaurantId, "device-replacement-new", "OWNER");
        assertThat(result.terminal().getStatus()).isEqualTo("ACTIVE");
        assertThat(terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE")).isEqualTo(5);
    }
}
