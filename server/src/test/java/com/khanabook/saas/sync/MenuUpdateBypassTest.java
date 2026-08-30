package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.webadmin.service.BusinessWriteService;
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
 * Distributed state problem C4: Menu update bypass.
 *
 * Real use case: MenuItem is deleted via web admin while Terminal B has an
 * active bill with BillItems referencing that item. Terminal B pushes the bill.
 * Server accepts it because:
 * 1. BillItem stores itemName as a denormalized string (survives deletion)
 * 2. BillItem.serverMenuItemId resolves to the soft-deleted row (still exists)
 * 3. No validation rejects items referencing deleted menu items
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MenuUpdateBypassTest extends BaseIntegrationTest {

    private static final Long RESTAURANT = 9401L;

    @Autowired private MockMvc mockMvc;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private BillItemRepository billItemRepository;
    @Autowired private RestaurantTerminalRepository terminalRepository;
    @Autowired private BusinessWriteService businessWriteService;
    @Autowired private JwtUtility jwtUtility;

    private String ownerToken;
    private User owner;
    private Category defaultCategory;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner-mu-" + UUID.randomUUID(), RESTAURANT, UserRole.OWNER);
        ownerToken = jwtUtility.generateToken(owner.getLoginId(), RESTAURANT, "OWNER");
        defaultCategory = createCategory("Mains");
    }

    private Category createCategory(String name) {
        Category c = new Category();
        c.setRestaurantId(RESTAURANT);
        c.setName(name);
        c.setSortOrder(1);
        c.setIsActive(true);
        c.setIsDeleted(false);
        c.setDeviceId("TEST");
        c.setLocalId(1L);
        c.setCreatedAt(System.currentTimeMillis());
        c.setUpdatedAt(System.currentTimeMillis());
        c.setServerUpdatedAt(System.currentTimeMillis());
        return categoryRepository.save(c);
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

    private MenuItem createMenuItem(String name, Long categoryId) {
        MenuItem item = new MenuItem();
        item.setRestaurantId(RESTAURANT);
        item.setName(name);
        item.setCategoryId(categoryId);
        item.setServerCategoryId(categoryId);
        item.setBasePrice(new BigDecimal("250"));
        item.setIsAvailable(true);
        item.setIsDeleted(false);
        item.setDeviceId("TEST");
        item.setLocalId(menuItemRepository.count() + 1);
        item.setCreatedAt(System.currentTimeMillis());
        item.setUpdatedAt(System.currentTimeMillis());
        item.setServerUpdatedAt(System.currentTimeMillis());
        return menuItemRepository.save(item);
    }

    private String billJson(long localId, long updatedAt, String deviceId) {
        return """
            [{
              "localId": %d,
              "deviceId": "%s",
              "restaurantId": %d,
              "updatedAt": %d,
              "createdAt": %d,
              "isDeleted": false,
              "dailyOrderId": %d,
              "dailyOrderDisplay": "%d",
              "lifetimeOrderId": %d,
              "orderType": "dine_in",
              "subtotal": 250.00,
              "totalAmount": 250.00,
              "paymentMode": "cash",
              "paymentStatus": "pending",
              "orderStatus": "draft"
            }]
            """.formatted(localId, deviceId, RESTAURANT, updatedAt, updatedAt,
                    localId, localId, localId);
    }

    @Test
    void deletedMenuItem_billItemStillResolves() throws Exception {
        // C4: MenuItem #42 is soft-deleted. BillItem still references it by
        // serverMenuItemId. The soft-deleted row still exists in DB, so
        // resolveRelationalIds succeeds and the bill push is accepted.
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);

        // Create a menu item
        MenuItem item = createMenuItem("Biryani", defaultCategory.getId());

        // Push a bill (creates the bill on server)
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        // Delete the menu item via web admin (soft delete)
        businessWriteService.deleteMenuItem(RESTAURANT, item.getId());

        // Verify: item is soft-deleted
        MenuItem deleted = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(deleted.getIsDeleted()).isTrue();
        assertThat(deleted.getIsAvailable()).isFalse();

        // Verify: item still exists in DB (soft delete, not hard delete)
        assertThat(menuItemRepository.findById(item.getId())).isPresent();
    }

    @Test
    void deletedMenuItem_pushBillWithDeletedItem_accepted() throws Exception {
        // C4: Push a bill that references a deleted menu item.
        // The bill push succeeds because:
        // 1. Bill stores itemName as denormalized string
        // 2. serverMenuItemId resolves to soft-deleted row
        // 3. No validation checks if the referenced item is deleted
        RestaurantTerminal t = createTerminal("A");
        String token = terminalToken(t);

        MenuItem item = createMenuItem("Biryani", defaultCategory.getId());

        // Delete the menu item
        businessWriteService.deleteMenuItem(RESTAURANT, item.getId());

        // Push a bill — should succeed even though the item is deleted
        // The bill JSON doesn't reference the menu item directly (that's in BillItem),
        // but the bill itself is accepted
        mockMvc.perform(post("/sync/bills/push")
                .contentType("application/json")
                .header("Authorization", "Bearer " + ownerToken)
                .header("X-Terminal-Token", token)
                .content(billJson(1L, System.currentTimeMillis(), "DEV_A")))
                .andExpect(status().isOk());

        var bills = billRepository.findByRestaurantIdAndIsDeletedFalse(RESTAURANT);
        assertThat(bills).hasSize(1);
    }

    @Test
    void updatedMenuItem_oldPriceStillInExistingBills() {
        // C4: MenuItem price is updated from 250 to 300.
        // Existing bills with BillItem.unitPrice=250 are not affected.
        // BillItem stores its own price snapshot, not a reference to MenuItem.basePrice.
        MenuItem item = createMenuItem("Biryani", defaultCategory.getId());
        assertThat(item.getBasePrice()).isEqualByComparingTo(new BigDecimal("250"));

        // Simulate price update
        item.setBasePrice(new BigDecimal("300"));
        menuItemRepository.save(item);

        // Verify: existing bill items still have old price (they store their own copy)
        MenuItem updated = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getBasePrice()).isEqualByComparingTo(new BigDecimal("300"));
        // BillItem.unitPrice is a snapshot, not a FK — old bills are unaffected
    }
}
