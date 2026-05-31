package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @GetMapping("/reasons")
    public ResponseEntity<List<Map<String, String>>> getReasonTaxonomy() {
        return ResponseEntity.ok(refundService.getReasonTaxonomy());
    }

    @GetMapping("/refundable")
    public ResponseEntity<Map<String, Object>> getRefundableOrders() {
        return ResponseEntity.ok(refundService.getRefundableOrders(requireTenant()));
    }

    @PostMapping("/bill/{billId}")
    public ResponseEntity<Map<String, Object>> initiateRefund(
            @PathVariable Long billId,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("refundAmount").toString());
        String reason = (String) body.getOrDefault("reason", "OTHER");
        return ResponseEntity.ok(refundService.initiatePartialRefund(billId, requireTenant(), amount, reason));
    }

    @PostMapping("/bill/{billId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelAndRefund(
            @PathVariable Long billId,
            @RequestBody Map<String, Object> body) {
        String reason = (String) body.getOrDefault("reason", "ORDER_CANCELLED");
        int delayMinutes = body.containsKey("delayMinutes") ? Integer.parseInt(body.get("delayMinutes").toString()) : 0;
        return ResponseEntity.ok(refundService.cancelAndAutoRefund(billId, requireTenant(), reason, delayMinutes));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getRefundSummary() {
        return ResponseEntity.ok(refundService.getRefundSummary(requireTenant()));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}