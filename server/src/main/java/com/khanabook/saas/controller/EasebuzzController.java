package com.khanabook.saas.controller;

import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Easebuzz gateway endpoints.
 *
 *   POST /payments/easebuzz/webhook     — called by Easebuzz with payment status.
 *                                          Hash-verified with the merchant salt.
 *   GET  /payments/easebuzz/verify/{id} — called by the device to reconcile a
 *                                          transaction whose callback was missed
 *                                          (e.g. offline at the wrong moment).
 */
@RestController
@RequestMapping("/payments/easebuzz")
@RequiredArgsConstructor
public class EasebuzzController {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzController.class);

    private final EasebuzzWebhookEventRepository webhookRepo;
    private final RestaurantProfileRepository profileRepo;

    /**
     * Webhook receiver. Easebuzz POSTs form-encoded fields; we verify the reverse
     * hash with the merchant salt before trusting the status.
     * Reverse hash format:
     *   SHA-512(salt|status|||||||||||email|firstname|productinfo|amount|txnid|key)
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestParam Map<String, String> params) {
        Map<String, Object> resp = new HashMap<>();
        String txnId = params.getOrDefault("txnid", "");
        String status = params.getOrDefault("status", "");
        String key = params.getOrDefault("key", "");
        String amount = params.getOrDefault("amount", "");
        String email = params.getOrDefault("email", "");
        String firstname = params.getOrDefault("firstname", "");
        String productinfo = params.getOrDefault("productinfo", "");
        String receivedHash = params.getOrDefault("hash", "");

        if (txnId.isBlank() || status.isBlank() || key.isBlank()) {
            resp.put("ok", false);
            resp.put("error", "missing required fields");
            return ResponseEntity.badRequest().body(resp);
        }

        RestaurantProfile profile = profileRepo.findAll().stream()
                .filter(p -> key.equals(p.getEasebuzzMerchantKey()))
                .findFirst()
                .orElse(null);

        if (profile == null) {
            log.warn("Easebuzz webhook with unknown merchant key");
            resp.put("ok", false);
            resp.put("error", "unknown merchant");
            return ResponseEntity.status(401).body(resp);
        }

        String salt = profile.getEasebuzzSalt();
        String raw = salt + "|" + status + "|||||||||||" + email + "|" + firstname
                + "|" + productinfo + "|" + amount + "|" + txnId + "|" + key;
        String expected = sha512(raw);

        if (!expected.equalsIgnoreCase(receivedHash)) {
            log.warn("Easebuzz webhook hash mismatch for txn {}", txnId);
            resp.put("ok", false);
            resp.put("error", "hash mismatch");
            return ResponseEntity.status(401).body(resp);
        }

        EasebuzzWebhookEvent event = webhookRepo
                .findByRestaurantIdAndTxnId(profile.getRestaurantId(), txnId)
                .orElseGet(EasebuzzWebhookEvent::new);
        event.setRestaurantId(profile.getRestaurantId());
        event.setTxnId(txnId);
        event.setEasebuzzId(params.get("easepayid"));
        event.setStatus(status);
        try {
            event.setAmount(new java.math.BigDecimal(amount));
        } catch (Exception ignored) {}
        event.setRawPayload(params.toString());
        event.setReceivedAt(System.currentTimeMillis());
        webhookRepo.save(event);

        resp.put("ok", true);
        resp.put("txnId", txnId);
        resp.put("status", status);
        return ResponseEntity.ok(resp);
    }

    /**
     * Device reconciliation: returns the authoritative status for a txn from
     * the most recent webhook record.
     *
     * Returns a canonical status — "success" / "failed" / "pending" — so the
     * device doesn't have to know Easebuzz's raw status vocabulary
     * (which uses "success", "failure", "userCancelled", etc.).
     * The raw value is also returned as `gatewayStatus` for diagnostics.
     */
    @GetMapping("/verify/{txnId}")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable String txnId) {
        Long tenantId = TenantContext.getCurrentTenant();
        EasebuzzWebhookEvent event = webhookRepo
                .findByRestaurantIdAndTxnId(tenantId, txnId)
                .orElse(null);

        Map<String, Object> resp = new HashMap<>();
        resp.put("txnId", txnId);
        if (event == null) {
            resp.put("status", "pending");
            resp.put("found", false);
            return ResponseEntity.ok(resp);
        }
        resp.put("status", canonicalStatus(event.getStatus()));
        resp.put("gatewayStatus", event.getStatus());
        resp.put("found", true);
        resp.put("amount", event.getAmount());
        resp.put("receivedAt", event.getReceivedAt());
        return ResponseEntity.ok(resp);
    }

    static String canonicalStatus(String raw) {
        if (raw == null) return "pending";
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "success", "successful", "completed", "captured" -> "success";
            case "failure", "failed", "usercancelled", "user_cancelled",
                    "cancelled", "dropped", "bounced" -> "failed";
            default -> "pending";
        };
    }

    static String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
