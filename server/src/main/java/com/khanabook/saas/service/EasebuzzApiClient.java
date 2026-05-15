package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EasebuzzApiClient {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzApiClient.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final EasebuzzProperties props;

    public Map createSubMerchant(Map<String, String> data) {
        String hash = generateHash(data.get("merchant_key"), data.get("business_name"));
        data.put("hash", hash);
        return post("/sub-merchant/create", data);
    }

    public Map updateSubMerchant(Map<String, String> data) {
        String hash = generateHash(props.getMerchantKey(), data.get("sub_merchant_id"));
        data.put("hash", hash);
        return post("/sub-merchant/update", data);
    }

    public Map generateKycAccessKey(Map<String, String> data) {
        String hash = generateHash(props.getMerchantKey(), data.get("sub_merchant_id"));
        data.put("hash", hash);
        return post("/sub-merchant/generate-kyc", data);
    }

    public Map getSubMerchantStatus(String subMerchantId) {
        Map<String, String> data = Map.of("merchant_key", props.getMerchantKey(), "sub_merchant_id", subMerchantId);
        String hash = generateHash(props.getMerchantKey(), subMerchantId);
        data.put("hash", hash);
        return post("/sub-merchant/status", data);
    }

    public Map initiatePayment(Map<String, String> data) {
        String txnid = data.get("txnid");
        String amount = data.get("amount");
        String productinfo = data.get("productinfo");
        String firstname = data.get("firstname");
        String email = data.get("email");
        String udf1 = data.getOrDefault("udf1", "");
        String udf2 = data.getOrDefault("udf2", "");
        String udf3 = data.getOrDefault("udf3", "");
        String udf4 = data.getOrDefault("udf4", "");
        String udf5 = data.getOrDefault("udf5", "");
        String hash = generatePaymentHash(txnid, amount, productinfo, firstname, email,
                udf1, udf2, udf3, udf4, udf5);
        data.put("hash", hash);
        data.put("return_url", props.getReturnUrl());
        data.put("notify_url", props.getNotifyUrl());
        return post("/payment/initiate", data);
    }

    public Map getTransactionStatus(String easebuzzId) {
        Map<String, String> data = Map.of(
            "merchant_key", props.getMerchantKey(),
            "easebuzz_id", easebuzzId
        );
        String hash = generateHash(props.getMerchantKey(), easebuzzId);
        data.put("hash", hash);
        return post("/transaction/status", data);
    }

    public Map initiateRefund(Map<String, String> data) {
        String txnid = data.get("txnid");
        String refundAmount = data.get("refund_amount");
        String hash = generateHash(props.getMerchantKey(), txnid + "|" + refundAmount);
        data.put("hash", hash);
        return post("/refund/initiate", data);
    }

    private Map post(String path, Map<String, String> data) {
        String url = props.getApiBaseUrl() + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        data.forEach(body::add);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.debug("Easebuzz API {} responded with status {}", path, response.getStatusCode());
            return response.getBody();
        } catch (Exception e) {
            log.error("Easebuzz API {} failed: {}", path, e.getMessage());
            return Map.of("status", "failure", "error", e.getMessage());
        }
    }

    private String generateHash(String... parts) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                sb.append(p == null ? "" : p).append("|");
            }
            sb.append(props.getSalt());
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) hash.append(String.format("%02x", b));
            return hash.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash generation failed", e);
        }
    }

    private String generatePaymentHash(String txnid, String amount, String productinfo,
                                        String firstname, String email, String udf1,
                                        String udf2, String udf3, String udf4, String udf5) {
        return generateHash(props.getMerchantKey(), txnid, amount, productinfo,
                firstname, email, udf1, udf2, udf3, udf4, udf5);
    }
}
