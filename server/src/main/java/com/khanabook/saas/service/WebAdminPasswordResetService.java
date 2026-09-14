package com.khanabook.saas.service;

import com.khanabook.saas.repository.RefreshTokenRepository;
import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

/**
 * Web-admin "forgot password" flow. Kept behaviourally identical to the Android
 * reset flow in {@link com.khanabook.saas.service.impl.AuthServiceImpl}:
 * OTP possession proves ownership, resetting the password rejects reuse of the
 * old password, invalidates all outstanding access tokens (via
 * {@code tokenInvalidatedAt}) and revokes every refresh token for the user.
 *
 * <p>The temp token minted after OTP verification is a stateless, HMAC-signed
 * value — it carries the phone and issue time and is verified by recomputing the
 * MAC. This survives restarts and works across multiple server instances, so no
 * in-memory store or cleanup job is needed.
 */
@Service
public class WebAdminPasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(WebAdminPasswordResetService.class);
    private static final long TEMP_TOKEN_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    private final PasswordResetOtpService passwordResetOtpService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final byte[] signingKey;

    public WebAdminPasswordResetService(PasswordResetOtpService passwordResetOtpService,
                                        UserRepository userRepository,
                                        RefreshTokenRepository refreshTokenRepository,
                                        PasswordEncoder passwordEncoder,
                                        @Value("${jwt.secret}") String jwtSecret) {
        this.passwordResetOtpService = passwordResetOtpService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.signingKey = jwtSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void requestOtp(String phone) {
        userRepository.findByPhoneNumber(phone)
                .or(() -> userRepository.findByLoginIdIgnoreCase(phone))
                .or(() -> userRepository.findByWhatsappNumber(phone))
                .orElseThrow(() -> new IllegalArgumentException("No account found with this phone number"));

        passwordResetOtpService.issueWebAdminOtp(phone);
    }

    @Transactional
    public String verifyOtp(String phone, String otp) {
        passwordResetOtpService.validateWebAdminOtpOrThrow(phone, otp);

        String tempToken = mintTempToken(phone, System.currentTimeMillis());
        log.info("Web admin password reset OTP verified for phone={}***, temp token issued",
                phone.length() >= 3 ? phone.substring(0, 3) : "***");
        return tempToken;
    }

    @Transactional
    public void resetPassword(String tempToken, String newPassword) {
        String phone = verifyTempToken(tempToken);

        User user = userRepository.findByPhoneNumber(phone)
                .or(() -> userRepository.findByLoginIdIgnoreCase(phone))
                .or(() -> userRepository.findByWhatsappNumber(phone))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be the same as the old password.");
        }

        long now = System.currentTimeMillis();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenInvalidatedAt(now);
        user.setUpdatedAt(now);
        user.setServerUpdatedAt(now);
        userRepository.save(user);

        refreshTokenRepository.revokeAllForUser(user.getId());
        log.info("Web admin password reset successful for phone={}*** — all refresh tokens revoked",
                phone.length() >= 3 ? phone.substring(0, 3) : "***");
    }

    // ─── Stateless temp token ──────────────────────────────────────────────────

    private String mintTempToken(String phone, long issuedAt) {
        String payload = B64_ENC.encodeToString(phone.getBytes(StandardCharsets.UTF_8))
                + "." + B64_ENC.encodeToString(Long.toString(issuedAt).getBytes(StandardCharsets.UTF_8));
        String signature = B64_ENC.encodeToString(hmac(payload));
        return payload + "." + signature;
    }

    private String verifyTempToken(String tempToken) {
        if (tempToken == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        String[] parts = tempToken.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        String payload = parts[0] + "." + parts[1];
        byte[] expectedSig = hmac(payload);
        byte[] providedSig;
        try {
            providedSig = B64_DEC.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        // Constant-time comparison to avoid signature-timing leaks.
        if (!MessageDigest.isEqual(expectedSig, providedSig)) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        long issuedAt;
        String phone;
        try {
            phone = new String(B64_DEC.decode(parts[0]), StandardCharsets.UTF_8);
            issuedAt = Long.parseLong(new String(B64_DEC.decode(parts[1]), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        if (System.currentTimeMillis() - issuedAt > TEMP_TOKEN_TTL_MILLIS) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        return phone;
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign reset token", e);
        }
    }
}
