package com.khanabook.saas.service;

import com.khanabook.saas.repository.UserRepository;
import com.khanabook.saas.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class WebAdminPasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(WebAdminPasswordResetService.class);
    private static final long TEMP_TOKEN_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final String WEB_RESET_PREFIX = "web-reset:";

    private final PasswordResetOtpService passwordResetOtpService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private record TempTokenEntry(String phoneNumber, long expiresAt) {}

    private final Map<String, TempTokenEntry> tempTokenStore = new ConcurrentHashMap<>();

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

        String tempToken = UUID.randomUUID().toString();
        tempTokenStore.put(tempToken, new TempTokenEntry(phone, System.currentTimeMillis() + TEMP_TOKEN_TTL_MILLIS));
        log.info("Web admin password reset OTP verified for phone={}***, temp token issued", phone.substring(0, 3));
        return tempToken;
    }

    @Transactional
    public void resetPassword(String tempToken, String newPassword) {
        TempTokenEntry entry = tempTokenStore.remove(tempToken);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        User user = userRepository.findByPhoneNumber(entry.phoneNumber())
                .or(() -> userRepository.findByLoginIdIgnoreCase(entry.phoneNumber()))
                .or(() -> userRepository.findByWhatsappNumber(entry.phoneNumber()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenInvalidatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        user.setServerUpdatedAt(System.currentTimeMillis());
        userRepository.save(user);
        log.info("Web admin password reset successful for phone={}***", entry.phoneNumber().substring(0, 3));
    }

    @Scheduled(fixedDelay = 600000) // Every 10 minutes
    public void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        tempTokenStore.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }
}
