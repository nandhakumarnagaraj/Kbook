package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.sync.service.GenericSyncService;

import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenericSyncServerOwnedStateTest {

    @Test
    void deviceUserPushCannotReactivateStaffOrClearInvalidation() {
        User existing = new User();
        existing.setIsActive(false);
        existing.setTokenInvalidatedAt(12345L);
        existing.setRole(UserRole.SHOP_STAFF);

        User incoming = new User();
        incoming.setIsActive(true);
        incoming.setTokenInvalidatedAt(null);
        incoming.setRole(UserRole.OWNER);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsActive()).isFalse();
        assertThat(incoming.getTokenInvalidatedAt()).isEqualTo(12345L);
        assertThat(incoming.getRole()).isEqualTo(UserRole.SHOP_STAFF);
    }

    @Test
    void deviceProfilePushCannotUnsuspendBusiness() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setIsSuspended(true);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setIsSuspended(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsSuspended()).isTrue();
    }

    /**
     * Online Payments Setup is owner-gated in RestaurantPaymentConfigController and
     * additionally requires a signed Merchant Agreement. The flag rides on
     * RestaurantProfileDTO, so a device push must not be able to flip it on and
     * bypass both checks.
     */
    @Test
    void deviceProfilePushCannotEnableEasebuzzPayments() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setEasebuzzEnabled(false);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setEasebuzzEnabled(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getEasebuzzEnabled()).isFalse();
    }

    /**
     * Owner-side counterpart: an owner who legitimately enabled payments online must
     * survive a stale device push carrying the older disabled value.
     */
    @Test
    void deviceProfilePushCannotDisableOwnerEnabledEasebuzz() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setEasebuzzEnabled(true);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setEasebuzzEnabled(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getEasebuzzEnabled()).isTrue();
    }

    @Test
    void deviceProfilePushPreservesEasebuzzWhenUnset() {
        RestaurantProfile existing = new RestaurantProfile();
        existing.setEasebuzzEnabled(true);
        RestaurantProfile incoming = new RestaurantProfile();
        incoming.setEasebuzzEnabled(null);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getEasebuzzEnabled()).isTrue();
    }
}
