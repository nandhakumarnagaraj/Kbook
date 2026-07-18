package com.khanabook.saas.service;

import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.UserRepository;
import net.jqwik.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: web-admin-full-capability, Property 16: Password Reset Validation
 *
 * Validates: Requirements 13.6
 */
class PasswordResetProperties {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private PasswordResetOtpService passwordResetOtpService;
    private WebAdminPasswordResetService resetService;

    private void setup() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        passwordResetOtpService = mock(PasswordResetOtpService.class);
        resetService = new WebAdminPasswordResetService(passwordResetOtpService, userRepository, passwordEncoder);
    }

    // ─── Property 16: Valid passwords (>= 6 chars) with valid tokens work ───────

    /**
     * Property 16a: For any password of length >= 6, given a valid temp token,
     * the system SHALL accept the reset — the password is hashed and saved.
     *
     * Validates: Requirements 13.6
     */
    @Property(tries = 100)
    @Label("Property 16: Valid passwords (>= 6 chars) with valid token are accepted")
    void validPasswordWithValidTokenIsAccepted(
            @ForAll("validPasswords") String password,
            @ForAll("phoneNumbers") String phone
    ) {
        setup();

        User user = createUser(phone);
        when(userRepository.findByPhoneNumber(phone)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(password)).thenReturn("encoded_" + password);

        // Issue a temp token via verifyOtp
        doNothing().when(passwordResetOtpService).validateOtpOrThrow(eq(phone), eq("1234"));
        String tempToken = resetService.verifyOtp(phone, "1234");

        // Reset password with valid token
        resetService.resetPassword(tempToken, password);

        // Password must be hashed and saved
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(user);
        assertEquals("encoded_" + password, user.getPasswordHash());
        assertNotNull(user.getTokenInvalidatedAt());
    }

    // ─── Property 16: Invalid/expired tokens always fail ────────────────────────

    /**
     * Property 16b: For any password (regardless of length), if the temp token
     * is invalid (not previously issued), the system SHALL reject the reset.
     *
     * Validates: Requirements 13.6
     */
    @Property(tries = 100)
    @Label("Property 16: Invalid tokens always fail regardless of password")
    void invalidTokenAlwaysFails(
            @ForAll("anyPasswords") String password,
            @ForAll("invalidTokens") String invalidToken
    ) {
        setup();

        assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(invalidToken, password),
                "Reset must fail with invalid token");

        verify(userRepository, never()).save(any());
    }

    // ─── Property 16: Short passwords are handled by DTO validation ─────────────

    /**
     * Property 16c: The ResetPasswordRequest DTO enforces @Size(min=6, max=128).
     * Passwords < 6 chars would be rejected at the controller validation layer
     * (MethodArgumentNotValidException). At the service level, the resetPassword()
     * method trusts that input has already been validated. This test verifies that
     * the DTO @Size constraint is correctly configured.
     *
     * Validates: Requirements 13.6
     */
    @Property(tries = 100)
    @Label("Property 16: Short passwords fail DTO validation (min 6 chars)")
    void shortPasswordsFailDtoValidation(
            @ForAll("shortPasswords") String shortPassword
    ) {
        // Validate that the DTO @Size annotation would reject short passwords
        var dto = new com.khanabook.saas.webadmin.dto.ResetPasswordRequest("some-token", shortPassword);

        jakarta.validation.Validator validator = jakarta.validation.Validation
                .buildDefaultValidatorFactory()
                .getValidator();

        var violations = validator.validate(dto);
        assertFalse(violations.isEmpty(),
                "Password '" + shortPassword + "' (length=" + shortPassword.length() +
                        ") must be rejected by @Size(min=6) validation");
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────────

    private User createUser(String phone) {
        User user = new User();
        user.setId(1L);
        user.setRestaurantId(1L);
        user.setName("Test User");
        user.setLoginId(phone);
        user.setPhoneNumber(phone);
        user.setWhatsappNumber(phone);
        user.setPasswordHash("old_hash");
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setLocalId(1L);
        user.setDeviceId("test-device");
        user.setVersion(0L);
        user.setIsDeleted(false);
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setCreatedAt(System.currentTimeMillis());
        return user;
    }

    // ─── Generators ─────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<String> validPasswords() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(6)
                .ofMaxLength(128);
    }

    @Provide
    Arbitrary<String> shortPasswords() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(5);
    }

    @Provide
    Arbitrary<String> anyPasswords() {
        return Arbitraries.strings()
                .all()
                .ofMinLength(1)
                .ofMaxLength(128);
    }

    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.strings()
                .numeric()
                .ofLength(10);
    }

    @Provide
    Arbitrary<String> invalidTokens() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(10)
                .ofMaxLength(50);
    }
}
