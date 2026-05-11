package com.khanabook.saas.integration;

import com.khanabook.saas.billing.service.CryptoService;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.integration.dto.MarketplaceConfigRequest;
import com.khanabook.saas.integration.dto.MarketplaceConfigResponse;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceConfigService {

    private final RestaurantProfileRepository restaurantProfileRepository;
    private final CryptoService cryptoService;

    @Transactional(readOnly = true)
    public MarketplaceConfigResponse get(Long restaurantId) {
        RestaurantProfile profile = requireProfile(restaurantId);
        return toResponse(profile);
    }

    @Transactional
    public MarketplaceConfigResponse save(Long restaurantId, MarketplaceConfigRequest request) {
        RestaurantProfile profile = requireProfile(restaurantId);
        if (request.zomatoEnabled() != null) {
            profile.setZomatoEnabled(request.zomatoEnabled());
        }
        if (request.swiggyEnabled() != null) {
            profile.setSwiggyEnabled(request.swiggyEnabled());
        }
        if (request.zomatoApiKey() != null && !request.zomatoApiKey().isBlank()) {
            profile.setZomatoApiKey(encrypt(request.zomatoApiKey().trim()));
        }
        if (request.zomatoWebhookSecret() != null && !request.zomatoWebhookSecret().isBlank()) {
            profile.setZomatoWebhookSecret(encrypt(request.zomatoWebhookSecret().trim()));
        }
        if (request.swiggyApiKey() != null && !request.swiggyApiKey().isBlank()) {
            profile.setSwiggyApiKey(encrypt(request.swiggyApiKey().trim()));
        }
        if (request.swiggyWebhookSecret() != null && !request.swiggyWebhookSecret().isBlank()) {
            profile.setSwiggyWebhookSecret(encrypt(request.swiggyWebhookSecret().trim()));
        }
        long now = System.currentTimeMillis();
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        restaurantProfileRepository.save(profile);
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public String decryptZomatoApiKey(RestaurantProfile profile) {
        return decryptMaybe(profile.getZomatoApiKey());
    }

    @Transactional(readOnly = true)
    public String decryptSwiggyApiKey(RestaurantProfile profile) {
        return decryptMaybe(profile.getSwiggyApiKey());
    }

    @Transactional(readOnly = true)
    public String decryptZomatoWebhookSecret(RestaurantProfile profile) {
        return decryptMaybe(profile.getZomatoWebhookSecret());
    }

    @Transactional(readOnly = true)
    public String decryptSwiggyWebhookSecret(RestaurantProfile profile) {
        return decryptMaybe(profile.getSwiggyWebhookSecret());
    }

    private MarketplaceConfigResponse toResponse(RestaurantProfile profile) {
        Long restaurantId = profile.getRestaurantId();
        return MarketplaceConfigResponse.builder()
                .zomatoEnabled(Boolean.TRUE.equals(profile.getZomatoEnabled()))
                .zomatoApiKeyMasked(mask(decryptMaybe(profile.getZomatoApiKey())))
                .zomatoOutletId(profile.getZomatoOutletId())
                .zomatoWebhookUrl("/api/v1/webhooks/zomato/" + restaurantId + "/order")
                .swiggyEnabled(Boolean.TRUE.equals(profile.getSwiggyEnabled()))
                .swiggyApiKeyMasked(mask(decryptMaybe(profile.getSwiggyApiKey())))
                .swiggyStoreId(profile.getSwiggyStoreId())
                .swiggyWebhookUrl("/api/v1/webhooks/swiggy/" + restaurantId + "/order")
                .build();
    }

    private RestaurantProfile requireProfile(Long restaurantId) {
        return restaurantProfileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant profile not found"));
    }

    private String encrypt(String value) {
        return cryptoService.encrypt(value);
    }

    private String decryptMaybe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return cryptoService.decrypt(value);
        } catch (IllegalStateException ex) {
            return value;
        }
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 3);
    }
}
