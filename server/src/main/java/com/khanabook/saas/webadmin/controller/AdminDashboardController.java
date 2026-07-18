package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.security.RequireRole;
import com.khanabook.saas.webadmin.dto.AdminBusinessDetailResponse;
import com.khanabook.saas.webadmin.dto.AdminBusinessListItemResponse;
import com.khanabook.saas.webadmin.dto.AdminDashboardSummaryResponse;
import com.khanabook.saas.webadmin.service.AdminReadService;
import com.khanabook.saas.webadmin.service.AdminWriteService;
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

    @GetMapping("/businesses")
    public ResponseEntity<List<AdminBusinessListItemResponse>> getBusinesses() {
        return ResponseEntity.ok(adminReadService.getBusinesses());
    }

    @GetMapping("/businesses/{restaurantId}")
    public ResponseEntity<AdminBusinessDetailResponse> getBusinessDetail(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(adminReadService.getBusinessDetail(restaurantId));
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
