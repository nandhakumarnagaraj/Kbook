package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileSyncServiceMergeTest {

    private final UserProfileSyncService service = new UserProfileSyncService();

    private User existingUser() {
        User user = new User();
        user.setId(1L);
        user.setRestaurantId(100L);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setLoginId("9123456780");
        user.setEmail("9123456780");
        user.setPhoneNumber("9123456780");
        user.setWhatsappNumber("9123456780");
        user.setPasswordHash("hashed");
        return user;
    }

    @Test
    void mergeUserFields_restoresPhoneNumberWhenPushOmitsIt() {
        User existing = existingUser();
        User incoming = new User();
        incoming.setId(existing.getId());
        // The sync push DTO does not carry phoneNumber, so it arrives null.
        incoming.setPhoneNumber(null);
        incoming.setWhatsappNumber("9123456780");

        service.mergeUserFields(incoming, existing);

        assertThat(incoming.getPhoneNumber()).isEqualTo("9123456780");
    }

    @Test
    void mergeUserFields_keepsServerPhoneNumberEvenWhenPushCarriesDifferentValue() {
        User existing = existingUser();
        User incoming = new User();
        incoming.setId(existing.getId());
        incoming.setPhoneNumber("9876543210");

        service.mergeUserFields(incoming, existing);

        assertThat(incoming.getPhoneNumber()).isEqualTo("9123456780");
    }

    @Test
    void mergeUserFields_leavesNullPhoneNumberWhenServerHasNone() {
        User existing = existingUser();
        existing.setPhoneNumber(null);
        User incoming = new User();
        incoming.setId(existing.getId());

        service.mergeUserFields(incoming, existing);

        assertThat(incoming.getPhoneNumber()).isNull();
    }
}
