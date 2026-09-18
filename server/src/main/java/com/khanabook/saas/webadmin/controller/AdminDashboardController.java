package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.core.security.RequireRole;
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

    @PostMapping("/businesses")
    @RequireRole(UserRole.KBOOK_ADMIN)
    public ResponseEntity<AdminBusinessDetailResponse> createBusiness(
            @jakarta.validation.Valid @RequestBody com.khanabook.saas.webadmin.dto.AdminCreateBusinessRequest request) {
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
