package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.webadmin.dto.AdminBusinessDetailResponse;
import com.khanabook.saas.webadmin.dto.AdminBusinessListItemResponse;
import com.khanabook.saas.webadmin.dto.AdminDashboardSummaryResponse;
import com.khanabook.saas.webadmin.service.AdminReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminReadService adminReadService;

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
}
