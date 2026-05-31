package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.Chargeback;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.ChargebackPreventionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business/chargebacks")
@RequiredArgsConstructor
public class ChargebackController {

    private final ChargebackPreventionService chargebackService;

    @GetMapping("/reason-codes")
    public ResponseEntity<List<Map<String, String>>> getReasonCodes() {
        return ResponseEntity.ok(chargebackService.getReasonCodes());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(chargebackService.getChargebackSummary(requireTenant()));
    }

    @PostMapping("/score/{billId}")
    public ResponseEntity<Map<String, Object>> scoreTransaction(@PathVariable Long billId) {
        return ResponseEntity.ok(chargebackService.scoreTransaction(billId));
    }

    @PostMapping
    public ResponseEntity<Chargeback> createChargeback(@RequestBody Map<String, Object> body) {
        Long billId = ((Number) body.get("billId")).longValue();
        String reasonCode = (String) body.get("reasonCode");
        String description = (String) body.getOrDefault("description", "");
        return ResponseEntity.ok(chargebackService.createChargeback(requireTenant(), billId, reasonCode, description));
    }

    @PostMapping("/{chargebackId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveChargeback(
            @PathVariable Long chargebackId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(chargebackService.resolveChargeback(requireTenant(), chargebackId, body.getOrDefault("resolution", "accepted")));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}