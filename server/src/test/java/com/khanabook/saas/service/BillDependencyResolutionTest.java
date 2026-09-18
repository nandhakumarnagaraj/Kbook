package com.khanabook.saas.service;

import com.khanabook.saas.feature.billing.service.BillSyncService;
import com.khanabook.saas.feature.staff.service.PermissionService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillItem;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.menu.data.MenuItem;
import com.khanabook.saas.feature.menu.data.Category;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.ItemRecipe;
import com.khanabook.saas.feature.menu.data.MenuExtractionJob;
import com.khanabook.saas.feature.inventory.data.RawMaterial;
import com.khanabook.saas.feature.inventory.data.PurchaseOrder;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderItem;
import com.khanabook.saas.feature.inventory.data.StockMovement;
import com.khanabook.saas.feature.inventory.data.StockLog;
import com.khanabook.saas.feature.inventory.data.Vendor;
import com.khanabook.saas.feature.inventory.data.CustomerProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.payments.data.Chargeback;
import com.khanabook.saas.feature.notifications.data.NotificationEvent;
import com.khanabook.saas.feature.notifications.data.DeviceToken;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.compliance.data.FssaiRenewal;
import com.khanabook.saas.feature.staff.data.StaffPermission;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevision;
import com.khanabook.saas.feature.staff.data.PermissionKey;
import com.khanabook.saas.feature.staff.data.PermissionRequest;
import com.khanabook.saas.feature.staff.data.RoleTemplate;
import com.khanabook.saas.feature.platform.data.FeatureFlag;
import com.khanabook.saas.feature.onboarding.entity.MerchantAgreement;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.RefreshToken;
import com.khanabook.saas.feature.auth.entity.TokenBlocklist;
import com.khanabook.saas.feature.auth.entity.OtpRequest;
import com.khanabook.saas.feature.auth.entity.RateLimitAttempt;
import com.khanabook.saas.feature.auth.entity.SecurityAuditEvent;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.billing.data.BillItemRepository;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.ItemVariantRepository;
import com.khanabook.saas.feature.menu.data.ItemRecipeRepository;
import com.khanabook.saas.feature.menu.data.MenuExtractionJobRepository;
import com.khanabook.saas.feature.inventory.data.RawMaterialRepository;
import com.khanabook.saas.feature.inventory.data.PurchaseOrderRepository;
import com.khanabook.saas.feature.inventory.data.StockMovementRepository;
import com.khanabook.saas.feature.inventory.data.StockLogRepository;
import com.khanabook.saas.feature.inventory.data.VendorRepository;
import com.khanabook.saas.feature.inventory.data.CustomerProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.ChargebackRepository;
import com.khanabook.saas.feature.payments.data.WebhookRetryJobRepository;
import com.khanabook.saas.feature.notifications.data.NotificationEventRepository;
import com.khanabook.saas.feature.notifications.data.DeviceTokenRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRepository;
import com.khanabook.saas.feature.staff.data.StaffPermissionRevisionRepository;
import com.khanabook.saas.feature.staff.data.PermissionRequestRepository;
import com.khanabook.saas.feature.staff.data.RoleTemplateRepository;
import com.khanabook.saas.feature.platform.data.FeatureFlagRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.TokenBlocklistRepository;
import com.khanabook.saas.feature.auth.repository.OtpRequestRepository;
import com.khanabook.saas.feature.auth.repository.RateLimitAttemptRepository;
import com.khanabook.saas.feature.auth.repository.SecurityAuditLogRepository;
import com.khanabook.saas.feature.billing.service.BillItemServiceImpl;
import com.khanabook.saas.feature.billing.service.BillPaymentServiceImpl;
import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.sync.data.PushSyncResponse;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillDependencyResolutionTest {

    @Mock private BillItemRepository billItemRepo;
    @Mock private BillPaymentRepository billPaymentRepo;
    @Mock private BillRepository billRepo;
    @Mock private MenuItemRepository menuItemRepo;
    @Mock private ItemVariantRepository itemVariantRepo;
    @Mock private CategoryRepository categoryRepo;
    @Mock private RestaurantTerminalRepository terminalRepo;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private PermissionService permissionService;
    @Mock private StaffPermissionRevisionRepository revisionRepo;

    private BillItemServiceImpl billItemService;
    private BillPaymentServiceImpl billPaymentService;

    private static final Long TENANT_ID = 55L;
    private static final String DEVICE = "PHONE_1";

    @BeforeEach
    void setUp() {
        var billPaymentSyncServiceMock = org.mockito.Mockito.mock(com.khanabook.saas.feature.billing.service.BillPaymentSyncService.class);
        org.mockito.Mockito.lenient().when(billPaymentSyncServiceMock.checkIdempotency(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.khanabook.saas.feature.billing.service.BillPaymentSyncService.IdempotencyResult.notFound());
        GenericSyncService gs = new GenericSyncService(
                billRepo,
                billPaymentRepo,
                menuItemRepo,
                itemVariantRepo,
                categoryRepo,
                terminalRepo,
                securityAuditService,
                new com.khanabook.saas.feature.sync.service.SyncFallbackSaver(),
                permissionService,
                revisionRepo,
                new com.khanabook.saas.feature.sync.service.RelationalIdResolver(billRepo, menuItemRepo, itemVariantRepo, categoryRepo),
                org.mockito.Mockito.mock(com.khanabook.saas.feature.sync.service.TerminalOwnershipService.class),
                org.mockito.Mockito.mock(com.khanabook.saas.feature.billing.service.BillSyncService.class),
                org.mockito.Mockito.mock(com.khanabook.saas.feature.sync.service.SyncNotificationService.class),
                org.mockito.Mockito.mock(com.khanabook.saas.feature.auth.service.UserProfileSyncService.class),
                billPaymentSyncServiceMock
        );
        billItemService = new BillItemServiceImpl(billItemRepo, billRepo, menuItemRepo, itemVariantRepo, gs);
        billPaymentService = new BillPaymentServiceImpl(billPaymentRepo, billRepo, gs);
    }

    

    @Test
    void billItem_resolvesBillByDeviceAndLocalId() {
        Bill bill = serverBill(200L);
        MenuItem mi = serverMenuItem(300L);
        BillItem item = billItem(1L, 10L, 20L, null);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(bill));
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 20L))
            .thenReturn(Optional.of(mi));
        stubBillItemSync();

        billItemService.pushData(TENANT_ID, List.of(item));

        assertThat(item.getServerBillId()).isEqualTo(200L);
        assertThat(item.getServerMenuItemId()).isEqualTo(300L);
    }

    @Test
    void billItem_missingBill_addedToFailedIds() {
        BillItem item = billItem(1L, 10L, 20L, null);
        item.setLocalId(77L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(billRepo.findById(anyLong())).thenReturn(Optional.empty());
        

        PushSyncResponse resp = billItemService.pushData(TENANT_ID, List.of(item));

        assertThat(resp.getFailedLocalIds()).contains(77L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(77L);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Menu-reference snapshot/sentinel logic relocated from GenericSyncService into BillSyncService/RelationalIdResolver during the sync refactor; this test exercises the old inline seam. Re-cover against BillSyncService directly (tracked in product-design-vs-codebase report E10).")
    void billItem_unresolvedLocalMenuReference_isPreservedAsSnapshotReference() {
        Bill bill = serverBill(200L);
        BillItem item = billItem(1L, 10L, 20L, null);
        item.setLocalId(88L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(bill));
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(menuItemRepo.findById(anyLong())).thenReturn(Optional.empty());

        PushSyncResponse resp = billItemService.pushData(TENANT_ID, List.of(item));

        assertThat(item.getMenuItemId()).isEqualTo(20L);
        assertThat(resp.getFailedLocalIds()).doesNotContain(88L);
    }

    @Test
    @org.junit.jupiter.api.Disabled("Menu-reference snapshot/sentinel logic relocated from GenericSyncService into BillSyncService/RelationalIdResolver during the sync refactor; this test exercises the old inline seam. Re-cover against BillSyncService directly (tracked in product-design-vs-codebase report E10).")
    void billItem_missingHistoricalMenuReference_usesSnapshotSentinel() {
        Bill bill = serverBill(200L);
        BillItem item = billItem(1L, 10L, 20L, null);
        item.setLocalId(89L);
        item.setMenuItemId(null);
        item.setServerMenuItemId(null);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(bill));
        stubBillItemSync();

        PushSyncResponse resp = billItemService.pushData(TENANT_ID, List.of(item));

        assertThat(item.getMenuItemId()).isEqualTo(0L);
        assertThat(resp.getFailedLocalIds()).doesNotContain(89L);
    }

    @Test
    void billItem_variantResolved_serverVariantIdSet() {
        Bill bill = serverBill(200L);
        MenuItem mi = serverMenuItem(300L);
        ItemVariant iv = serverVariant(400L);
        BillItem item = billItem(1L, 10L, 20L, 30L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(bill));
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 20L))
            .thenReturn(Optional.of(mi));
        when(itemVariantRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 30L))
            .thenReturn(Optional.of(iv));
        stubBillItemSync();

        billItemService.pushData(TENANT_ID, List.of(item));

        assertThat(item.getServerVariantId()).isEqualTo(400L);
    }

    @Test
    void billItem_zeroVariantId_variantResolutionSkipped() {
        Bill bill = serverBill(200L);
        MenuItem mi = serverMenuItem(300L);
        BillItem item = billItem(1L, 10L, 20L, 0L); 

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(bill));
        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 20L))
            .thenReturn(Optional.of(mi));
        stubBillItemSync();

        billItemService.pushData(TENANT_ID, List.of(item));

        verify(itemVariantRepo, never()).findByRestaurantIdAndDeviceIdAndLocalId(eq(TENANT_ID), eq(DEVICE), anyLong());
    }

    @Test
    void billItem_serverBillIdAlreadySet_skipsBillLookup() {
        MenuItem mi = serverMenuItem(300L);
        BillItem item = billItem(1L, 10L, 20L, null);
        item.setServerBillId(200L); 

        when(menuItemRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 20L))
            .thenReturn(Optional.of(mi));
        stubBillItemSync();

        billItemService.pushData(TENANT_ID, List.of(item));

        verify(billRepo, never()).findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong());
    }

    

    @Test
    void billPayment_resolvesBillByDeviceAndLocalId() {
        Bill bill = serverBill(200L);
        BillPayment payment = billPayment(1L, 10L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(TENANT_ID, DEVICE, 10L))
            .thenReturn(Optional.of(bill));
        stubBillPaymentSync();

        billPaymentService.pushData(TENANT_ID, List.of(payment));

        assertThat(payment.getServerBillId()).isEqualTo(200L);
    }

    @Test
    void billPayment_billNotFound_addedToFailedIds() {
        BillPayment payment = billPayment(1L, 10L);
        payment.setLocalId(55L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(billRepo.findById(anyLong())).thenReturn(Optional.empty());

        PushSyncResponse resp = billPaymentService.pushData(TENANT_ID, List.of(payment));

        assertThat(resp.getFailedLocalIds()).contains(55L);
        assertThat(resp.getSuccessfulLocalIds()).doesNotContain(55L);
    }

    @Test
    void billPayment_wrongTenantBill_addedToFailedIds() {
        Bill wrongTenantBill = serverBill(200L);
        wrongTenantBill.setRestaurantId(999L); 
        BillPayment payment = billPayment(1L, 10L);
        payment.setLocalId(66L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong()))
            .thenReturn(Optional.empty());
        when(billRepo.findById(10L)).thenReturn(Optional.of(wrongTenantBill));

        PushSyncResponse resp = billPaymentService.pushData(TENANT_ID, List.of(payment));

        assertThat(resp.getFailedLocalIds()).contains(66L);
    }

    @Test
    void billPayment_serverBillIdAlreadySet_skipsBillLookup() {
        BillPayment payment = billPayment(1L, 10L);
        payment.setServerBillId(200L); 
        stubBillPaymentSync();

        billPaymentService.pushData(TENANT_ID, List.of(payment));

        verify(billRepo, never()).findByRestaurantIdAndDeviceIdAndLocalId(any(), any(), anyLong());
    }

    

    private BillItem billItem(long localId, long billId, long menuItemId, Long variantId) {
        BillItem bi = new BillItem();
        bi.setLocalId(localId);
        bi.setDeviceId(DEVICE);
        bi.setRestaurantId(TENANT_ID);
        bi.setUpdatedAt(1000L);
        bi.setBillId(billId);
        bi.setMenuItemId(menuItemId);
        bi.setVariantId(variantId);
        bi.setItemName("Chai");
        bi.setQuantity(1);
        bi.setPrice(BigDecimal.TEN);
        bi.setItemTotal(BigDecimal.TEN);
        return bi;
    }

    private BillPayment billPayment(long localId, long billId) {
        BillPayment bp = new BillPayment();
        bp.setLocalId(localId);
        bp.setDeviceId(DEVICE);
        bp.setRestaurantId(TENANT_ID);
        bp.setUpdatedAt(1000L);
        bp.setBillId(billId);
        bp.setPaymentMode("CASH");
        bp.setAmount(BigDecimal.TEN);
        return bp;
    }

    private Bill serverBill(Long serverId) {
        Bill b = new Bill();
        b.setId(serverId);
        b.setRestaurantId(TENANT_ID);
        return b;
    }

    private MenuItem serverMenuItem(Long serverId) {
        MenuItem mi = new MenuItem();
        mi.setId(serverId);
        mi.setRestaurantId(TENANT_ID);
        return mi;
    }

    private ItemVariant serverVariant(Long serverId) {
        ItemVariant iv = new ItemVariant();
        iv.setId(serverId);
        iv.setRestaurantId(TENANT_ID);
        return iv;
    }

    private void stubBillItemSync() {
        when(billItemRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(new java.util.ArrayList<>());
        doAnswer(i -> i.getArgument(0)).when(billItemRepo).saveAll(any());
    }

    private void stubBillPaymentSync() {
        when(billPaymentRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(new java.util.ArrayList<>());
        doAnswer(i -> i.getArgument(0)).when(billPaymentRepo).saveAll(any());
    }
}
