package com.khanabook.saas.feature.payments.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The sub-merchant id is issued by Easebuzz (its KYC access-key specification calls it the
 * "Unique ID for Submerchant created by EaseBuzz"). KhanaBook must therefore read it out of the
 * create response instead of inventing one — and must read it from whichever envelope the
 * gateway used.
 */
class SubMerchantIdExtractionTest {

    @Test
    void readsTheIdFromTheDocumentedNestedSubmerchantObject() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("submerchant", Map.of("submerchant_id", "S360NESTED"));

        assertEquals("S360NESTED", SubMerchantService.extractSubMerchantId(response));
    }

    @Test
    void stillReadsATopLevelIdForAccountsThatReturnThatShape() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("submerchant_id", "S360TOP");

        assertEquals("S360TOP", SubMerchantService.extractSubMerchantId(response));
    }

    @Test
    void returnsNullWhenTheGatewayIssuedNoId() {
        assertNull(SubMerchantService.extractSubMerchantId(Map.of("status", true)));
        assertNull(SubMerchantService.extractSubMerchantId(Map.of("submerchant", Map.of())));
        assertNull(SubMerchantService.extractSubMerchantId(Map.of("submerchant_id", "   ")));
        assertNull(SubMerchantService.extractSubMerchantId(null));
    }
}
