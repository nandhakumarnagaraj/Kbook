package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.payment.entity.Payment;
import com.khanabook.saas.payment.repository.PaymentRepository;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.CategoryRepository;
import com.khanabook.saas.repository.ItemVariantRepository;
import com.khanabook.saas.repository.MenuItemRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.sync.dto.PushSyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GenericSyncServiceTest {

    @Mock private BillRepository billRepo;
    @Mock private RestaurantProfileRepository profileRepo;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BillPaymentRepository billPaymentRepository;

    @Captor private ArgumentCaptor<Iterable<Bill>> billSaveCaptor;
    @Captor private ArgumentCaptor<Iterable<BillPayment>> billPaymentSaveCaptor;
    @Captor private ArgumentCaptor<Iterable<RestaurantProfile>> profileSaveCaptor;

    private GenericSyncService service;

    private static final Long TENANT_ID = 42L;
    private static final String DEVICE_A = "DEVICE_A";

    @BeforeEach
    void setUp() {
        service = new GenericSyncService(
            billRepo,
            menuItemRepository,
            itemVariantRepository,
            categoryRepository,
            paymentRepository
        );
    }

    

    @Test
    void nullTenantId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.handlePushSync(null, List.of(bill(1L, 1000L)), billRepo))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Tenant ID");
    }

    @Test
    void emptyPayload_returnsEmptyLists() {
        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(), billRepo);
        assertThat(resp.getSuccessfulLocalIds()).isEmpty();
        assertThat(resp.getFailedLocalIds()).isEmpty();
        verifyNoInteractions(billRepo);
    }

    

    @Test
    void newRecord_insertsAndAcknowledges() {
        Bill incoming = bill(1L, 1000L);
        incoming.setLifetimeOrderId(101L);
        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        assertThat(resp.getSuccessfulLocalIds()).containsExactly(1L);
        assertThat(resp.getFailedLocalIds()).isEmpty();
        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        
        assertThat(saved.getRestaurantId()).isEqualTo(TENANT_ID);
    }

    

    @Test
    void lww_mobileNewer_updatesRecord() {
        Bill existing = existingBill(5L, 1L, 1000L);
        Bill incoming = bill(1L, 2000L);
        incoming.setLifetimeOrderId(101L);
        stubExisting(List.of(existing));
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        assertThat(resp.getSuccessfulLocalIds()).containsExactly(1L);
        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getId()).isEqualTo(5L);       
        assertThat(saved.getUpdatedAt()).isEqualTo(2000L);
    }

    

    @Test
    void lww_serverNewer_clientAcknowledgedWithoutSave() {
        Bill existing = existingBill(5L, 1L, 9000L);
        Bill incoming = bill(1L, 1000L);  
        incoming.setLifetimeOrderId(101L);
        stubExisting(List.of(existing));

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        
        assertThat(resp.getSuccessfulLocalIds()).containsExactly(1L);
        verify(billRepo, never()).saveAll(any());
    }

    

    @Test
    void tenantIsolation_payloadRestaurantIdOverriddenByServer() {
        Bill incoming = bill(1L, 1000L);
        incoming.setLifetimeOrderId(101L);
        incoming.setRestaurantId(666L); 
        stubNoExisting();

        assertThatThrownBy(() -> service.handlePushSync(TENANT_ID, List.of(incoming), billRepo))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Permission denied");

        verify(billRepo, never()).saveAll(any());
    }

    

    @Test
    void createdAtNull_defaultsToUpdatedAt() {
        Bill incoming = bill(1L, 5000L);
        incoming.setLifetimeOrderId(101L);
        incoming.setCreatedAt(null);
        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getCreatedAt()).isEqualTo(5000L);
    }

    @Test
    void createdAtPresent_notOverridden() {
        Bill incoming = bill(1L, 5000L);
        incoming.setLifetimeOrderId(101L);
        incoming.setCreatedAt(1000L); 
        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getCreatedAt()).isEqualTo(1000L); 
    }

    

    @Test
    void serverUpdatedAt_alwaysSetByServer_notClient() {
        Bill incoming = bill(1L, 5000L);
        incoming.setLifetimeOrderId(101L);
        incoming.setServerUpdatedAt(9_999_999_999L); 
        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        long before = System.currentTimeMillis();
        service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);
        long after = System.currentTimeMillis();

        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getServerUpdatedAt()).isBetween(before, after);
    }

    

    @Test
    void batchWithDuplicateLocalIds_onlyLatestSaved() {
        Bill older = bill(1L, 1000L);
        Bill newer = bill(1L, 3000L);
        older.setLifetimeOrderId(101L);
        newer.setLifetimeOrderId(101L);
        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(older, newer), billRepo);

        verify(billRepo).saveAll(billSaveCaptor.capture());
        List<Bill> saved = (List<Bill>) billSaveCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getUpdatedAt()).isEqualTo(3000L);
        
        assertThat(resp.getSuccessfulLocalIds()).containsExactlyInAnyOrder(1L, 1L);
    }

    

    @Test
    void singletonProfile_reinstall_matchesExistingTenantRecord() {
        RestaurantProfile existing = existingProfile(99L, 1L, "DEVICE_OLD", 5000L);
        RestaurantProfile incoming = profileWithDevice(1L, "DEVICE_NEW", 6000L);

        when(profileRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(eq(TENANT_ID), eq("DEVICE_NEW"), anyList()))
            .thenReturn(List.of()); 
        when(profileRepo.findByRestaurantIdAndLocalIdIn(eq(TENANT_ID), anyList()))
            .thenReturn(List.of(existing)); 
        doAnswer(i -> i.getArgument(0)).when(profileRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), profileRepo);

        assertThat(resp.getSuccessfulLocalIds()).containsExactly(1L);
        verify(profileRepo).saveAll(profileSaveCaptor.capture());
        RestaurantProfile saved = profileSaveCaptor.getValue().iterator().next();
        
        assertThat(saved.getId()).isEqualTo(99L);
    }

    

    @Test
    void billWithLocalId1_doesNotTriggerCrossDeviceFallback() {
        Bill incoming = bill(1L, 5000L); 
        incoming.setLifetimeOrderId(101L);

        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(eq(TENANT_ID), eq(DEVICE_A), anyList()))
            .thenReturn(List.of());
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        
        verify(billRepo, never()).findByRestaurantIdAndLocalIdIn(anyLong(), anyList());
    }

    

    @Test
    void missingLocalId_recoveredFromServerId() {
        Bill incoming = new Bill();
        incoming.setId(77L);       
        incoming.setLocalId(null);
        incoming.setDeviceId(DEVICE_A);
        incoming.setRestaurantId(TENANT_ID);
        incoming.setLifetimeOrderId(101L);
        incoming.setUpdatedAt(1000L);

        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        
        assertThat(resp.getSuccessfulLocalIds()).containsExactly(77L);
        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getLocalId()).isEqualTo(77L);
        assertThat(saved.getId()).isNull(); 
    }

    

    @Test
    void multipleDifferentRecords_savedInSingleBatch() {
        Bill b1 = bill(1L, 1000L);
        Bill b2 = bill(2L, 2000L);
        Bill b3 = bill(3L, 3000L);
        b1.setLifetimeOrderId(101L);
        b2.setLifetimeOrderId(102L);
        b3.setLifetimeOrderId(103L);
        stubNoExisting();
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        service.handlePushSync(TENANT_ID, List.of(b1, b2, b3), billRepo);

        
        verify(billRepo, times(1)).saveAll(any());
    }

    @Test
    void sameDeviceLifetimeCollision_updatesExistingBillInsteadOfInsertingDuplicate() {
        Bill incoming = bill(7L, 4000L);
        incoming.setLifetimeOrderId(271L);
        Bill existing = existingBill(55L, 2L, 1000L);
        existing.setLifetimeOrderId(271L);

        stubNoExisting();
        when(billRepo.findByRestaurantIdAndLifetimeOrderIdAndIsDeletedFalse(TENANT_ID, 271L))
            .thenReturn(java.util.Optional.of(existing));
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        assertThat(resp.getSuccessfulLocalIds()).containsExactly(7L);
        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getId()).isEqualTo(55L);
        assertThat(saved.getLocalId()).isEqualTo(7L);
    }

    @Test
    void crossDeviceLifetimeCollision_rejectedWithoutSave() {
        Bill incoming = bill(7L, 4000L);
        incoming.setLifetimeOrderId(271L);
        Bill existing = existingBill(55L, 2L, 1000L);
        existing.setDeviceId("DEVICE_B");
        existing.setLifetimeOrderId(271L);

        stubNoExisting();
        when(billRepo.findByRestaurantIdAndLifetimeOrderIdAndIsDeletedFalse(TENANT_ID, 271L))
            .thenReturn(java.util.Optional.of(existing));

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        assertThat(resp.getSuccessfulLocalIds()).isEmpty();
        assertThat(resp.getFailedLocalIds()).containsExactly(7L);
        verify(billRepo, never()).saveAll(any());
    }

    @Test
    void gatewayBackedBill_syncPreservesServerOwnedPaymentState() {
        Bill existing = existingBill(5L, 1L, 1000L);
        existing.setOrderStatus("draft");
        existing.setPaymentStatus("pending");
        existing.setCancelReason("");

        Bill incoming = bill(1L, 2000L);
        incoming.setLifetimeOrderId(101L);
        incoming.setOrderStatus("completed");
        incoming.setPaymentStatus("success");
        incoming.setCancelReason("client overwrite");

        stubExisting(List.of(existing));
        when(paymentRepository.findTopByRestaurantIdAndBillIdOrderByCreatedAtDesc(TENANT_ID, 5L))
            .thenReturn(java.util.Optional.of(new Payment()));
        doAnswer(i -> i.getArgument(0)).when(billRepo).saveAll(any());

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billRepo);

        assertThat(resp.getSuccessfulLocalIds()).containsExactly(1L);
        verify(billRepo).saveAll(billSaveCaptor.capture());
        Bill saved = billSaveCaptor.getValue().iterator().next();
        assertThat(saved.getOrderStatus()).isEqualTo("draft");
        assertThat(saved.getPaymentStatus()).isEqualTo("pending");
        assertThat(saved.getCancelReason()).isEmpty();
        assertThat(saved.getUpdatedAt()).isEqualTo(2000L);
    }

    @Test
    void gatewayOwnedBillPayment_syncAcknowledgedWithoutSave() {
        BillPayment incoming = new BillPayment();
        incoming.setLocalId(88L);
        incoming.setUpdatedAt(2000L);
        incoming.setDeviceId(DEVICE_A);
        incoming.setRestaurantId(TENANT_ID);
        incoming.setGatewayTxnId("TXN-1");
        incoming.setVerifiedBy("easebuzz");

        when(billPaymentRepository.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(List.of());
        when(paymentRepository.findByRestaurantIdAndGatewayTxnId(TENANT_ID, "TXN-1"))
            .thenReturn(java.util.Optional.of(new Payment()));

        PushSyncResponse resp = service.handlePushSync(TENANT_ID, List.of(incoming), billPaymentRepository);

        assertThat(resp.getSuccessfulLocalIds()).containsExactly(88L);
        verify(billPaymentRepository, never()).saveAll(any());
    }

    

    private Bill bill(long localId, long updatedAt) {
        Bill b = new Bill();
        b.setLocalId(localId);
        b.setUpdatedAt(updatedAt);
        b.setDeviceId(DEVICE_A);
        b.setRestaurantId(TENANT_ID);
        b.setLifetimeOrderId(localId);
        return b;
    }

    private Bill existingBill(Long serverId, long localId, long updatedAt) {
        Bill b = bill(localId, updatedAt);
        b.setId(serverId);
        return b;
    }

    private RestaurantProfile existingProfile(Long serverId, long localId, String deviceId, long updatedAt) {
        RestaurantProfile p = new RestaurantProfile();
        p.setId(serverId);
        p.setLocalId(localId);
        p.setDeviceId(deviceId);
        p.setRestaurantId(TENANT_ID);
        p.setUpdatedAt(updatedAt);
        return p;
    }

    private RestaurantProfile profileWithDevice(long localId, String deviceId, long updatedAt) {
        RestaurantProfile p = new RestaurantProfile();
        p.setLocalId(localId);
        p.setDeviceId(deviceId);
        p.setRestaurantId(TENANT_ID);
        p.setUpdatedAt(updatedAt);
        return p;
    }

    private void stubNoExisting() {
        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(new ArrayList<>());
        when(billRepo.findByRestaurantIdAndLifetimeOrderIdAndIsDeletedFalse(any(), any()))
            .thenReturn(java.util.Optional.empty());
    }

    private void stubExisting(List<Bill> records) {
        when(billRepo.findByRestaurantIdAndDeviceIdAndLocalIdIn(any(), any(), anyList()))
            .thenReturn(records);
    }
}
