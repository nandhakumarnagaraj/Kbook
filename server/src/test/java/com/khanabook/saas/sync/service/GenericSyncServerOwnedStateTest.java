package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.RestaurantProfile;
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
}
