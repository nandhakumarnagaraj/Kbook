package com.khanabook.saas.feature.payments.controller;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.payments.service.InstantSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * On-demand settlement is per transaction, so the body names the bills to settle:
     * {@code {"billIds":[12,18]}}. A restaurant-level amount is not a valid request — the
     * gateway needs an Easebuzz id and captured amount for every transaction in the batch.
     */
    @PostMapping("/instant")
    public ResponseEntity<Map<String, Object>> requestInstantSettlement(@RequestBody Map<String, Object> body) {
        Object raw = body.get("billIds");
        if (!(raw instanceof List<?> ids) || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure",
                    "error", "billIds is required: on-demand settlement settles chosen transactions"));
        }
        List<Long> billIds = new java.util.ArrayList<>();
        for (Object id : ids) {
            try {
                billIds.add(Long.valueOf(String.valueOf(id)));
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("status", "failure",
                        "error", "billIds must contain numeric bill ids"));
            }
        }
        return ResponseEntity.ok(settlementService.requestInstantSettlement(requireTenant(), billIds));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}