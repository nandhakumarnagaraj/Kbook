package com.khanabook.saas.controller;

import com.khanabook.saas.BaseIntegrationTest;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketplaceConfigControllerTest extends BaseIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void healthCheck_swiggy_fullyConfigured_returnsHealthy() {
        Long restaurantId = 1001L;
        String token = persistUserAndGetToken("swiggy_health@test.com", restaurantId, UserRole.OWNER);

        // Configure Swiggy credentials
        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
        profile.setSwiggyEnabled(true);
        profile.setSwiggyApiKey("sk_live_swiggy_key_12345678");
        profile.setSwiggyStoreId("store_001");
        profile.setSwiggyWebhookSecret("whsec_test_secret_key");
        restaurantProfileRepository.save(profile);

        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=SWIGGY",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("platform")).isEqualTo("SWIGGY");
        assertThat(resp.getBody().get("enabled")).isEqualTo(true);
        assertThat(resp.getBody().get("apiKeyConfigured")).isEqualTo(true);
        assertThat(resp.getBody().get("apiKeyFormatValid")).isEqualTo(true);
        assertThat(resp.getBody().get("storeIdConfigured")).isEqualTo(true);
        assertThat(resp.getBody().get("storeIdFormatValid")).isEqualTo(true);
        assertThat(resp.getBody().get("webhookRegistered")).isEqualTo(true);
        assertThat(resp.getBody().get("healthy")).isEqualTo(true);
    }

    @Test
    void healthCheck_zomato_fullyConfigured_returnsHealthy() {
        Long restaurantId = 1002L;
        String token = persistUserAndGetToken("zomato_health@test.com", restaurantId, UserRole.OWNER);

        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
        profile.setZomatoEnabled(true);
        profile.setZomatoApiKey("zomato_api_key_12345678");
        profile.setZomatoOutletId("outlet_abc");
        profile.setZomatoWebhookSecret("whsec_zomato_secret");
        restaurantProfileRepository.save(profile);

        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=ZOMATO",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("platform")).isEqualTo("ZOMATO");
        assertThat(resp.getBody().get("enabled")).isEqualTo(true);
        assertThat(resp.getBody().get("apiKeyConfigured")).isEqualTo(true);
        assertThat(resp.getBody().get("apiKeyFormatValid")).isEqualTo(true);
        assertThat(resp.getBody().get("outletIdConfigured")).isEqualTo(true);
        assertThat(resp.getBody().get("outletIdFormatValid")).isEqualTo(true);
        assertThat(resp.getBody().get("webhookRegistered")).isEqualTo(true);
        assertThat(resp.getBody().get("healthy")).isEqualTo(true);
    }

    @Test
    void healthCheck_disabledMarketplace_returnsUnhealthy() {
        Long restaurantId = 1003L;
        String token = persistUserAndGetToken("disabled_mp@test.com", restaurantId, UserRole.OWNER);

        // Don't enable anything — should be unhealthy
        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=SWIGGY",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("healthy")).isEqualTo(false);
        assertThat(resp.getBody().get("enabled")).isEqualTo(false);
    }

    @Test
    void healthCheck_missingCredentials_returnsUnhealthy() {
        Long restaurantId = 1004L;
        String token = persistUserAndGetToken("missing_creds@test.com", restaurantId, UserRole.OWNER);

        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
        profile.setZomatoEnabled(true);
        // No API key or outlet ID set
        restaurantProfileRepository.save(profile);

        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=ZOMATO",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("healthy")).isEqualTo(false);
        assertThat(resp.getBody().get("apiKeyConfigured")).isEqualTo(false);
        assertThat(resp.getBody().get("outletIdConfigured")).isEqualTo(false);
    }

    @Test
    void healthCheck_invalidPlatform_returns400() {
        Long restaurantId = 1005L;
        String token = persistUserAndGetToken("invalid_plat@test.com", restaurantId, UserRole.OWNER);

        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=INVALID",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void healthCheck_requiresAuth() {
        ResponseEntity<Map> resp = rest.getForEntity(
            "/marketplace/health?platform=SWIGGY",
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthCheck_apiKeyFormat_invalid_returnsFormatInvalid() {
        Long restaurantId = 1006L;
        String token = persistUserAndGetToken("badkey_fmt@test.com", restaurantId, UserRole.OWNER);

        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
        profile.setSwiggyEnabled(true);
        profile.setSwiggyApiKey("abc");  // Too short
        profile.setSwiggyStoreId("store_001");
        restaurantProfileRepository.save(profile);

        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=SWIGGY",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("apiKeyFormatValid")).isEqualTo(false);
        assertThat(resp.getBody().get("healthy")).isEqualTo(false);
    }

    @Test
    void healthCheck_storeIdFormat_invalid_returnsFormatInvalid() {
        Long restaurantId = 1007L;
        String token = persistUserAndGetToken("badstore_fmt@test.com", restaurantId, UserRole.OWNER);

        RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId).orElseThrow();
        profile.setSwiggyEnabled(true);
        profile.setSwiggyApiKey("sk_live_valid_key_12345678");
        profile.setSwiggyStoreId("");  // Empty store ID
        restaurantProfileRepository.save(profile);

        ResponseEntity<Map> resp = rest.exchange(
            "/marketplace/health?platform=SWIGGY",
            HttpMethod.GET,
            bearerRequest(token),
            Map.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("storeIdConfigured")).isEqualTo(false);
        assertThat(resp.getBody().get("healthy")).isEqualTo(false);
    }

    // --- Helpers ---

    private HttpEntity<Void> bearerRequest(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }
}
