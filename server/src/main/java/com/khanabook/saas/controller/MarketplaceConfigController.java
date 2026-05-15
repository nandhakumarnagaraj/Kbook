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
        config.put("zomatoWebhookUrl", "/api/v1/marketplace/webhook/zomato");
        config.put("swiggyEnabled", profile.getSwiggyEnabled() != null && profile.getSwiggyEnabled());
        config.put("swiggyApiKeyMasked", maskKey(profile.getSwiggyApiKey()));
        config.put("swiggyStoreId", profile.getSwiggyStoreId());
        config.put("swiggyWebhookUrl", "/api/v1/marketplace/webhook/swiggy");
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
        if (data.containsKey("swiggyEnabled")) profile.setSwiggyEnabled((Boolean) data.get("swiggyEnabled"));
        if (data.containsKey("swiggyApiKey")) profile.setSwiggyApiKey((String) data.get("swiggyApiKey"));
        if (data.containsKey("swiggyWebhookSecret")) profile.setSwiggyWebhookSecret((String) data.get("swiggyWebhookSecret"));
        profileRepo.save(profile);
        return getConfig();
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return key;
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
