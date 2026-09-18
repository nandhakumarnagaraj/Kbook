package com.khanabook.saas.feature.business.controller;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.business.service.UnifiedCommerceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/business/commerce")
@RequiredArgsConstructor
public class UnifiedCommerceController {

    private final UnifiedCommerceService commerceService;

    @GetMapping("/unified-dashboard")
    public ResponseEntity<Map<String, Object>> getUnifiedDashboard() {
        return ResponseEntity.ok(commerceService.getUnifiedDashboard(requireTenant()));
    }

    @GetMapping("/cross-channel-insights")
    public ResponseEntity<Map<String, Object>> getCrossChannelInsights() {
        return ResponseEntity.ok(commerceService.getCrossChannelInsights(requireTenant()));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}