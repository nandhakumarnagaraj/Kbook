package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.CustomerDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business/customers")
@RequiredArgsConstructor
public class CustomerDataController {

    private final CustomerDataService customerDataService;

    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> getInsights() {
        return ResponseEntity.ok(customerDataService.getCustomerInsights(requireTenant()));
    }

    @GetMapping("/churn-risk")
    public ResponseEntity<List<Map<String, Object>>> getChurnRisk() {
        return ResponseEntity.ok(customerDataService.getChurnRisk(requireTenant()));
    }

    @PostMapping("/opt-out")
    public ResponseEntity<Map<String, Object>> optOut(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(customerDataService.optOutCustomer(requireTenant(), request.get("phone")));
    }

    @PostMapping("/opt-in")
    public ResponseEntity<Map<String, Object>> optIn(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(customerDataService.optInCustomer(requireTenant(), request.get("phone")));
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteData(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(customerDataService.deleteCustomerData(requireTenant(), request.get("phone")));
    }

    @PostMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(customerDataService.exportCustomerData(requireTenant(), request.get("phone")));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}