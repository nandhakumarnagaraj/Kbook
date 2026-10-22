package com.khanabook.saas.feature.payments.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Onboarding failures are surfaced to the owner, so the message shown must be the gateway's own
 * text rather than a dumped response map.
 */
class SubMerchantErrorExtractionTest {

    @Test
    void prefersErrorDescFromTheDocumentedFailureShape() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", false);
        response.put("error", null);
        response.put("error_desc", "Sub merchant already exists");

        assertEquals("Sub merchant already exists", SubMerchantService.extractProviderError(response));
    }

    @Test
    void fallsBackToTheErrorCodeAndThenMessage() {
        Map<String, Object> withError = new HashMap<>();
        withError.put("status", false);
        withError.put("error", "INVALID_BANK_IFSC");
        assertEquals("INVALID_BANK_IFSC", SubMerchantService.extractProviderError(withError));

        Map<String, Object> withMsg = new HashMap<>();
        withMsg.put("status", false);
        withMsg.put("msg", "Hash mismatch");
        assertEquals("Hash mismatch", SubMerchantService.extractProviderError(withMsg));
    }

    @Test
    void neverRendersABooleanAsTheReason() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", false);
        response.put("error", false);

        String text = SubMerchantService.extractProviderError(response);

        // A Boolean is not a reason: the map is dumped instead, but "false" must never stand in
        // for the explanation.
        assertNotEquals("false", text);
        assertTrue(text.contains("error=false"));
    }

    @Test
    void handlesAMissingResponse() {
        assertEquals("Unknown error", SubMerchantService.extractProviderError(null));
    }
}
