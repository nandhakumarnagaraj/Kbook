package com.khanabook.saas.payment.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class EasebuzzHashService {

    public String buildRequestHash(
            String key,
            String txnId,
            String amount,
            String productInfo,
            String firstName,
            String email,
            String salt
    ) {
        String raw = String.join("|", key, txnId, amount, productInfo, firstName, email) + "|||||||||||" + salt;
        return sha512(raw);
    }

    public String buildWebhookHash(
            String salt,
            String status,
            String email,
            String firstName,
            String productInfo,
            String amount,
            String txnId,
            String key
    ) {
        String raw = salt + "|" + status + "|||||||||||" + email + "|" + firstName
                + "|" + productInfo + "|" + amount + "|" + txnId + "|" + key;
        return sha512(raw);
    }

    public String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash Easebuzz payload", e);
        }
    }
}
