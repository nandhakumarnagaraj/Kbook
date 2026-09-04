package com.khanabook.saas.service;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.impl.MenuItemServiceImpl;
import com.khanabook.saas.service.SecurityAuditService;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import com.khanabook.saas.sync.service.GenericSyncService;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuItemServiceImplTest {

    @Mock private MenuItemRepository menuItemRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private BillRepository billRepository;
    @Mock private BillPaymentRepository billPaymentRepository;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private RestaurantTerminalRepository terminalRepository;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private com.khanabook.saas.repository.StaffPermissionRevisionRepository revisionRepo;

    private GenericSyncService genericSyncService;
    private MenuItemServiceImpl service;

    private static final Long TENANT_ID = 55L;
    private static final String DEVICE = "PHONE_1";
    private static final Long STAFF_USER = 900L;
    private static final Long OWNER_USER = 1L;

    @BeforeEach
    void setUp() {
        genericSyncService = new GenericSyncService(
            billRepository,
            billPaymentRepository,
            menuItemRepo,
            itemVariantRepository,
            categoryRepo,
            terminalRepository,
            securityAuditService,
            new com.khanabook.saas.sync.service.SyncFallbackSaver(),
            org.mockito.Mockito.mock(com.khanabook.saas.service.PermissionService.class),
            revisionRepo,
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.RelationalIdResolver.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.TerminalOwnershipService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.BillSyncService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.SyncNotificationService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.UserProfileSyncService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.BillPaymentSyncService.class)
        );
        service = new MenuItemServiceImpl(
            menuItemRepo, categoryRepo, genericSyncService,
            org.mockito.Mockito.mock(com.khanabook.saas.service.PushNotificationService.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Existing behavior (unchanged) — run as KBOOK_ADMIN to bypass authz ─────

    @Test
    void pushData_resolvesCategoryByDeviceAndLocalId() {
        actAsKbookAdmin();
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
        actAsKbookAdmin();
        MenuItem item = menuItem(1L, 10L);

        when(categoryRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(categoryRepo.findById(anyLong())).thenReturn(Optional.empty());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(item));

        assertThat(resp.getFailedLocalIds()).contains(1L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(1L);
    }

    // ── P0: master data is single-writer (role-bound) ─────────────────────────

    @Test
    void staff_cannotChangeMenuPrice() {
        actAsStaff();
        MenuItem incoming = withServerId(menuItem(1L, 10L), 500L, new BigDecimal("300"), true);

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(1L);
        assertThat(resp.getFailedReasons().get(1L)).contains("owner or an admin");
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void staffWithEditFullGrant_stillCannotChangeMenu() {
        // The pen is role-bound now: a menu.* grant alone does NOT let a staff
        // member write master data.
        actAsStaff();
        MenuItem incoming = withServerId(menuItem(3L, 10L), 502L, new BigDecimal("300"), false);

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(3L);
        assertThat(resp.getFailedReasons().get(3L)).contains("owner or an admin");
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void staff_stillCannotChangeMenu_whenGrantedSinceEdit() {
        // Even a continuously-granted staff edit is rejected: staff are bill-mint
        // readers of the cached menu, never master-data writers.
        actAsStaff();
        MenuItem incoming = withServerId(menuItem(7L, 10L), 510L, new BigDecimal("300"), true);

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(7L);
        assertThat(resp.getFailedReasons().get(7L)).contains("owner or an admin");
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void owner_canChangePriceAndAvailability() {
        actAsOwner();
        MenuItem existing = serverRow(503L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(4L, 10L), 503L, new BigDecimal("300"), false);

        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).doesNotContain(4L);
        verify(menuItemRepo).saveAll(any());
    }

    @Test
    void shopAdmin_canChangePriceAndAvailability() {
        // SHOP_ADMIN is a master-data writer by role — no menu.* grant needed.
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentRole("SHOP_ADMIN");
        TenantContext.setCurrentUserId(STAFF_USER);
        MenuItem existing = serverRow(504L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(5L, 10L), 504L, new BigDecimal("300"), false);

        assertThat(com.khanabook.saas.sync.validation.SyncPushGuard.isMasterDataWriter("SHOP_ADMIN")).isTrue();

        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).doesNotContain(5L);
        verify(menuItemRepo).saveAll(any());
    }

    @Test
    void nonAdmin_withNoResolvableUser_isRejected() {
        // Role set but user id absent — cannot authorize an unknown actor.
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentRole("OWNER");
        // no setCurrentUserId
        MenuItem incoming = menuItem(6L, 10L);

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(6L);
        assertThat(resp.getFailedReasons().get(6L)).contains("unknown user");
        verify(menuItemRepo, never()).saveAll(any());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void actAsStaff() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentRole("STAFF");
        TenantContext.setCurrentUserId(STAFF_USER);
    }

    private void actAsOwner() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentRole("OWNER");
        TenantContext.setCurrentUserId(OWNER_USER);
    }

    private void actAsKbookAdmin() {
        TenantContext.setCurrentTenant(TENANT_ID);
        TenantContext.setCurrentRole("KBOOK_ADMIN");
        TenantContext.setCurrentUserId(OWNER_USER);
    }

    private MenuItem menuItem(long localId, long categoryId) {
        MenuItem mi = new MenuItem();
        mi.setLocalId(localId);
        mi.setDeviceId(DEVICE);
        mi.setRestaurantId(TENANT_ID);
        mi.setUpdatedAt(System.currentTimeMillis());
        mi.setCreatedAt(System.currentTimeMillis());
        mi.setCategoryId(categoryId);
        mi.setName("Burger");
        mi.setBasePrice(BigDecimal.TEN);
        return mi;
    }

    private MenuItem withServerId(MenuItem mi, long serverId, BigDecimal price, Boolean available) {
        mi.setId(serverId);
        mi.setServerCategoryId(10L); // pre-resolved so category resolution is a no-op
        mi.setCategoryId(10L);
        mi.setBasePrice(price);
        mi.setIsAvailable(available);
        return mi;
    }

    private MenuItem serverRow(long serverId, BigDecimal price, Boolean available) {
        MenuItem mi = new MenuItem();
        mi.setId(serverId);
        mi.setLocalId(serverId);
        mi.setDeviceId(DEVICE);
        mi.setRestaurantId(TENANT_ID);
        mi.setName("Burger");
        mi.setBasePrice(price);
        mi.setIsAvailable(available);
        mi.setIsDeleted(false);
        mi.setServerCategoryId(10L);
        mi.setCategoryId(10L);
        mi.setCreatedAt(1L);
        long older = System.currentTimeMillis() - 60_000L; // within clock-skew window, older than incoming
        mi.setUpdatedAt(older);
        mi.setServerUpdatedAt(older);
        return mi;
    }
}
