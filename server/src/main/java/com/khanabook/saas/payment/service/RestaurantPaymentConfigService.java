package com.khanabook.saas.payment.service;

import com.khanabook.saas.payment.dto.RestaurantPaymentConfigResponse;
import com.khanabook.saas.payment.dto.SaveRestaurantPaymentConfigRequest;
import com.khanabook.saas.payment.entity.PaymentGateway;
import com.khanabook.saas.payment.entity.RestaurantPaymentConfig;
import com.khanabook.saas.payment.repository.RestaurantPaymentConfigRepository;
import com.khanabook.saas.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantPaymentConfigService {

    private final RestaurantPaymentConfigRepository repository;
    private final CryptoService cryptoService;

    @Transactional
    public RestaurantPaymentConfigResponse save(Long restaurantId, SaveRestaurantPaymentConfigRequest request) {
        requireOwnerOrAdmin();
        long now = System.currentTimeMillis();
        RestaurantPaymentConfig config = repository
                .findByRestaurantIdAndGatewayName(restaurantId, PaymentGateway.EASEBUZZ)
                .orElseGet(RestaurantPaymentConfig::new);
        config.setRestaurantId(restaurantId);
        config.setGatewayName(PaymentGateway.EASEBUZZ);
        config.setMerchantKey(request.getMerchantKey().trim());
        config.setEncryptedSalt(cryptoService.encrypt(request.getSalt().trim()));
        config.setEnvironment(request.getEnvironment().trim().toUpperCase());
        config.setIsActive(true);
        if (config.getCreatedAt() == null) {
            config.setCreatedAt(now);
        }
        config.setUpdatedAt(now);
        repository.save(config);
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public RestaurantPaymentConfigResponse get(Long restaurantId) {
        requireOwnerOrAdmin();
        RestaurantPaymentConfig config = repository
                .findByRestaurantIdAndGatewayName(restaurantId, PaymentGateway.EASEBUZZ)
                .orElseThrow(() -> new IllegalArgumentException("Easebuzz config not found"));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public RestaurantPaymentConfig getActiveConfig(Long restaurantId) {
        RestaurantPaymentConfig config = repository
                .findByRestaurantIdAndGatewayName(restaurantId, PaymentGateway.EASEBUZZ)
                .orElseThrow(() -> new IllegalArgumentException("Easebuzz config not found"));
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new IllegalArgumentException("Easebuzz config is inactive");
        }
        return config;
    }

    @Transactional(readOnly = true)
    public RestaurantPaymentConfig getActiveConfigByMerchantKey(String merchantKey) {
        RestaurantPaymentConfig config = repository
                .findByMerchantKeyAndGatewayName(merchantKey, PaymentGateway.EASEBUZZ)
                .orElseThrow(() -> new IllegalArgumentException("Easebuzz merchant config not found"));
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new IllegalArgumentException("Easebuzz config is inactive");
        }
        return config;
    }

    @Transactional
    public RestaurantPaymentConfigResponse toggleActive(Long restaurantId, boolean enabled) {
        requireOwnerOrAdmin();
        RestaurantPaymentConfig config = repository
                .findByRestaurantIdAndGatewayName(restaurantId, PaymentGateway.EASEBUZZ)
                .orElseThrow(() -> new IllegalArgumentException("Easebuzz config not found — save credentials first"));
        if (Boolean.valueOf(enabled).equals(config.getIsActive())) {
            return toResponse(config);
        }
        config.setIsActive(enabled);
        config.setUpdatedAt(System.currentTimeMillis());
        repository.save(config);
        return toResponse(config);
    }

    @Transactional
    public String decryptSalt(RestaurantPaymentConfig config) {
        try {
            return cryptoService.decrypt(config.getEncryptedSalt());
        } catch (IllegalStateException e) {
            // Salt was stored as plaintext before encryption was introduced.
            // Return it as-is and transparently re-encrypt for future requests.
            String plaintext = config.getEncryptedSalt();
            config.setEncryptedSalt(cryptoService.encrypt(plaintext));
            config.setUpdatedAt(System.currentTimeMillis());
            repository.save(config);
            return plaintext;
        }
    }

    private RestaurantPaymentConfigResponse toResponse(RestaurantPaymentConfig config) {
        return RestaurantPaymentConfigResponse.builder()
                .restaurantId(config.getRestaurantId())
                .gateway(config.getGatewayName().name())
                .merchantKeyMasked(maskMerchantKey(config.getMerchantKey()))
                .environment(config.getEnvironment())
                .active(Boolean.TRUE.equals(config.getIsActive()))
                .build();
    }

    private String maskMerchantKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 7) {
            return key.charAt(0) + "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 3);
    }

    private void requireOwnerOrAdmin() {
        String role = TenantContext.getCurrentRole();
        if (!"OWNER".equals(role) && !"KBOOK_ADMIN".equals(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Only owners/admins can manage payment config");
        }
    }
}
