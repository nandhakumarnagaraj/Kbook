package com.khanabook.saas.service;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.service.impl.MenuItemServiceImpl;
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
class MenuItemServiceImplTest {

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private CategoryRepository categoryRepo;

    private GenericSyncService genericSyncService;
    private MenuItemServiceImpl service;

    private static final Long TENANT_ID = 55L;
    private static final String DEVICE = "PHONE_1";

    @BeforeEach
    void setUp() {
        genericSyncService = new GenericSyncService();
        service = new MenuItemServiceImpl(menuItemRepo, categoryRepo, genericSyncService);
    }

    @Test
    void pushData_resolvesCategoryByDeviceAndLocalId() {
        Category cat = new Category();
        cat.setId(200L);
        cat.setRestaurantId(TENANT_ID);

        MenuItem item = menuItem(1L, 10L);

        when(categoryRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(cat));
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        service.pushData(TENANT_ID, List.of(item));

        assertThat(item.getServerCategoryId()).isEqualTo(200L);
    }

    @Test
    void pushData_missingCategory_addedToFailedIds() {
        MenuItem item = menuItem(1L, 10L);

        when(categoryRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(categoryRepo.findById(anyLong())).thenReturn(Optional.empty());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(item));

        assertThat(resp.getFailedLocalIds()).contains(1L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(1L);
    }

    private MenuItem menuItem(long localId, long categoryId) {
        MenuItem mi = new MenuItem();
        mi.setLocalId(localId);
        mi.setDeviceId(DEVICE);
        mi.setRestaurantId(TENANT_ID);
        mi.setUpdatedAt(1000L);
        mi.setCategoryId(categoryId);
        mi.setName("Burger");
        mi.setBasePrice(BigDecimal.TEN);
        return mi;
    }
}
