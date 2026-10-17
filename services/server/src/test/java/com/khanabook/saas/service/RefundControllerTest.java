package com.khanabook.saas.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.payments.controller.RefundController;
import com.khanabook.saas.feature.payments.service.RefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rejectedRefundIsReturnedAsBadRequest() {
        TenantContext.setCurrentTenant(42L);
        when(refundService.initiatePartialRefund(7L, 42L, new java.math.BigDecimal("10.00"), "OTHER"))
                .thenReturn(Map.of("status", "failure", "refundStatus", "failed", "error", "Refund rejected"));

        var response = new RefundController(refundService).initiateRefund(
                7L, Map.of("refundAmount", "10.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("refundStatus", "failed");
    }
}
