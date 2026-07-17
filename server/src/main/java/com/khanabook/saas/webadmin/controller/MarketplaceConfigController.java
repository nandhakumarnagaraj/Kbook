package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.webadmin.dto.MarketplaceConfigRequest;
import com.khanabook.saas.webadmin.dto.MarketplaceConfigResponse;
import com.khanabook.saas.webadmin.service.BusinessReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/business/marketplace")
@RequiredArgsConstructor
public class MarketplaceConfigController {

    private final BusinessReadService businessReadService;

    @GetMapping("/config")
    public ResponseEntity<MarketplaceConfigResponse> getConfig() {
        Long restaurantId = requireTenant();
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return ResponseEntity.ok(businessReadService.getMarketplaceConfig(
                restaurantId, base + "/api/v1/webhooks/zomato", base + "/api/v1/webhooks/swiggy"));
    }

    @PostMapping("/config")
    public ResponseEntity<MarketplaceConfigResponse> saveConfig(@RequestBody MarketplaceConfigRequest request) {
        Long restaurantId = requireTenant();
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return ResponseEntity.ok(businessReadService.saveMarketplaceConfig(
                restaurantId, request, base + "/api/v1/webhooks/zomato", base + "/api/v1/webhooks/swiggy"));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant context is missing");
        }
        return tenantId;
    }
}
