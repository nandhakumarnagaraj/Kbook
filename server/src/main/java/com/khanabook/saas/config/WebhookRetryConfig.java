package com.khanabook.saas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.service.EasebuzzWebhookService;
import com.khanabook.saas.service.WebhookRetryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebhookRetryConfig {

    private final WebhookRetryService webhookRetryService;
    private final EasebuzzWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void registerExecutors() {
        webhookRetryService.registerWebhookExecutor("PAYMENT", payload -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> data = objectMapper.readValue(payload, Map.class);
                Map<String, Object> result = webhookService.handlePaymentWebhook(data);
                return "received".equals(result.get("status"));
            } catch (Exception e) {
                log.error("Failed to retry PAYMENT webhook", e);
                return false;
            }
        });

        webhookRetryService.registerWebhookExecutor("SUB_MERCHANT", payload -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                Map<String, Object> result = webhookService.handleSubMerchantWebhook(data);
                return "received".equals(result.get("status"));
            } catch (Exception e) {
                log.error("Failed to retry SUB_MERCHANT webhook", e);
                return false;
            }
        });

        webhookRetryService.registerWebhookExecutor("PAYOUT", payload -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> data = objectMapper.readValue(payload, Map.class);
                Map<String, Object> result = webhookService.handlePayoutWebhook(data);
                return "received".equals(result.get("status"));
            } catch (Exception e) {
                log.error("Failed to retry PAYOUT webhook", e);
                return false;
            }
        });

        webhookRetryService.registerWebhookExecutor("REFUND", payload -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> data = objectMapper.readValue(payload, Map.class);
                Map<String, Object> result = webhookService.handleRefundWebhook(data);
                return "received".equals(result.get("status"));
            } catch (Exception e) {
                log.error("Failed to retry REFUND webhook", e);
                return false;
            }
        });

        log.info("Registered 4 webhook executors: PAYMENT, SUB_MERCHANT, PAYOUT, REFUND");
    }
}
