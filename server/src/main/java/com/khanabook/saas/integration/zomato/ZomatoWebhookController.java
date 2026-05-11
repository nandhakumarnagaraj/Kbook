package com.khanabook.saas.integration.zomato;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.integration.MarketplaceConfigService;
import com.khanabook.saas.integration.MarketplaceWebhookEvent;
import com.khanabook.saas.integration.MarketplaceWebhookEventRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks/zomato")
@RequiredArgsConstructor
public class ZomatoWebhookController {

    private final ZomatoIntegrationService integrationService;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final MarketplaceConfigService marketplaceConfigService;
    private final MarketplaceWebhookEventRepository webhookEventRepository;

    @PostMapping("/{restaurantId}/order")
    public ResponseEntity<Map<String, Object>> receiveOrderWebhook(
            @PathVariable Long restaurantId,
            @RequestHeader(value = "X-Zomato-Signature", required = false) String signature,
            @RequestBody String payload
    ) {
        try {
            log.info("Received Zomato webhook for restaurant {}", restaurantId);

            RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
                    .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
            String secret = marketplaceConfigService.decryptZomatoWebhookSecret(profile);
            boolean signatureValid = validateWebhookSignature(secret, payload, signature);

            if (!signatureValid) {
                saveWebhookEvent(restaurantId, payload, false, false);
                log.warn("Invalid Zomato webhook signature for restaurant {}", restaurantId);
                return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
            }

            integrationService.processWebhook(restaurantId, payload);
            saveWebhookEvent(restaurantId, payload, true, true);
            return ResponseEntity.ok(Map.of("success", true, "message", "Order processed successfully"));

        } catch (Exception e) {
            saveWebhookEvent(restaurantId, payload, true, false);
            log.error("Error processing Zomato webhook for restaurant {}: {}", restaurantId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    private boolean validateWebhookSignature(String secret, String payload, String signature) {
        if (secret == null || secret.isBlank()) {
            log.warn("Webhook secret not configured — rejecting webhook");
            return false;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            String expectedSignature = hmacSha256(payload, secret);
            byte[] expectedBytes = expectedSignature.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] actualBytes = signature.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.security.MessageDigest.isEqual(expectedBytes, actualBytes);
        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }

    private String hmacSha256(String data, String key) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes());
        StringBuilder result = new StringBuilder();
        for (byte b : rawHmac) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private void saveWebhookEvent(Long restaurantId, String payload, boolean signatureValid, boolean processed) {
        MarketplaceWebhookEvent event = new MarketplaceWebhookEvent();
        event.setRestaurantId(restaurantId);
        event.setPlatform("ZOMATO");
        event.setPlatformOrderId(extractPlatformOrderId(payload));
        event.setSignatureValid(signatureValid);
        event.setProcessed(processed);
        event.setPayload(payload);
        event.setReceivedAt(System.currentTimeMillis());
        webhookEventRepository.save(event);
    }

    private String extractPlatformOrderId(String payload) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            String orderId = node.path("order_id").asText(node.path("id").asText(""));
            return orderId.isBlank() ? null : orderId;
        } catch (Exception ex) {
            return null;
        }
    }
}
