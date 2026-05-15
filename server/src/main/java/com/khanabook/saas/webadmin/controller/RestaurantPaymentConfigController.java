package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.SubMerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/restaurants/payment-config/easebuzz")
@RequiredArgsConstructor
public class RestaurantPaymentConfigController {

    private final RestaurantProfileRepository profileRepo;
    private final SubMerchantService subMerchantService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        EasebuzzSubMerchant sm = null;
        try {
            sm = subMerchantService.getByRestaurantId(restaurantId);
        } catch (Exception e) {
            // no sub-merchant configured yet
        }
        Map<String, Object> config = new HashMap<>();
        config.put("easebuzzEnabled", profile.getEasebuzzEnabled() != null && profile.getEasebuzzEnabled());
        config.put("subMerchantStatus", sm != null ? sm.getStatus() : "NOT_STARTED");
        config.put("subMerchantId", sm != null ? sm.getSubMerchantId() : null);
        config.put("kycStatus", sm != null ? sm.getKycStatus() : null);
        config.put("kycPortalUrl", sm != null ? sm.getKycPortalUrl() : null);
        config.put("kycSubmittedAt", sm != null ? sm.getKycSubmittedAt() : null);
        config.put("kycActivatedAt", sm != null ? sm.getKycActivatedAt() : null);
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> data) {
        Long restaurantId = TenantContext.getCurrentTenant();
        RestaurantProfile profile = profileRepo.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));
        if (data.containsKey("easebuzzEnabled")) {
            profile.setEasebuzzEnabled((Boolean) data.get("easebuzzEnabled"));
        }
        profileRepo.save(profile);
        return getConfig();
    }

    @GetMapping("/sub-merchant-status")
    public ResponseEntity<Map<String, Object>> getSubMerchantStatus() {
        Long restaurantId = TenantContext.getCurrentTenant();
        try {
            EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
            Map<String, Object> result = new HashMap<>();
            result.put("status", sm.getStatus());
            result.put("kycStatus", sm.getKycStatus());
            result.put("kycPortalUrl", sm.getKycPortalUrl() != null ? sm.getKycPortalUrl() : "");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "NOT_STARTED"));
        }
    }
}
