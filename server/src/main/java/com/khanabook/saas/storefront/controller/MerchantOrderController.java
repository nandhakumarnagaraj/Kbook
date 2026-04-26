package com.khanabook.saas.storefront.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.storefront.dto.MerchantCustomerOrderDetailResponse;
import com.khanabook.saas.storefront.dto.MerchantCustomerOrderSummaryResponse;
import com.khanabook.saas.storefront.dto.UpdateCustomerOrderStatusRequest;
import com.khanabook.saas.storefront.service.StorefrontService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/storefront/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final StorefrontService storefrontService;

    @GetMapping
    public ResponseEntity<List<MerchantCustomerOrderSummaryResponse>> listOrders() {
        return ResponseEntity.ok(storefrontService.listMerchantOrders(requireTenant()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<MerchantCustomerOrderDetailResponse> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(storefrontService.getMerchantOrder(requireTenant(), orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<MerchantCustomerOrderDetailResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateCustomerOrderStatusRequest request
    ) {
        return ResponseEntity.ok(storefrontService.updateMerchantOrderStatus(requireTenant(), orderId, request));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant context is missing");
        }
        return tenantId;
    }
}
