package com.khanabook.saas.feature.payments.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Masks customer PII inside Easebuzz webhook and redirect payloads before they are
 * logged.
 *
 * <p>Easebuzz posts the payer's name, email and phone on every notification, and the
 * hosted-checkout redirect echoes much of the same data. Logging the raw map at DEBUG
 * (or at INFO on a chatty deploy) writes those values to disk and to any log shipper,
 * which turns a routine payment trace into a data-protection problem. This helper
 * returns a deep copy with the sensitive fields replaced by stable placeholders, so
 * operators can still see <em>which</em> fields arrived and that they were well formed
 * without seeing the values.
 *
 * <p>{@code hash} and the transaction identifiers are deliberately left intact: they
 * are the fields needed to debug a signature mismatch, and neither is personal data.
 */
public final class PaymentLogMasker {

    private static final String REDACTED = "***";
    private static final int MAX_DEPTH = 6;

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{10,15}$");

    private PaymentLogMasker() {
    }

    /**
     * @return a masked deep copy of {@code payload}; the input is never mutated so
     *         signature verification still sees the original values.
     */
    public static Map<String, Object> mask(Map<?, ?> payload) {
        if (payload == null) {
            return Map.of();
        }
        return maskMap(payload, 0);
    }

    private static Map<String, Object> maskMap(Map<?, ?> payload, int depth) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : payload.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().toString();
            masked.put(key, maskValue(key, entry.getValue(), depth));
        }
        return masked;
    }

    private static Object maskValue(String key, Object value, int depth) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> nested) {
            return depth >= MAX_DEPTH ? REDACTED : maskMap(nested, depth + 1);
        }
        if (value instanceof List<?> list) {
            if (depth >= MAX_DEPTH) {
                return REDACTED;
            }
            List<Object> maskedList = new ArrayList<>(list.size());
            for (Object item : list) {
                maskedList.add(item instanceof Map<?, ?> nested
                        ? maskMap(nested, depth + 1)
                        : maskScalar(key, String.valueOf(item)));
            }
            return maskedList;
        }
        return maskScalar(key, String.valueOf(value));
    }

    private static String maskScalar(String key, String value) {
        if (value.isEmpty()) {
            return value;
        }
        String normalisedKey = key.toLowerCase();

        if (normalisedKey.contains("email")) {
            return maskEmail(value);
        }
        if (normalisedKey.contains("phone") || normalisedKey.contains("mobile")) {
            return maskPhone(value);
        }
        if (isNameKey(normalisedKey)) {
            return maskName(value);
        }
        if (isHighlySensitiveKey(normalisedKey)) {
            return REDACTED;
        }
        // Unknown key: still catch PII that arrives under a field name we did not predict.
        if (EMAIL.matcher(value).matches()) {
            return maskEmail(value);
        }
        if (PHONE.matcher(value).matches()) {
            return maskPhone(value);
        }
        return value;
    }

    private static boolean isNameKey(String key) {
        return key.equals("name") || key.endsWith("_name") || key.equals("firstname")
                || key.equals("lastname") || key.equals("first_name") || key.equals("last_name")
                || key.contains("name_in_bank");
    }

    private static boolean isHighlySensitiveKey(String key) {
        return key.contains("account") || key.contains("pan") || key.contains("gst")
                || key.contains("aadhaar") || key.contains("address") || key.contains("vpa")
                || key.contains("upi") || key.contains("ifsc") || key.contains("card")
                || key.contains("cvv");
    }

    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return REDACTED;
        }
        return value.charAt(0) + REDACTED + value.substring(at);
    }

    private static String maskPhone(String value) {
        String digits = value.startsWith("+") ? value.substring(1) : value;
        if (digits.length() < 4) {
            return REDACTED;
        }
        return REDACTED + digits.substring(digits.length() - 4);
    }

    private static String maskName(String value) {
        if (value.isBlank()) {
            return value;
        }
        return value.charAt(0) + REDACTED;
    }
}
