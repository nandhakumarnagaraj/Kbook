package com.khanabook.saas.controller;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.menu.service.AssetStorageService;
import com.khanabook.saas.feature.notifications.controller.RestaurantAssetController;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The legacy {@code POST /restaurants/kyc-document} path returns the hosted KYC portal URL, which
 * embeds an access key. Counter staff must not be able to obtain it by attaching a tiny file, so
 * the endpoint is owner-only and the service is never reached for any other role.
 */
class RestaurantAssetControllerKycGateTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void uploadPathIsOwnerOnlyBecauseItReturnsThePortalLink() {
        TenantContext.setCurrentTenant(42L);
        AssetStorageService assets = mock(AssetStorageService.class);
        SubMerchantService subMerchants = mock(SubMerchantService.class);
        RestaurantAssetController controller = new RestaurantAssetController(assets, subMerchants);
        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", new byte[512]);

        TenantContext.setCurrentRole("SHOP_STAFF");
        assertThrows(ResponseStatusException.class,
                () -> controller.uploadKycDocument("id_proof", null, file));
        verify(subMerchants, never()).submitKycDocument(42L, "id_proof", null, file);

        TenantContext.setCurrentRole("OWNER");
        when(subMerchants.submitKycDocument(42L, "id_proof", null, file))
                .thenReturn(Map.of("status", "portal_required", "kycUrl", "https://kyc.easebuzz.in/session/x"));
        assertEquals("https://kyc.easebuzz.in/session/x",
                controller.uploadKycDocument("id_proof", null, file).getBody().get("kycUrl"));
    }
}
