package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.MarketplaceOrder;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.security.RequireRole;
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
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<List<MarketplaceOrder>> getOrders() {
        return ResponseEntity.ok(orderService.getOrders(requireTenant()));
    }

    @GetMapping("/pending")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<List<MarketplaceOrder>> getPending() {
        return ResponseEntity.ok(orderService.getPendingOrders(requireTenant()));
    }

    @GetMapping("/counts")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<Map<String, Long>> getCounts() {
        return ResponseEntity.ok(orderService.getOrderCounts(requireTenant()));
    }

    @PostMapping("/{orderId}/accept")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<MarketplaceOrder> accept(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.acceptOrder(orderId, requireTenant()));
    }

    @PostMapping("/{orderId}/reject")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<MarketplaceOrder> reject(@PathVariable Long orderId,
                                                    @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(orderService.rejectOrder(orderId, requireTenant(), reason));
    }

    @PostMapping("/{orderId}/mark-ready")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<MarketplaceOrder> markReady(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.markReady(orderId, requireTenant()));
    }

    @PostMapping("/{orderId}/complete")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<MarketplaceOrder> complete(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.completeOrder(orderId, requireTenant()));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is missing");
        }
        return tenantId;
    }
}
