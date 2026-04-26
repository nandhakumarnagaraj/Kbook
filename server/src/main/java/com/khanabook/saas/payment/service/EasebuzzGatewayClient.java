package com.khanabook.saas.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class EasebuzzGatewayClient {

    @Value("${easebuzz.base-url}")
    private String prodEasebuzzBaseUrl;

    @Value("${easebuzz.success-url}")
    private String successUrl;

    @Value("${easebuzz.failure-url}")
    private String failureUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GatewaySession createSession(
            String environment,
            String merchantKey,
            String txnId,
            String amount,
            String productInfo,
            String firstName,
            String email,
            String phone,
            String requestHash
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", merchantKey);
        form.add("txnid", txnId);
        form.add("amount", amount);
        form.add("productinfo", productInfo);
        form.add("firstname", firstName);
        form.add("email", email);
        form.add("phone", phone);
        form.add("hash", requestHash);
        form.add("surl", successUrl);
        form.add("furl", failureUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String baseUrl = baseUrlFor(environment);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/payment/initiateLink",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Easebuzz session creation failed");
        }

        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            if (json.path("status").asInt(0) != 1) {
                throw new IllegalArgumentException(json.path("error_desc").asText("Easebuzz initiation failed"));
            }

            String accessKey = json.path("data").asText("");
            if (accessKey.isBlank()) {
                throw new IllegalStateException("Easebuzz returned no access key");
            }

            return GatewaySession.builder()
                    .accessKey(accessKey)
                    .checkoutUrl(baseUrl + "/pay/" + accessKey)
                    .build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Easebuzz response", e);
        }
    }

    private String baseUrlFor(String environment) {
        String raw = "TEST".equalsIgnoreCase(environment)
                ? "https://testpay.easebuzz.in"
                : prodEasebuzzBaseUrl;
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    @Getter
    @Builder
    public static class GatewaySession {
        private String accessKey;
        private String checkoutUrl;
    }
}
