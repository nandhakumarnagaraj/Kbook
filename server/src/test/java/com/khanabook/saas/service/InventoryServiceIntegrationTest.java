package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private void fillSync(com.khanabook.saas.sync.entity.BaseSyncEntity e) {
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

    private String persistStaffToken() {
        return persistUserAndGetToken(
                "inv-staff-" + restaurant + "@test.com", restaurant, UserRole.WAITER);
    }
}
