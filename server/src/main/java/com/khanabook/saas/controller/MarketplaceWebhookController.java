package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
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

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> processOrder(String platform, Map<String, Object> payload) {
        try {
            Map<String, Object> customer = (Map<String, Object>) payload.get("customer");
            Map<String, Object> orderObj = (Map<String, Object>) payload.get("order");
            if (customer == null || orderObj == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing customer or order"));
            }
            Map<String, Object> details = (Map<String, Object>) orderObj.get("details");
            Map<String, Object> store = (Map<String, Object>) orderObj.get("store");
            Map<String, Object> customerAddress = (Map<String, Object>) customer.get("address");
            List<Map<String, Object>> payments = (List<Map<String, Object>>) orderObj.get("payment");

            String storeId = strVal(nested(store, "merchant_ref_id"));
            Number orderIdNum = (Number) nested(details, "id");
            String platformOrderId = orderIdNum != null ? orderIdNum.toString() : "";

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
            order.setPlatformStatus(strVal(nested(details, "order_state"), "received"));
            order.setCustomerName(strVal(nested(customer, "name")));
            order.setCustomerPhone(strVal(nested(customer, "phone")));
            order.setCustomerAddress(strVal(nested(customerAddress, "line_1")));
            order.setTotalAmount(parseAmount(nested(details, "order_total")));
            order.setSubtotal(parseAmount(nested(details, "order_subtotal")));
            order.setTaxAmount(computeTotalTax(details));
            order.setPaymentMode(payments != null && !payments.isEmpty() ? strVal(payments.get(0).get("option")) : "");
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
        if ("SWIGGY".equals(platform)) {
            return profileRepo.findRestaurantIdBySwiggyStoreIdAndIsDeletedFalse(storeId).orElse(null);
        }
        return profileRepo.findRestaurantIdByZomatoOutletIdAndIsDeletedFalse(storeId).orElse(null);
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal computeTotalTax(Map<String, Object> details) {
        BigDecimal total = BigDecimal.ZERO;
        try {
            Object orderLevel = details.get("order_level_total_taxes");
            Object itemLevel = details.get("item_level_total_taxes");
            if (orderLevel instanceof Number) total = total.add(BigDecimal.valueOf(((Number) orderLevel).doubleValue()));
            if (itemLevel instanceof Number) total = total.add(BigDecimal.valueOf(((Number) itemLevel).doubleValue()));
        } catch (Exception ignored) {}
        return total;
    }

    private static Object nested(Map<String, Object> map, String key) {
        return map != null ? map.get(key) : null;
    }

    private static String strVal(Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private static String strVal(Object value, String fallback) {
        String s = strVal(value);
        return s.isEmpty() ? fallback : s;
    }
}
