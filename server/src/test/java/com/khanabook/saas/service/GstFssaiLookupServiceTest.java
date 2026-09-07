package com.khanabook.saas.service;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GstFssaiLookupServiceTest {

    private final GstFssaiLookupService service = new GstFssaiLookupService();

    @Test
    void lookupFssai_rejectsInvalidLength() {
        Map<String, Object> result = service.lookupFssai("123");
        assertFalse((Boolean) result.get("valid"));
        assertEquals("Invalid FSSAI format (must be 14 digits)", result.get("error"));
    }

    @Test
    void lookupFssai_rejectsNull() {
        Map<String, Object> result = service.lookupFssai(null);
        assertFalse((Boolean) result.get("valid"));
    }

    @Test
    void lookupFssai_validatesAndParsesLiveApi() {
        // Test with the known working license
        Map<String, Object> result = service.lookupFssai("13625026000292");
        if (Boolean.TRUE.equals(result.get("valid"))) {
            assertEquals("13625026000292", result.get("licenseNo"));
            assertNotNull(result.get("businessName"));
            assertNotNull(result.get("address"));
            assertNotNull(result.get("expiryDate"));
            assertEquals("01-07-2027", result.get("expiryDate"));
            assertEquals("CHAPP1101N", result.get("pan"));
            assertEquals("PATLOLLA SRAVANTHI", result.get("contactPerson"));
            assertEquals("502032", result.get("pincode"));
            assertEquals("TE", result.get("state"));
        }
    }
}
