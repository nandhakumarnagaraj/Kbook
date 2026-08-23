package com.khanabook.saas.service;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.entity.StockMovement;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.ItemRecipeRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RawMaterialRepository;
import com.khanabook.saas.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plan 05 inventory loop: purchase (weighted-avg cost), wastage with mandatory
 * reason, physical-count variance, movement ledger completeness, and
 * zero-stock cascade hiding dependent menu items.
 */
class InventoryLoopServiceTest extends BaseIntegrationTest {

    @Autowired private InventoryService inventoryService;
    @Autowired private RawMaterialRepository rawMaterialRepository;
    @Autowired private ItemRecipeRepository itemRecipeRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private BillItemRepository billItemRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private com.khanabook.saas.repository.CategoryRepository categoryRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private Long restaurant;

    @org.junit.jupiter.api.BeforeEach
    void initRestaurant() {
        restaurant = 9_300_000L + (long) (Math.random() * 100_000);
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

    private RawMaterial seedMaterial(double stock) {
        RawMaterial m = new RawMaterial();
        m.setRestaurantId(restaurant);
        m.setName("Tomato-" + restaurant);
        m.setUnit("kg");
        m.setStockQuantity(BigDecimal.valueOf(stock));
        m.setLowStockThreshold(BigDecimal.ONE);
        long t = System.currentTimeMillis();
        m.setCreatedAt(t);
        m.setUpdatedAt(t);
        return rawMaterialRepository.save(m);
    }

    private MenuItem seedMenuItem(boolean available) {
        var cat = new com.khanabook.saas.entity.Category();
        fillSync(cat);
        cat.setName("Cat" + seq);
        cat.setIsVeg(true);
        cat.setIsActive(true);
        Long categoryId = categoryRepository.save(cat).getId();

        MenuItem mi = new MenuItem();
        fillSync(mi);
        mi.setCategoryId(categoryId);
        mi.setName("Tomato Curry " + seq);
        mi.setBasePrice(BigDecimal.TEN);
        mi.setIsAvailable(available);
        return menuItemRepository.save(mi);
    }

    @Test
    void purchase_updatesWeightedAverageCost_andLedgersIn() {
        RawMaterial m = seedMaterial(10);
        m.setCostPerUnit(new BigDecimal("40"));
        rawMaterialRepository.saveAndFlush(m);

        // Buy 10 more at Rs.60 -> weighted avg should be 50.
        inventoryService.purchase(restaurant, m.getId(), BigDecimal.TEN,
                new BigDecimal("60"), null, null);

        RawMaterial after = rawMaterialRepository.findById(m.getId()).orElseThrow();
        assertThat(after.getStockQuantity()).isEqualByComparingTo("20");
        assertThat(after.getCostPerUnit()).isEqualByComparingTo("50");

        List<StockMovement> moves = stockMovementRepository
                .findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(restaurant, m.getId());
        assertThat(moves).anySatisfy(mv -> {
            assertThat(mv.getKind()).isEqualTo(StockMovement.KIND_PURCHASE);
            assertThat(mv.getQuantity()).isEqualByComparingTo("10");
            assertThat(mv.getUnitCost()).isEqualByComparingTo("60");
        });
    }

    @Test
    void wastage_requiresReason_andDeducts() {
        RawMaterial m = seedMaterial(5);

        assertThatThrownBy(() -> inventoryService.wastage(
                restaurant, m.getId(), BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        inventoryService.wastage(restaurant, m.getId(), new BigDecimal("2"), "Spoilage");
        assertThat(rawMaterialRepository.findById(m.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("3");
    }

    @Test
    void physicalCount_reportsVariance_andAdjustsStock() {
        RawMaterial m = seedMaterial(8);

        var result = inventoryService.adjustPhysicalCount(
                restaurant, m.getId(), new BigDecimal("7"), null);

        assertThat((BigDecimal) result.get("variance")).isEqualByComparingTo("-1");
        assertThat(rawMaterialRepository.findById(m.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("7");
        List<StockMovement> moves = stockMovementRepository
                .findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(restaurant, m.getId());
        assertThat(moves).anySatisfy(mv ->
                assertThat(mv.getKind()).isEqualTo(StockMovement.KIND_ADJUST));
    }

    @Test
    void zeroStock_cascadesToHideDependentMenuItems() {
        RawMaterial m = seedMaterial(2);
        MenuItem item = seedMenuItem(true); // available
        ItemRecipe r = new ItemRecipe();
        r.setRestaurantId(restaurant);
        r.setMenuItemId(item.getId());
        r.setRawMaterial(m);
        r.setQuantityPerItem(BigDecimal.ONE);
        long t = System.currentTimeMillis();
        r.setCreatedAt(t);
        r.setUpdatedAt(t);
        itemRecipeRepository.save(r);

        // Wastage everything -> stock hits zero -> item must be hidden.
        inventoryService.wastage(restaurant, m.getId(), new BigDecimal("2"), "Spoiled");

        MenuItem reloaded = menuItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getIsAvailable()).isFalse();

        // Ledger must contain the WASTAGE row.
        assertThat(stockMovementRepository
                .findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(restaurant, m.getId()))
                .anySatisfy(mv -> mv.getKind().equals(StockMovement.KIND_WASTAGE));
    }

    @Test
    void salesDeduction_writesLedgerRow_perBill() {
        RawMaterial m = seedMaterial(10);
        MenuItem item = seedMenuItem(true);
        ItemRecipe r = new ItemRecipe();
        r.setRestaurantId(restaurant);
        r.setMenuItemId(item.getId());
        r.setRawMaterial(m);
        r.setQuantityPerItem(BigDecimal.valueOf(2));
        long t = System.currentTimeMillis();
        r.setCreatedAt(t);
        r.setUpdatedAt(t);
        itemRecipeRepository.save(r);

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
        final Bill savedBill = bill;

        BillItem bi = new BillItem();
        fillSync(bi);
        bi.setBillId(bill.getId());
        bi.setServerBillId(bill.getId());
        bi.setMenuItemId(item.getId());
        bi.setItemName("X");
        bi.setPrice(BigDecimal.TEN);
        bi.setQuantity(3);
        bi.setItemTotal(BigDecimal.valueOf(30));
        billItemRepository.save(bi);

        inventoryService.deductForFinalizedBill(bill);

        assertThat(rawMaterialRepository.findById(m.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("4"); // 10 - (2*3)
        assertThat(stockMovementRepository
                .findByRestaurantIdAndRawMaterialIdOrderByCreatedAtDesc(restaurant, m.getId()))
                .anySatisfy(mv -> {
                    assertThat(mv.getKind()).isEqualTo(StockMovement.KIND_SALES_DEDUCT);
                    assertThat(mv.getQuantity()).isEqualByComparingTo("-6");
                    assertThat(mv.getBillId()).isEqualTo(savedBill.getId());
                });
    }

    @Test
    void mergedUpdatePush_deductsOnce_andPersistsDeductedFlag() {
        // Reproduces the C2 bug: GenericSyncService saves the bill via merge()
        // (detached payload copy), then calls deduction. The flag must survive
        // commit on the MANAGED instance, and a second push must not re-deduct.
        RawMaterial m = seedMaterial(10);
        MenuItem item = seedMenuItem(true);
        ItemRecipe r = new ItemRecipe();
        r.setRestaurantId(restaurant);
        r.setMenuItemId(item.getId());
        r.setRawMaterial(m);
        r.setQuantityPerItem(BigDecimal.valueOf(5));
        long t = System.currentTimeMillis();
        r.setCreatedAt(t);
        r.setUpdatedAt(t);
        itemRecipeRepository.save(r);

        Bill draft = new Bill();
        fillSync(draft);
        draft.setOrderStatus("draft");
        draft.setPaymentStatus("pending");
        draft.setPaymentMode("cash");
        draft.setOrderType("dine_in");
        draft.setTotalAmount(BigDecimal.TEN);
        draft.setSubtotal(BigDecimal.TEN);
        draft.setLastResetDate(java.time.LocalDate.now().toString());
        draft.setDailyOrderId(seq++);
        Bill savedDraft = billRepository.save(draft);
        Long serverId = savedDraft.getId();

        BillItem bi = new BillItem();
        fillSync(bi);
        bi.setBillId(serverId);
        bi.setServerBillId(serverId);
        bi.setMenuItemId(item.getId());
        bi.setItemName("X");
        bi.setPrice(BigDecimal.TEN);
        bi.setQuantity(1);
        bi.setItemTotal(BigDecimal.TEN);
        billItemRepository.save(bi);
        billItemRepository.flush();

        // Simulate the sync's merge(): detached copy of the bill, updated to paid.
        Bill payload = new Bill();
        fillSync(payload);
        payload.setId(serverId);
        payload.setPublicToken(savedDraft.getPublicToken()); // server-owned, preserved in real flow
        payload.setOrderStatus("completed");
        payload.setPaymentStatus("paid");
        payload.setPaymentMode("cash");
        payload.setOrderType("dine_in");
        payload.setTotalAmount(BigDecimal.TEN);
        payload.setSubtotal(BigDecimal.TEN);
        payload.setLastResetDate(java.time.LocalDate.now().toString());
        payload.setDailyOrderId(draft.getDailyOrderId());
        payload.setUpdatedAt(System.currentTimeMillis() + 1000);
        Bill merged = billRepository.save(payload);   // returns MANAGED copy
        billItemRepository.flush();

        // Deduction runs against the MANAGED instance (the fixed call site pattern).
        billRepository.findById(serverId).ifPresent(inventoryService::deductForFinalizedBill);
        assertThat(rawMaterialRepository.findById(m.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("5");

        // A second push of the same bill must not deduct again.
        inventoryService.deductForFinalizedBill(billRepository.findById(serverId).orElseThrow());

        Bill persisted = billRepository.findById(serverId).orElseThrow();
        assertThat(persisted.getInventoryDeducted()).isTrue();
        assertThat(rawMaterialRepository.findById(m.getId()).orElseThrow()
                .getStockQuantity()).isEqualByComparingTo("5");
    }
}