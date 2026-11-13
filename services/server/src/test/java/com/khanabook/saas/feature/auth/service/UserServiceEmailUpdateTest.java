package com.khanabook.saas.feature.auth.service;

import com.khanabook.saas.feature.auth.entity.AuthProvider;
import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.sync.service.GenericSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceEmailUpdateTest {

    private UserRepository userRepository;
    private RestaurantProfileRepository restaurantProfileRepository;
    private GenericSyncService genericSyncService;
    private PasswordResetOtpService passwordResetOtpService;
    private EmailNotificationService emailNotificationService;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        restaurantProfileRepository = mock(RestaurantProfileRepository.class);
        genericSyncService = mock(GenericSyncService.class);
        passwordResetOtpService = mock(PasswordResetOtpService.class);
        emailNotificationService = mock(EmailNotificationService.class);
        userService = new UserServiceImpl(userRepository, restaurantProfileRepository, genericSyncService, passwordResetOtpService,
                emailNotificationService);
    }

    @Test
    void requestEmailUpdateOtp_allowsGoogleAuthUser() {
        Long tenantId = 100L;
        User googleUser = new User();
        googleUser.setId(1L);
        googleUser.setRestaurantId(tenantId);
        googleUser.setAuthProvider(AuthProvider.GOOGLE);
        googleUser.setGoogleEmail("google@khanabook.com");
        googleUser.setEmail("google@khanabook.com");

        when(userRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L)))
                .thenReturn(List.of(googleUser));

        userService.requestEmailUpdateOtp(tenantId, "new@khanabook.com");

        // Google sign-in resolves the account through googleEmail (see AuthServiceImpl's
        // login chain), so changing the profile email cannot lock the owner out.
        verify(passwordResetOtpService).issueEmailUpdateOtp(tenantId, "new@khanabook.com");
    }

    @Test
    void requestEmailUpdateOtp_rejectsEmailBelongingToAnotherShop() {
        Long tenantId = 100L;
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRestaurantId(tenantId);
        currentUser.setAuthProvider(AuthProvider.PHONE);
        currentUser.setEmail("old@khanabook.com");

        when(userRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L)))
                .thenReturn(List.of(currentUser));

        User otherShopUser = new User();
        otherShopUser.setId(99L);
        otherShopUser.setRestaurantId(200L); // different tenant
        otherShopUser.setEmail("taken@khanabook.com");

        when(userRepository.findByEmail("taken@khanabook.com")).thenReturn(Optional.of(otherShopUser));

        assertThatThrownBy(() -> userService.requestEmailUpdateOtp(tenantId, "taken@khanabook.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already related to another shop");

        verifyNoInteractions(passwordResetOtpService);
    }

    @Test
    void requestEmailUpdateOtp_issuesOtpForValidNewEmail() {
        Long tenantId = 100L;
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRestaurantId(tenantId);
        currentUser.setAuthProvider(AuthProvider.PHONE);
        currentUser.setEmail("old@khanabook.com");

        when(userRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L)))
                .thenReturn(List.of(currentUser));
        when(userRepository.findByEmail("new@khanabook.com")).thenReturn(Optional.empty());
        when(userRepository.findByLoginId("new@khanabook.com")).thenReturn(Optional.empty());

        userService.requestEmailUpdateOtp(tenantId, "new@khanabook.com");

        verify(passwordResetOtpService).issueEmailUpdateOtp(tenantId, "new@khanabook.com");
    }

    @Test
    void confirmEmailUpdate_validatesOtpAndUpdatesUserAndProfile() {
        Long tenantId = 100L;
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRestaurantId(tenantId);
        currentUser.setAuthProvider(AuthProvider.PHONE);
        currentUser.setLoginId("9876543210");
        currentUser.setEmail("old@khanabook.com");

        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(tenantId);
        profile.setEmail("old@khanabook.com");

        when(userRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L)))
                .thenReturn(List.of(currentUser));
        when(userRepository.findByEmail("new@khanabook.com")).thenReturn(Optional.empty());
        when(userRepository.findByLoginId("new@khanabook.com")).thenReturn(Optional.empty());
        when(restaurantProfileRepository.findByRestaurantId(tenantId)).thenReturn(Optional.of(profile));

        userService.confirmEmailUpdate(tenantId, "new@khanabook.com", "123456");

        verify(passwordResetOtpService).validateEmailUpdateOtpOrThrow(tenantId, "new@khanabook.com", "123456");
        assertThat(currentUser.getEmail()).isEqualTo("new@khanabook.com");
        verify(userRepository).save(currentUser);

        assertThat(profile.getEmail()).isEqualTo("new@khanabook.com");
        verify(restaurantProfileRepository).save(profile);

        // The previous mailbox must learn that it can no longer sign in.
        verify(emailNotificationService).sendLoginIdChangedNotice("old@khanabook.com", "9876543210");
    }

    @Test
    void confirmEmailUpdate_doesNotNotifyWhenOldEmailIsNotAMailbox() {
        Long tenantId = 100L;
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRestaurantId(tenantId);
        currentUser.setAuthProvider(AuthProvider.PHONE);
        currentUser.setLoginId("9876543210");
        currentUser.setEmail("9876543210"); // PHONE accounts store the number in email

        when(userRepository.findByRestaurantIdAndLocalIdIn(tenantId, List.of(1L)))
                .thenReturn(List.of(currentUser));
        when(userRepository.findByEmail("new@khanabook.com")).thenReturn(Optional.empty());
        when(userRepository.findByLoginId("new@khanabook.com")).thenReturn(Optional.empty());
        when(restaurantProfileRepository.findByRestaurantId(tenantId)).thenReturn(Optional.empty());

        userService.confirmEmailUpdate(tenantId, "new@khanabook.com", "123456");

        verifyNoInteractions(emailNotificationService);
    }
}
