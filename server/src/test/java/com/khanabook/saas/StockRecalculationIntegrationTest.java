package com.khanabook.saas;

import com.khanabook.saas.entity.*;
import com.khanabook.saas.repository.*;
import com.khanabook.saas.service.StockLogService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;


@Transactional
class StockRecalculationIntegrationTest extends BaseIntegrationTest {

    @Autowired StockLogService stockLogService;
    @Autowired MenuItemRepository menuItemRepo;
    @Autowired ItemVariantRepository itemVariantRepo;
    @Autowired StockLogRepository stockLogRepo;
    @Autowired CategoryRepository categoryRepo;

    private static final Long TENANT = 2001L;
    private static final String DEVICE_A = "PHONE_A";
    private static final String DEVICE_B = "PHONE_B";

    private Long savedMenuItemId;

    @BeforeEach
    void setUp() {
        stockLogRepo.deleteAll();
        itemVariantRepo.deleteAll();
        menuItemRepo.deleteAll();
        categoryRepo.deleteAll();

        Category cat = category(TENANT, DEVICE_A, 1L);
        Category savedCat = categoryRepo.save(cat);

        MenuItem item = menuItem(TENANT, DEVICE_A, 1L, savedCat.getId());
        item.setCurrentStock(BigDecimal.ZERO);
        savedMenuItemId = menuItemRepo.save(item).getId();
    }

    @Test
    void initialStock_setViaStockLog_recalculatedCorrectly() {
        StockLog initial = stockLog(1L, DEVICE_A, 1L, 0L, new BigDecimal("50.00"), "initial");
        initial.setServerMenuItemId(savedMenuItemId);

        PushSyncResponse resp = stockLogService.pushData(TENANT, List.of(initial));

        assertThat(resp.getSuccessfulLocalIds()).contains(1L);
        MenuItem updated = menuItemRepo.findById(savedMenuItemId).orElseThrow();
        assertThat(updated.getCurrentStock()).isEqualByComparingTo("50.00");
    }

    @Test
    void saleThenAdjustment_stockCalculatesCorrectly() {
        StockLog init = stockLog(1L, DEVICE_A, 1L, 0L, new BigDecimal("100.00"), "initial");
        init.setServerMenuItemId(savedMenuItemId);
        StockLog sale = stockLog(2L, DEVICE_A, 1L, 0L, new BigDecimal("-3.00"), "sale");
        sale.setServerMenuItemId(savedMenuItemId);

        stockLogService.pushData(TENANT, List.of(init));
        stockLogService.pushData(TENANT, List.of(sale));

        MenuItem updated = menuItemRepo.findById(savedMenuItemId).orElseThrow();
        assertThat(updated.getCurrentStock()).isEqualByComparingTo("97.00");
    }

    @Test
    void multiDeviceStockLogs_bothContributedToTotal() {
        
        StockLog fromA = stockLog(1L, DEVICE_A, 1L, 0L, new BigDecimal("50.00"), "initial");
        fromA.setServerMenuItemId(savedMenuItemId);

        
        StockLog fromB = stockLog(1L, DEVICE_B, 1L, 0L, new BigDecimal("-5.00"), "sale");
        fromB.setServerMenuItemId(savedMenuItemId);

        stockLogService.pushData(TENANT, List.of(fromA));
        stockLogService.pushData(TENANT, List.of(fromB));

        
        MenuItem updated = menuItemRepo.findById(savedMenuItemId).orElseThrow();
        assertThat(updated.getCurrentStock()).isEqualByComparingTo("45.00");
    }

    @Test
    void stockLog_isDeletedSoftly_doesNotAffectCurrentStock() {
        
        StockLog init = stockLog(1L, DEVICE_A, 1L, 0L, new BigDecimal("20.00"), "initial");
        init.setServerMenuItemId(savedMenuItemId);
        stockLogService.pushData(TENANT, List.of(init));

        
        StockLog deletedSale = stockLog(2L, DEVICE_A, 1L, 0L, new BigDecimal("-5.00"), "sale");
        deletedSale.setServerMenuItemId(savedMenuItemId);
        deletedSale.setIsDeleted(true);
        stockLogService.pushData(TENANT, List.of(deletedSale));

        
        
        
        MenuItem updated = menuItemRepo.findById(savedMenuItemId).orElseThrow();
        
        
        assertThat(updated.getCurrentStock())
            .isEqualByComparingTo("20.00"); 
    }

    @Test
    void weightBasedItem_fractionalDelta_preservedWithFourDecimals() {
        StockLog init = stockLog(1L, DEVICE_A, 1L, 0L, new BigDecimal("10.2500"), "initial");
        init.setServerMenuItemId(savedMenuItemId);
        StockLog sale = stockLog(2L, DEVICE_A, 1L, 0L, new BigDecimal("-0.1250"), "sale");
        sale.setServerMenuItemId(savedMenuItemId);

        stockLogService.pushData(TENANT, List.of(init));
        stockLogService.pushData(TENANT, List.of(sale));

        MenuItem updated = menuItemRepo.findById(savedMenuItemId).orElseThrow();
        assertThat(updated.getCurrentStock()).isEqualByComparingTo("10.1250");
    }

    @Test
    void variantStock_recalculatedIndependentlyFromMenuItemStock() {
        ItemVariant variant = variant(TENANT, DEVICE_A, 1L, savedMenuItemId);
        variant.setCurrentStock(BigDecimal.ZERO);
        Long variantId = itemVariantRepo.save(variant).getId();

        StockLog variantLog = stockLog(1L, DEVICE_A, 1L, 1L, new BigDecimal("30.00"), "initial");
        variantLog.setServerMenuItemId(savedMenuItemId);
        variantLog.setServerVariantId(variantId);

        stockLogService.pushData(TENANT, List.of(variantLog));

        ItemVariant updatedVariant = itemVariantRepo.findById(variantId).orElseThrow();
        assertThat(updatedVariant.getCurrentStock()).isEqualByComparingTo("30.00");

        
        MenuItem updatedItem = menuItemRepo.findById(savedMenuItemId).orElseThrow();
        assertThat(updatedItem.getCurrentStock()).isEqualByComparingTo("30.00");
    }

    

    private StockLog stockLog(long localId, String device, long menuItemLocalId,
                               long variantLocalId, BigDecimal delta, String reason) {
        StockLog sl = new StockLog();
        sl.setLocalId(localId);
        sl.setDeviceId(device);
        sl.setRestaurantId(TENANT);
        sl.setUpdatedAt(System.currentTimeMillis());
        sl.setServerUpdatedAt(0L);
        sl.setCreatedAt(System.currentTimeMillis());
        sl.setIsDeleted(false);
        sl.setMenuItemId(menuItemLocalId);
        sl.setVariantId(variantLocalId);
        sl.setDelta(delta);
        sl.setReason(reason);
        return sl;
    }

    private Category category(Long tenantId, String deviceId, long localId) {
        Category c = new Category();
        c.setRestaurantId(tenantId);
        c.setDeviceId(deviceId);
        c.setLocalId(localId);
        c.setUpdatedAt(1000L);
        c.setServerUpdatedAt(1000L);
        c.setCreatedAt(1000L);
        c.setIsDeleted(false);
        c.setName("Food");
        c.setIsVeg(true);
        c.setIsActive(true);
        return c;
    }

    private MenuItem menuItem(Long tenantId, String deviceId, long localId, Long serverCategoryId) {
        MenuItem m = new MenuItem();
        m.setRestaurantId(tenantId);
        m.setDeviceId(deviceId);
        m.setLocalId(localId);
        m.setUpdatedAt(1000L);
        m.setServerUpdatedAt(1000L);
        m.setCreatedAt(1000L);
        m.setIsDeleted(false);
        m.setCategoryId(1L);
        m.setServerCategoryId(serverCategoryId);
        m.setName("Samosa");
        m.setBasePrice(new BigDecimal("15.00"));
        m.setIsAvailable(true);
        return m;
    }

    private ItemVariant variant(Long tenantId, String deviceId, long localId, Long serverMenuItemId) {
        ItemVariant v = new ItemVariant();
        v.setRestaurantId(tenantId);
        v.setDeviceId(deviceId);
        v.setLocalId(localId);
        v.setUpdatedAt(1000L);
        v.setServerUpdatedAt(1000L);
        v.setCreatedAt(1000L);
        v.setIsDeleted(false);
        v.setMenuItemId(1L);
        v.setServerMenuItemId(serverMenuItemId);
        v.setVariantName("Small");
        v.setPrice(new BigDecimal("10.00"));
        v.setIsAvailable(true);
        return v;
    }
}
