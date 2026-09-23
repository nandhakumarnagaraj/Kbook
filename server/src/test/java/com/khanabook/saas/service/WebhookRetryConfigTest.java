package com.khanabook.saas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import com.khanabook.saas.feature.payments.service.RefundService;
import com.khanabook.saas.feature.payments.service.WebhookRetryConfig;
import com.khanabook.saas.feature.payments.service.WebhookRetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookRetryConfigTest {
    @Mock private WebhookRetryService retryService;
    @Mock private EasebuzzWebhookService webhookService;
    @Mock private RefundService refundService;

    @Test
    void delayedRefundExecutorInitiatesRecordedRefund() {
        WebhookRetryConfig config = new WebhookRetryConfig(
                retryService, webhookService, refundService, new ObjectMapper());
        config.registerExecutors();
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Function<String, Boolean>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(Function.class);
        verify(retryService).registerWebhookExecutor(eq("DELAYED_REFUND"), captor.capture());
        when(refundService.initiatePartialRefund(20L, 10L,
                new java.math.BigDecimal("100.00"), "ORDER_CANCELLED"))
                .thenReturn(Map.of("status", "success"));

        boolean success = captor.getValue().apply(
                "{\"billId\":20,\"restaurantId\":10,\"amount\":\"100.00\",\"reason\":\"ORDER_CANCELLED\"}");

        assertThat(success).isTrue();
        verify(refundService).initiatePartialRefund(20L, 10L,
                new java.math.BigDecimal("100.00"), "ORDER_CANCELLED");
    }
}
