package com.khanabook.saas.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.security.TenantContext;
import com.khanabook.saas.service.EasebuzzPaymentService;
import com.khanabook.saas.service.EasebuzzWebhookService;
import com.khanabook.saas.service.PermissionService;
import com.khanabook.saas.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payments/easebuzz")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final EasebuzzPaymentService paymentService;
    private final EasebuzzWebhookService webhookService;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;
    private final PermissionService permissionService;

    private void requirePermission(String permissionKey) {
        Long restaurantId = TenantContext.getCurrentTenant();
        Long userId = TenantContext.getCurrentUserId();
        if (restaurantId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        if (!permissionService.hasPermission(restaurantId, userId, permissionKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + permissionKey);
        }
    }

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) {
        requirePermission("billing.settle");
        Object billIdObj = request.get("billId");
        Object restaurantIdObj = request.get("restaurantId");
        if (billIdObj == null || restaurantIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure", "error", "billId and restaurantId are required"));
        }
        Long billId = Long.valueOf(billIdObj.toString());
        Long restaurantId = Long.valueOf(restaurantIdObj.toString());
        // Verify caller owns this restaurant
        Long callerRestaurantId = TenantContext.getCurrentTenant();
        if (callerRestaurantId != null && !callerRestaurantId.equals(restaurantId)) {
            return ResponseEntity.status(403).body(Map.of("status", "failure", "error", "Access denied"));
        }
        Map<String, Object> result = paymentService.createOrder(billId, restaurantId);
        if ("failure".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/create-link")
    public ResponseEntity<Map<String, Object>> createPaymentLink(@RequestBody Map<String, Object> request) {
        requirePermission("billing.settle");
        Object restaurantIdObj = request.get("restaurantId");
        Object amountObj = request.get("amount");
        Object customerNameObj = request.get("customerName");
        Object customerEmailObj = request.get("customerEmail");
        Object customerPhoneObj = request.get("customerPhone");
        Object messageObj = request.get("message");

        if (restaurantIdObj == null || amountObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure", "error", "restaurantId and amount are required"));
        }

        Map<String, Object> result = paymentService.createPaymentLink(request);
        if ("failure".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/create-link-for-bill")
    public ResponseEntity<Map<String, Object>> createPaymentLinkForBill(@RequestBody Map<String, Object> request) {
        requirePermission("billing.settle");
        Object billIdObj = request.get("billId");
        Object restaurantIdObj = request.get("restaurantId");
        if (billIdObj == null || restaurantIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure", "error", "billId and restaurantId are required"));
        }
        Long billId = Long.valueOf(billIdObj.toString());
        Long restaurantId = Long.valueOf(restaurantIdObj.toString());
        // Verify caller owns this restaurant
        Long callerRestaurantId = TenantContext.getCurrentTenant();
        if (callerRestaurantId != null && !callerRestaurantId.equals(restaurantId)) {
            return ResponseEntity.status(403).body(Map.of("status", "failure", "error", "Access denied"));
        }
        Map<String, Object> result = paymentService.createPaymentLinkForBill(billId, restaurantId);
        if ("failure".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/create-fssai-order")
    public ResponseEntity<Map<String, Object>> createFssaiOrder(@RequestBody Map<String, Object> request) {
        requirePermission("settings.gst");
        Object yearsObj = request.get("years");
        Object fssaiNumberObj = request.get("fssaiNumber");
        Object restaurantIdObj = request.get("restaurantId");
        if (yearsObj == null || fssaiNumberObj == null || restaurantIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure", "error", "years, fssaiNumber and restaurantId are required"));
        }
        Integer years = Integer.valueOf(yearsObj.toString());
        String fssaiNumber = fssaiNumberObj.toString();
        Long restaurantId = Long.valueOf(restaurantIdObj.toString());
        Map<String, Object> result = paymentService.createFssaiRenewalOrder(years, fssaiNumber, restaurantId);
        if ("failure".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/{billId}")
    public ResponseEntity<Map<String, Object>> getStatus(
            @PathVariable Long billId,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(billId, refresh));
    }

    @PostMapping("/verify/{billId}")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.verifyPayment(billId));
    }

    @PostMapping("/refund/{billId}")
    public ResponseEntity<Map<String, Object>> refund(@PathVariable Long billId,
                                                       @RequestBody Map<String, Object> request) {
        requirePermission("billing.refund");
        // Tenant scoping: the caller may only refund bills of their own restaurant.
        Long callerRestaurantId = TenantContext.getCurrentTenant();
        if (callerRestaurantId == null) {
            return ResponseEntity.status(403).body(Map.of("status", "failure", "error", "Access denied"));
        }
        BigDecimal amount;
        try {
            amount = request.get("amount") != null
                    ? new BigDecimal(request.get("amount").toString())
                    : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "failure", "error", "Invalid refund amount"));
        }
        String reason = (String) request.get("reason");
        // RefundService enforces ownership, paid-status eligibility and refundable
        // bounds (positive, not exceeding remaining unrefunded total).
        Map<String, Object> result = refundService.initiatePartialRefund(billId, callerRestaurantId, amount, reason);
        if ("failure".equals(result.get("status"))) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/refund-status/{billId}")
    public ResponseEntity<Map<String, Object>> getRefundStatus(@PathVariable Long billId) {
        return ResponseEntity.ok(paymentService.getRefundStatus(billId));
    }

    @Deprecated
    @PostMapping("/cancel/{billId}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long billId) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "failure",
            "error", "Cancel API is not supported by Easebuzz. Unpaid transactions auto-expire in 15 minutes."
        ));
    }

    @GetMapping("/return")
    public ResponseEntity<Void> handleReturn(
            @RequestParam Map<String, String> params) {
        log.debug("Easebuzz return redirect received: {}", params);
        String txnid = params.get("txnid");
        String status = params.get("status");
        String hash = params.get("hash");

        // URL-encode txnid before using in Location header to prevent header injection
        String encodedTxnid = txnid != null ? URLEncoder.encode(txnid, StandardCharsets.UTF_8) : "";

        // Security: verify reverse hash if present, otherwise don't trust status
        boolean verified = false;
        if (hash != null && !hash.isBlank()) {
            verified = webhookService.verifyPaymentReturnHash(params);
        }

        if (verified && "success".equalsIgnoreCase(status) && txnid != null && !txnid.isBlank()) {
            return ResponseEntity.status(302)
                    .header("Location", "khanabook://payment/success?txnid=" + encodedTxnid)
                    .build();
        }
        // For unverified or failed — redirect to status check (app will poll)
        String redirectUrl = txnid != null && !txnid.isBlank()
                ? "khanabook://payment/status?txnid=" + encodedTxnid
                : "khanabook://payment/failure";
        return ResponseEntity.status(302)
                .header("Location", redirectUrl)
                .build();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> paymentWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Payment webhook received: {}", payload);
        Map<String, Object> result = webhookService.handlePaymentWebhook(payload);
        if ("hash_mismatch".equals(result.get("status"))) {
            return ResponseEntity.status(401).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/refund/webhook")
    public ResponseEntity<Map<String, Object>> refundWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Refund webhook received: {}", payload);
        Map<String, Object> result = webhookService.handleRefundWebhook(payload);
        if ("hash_mismatch".equals(result.get("status"))) {
            return ResponseEntity.status(401).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/sub-merchant/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> subMerchantWebhookJson(@RequestBody Map<String, Object> payload) {
        log.debug("Sub-merchant webhook (JSON) received");
        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        if ("hash_mismatch".equals(result.get("status"))) {
            return ResponseEntity.status(401).body(result);
        }
        return ResponseEntity.ok(result);
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

        Map<String, Object> result = webhookService.handleSubMerchantWebhook(payload);
        if ("hash_mismatch".equals(result.get("status"))) {
            return ResponseEntity.status(401).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/payout/webhook")
    public ResponseEntity<Map<String, Object>> payoutWebhook(@RequestBody Map<String, String> payload) {
        log.debug("Payout webhook received: {}", payload);
        Map<String, Object> result = webhookService.handlePayoutWebhook(payload);
        if ("hash_mismatch".equals(result.get("status"))) {
            return ResponseEntity.status(401).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
