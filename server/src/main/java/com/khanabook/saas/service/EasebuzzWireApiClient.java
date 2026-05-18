package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Easebuzz WIRE Platform API Client.
 *
 * The WIRE platform (wire.easebuzz.in) uses a different authentication mechanism
 * from the standard payment APIs. All WIRE APIs require:
 *   - Authorization header: SHA-512("{key}|{salt}")
 *   - WIRE-API-KEY header: the WIRE-specific API key
 *
 * APIs implemented here:
 *   1. Webhook Configuration for InstaCollect (QR)
 *   2. Get Sub-merchant details by Email
 *   3. Get Sub-merchant Details by ID
 *   4. Webhook Configuration for WIRE (Payouts)
 *   5. Get KYC Profile URL
 *   6. Get Sub-merchant details by Key
 */
@Service
public class EasebuzzWireApiClient {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzWireApiClient.class);
    private final RestTemplate restTemplate;
    private final EasebuzzProperties props;

    public EasebuzzWireApiClient(EasebuzzProperties props) {
        this.props = props;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    // ============================================================
    // Auth helpers — WIRE uses Authorization header + WIRE-API-KEY
    // ============================================================

    private HttpHeaders buildWireHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", generateWireAuthHash());
        if (props.getWireApiKey() != null && !props.getWireApiKey().isBlank()) {
            headers.set("WIRE-API-KEY", props.getWireApiKey());
        }
        return headers;
    }

    /**
     * WIRE auth hash: SHA-512("{key}|{salt}")
     * This is simpler than the payment APIs — just key and salt with a pipe.
     */
    private String generateWireAuthHash() {
        String raw = props.getMerchantKey() + "|" + props.getSalt();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) {
                hash.append(String.format("%02x", b));
            }
            return hash.toString();
        } catch (Exception e) {
            throw new RuntimeException("WIRE auth hash failed", e);
        }
    }

    private String baseUrl() {
        return props.getWireBaseUrl();
    }

    // ============================================================
    // 1. Webhook Configuration for InstaCollect (QR)
    // ============================================================
    // PUT /api/v1/insta-collect/merchants/webhooks/
    // Hash: SHA-512(key|salt) in Authorization header
    // Uses: WIRE-API-KEY header

    public Map<String, Object> configureInstaCollectWebhook(
            String merchantKey,
            String merchantEmail,
            List<Map<String, Object>> webhookConfigs) {
        HttpHeaders headers = buildWireHeaders();
        Map<String, Object> body = new HashMap<>();
        body.put("key", props.getMerchantKey());
        body.put("merchant_key", merchantKey);
        if (merchantEmail != null && !merchantEmail.isBlank()) {
            body.put("merchant_email", merchantEmail);
        }
        body.put("webhook_conf", webhookConfigs);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/insta-collect/merchants/webhooks/",
                    HttpMethod.PUT, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("InstaCollect webhook configuration updated successfully");
            return response.getBody();
        } catch (Exception e) {
            log.error("InstaCollect webhook config failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // Convenience method for a single webhook config
    public Map<String, Object> configureInstaCollectWebhook(
            String merchantKey,
            String merchantEmail,
            String eventType,
            String url,
            String intervalUnit,
            int intervalValue,
            int maxAttempts) {
        Map<String, Object> config = new HashMap<>();
        config.put("event_type", eventType);
        config.put("status", "enable");
        config.put("url", url);
        config.put("interval_unit", intervalUnit);
        config.put("interval_value", intervalValue);
        config.put("max_attempts", maxAttempts);

        return configureInstaCollectWebhook(merchantKey, merchantEmail, List.of(config));
    }

    // ============================================================
    // 2. Get Sub-merchant Details by Email
    // ============================================================
    // GET /api/v1/merchants/retrieve/?key={key}&email={email}

    public Map<String, Object> getSubMerchantByEmail(String email) {
        HttpHeaders headers = buildWireHeaders();
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl() + "/api/v1/merchants/retrieve/")
                .queryParam("key", props.getMerchantKey())
                .queryParam("email", email)
                .build()
                .toUriString();

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("Sub-merchant lookup by email succeeded for: {}", email);
            return response.getBody();
        } catch (Exception e) {
            log.error("Sub-merchant lookup by email failed for {}: {}", email, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ============================================================
    // 3. Get Sub-merchant Details by ID
    // ============================================================
    // GET /api/v1/merchants/{submerchant_id}/

    public Map<String, Object> getSubMerchantById(String subMerchantId) {
        HttpHeaders headers = buildWireHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/merchants/" + subMerchantId + "/",
                    HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("Sub-merchant lookup by ID succeeded for: {}", subMerchantId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Sub-merchant lookup by ID failed for {}: {}", subMerchantId, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ============================================================
    // 4. Webhook Configuration for WIRE (Payouts)
    // ============================================================
    // PUT /api/v1/merchants/webhooks/

    public Map<String, Object> configurePayoutWebhook(
            String merchantKey,
            List<Map<String, Object>> webhookConfigs) {
        HttpHeaders headers = buildWireHeaders();
        Map<String, Object> body = new HashMap<>();
        body.put("key", props.getMerchantKey());
        body.put("merchant_key", merchantKey);
        body.put("webhook_conf", webhookConfigs);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/merchants/webhooks/",
                    HttpMethod.PUT, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("Payout webhook configuration updated successfully");
            return response.getBody();
        } catch (Exception e) {
            log.error("Payout webhook config failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // Convenience method for a single webhook config
    public Map<String, Object> configurePayoutWebhook(
            String merchantKey,
            String eventType,
            String url,
            String intervalUnit,
            int intervalValue,
            int maxAttempts) {
        Map<String, Object> config = new HashMap<>();
        config.put("event_type", eventType);
        config.put("status", "enable");
        config.put("url", url);
        config.put("interval_unit", intervalUnit);
        config.put("interval_value", intervalValue);
        config.put("max_attempts", maxAttempts);

        return configurePayoutWebhook(merchantKey, List.of(config));
    }

    // ============================================================
    // 5. Get KYC Profile URL
    // ============================================================
    // GET /api/v1/merchants/{submerchant_id}/kyc/url/

    public Map<String, Object> getKycProfileUrl(String subMerchantId) {
        HttpHeaders headers = buildWireHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/merchants/" + subMerchantId + "/kyc/url/",
                    HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("KYC profile URL retrieval succeeded for: {}", subMerchantId);
            return response.getBody();
        } catch (Exception e) {
            log.error("KYC profile URL retrieval failed for {}: {}", subMerchantId, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ============================================================
    // 6. Get Sub-merchant Details by Key
    // ============================================================
    // GET /api/v1/merchants/{submerchant_key}

    public Map<String, Object> getSubMerchantByKey(String subMerchantKey) {
        HttpHeaders headers = buildWireHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl() + "/api/v1/merchants/" + subMerchantKey,
                    HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            log.info("Sub-merchant lookup by key succeeded for: {}", subMerchantKey);
            return response.getBody();
        } catch (Exception e) {
            log.error("Sub-merchant lookup by key failed for {}: {}", subMerchantKey, e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
