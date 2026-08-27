package com.khanabook.saas.service;

import com.khanabook.saas.controller.AuthController.*;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.entity.RefreshToken;
import com.khanabook.saas.repository.RefreshTokenRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.service.PasswordResetOtpService;
import com.khanabook.saas.service.impl.AuthServiceImpl;
import com.khanabook.saas.utility.JwtUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RestaurantProfileRepository restaurantProfileRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtility jwtUtility;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetOtpService passwordResetOtpService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(authService, "accessTokenExpMs", 3600000L);
    }

    @Test
    void login_success() {
        User user = activeUser("9876543210", "hashed", 100L);
        user.setLoginId("9876543210");
        when(userRepository.findByLoginIdIgnoreCase("9876543210")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
        when(jwtUtility.generateToken(anyString(), anyLong(), anyString(), any())).thenReturn("jwt-token");
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuthResponse resp = authService.login(loginRequest("9876543210", "pass123"));

        assertThat(resp.getToken()).isEqualTo("jwt-token");
        assertThat(resp.getRestaurantId()).isEqualTo(100L);
    }

    @Test
    void signup_newUser_createsProfileAndUser() {
        when(userRepository.findActiveByAnyIdentifier("9876543210")).thenReturn(Optional.empty());
        when(userRepository.findDeletedHoldingIdentifier("9876543210")).thenReturn(java.util.List.of());
        when(passwordEncoder.encode("pass123")).thenReturn("bcrypt-hash");
        when(jwtUtility.generateToken(anyString(), anyLong(), anyString(), any())).thenReturn("signup-token");
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doNothing().when(passwordResetOtpService).validateSignupOtpOrThrow("9876543210", "123456");

        SignupRequest req = new SignupRequest("9876543210", "Nandha", "pass123", "123456", "DEVICE_A");
        AuthResponse resp = authService.signup(req);

        assertThat(resp.getToken()).isEqualTo("signup-token");
        assertThat(resp.getUserName()).isEqualTo("Nandha");

        ArgumentCaptor<RestaurantProfile> profileCaptor = ArgumentCaptor.forClass(RestaurantProfile.class);
        verify(restaurantProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getShopName()).contains("Nandha");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(savedUser.getIsActive()).isTrue();
    }

    @Test
    void signup_restaurantIdIsUuidBased_notSequential() {
        when(userRepository.findActiveByAnyIdentifier(anyString())).thenReturn(Optional.empty());
        when(userRepository.findDeletedHoldingIdentifier(anyString())).thenReturn(java.util.List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(jwtUtility.generateToken(anyString(), anyLong(), anyString(), any())).thenReturn("t");
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doNothing().when(passwordResetOtpService).validateSignupOtpOrThrow(anyString(), eq("123456"));

        AuthResponse r1 = authService.signup(new SignupRequest("1111111111", "A", "p", "123456", "D1"));
        AuthResponse r2 = authService.signup(new SignupRequest("2222222222", "B", "p", "123456", "D2"));

        assertThat(r1.getRestaurantId()).isNotEqualTo(r2.getRestaurantId());
    }

    @Test
    void signup_liveAccountWithSameNumber_isRejected() {
        User live = activeUser("9876543210", "hash", 100L);
        live.setPhoneNumber("9876543210");
        when(userRepository.findActiveByAnyIdentifier("9876543210")).thenReturn(Optional.of(live));

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("9876543210", "Nandha", "pass123", "123456", "DEVICE_A")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void signup_softDeletedAccountWithSameNumber_isAllowedAndReleasesIdentifier() {
        // No LIVE account holds the number -> available.
        when(userRepository.findActiveByAnyIdentifier("9876543210")).thenReturn(Optional.empty());
        doNothing().when(passwordResetOtpService).validateSignupOtpOrThrow("9876543210", "123456");

        // A soft-deleted account still holds the number in the unique-indexed columns.
        User dead = new User();
        ReflectionTestUtils.setField(dead, "id", 42L);
        dead.setIsDeleted(true);
        dead.setPhoneNumber("9876543210");
        dead.setWhatsappNumber("9876543210");
        dead.setLoginId("9876543210");
        when(userRepository.findDeletedHoldingIdentifier("9876543210"))
                .thenReturn(java.util.List.of(dead));

        when(passwordEncoder.encode("pass123")).thenReturn("bcrypt-hash");
        when(jwtUtility.generateToken(anyString(), anyLong(), anyString(), any())).thenReturn("signup-token");
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuthResponse resp = authService.signup(
                new SignupRequest("9876543210", "Nandha", "pass123", "123456", "DEVICE_A"));

        assertThat(resp.getToken()).isEqualTo("signup-token");

        // The dead account's reusable identifiers are released, history preserved (id still set).
        ArgumentCaptor<java.util.List<User>> releaseCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(userRepository).saveAll(releaseCaptor.capture());
        User released = releaseCaptor.getValue().get(0);
        assertThat(released.getPhoneNumber()).isNull();
        assertThat(released.getWhatsappNumber()).isNull();
        assertThat(released.getLoginId()).isEqualTo("9876543210|deleted:42");
        assertThat(released.getIsDeleted()).isTrue();

        // The new live user is created.
        verify(userRepository).saveAndFlush(any());
    }

    private User activeUser(String phone, String hash, Long restaurantId) {
        User u = new User();
        u.setEmail(phone);
        u.setLoginId(phone);
        u.setPasswordHash(hash);
        u.setRestaurantId(restaurantId);
        u.setRole(UserRole.OWNER);
        u.setName("Test User");
        u.setIsActive(true);
        return u;
    }

    private LoginRequest loginRequest(String phone, String password) {
        LoginRequest r = new LoginRequest();
        r.setLoginId(phone);
        r.setPassword(password);
        r.setDeviceId("DEVICE_A");
        return r;
    }
}
