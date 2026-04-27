package com.khanabook.saas.payment.controller;

import com.khanabook.saas.payment.service.EasebuzzPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments/easebuzz")
@RequiredArgsConstructor
public class EasebuzzWebhookController {

    private final EasebuzzPaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.processGatewayCallback(params));
    }

    @PostMapping("/refund/webhook")
    public ResponseEntity<Map<String, Object>> refundWebhook(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.processRefundWebhook(params));
    }
}
