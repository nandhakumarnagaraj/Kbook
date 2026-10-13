package com.khanabook.saas.service;

import com.khanabook.saas.feature.inventory.service.InventoryService;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.billing.data.BillItemRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipeRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 inventory: recipe deduction on bill finalization is idempotent,
 * crossing the low-stock threshold fires exactly one alert, and material
 * CRUD is tenant-scoped.
 */
@AutoConfigureMockMvc
class InventoryServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired private InventoryService inventoryService;
    @Autowired private RawMaterialRepository rawMaterialRepository;
    @Autowired private ItemRecipeRepository itemRecipeRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BillItemRepository billItemRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private MockMvc mockMvc;

    @MockBean private PushNotificationService pushNotificationService;

    private Long restaurant;

    @org.junit.jupiter.api.BeforeEach
    void initRestaurant() {
        restaurant = 9_100_000L + (long) (Math.random() * 100_000);
    }

    private long seq = 1;

    private void fillSync(com.khanabook.saas.feature.sync.data.BaseSyncEntity e) {
        long t = System.currentTimeMillis();
        e.setLocalId(seq++);
        e.setDeviceId("SEED");
        e.setRestaurantId(restaurant);
        e.setCreatedAt(t);
        e.setUpdatedAt(t);
        e.setServerUpdatedAt(t);
    }

    private RawMaterial seedMaterial(double stock, double threshold) {
        RawMaterial m = new RawMaterial();
        m.setRestaurantId(restaurant);
        m.setName("Paneer-" + restaurant);
        m.setUnit("kg");
        m.setStockQuantity(BigDecimal.valueOf(stock));
        m.setLowStockThreshold(BigDecimal.valueOf(threshold));
        long t = System.currentTimeMillis();
        m.setCreatedAt(t);
        m.setUpdatedAt(t);
        return rawMaterialRepository.save(m);
    }

    private ItemRecipe seedRecipe(Long menuItemId, RawMaterial material, double qtyPerItem) {
        ItemRecipe r = new ItemRecipe();
        r.setRestaurantId(restaurant);
        r.setMenuItemId(menuItemId);
        r.setRawMaterial(material);
        r.setQuantityPerItem(BigDecimal.valueOf(qtyPerItem));
        long t = System.currentTimeMillis();
        r.setCreatedAt(t);
        r.setUpdatedAt(t);
        return itemRecipeRepository.save(r);
    }

    private Bill seedFinalizedBillWithItem(Long menuItemId, int qty) {
        Bill bill = new Bill();
        fillSync(bill);
        bill.setOrderStatus("completed");
        bill.setPaymentStatus("paid");
        bill.setPaymentMode("cash");
        bill.setOrderType("dine_in");
        bill.setTotalAmount(BigDecimal.TEN);
        bill.setSubtotal(BigDecimal.TEN);
        bill.setLastResetDate(java.time.LocalDate.now().toString());
        bill.setDailyOrderId(seq++);
        bill = billRepository.save(bill);

        BillItem item = new BillItem();
        fillSync(item);
        item.setBillId(bill.getId());
        item.setServerBillId(bill.getId());
        item.setMenuItemId(menuItemId);
        item.setItemName("Test Item");
        item.setPrice(BigDecimal.TEN);
        item.setQuantity(qty);
        item.setItemTotal(BigDecimal.valueOf(10L * qty));
        billItemRepository.save(item);
        return bill;
    }

    @Test
    void deduction_isIdempotent_perBill() {
        RawMaterial material = seedMaterial(10, 2);
        seedRecipe(501L, material, 3);
        Bill bill = seedFinalizedBillWithItem(501L, 2); // consumes 6

        inventoryService.deductForFinalizedBill(bill);
        assertThat(rawMaterialRepository.findById(material.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("4");

        // Second call (re-push of same bill) must not deduct again.
        inventoryService.deductForFinalizedBill(bill);
        assertThat(rawMaterialRepository.findById(material.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void crossingLowStockThreshold_firesSingleAlert() {
        RawMaterial material = seedMaterial(4, 2); // 1 unit of 3 -> 1 <= 2 crosses
        seedRecipe(502L, material, 3);
        Bill bill = seedFinalizedBillWithItem(502L, 1);

        inventoryService.deductForFinalizedBill(bill);

        verify(pushNotificationService, times(1)).pushToRestaurant(
                eq(restaurant), contains("Low Stock"), any(), eq("inventory_low"),
                any(), eq("raw_material"), any());
    }

    @Test
    void materials_crud_isTenantScoped_andOwnerGated() throws Exception {
        String ownerToken = persistUserAndGetToken(
                "inv-owner-" + restaurant + "@test.com", restaurant, UserRole.OWNER);

        // Create
        mockMvc.perform(post("/inventory/materials")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flour\",\"unit\":\"kg\",\"stockQuantity\":5,\"lowStockThreshold\":1}"))
                .andExpect(status().isOk());

        // Duplicate name rejected
        mockMvc.perform(post("/inventory/materials")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flour\"}"))
                .andExpect(status().isConflict());

        // Staff (no OWNER role) mutation rejected
        String staffToken = persistStaffToken();
        mockMvc.perform(post("/inventory/materials")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Salt\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * The Item Recipes tab renders the ingredient name and unit, so the endpoint has
     * to send them as values. Returning the JPA entity sent the lazy RawMaterial
     * proxy instead (JacksonConfig writes its identifier), leaving the tab showing a
     * blank ingredient name.
     */
    @Test
    void recipeList_exposesIngredientNameAndUnit() throws Exception {
        RawMaterial material = seedMaterial(5, 1);
        seedRecipe(601L, material, 0.25);
        String ownerToken = persistUserAndGetToken(
                "inv-recipe-owner-" + restaurant + "@test.com", restaurant, UserRole.OWNER);

        mockMvc.perform(get("/inventory/recipes/601")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rawMaterialId").value(material.getId()))
                .andExpect(jsonPath("$[0].materialName").value(material.getName()))
                .andExpect(jsonPath("$[0].unit").value("kg"))
                .andExpect(jsonPath("$[0].quantityPerItem").value(0.25));
    }

    /** A recipe line for another tenant's menu item must not be creatable. */
    @Test
    void recipeCreate_rejectsMenuItemOwnedByAnotherRestaurant() throws Exception {
        RawMaterial material = seedMaterial(3, 1);
        MenuItem foreignItem = seedMenuItemFor(restaurant + 1);
        String ownerToken = persistUserAndGetToken(
                "inv-recipe-guard-" + restaurant + "@test.com", restaurant, UserRole.OWNER);

        mockMvc.perform(post("/inventory/recipes")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":" + foreignItem.getId() + ",\"rawMaterialId\":"
                                + material.getId() + ",\"quantityPerItem\":0.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("menuItemId not found"));

        assertThat(itemRecipeRepository.findByRestaurantIdAndMenuItemIdAndIsDeletedFalse(
                restaurant, foreignItem.getId())).isEmpty();
    }

    /**
     * Purchase orders are answered with DTOs: serializing the entity walked
     * PurchaseOrderItem.purchaseOrder back into the parent and repeated forever, and
     * the owner UI needs the material name/unit per line.
     */
    @Test
    void purchaseOrderEndpoints_serializeDtosNotJpaEntities() throws Exception {
        RawMaterial material = seedMaterial(2, 1);
        String ownerToken = persistUserAndGetToken(
                "inv-po-owner-" + restaurant + "@test.com", restaurant, UserRole.OWNER);

        mockMvc.perform(post("/inventory/purchase-orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"weekly\",\"sendNow\":true,\"items\":[{\"rawMaterialId\":"
                                + material.getId() + ",\"quantity\":3}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.items[0].materialName").value(material.getName()))
                .andExpect(jsonPath("$.items[0].unit").value("kg"));

        mockMvc.perform(get("/inventory/purchase-orders")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].materialName").value(material.getName()))
                .andExpect(jsonPath("$[0].items[0].unit").value("kg"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(3));
    }

    private MenuItem seedMenuItemFor(Long restaurantId) {
        long t = System.currentTimeMillis();
        Category category = new Category();
        category.setRestaurantId(restaurantId);
        category.setLocalId(seq++);
        category.setDeviceId("SEED");
        category.setName("Cat-" + restaurantId);
        category.setIsVeg(true);
        category.setIsActive(true);
        category.setCreatedAt(t);
        category.setUpdatedAt(t);
        category.setServerUpdatedAt(t);
        Category savedCategory = categoryRepository.save(category);

        MenuItem item = new MenuItem();
        item.setRestaurantId(restaurantId);
        item.setLocalId(seq++);
        item.setDeviceId("SEED");
        item.setCategoryId(savedCategory.getId());
        item.setServerCategoryId(savedCategory.getId());
        item.setName("Foreign Item-" + restaurantId);
        item.setBasePrice(BigDecimal.TEN);
        item.setIsAvailable(true);
        item.setCreatedAt(t);
        item.setUpdatedAt(t);
        item.setServerUpdatedAt(t);
        return menuItemRepository.save(item);
    }

    private String persistStaffToken() {
        return persistUserAndGetToken(
                "inv-staff-" + restaurant + "@test.com", restaurant, UserRole.SHOP_STAFF);
    }
}
