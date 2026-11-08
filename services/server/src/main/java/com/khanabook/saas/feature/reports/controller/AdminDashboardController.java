package com.khanabook.saas.feature.reports.controller;

import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.core.security.RequireRole;
import com.khanabook.saas.feature.platform.dto.AdminBusinessDetailResponse;
import com.khanabook.saas.feature.platform.dto.AdminBusinessListItemResponse;
import com.khanabook.saas.feature.reports.dto.AdminDashboardSummaryResponse;
import com.khanabook.saas.feature.platform.service.AdminReadService;
import com.khanabook.saas.feature.platform.service.AdminWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminReadService adminReadService;
    private final AdminWriteService adminWriteService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<AdminDashboardSummaryResponse> getDashboardSummary() {
        return ResponseEntity.ok(adminReadService.getDashboardSummary());
    }

    @GetMapping("/dashboard/daily-trends")
    public ResponseEntity<List<com.khanabook.saas.feature.platform.dto.AdminDailyTrendItemResponse>> getDailyTrends(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminReadService.getDailyTrends(days));
    }

    @GetMapping("/businesses")
    public ResponseEntity<List<AdminBusinessListItemResponse>> getBusinesses() {
        return ResponseEntity.ok(adminReadService.getBusinesses());
    }

    @GetMapping("/users")
    public ResponseEntity<List<com.khanabook.saas.feature.platform.dto.AdminUserListItemResponse>> getUsers() {
        return ResponseEntity.ok(adminReadService.getUsers());
    }

    @GetMapping("/bills")
    public ResponseEntity<List<com.khanabook.saas.feature.platform.dto.AdminBillListItemResponse>> getRecentBills(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(adminReadService.getRecentBills(limit));
    }

    @GetMapping("/activity-logs")
    public ResponseEntity<List<com.khanabook.saas.feature.platform.dto.AdminActivityLogResponse>> getActivityLogs(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(adminReadService.getActivityLogs(limit));
    }

    @GetMapping("/notifications")
    public ResponseEntity<com.khanabook.saas.feature.platform.dto.AdminNotificationFeedResponse> getNotifications(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(adminReadService.getNotifications(limit));
    }

    @GetMapping("/backups")
    public ResponseEntity<com.khanabook.saas.feature.platform.dto.AdminBackupStatusResponse> getBackupStatus() {
        return ResponseEntity.ok(adminReadService.getBackupStatus());
    }

    @GetMapping("/businesses/{restaurantId}")
    public ResponseEntity<AdminBusinessDetailResponse> getBusinessDetail(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(adminReadService.getBusinessDetail(restaurantId));
    }

    @PostMapping("/businesses")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<AdminBusinessDetailResponse> createBusiness(
            @jakarta.validation.Valid @RequestBody com.khanabook.saas.feature.platform.dto.AdminCreateBusinessRequest request) {
        AdminBusinessDetailResponse created = adminWriteService.createBusiness(request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }

    @PostMapping("/businesses/{restaurantId}/suspend")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Void> suspendBusiness(@PathVariable Long restaurantId) {
        adminWriteService.suspendBusiness(restaurantId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/businesses/{restaurantId}/activate")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<Void> activateBusiness(@PathVariable Long restaurantId) {
        adminWriteService.activateBusiness(restaurantId);
        return ResponseEntity.ok().build();
    }
}
