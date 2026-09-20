package com.khanabook.saas.service;

import com.khanabook.saas.core.security.TenantContext;
import com.khanabook.saas.feature.onboarding.service.MerchantAgreementService;
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
import static org.mockito.Mockito.never;
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
        MerchantAgreementService agreements = mock(MerchantAgreementService.class);
        RestaurantPaymentConfigController controller =
                new RestaurantPaymentConfigController(profiles, subMerchants, agreements);

        RestaurantProfile profile = new RestaurantProfile();
        profile.setEasebuzzEnabled(false);
        EasebuzzSubMerchant subMerchant = new EasebuzzSubMerchant();
        subMerchant.setStatus("ACTIVE");
        subMerchant.setSubMerchantId("SM42");
        when(profiles.findByRestaurantId(42L)).thenReturn(Optional.of(profile));
        when(subMerchants.getByRestaurantId(42L)).thenReturn(subMerchant);
        when(agreements.hasCurrentSignedAgreement(42L)).thenReturn(true);

        Map<String, Object> response = controller.getConfig().getBody();

        assertEquals(true, response.get("paymentLinkReady"));
        assertEquals(false, response.get("easebuzzEnabled"));
        verify(subMerchants, never()).ensureEasebuzzEnabled(42L);
        verify(profiles, never()).save(profile);
    }

    @Test
    void lookupFailureIsNotReportedAsMissingOnboarding() {
        TenantContext.setCurrentTenant(42L);
        RestaurantProfileRepository profiles = mock(RestaurantProfileRepository.class);
        SubMerchantService subMerchants = mock(SubMerchantService.class);
        MerchantAgreementService agreements = mock(MerchantAgreementService.class);
        RestaurantPaymentConfigController controller =
                new RestaurantPaymentConfigController(profiles, subMerchants, agreements);
        when(profiles.findByRestaurantId(42L)).thenReturn(Optional.of(new RestaurantProfile()));
        when(subMerchants.getByRestaurantId(42L)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, controller::getConfig);
    }
}
