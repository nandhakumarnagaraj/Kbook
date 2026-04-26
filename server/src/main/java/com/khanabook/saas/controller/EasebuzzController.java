package com.khanabook.saas.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.khanabook.saas.payment.service.EasebuzzPaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
@Slf4j
public class EasebuzzController {
    private final EasebuzzPaymentService paymentService;

    @RequestMapping(value = "/return/success", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> returnSuccess(@RequestParam Map<String, String> params) {
        reconcileReturnCallback(params);
        return ResponseEntity.ok(returnPage(
                "Payment received",
                "Returning to app...",
                buildAppReturnUrl("success", params)
        ));
    }

    @RequestMapping(value = "/return/failure", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> returnFailure(@RequestParam Map<String, String> params) {
        reconcileReturnCallback(params);
        return ResponseEntity.ok(returnPage(
                "Payment not completed",
                "Returning to app...",
                buildAppReturnUrl("failure", params)
        ));
    }

    private void reconcileReturnCallback(Map<String, String> params) {
        try {
            paymentService.processGatewayCallback(params);
        } catch (Exception ex) {
            log.warn("Easebuzz return callback reconciliation failed for txnId={}: {}", params.get("txnid"), ex.getMessage());
        }
    }

    private static String returnPage(String title, String message, String appUrl) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>"
                + "<title>" + title + "</title>"
                + "<script>window.location.replace('" + appUrl + "');"
                + "setTimeout(function(){window.location.href='" + appUrl + "';},150);</script>"
                + "</head>"
                + "<body style=\"font-family:sans-serif;text-align:center;padding:2em;\">"
                + "<h3>" + title + "</h3><p>" + message + "</p>"
                + "<p><a href=\"" + appUrl + "\">Tap here if the app does not open automatically</a></p>"
                + "</body></html>";
    }

    private static String buildAppReturnUrl(String outcome, Map<String, String> params) {
        String txnId = params.getOrDefault("txnid", params.getOrDefault("txnId", ""));
        String status = params.getOrDefault("status", outcome);
        return "khanabook://payment/" + outcome
                + "?txnid=" + urlEncode(txnId)
                + "&status=" + urlEncode(status);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
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
