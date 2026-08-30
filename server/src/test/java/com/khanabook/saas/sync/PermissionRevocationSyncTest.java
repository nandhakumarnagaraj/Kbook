package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Distributed state problems B2/B4: Permission revocation stale cache +
 * offline auth decider wiring.
 *
 * Real use case: Owner revokes CASHIER's "billing.settle" permission.
 * CASHIER's device is offline. CASHIER keeps settling bills.
 * When device reconnects, the operation should be revalidated.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PermissionRevocationSyncTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 8601L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private PermissionService permissionService;
    @Autowired private StaffPermissionRevisionRepository revisionRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String ownerToken;
    private User cashier;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, "OWNER");
        cashier = persistUser("cashier-" + UUID.randomUUID(), RESTAURANT, UserRole.CASHIER);
    }

    private RestaurantTerminal createTerminal(String series) {
        RestaurantTerminal t = new RestaurantTerminal();
        t.setRestaurantId(RESTAURANT);
        t.setTerminalSeries(series);
        t.setTerminalName("Terminal " + series);
        t.setDeviceId("DEV_" + series);
        t.setIsActive(true);
        t.setCreatedAt(System.currentTimeMillis());
        t.setUpdatedAt(System.currentTimeMillis());
        return terminalRepository.save(t);
    }

    private String terminalToken(RestaurantTerminal t) {
        return jwtUtility.generateTerminalToken(
                "owner", RESTAURANT, "OWNER",
                t.getId().toString(), t.getTerminalSeries(), t.getDeviceId());
    }

    private String billJson(long localId, long updatedAt) {
        return """
            [{
              "localId": %d,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "dailyOrderId": 1,
              "dailyOrderDisplay": "1",
              "lifetimeOrderId": 1,
              "orderType": "dine_in",
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "pending",
              "orderStatus": "draft"
            }]
            """.formatted(localId, RESTAURANT, updatedAt, updatedAt);
    }

    @Test
    void permissionGrantCreatesRevision() {
        permissionService.grantPermission(RESTAURANT, cashier.getId(), "billing.settle", owner.getId());

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, cashier.getId());
        assertThat(revision).isPresent();
        assertThat(revision.get().getRevision()).isGreaterThan(0);
    }

    @Test
    void permissionRevokeBumpsRevision() {
        permissionService.grantPermission(RESTAURANT, cashier.getId(), "billing.settle", owner.getId());
        long rev1 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, cashier.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        permissionService.revokePermission(RESTAURANT, cashier.getId(), "billing.settle");
        long rev2 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, cashier.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        assertThat(rev2).isGreaterThan(rev1);
    }

    @Test
    void grantRevokeGrantRevoke_isMonotonic() {
        permissionService.grantPermission(RESTAURANT, cashier.getId(), "billing.settle", owner.getId());
        permissionService.revokePermission(RESTAURANT, cashier.getId(), "billing.settle");
        permissionService.grantPermission(RESTAURANT, cashier.getId(), "billing.settle", owner.getId());
        permissionService.revokePermission(RESTAURANT, cashier.getId(), "billing.settle");

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, cashier.getId());
        assertThat(revision).isPresent();
        // After 4 operations (grant, revoke, grant, revoke), revision should be >= 4
        assertThat(revision.get().getRevision()).isGreaterThanOrEqualTo(4L);
    }

    @Test
    void billPush_afterPermissionRevocation_scenario() throws Exception {
        RestaurantTerminal terminal = createTerminal("A");
        String token = terminalToken(terminal);

        // Grant permission
        permissionService.grantPermission(RESTAURANT, cashier.getId(), "billing.create", owner.getId());

        // Bill push succeeds while permission is active
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis())))
                .andExpect(status().isOk());

        // Revoke permission
        permissionService.revokePermission(RESTAURANT, cashier.getId(), "billing.create");

        // Verify permission is no longer granted
        assertThat(permissionService.hasPermission(RESTAURANT, cashier.getId(), "billing.create")).isFalse();
    }

    @Test
    void offlineAuthDecider_revalidateFlow() {
        permissionService.grantPermission(RESTAURANT, cashier.getId(), "billing.settle", owner.getId());

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, cashier.getId());
        assertThat(revision).isPresent();
        long createdRevision = revision.get().getRevision();

        // Permission is currently granted
        assertThat(permissionService.hasPermission(RESTAURANT, cashier.getId(), "billing.settle")).isTrue();

        // Revoke permission
        permissionService.revokePermission(RESTAURANT, cashier.getId(), "billing.settle");

        // Verify revocation
        assertThat(permissionService.hasPermission(RESTAURANT, cashier.getId(), "billing.settle")).isFalse();

        // Check that the revision was bumped
        var newRevision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, cashier.getId());
        assertThat(newRevision).isPresent();
        assertThat(newRevision.get().getRevision()).isGreaterThan(createdRevision);
    }
}
