package com.khanabook.saas.service;

import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.StockLog;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.StockLogRepository;
import com.khanabook.saas.service.impl.StockLogServiceImpl;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockLogServiceImplTest {

    @Mock private StockLogRepository stockLogRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private ItemVariantRepository itemVariantRepository;

    private GenericSyncService genericSyncService;
    private StockLogServiceImpl service;

    private static final Long TENANT_ID = 10L;
    private static final String DEVICE = "TAB_1";

    @BeforeEach
    void setUp() {
        genericSyncService = new GenericSyncService();
        service = new StockLogServiceImpl(
            stockLogRepository, menuItemRepository, itemVariantRepository, genericSyncService
        );
    }

    

    @Test
    void push_resolvesMenuItemByDeviceAndLocalId() {
        MenuItem mi = menuItem(100L);
        StockLog sl = stockLog(1L, 1000L, 5L, null);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 5L))
            .thenReturn(Optional.of(mi));
        stubSyncService();

        service.pushData(TENANT_ID, List.of(sl));

        assertThat(sl.getServerMenuItemId()).isEqualTo(100L);
    }

    @Test
    void push_fallsBackToServerIdWhenLocalLookupFails() {
        MenuItem mi = menuItem(200L);
        mi.setRestaurantId(TENANT_ID);
        StockLog sl = stockLog(1L, 1000L, 5L, null);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(menuItemRepository.findById(5L)).thenReturn(Optional.of(mi));
        stubSyncService();

        service.pushData(TENANT_ID, List.of(sl));

        assertThat(sl.getServerMenuItemId()).isEqualTo(200L);
    }

    @Test
    void push_menuItemNotFound_addedToFailedIds() {
        StockLog sl = stockLog(1L, 1000L, 5L, null);
        sl.setLocalId(99L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(menuItemRepository.findById(anyLong())).thenReturn(Optional.empty());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(sl));

        assertThat(resp.getFailedLocalIds()).contains(99L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(99L);
    }

    @Test
    void push_menuItemWrongTenant_addedToFailedIds() {
        MenuItem mi = menuItem(200L);
        mi.setRestaurantId(999L); 
        StockLog sl = stockLog(1L, 1000L, 5L, null);
        sl.setLocalId(88L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(menuItemRepository.findById(5L)).thenReturn(Optional.of(mi));

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(sl));

        assertThat(resp.getFailedLocalIds()).contains(88L);
    }

    

    @Test
    void push_resolvesVariantWhenPresent() {
        MenuItem mi = menuItem(100L);
        ItemVariant iv = itemVariant(50L, TENANT_ID);
        StockLog sl = stockLog(1L, 1000L, 5L, 10L);
        sl.setServerMenuItemId(100L); 

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.of(mi));
        when(itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(iv));
        stubSyncService();

        service.pushData(TENANT_ID, List.of(sl));

        assertThat(sl.getServerVariantId()).isEqualTo(50L);
    }

    @Test
    void push_variantIdZero_variantResolutionSkipped() {
        MenuItem mi = menuItem(100L);
        StockLog sl = stockLog(1L, 1000L, 5L, 0L); 
        sl.setServerMenuItemId(100L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.of(mi));
        stubSyncService();

        service.pushData(TENANT_ID, List.of(sl));

        verifyNoInteractions(itemVariantRepository);
        assertThat(sl.getServerVariantId()).isNull();
    }

    

    @Test
    void push_successfulSync_triggersRecalculateForAffectedItems() {
        MenuItem mi = menuItem(100L);
        StockLog sl = stockLog(1L, 1000L, 5L, null);
        sl.setLocalId(1L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.of(mi));

        
        when(stockLogRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(stockLogRepository).saveAll(any());

        service.pushData(TENANT_ID, List.of(sl));

        verify(menuItemRepository).recalculateStock(100L);
    }

    @Test
    void push_failedResolution_doesNotTriggerRecalculate() {
        StockLog sl = stockLog(1L, 1000L, 5L, null);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(menuItemRepository.findById(anyLong())).thenReturn(Optional.empty());

        service.pushData(TENANT_ID, List.of(sl));

        verify(menuItemRepository, never()).recalculateStock(anyLong());
    }

    @Test
    void push_variantAffected_recalculatesVariantStock() {
        MenuItem mi = menuItem(100L);
        ItemVariant iv = itemVariant(50L, TENANT_ID);
        StockLog sl = stockLog(1L, 1000L, 5L, 10L);
        sl.setLocalId(1L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.of(mi));
        when(itemVariantRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), eq(10L)))
            .thenReturn(Optional.of(iv));
        when(stockLogRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(stockLogRepository).saveAll(any());

        service.pushData(TENANT_ID, List.of(sl));

        verify(itemVariantRepository).recalculateStock(50L);
    }

    @Test
    void push_multipleLogsForSameItem_recalculatesOnce() {
        MenuItem mi = menuItem(100L);
        StockLog sl1 = stockLog(1L, 1000L, 5L, null);
        sl1.setLocalId(1L);
        StockLog sl2 = stockLog(2L, 2000L, 5L, null);
        sl2.setLocalId(2L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.of(mi));
        when(stockLogRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(stockLogRepository).saveAll(any());

        service.pushData(TENANT_ID, List.of(sl1, sl2));

        
        verify(menuItemRepository, times(1)).recalculateStock(100L);
    }

    

    @Test
    void push_mixedBatch_failedAndSuccessfulPartitioned() {
        MenuItem mi = menuItem(100L);
        StockLog good = stockLog(1L, 1000L, 5L, null);
        good.setLocalId(1L);
        StockLog bad = stockLog(2L, 2000L, 99L, null); 
        bad.setLocalId(2L);

        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 5L))
            .thenReturn(Optional.of(mi));
        when(menuItemRepository.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 99L))
            .thenReturn(Optional.empty());
        when(menuItemRepository.findById(99L)).thenReturn(Optional.empty());
        when(stockLogRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(stockLogRepository).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(good, bad));

        assertThat(resp.getSuccessfulLocalIds()).contains(1L);
        assertThat(resp.getFailedLocalIds()).contains(2L);
    }

    

    private StockLog stockLog(long localId, long updatedAt, long menuItemId, Long variantId) {
        StockLog sl = new StockLog();
        sl.setLocalId(localId);
        sl.setUpdatedAt(updatedAt);
        sl.setDeviceId(DEVICE);
        sl.setRestaurantId(TENANT_ID);
        sl.setMenuItemId(menuItemId);
        sl.setVariantId(variantId);
        sl.setReason("sale");
        sl.setDelta(java.math.BigDecimal.ONE);
        return sl;
    }

    private MenuItem menuItem(Long serverId) {
        MenuItem mi = new MenuItem();
        mi.setId(serverId);
        mi.setRestaurantId(TENANT_ID);
        return mi;
    }

    private ItemVariant itemVariant(Long serverId, Long tenantId) {
        ItemVariant iv = new ItemVariant();
        iv.setId(serverId);
        iv.setRestaurantId(tenantId);
        return iv;
    }

    private void stubSyncService() {
        when(stockLogRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(stockLogRepository).saveAll(any());
    }
}
