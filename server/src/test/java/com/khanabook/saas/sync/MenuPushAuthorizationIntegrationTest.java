package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 production-readiness: exercises the REAL authenticated HTTP → security →
 * JWT → MenuItemService → MenuPushAuthorizer → DB path for menu pushes.
 *
 * <h3>IMPORTANT FINDING surfaced by this test (see report)</h3>
 * The security config gates the whole sync surface with
 * {@code requestMatchers("/sync/**").hasRole("OWNER")} (SecurityConfig). So a
 * non-OWNER staff token is rejected with 403 at the route layer, BEFORE reaching
 * MenuItemServiceImpl / MenuPushAuthorizer. Consequently the fine-grained
 * per-user menu-permission enforcement only executes for OWNER today (who always
 * passes). These tests assert the ACTUAL current contract rather than the intended
 * staff-level enforcement, which is not reachable over HTTP under the current
 * security model. Whether to allow non-OWNER roles on /sync/menuitem/** is a
 * separate security decision (P1), not changed here.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MenuPushAuthorizationIntegrationTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 8701L;

    @Autowired private MockMvc mockMvc;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PermissionService permissionService;
    @Autowired private StaffPermissionRevisionRepository revisionRepository;
    @Autowired private JwtUtility jwtUtility;

    private User owner;
    private User cashier;
    private String ownerToken;
    private String cashierToken;
    private Category category;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        cashier = persistUser("cashier-" + UUID.randomUUID(), RESTAURANT, UserRole.CASHIER);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, UserRole.OWNER.name());
        cashierToken = jwtUtility.generateToken(cashier.getLoginId(), RESTAURANT, UserRole.CASHIER.name());
        category = createCategory();
    }

    private Category createCategory() {
        Category c = new Category();
        c.setRestaurantId(RESTAURANT);
        c.setName("Mains");
        c.setSortOrder(1);
        c.setIsActive(true);
        c.setIsDeleted(false);
        c.setDeviceId("DEV_A");
        c.setLocalId(1L);
        c.setCreatedAt(System.currentTimeMillis());
        c.setUpdatedAt(System.currentTimeMillis());
        c.setServerUpdatedAt(System.currentTimeMillis());
        return categoryRepository.save(c);
    }

    private MenuItem createServerMenuItem(BigDecimal price, boolean available) {
        MenuItem item = new MenuItem();
        item.setRestaurantId(RESTAURANT);
        item.setName("Biryani");
        item.setCategoryId(category.getId());
        item.setServerCategoryId(category.getId());
        item.setBasePrice(price);
        item.setIsAvailable(available);
        item.setIsDeleted(false);
        item.setDeviceId("DEV_A");
        item.setLocalId(1000L);
        item.setCreatedAt(System.currentTimeMillis());
        item.setUpdatedAt(System.currentTimeMillis() - 60_000L);
        item.setServerUpdatedAt(System.currentTimeMillis() - 60_000L);
        return menuItemRepository.save(item);
    }

    private String priceChangeJson(MenuItem existing, BigDecimal newPrice, Long revisionAtCreation) {
        String revField = revisionAtCreation == null ? "" :
                ("\"permissionRevisionAtCreation\": " + revisionAtCreation + ",");
        return """
            [{
              "serverId": %d,
              "localId": 1000,
              "deviceId": "DEV_A",
              "restaurantId": %d,
              "categoryId": %d,
              "serverCategoryId": %d,
              "name": "Biryani",
              "basePrice": %s,
              "foodType": "veg",
              "isAvailable": true,
              %s
              "createdAt": %d,
              "updatedAt": %d,
              "isDeleted": false,
              "serverUpdatedAt": 0
            }]
            """.formatted(existing.getId(), RESTAURANT, category.getId(), category.getId(),
                    newPrice.toPlainString(), revField,
                    System.currentTimeMillis(), System.currentTimeMillis());
    }

    // ── FINDING: non-OWNER staff cannot even reach the menu push endpoint ───────

    @Test
    void staffToken_isBlockedAtRouteLayer_beforeAuthorizer() throws Exception {
        MenuItem existing = createServerMenuItem(new BigDecimal("250.00"), true);
        // Even WITH the permission granted, a CASHIER is blocked by SecurityConfig
        // (/sync/** requires OWNER) — the request never reaches MenuPushAuthorizer.
        permissionService.grantPermission(RESTAURANT, cashier.getId(),
                PermissionKey.MENU_EDIT_PRICE.getKey(), owner.getId());

        mockMvc.perform(post("/sync/menuitem/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + cashierToken)
                .content(priceChangeJson(existing, new BigDecimal("300.00"), null)))
                .andExpect(status().isForbidden());

        MenuItem after = menuItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getBasePrice()).isEqualByComparingTo(new BigDecimal("250.00")); // unchanged
    }

    // ── OWNER path: reaches the service, authorizer allows (OWNER has all perms) ─

    @Test
    void ownerToken_priceChange_reachesServiceAndIsApplied() throws Exception {
        MenuItem existing = createServerMenuItem(new BigDecimal("250.00"), true);

        mockMvc.perform(post("/sync/menuitem/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .content(priceChangeJson(existing, new BigDecimal("300.00"), null)))
                .andExpect(status().isOk());

        MenuItem after = menuItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getBasePrice()).isEqualByComparingTo(new BigDecimal("300.00")); // applied
    }

    @Test
    void ownerToken_availabilityChange_reachesServiceAndIsApplied() throws Exception {
        MenuItem existing = createServerMenuItem(new BigDecimal("250.00"), true);
        String body = """
            [{
              "serverId": %d, "localId": 1000, "deviceId": "DEV_A", "restaurantId": %d,
              "categoryId": %d, "serverCategoryId": %d, "name": "Biryani", "basePrice": 250.00,
              "foodType": "veg", "isAvailable": false,
              "createdAt": %d, "updatedAt": %d, "isDeleted": false, "serverUpdatedAt": 0
            }]
            """.formatted(existing.getId(), RESTAURANT, category.getId(), category.getId(),
                    System.currentTimeMillis(), System.currentTimeMillis());

        mockMvc.perform(post("/sync/menuitem/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .content(body))
                .andExpect(status().isOk());

        MenuItem after = menuItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getIsAvailable()).isFalse(); // applied
    }
}
