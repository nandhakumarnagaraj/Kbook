package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.utility.JwtUtility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * JWT → MenuItemService → DB path for menu pushes.
 *
 * <h3>Master data is single-writer</h3>
 * Only the roles OWNER / KBOOK_ADMIN may write the menu. Staff
 * terminals are offline-first bill-mints that READ the cached menu; a staff
 * push is denied per record via {@code failedReasons} inside a 200 batch (the
 * device sync loop keeps running) — even when the actor holds a {@code menu.*}
 * grant, which is now advisory/UI-only.
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
    @Autowired private ObjectMapper objectMapper;

    private User owner;
    private User staff;
    private String ownerToken;
    private String staffToken;
    private Category category;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        staff = persistUser("staff-" + UUID.randomUUID(), RESTAURANT, UserRole.SHOP_STAFF);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, UserRole.OWNER.name());
        staffToken = jwtUtility.generateToken(staff.getLoginId(), RESTAURANT, UserRole.SHOP_STAFF.name());
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

// ── FINDING FIXED: staff (even with a menu.* grant) cannot write master data.
//    The pen is role-bound — denial surfaces via failedReasons, not a 403. ──

    @Test
    void staff_isBlockedByRoleBoundWriterGate() throws Exception {
        MenuItem existing = createServerMenuItem(new BigDecimal("250.00"), true);

        var result = mockMvc.perform(post("/sync/menuitem/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + staffToken)
                .content(priceChangeJson(existing, new BigDecimal("300.00"), null)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode failedReasons = objectMapper.readTree(result.getResponse().getContentAsString()).get("failedReasons");
        assertThat(failedReasons).isNotNull();
        assertThat(failedReasons.has("1000")).isTrue();
        assertThat(failedReasons.get("1000").asText()).contains("restaurant owner");

        MenuItem after = menuItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getBasePrice()).isEqualByComparingTo(new BigDecimal("250.00")); // unchanged
    }

    @Test
    void staff_withMenuEditPriceGrant_stillBlocked_staffIsNotAWriter() throws Exception {
        // menu.* grants are advisory (UI-only) now: the role is the pen.
        MenuItem existing = createServerMenuItem(new BigDecimal("250.00"), true);
        permissionService.grantPermission(RESTAURANT, staff.getId(),
                PermissionKey.MENU_EDIT_PRICE.getKey(), owner.getId());

        var result = mockMvc.perform(post("/sync/menuitem/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + staffToken)
                .content(priceChangeJson(existing, new BigDecimal("300.00"), null)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode failedReasons = objectMapper.readTree(result.getResponse().getContentAsString()).get("failedReasons");
        assertThat(failedReasons.has("1000")).isTrue();
        assertThat(failedReasons.get("1000").asText()).contains("restaurant owner");

        MenuItem after = menuItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getBasePrice()).isEqualByComparingTo(new BigDecimal("250.00")); // unchanged
    }

    // ── OWNER path: reaches the service, role-bound writer → applied ─────────

    @Test
    void kbookAdmin_cannotPushMenuViaDeviceSyncChannel() throws Exception {
        // KBOOK_ADMIN is a master-data writer by role, but the device sync channel
        // (/sync/**) is restaurant-role only: OWNER or SHOP_STAFF. Platform admins
        // mutate menu via platform tooling, not a restaurant terminal.
        User admin = persistUser("kbook-" + UUID.randomUUID(), RESTAURANT, UserRole.KBOOK_ADMIN);
        String adminToken = jwtUtility.generateToken(admin.getLoginId(), RESTAURANT, UserRole.KBOOK_ADMIN.name());
        MenuItem existing = createServerMenuItem(new BigDecimal("250.00"), true);

        mockMvc.perform(post("/sync/menuitem/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + adminToken)
                .content(priceChangeJson(existing, new BigDecimal("300.00"), null)))
                .andExpect(status().isForbidden());
    }

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
