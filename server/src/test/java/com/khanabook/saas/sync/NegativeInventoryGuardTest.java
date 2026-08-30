package com.khanabook.saas.sync;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.ItemRecipe;
import com.khanabook.saas.entity.RawMaterial;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NegativeInventoryGuardTest extends BaseIntegrationTest {

    @Autowired private InventoryService inventoryService;
    @Autowired private RawMaterialRepository rawMaterialRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private BillItemRepository billItemRepository;
    @Autowired private ItemRecipeRepository itemRecipeRepository;

    @Test
    void deductWithSufficientStock_succeeds() {
        RawMaterial material = createMaterial(1L, "Test Oil", "L", new BigDecimal("10.00"));
        Bill savedBill = billRepository.save(createBill(1L));
        billItemRepository.save(createBillItem(savedBill.getId(), savedBill.getId(), 100L, 2));
        createRecipe(1L, 100L, material, new BigDecimal("2.00"));

        inventoryService.deductForFinalizedBill(savedBill);

        RawMaterial r = rawMaterialRepository.findById(material.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("6.00").compareTo(r.getStockQuantity()));
    }

    @Test
    void deductExceedingStock_preventsNegative() {
        RawMaterial material = createMaterial(1L, "Test Spice", "kg", new BigDecimal("1.00"));
        Bill savedBill = billRepository.save(createBill(1L));
        billItemRepository.save(createBillItem(savedBill.getId(), savedBill.getId(), 101L, 5));
        createRecipe(1L, 101L, material, new BigDecimal("1.00"));

        inventoryService.deductForFinalizedBill(savedBill);

        RawMaterial r = rawMaterialRepository.findById(material.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1.00").compareTo(r.getStockQuantity()));
    }

    @Test
    void deductZeroStock_preventsNegative() {
        RawMaterial material = createMaterial(1L, "Empty Item", "pcs", BigDecimal.ZERO);
        Bill savedBill = billRepository.save(createBill(1L));
        billItemRepository.save(createBillItem(savedBill.getId(), savedBill.getId(), 102L, 1));
        createRecipe(1L, 102L, material, new BigDecimal("1.00"));

        inventoryService.deductForFinalizedBill(savedBill);

        RawMaterial r = rawMaterialRepository.findById(material.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getStockQuantity()));
    }

    private RawMaterial createMaterial(Long tenantId, String name, String unit, BigDecimal stock) {
        RawMaterial m = new RawMaterial();
        m.setRestaurantId(tenantId);
        m.setName(name);
        m.setUnit(unit);
        m.setStockQuantity(stock);
        m.setLowStockThreshold(BigDecimal.ZERO);
        m.setIsDeleted(false);
        m.setCreatedAt(System.currentTimeMillis());
        m.setUpdatedAt(System.currentTimeMillis());
        return rawMaterialRepository.save(m);
    }

    private ItemRecipe createRecipe(Long tenantId, Long menuItemId, RawMaterial material, BigDecimal qty) {
        ItemRecipe r = new ItemRecipe();
        r.setRestaurantId(tenantId);
        r.setMenuItemId(menuItemId);
        r.setRawMaterial(material);
        r.setQuantityPerItem(qty);
        r.setIsDeleted(false);
        r.setCreatedAt(System.currentTimeMillis());
        r.setUpdatedAt(System.currentTimeMillis());
        return itemRecipeRepository.save(r);
    }

    private Bill createBill(Long tenantId) {
        Bill b = new Bill();
        long now = System.currentTimeMillis();
        b.setLocalId(System.nanoTime());
        b.setRestaurantId(tenantId);
        b.setDeviceId("test-device");
        b.setPublicToken(UUID.randomUUID());
        b.setDailyOrderId(1L);
        b.setDailyOrderDisplay("D-1");
        b.setOrderType("order");
        b.setSubtotal(new BigDecimal("100.00"));
        b.setTotalAmount(new BigDecimal("100.00"));
        b.setGstPercentage(new BigDecimal("0.0"));
        b.setCgstAmount(new BigDecimal("0.0"));
        b.setSgstAmount(new BigDecimal("0.0"));
        b.setCustomTaxAmount(new BigDecimal("0.0"));
        b.setPartAmount1(new BigDecimal("0.0"));
        b.setPartAmount2(new BigDecimal("0.0"));
        b.setPaymentMode("cash");
        b.setOrderStatus("completed");
        b.setPaymentStatus("paid");
        b.setLastResetDate("");
        b.setIsDeleted(false);
        b.setInventoryDeducted(false);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        b.setServerUpdatedAt(now);
        return b;
    }

    private BillItem createBillItem(Long billId, Long serverBillId, Long menuItemId, int quantity) {
        BillItem i = new BillItem();
        i.setLocalId(System.nanoTime());
        i.setBillId(billId);
        i.setServerBillId(serverBillId);
        i.setRestaurantId(1L);
        i.setDeviceId("test-device");
        i.setMenuItemId(menuItemId);
        i.setItemName("Test Item");
        i.setPrice(new BigDecimal("100.00"));
        i.setQuantity(quantity);
        i.setItemTotal(new BigDecimal(String.valueOf(100 * quantity)));
        i.setIsDeleted(false);
        i.setCreatedAt(System.currentTimeMillis());
        i.setUpdatedAt(System.currentTimeMillis());
        i.setServerUpdatedAt(System.currentTimeMillis());
        return i;
    }
}
