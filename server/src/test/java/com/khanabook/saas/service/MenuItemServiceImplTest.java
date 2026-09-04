package com.khanabook.saas.service;

import com.khanabook.saas.entity.Category;
import com.khanabook.saas.entity.MenuItem;
import com.khanabook.saas.entity.PermissionKey;
import com.khanabook.saas.entity.StaffPermission;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantTerminalRepository;
import com.khanabook.saas.repository.StaffPermissionRepository;
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
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private PermissionService permissionService;
    @Mock private StaffPermissionRepository staffPermissionRepository;
    @Mock private com.khanabook.saas.repository.StaffPermissionRevisionRepository revisionRepo;

    private GenericSyncService genericSyncService;
    private MenuItemServiceImpl service;

    private static final Long TENANT_ID = 55L;
    private static final String DEVICE = "PHONE_1";
    private static final Long STAFF_USER = 900L;
    private static final Long OWNER_USER = 1L;

    private static final String PRICE = PermissionKey.MENU_EDIT_PRICE.getKey();
    private static final String AVAIL = PermissionKey.MENU_TOGGLE_AVAILABILITY.getKey();

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
            permissionService,
            revisionRepo,
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.RelationalIdResolver.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.TerminalOwnershipService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.BillSyncService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.SyncNotificationService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.UserProfileSyncService.class),
            org.mockito.Mockito.mock(com.khanabook.saas.sync.service.BillPaymentSyncService.class)
        );
        service = new MenuItemServiceImpl(
            menuItemRepo, categoryRepo, genericSyncService, permissionService, staffPermissionRepository,
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

    // ── P0: server-side permission enforcement ────────────────────────────────

    @Test
    void staffWithoutEditPrice_cannotChangePrice() {
        actAsStaff();
        // Existing server row @250; incoming @300 (price change).
        MenuItem existing = serverRow(500L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(1L, 10L), 500L, new BigDecimal("300"), true);

        when(menuItemRepo.findById(500L)).thenReturn(Optional.of(existing));
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, PRICE)).thenReturn(false);

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(1L);
        assertThat(resp.getFailedReasons().get(1L)).contains("price");
        // Change must NOT be applied — nothing staged for save.
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void staffWithoutToggleAvailability_cannotToggle() {
        actAsStaff();
        MenuItem existing = serverRow(501L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(2L, 10L), 501L, new BigDecimal("250"), false);

        when(menuItemRepo.findById(501L)).thenReturn(Optional.of(existing));
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, AVAIL)).thenReturn(false);

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(2L);
        assertThat(resp.getFailedReasons().get(2L)).contains("availability");
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void staffWithEditPrice_canChangePrice() {
        actAsStaff();
        MenuItem existing = serverRow(502L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(3L, 10L), 502L, new BigDecimal("300"), true);

        when(menuItemRepo.findById(502L)).thenReturn(Optional.of(existing));
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, PRICE)).thenReturn(true);
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).doesNotContain(3L);
        verify(menuItemRepo).saveAll(any());
    }

    @Test
    void owner_canChangePriceAndAvailability() {
        actAsOwner();
        MenuItem existing = serverRow(503L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(4L, 10L), 503L, new BigDecimal("300"), false);

        when(menuItemRepo.findById(503L)).thenReturn(Optional.of(existing));
        // OWNER always holds every permission (PermissionService semantics).
        when(permissionService.hasPermission(eq(TENANT_ID), eq(OWNER_USER), any())).thenReturn(true);
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).doesNotContain(4L);
        verify(menuItemRepo).saveAll(any());
    }

    @Test
    void revokedPermission_isRejected() {
        actAsStaff();
        // P0-enforceable revocation guarantee: the permission is currently NOT granted
        // (revoked and not re-granted). The price change must be rejected.
        //
        // NOTE: the stricter "revoked AFTER an offline op was created, then re-granted"
        // case (Decision-A strict) requires the on-device creation revision, which is P1.
        // That path is proven at the pure layer in
        // MenuPushAuthorizerTest.authorize_revokedAfterCreation_rejected.
        MenuItem existing = serverRow(504L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(5L, 10L), 504L, new BigDecimal("300"), true);

        when(menuItemRepo.findById(504L)).thenReturn(Optional.of(existing));
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, PRICE)).thenReturn(false);
        StaffPermission sp = new StaffPermission(TENANT_ID, STAFF_USER, PRICE, OWNER_USER);
        sp.setGranted(false);
        sp.setLastRevokedRevision(6L);
        when(staffPermissionRepository.findByRestaurantIdAndUserIdAndPermissionKey(TENANT_ID, STAFF_USER, PRICE))
            .thenReturn(Optional.of(sp));

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(5L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(5L);
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void p1_stampedRevisionOlderThanRevocation_isRejected() {
        actAsStaff();
        // Full Decision-A strict (P1): the edit was created at revision 5, but the
        // permission was revoked at revision 6 — even though it is granted again now.
        // With the stamped revision flowing through, this is a hard REJECT, not a
        // QUARANTINE fallback.
        MenuItem existing = serverRow(510L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(7L, 10L), 510L, new BigDecimal("300"), true);
        incoming.setPermissionRevisionAtCreation(5L);

        when(menuItemRepo.findById(510L)).thenReturn(Optional.of(existing));
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, PRICE)).thenReturn(true);
        StaffPermission sp = new StaffPermission(TENANT_ID, STAFF_USER, PRICE, OWNER_USER);
        sp.setLastRevokedRevision(6L);
        when(staffPermissionRepository.findByRestaurantIdAndUserIdAndPermissionKey(TENANT_ID, STAFF_USER, PRICE))
            .thenReturn(Optional.of(sp));

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).contains(7L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(7L);
        verify(menuItemRepo, never()).saveAll(any());
    }

    @Test
    void p1_stampedRevisionAfterRevocation_isAccepted() {
        actAsStaff();
        // Edit created at revision 7, permission last revoked at revision 6 (before the
        // edit) and granted since. Continuously authorized → accepted.
        MenuItem existing = serverRow(511L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(8L, 10L), 511L, new BigDecimal("300"), true);
        incoming.setPermissionRevisionAtCreation(7L);

        when(menuItemRepo.findById(511L)).thenReturn(Optional.of(existing));
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, PRICE)).thenReturn(true);
        StaffPermission sp = new StaffPermission(TENANT_ID, STAFF_USER, PRICE, OWNER_USER);
        sp.setLastRevokedRevision(6L);
        when(staffPermissionRepository.findByRestaurantIdAndUserIdAndPermissionKey(TENANT_ID, STAFF_USER, PRICE))
            .thenReturn(Optional.of(sp));
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).doesNotContain(8L);
        verify(menuItemRepo).saveAll(any());
    }

    @Test
    void staffWithEditFull_canChangePriceAndAvailability() {
        actAsStaff();
        String editFull = PermissionKey.MENU_EDIT_FULL.getKey();
        MenuItem existing = serverRow(520L, new BigDecimal("250"), true);
        MenuItem incoming = withServerId(menuItem(9L, 10L), 520L, new BigDecimal("300"), false);

        when(menuItemRepo.findById(520L)).thenReturn(Optional.of(existing));
        // Holds ONLY menu.edit_full — implication must satisfy price + availability.
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, PRICE)).thenReturn(false);
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, AVAIL)).thenReturn(false);
        when(permissionService.hasPermission(TENANT_ID, STAFF_USER, editFull)).thenReturn(true);
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(menuItemRepo).saveAll(any());

        PushSyncResponse resp = service.pushData(TENANT_ID, List.of(incoming));

        assertThat(resp.getFailedLocalIds()).doesNotContain(9L);
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
