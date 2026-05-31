package com.khanabook.saas.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeveloperPortalService {

    public Map<String, Object> getApiDocumentation() {
        Map<String, Object> docs = new LinkedHashMap<>();
        docs.put("version", "2.0.0");
        docs.put("baseUrl", "/api/v2");
        docs.put("endpoints", List.of(
            endpointGroup("Orders", List.of(
                apiEndpoint("GET", "/bills", "List all orders", "read"),
                apiEndpoint("POST", "/bills", "Create a new order", "write"),
                apiEndpoint("PUT", "/bills/{id}/status", "Update order status", "write")
            )),
            endpointGroup("Payments", List.of(
                apiEndpoint("POST", "/payments/easebuzz/create-order", "Initiate payment", "write"),
                apiEndpoint("GET", "/payments/easebuzz/status/{billId}", "Check payment status", "read"),
                apiEndpoint("POST", "/payments/easebuzz/refund", "Initiate refund", "write")
            )),
            endpointGroup("Menu", List.of(
                apiEndpoint("GET", "/menu-items", "List menu items", "read"),
                apiEndpoint("POST", "/menu-items", "Create menu item", "write"),
                apiEndpoint("PUT", "/menu-items/{id}", "Update menu item", "write")
            )),
            endpointGroup("Webhooks", List.of(
                apiEndpoint("POST", "/payments/easebuzz/webhook/payment", "Payment webhook", "system"),
                apiEndpoint("POST", "/payments/easebuzz/webhook/sub-merchant", "Sub-merchant webhook", "system")
            ))
        ));
        docs.put("authentication", "Bearer JWT token in Authorization header");
        docs.put("rateLimits", Map.of("free", "100 requests/minute", "standard", "1000 requests/minute", "premium", "10000 requests/minute"));
        return docs;
    }

    public Map<String, Object> getWebhookEvents() {
        Map<String, Object> events = new LinkedHashMap<>();
        events.put("payment.completed", "Fired when payment is successful");
        events.put("payment.failed", "Fired when payment fails");
        events.put("refund.initiated", "Fired when refund is initiated");
        events.put("refund.completed", "Fired when refund is processed");
        events.put("order.created", "Fired when new order is placed");
        events.put("order.completed", "Fired when order is marked completed");
        events.put("order.cancelled", "Fired when order is cancelled");
        events.put("sub_merchant.kyc_status", "Fired when KYC status changes");
        events.put("settlement.completed", "Fired when settlement is processed");
        return events;
    }

    public Map<String, Object> getRateLimitStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("tier", "free");
        status.put("limit", "100/minute");
        status.put("remaining", 87);
        status.put("resetAt", System.currentTimeMillis() + 60000);
        status.put("used", 13);
        return status;
    }

    private Map<String, Object> endpointGroup(String name, List<Map<String, String>> endpoints) {
        return Map.of("group", name, "endpoints", endpoints);
    }

    private Map<String, String> apiEndpoint(String method, String path, String description, String auth) {
        return Map.of("method", method, "path", path, "description", description, "auth", auth);
    }
}