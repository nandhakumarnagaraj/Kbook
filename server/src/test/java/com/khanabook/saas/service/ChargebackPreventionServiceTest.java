package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.Chargeback;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.ChargebackRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
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
class ChargebackPreventionServiceTest {

    @Mock private ChargebackRepository chargebackRepository;
    @Mock private BillRepository billRepository;
    @Mock private EmailNotificationService emailNotificationService;
    @Mock private RestaurantProfileRepository profileRepository;

    @InjectMocks
    private ChargebackPreventionService chargebackService;

    private Bill testBill;

    @BeforeEach
    void setup() {
        testBill = new Bill();
        testBill.setId(1L);
        testBill.setRestaurantId(100L);
        testBill.setTotalAmount(new BigDecimal("5000"));
        testBill.setCustomerName("Test Customer");
        testBill.setCustomerWhatsapp("9876543210");
        testBill.setPaymentMode("upi");
        testBill.setGatewayTxnId("TXN123");
        testBill.setCreatedAt(System.currentTimeMillis());
    }

    @Test
    void scoreTransaction_lowRisk_returnsLowScore() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(100L)).thenReturn(java.util.List.of());

        var result = chargebackService.scoreTransaction(1L);

        assertThat(result.get("risk")).isEqualTo("low");
        assertThat(((Number) result.get("score")).doubleValue()).isLessThan(25);
    }

    @Test
    void scoreTransaction_highAmount_addsRiskPoints() {
        testBill.setTotalAmount(new BigDecimal("15000"));
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(100L)).thenReturn(java.util.List.of());

        var result = chargebackService.scoreTransaction(1L);

        assertThat(((Number) result.get("score")).doubleValue()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void scoreTransaction_missingCustomerInfo_addsRiskPoints() {
        testBill.setCustomerName(null);
        testBill.setCustomerWhatsapp(null);
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(100L)).thenReturn(java.util.List.of());

        var result = chargebackService.scoreTransaction(1L);

        assertThat(((Number) result.get("score")).doubleValue()).isGreaterThanOrEqualTo(25);
    }

    @Test
    void scoreTransaction_unknownBill_returnsZeroScore() {
        when(billRepository.findById(999L)).thenReturn(Optional.empty());

        var result = chargebackService.scoreTransaction(999L);

        assertThat(result.get("score")).isEqualTo(0);
        assertThat(result.get("risk")).isEqualTo("unknown");
    }

    @Test
    void createChargeback_savesChargebackWithEmailNotification() {
        when(billRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(100L)).thenReturn(java.util.List.of());
        when(chargebackRepository.save(any(Chargeback.class))).thenAnswer(i -> i.getArgument(0));

        chargebackService.createChargeback(100L, 1L, "fraudulent", "Test chargeback");

        verify(chargebackRepository).save(any(Chargeback.class));
    }

    @Test
    void resolveChargeback_updatesStatus() {
        Chargeback cb = new Chargeback();
        cb.setId(1L);
        cb.setRestaurantId(100L);
        cb.setStatus("open");
        when(chargebackRepository.findById(1L)).thenReturn(Optional.of(cb));
        when(chargebackRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = chargebackService.resolveChargeback(100L, 1L, "merchant_accepted");

        assertThat(result.get("status")).isEqualTo("resolved");
    }

    @Test
    void resolveChargeback_unauthorizedThrows() {
        Chargeback cb = new Chargeback();
        cb.setId(1L);
        cb.setRestaurantId(200L);
        when(chargebackRepository.findById(1L)).thenReturn(Optional.of(cb));

        assertThatThrownBy(() -> chargebackService.resolveChargeback(100L, 1L, "test"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
