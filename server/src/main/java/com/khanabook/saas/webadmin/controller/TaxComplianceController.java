package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.TaxComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/business/tax")
@RequiredArgsConstructor
public class TaxComplianceController {

    private final TaxComplianceService taxService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(taxService.getTaxSummary(requireTenant()));
    }

    @GetMapping("/gst-report")
    public ResponseEntity<Map<String, Object>> getGstReport(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(taxService.getGstReport(requireTenant(), year, month));
    }

    @GetMapping("/gst-report/csv")
    public ResponseEntity<String> getGstReportCsv(
            @RequestParam int year, @RequestParam int month) {
        String csv = taxService.generateGstReportCsv(requireTenant(), year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gst-report-" + year + "-" + String.format("%02d", month) + ".csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv);
    }

    @GetMapping("/gst-return")
    public ResponseEntity<Map<String, Object>> getGstReturn(
            @RequestParam int year, @RequestParam int quarter) {
        return ResponseEntity.ok(taxService.getGstReturnData(requireTenant(), year, quarter));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalArgumentException("Tenant context is missing");
        return tenantId;
    }
}