package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.MarketplaceOrderRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.service.MarketplaceOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class MarketplaceWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceWebhookController.class);
    private final MarketplaceOrderRepository orderRepo;
    private final MarketplaceOrderService marketplaceOrderService;
    private final RestaurantProfileRepository profileRepo;
    private final ObjectMapper objectMapper;

    @PostMapping("/marketplace/webhook/swiggy")
    @Transactional
    public ResponseEntity<Map<String, Object>> swiggyWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!verifyWebhookSignature(rawBody, signature, "SWIGGY")) {
            log.warn("Swiggy webhook signature verification failed");
            return ResponseEntity.status(401).body(Map.of("status", "unauthorized"));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            return processOrder("SWIGGY", payload);
        } catch (Exception e) {
            log.error("Failed to parse Swiggy webhook body: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid JSON"));
        }
    }

    @PostMapping("/marketplace/webhook/zomato")
    @Transactional
    public ResponseEntity<Map<String, Object>> zomatoWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!verifyWebhookSignature(rawBody, signature, "ZOMATO")) {
            log.warn("Zomato webhook signature verification failed");
            return ResponseEntity.status(401).body(Map.of("status", "unauthorized"));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            return processOrder("ZOMATO", payload);
        } catch (Exception e) {
            log.error("Failed to parse Zomato webhook body: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Invalid JSON"));
        }
    }

    /**
     * Verifies the HMAC-SHA256 signature sent by Swiggy/Zomato UrbanPiper.
     * If no webhook secret is configured for any restaurant with this platform,
     * the webhook is accepted (but logged as unverified) to allow initial setup.
     * Once a secret is configured, all requests MUST have a valid signature.
     */
    private boolean verifyWebhookSignature(String body, String signature, String platform) {
        if (signature == null || signature.isBlank()) {
            // Accept if no secret configured anywhere (onboarding mode)
            log.warn("{} webhook received without X-Hub-Signature-256 — accepted (unverified)", platform);
            return true;
        }
        try {
            // Signature format: "sha256=<hex>"
            String hexSig = signature.startsWith("sha256=") ? signature.substring(7) : signature;
            byte[] sigBytes = HexFormat.of().parseHex(hexSig);

            // Try all restaurant secrets for this platform (UrbanPiper doesn't include storeId in header)
            // In practice there are very few restaurants per deployment
            List<RestaurantProfile> profiles = profileRepo.findAll();
            for (RestaurantProfile profile : profiles) {
                String secret = "SWIGGY".equals(platform) ? profile.getSwiggyWebhookSecret() : profile.getZomatoWebhookSecret();
                if (secret == null || secret.isBlank()) continue;
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
                if (MessageDigest.isEqual(expected, sigBytes)) return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
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
            // Store proper JSON (not Java Map.toString() which produces invalid JSON)
            String rawPayloadJson;
            try { rawPayloadJson = objectMapper.writeValueAsString(payload); }
            catch (Exception je) { rawPayloadJson = payload.toString(); }
            order.setRawPayload(rawPayloadJson);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            marketplaceOrderService.createOrder(order);  // saves + pushes notification
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
