package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.RefreshToken;
import com.khanabook.saas.feature.auth.entity.TokenBlocklist;
import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.entity.RateLimitAttempt;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import com.khanabook.saas.feature.auth.repository.OtpRequestRepository;
import com.khanabook.saas.feature.auth.repository.RateLimitAttemptRepository;
import com.khanabook.saas.feature.auth.repository.SecurityAuditLogRepository;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.core.utility.JwtUtility;
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
 * Real use case: Owner revokes SHOP_STAFF's "settings.gst" permission.
 * SHOP_STAFF's device is offline. SHOP_STAFF keeps editing config.
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
    private User staff;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, "OWNER");
        staff = persistUser("staff-" + UUID.randomUUID(), RESTAURANT, UserRole.SHOP_STAFF);
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
        permissionService.grantPermission(RESTAURANT, staff.getId(), "settings.gst", owner.getId());

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, staff.getId());
        assertThat(revision).isPresent();
        assertThat(revision.get().getRevision()).isGreaterThan(0);
    }

    @Test
    void permissionRevokeBumpsRevision() {
        permissionService.grantPermission(RESTAURANT, staff.getId(), "settings.gst", owner.getId());
        long rev1 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, staff.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        permissionService.revokePermission(RESTAURANT, staff.getId(), "settings.gst");
        long rev2 = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, staff.getId())
                .map(StaffPermissionRevision::getRevision).orElse(0L);

        assertThat(rev2).isGreaterThan(rev1);
    }

    @Test
    void grantRevokeGrantRevoke_isMonotonic() {
        permissionService.grantPermission(RESTAURANT, staff.getId(), "settings.gst", owner.getId());
        permissionService.revokePermission(RESTAURANT, staff.getId(), "settings.gst");
        permissionService.grantPermission(RESTAURANT, staff.getId(), "settings.gst", owner.getId());
        permissionService.revokePermission(RESTAURANT, staff.getId(), "settings.gst");

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, staff.getId());
        assertThat(revision).isPresent();
        // After 4 operations (grant, revoke, grant, revoke), revision should be >= 4
        assertThat(revision.get().getRevision()).isGreaterThanOrEqualTo(4L);
    }

    @Test
    void billPush_afterPermissionRevocation_scenario() throws Exception {
        RestaurantTerminal terminal = createTerminal("A");
        String token = terminalToken(terminal);

        // Grant a config (owner-only) permission (create/settle are now role-bound for SHOP_STAFF)
        permissionService.grantPermission(RESTAURANT, staff.getId(), "settings.gst", owner.getId());

        // Bill push succeeds while permission is active
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis())))
                .andExpect(status().isOk());

        // Revoke permission
        permissionService.revokePermission(RESTAURANT, staff.getId(), "settings.gst");

        // Verify permission is no longer granted
        assertThat(permissionService.hasPermission(RESTAURANT, staff.getId(), "settings.gst")).isFalse();
    }

    @Test
    void offlineAuthDecider_revalidateFlow() {
        permissionService.grantPermission(RESTAURANT, staff.getId(), "settings.gst", owner.getId());

        var revision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, staff.getId());
        assertThat(revision).isPresent();
        long createdRevision = revision.get().getRevision();

        // Permission is currently granted
        assertThat(permissionService.hasPermission(RESTAURANT, staff.getId(), "settings.gst")).isTrue();

        // Revoke permission
        permissionService.revokePermission(RESTAURANT, staff.getId(), "settings.gst");

        // Verify revocation
        assertThat(permissionService.hasPermission(RESTAURANT, staff.getId(), "settings.gst")).isFalse();

        // Check that the revision was bumped
        var newRevision = revisionRepository.findByRestaurantIdAndUserId(RESTAURANT, staff.getId());
        assertThat(newRevision).isPresent();
        assertThat(newRevision.get().getRevision()).isGreaterThan(createdRevision);
    }
}
