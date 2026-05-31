package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.service.WebhookRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/webhooks")
@RequiredArgsConstructor
public class WebhookHealthController {

    private final WebhookRetryService retryService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthSummary() {
        return ResponseEntity.ok(retryService.getHealthSummary());
    }

    @GetMapping("/dead-letter")
    public ResponseEntity<List<Map<String, Object>>> getDeadLetterJobs() {
        return ResponseEntity.ok(retryService.getDeadLetterJobs());
    }

    @PostMapping("/dead-letter/{jobId}/replay")
    public ResponseEntity<Map<String, String>> replayDeadLetter(@PathVariable Long jobId) {
        retryService.replayDeadLetter(jobId);
        return ResponseEntity.ok(Map.of("status", "replayed"));
    }
}