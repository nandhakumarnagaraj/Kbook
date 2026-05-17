package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MarketplaceWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceWebhookController.class);
    private final MarketplaceOrderRepository orderRepo;
    private final RestaurantProfileRepository profileRepo;

    @PostMapping("/marketplace/webhook/swiggy")
    @Transactional
    public ResponseEntity<Map<String, Object>> swiggyWebhook(@RequestBody Map<String, Object> payload) {
        return processOrder("SWIGGY", payload);
    }

    @PostMapping("/marketplace/webhook/zomato")
    @Transactional
    public ResponseEntity<Map<String, Object>> zomatoWebhook(@RequestBody Map<String, Object> payload) {
        return processOrder("ZOMATO", payload);
    }

    private ResponseEntity<Map<String, Object>> processOrder(String platform, Map<String, Object> payload) {
        try {
            String platformOrderId = (String) payload.getOrDefault("order_id", "");
            String storeId = (String) payload.getOrDefault("store_id", "");
            Long restaurantId = resolveRestaurantId(platform, storeId);
            if (restaurantId == null) {
                log.warn("No restaurant found for {} storeId={}", platform, storeId);
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Unknown store"));
            }
            if (orderRepo.findByPlatformAndPlatformOrderId(platform, platformOrderId).isPresent()) {
                return ResponseEntity.ok(Map.of("status", "duplicate"));
            }
            long now = System.currentTimeMillis();
            MarketplaceOrder order = new MarketplaceOrder();
            order.setRestaurantId(restaurantId);
            order.setPlatform(platform);
            order.setPlatformOrderId(platformOrderId);
            order.setPlatformStatus((String) payload.getOrDefault("status", "received"));
            order.setCustomerName((String) payload.getOrDefault("customer_name", ""));
            order.setCustomerPhone((String) payload.getOrDefault("customer_phone", ""));
            order.setCustomerAddress((String) payload.getOrDefault("delivery_address", ""));
            order.setTotalAmount(parseAmount(payload.get("total_amount")));
            order.setSubtotal(parseAmount(payload.get("subtotal")));
            order.setTaxAmount(parseAmount(payload.get("tax_amount")));
            order.setPaymentMode((String) payload.getOrDefault("payment_mode", ""));
            order.setRawPayload(payload.toString());
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orderRepo.save(order);
            log.info("{} order received orderId={} restaurantId={}", platform, platformOrderId, restaurantId);
            return ResponseEntity.ok(Map.of("status", "received", "order_id", order.getId()));
        } catch (Exception e) {
            log.error("Failed to process {} webhook: {}", platform, e.getMessage());
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private Long resolveRestaurantId(String platform, String storeId) {
        if (storeId == null || storeId.isBlank()) return null;
        return profileRepo.findAll().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .filter(p -> storeId.equals(
                    "SWIGGY".equals(platform) ? p.getSwiggyStoreId() : p.getZomatoOutletId()))
                .map(RestaurantProfile::getRestaurantId)
                .findFirst().orElse(null);
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
