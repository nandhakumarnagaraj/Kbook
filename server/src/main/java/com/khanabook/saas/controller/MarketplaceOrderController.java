package com.khanabook.saas.controller;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.MarketplaceOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business/marketplace-orders")
@RequiredArgsConstructor
public class MarketplaceOrderController {

    private final MarketplaceOrderService orderService;

    @GetMapping
    public ResponseEntity<List<MarketplaceOrder>> getOrders() {
        return ResponseEntity.ok(orderService.getOrders(getTenant()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<MarketplaceOrder>> getPending() {
        return ResponseEntity.ok(orderService.getPendingOrders(getTenant()));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> getCounts() {
        return ResponseEntity.ok(orderService.getOrderCounts(getTenant()));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<MarketplaceOrder> accept(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.acceptOrder(orderId, getTenant()));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<MarketplaceOrder> reject(@PathVariable Long orderId,
                                                    @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(orderService.rejectOrder(orderId, getTenant(), reason));
    }

    @PostMapping("/{orderId}/mark-ready")
    public ResponseEntity<MarketplaceOrder> markReady(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markReady(orderId, getTenant()));
    }

    @PostMapping("/{orderId}/complete")
    public ResponseEntity<MarketplaceOrder> complete(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.completeOrder(orderId, getTenant()));
    }

    private Long getTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new RuntimeException("Tenant context missing");
        return tenantId;
    }
}
