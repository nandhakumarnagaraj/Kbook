package com.khanabook.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.payments.service.EasebuzzPaymentService;
import com.khanabook.saas.feature.payments.service.RefundService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.core.exception.BusinessRuleException;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.RefundAttemptRepository;
import com.khanabook.saas.feature.payments.data.WebhookRetryJob;
import com.khanabook.saas.feature.payments.service.WebhookRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private EasebuzzPaymentService easebuzzPaymentService;
    @Mock private BillRepository billRepository;
    @Mock private RefundAttemptRepository refundAttemptRepository;
    @Mock private WebhookRetryService webhookRetryService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RefundService refundService;

    private Bill testBill;

    @BeforeEach
    void setup() {
        testBill = new Bill();
        testBill.setId(1L);
        testBill.setRestaurantId(100L);
        testBill.setTotalAmount(new BigDecimal("1000"));
        testBill.setRefundAmount(BigDecimal.ZERO);
        testBill.setPaymentStatus("paid");
        testBill.setGatewayTxnId("TXN123");
        testBill.setCustomerName("Test Customer");
        testBill.setCustomerWhatsapp("9876543210");
        testBill.setCreatedAt(System.currentTimeMillis());
        lenient().when(refundAttemptRepository.sumAmountByBillIdAndStatus(1L, "INITIATED"))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void initiatePartialRefund_firstRefund_succeeds() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        java.util.Map<String, Object> refundResult = new java.util.LinkedHashMap<>();
        refundResult.put("status", "success");
        refundResult.put("merchant_refund_id", "REF_1_0");
        refundResult.put("easebuzz_refund_id", "EB_REF_1");
        refundResult.put("easebuzz_payment_id", "EB_PAY_1");
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("300")), anyString()))
                .thenReturn(refundResult);
        when(billRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = refundService.initiatePartialRefund(1L, 100L, new BigDecimal("300"), "QUALITY_ISSUE");

        assertThat(result.get("refundStatus")).isEqualTo("initiated");
        assertThat(result.get("totalRefunded")).isEqualTo(BigDecimal.ZERO);
        assertThat(result.get("pendingRefund")).isEqualTo(new BigDecimal("300"));
        assertThat(result.get("remainingRefundable")).isEqualTo(new BigDecimal("700"));
        assertThat(testBill.getPaymentStatus()).isEqualTo("paid");
        assertThat(testBill.getRefundAmount()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void initiatePartialRefund_secondPartialRefund_succeeds() {
        testBill.setRefundAmount(new BigDecimal("300"));
        testBill.setPaymentStatus("partially_refunded");
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        java.util.Map<String, Object> refundResult = new java.util.LinkedHashMap<>();
        refundResult.put("status", "success");
        refundResult.put("merchant_refund_id", "REF_1_300");
        refundResult.put("easebuzz_refund_id", "EB_REF_2");
        refundResult.put("easebuzz_payment_id", "EB_PAY_1");
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("200")), anyString()))
                .thenReturn(refundResult);
        when(billRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = refundService.initiatePartialRefund(1L, 100L, new BigDecimal("200"), "CUSTOMER_REQUEST");

        assertThat(result.get("totalRefunded")).isEqualTo(new BigDecimal("300"));
        assertThat(result.get("pendingRefund")).isEqualTo(new BigDecimal("200"));
        assertThat(result.get("remainingRefundable")).isEqualTo(new BigDecimal("500"));
    }

    @Test
    void initiatePartialRefund_exceedsRemaining_throwsException() {
        testBill.setRefundAmount(new BigDecimal("800"));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("300"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds remaining refundable");
    }

    @Test
    void initiatePartialRefund_alreadyFullyRefunded_throwsException() {
        testBill.setRefundAmount(new BigDecimal("1000"));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("100"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already fully refunded");
    }

    @Test
    void initiatePartialRefund_notPaidBill_throwsException() {
        testBill.setPaymentStatus("pending");
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("100"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    void initiatePartialRefund_negativeAmount_throwsException() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("-100"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void initiatePartialRefund_reservesPendingAmountWithoutClaimingCompletion() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(refundAttemptRepository.sumAmountByBillIdAndStatus(1L, "INITIATED"))
                .thenReturn(new BigDecimal("300"));
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("200")), anyString()))
                .thenReturn(java.util.Map.of("status", "success", "merchant_refund_id", "REF_1_300",
                        "easebuzz_refund_id", "EB_REF_2", "easebuzz_payment_id", "EB_PAY_1"));

        var result = refundService.initiatePartialRefund(1L, 100L, new BigDecimal("200"), "CUSTOMER_REQUEST");

        assertThat(result).containsEntry("totalRefunded", BigDecimal.ZERO)
                .containsEntry("pendingRefund", new BigDecimal("500"))
                .containsEntry("remainingRefundable", new BigDecimal("500"));
        assertThat(testBill.getPaymentStatus()).isEqualTo("paid");
        assertThat(testBill.getRefundAmount()).isEqualTo(BigDecimal.ZERO);
        verify(refundAttemptRepository).save(any());
    }

    @Test
    void initiateFullRefund_doesNotMarkBillRefundedBeforeCompletion() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("1000")), anyString()))
                .thenReturn(java.util.Map.of("status", "success", "merchant_refund_id", "REF_1_0",
                        "easebuzz_refund_id", "EB_REF_FULL", "easebuzz_payment_id", "EB_PAY_1"));

        var result = refundService.initiatePartialRefund(1L, 100L,
                new BigDecimal("1000"), "ORDER_CANCELLED");

        assertThat(result).containsEntry("refundStatus", "initiated")
                .containsEntry("totalRefunded", BigDecimal.ZERO)
                .containsEntry("pendingRefund", new BigDecimal("1000"))
                .containsEntry("remainingRefundable", BigDecimal.ZERO);
        assertThat(testBill.getPaymentStatus()).isEqualTo("paid");
        assertThat(testBill.getRefundAmount()).isEqualTo(BigDecimal.ZERO);
        verify(refundAttemptRepository).save(any());
    }

    @Test
    void initiatePartialRefund_rejectsAmountAlreadyReservedByPendingAttempt() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(refundAttemptRepository.sumAmountByBillIdAndStatus(1L, "INITIATED"))
                .thenReturn(new BigDecimal("300"));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L,
                new BigDecimal("800"), "CUSTOMER_REQUEST"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds remaining refundable");
        verifyNoInteractions(easebuzzPaymentService);
    }

    @Test
    void initiatePartialRefund_overPreciseAmount_doesNotCallGateway() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("10.001"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at most two decimal places");

        verifyNoInteractions(easebuzzPaymentService);
        verify(billRepository, never()).save(any());
    }

    @Test
    void initiatePartialRefund_gatewayFailureKeepsAccountingState() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("300")), anyString()))
                .thenReturn(java.util.Map.of("status", "failure", "error", "Refund rejected"));

        var result = refundService.initiatePartialRefund(1L, 100L, new BigDecimal("300"), "QUALITY_ISSUE");

        assertThat(result.get("refundStatus")).isEqualTo("failed");
        assertThat(result.get("totalRefunded")).isEqualTo(BigDecimal.ZERO);
        assertThat(result.get("remainingRefundable")).isEqualTo(new BigDecimal("1000"));
        assertThat(testBill.getRefundAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(testBill.getPaymentStatus()).isEqualTo("paid");
        verify(billRepository, never()).save(any());
    }

    @Test
    void cancelAndAutoRefund_reportsGatewayRejectionWithoutClaimingRefundApplied() {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(billRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("1000")), eq("ORDER_CANCELLED")))
                .thenReturn(java.util.Map.of("status", "failure", "error", "Refund rejected"));

        var result = refundService.cancelAndAutoRefund(1L, 100L, "ORDER_CANCELLED", 0);

        assertThat(result).containsEntry("status", "cancelled")
                .containsEntry("refundApplied", false)
                .containsEntry("refundStatus", "failed");
        assertThat(testBill.getRefundAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(testBill.getPaymentStatus()).isEqualTo("paid");
        verify(easebuzzPaymentService, times(1)).initiateRefund(1L, new BigDecimal("1000"), "ORDER_CANCELLED");
    }

    @Test
    void cancelAndAutoRefund_persistsDelayedRefundInsteadOfUsingMemoryTimer() throws Exception {
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testBill));
        when(billRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"billId\":1}");
        WebhookRetryJob job = new WebhookRetryJob();
        job.setId(77L);
        when(webhookRetryService.enqueueAt(eq("DELAYED_REFUND"), eq("{\"billId\":1}"),
                anyLong(), eq("DELAYED_REFUND:100:1"))).thenReturn(job);

        var result = refundService.cancelAndAutoRefund(1L, 100L, "ORDER_CANCELLED", 15);

        assertThat(result).containsEntry("refundScheduled", true)
                .containsEntry("refundJobId", 77L)
                .containsEntry("refundDelayMinutes", 15);
        verify(webhookRetryService).enqueueAt(eq("DELAYED_REFUND"), anyString(),
                longThat(time -> time > System.currentTimeMillis()), eq("DELAYED_REFUND:100:1"));
        verifyNoInteractions(easebuzzPaymentService);
    }
}
