package com.khanabook.saas.feature.payments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.billing.service.PostSplitService;
import com.khanabook.saas.feature.payments.service.EasebuzzWebhookService;
import com.khanabook.saas.feature.payments.service.WebhookRetryService;
import com.khanabook.saas.core.exception.BusinessRuleException;
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
    private final RefundService refundService;
    private final PostSplitService postSplitService;
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

        webhookRetryService.registerWebhookExecutor("DELAYED_REFUND", payload -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                Long billId = Long.valueOf(data.get("billId").toString());
                Long restaurantId = Long.valueOf(data.get("restaurantId").toString());
                java.math.BigDecimal amount = new java.math.BigDecimal(data.get("amount").toString());
                String reason = data.getOrDefault("reason", "ORDER_CANCELLED").toString();
                Map<String, Object> result = refundService.initiatePartialRefund(
                        billId, restaurantId, amount, reason);
                return "success".equals(result.get("status"));
            } catch (BusinessRuleException e) {
                // A prior execution may have committed the full refund attempt
                // before the worker could mark this queue job complete.
                if (e.getMessage() != null && e.getMessage().contains("fully refunded or reserved")) {
                    log.info("Delayed refund already completed or reserved; marking work complete");
                    return true;
                }
                log.error("Delayed refund no longer satisfies business rules", e);
                return false;
            } catch (Exception e) {
                log.error("Failed to execute delayed refund", e);
                return false;
            }
        });

        webhookRetryService.registerWebhookExecutor("POST_SPLIT", payload -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(payload, Map.class);
                return postSplitService.createPostSplit(
                        Long.valueOf(data.get("billId").toString()),
                        data.get("easebuzzId").toString(),
                        data.get("txnid").toString());
            } catch (Exception e) {
                log.error("Failed to execute post-split work", e);
                return false;
            }
        });

        log.info("Registered 6 durable executors: PAYMENT, SUB_MERCHANT, PAYOUT, REFUND, DELAYED_REFUND, POST_SPLIT");
    }
}
