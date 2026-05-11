package com.khanabook.saas.billing.controller;

import com.khanabook.saas.billing.service.EasebuzzSubMerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments/easebuzz/sub-merchant")
@RequiredArgsConstructor
public class EasebuzzSubMerchantWebhookController {

    private final EasebuzzSubMerchantService service;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> formWebhook(@RequestParam Map<String, String> params) {
        service.processWebhook(Map.copyOf(params));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jsonWebhook(@RequestBody Map<String, Object> body) {
        service.processWebhook(body);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
