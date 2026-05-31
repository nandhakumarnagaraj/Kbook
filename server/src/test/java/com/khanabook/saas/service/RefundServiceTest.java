package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
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
    @Mock private EmailNotificationService emailNotificationService;

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
    }

    @Test
    void initiatePartialRefund_firstRefund_succeeds() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));
        java.util.Map<String, Object> refundResult = new java.util.LinkedHashMap<>();
        refundResult.put("status", "success");
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("300")), anyString()))
                .thenReturn(refundResult);
        when(billRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = refundService.initiatePartialRefund(1L, 100L, new BigDecimal("300"), "QUALITY_ISSUE");

        assertThat(result.get("refundStatus")).isEqualTo("initiated");
        assertThat(result.get("totalRefunded")).isEqualTo(new BigDecimal("300"));
        assertThat(result.get("remainingRefundable")).isEqualTo(new BigDecimal("700"));
    }

    @Test
    void initiatePartialRefund_secondPartialRefund_succeeds() {
        testBill.setRefundAmount(new BigDecimal("300"));
        testBill.setPaymentStatus("partially_refunded");
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));
        java.util.Map<String, Object> refundResult = new java.util.LinkedHashMap<>();
        refundResult.put("status", "success");
        when(easebuzzPaymentService.initiateRefund(eq(1L), eq(new BigDecimal("200")), anyString()))
                .thenReturn(refundResult);
        when(billRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = refundService.initiatePartialRefund(1L, 100L, new BigDecimal("200"), "CUSTOMER_REQUEST");

        assertThat(result.get("totalRefunded")).isEqualTo(new BigDecimal("500"));
        assertThat(result.get("remainingRefundable")).isEqualTo(new BigDecimal("500"));
    }

    @Test
    void initiatePartialRefund_exceedsRemaining_throwsException() {
        testBill.setRefundAmount(new BigDecimal("800"));
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("300"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceeds remaining refundable");
    }

    @Test
    void initiatePartialRefund_alreadyFullyRefunded_throwsException() {
        testBill.setRefundAmount(new BigDecimal("1000"));
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("100"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already fully refunded");
    }

    @Test
    void initiatePartialRefund_notPaidBill_throwsException() {
        testBill.setPaymentStatus("pending");
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("100"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    void initiatePartialRefund_negativeAmount_throwsException() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));

        assertThatThrownBy(() -> refundService.initiatePartialRefund(1L, 100L, new BigDecimal("-100"), "test"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be positive");
    }
}
