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
import static org.mockito.Mockito.*;

class UserServiceMobileUpdateTest {

    private static final Long TENANT_ID = 100L;
    private static final String OLD_MOBILE = "9876543210";
    private static final String NEW_MOBILE = "9123456780";

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
        userService = new UserServiceImpl(userRepository, restaurantProfileRepository, genericSyncService,
                passwordResetOtpService, emailNotificationService);
    }

    private User phoneAuthOwner() {
        User user = new User();
        user.setId(1L);
        user.setRestaurantId(TENANT_ID);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setLoginId(OLD_MOBILE);
        user.setEmail(OLD_MOBILE);
        user.setPhoneNumber(OLD_MOBILE);
        user.setWhatsappNumber(OLD_MOBILE);
        return user;
    }

    @Test
    void confirmMobileNumberUpdate_rewritesAllLoginIdentifiersIncludingPhoneNumber() {
        User currentUser = phoneAuthOwner();
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(TENANT_ID);
        profile.setWhatsappNumber(OLD_MOBILE);

        when(userRepository.findByRestaurantIdAndLocalIdIn(TENANT_ID, List.of(1L)))
                .thenReturn(List.of(currentUser));
        when(userRepository.findByLoginId(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.findByWhatsappNumber(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(NEW_MOBILE)).thenReturn(Optional.empty());
        when(restaurantProfileRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(profile));

        userService.confirmMobileNumberUpdate(TENANT_ID, NEW_MOBILE, "123456");

        verify(passwordResetOtpService).validateMobileUpdateOtpOrThrow(TENANT_ID, NEW_MOBILE, "123456");
        assertThat(currentUser.getLoginId()).isEqualTo(NEW_MOBILE);
        assertThat(currentUser.getEmail()).isEqualTo(NEW_MOBILE);
        // The regression: phone_number used to keep the old number, so the old
        // number still authenticated after an OTP-verified change.
        assertThat(currentUser.getPhoneNumber()).isEqualTo(NEW_MOBILE);
        assertThat(currentUser.getWhatsappNumber()).isEqualTo(NEW_MOBILE);
        verify(userRepository).save(currentUser);

        assertThat(profile.getWhatsappNumber()).isEqualTo(NEW_MOBILE);
        verify(restaurantProfileRepository).save(profile);
    }

    @Test
    void confirmMobileNumberUpdate_rejectsNumberHeldInPhoneNumberByAnotherAccount() {
        User currentUser = phoneAuthOwner();

        when(userRepository.findByRestaurantIdAndLocalIdIn(TENANT_ID, List.of(1L)))
                .thenReturn(List.of(currentUser));
        when(userRepository.findByLoginId(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.findByWhatsappNumber(NEW_MOBILE)).thenReturn(Optional.empty());

        User otherAccount = new User();
        otherAccount.setId(99L);
        otherAccount.setRestaurantId(TENANT_ID);
        otherAccount.setPhoneNumber(NEW_MOBILE);
        when(userRepository.findByPhoneNumber(NEW_MOBILE)).thenReturn(Optional.of(otherAccount));

        assertThatThrownBy(() -> userService.confirmMobileNumberUpdate(TENANT_ID, NEW_MOBILE, "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already related to another account");

        verify(userRepository, never()).save(any(User.class));
        verify(restaurantProfileRepository, never()).save(any(RestaurantProfile.class));
    }

    @Test
    void requestMobileNumberUpdateOtp_rejectsNumberBelongingToAnotherShop() {
        User currentUser = phoneAuthOwner();

        when(userRepository.findByRestaurantIdAndLocalIdIn(TENANT_ID, List.of(1L)))
                .thenReturn(List.of(currentUser));

        User otherShopUser = new User();
        otherShopUser.setId(99L);
        otherShopUser.setRestaurantId(200L);
        otherShopUser.setLoginId(NEW_MOBILE);
        when(userRepository.findByLoginId(NEW_MOBILE)).thenReturn(Optional.of(otherShopUser));

        assertThatThrownBy(() -> userService.requestMobileNumberUpdateOtp(TENANT_ID, NEW_MOBILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already related to another shop");

        verifyNoInteractions(passwordResetOtpService);
    }
}
