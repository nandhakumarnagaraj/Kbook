package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.menu.data.MenuExtractionJob;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.inventory.data.PurchaseOrder;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderItem;
import com.khanabook.saas.feature.inventory.data.StockMovement;
import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.data.Vendor;
import com.khanabook.saas.feature.inventory.data.CustomerProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.payments.data.Chargeback;
import com.khanabook.saas.feature.notifications.data.NotificationEvent;
import com.khanabook.saas.feature.notifications.data.DeviceToken;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.staff.data.StaffPermission;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevision;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.data.PermissionRequest;
import com.khanabook.saas.feature.staff.data.RoleTemplate;
import com.khanabook.saas.feature.platform.data.FeatureFlag;
import com.khanabook.saas.feature.onboarding.entity.MerchantAgreement;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.RefreshToken;
import com.khanabook.saas.feature.auth.entity.TokenBlocklist;
import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.entity.RateLimitAttempt;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.data.BillItemRepository;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipeRepository;
import com.khanabook.saas.feature.menu.data.MenuExtractionJobRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterialRepository;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderRepository;
import com.khanabook.saas.feature.inventory.data.StockMovementRepository;
import com.khanabook.saas.feature.inventory.data.StockLogRepository;
import com.khanabook.saas.feature.inventory.data.VendorRepository;
import com.khanabook.saas.feature.inventory.data.CustomerProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.ChargebackRepository;
import com.khanabook.saas.feature.payments.data.WebhookRetryJobRepository;
import com.khanabook.saas.feature.notifications.data.NotificationEventRepository;
import com.khanabook.saas.feature.notifications.data.DeviceTokenRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.staff.data.PermissionRequestRepository;
import com.khanabook.saas.feature.staff.data.RoleTemplateRepository;
import com.khanabook.saas.feature.platform.data.FeatureFlagRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import com.khanabook.saas.feature.auth.repository.OtpRequestRepository;
import com.khanabook.saas.feature.auth.repository.RateLimitAttemptRepository;
import com.khanabook.saas.feature.auth.repository.SecurityAuditLogRepository;
import com.khanabook.saas.core.utility.JwtUtility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Distributed state problem B5: Device sells out-of-stock item.
 *
 * Real use case: Terminal A sells an item while offline. Terminal B doesn't
 * know the item is out of stock. Both push bills. Server accepts both because
 * it doesn't validate stock during sync push.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OutOfStockSaleTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 8801L;

    @Autowired private MockMvc mockMvc;
    @Autowired private BillRepository billRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private JwtUtility jwtUtility;
    @Autowired private ObjectMapper objectMapper;

    private String authToken() {
        return persistUserAndGetToken("owner-oos-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
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

    private String billJson(long localId, long updatedAt, String deviceId, String publicToken) {
        String tokenLine = publicToken == null ? "" : "\"publicToken\": \"" + publicToken + "\",";
        return """
            [{
              "localId": %d,
              "deviceId": "%s",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              %s
              "dailyOrderId": %d,
              "dailyOrderDisplay": "%d",
              "lifetimeOrderId": %d,
              "orderType": "dine_in",
              "subtotal": 100.00,
              "totalAmount": 100.00,
              "paymentMode": "cash",
              "paymentStatus": "pending",
              "orderStatus": "draft"
            }]
            """.formatted(localId, deviceId, RESTAURANT, updatedAt, updatedAt,
                    tokenLine, localId, localId, localId);
    }

    @Test
    void billPush_acceptedWithoutStockValidation() throws Exception {
        // B5 scenario: Server has no stock tracking at the bill-sync level.
        // A bill can be pushed with items even if those items are out of stock.
        // The server accepts the bill because it doesn't cross-check inventory
        // during sync push — stock validation is a separate concern.
        RestaurantTerminal tA = createTerminal("A");
        String tokenA = terminalToken(tA);
        String auth = authToken();

        String publicToken = UUID.randomUUID().toString();

        // Push a bill — server should accept it regardless of stock
        var result = mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A", publicToken)))
                .andExpect(status().isOk())
                .andReturn();

        // Parse response to verify bill was saved
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.has("successfulLocalIds")).isTrue();
        assertThat(response.get("successfulLocalIds").size()).isEqualTo(1);

        // Verify bill exists in DB
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
        // Server accepted the bill — no stock validation at sync time
    }

    @Test
    void twoTerminals_concurrentBillPush_bothAccepted() throws Exception {
        // B5 variant: Both terminals sell items simultaneously while offline.
        // When both push, there's no stock validation — both bills go through.
        // This can lead to overselling.
        RestaurantTerminal tA = createTerminal("A");
        RestaurantTerminal tB = createTerminal("B");
        String tokenA = terminalToken(tA);
        String tokenB = terminalToken(tB);
        String auth = authToken();

        // Both push bills for items (different publicTokens = different bills)
        String publicTokenA = UUID.randomUUID().toString();
        String publicTokenB = UUID.randomUUID().toString();

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenA)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A", publicTokenA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + auth)
                .header("X-Terminal-Token", tokenB)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_B", publicTokenB)))
                .andExpect(status().isOk());

        // Both bills accepted — overselling is possible
        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(2);
    }
}
