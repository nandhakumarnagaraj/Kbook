package com.khanabook.saas.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.khanabook.saas.controller.AuthController.AuthResponse;
import com.khanabook.saas.controller.AuthController.LoginRequest;
import com.khanabook.saas.controller.AuthController.SignupRequest;
import java.util.Map;
import com.khanabook.saas.entity.AuthProvider;
import com.khanabook.saas.entity.RefreshToken;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.User;
import com.khanabook.saas.entity.UserRole;
import com.khanabook.saas.repository.RefreshTokenRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.service.AuthService;
import com.khanabook.saas.utility.JwtUtility;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RestaurantProfileRepository restaurantProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtility jwtUtility;
    private final PasswordEncoder passwordEncoder;
    private final com.khanabook.saas.service.PasswordResetOtpService passwordResetOtpService;

    @Value("${jwt.expiration.ms:2592000000}")
    private long accessTokenExpMs;

    @Autowired(required = false)
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    private static final long REFRESH_TOKEN_DAYS = 30;

    private String normalizeLoginIdentifier(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.contains("@")) {
            normalized = normalized.toLowerCase(java.util.Locale.ROOT);
        }
        return normalized;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String loginId = normalizeLoginIdentifier(request.getLoginId());
        User user = userRepository.findByLoginIdIgnoreCase(loginId)
                .or(() -> userRepository.findByEmailIgnoreCase(loginId))
                .or(() -> userRepository.findByWhatsappNumber(loginId))
                .orElseThrow(() -> new IllegalArgumentException("Invalid login ID or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid login ID or password");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new IllegalArgumentException("Account is disabled. Contact your administrator.");
        }

        backfillLoginIdIfMissing(user);
        log.info("User logged in: restaurantId={}", user.getRestaurantId());
        return buildAuthResponse(user, request.getDeviceId());
    }

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        ensurePhoneNumberAvailableForSignup(request.getPhoneNumber());
        passwordResetOtpService.validateSignupOtpOrThrow(request.getPhoneNumber(), request.getOtp());

        Long newRestaurantId = Math.abs(UUID.randomUUID().getMostSignificantBits());

        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(newRestaurantId);
        profile.setDeviceId(request.getDeviceId());
        profile.setLocalId(1L);
        profile.setShopName(request.getName() + "'s Restaurant");
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        profile.setLastResetDate(today);
        profile.setLastResetDateProper(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        profile.setLogoVersion(0);
        profile.setUpiQrVersion(0);
        profile.setUpdatedAt(System.currentTimeMillis());
        profile.setServerUpdatedAt(System.currentTimeMillis());
        profile.setCreatedAt(System.currentTimeMillis());
        restaurantProfileRepository.save(profile);

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setName(request.getName());
        user.setEmail(null);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setLoginId(request.getPhoneNumber());
        user.setAuthProvider(AuthProvider.PHONE);
        user.setWhatsappNumber(request.getPhoneNumber());
        user.setPasswordHash(hashedPassword);
        user.setRestaurantId(newRestaurantId);
        user.setDeviceId(request.getDeviceId());
        user.setLocalId(1L);
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setCreatedAt(System.currentTimeMillis());
        user = userRepository.saveAndFlush(user);

        log.info("New user signed up: restaurantId={}", newRestaurantId);
        return buildAuthResponse(user, request.getDeviceId());
    }

    @Override
    @Transactional
    public AuthResponse devSignup(SignupRequest request) {
        ensurePhoneNumberAvailableForSignup(request.getPhoneNumber());
        Long newRestaurantId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        RestaurantProfile profile = new RestaurantProfile();
        profile.setRestaurantId(newRestaurantId);
        profile.setDeviceId(request.getDeviceId());
        profile.setLocalId(1L);
        profile.setShopName(request.getName() + "'s Restaurant");
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        profile.setLastResetDate(today);
        profile.setLastResetDateProper(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
        profile.setLogoVersion(0);
        profile.setUpiQrVersion(0);
        profile.setUpdatedAt(System.currentTimeMillis());
        profile.setServerUpdatedAt(System.currentTimeMillis());
        profile.setCreatedAt(System.currentTimeMillis());
        restaurantProfileRepository.save(profile);
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User();
        user.setName(request.getName());
        user.setEmail(null);
        user.setPhoneNumber(request.getPhoneNumber());
        user.setLoginId(request.getPhoneNumber());
        user.setAuthProvider(AuthProvider.PHONE);
        user.setWhatsappNumber(request.getPhoneNumber());
        user.setPasswordHash(hashedPassword);
        user.setRestaurantId(newRestaurantId);
        user.setDeviceId(request.getDeviceId());
        user.setLocalId(1L);
        user.setRole(UserRole.OWNER);
        user.setIsActive(true);
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setCreatedAt(System.currentTimeMillis());
        userRepository.save(user);
        log.info("Dev signup: restaurantId={} phone={}", newRestaurantId, request.getPhoneNumber());
        return buildAuthResponse(user, request.getDeviceId());
    }

    @Override
    @Transactional
    public AuthResponse devAdminSignup(Map<String, String> body) {
        String phone = body.getOrDefault("phoneNumber", "9000000001");
        String name = body.getOrDefault("name", "KBook Admin");
        String password = body.getOrDefault("password", "admin123");
        String deviceId = body.getOrDefault("deviceId", "WEB_ADMIN");

        Long adminRestaurantId = 0L;

        String hashedPassword = passwordEncoder.encode(password);
        User user = new User();
        user.setName(name);        user.setEmail(phone.contains("@") ? phone : "admin_" + phone + "@kbook.app");
        user.setPhoneNumber(phone.matches("\\d+") ? phone : null);
        user.setLoginId(phone);
        user.setAuthProvider(AuthProvider.PHONE);
        user.setPasswordHash(hashedPassword);
        user.setRestaurantId(adminRestaurantId);
        user.setDeviceId(deviceId);
        user.setLocalId(1L);
        user.setRole(UserRole.KBOOK_ADMIN);
        user.setIsActive(true);
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setCreatedAt(System.currentTimeMillis());
        userRepository.save(user);
        log.info("Dev admin signup: loginId={}", phone);
        return buildAuthResponse(user, deviceId);
    }

    @Override
    @Transactional
    public void requestSignupOtp(String phoneNumber) {
        ensurePhoneNumberAvailableForSignup(phoneNumber);
        passwordResetOtpService.issueSignupOtp(phoneNumber);
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(com.khanabook.saas.controller.AuthController.GoogleLoginRequest request) {
        try {
            if (googleIdTokenVerifier == null) {
                throw new IllegalArgumentException("Google login is not enabled on this server.");
            }

            com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = googleIdTokenVerifier.verify(request.getIdToken());
            if (idToken != null) {
                com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = idToken.getPayload();
                String email = normalizeLoginIdentifier(payload.getEmail());
                String name = (String) payload.get("name");

                return userRepository.findByLoginIdIgnoreCase(email)
                        .or(() -> userRepository.findByEmailIgnoreCase(email))
                        .or(() -> userRepository.findByGoogleEmailIgnoreCase(email))
                        .map(user -> {
                            if (!Boolean.TRUE.equals(user.getIsActive())) {
                                throw new IllegalArgumentException("Account is disabled. Contact your administrator.");
                            }
                            if (user.getGoogleEmail() == null) {
                                user.setGoogleEmail(email);
                            }
                            if (user.getLoginId() == null || user.getLoginId().isBlank()) {
                                user.setLoginId(email);
                            }
                            user.setAuthProvider(AuthProvider.GOOGLE);
                            long now = System.currentTimeMillis();
                            user.setUpdatedAt(now);
                            user.setServerUpdatedAt(now);
                            user.setDeviceId("server");
                            userRepository.save(user);
                            restaurantProfileRepository.findByRestaurantId(user.getRestaurantId()).ifPresent(profile -> {
                                if (profile.getEmail() == null || profile.getEmail().isBlank()) {
                                    profile.setEmail(user.getEmail());
                                    profile.setUpdatedAt(now);
                                    profile.setServerUpdatedAt(now);
                                    profile.setDeviceId("server");
                                    restaurantProfileRepository.save(profile);
                                }
                            });
                            return buildAuthResponse(user, request.getDeviceId());
                        }).orElseGet(() -> {
                            Long newRestaurantId = Math.abs(UUID.randomUUID().getMostSignificantBits());
                            RestaurantProfile profile = new RestaurantProfile();
                            profile.setRestaurantId(newRestaurantId);
                            profile.setDeviceId(request.getDeviceId());
                            profile.setLocalId(1L);
                            profile.setShopName((name != null ? name : "User") + "'s Restaurant");
                            profile.setEmail(email);
                            String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
                            profile.setLastResetDate(today);
                            profile.setLastResetDateProper(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")));
                            profile.setLogoVersion(0);
                            profile.setUpiQrVersion(0);
                            profile.setVersion(0L);
                            profile.setIsDeleted(false);
                            profile.setUpdatedAt(System.currentTimeMillis());
                            profile.setServerUpdatedAt(System.currentTimeMillis());
                            profile.setCreatedAt(System.currentTimeMillis());
                            restaurantProfileRepository.save(profile);

                            User user = new User();
                            user.setName(name != null ? name : "Google User");
                            user.setEmail(email);
                            user.setLoginId(email);
                            user.setGoogleEmail(email);
                            user.setAuthProvider(AuthProvider.GOOGLE);
                            user.setWhatsappNumber(null);
                            user.setPasswordHash("GOOGLE_AUTH");
                            user.setRestaurantId(newRestaurantId);
                            user.setDeviceId(request.getDeviceId());
                            user.setLocalId(1L);
                            user.setRole(UserRole.OWNER);
                            user.setIsActive(true);
                            user.setVersion(0L);
                            user.setIsDeleted(false);
                            user.setUpdatedAt(System.currentTimeMillis());
                            user.setServerUpdatedAt(System.currentTimeMillis());
                            user.setCreatedAt(System.currentTimeMillis());
                            userRepository.save(user);

                            return buildAuthResponse(user, request.getDeviceId());
                        });
            } else {
                throw new IllegalArgumentException("Invalid Google ID token.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google login failed", e);
            throw new IllegalArgumentException("Google login failed. Please ensure you are using a valid Google account.");
        }
    }

    @Override
    @Transactional
    public AuthResponse refreshAccessToken(String rawToken, String clientIp) {
        String tokenHash = sha256(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired refresh token"));

        if (stored.isRevoked()) {
            log.warn("Attempted to use revoked refresh token userId={}", stored.getUserId());
            throw new IllegalArgumentException("Refresh token has been revoked");
        }

        if (stored.getExpiresAt() < System.currentTimeMillis()) {
            log.warn("Expired refresh token used userId={}", stored.getUserId());
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Refresh token has expired. Please log in again.");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Account is disabled");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        log.info("Access token refreshed: userId={} restaurantId={}", user.getId(), user.getRestaurantId());
        return buildAuthResponse(user, user.getDeviceId());
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String tokenHash = sha256(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Refresh token revoked: userId={}", rt.getUserId());
        });
    }

    @Override
    @Transactional
    public void requestPasswordResetOtp(String phoneNumber) {
        findUserByLoginId(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this number"));
        passwordResetOtpService.issueOtp(phoneNumber);
    }

    @Override
    @Transactional
    public void resetPassword(String phoneNumber, String otp, String newPassword) {
        passwordResetOtpService.validateOtpOrThrow(phoneNumber, otp);

        User user = findUserByLoginId(phoneNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be the same as the old password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenInvalidatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setDeviceId("server");
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId());
        log.info("Password changed for user: {} — all refresh tokens revoked", phoneNumber);
    }

    @Override
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = findUserByLoginId(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be the same as the old password.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenInvalidatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        user.setDeviceId("server");
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId());
        log.info("Password changed via settings for user: {} — all refresh tokens revoked", username);
    }

    @Override
    public boolean checkUserExists(String phoneNumber) {
        return findUserByLoginId(phoneNumber).isPresent();
    }

    @Override
    @Transactional
    public void devReset() {
        log.warn("DEV RESET TRIGGERED: Wiping all data...");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        restaurantProfileRepository.deleteAll();
    }

    private AuthResponse buildAuthResponse(User user, String deviceId) {
        String loginIdentifier = getLoginIdentifier(user);
        String accessToken = jwtUtility.generateToken(loginIdentifier, user.getRestaurantId(), user.getRole().name(), deviceId);

        String rawRefreshToken = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = sha256(rawRefreshToken);
        long expiresAt = System.currentTimeMillis() + (REFRESH_TOKEN_DAYS * 24 * 60 * 60 * 1000L);
        refreshTokenRepository.save(new RefreshToken(tokenHash, user.getId(), user.getRestaurantId(), expiresAt, deviceId));

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                accessTokenExpMs / 1000,
                user.getRestaurantId(),
                user.getName(),
                loginIdentifier,
                user.getEmail(),
                user.getWhatsappNumber(),
                user.getRole().name()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private java.util.Optional<User> findUserByLoginId(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .or(() -> userRepository.findByLoginIdIgnoreCase(normalizeLoginIdentifier(phoneNumber)))
                .or(() -> userRepository.findByEmailIgnoreCase(normalizeLoginIdentifier(phoneNumber)))
                .or(() -> userRepository.findByWhatsappNumber(phoneNumber));
    }

    private void ensurePhoneNumberAvailableForSignup(String phoneNumber) {
        if (findUserByLoginId(phoneNumber).isPresent()) {
            throw new IllegalArgumentException("This number is already registered.");
        }
    }

    private void backfillLoginIdIfMissing(User user) {
        if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
            return;
        }
        if (user.getAuthProvider() == AuthProvider.GOOGLE && user.getGoogleEmail() != null
                && !user.getGoogleEmail().isBlank()) {
            user.setLoginId(user.getGoogleEmail());
        } else {
            user.setLoginId(user.getEmail());
        }
        user.setUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);
    }

    private String getLoginIdentifier(User user) {
        if (user.getLoginId() != null && !user.getLoginId().isBlank()) {
            return user.getLoginId();
        }
        return user.getEmail();
    }
}
