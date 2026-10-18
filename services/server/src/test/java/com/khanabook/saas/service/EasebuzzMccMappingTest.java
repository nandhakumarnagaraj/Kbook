package com.khanabook.saas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.khanabook.saas.feature.payments.service.EasebuzzApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The MCC is sent when the sub-merchant is created and Easebuzz cannot change it afterwards
 * (ERA, 2026-10-17), so the mapping from the outlet format chosen at onboarding has to be right
 * the first time.
 */
class EasebuzzMccMappingTest {

    @Test
    @DisplayName("dine-in outlets map to 5812")
    void dineInFormats() {
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat("DINE_IN"));
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat("CAFE"));
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat("CLOUD_KITCHEN"));
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat("dine-in"));
    }

    @Test
    @DisplayName("QSR maps to 5814")
    void qsrFormats() {
        assertEquals("5814", EasebuzzApiClient.mccCodeForFormat("QSR"));
        assertEquals("5814", EasebuzzApiClient.mccCodeForFormat("fast food"));
        assertEquals("5814", EasebuzzApiClient.mccCodeForFormat("Quick Service Restaurant"));
    }

    @Test
    @DisplayName("bars map to 5813 and caterers to 5811")
    void barAndCatererFormats() {
        assertEquals("5813", EasebuzzApiClient.mccCodeForFormat("BAR"));
        assertEquals("5813", EasebuzzApiClient.mccCodeForFormat("pub"));
        assertEquals("5811", EasebuzzApiClient.mccCodeForFormat("CATERER"));
        assertEquals("5811", EasebuzzApiClient.mccCodeForFormat("catering"));
    }

    @Test
    @DisplayName("a missing or unknown format falls back to 5812 rather than failing the submit")
    void unknownFormatFallsBack() {
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat(null));
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat(""));
        assertEquals("5812", EasebuzzApiClient.mccCodeForFormat("SOMETHING_ELSE"));
    }
}
