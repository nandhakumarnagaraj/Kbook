package com.khanabook.saas.webadmin.controller;

import com.khanabook.saas.service.DeveloperPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/developer")
@RequiredArgsConstructor
public class DeveloperPortalController {

    private final DeveloperPortalService portalService;

    @GetMapping("/docs")
    public ResponseEntity<Map<String, Object>> getApiDocs() {
        return ResponseEntity.ok(portalService.getApiDocumentation());
    }

    @GetMapping("/webhook-events")
    public ResponseEntity<Map<String, Object>> getWebhookEvents() {
        return ResponseEntity.ok(portalService.getWebhookEvents());
    }

    @GetMapping("/rate-limits")
    public ResponseEntity<Map<String, Object>> getRateLimits() {
        return ResponseEntity.ok(portalService.getRateLimitStatus());
    }
}