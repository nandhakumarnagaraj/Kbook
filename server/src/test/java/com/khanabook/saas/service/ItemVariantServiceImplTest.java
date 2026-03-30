package com.khanabook.saas.service;

import com.khanabook.saas.entity.ItemVariant;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.service.impl.ItemVariantServiceImpl;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemVariantServiceImplTest {

    @Mock private ItemVariantRepository itemVariantRepo;
    @Mock private MenuItemRepository menuItemRepo;

    private GenericSyncService genericSyncService;
    private ItemVariantServiceImpl service;

    private static final Long TENANT_ID = 55L;
    private static final String DEVICE = "PHONE_1";

    @BeforeEach
    void setUp() {
        genericSyncService = new GenericSyncService();
        service = new ItemVariantServiceImpl(itemVariantRepo, menuItemRepo, genericSyncService);
    }

    @Test
    void pushData_resolvesMenuItemByDeviceAndLocalId() {
        MenuItem mi = new MenuItem();
        mi.setId(300L);
        mi.setRestaurantId(TENANT_ID);

        ItemVariant variant = itemVariant(1L, 20L);

        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 20L))
            .thenReturn(Optional.of(mi));
        when(itemVariantRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(itemVariantRepo).saveAll(any());

        service.pushData(TENANT_ID, List.of(variant));

        assertThat(variant.getServerMenuItemId()).isEqualTo(300L);
    }

    @Test
    void pushData_missingMenuItem_addedToFailedIds() {
        ItemVariant variant = itemVariant(1L, 20L);

        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(menuItemRepo.findById(anyLong())).thenReturn(Optional.empty());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(variant));

        assertThat(resp.getFailedLocalIds()).contains(1L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(1L);
    }

    private ItemVariant itemVariant(long localId, long menuItemId) {
        ItemVariant iv = new ItemVariant();
        iv.setLocalId(localId);
        iv.setDeviceId(DEVICE);
        iv.setRestaurantId(TENANT_ID);
        iv.setUpdatedAt(1000L);
        iv.setMenuItemId(menuItemId);
        iv.setVariantName("Large");
        iv.setPrice(BigDecimal.TEN);
        return iv;
    }
}
