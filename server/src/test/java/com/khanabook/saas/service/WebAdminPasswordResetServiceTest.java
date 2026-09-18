package com.khanabook.saas.service;

import com.khanabook.saas.feature.auth.entity.User;
import com.khanabook.saas.feature.auth.repository.RefreshTokenRepository;
import com.khanabook.saas.feature.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAdminPasswordResetServiceTest {

    private static final String SECRET = "test-signing-secret-at-least-32-bytes-long!!";
    private static final String PHONE = "9876543210";

    @Mock private PasswordResetOtpService passwordResetOtpService;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private WebAdminPasswordResetService service;

    @BeforeEach
    void setup() {
        service = new WebAdminPasswordResetService(
                passwordResetOtpService, userRepository, refreshTokenRepository, passwordEncoder, SECRET);
    }

    @Test
    void resetPassword_validToken_setsHashInvalidatesTokensAndRevokesRefresh() {
        doNothing().when(passwordResetOtpService).validateWebAdminOtpOrThrow(PHONE, "123456");
        String tempToken = service.verifyOtp(PHONE, "123456");

        User user = user(42L, "old-hash");
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("newPass1", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("newPass1")).thenReturn("new-hash");

        service.resetPassword(tempToken, "newPass1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenInvalidatedAt()).isNotNull();
        verify(refreshTokenRepository).revokeAllForUser(42L);
    }

    @Test
    void resetPassword_sameAsOldPassword_isRejected() {
        doNothing().when(passwordResetOtpService).validateWebAdminOtpOrThrow(PHONE, "123456");
        String tempToken = service.verifyOtp(PHONE, "123456");

        User user = user(42L, "old-hash");
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("samePass", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.resetPassword(tempToken, "samePass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same as the old password");

        verify(refreshTokenRepository, never()).revokeAllForUser(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_tamperedToken_isRejected() {
        doNothing().when(passwordResetOtpService).validateWebAdminOtpOrThrow(PHONE, "123456");
        String tempToken = service.verifyOtp(PHONE, "123456");

        // Flip the final character of the signature segment.
        String tampered = tempToken.substring(0, tempToken.length() - 1)
                + (tempToken.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> service.resetPassword(tampered, "newPass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired reset token");

        verifyNoInteractions(userRepository);
    }

    @Test
    void resetPassword_malformedToken_isRejected() {
        assertThatThrownBy(() -> service.resetPassword("not-a-valid-token", "newPass1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired reset token");
    }

    private User user(Long id, String hash) {
        User u = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
        u.setPhoneNumber(PHONE);
        u.setLoginId(PHONE);
        u.setPasswordHash(hash);
        return u;
    }
}
