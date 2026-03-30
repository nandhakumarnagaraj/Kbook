package com.khanabook.saas;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillItem;
import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.BillItemRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;


@Transactional
class SyncRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired BillRepository billRepo;
    @Autowired BillItemRepository billItemRepo;
    @Autowired CategoryRepository categoryRepo;
    @Autowired MenuItemRepository menuItemRepo;

    private static final Long TENANT_A = 1001L;
    private static final Long TENANT_B = 1002L;

    @BeforeEach
    void clean() {
        billItemRepo.deleteAll();
        billRepo.deleteAll();
        menuItemRepo.deleteAll();
        categoryRepo.deleteAll();
    }

    

    @Test
    void pullByServerUpdatedAt_returnsOnlyNewerRecords() {
        Bill old = bill(TENANT_A, "DEV_A", 1L, 1000L, 1000L);
        Bill recent = bill(TENANT_A, "DEV_A", 2L, 5000L, 5000L);
        billRepo.saveAll(List.of(old, recent));

        List<Bill> pulled = billRepo
            .findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(TENANT_A, 2000L, "OTHER");

        assertThat(pulled).hasSize(1);
        assertThat(pulled.get(0).getLocalId()).isEqualTo(2L);
    }

    @Test
    void pull_excludesRequestingDevice() {
        
        Bill b = bill(TENANT_A, "TABLET_1", 1L, 1000L, 1000L);
        billRepo.save(b);

        List<Bill> pulled = billRepo
            .findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(TENANT_A, 0L, "TABLET_1");

        assertThat(pulled).isEmpty();
    }

    @Test
    void pull_includesOtherDevicesRecords() {
        Bill fromDeviceB = bill(TENANT_A, "TABLET_2", 1L, 1000L, 1000L);
        billRepo.save(fromDeviceB);

        List<Bill> pulled = billRepo
            .findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(TENANT_A, 0L, "TABLET_1");

        assertThat(pulled).hasSize(1);
    }

    

    @Test
    void pull_strictlyIsolatedByTenant() {
        Bill tenantABill = bill(TENANT_A, "DEV_A", 1L, 1000L, 1000L);
        Bill tenantBBill = bill(TENANT_B, "DEV_B", 1L, 1000L, 1000L);
        billRepo.saveAll(List.of(tenantABill, tenantBBill));

        List<Bill> tenantAPull = billRepo
            .findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(TENANT_A, 0L, "OTHER");
        List<Bill> tenantBPull = billRepo
            .findByRestaurantIdAndServerUpdatedAtGreaterThanAndDeviceIdNot(TENANT_B, 0L, "OTHER");

        assertThat(tenantAPull).hasSize(1);
        assertThat(tenantBPull).hasSize(1);
        assertThat(tenantAPull.get(0).getRestaurantId()).isEqualTo(TENANT_A);
        assertThat(tenantBPull.get(0).getRestaurantId()).isEqualTo(TENANT_B);
    }

    @Test
    void upsertLookup_findByRestaurantAndDeviceAndLocalId_exactMatch() {
        Bill b = bill(TENANT_A, "TABLET_1", 42L, 1000L, 1000L);
        billRepo.save(b);

        var found = billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_A, "TABLET_1", 42L);
        assertThat(found).isPresent();

        
        var wrongTenant = billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_B, "TABLET_1", 42L);
        assertThat(wrongTenant).isEmpty();

        
        var wrongDevice = billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_A, "TABLET_X", 42L);
        assertThat(wrongDevice).isEmpty();
    }

    

    @Test
    void billItem_withNullServerBillId_savesSuccessfully() {
        
        BillItem item = billItem(TENANT_A, "DEV_A", 1L, null);
        assertThatNoException().isThrownBy(() -> billItemRepo.save(item));
    }

    @Test
    void billItem_withValidServerBillId_savesSuccessfully() {
        Bill parent = bill(TENANT_A, "DEV_A", 1L, 1000L, 1000L);
        Bill saved = billRepo.save(parent);

        BillItem item = billItem(TENANT_A, "DEV_A", 1L, saved.getId());
        assertThatNoException().isThrownBy(() -> billItemRepo.save(item));
    }

    @Test
    void billItem_withInvalidServerBillId_violatesFkConstraint() {
        Long nonExistentBillId = 999999L;
        BillItem item = billItem(TENANT_A, "DEV_A", 1L, nonExistentBillId);

        
        assertThatThrownBy(() -> {
            billItemRepo.save(item);
            billItemRepo.flush(); 
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    

    @Test
    void menuItem_withValidServerCategoryId_savesSuccessfully() {
        Category cat = category(TENANT_A, "DEV_A", 1L);
        Category savedCat = categoryRepo.save(cat);

        MenuItem item = menuItem(TENANT_A, "DEV_A", 1L, savedCat.getId());
        assertThatNoException().isThrownBy(() -> menuItemRepo.save(item));
    }

    @Test
    void menuItem_withInvalidServerCategoryId_violatesFkConstraint() {
        MenuItem item = menuItem(TENANT_A, "DEV_A", 1L, 999999L);

        assertThatThrownBy(() -> {
            menuItemRepo.save(item);
            menuItemRepo.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    

    @Test
    void findByLocalIdIn_returnsAllMatchingRecords() {
        Bill b1 = bill(TENANT_A, "DEV_A", 1L, 1000L, 1000L);
        Bill b2 = bill(TENANT_A, "DEV_A", 2L, 2000L, 2000L);
        Bill b3 = bill(TENANT_A, "DEV_A", 3L, 3000L, 3000L);
        billRepo.saveAll(List.of(b1, b2, b3));

        List<Bill> found = billRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(
            TENANT_A, "DEV_A", List.of(1L, 3L));

        assertThat(found).hasSize(2);
        assertThat(found.stream().map(Bill::getLocalId))
            .containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void findByLocalIdIn_emptyList_returnsEmpty() {
        List<Bill> found = billRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(
            TENANT_A, "DEV_A", List.of());
        assertThat(found).isEmpty();
    }

    

    private Bill bill(Long tenantId, String deviceId, long localId, long updatedAt, long serverUpdatedAt) {
        Bill b = new Bill();
        b.setRestaurantId(tenantId);
        b.setDeviceId(deviceId);
        b.setLocalId(localId);
        b.setUpdatedAt(updatedAt);
        b.setServerUpdatedAt(serverUpdatedAt);
        b.setCreatedAt(updatedAt);
        b.setDailyOrderId(localId);
        b.setLifetimeOrderId(localId);
        b.setOrderType("DINE_IN");
        b.setSubtotal(BigDecimal.TEN);
        b.setTotalAmount(BigDecimal.TEN);
        b.setPaymentMode("CASH");
        b.setPaymentStatus("PAID");
        b.setOrderStatus("COMPLETED");
        b.setIsDeleted(false);
        b.setLastResetDate("2026-03-16");
        return b;
    }

    private BillItem billItem(Long tenantId, String deviceId, long localId, Long serverBillId) {
        BillItem bi = new BillItem();
        bi.setRestaurantId(tenantId);
        bi.setDeviceId(deviceId);
        bi.setLocalId(localId);
        bi.setUpdatedAt(1000L);
        bi.setServerUpdatedAt(1000L);
        bi.setCreatedAt(1000L);
        bi.setIsDeleted(false);
        bi.setBillId(1L);
        bi.setServerBillId(serverBillId);
        bi.setMenuItemId(1L);
        bi.setItemName("Chai");
        bi.setQuantity(1);
        bi.setPrice(BigDecimal.TEN);
        bi.setItemTotal(BigDecimal.TEN);
        return bi;
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
        c.setName("Beverages");
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
        m.setName("Chai");
        m.setBasePrice(BigDecimal.TEN);
        m.setIsAvailable(true);
        return m;
    }
}
