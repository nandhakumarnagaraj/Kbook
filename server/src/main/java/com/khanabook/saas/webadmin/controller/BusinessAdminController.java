package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.webadmin.dto.BusinessDashboardResponse;
import com.khanabook.saas.webadmin.dto.BusinessMenuListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessOrderListItemResponse;
import com.khanabook.saas.webadmin.dto.BusinessStaffListItemResponse;
import com.khanabook.saas.webadmin.dto.RefundBillRequest;
import com.khanabook.saas.webadmin.service.BusinessReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessAdminController {

    private final BusinessReadService businessReadService;

    @GetMapping("/dashboard")
    public ResponseEntity<BusinessDashboardResponse> getDashboard() {
        return ResponseEntity.ok(businessReadService.getDashboard(requireTenant()));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<BusinessOrderListItemResponse>> getOrders() {
        return ResponseEntity.ok(businessReadService.getOrders(requireTenant()));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<BusinessMenuListItemResponse>> getMenu() {
        return ResponseEntity.ok(businessReadService.getMenu(requireTenant()));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<BusinessStaffListItemResponse>> getStaff() {
        return ResponseEntity.ok(businessReadService.getStaff(requireTenant()));
    }

    @PostMapping("/bills/{billId}/refund")
    public ResponseEntity<BusinessOrderListItemResponse> refundBill(
            @PathVariable Long billId,
            @RequestBody RefundBillRequest request) {
        return ResponseEntity.ok(businessReadService.refundBill(
                requireTenant(), billId, request.refundAmount(), request.reason()));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant context is missing");
        }
        return tenantId;
    }
}
