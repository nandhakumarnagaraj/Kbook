package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.FinancingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/business/financing")
@RequiredArgsConstructor
public class FinancingController {

    private final FinancingService financingService;

    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> getEligibility() {
        return ResponseEntity.ok(financingService.getCreditEligibility(requireTenant()));
    }

    @PostMapping("/options")
    public ResponseEntity<Map<String, Object>> getLoanOptions(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return ResponseEntity.ok(financingService.getLoanOptions(requireTenant(), amount));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}