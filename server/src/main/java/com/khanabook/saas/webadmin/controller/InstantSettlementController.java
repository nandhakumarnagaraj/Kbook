package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.InstantSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/business/settlements")
@RequiredArgsConstructor
public class InstantSettlementController {

    private final InstantSettlementService settlementService;

    @GetMapping("/estimate")
    public ResponseEntity<Map<String, Object>> getEstimate() {
        return ResponseEntity.ok(settlementService.getSettlementEstimate(requireTenant()));
    }

    @PostMapping("/instant")
    public ResponseEntity<Map<String, Object>> requestInstantSettlement(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return ResponseEntity.ok(settlementService.requestInstantSettlement(requireTenant(), amount));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}