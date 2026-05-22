package com.khanabook.saas.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.service.EasebuzzPaymentService;
import com.khanabook.saas.service.EasebuzzWebhookService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payments/easebuzz")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final EasebuzzPaymentService paymentService;
    private final EasebuzzWebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) {
        Object billIdObj = request.get("billId");
        Object restaurantIdObj = request.get("restaurantId");
        if (billIdObj == null || restaurantIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure", "error", "billId and restaurantId are required"));
        }
        Long billId = Long.valueOf(billIdObj.toString());
        Long restaurantId = Long.valueOf(restaurantIdObj.toString());
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
    public ResponseEntity<String> handleReturn(
            @RequestParam Map<String, String> params) {
        log.debug("Easebuzz return redirect received: {}", params);
        // surl/furl is a browser redirect — just show a close page.
        // Actual processing happens via notify_url webhook or Android verify().
        String html = """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
            <title>Processing - KhanaBook</title>
            <style>
            body{font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;justify-content:center;
            align-items:center;min-height:100vh;margin:0;background:#f8f9fa}
            .card{background:#fff;border-radius:16px;padding:40px;text-align:center;box-shadow:0 4px 24px rgba(0,0,0,.1);max-width:400px}
            .spinner{width:48px;height:48px;border:4px solid #e9ecef;border-top-color:#d4a853;border-radius:50%%;animation:spin .8s linear infinite;margin:0 auto 16px}
            @keyframes spin{to{transform:rotate(360deg)}}
            .title{font-size:20px;font-weight:700;color:#333;margin-bottom:8px}
            .msg{font-size:14px;color:#666}
            </style></head>
            <body><div class="card">
            <div class="spinner"></div>
            <div class="title">Processing Payment</div>
            <div class="msg">Please wait while we confirm your payment. You can close this window and check the bill in the app.</div>
            </div></body></html>
            """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
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

    @PostMapping(value = "/sub-merchant/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> subMerchantWebhookJson(@RequestBody Map<String, Object> payload) {
        log.debug("Sub-merchant webhook (JSON) received");
        return ResponseEntity.ok(webhookService.handleSubMerchantWebhook(payload));
    }

    @PostMapping(value = "/sub-merchant/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> subMerchantWebhookForm(@RequestParam Map<String, String> params) {
        log.debug("Sub-merchant webhook (form-url-encoded) received");
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", params.get("status"));

        // The 'data' field is a JSON string in form-url-encoded format
        String dataJson = params.get("data");
        if (dataJson != null && !dataJson.isBlank()) {
            try {
                Map<String, Object> dataMap = objectMapper.readValue(dataJson,
                        new TypeReference<Map<String, Object>>() {});
                payload.put("data", dataMap);
            } catch (Exception e) {
                log.warn("Failed to parse 'data' JSON in form-url-encoded sub-merchant webhook", e);
                return ResponseEntity.badRequest().body(Map.of("status", "error", "error", "Invalid data payload"));
            }
        }

        return ResponseEntity.ok(webhookService.handleSubMerchantWebhook(payload));
    }

    @PostMapping("/payout/webhook")
    public ResponseEntity<Map<String, Object>> payoutWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Payout webhook received: {}", payload);
        return ResponseEntity.ok(webhookService.handlePayoutWebhook(payload));
    }
}
