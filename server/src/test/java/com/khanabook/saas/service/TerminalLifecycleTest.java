package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.controller.TerminalController;
import com.khanabook.saas.controller.TerminalManagementController;
import com.khanabook.saas.entity.RestaurantTerminal;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10: End-to-end lifecycle tests validating secure terminal management:
 * - Unknown devices ALWAYS require approval (even single-terminal shops)
 * - First device auto-creates only for OWNER on a virgin restaurant
 * - Recovery preserves terminal identity
 * - Deactivation revokes credentials
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TerminalLifecycleTest extends BaseIntegrationTest {

    private static final AtomicLong RESTAURANT_ID_COUNTER = new AtomicLong(8001L);

    @Autowired
    private TerminalController terminalController;
    @Autowired
    private TerminalManagementController managementController;
    @Autowired
    private TerminalManagementService terminalManagementService;
    @Autowired
    private RestaurantTerminalRepository terminalRepository;

    private Long restaurantId;

    @BeforeEach
    void setUp() {
        restaurantId = RESTAURANT_ID_COUNTER.getAndIncrement();
        String loginId = "lifecycle-owner-" + restaurantId;
        if (userRepository.findByLoginId(loginId).isEmpty()) {
            persistUser(loginId, restaurantId, UserRole.OWNER);
        }
        TenantContext.setCurrentTenant(restaurantId);
        TenantContext.setCurrentRole("OWNER");
        TenantContext.setCurrentUserId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void firstDevice_ownerAutoCreatesTerminalA() {
        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-first", "Pixel 7"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = (TerminalController.TerminalActivationResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.terminalSeries()).isEqualTo("A");
        assertThat(body.terminalToken()).isNotBlank();
    }

    @Test
    void unknownDevice_cannotHijackSingleTerminal() {
        // OWNER creates Terminal A
        terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-A", null));

        // Unknown Device B tries to activate on single-terminal shop
        // WITHOUT a terminal token — must get PENDING (recovery candidate), not credentials
        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-B", null));

        // Must get PENDING_APPROVAL — NOT auto-rebind
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var pending = (TerminalController.TerminalPendingResponse) response.getBody();
        assertThat(pending.status()).isEqualTo("PENDING_APPROVAL");

        // Terminal A must remain bound to Device A
        var terminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
        assertThat(terminals).hasSize(1);
        assertThat(terminals.get(0).getDeviceId()).isEqualTo("device-A");
        assertThat(terminals.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void copiedDeviceId_withoutToken_cannotClaimTerminal() {
        // OWNER creates Terminal A bound to device-A
        terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-A-sec", null));

        // Another caller knows device-A's deviceId but has no terminal token
        // (TenantContext.getCurrentTerminalDevice() is null — no X-Terminal-Token header)
        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-A-sec", null));

        // Without a valid terminal token, known deviceId triggers a RECOVERY request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var pending = (TerminalController.TerminalPendingResponse) response.getBody();
        assertThat(pending.status()).isEqualTo("PENDING_APPROVAL");

        // Terminal A remains unchanged
        var terminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
        assertThat(terminals).hasSize(1);
        assertThat(terminals.get(0).getDeviceId()).isEqualTo("device-A-sec");
    }

    @Test
    void knownDevice_withValidToken_succeedsWithoutRecovery() {
        // OWNER creates Terminal A
        var createResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-token-test", null));
        var created = (TerminalController.TerminalActivationResponse) createResponse.getBody();

        // Simulate the same device calling activate with its terminal token
        // (TenantContext has the terminal device set by TerminalRequestFilter)
        TenantContext.setCurrentTerminalDevice("device-token-test");
        TenantContext.setCurrentTerminalId(created.terminalId());
        TenantContext.setCurrentTerminalSeries(created.terminalSeries());

        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-token-test", null));

        // Should return 200 OK — no recovery needed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Clear terminal context for other tests
        TenantContext.setCurrentTerminalDevice(null);
        TenantContext.setCurrentTerminalId(null);
        TenantContext.setCurrentTerminalSeries(null);
    }

    @Test
    void approvedRecovery_preservesLogicalTerminal() {
        // Create Terminal A with Device A
        var createResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-orig", "Phone"));
        var created = (TerminalController.TerminalActivationResponse) createResponse.getBody();
        Long terminalId = Long.parseLong(created.terminalId());

        // Get initial state
        RestaurantTerminal before = terminalRepository.findById(terminalId).orElseThrow();
        Long originalCredVer = before.getCredentialVersion();

        // Admin recovers to new device (simulates approved recovery)
        var recoverResponse = managementController.recoverTerminal(
                terminalId, new TerminalManagementController.RecoverRequest("device-new"));
        assertThat(recoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var recovered = recoverResponse.getBody();

        // Verify recovery invariants:
        assertThat(recovered.terminalId()).isEqualTo(terminalId); // same terminal ID
        assertThat(recovered.terminalSeries()).isEqualTo("A"); // same series
        assertThat(recovered.terminalToken()).isNotBlank(); // new token issued

        // DB-level invariants
        RestaurantTerminal after = terminalRepository.findById(terminalId).orElseThrow();
        assertThat(after.getTerminalSeries()).isEqualTo("A"); // series unchanged
        assertThat(after.getDeviceId()).isEqualTo("device-new"); // new device bound
        assertThat(after.getStatus()).isEqualTo("ACTIVE");
        assertThat(after.getCredentialVersion()).isGreaterThan(originalCredVer); // old tokens revoked

        // Active count unchanged (recovery does not consume a slot)
        long activeCount = terminalRepository.countByRestaurantIdAndStatus(restaurantId, "ACTIVE");
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void deactivateAndRecover_fullFlow() {
        // Create terminal
        var createResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-deact", null));
        var created = (TerminalController.TerminalActivationResponse) createResponse.getBody();
        Long terminalId = Long.parseLong(created.terminalId());

        // Deactivate
        var deactResponse = managementController.deactivateTerminal(terminalId);
        assertThat(deactResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify terminal is inactive and credential version bumped
        RestaurantTerminal deactivated = terminalRepository.findById(terminalId).orElseThrow();
        assertThat(deactivated.getStatus()).isEqualTo("INACTIVE");
        assertThat(deactivated.getIsActive()).isFalse();

        // Same device tries to activate WITH old token (simulated) — terminal is deactivated
        TenantContext.setCurrentTerminalDevice("device-deact");
        var forbiddenResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-deact", null));
        assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        TenantContext.setCurrentTerminalDevice(null);

        // Admin recovers to a new device
        var recoverResponse = managementController.recoverTerminal(
                terminalId, new TerminalManagementController.RecoverRequest("device-replacement"));
        assertThat(recoverResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recoverResponse.getBody().terminalSeries()).isEqualTo("A");
    }

    @Test
    void concurrentUnknownDevices_neitherHijacksSingleTerminal() {
        // Create Terminal A with Device A
        terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-A-conc", null));

        // Device B and Device C both try to activate
        var responseB = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-B-conc", null));
        var responseC = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-C-conc", null));

        // Both get PENDING — neither can take over Terminal A
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(responseC.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Terminal A still belongs to Device A
        var terminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
        assertThat(terminals).hasSize(1);
        assertThat(terminals.get(0).getDeviceId()).isEqualTo("device-A-conc");
    }

    @Test
    void oldDevice_reconnectsNormally_afterUnauthorizedRecoveryAttempt() {
        // Create Terminal A with Device A
        var createResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-A-recon", null));
        var created = (TerminalController.TerminalActivationResponse) createResponse.getBody();

        // Unknown Device B submits a pending request
        var pendingResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-B-recon", null));
        assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // No recovery was approved. Device A reconnects with its valid terminal token.
        TenantContext.setCurrentTerminalDevice("device-A-recon");
        TenantContext.setCurrentTerminalId(created.terminalId());

        var reconnectResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-A-recon", null));
        assertThat(reconnectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = (TerminalController.TerminalActivationResponse) reconnectResponse.getBody();
        assertThat(body.terminalSeries()).isEqualTo("A");

        // Clean up terminal context
        TenantContext.setCurrentTerminalDevice(null);
        TenantContext.setCurrentTerminalId(null);
    }

    @Test
    void rename_updatesTerminalName() {
        var response = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-rename", null));
        var body = (TerminalController.TerminalActivationResponse) response.getBody();
        Long terminalId = Long.parseLong(body.terminalId());

        var renameResponse = managementController.renameTerminal(
                terminalId, new TerminalManagementController.RenameRequest("Front Counter"));
        assertThat(renameResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renameResponse.getBody().terminalName()).isEqualTo("Front Counter");
    }

    @Test
    void approvalFlow_multiTerminal() {
        // OWNER creates Terminal A
        terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-1-multi", null));

        // Second device gets PENDING
        var pendingResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-2-multi", null));
        assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        var pending = (TerminalController.TerminalPendingResponse) pendingResponse.getBody();

        // Admin approves
        var approvalResponse = managementController.approveRequest(pending.requestId());
        assertThat(approvalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approvalResponse.getBody().terminalSeries()).isEqualTo("B");

        // Device 2 completes activation (securely obtains credentials)
        var completeResponse = terminalController.completeActivation(
                new TerminalController.CompleteActivationRequest(pending.requestId(), "device-2-multi"));
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void crossRestaurant_requestStatusQuery_returns404() {
        // Create a terminal and pending request for this restaurant
        terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-cross-1", null));
        var pendingResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-cross-2", null));
        var pending = (TerminalController.TerminalPendingResponse) pendingResponse.getBody();

        // Switch to a different restaurant context
        Long otherRestaurantId = restaurantId + 1000L;
        String otherLoginId = "other-owner-" + otherRestaurantId;
        if (userRepository.findByLoginId(otherLoginId).isEmpty()) {
            persistUser(otherLoginId, otherRestaurantId, UserRole.OWNER);
        }
        TenantContext.setCurrentTenant(otherRestaurantId);

        // Query the first restaurant's request — must get 404
        var crossResponse = terminalController.getRequestStatus(pending.requestId());
        assertThat(crossResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Restore context
        TenantContext.setCurrentTenant(restaurantId);
    }

    @Test
    void completeActivation_wrongDevice_isForbidden() {
        // Create terminal + pending request
        terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-comp-1", null));
        var pendingResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-comp-2", null));
        var pending = (TerminalController.TerminalPendingResponse) pendingResponse.getBody();

        // Approve
        managementController.approveRequest(pending.requestId());

        // A different device tries to complete — must be rejected
        var wrongDeviceResponse = terminalController.completeActivation(
                new TerminalController.CompleteActivationRequest(pending.requestId(), "device-attacker"));
        assertThat(wrongDeviceResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void historicalRevokedTerminal_doesNotSilentlyCreateDuplicate() {
        // Create Terminal A, then deactivate it (simulates revoked history)
        var createResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-hist", null));
        var created = (TerminalController.TerminalActivationResponse) createResponse.getBody();
        Long terminalId = Long.parseLong(created.terminalId());
        managementController.deactivateTerminal(terminalId);

        // Restaurant now has one INACTIVE historical terminal.
        // A new OWNER device calls activate — it should NOT auto-create another Terminal A.
        // It should go through the pending approval path since allTerminals is not empty.
        var newDeviceResponse = terminalController.activate(
                new TerminalController.TerminalActivationRequest("device-hist-new", null));
        assertThat(newDeviceResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Verify no second terminal was silently created
        var terminals = terminalRepository.findByRestaurantIdOrderByIdAsc(restaurantId);
        assertThat(terminals).hasSize(1);
        assertThat(terminals.get(0).getTerminalSeries()).isEqualTo("A");
        assertThat(terminals.get(0).getStatus()).isEqualTo("INACTIVE");
    }
}
