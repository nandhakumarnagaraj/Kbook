package com.khanabook.saas.feature.payments.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentLogMaskerTest {

    @Test
    void masksPayerIdentityButKeepsTransactionIdentifiers() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("txnid", "KB_1001_9f2c");
        payload.put("easebuzz_id", "E1234567");
        payload.put("hash", "a1b2c3d4e5f6");
        payload.put("email", "ravi.kumar@example.com");
        payload.put("phone", "9876543210");
        payload.put("firstname", "Ravi");

        Map<String, Object> masked = PaymentLogMasker.mask(payload);

        assertEquals("KB_1001_9f2c", masked.get("txnid"));
        assertEquals("E1234567", masked.get("easebuzz_id"));
        assertEquals("a1b2c3d4e5f6", masked.get("hash"), "hash is needed to debug signature mismatches");
        assertEquals("r***@example.com", masked.get("email"));
        assertEquals("***3210", masked.get("phone"));
        assertEquals("R***", masked.get("firstname"));
    }

    @Test
    void doesNotMutateTheCallerPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "ravi.kumar@example.com");

        Map<String, Object> masked = PaymentLogMasker.mask(payload);

        assertNotSame(payload, masked);
        assertEquals("ravi.kumar@example.com", payload.get("email"),
                "signature verification still needs the original values");
    }

    @Test
    void masksNestedWebhookData() {
        Map<String, Object> data = new HashMap<>();
        data.put("customer_email", "buyer@shop.in");
        data.put("customer_phone", "+919812345678");
        data.put("sub_merchant_name", "Chai Point");
        data.put("sub_merchant_account_number", "123456789012");
        data.put("status", "success");

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "PAYMENT_SUCCESS");
        payload.put("data", data);

        @SuppressWarnings("unchecked")
        Map<String, Object> maskedData = (Map<String, Object>) PaymentLogMasker.mask(payload).get("data");

        assertEquals("b***@shop.in", maskedData.get("customer_email"));
        assertEquals("***5678", maskedData.get("customer_phone"));
        assertEquals("C***", maskedData.get("sub_merchant_name"));
        assertEquals("***", maskedData.get("sub_merchant_account_number"));
        assertEquals("success", maskedData.get("status"));
    }

    @Test
    void masksUnpredictedKeysWhoseValueLooksLikePii() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("payer_contact_v2", "someone@example.org");
        payload.put("note", "9812345678");
        payload.put("amount", "249.00");

        Map<String, Object> masked = PaymentLogMasker.mask(payload);

        assertEquals("s***@example.org", masked.get("payer_contact_v2"));
        assertEquals("***5678", masked.get("note"));
        assertEquals("249.00", masked.get("amount"));
    }

    @Test
    void masksListsOfNestedMapsAndHandlesNulls() {
        Map<String, Object> payer = new HashMap<>();
        payer.put("name", "Anita");
        Map<String, Object> payload = new HashMap<>();
        payload.put("payers", List.of(payer));
        payload.put("email", null);

        Map<String, Object> masked = PaymentLogMasker.mask(payload);

        assertEquals("A***", ((Map<?, ?>) ((List<?>) masked.get("payers")).get(0)).get("name"));
        assertTrue(masked.containsKey("email"));
        assertEquals(null, masked.get("email"));
    }

    @Test
    void emptyAndNullPayloadsAreSafe() {
        assertTrue(PaymentLogMasker.mask(null).isEmpty());
        assertTrue(PaymentLogMasker.mask(Map.of()).isEmpty());
    }

    @Test
    void shortOrMalformedValuesDoNotLeak() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "not-an-email");
        payload.put("phone", "12");

        Map<String, Object> masked = PaymentLogMasker.mask(payload);

        assertEquals("***", masked.get("email"));
        assertEquals("***", masked.get("phone"));
        assertFalse(String.valueOf(masked.get("email")).contains("not-an-email"));
    }
}
