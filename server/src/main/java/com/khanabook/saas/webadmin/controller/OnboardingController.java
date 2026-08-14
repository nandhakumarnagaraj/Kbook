package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/business/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getProgress() {
        return ResponseEntity.ok(onboardingService.getOnboardingProgress(requireTenant()));
    }

    @PostMapping("/prefill-from-profile")
    public ResponseEntity<Map<String, Object>> prefillFromProfile() {
        return ResponseEntity.ok(onboardingService.prefillFromProfile(requireTenant()));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}