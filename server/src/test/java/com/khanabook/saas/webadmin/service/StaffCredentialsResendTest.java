package com.khanabook.saas.webadmin.service;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.entity.UserRole;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import com.khanabook.saas.feature.auth.service.PasswordResetOtpService;
import com.khanabook.saas.feature.business.dto.StaffCredentialsResponse;
import com.khanabook.saas.feature.business.service.BusinessWriteService;
import com.khanabook.saas.feature.menu.data.CategoryRepository;
import com.khanabook.saas.feature.menu.data.MenuItemRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import com.khanabook.saas.feature.staff.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Re-issuing a staff password replaces a real secret, so it must behave like a
 * credential change rather than a notification: every session minted under the old
 * password has to stop working, or a leaked first password never expires.
 */
class StaffCredentialsResendTest {

    private static final Long RESTAURANT_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final String PHONE = "9876543210";

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordResetOtpService otpService;
    private BusinessWriteService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        otpService = mock(PasswordResetOtpService.class);
        service = new BusinessWriteService(
                userRepository,
                mock(CategoryRepository.class),
                mock(MenuItemRepository.class),
                mock(RestaurantTerminalRepository.class),
                mock(RestaurantProfileRepository.class),
                mock(PermissionService.class),
                otpService,
                refreshTokenRepository);
    }

    private User staffAccount() {
        User user = new User();
        user.setId(USER_ID);
        user.setRestaurantId(RESTAURANT_ID);
        user.setPhoneNumber(PHONE);
        user.setWhatsappNumber(PHONE);
        user.setLoginId(PHONE);
        user.setRole(UserRole.SHOP_STAFF);
        user.setIsActive(true);
        user.setPasswordHash("old-hash");
        return user;
    }

    @Test
    void resendReplacesHashInvalidatesTokensAndRevokesSessions() {
        User user = staffAccount();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        StaffCredentialsResponse response = service.resendStaffCredentials(RESTAURANT_ID, USER_ID);

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.phone()).isEqualTo(PHONE);
        // The plaintext is never echoed back to the caller.
        assertThat(response.toString()).doesNotContain(user.getPasswordHash());

        assertThat(user.getPasswordHash()).isNotEqualTo("old-hash");
        assertThat(user.getTokenInvalidatedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllForUser(USER_ID);
        verify(otpService).sendStaffCredentials(eq(PHONE), anyString());
    }

    @Test
    void crossTenantIdIsIndistinguishableFromMissing() {
        User user = staffAccount();
        user.setRestaurantId(99L);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resendStaffCredentials(RESTAURANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));

        verify(otpService, never()).sendStaffCredentials(anyString(), anyString());
        verify(refreshTokenRepository, never()).revokeAllForUser(any());
    }

    @Test
    void ownerAccountCannotHaveItsPasswordRegenerated() {
        User owner = staffAccount();
        owner.setRole(UserRole.OWNER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.resendStaffCredentials(RESTAURANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(400));

        verify(otpService, never()).sendStaffCredentials(anyString(), anyString());
    }

    @Test
    void deactivatedAccountCannotBeReactivatedByResending() {
        User user = staffAccount();
        user.setIsActive(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resendStaffCredentials(RESTAURANT_ID, USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("deactivated");

        verify(otpService, never()).sendStaffCredentials(anyString(), anyString());
        // A re-send must not double as an activation.
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * Delivery is lossy, so a failed send has to surface. The new hash is already
     * stored at this point, which means the owner must be able to retry — the
     * exception propagating is what tells them the delivery did not happen.
     */
    @Test
    void failedDeliveryPropagatesSoOwnerCanRetry() {
        User user = staffAccount();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("WhatsApp send failed with status 500"))
                .when(otpService).sendStaffCredentials(eq(PHONE), anyString());

        assertThatThrownBy(() -> service.resendStaffCredentials(RESTAURANT_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(refreshTokenRepository).revokeAllForUser(USER_ID);
    }
}
