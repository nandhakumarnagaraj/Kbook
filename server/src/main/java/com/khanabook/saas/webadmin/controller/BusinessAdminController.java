package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.webadmin.dto.*;
import com.khanabook.saas.webadmin.service.BusinessReadService;
import com.khanabook.saas.webadmin.service.BusinessWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessAdminController {

    private final BusinessReadService businessReadService;
    private final BusinessWriteService businessWriteService;

    @GetMapping("/dashboard")
    public ResponseEntity<BusinessDashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(businessReadService.getDashboard(requireTenant(), from, to));
    }

    @GetMapping("/marketplace-setup")
    public ResponseEntity<BusinessMarketplaceSetupResponse> getMarketplaceSetup() {
        return ResponseEntity.ok(businessReadService.getMarketplaceSetup(requireTenant()));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<BusinessOrderListItemResponse>> getOrders() {
        return ResponseEntity.ok(businessReadService.getOrders(requireTenant()));
    }

    @GetMapping("/orders/{billId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable Long billId) {
        return ResponseEntity.ok(businessReadService.getOrderDetail(requireTenant(), billId));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<BusinessMenuListItemResponse>> getMenu() {
        return ResponseEntity.ok(businessReadService.getMenu(requireTenant()));
    }

    @GetMapping("/menu/categories")
    public ResponseEntity<List<BusinessCategoryResponse>> getMenuCategories() {
        return ResponseEntity.ok(businessReadService.getCategories(requireTenant()));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<BusinessStaffListItemResponse>> getStaff() {
        return ResponseEntity.ok(businessReadService.getStaff(requireTenant()));
    }

    @PostMapping("/bills/{billId}/manual-refund")
    public ResponseEntity<BusinessOrderListItemResponse> manualRefundBill(
            @PathVariable Long billId,
            @RequestBody com.khanabook.saas.webadmin.dto.RefundBillRequest request) {
        businessReadService.markManualRefund(requireTenant(), billId, request.refundAmount(), request.reason());
        return ResponseEntity.ok(businessReadService.getPosOrder(requireTenant(), billId));
    }

    // ─── Staff Write Endpoints ───────────────────────────────────────────────────

    @PostMapping("/staff")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<StaffCreatedResponse> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.ok(businessWriteService.createStaff(requireTenant(), request));
    }

    @PutMapping("/staff/{userId}")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<Void> updateStaff(@PathVariable Long userId, @Valid @RequestBody UpdateStaffRequest request) {
        businessWriteService.updateStaff(requireTenant(), userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/staff/{userId}/deactivate")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<Void> deactivateStaff(@PathVariable Long userId) {
        businessWriteService.deactivateStaff(requireTenant(), userId);
        return ResponseEntity.ok().build();
    }

    // ─── Menu Write Endpoints ────────────────────────────────────────────────────

    @PostMapping("/menu")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<BusinessMenuListItemResponse> createMenuItem(@Valid @RequestBody CreateMenuItemRequest request) {
        var item = businessWriteService.createMenuItem(requireTenant(), request);
        return ResponseEntity.ok(businessReadService.mapMenuItemToResponse(item));
    }

    @PutMapping("/menu/{menuItemId}")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<BusinessMenuListItemResponse> updateMenuItem(
            @PathVariable Long menuItemId, @Valid @RequestBody UpdateMenuItemRequest request) {
        var item = businessWriteService.updateMenuItem(requireTenant(), menuItemId, request);
        return ResponseEntity.ok(businessReadService.mapMenuItemToResponse(item));
    }

    @DeleteMapping("/menu/{menuItemId}")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long menuItemId) {
        businessWriteService.deleteMenuItem(requireTenant(), menuItemId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/menu/{menuItemId}/toggle-availability")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<BusinessMenuListItemResponse> toggleMenuItemAvailability(@PathVariable Long menuItemId) {
        var item = businessWriteService.toggleMenuItemAvailability(requireTenant(), menuItemId);
        return ResponseEntity.ok(businessReadService.mapMenuItemToResponse(item));
    }

    // ─── Terminal Write Endpoints ────────────────────────────────────────────────

    @PostMapping("/terminals/{terminalId}/reactivate")
    @RequireRole({UserRole.OWNER, UserRole.SHOP_ADMIN})
    public ResponseEntity<Void> reactivateTerminal(@PathVariable Long terminalId) {
        businessWriteService.reactivateTerminal(requireTenant(), terminalId);
        return ResponseEntity.ok().build();
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant context is missing");
        }
        return tenantId;
    }
}
