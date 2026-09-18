package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.core.security.RequireRole;
import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.webadmin.dto.*;
import com.khanabook.saas.webadmin.service.BusinessReadService;
import com.khanabook.saas.webadmin.service.BusinessWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import com.khanabook.saas.service.AssetStorageService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
public class BusinessAdminController {

    private final BusinessReadService businessReadService;
    private final BusinessWriteService businessWriteService;
    private final AssetStorageService assetStorageService;

    @GetMapping("/dashboard")
    public ResponseEntity<BusinessDashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(businessReadService.getDashboard(requireTenant(), from, to));
    }

    @GetMapping("/dashboard/trends")
    public ResponseEntity<DashboardTrendsResponse> getDashboardTrends() {
        return ResponseEntity.ok(businessReadService.getDashboardTrends(requireTenant()));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<BusinessOrderListItemResponse>> getOrders() {
        return ResponseEntity.ok(businessReadService.getOrders(requireTenant()));
    }

    @GetMapping("/orders/page")
    public ResponseEntity<PaginatedOrdersResponse> getOrdersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        size = Math.min(size, 100);
        return ResponseEntity.ok(businessReadService.getOrdersPaginated(requireTenant(), page, size, status, from, to));
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

    @PostMapping("/bills/{billId}/void")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<BusinessOrderListItemResponse> voidBill(
            @PathVariable Long billId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        businessReadService.voidBill(requireTenant(), billId, reason);
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

    @PostMapping("/staff/{userId}/activate")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<Void> activateStaff(@PathVariable Long userId) {
        businessWriteService.activateStaff(requireTenant(), userId);
        return ResponseEntity.ok().build();
    }

    /** Signs one staff member out of every device, without disabling their account. */
    @PostMapping("/staff/{userId}/revoke-sessions")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<java.util.Map<String, Integer>> revokeStaffSessions(@PathVariable Long userId) {
        int revoked = businessWriteService.revokeStaffSessions(requireTenant(), userId);
        return ResponseEntity.ok(java.util.Map.of("revoked", revoked));
    }

    /**
     * Signs everyone in the restaurant out of every device — including the caller,
     * since the usual trigger is a lost or stolen terminal.
     */
    @PostMapping("/sessions/revoke-all")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<java.util.Map<String, Integer>> revokeAllSessions() {
        int revoked = businessWriteService.revokeAllSessions(requireTenant());
        return ResponseEntity.ok(java.util.Map.of("revoked", revoked));
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

    @PostMapping(value = "/menu/{menuItemId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<AssetStorageService.AssetUploadResult> uploadMenuItemImage(
            @PathVariable Long menuItemId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(assetStorageService.uploadMenuItemImage(requireTenant(), menuItemId, file));
    }

    @DeleteMapping("/menu/{menuItemId}/image")
    @RequireRole(UserRole.OWNER)
    public ResponseEntity<Void> deleteMenuItemImage(@PathVariable Long menuItemId) {
        assetStorageService.deleteMenuItemImage(requireTenant(), menuItemId);
        return ResponseEntity.ok().build();
    }

    // ─── Terminal Write Endpoints ────────────────────────────────────────────────

    @PostMapping("/terminals/{terminalId}/reactivate")
    @RequireRole(UserRole.OWNER)
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

    private Boolean parseBoolean(Object val) {
        if (val == null) return null;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof Number) return ((Number) val).intValue() != 0;
        if (val instanceof String) {
            String s = ((String) val).trim().toLowerCase();
            return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
        }
        return false;
    }
}
