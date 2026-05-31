package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.PaymentRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/admin/payment-routing")
@RequiredArgsConstructor
public class PaymentRoutingController {

    private final PaymentRoutingService routingService;

    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations() {
        return ResponseEntity.ok(routingService.getRoutingRecommendations());
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistoricalRates() {
        Long tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(routingService.getHistoricalSuccessRates(tenantId != null ? tenantId : 0L));
    }

    @PostMapping("/select-method")
    public ResponseEntity<Map<String, Object>> selectOptimalMethod(@RequestBody Map<String, Object> request) {
        Long tenantId = TenantContext.getCurrentTenant();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String customerVpa = (String) request.getOrDefault("customerVpa", null);
        return ResponseEntity.ok(routingService.selectOptimalPaymentMethod(tenantId, amount, customerVpa));
    }
}