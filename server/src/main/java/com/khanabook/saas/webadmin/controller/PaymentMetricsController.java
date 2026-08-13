package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.webadmin.service.PaymentMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/payment-metrics")
@RequiredArgsConstructor
public class PaymentMetricsController {

    private final PaymentMetricsService metricsService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(metricsService.getOverview());
    }

    @GetMapping("/trends")
    public ResponseEntity<List<Map<String, Object>>> trends(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(metricsService.getTrends(period, days));
    }

    @GetMapping("/failed-transactions")
    public ResponseEntity<Map<String, Object>> failedTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(metricsService.getFailedTransactions(page, size));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<Map<String, Object>>> anomalies() {
        return ResponseEntity.ok(metricsService.getAnomalies());
    }
}