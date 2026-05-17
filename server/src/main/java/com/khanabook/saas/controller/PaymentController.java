package com.khanabook.saas.controller;

import com.khanabook.saas.service.EasebuzzPaymentService;
import com.khanabook.saas.service.EasebuzzWebhookService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/payments/easebuzz")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final EasebuzzPaymentService paymentService;
    private final EasebuzzWebhookService webhookService;

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) {
        Long billId = Long.valueOf(request.get("billId").toString());
        Long restaurantId = Long.valueOf(request.get("restaurantId").toString());
        Map<String, Object> result = paymentService.createOrder(billId, restaurantId);
        if ("failure".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/{billId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.verifyPayment(billId));
    }

    @PostMapping("/verify/{billId}")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.verifyPayment(billId));
    }

    @PostMapping("/refund/{billId}")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable Long billId,
                                                       @RequestBody Map<String, Object> request) {
        BigDecimal amount = request.get("amount") != null
                ? new BigDecimal(request.get("amount").toString())
                : BigDecimal.ZERO;
        String reason = (String) request.get("reason");
        return ResponseEntity.ok(paymentService.initiateRefund(billId, amount, reason));
    }

    @GetMapping("/refund-status/{billId}")
    public ResponseEntity<Map<String, Object>> getRefundStatus(@PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.getRefundStatus(billId));
    }

    @PostMapping("/cancel/{billId}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.cancelTransaction(billId));
    }

    @GetMapping("/return")
    public ResponseEntity<Map<String, Object>> handleReturn(
            @RequestParam Map<String, String> params) {
        log.debug("Easebuzz return redirect received: {}", params);
        // Forward to webhook handler for idempotent processing
        return ResponseEntity.ok(webhookService.handlePaymentWebhook(params));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> paymentWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Payment webhook received: {}", payload);
        return ResponseEntity.ok(webhookService.handlePaymentWebhook(payload));
    }

    @PostMapping("/refund/webhook")
    public ResponseEntity<Map<String, Object>> refundWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Refund webhook received: {}", payload);
        return ResponseEntity.ok(webhookService.handleRefundWebhook(payload));
    }

    @PostMapping("/sub-merchant/webhook")
    public ResponseEntity<Map<String, Object>> subMerchantWebhook(@RequestBody Map<String, Object> payload) {
        log.debug("Sub-merchant webhook received: {}", payload);
        webhookService.handleSubMerchantWebhook(payload);
        return ResponseEntity.ok(Map.of("status", "received"));
    }

    @PostMapping("/payout/webhook")
    public ResponseEntity<Map<String, Object>> payoutWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Payout webhook received: {}", payload);
        return ResponseEntity.ok(webhookService.handlePayoutWebhook(payload));
    }
}
