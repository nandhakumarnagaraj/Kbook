package com.khanabook.saas.controller;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceConfigController {

    private final RestaurantProfileRepository profileRepo;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        Map<String, Object> config = new HashMap<>();
        config.put("zomatoEnabled", profile.getZomatoEnabled() != null && profile.getZomatoEnabled());
        config.put("zomatoApiKeyMasked", maskKey(profile.getZomatoApiKey()));
        config.put("zomatoOutletId", profile.getZomatoOutletId());
        config.put("zomatoWebhookUrl", "/api/v2/marketplace/webhook/zomato");
        config.put("swiggyEnabled", profile.getSwiggyEnabled() != null && profile.getSwiggyEnabled());
        config.put("swiggyApiKeyMasked", maskKey(profile.getSwiggyApiKey()));
        config.put("swiggyStoreId", profile.getSwiggyStoreId());
        config.put("swiggyWebhookUrl", "/api/v2/marketplace/webhook/swiggy");
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        if (data.containsKey("zomatoEnabled")) profile.setZomatoEnabled((Boolean) data.get("zomatoEnabled"));
        if (data.containsKey("zomatoApiKey")) profile.setZomatoApiKey((String) data.get("zomatoApiKey"));
        if (data.containsKey("zomatoWebhookSecret")) profile.setZomatoWebhookSecret((String) data.get("zomatoWebhookSecret"));
        if (data.containsKey("zomatoOutletId")) profile.setZomatoOutletId((String) data.get("zomatoOutletId"));
        if (data.containsKey("swiggyEnabled")) profile.setSwiggyEnabled((Boolean) data.get("swiggyEnabled"));
        if (data.containsKey("swiggyApiKey")) profile.setSwiggyApiKey((String) data.get("swiggyApiKey"));
        if (data.containsKey("swiggyWebhookSecret")) profile.setSwiggyWebhookSecret((String) data.get("swiggyWebhookSecret"));
        if (data.containsKey("swiggyStoreId")) profile.setSwiggyStoreId((String) data.get("swiggyStoreId"));
        
        long now = System.currentTimeMillis();
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        profile.setDeviceId("server");
        
        profileRepo.save(profile);
        return getConfig();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck(
            @RequestParam(defaultValue = "SWIGGY") String platform) {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Map<String, Object> result = new HashMap<>();
        result.put("platform", platform);

        if ("SWIGGY".equalsIgnoreCase(platform)) {
            boolean enabled = profile.getSwiggyEnabled() != null && profile.getSwiggyEnabled();
            String apiKey = profile.getSwiggyApiKey();
            String storeId = profile.getSwiggyStoreId();
            boolean hasApiKey = apiKey != null && !apiKey.isBlank();
            boolean hasStoreId = storeId != null && !storeId.isBlank();
            boolean apiKeyFormatValid = hasApiKey && apiKey.length() >= 8;
            boolean storeIdFormatValid = hasStoreId && storeId.matches("^[A-Za-z0-9_-]{3,64}$");

            result.put("enabled", enabled);
            result.put("apiKeyConfigured", hasApiKey);
            result.put("apiKeyFormatValid", apiKeyFormatValid);
            result.put("storeIdConfigured", hasStoreId);
            result.put("storeIdFormatValid", storeIdFormatValid);
            result.put("webhookRegistered", profile.getSwiggyWebhookSecret() != null && !profile.getSwiggyWebhookSecret().isBlank());
            result.put("webhookUrl", "/api/v2/marketplace/webhook/swiggy");
            result.put("healthy", enabled && apiKeyFormatValid && storeIdFormatValid);
        } else if ("ZOMATO".equalsIgnoreCase(platform)) {
            boolean enabled = profile.getZomatoEnabled() != null && profile.getZomatoEnabled();
            String apiKey = profile.getZomatoApiKey();
            String outletId = profile.getZomatoOutletId();
            boolean hasApiKey = apiKey != null && !apiKey.isBlank();
            boolean hasOutletId = outletId != null && !outletId.isBlank();
            boolean apiKeyFormatValid = hasApiKey && apiKey.length() >= 8;
            boolean outletIdFormatValid = hasOutletId && outletId.matches("^[A-Za-z0-9_-]{3,64}$");

            result.put("enabled", enabled);
            result.put("apiKeyConfigured", hasApiKey);
            result.put("apiKeyFormatValid", apiKeyFormatValid);
            result.put("outletIdConfigured", hasOutletId);
            result.put("outletIdFormatValid", outletIdFormatValid);
            result.put("webhookRegistered", profile.getZomatoWebhookSecret() != null && !profile.getZomatoWebhookSecret().isBlank());
            result.put("webhookUrl", "/api/v2/marketplace/webhook/zomato");
            result.put("healthy", enabled && apiKeyFormatValid && outletIdFormatValid);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid platform. Use SWIGGY or ZOMATO."));
        }

        return ResponseEntity.ok(result);
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
