package com.khanabook.saas.service;

import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.business.service.BillEditService;
import com.khanabook.saas.feature.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The owner edit seam is the web counterpart of the device's
 * {@code BillRepository.updateOrderStatus / updatePaymentMode}, and it shares the sync
 * protocol with it. The invariant these tests defend is the one that broke before:
 * <b>every accepted edit must bump {@code statusVersion}</b>, or the server cannot tell a
 * deliberate change from a stale push and reverts it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillEditServiceTest {

    private static final Long RESTAURANT = 42L;
    private static final Long BILL_ID = 7L;

    @Mock private BillRepository billRepository;
    @Mock private BillPaymentRepository billPaymentRepository;
    @Mock private InventoryService inventoryService;
    @Mock private SecurityAuditService securityAuditService;

    private BillEditService service;

    @BeforeEach
    void setUp() {
        service = new BillEditService(billRepository, billPaymentRepository, inventoryService, securityAuditService);
    }

    private Bill bill(String orderStatus, String paymentStatus) {
        Bill bill = new Bill();
        bill.setId(BILL_ID);
        bill.setRestaurantId(RESTAURANT);
        bill.setOrderStatus(orderStatus);
        bill.setPaymentStatus(paymentStatus);
        bill.setPaymentMode("upi");
        bill.setStatusVersion(3);
        bill.setInventoryDeducted(false);
        bill.setIsDeleted(false);
        bill.setPublicToken(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        when(billRepository.findByIdForUpdate(BILL_ID)).thenReturn(Optional.of(bill));
        return bill;
    }

    private BillPayment payment(String mode) {
        BillPayment payment = new BillPayment();
        payment.setId(99L);
        payment.setServerBillId(BILL_ID);
        payment.setPaymentMode(mode);
        payment.setIsDeleted(false);
        when(billPaymentRepository.findByRestaurantIdAndServerBillIdIn(RESTAURANT, List.of(BILL_ID)))
                .thenReturn(List.of(payment));
        return payment;
    }

    // ─── Status ───────────────────────────────────────────────────────────────────

    @Test
    void completingAPendingBillBumpsVersionAuditsAndDeductsStockOnce() {
        Bill bill = bill("draft", "pending");
        when(billPaymentRepository.findByRestaurantIdAndServerBillIdIn(RESTAURANT, List.of(BILL_ID)))
                .thenReturn(List.of());

        service.updateOrderStatus(RESTAURANT, BILL_ID, "completed", "cash collected at counter");

        assertThat(bill.getOrderStatus()).isEqualTo("completed");
        assertThat(bill.getPaymentStatus()).isEqualTo("success");
        assertThat(bill.getPaidAt()).isNotNull();
        assertThat(bill.getStatusVersion()).isEqualTo(4);
        verify(billRepository).save(bill);
        verify(inventoryService).deductForFinalizedBill(bill);
        verify(securityAuditService).record(
                "BILL_STATUS_CHANGE", "draft->completed",
                "11111111-2222-3333-4444-555555555555", "cash collected at counter");
    }

    @Test
    void cancellingThroughTheStatusEndpointLeavesTheSameRowAsVoid() {
        Bill bill = bill("draft", "pending");

        service.updateOrderStatus(RESTAURANT, BILL_ID, "cancelled", "customer walked out");

        assertThat(bill.getOrderStatus()).isEqualTo("cancelled");
        assertThat(bill.getPaymentStatus()).isEqualTo("cancelled");
        assertThat(bill.getCancelReason()).isEqualTo("customer walked out");
        assertThat(bill.getStatusVersion()).isEqualTo(4);
        verify(inventoryService, never()).deductForFinalizedBill(any());
        // Same action name as BusinessReadService.voidBill, so one query finds every void.
        verify(securityAuditService).record(
                "BILL_VOID", "SUCCESS",
                "11111111-2222-3333-4444-555555555555", "customer walked out");
    }

    @Test
    void reopeningACompletedBillIsRefusedBecauseStockCannotBeReturned() {
        Bill bill = bill("completed", "success");

        assertThatThrownBy(() -> service.updateOrderStatus(RESTAURANT, BILL_ID, "draft", "owner mistake"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reopening a completed bill is not supported");

        assertThat(bill.getStatusVersion()).isEqualTo(3);
        verify(billRepository, never()).save(any());
        verify(securityAuditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void editingABillAlreadyInTheRequestedStateIsRefused() {
        bill("completed", "success");

        assertThatThrownBy(() -> service.updateOrderStatus(RESTAURANT, BILL_ID, "completed", "no change"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");

        verify(billRepository, never()).save(any());
    }

    @Test
    void aGatewaySettledBillIsNeverRewritten() {
        Bill bill = bill("draft", "paid");

        assertThatThrownBy(() -> service.updateOrderStatus(RESTAURANT, BILL_ID, "completed", "looks settled"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment gateway");

        assertThat(bill.getStatusVersion()).isEqualTo(3);
        verify(billRepository, never()).save(any());
    }

    @Test
    void aBillCarryingAGatewayTransactionIsNeverRewrittenEvenWhenStatusLooksLocal() {
        Bill bill = bill("draft", "pending");
        bill.setGatewayTxnId("TX-123");

        assertThatThrownBy(() -> service.updateOrderStatus(RESTAURANT, BILL_ID, "completed", "looks settled"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnknownStatusIsRejectedBeforeAnyWrite() {
        assertThatThrownBy(() -> service.updateOrderStatus(RESTAURANT, BILL_ID, "refunded", "typo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderStatus must be one of");

        verify(billRepository, never()).save(any());
    }

    @Test
    void aBillFromAnotherRestaurantIsNotFound() {
        Bill other = new Bill();
        other.setId(BILL_ID);
        other.setRestaurantId(RESTAURANT + 1);
        other.setOrderStatus("draft");
        when(billRepository.findByIdForUpdate(BILL_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateOrderStatus(RESTAURANT, BILL_ID, "completed", "cross tenant"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }

    // ─── Payment mode ─────────────────────────────────────────────────────────────

    @Test
    void changingTheModeBumpsVersionAuditsAndKeepsThePaymentRowInStep() {
        Bill bill = bill("completed", "success");
        BillPayment payment = payment("upi");

        service.updatePaymentMode(RESTAURANT, BILL_ID, "cash", null, null, "punched UPI by mistake");

        assertThat(bill.getPaymentMode()).isEqualTo("cash");
        assertThat(bill.getStatusVersion()).isEqualTo(4);
        assertThat(payment.getPaymentMode()).isEqualTo("cash");
        verify(billPaymentRepository).save(payment);
        verify(securityAuditService).record(
                "BILL_PAYMODE_CHANGE", "upi->cash",
                "11111111-2222-3333-4444-555555555555", "punched UPI by mistake");
    }

    @Test
    void aSplitBillRefusesAModeChangeInsteadOfDivergingFromItsPaymentRows() {
        Bill bill = bill("completed", "success");
        BillPayment first = new BillPayment();
        first.setPaymentMode("part_payment_upi_cash");
        BillPayment second = new BillPayment();
        second.setPaymentMode("part_payment_upi_cash");
        when(billPaymentRepository.findByRestaurantIdAndServerBillIdIn(RESTAURANT, List.of(BILL_ID)))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.updatePaymentMode(RESTAURANT, BILL_ID, "cash", null, null, "fix"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 payment records");

        assertThat(bill.getPaymentMode()).isEqualTo("upi");
        verify(billRepository, never()).save(any());
    }

    @Test
    void aPartPaymentModeStoresTheSplitAndLeavingItClearsTheStaleAmounts() {
        Bill bill = bill("completed", "success");
        when(billPaymentRepository.findByRestaurantIdAndServerBillIdIn(RESTAURANT, List.of(BILL_ID)))
                .thenReturn(List.of());

        service.updatePaymentMode(RESTAURANT, BILL_ID, "part_payment_upi_cash",
                new BigDecimal("200.00"), new BigDecimal("150.00"), "half UPI half cash");
        assertThat(bill.getPartAmount1()).isEqualByComparingTo("200.00");
        assertThat(bill.getPartAmount2()).isEqualByComparingTo("150.00");

        // …and moving back to a single mode must not leave the split amounts behind.
        service.updatePaymentMode(RESTAURANT, BILL_ID, "cash", null, null, "customer paid the rest in cash");
        assertThat(bill.getPaymentMode()).isEqualTo("cash");
        assertThat(bill.getPartAmount1()).isNull();
        assertThat(bill.getPartAmount2()).isNull();
        assertThat(bill.getStatusVersion()).isEqualTo(5);
    }

    @Test
    void anUnknownPaymentModeIsRejected() {
        bill("completed", "success");

        assertThatThrownBy(() -> service.updatePaymentMode(RESTAURANT, BILL_ID, "bitcoin", null, null, "typo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paymentMode must be one of");

        verify(billRepository, never()).save(any());
    }

    @Test
    void aCancelledBillsModeCannotBeChanged() {
        bill("cancelled", "cancelled");

        assertThatThrownBy(() -> service.updatePaymentMode(RESTAURANT, BILL_ID, "cash", null, null, "fix"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled bill");

        verify(billRepository, never()).save(any());
    }

    @Test
    void theModeAlreadyInUseIsRefusedRatherThanRewritten() {
        Bill bill = bill("completed", "success");

        assertThatThrownBy(() -> service.updatePaymentMode(RESTAURANT, BILL_ID, "upi", null, null, "no change"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already uses upi");

        assertThat(bill.getStatusVersion()).isEqualTo(3);
        ArgumentCaptor<Bill> saved = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository, never()).save(saved.capture());
    }
}
