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

    public RefundInitiation initiateRefund(
            String environment,
            String merchantKey,
            String merchantRefundId,
            String easebuzzId,
            String refundAmount,
            String requestHash
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", merchantKey);
        form.add("merchant_refund_id", merchantRefundId);
        form.add("easebuzz_id", easebuzzId);
        form.add("refund_amount", refundAmount);
        form.add("hash", requestHash);

        JsonNode json = postForm(dashboardBaseUrlFor(environment) + "/transaction/v2/refund", form);
        JsonNode data = json.path("data");
        return RefundInitiation.builder()
                .rawPayload(json.toString())
                .apiStatus(stringValue(json, "status"))
                .apiAccepted("1".equals(stringValue(json, "status")))
                .refundStatus(firstNonBlank(
                        stringValue(data, "refund_status"),
                        stringValue(json, "refund_status")))
                .message(firstNonBlank(
                        stringValue(json, "message"),
                        stringValue(data, "message"),
                        stringValue(data, "reason"),
                        stringValue(data, "error_desc"),
                        stringValue(json, "data")))
                .refundId(firstNonBlank(
                        stringValue(data, "refund_id"),
                        stringValue(data, "request_id"),
                        stringValue(json, "refund_id")))
                .build();
    }

    public RefundLookup fetchRefundStatus(
            String environment,
            String merchantKey,
            String easebuzzId,
            String requestHash
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", merchantKey);
        form.add("easebuzz_id", easebuzzId);
        form.add("hash", requestHash);

        JsonNode json = postForm(dashboardBaseUrlFor(environment) + "/refund/v1/retrieve", form);
        JsonNode data = json.path("data");
        JsonNode refunds = data.path("refunds");
        JsonNode refundNode = refunds.isArray() && refunds.size() > 0 ? refunds.get(0) : data;

        return RefundLookup.builder()
                .rawPayload(json.toString())
                .rawStatus(firstNonBlank(
                        stringValue(refundNode, "refund_status"),
                        stringValue(data, "refund_status"),
                        stringValue(json, "refund_status"),
                        stringValue(json, "status")))
                .refundId(firstNonBlank(
                        stringValue(refundNode, "refund_id"),
                        stringValue(data, "refund_id"),
                        stringValue(json, "refund_id")))
                .arnNumber(firstNonBlank(
                        stringValue(refundNode, "arn_number"),
                        stringValue(data, "arn_number"),
                        stringValue(json, "arn_number")))
                .message(firstNonBlank(
                        stringValue(refundNode, "chargeback_description"),
                        stringValue(data, "message"),
                        stringValue(json, "message")))
                .build();
    }

    private String baseUrlFor(String environment) {
        String raw = "TEST".equalsIgnoreCase(environment)
                ? "https://testpay.easebuzz.in"
                : prodEasebuzzBaseUrl;
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private String dashboardBaseUrlFor(String environment) {
        String raw = "TEST".equalsIgnoreCase(environment)
                ? "https://testdashboard.easebuzz.in"
                : "https://dashboard.easebuzz.in";
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Easebuzz request failed");
        }

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Easebuzz response", e);
        }
    }

    private String stringValue(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    @Getter
    @Builder
    public static class GatewaySession {
        private String accessKey;
        private String checkoutUrl;
    }

    @Getter
    @Builder
    public static class RefundInitiation {
        private String rawPayload;
        private String apiStatus;
        private boolean apiAccepted;
        private String refundStatus;
        private String refundId;
        private String message;
    }

    @Getter
    @Builder
    public static class RefundLookup {
        private String rawPayload;
        private String rawStatus;
        private String refundId;
        private String arnNumber;
        private String message;
    }
}
