package com.khanabook.saas.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.billing.dto.CreateSubMerchantRequest;
import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class EasebuzzSubMerchantClient {

    @Value("${easebuzz.submerchant.base-url}")
    private String baseUrl;

    @Value("${easebuzz.submerchant.create-path}")
    private String createPath;

    @Value("${easebuzz.submerchant.update-path}")
    private String updatePath;

    @Value("${easebuzz.submerchant.kyc-access-path}")
    private String kycAccessPath;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubMerchantGatewayResponse createSubMerchant(
            String masterMerchantKey,
            String requestHash,
            CreateSubMerchantRequest request
    ) {
        MultiValueMap<String, String> form = baseForm(masterMerchantKey, requestHash);
        form.add("business_name", request.businessName());
        form.add("business_type", request.businessType());
        form.add("pan", request.pan());
        form.add("gstin", blankToEmpty(request.gstin()));
        form.add("business_address", request.businessAddress());
        form.add("contact_person", request.contactPerson());
        form.add("contact_email", request.contactEmail());
        form.add("contact_phone", request.contactPhone());
        form.add("bank_account_number", request.bankAccountNumber());
        form.add("bank_ifsc_code", request.bankIfscCode());
        form.add("bank_account_holder_name", request.bankAccountHolderName());
        form.add("bank_name", request.bankName());
        form.add("merchant_reference_id", String.valueOf(request.restaurantId()));
        return parse(postForm(resolveUrl(createPath), form));
    }

    public SubMerchantGatewayResponse updateSubMerchant(
            String masterMerchantKey,
            String requestHash,
            String subMerchantId,
            CreateSubMerchantRequest request
    ) {
        MultiValueMap<String, String> form = baseForm(masterMerchantKey, requestHash);
        form.add("sub_merchant_id", subMerchantId);
        form.add("business_name", request.businessName());
        form.add("business_type", request.businessType());
        form.add("pan", request.pan());
        form.add("gstin", blankToEmpty(request.gstin()));
        form.add("business_address", request.businessAddress());
        form.add("contact_person", request.contactPerson());
        form.add("contact_email", request.contactEmail());
        form.add("contact_phone", request.contactPhone());
        form.add("bank_account_number", request.bankAccountNumber());
        form.add("bank_ifsc_code", request.bankIfscCode());
        form.add("bank_account_holder_name", request.bankAccountHolderName());
        form.add("bank_name", request.bankName());
        form.add("merchant_reference_id", String.valueOf(request.restaurantId()));
        return parse(postForm(resolveUrl(updatePath), form));
    }

    public SubMerchantGatewayResponse generateKycAccessKey(
            String masterMerchantKey,
            String requestHash,
            String subMerchantId
    ) {
        MultiValueMap<String, String> form = baseForm(masterMerchantKey, requestHash);
        form.add("sub_merchant_id", subMerchantId);
        return parse(postForm(resolveUrl(kycAccessPath), form));
    }

    private MultiValueMap<String, String> baseForm(String key, String hash) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("key", key);
        form.add("hash", hash);
        return form;
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
            throw new IllegalStateException("Easebuzz sub-merchant request failed");
        }
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Easebuzz sub-merchant response", e);
        }
    }

    private SubMerchantGatewayResponse parse(JsonNode json) {
        JsonNode data = json.path("data");
        return SubMerchantGatewayResponse.builder()
                .rawPayload(json.toString())
                .apiStatus(firstNonBlank(stringValue(json, "status"), stringValue(data, "status")))
                .message(firstNonBlank(
                        stringValue(json, "message"),
                        stringValue(json, "error_desc"),
                        stringValue(data, "message"),
                        stringValue(data, "error_desc")))
                .subMerchantId(firstNonBlank(
                        stringValue(data, "sub_merchant_id"),
                        stringValue(data, "merchant_id"),
                        stringValue(json, "sub_merchant_id")))
                .kycAccessKey(firstNonBlank(
                        stringValue(data, "access_key"),
                        stringValue(data, "kyc_access_key"),
                        stringValue(json, "access_key")))
                .kycPortalUrl(firstNonBlank(
                        stringValue(data, "kyc_url"),
                        stringValue(data, "portal_url"),
                        stringValue(data, "kyc_portal_url"),
                        stringValue(json, "kyc_url")))
                .gatewayStatus(firstNonBlank(
                        stringValue(data, "sub_merchant_status"),
                        stringValue(data, "status"),
                        stringValue(json, "sub_merchant_status")))
                .build();
    }

    private String resolveUrl(String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
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

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Getter
    @Builder
    public static class SubMerchantGatewayResponse {
        private String apiStatus;
        private String message;
        private String subMerchantId;
        private String gatewayStatus;
        private String kycAccessKey;
        private String kycPortalUrl;
        private String rawPayload;
    }
}
