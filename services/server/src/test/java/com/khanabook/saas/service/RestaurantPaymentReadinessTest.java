package com.khanabook.saas.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.payments.controller.RestaurantPaymentConfigController;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RestaurantPaymentReadinessTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void readingReadinessDoesNotTurnOnDisabledPaymentMethod() {
        TenantContext.setCurrentTenant(42L);
        RestaurantProfileRepository profiles = mock(RestaurantProfileRepository.class);
        SubMerchantService subMerchants = mock(SubMerchantService.class);
        RestaurantPaymentConfigController controller =
                new RestaurantPaymentConfigController(profiles, subMerchants);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setEasebuzzEnabled(false);
        EasebuzzSubMerchant subMerchant = new EasebuzzSubMerchant();
        subMerchant.setStatus("ACTIVE");
        subMerchant.setSubMerchantId("SM42");
        when(profiles.findByRestaurantId(42L)).thenReturn(Optional.of(profile));
        when(subMerchants.getByRestaurantId(42L)).thenReturn(subMerchant);

        Map<String, Object> response = controller.getConfig().getBody();

        assertEquals(false, response.get("paymentLinkReady"));
        assertEquals(true, response.get("subMerchantActive"));
        assertEquals(false, response.get("easebuzzEnabled"));
        verify(profiles, org.mockito.Mockito.never()).save(profile);
    }

    @Test
    void counterStaffAreNotHandedTheOwnerKycPortalLink() {
        TenantContext.setCurrentTenant(42L);
        RestaurantProfileRepository profiles = mock(RestaurantProfileRepository.class);
        SubMerchantService subMerchants = mock(SubMerchantService.class);
        RestaurantPaymentConfigController controller =
                new RestaurantPaymentConfigController(profiles, subMerchants);

        EasebuzzSubMerchant subMerchant = new EasebuzzSubMerchant();
        subMerchant.setStatus("PENDING_KYC");
        subMerchant.setSubMerchantId("SM42");
        subMerchant.setKycPortalUrl("https://kyc.easebuzz.in/session/secret");
        when(subMerchants.getByRestaurantId(42L)).thenReturn(subMerchant);

        TenantContext.setCurrentRole("SHOP_STAFF");
        Map<String, Object> staffView = controller.getSubMerchantStatus().getBody();

        // The URL embeds a KYC access key: counter terminals have no business holding it.
        assertEquals("", staffView.get("kycUrl"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, controller::kycAccessKey,
                "staff must not be able to mint a KYC access link");

        // The same rule covers every path that hands back the portal credential.
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.kycPortalLink(Map.of("docType", "id_proof")),
                "staff must not be able to fetch the KYC portal link through the document path");

        TenantContext.setCurrentRole("OWNER");
        assertEquals("https://kyc.easebuzz.in/session/secret",
                controller.getSubMerchantStatus().getBody().get("kycUrl"));

        when(subMerchants.requestKycPortalLink(42L, "id_proof"))
                .thenReturn(Map.of("status", "portal_required", "kycUrl", "https://kyc.easebuzz.in/session/secret"));
        assertEquals("https://kyc.easebuzz.in/session/secret",
                controller.kycPortalLink(Map.of("docType", "id_proof")).getBody().get("kycUrl"));
    }

    @Test
    void lookupFailureIsNotReportedAsMissingOnboarding() {
        TenantContext.setCurrentTenant(42L);
        RestaurantProfileRepository profiles = mock(RestaurantProfileRepository.class);
        SubMerchantService subMerchants = mock(SubMerchantService.class);
        RestaurantPaymentConfigController controller =
                new RestaurantPaymentConfigController(profiles, subMerchants);
        when(profiles.findByRestaurantId(42L)).thenReturn(Optional.of(new RestaurantProfile()));
        when(subMerchants.getByRestaurantId(42L)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, controller::getConfig);
    }
}
