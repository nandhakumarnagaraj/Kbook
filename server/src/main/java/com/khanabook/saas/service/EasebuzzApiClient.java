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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EasebuzzApiClient {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzApiClient.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final EasebuzzProperties props;

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
        String udf6 = data.getOrDefault("udf6", "");
        String udf7 = data.getOrDefault("udf7", "");
        String udf8 = data.getOrDefault("udf8", "");
        String udf9 = data.getOrDefault("udf9", "");
        String udf10 = data.getOrDefault("udf10", "");

        String hash = generateHash(
            props.getMerchantKey(), txnid, amount, productinfo, firstname, email,
            udf1, udf2, udf3, udf4, udf5, udf6, udf7, udf8, udf9, udf10
        );

        Map<String, String> params = new HashMap<>(data);
        params.put("key", props.getMerchantKey());
        params.put("hash", hash);
        params.put("surl", props.getReturnUrl());
        params.put("furl", props.getReturnUrl());

        Map raw = post(props.getPaymentBaseUrl() + "/payment/initiateLink", params);

        Map result = new HashMap<>();
        String rawStatus = (String) raw.getOrDefault("status", "0");
        if ("1".equals(rawStatus)) {
            String accessKey = (String) raw.get("data");
            result.put("status", "success");
            result.put("access_token", accessKey);
            result.put("payment_url", props.getPaymentBaseUrl() + "/pay/" + accessKey);
        } else {
            result.put("status", "failure");
            result.put("error", raw.getOrDefault("data", "Payment initiation failed"));
        }
        return result;
    }

    public Map getTransactionStatus(String txnid) {
        Map<String, String> params = new HashMap<>();
        params.put("key", props.getMerchantKey());
        params.put("txnid", txnid);
        params.put("hash", generateHash(props.getMerchantKey(), txnid));

        Map raw = post(props.getDashboardBaseUrl() + "/transaction/v2.1/retrieve", params);

        Map result = new HashMap<>();
        Object rawStatus = raw != null ? raw.get("status") : null;
        boolean apiOk = rawStatus instanceof Boolean && (Boolean) rawStatus;
        if (apiOk) {
            List<Map<String, Object>> msgList = (List<Map<String, Object>>) raw.get("msg");
            if (msgList != null && !msgList.isEmpty()) {
                Map<String, Object> txnData = msgList.get(0);
                result.put("status", txnData.getOrDefault("status", "failure"));
                result.put("easebuzz_id", txnData.get("easepayid"));
            } else {
                result.put("status", "failure");
                result.put("error", "No transaction data found");
            }
        } else {
            result.put("status", "failure");
            result.put("error", raw != null ? raw.getOrDefault("error", "Transaction status check failed") : "Unknown error");
        }
        return result;
    }

    public Map initiateRefund(String txnid, String amount, String refundAmount,
                               String email, String phone) {
        Map<String, String> params = new HashMap<>();
        params.put("key", props.getMerchantKey());
        params.put("txnid", txnid);
        params.put("amount", amount);
        params.put("refund_amount", refundAmount);
        params.put("email", email != null ? email : "");
        params.put("phone", phone != null ? phone : "");

        String hash = generateHash(
            props.getMerchantKey(), txnid, amount, refundAmount, params.get("email"), params.get("phone")
        );
        params.put("hash", hash);

        return post(props.getDashboardBaseUrl() + "/transaction/v1/refund", params);
    }

    public Map createSubMerchant(String subMerchantName, String email, String phone,
                                  String accountNumber, String ifsc, String bankName,
                                  String nameInBank, String branchName) {
        String hash = generateHash(
            props.getMerchantKey(),
            email != null ? email : "",
            phone != null ? phone : ""
        );

        Map<String, Object> merchantDetails = new HashMap<>();
        merchantDetails.put("merchant_key", props.getMerchantKey());
        merchantDetails.put("hash", hash);

        String password = generateRandomPassword();

        Map<String, Object> submerchantDetails = new HashMap<>();
        submerchantDetails.put("sub_merchant_name", subMerchantName);
        submerchantDetails.put("sub_merchant_email", email);
        submerchantDetails.put("sub_merchant_phone", phone);
        submerchantDetails.put("sub_merchant_name_in_bank", nameInBank);
        submerchantDetails.put("sub_merchant_account_number", accountNumber);
        submerchantDetails.put("sub_merchant_bank_name", bankName);
        submerchantDetails.put("sub_merchant_branch_name", branchName);
        submerchantDetails.put("sub_merchant_ifsc_code", ifsc);
        submerchantDetails.put("sub_merchant_password", password);
        submerchantDetails.put("sub_merchant_confirm_password", password);

        Map<String, Object> body = new HashMap<>();
        body.put("merchant_details", merchantDetails);
        body.put("submerchant_details", submerchantDetails);

        Map raw = postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create", body);

        Map result = new HashMap<>();
        Boolean apiStatus = raw != null ? (Boolean) raw.get("status") : false;
        result.put("status", Boolean.TRUE.equals(apiStatus));
        if (Boolean.TRUE.equals(apiStatus)) {
            String subMerchantId = (String) raw.get("submerchant_id");
            Map submerchant = (Map) raw.get("submerchant");
            if (subMerchantId == null && submerchant != null) {
                subMerchantId = (String) submerchant.get("submerchant_id");
            }
            result.put("submerchant_id", subMerchantId);
        } else {
            result.put("error", raw != null ? raw.getOrDefault("error_desc", raw.get("error")) : "Unknown error");
        }
        return result;
    }

    public Map updateSubMerchant(String subMerchantId, String subMerchantName, String email, String phone,
                                  String accountNumber, String ifsc, String bankName,
                                  String nameInBank, String branchName) {
        String hash = generateHash(
            props.getMerchantKey(),
            email != null ? email : "",
            phone != null ? phone : ""
        );

        Map<String, Object> merchantDetails = new HashMap<>();
        merchantDetails.put("merchant_key", props.getMerchantKey());
        merchantDetails.put("hash", hash);

        Map<String, Object> submerchantDetails = new HashMap<>();
        submerchantDetails.put("sub_merchant_name", subMerchantName);
        submerchantDetails.put("sub_merchant_email", email);
        submerchantDetails.put("sub_merchant_phone", phone);
        submerchantDetails.put("sub_merchant_name_in_bank", nameInBank);
        submerchantDetails.put("sub_merchant_account_number", accountNumber);
        submerchantDetails.put("sub_merchant_bank_name", bankName);
        submerchantDetails.put("sub_merchant_branch_name", branchName);
        submerchantDetails.put("sub_merchant_ifsc_code", ifsc);
        submerchantDetails.put("sub_merchant_id", subMerchantId);

        Map<String, Object> body = new HashMap<>();
        body.put("merchant_details", merchantDetails);
        body.put("submerchant_details", submerchantDetails);

        Map raw = postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create", body);

        Map result = new HashMap<>();
        Boolean apiStatus = raw != null ? (Boolean) raw.get("status") : false;
        result.put("status", Boolean.TRUE.equals(apiStatus));
        if (Boolean.TRUE.equals(apiStatus)) {
            result.put("submerchant_id", raw.get("submerchant_id"));
        } else {
            result.put("error", raw != null ? raw.getOrDefault("error_desc", raw.get("error")) : "Unknown error");
        }
        return result;
    }

    public Map generateKycAccessKey(String subMerchantId, String name, String email, String phone) {
        String hash = generateHash(
            props.getMerchantKey(), subMerchantId, name, email != null ? email : "", phone != null ? phone : ""
        );

        Map<String, Object> body = new HashMap<>();
        body.put("merchant_key", props.getMerchantKey());
        body.put("sub_merchant_id", subMerchantId);
        body.put("name", name);
        body.put("email", email != null ? email : "");
        body.put("phone", phone != null ? phone : "");
        body.put("hash", hash);

        Map raw = postJson(props.getDashboardBaseUrl() + "/submerchant/v1/generate_kyc_access_key", body);

        Map result = new HashMap<>();
        Object rawStatus = raw != null ? raw.get("status") : null;
        boolean apiOk = rawStatus instanceof Boolean && (Boolean) rawStatus;
        if (apiOk) {
            result.put("status", "success");
            result.put("kyc_url", raw.get("msg"));
        } else {
            result.put("status", "failure");
            result.put("error", raw != null ? raw.getOrDefault("msg", raw.getOrDefault("error", "KYC access key generation failed")) : "Unknown error");
        }
        return result;
    }

    public Map createSplitLabel(String nameOnBank, String bankName, String branchName,
                                 String ifscCode, String accountNumber, String label,
                                 String payoutPercentage) {
        String hash = generateHash(
            props.getMerchantKey(), nameOnBank, bankName, branchName, ifscCode, accountNumber, label
        );

        Map<String, Object> body = new HashMap<>();
        body.put("key", props.getMerchantKey());
        body.put("hash", hash);
        body.put("name_on_bank", nameOnBank);
        body.put("bank_name", bankName);
        body.put("branch_name", branchName);
        body.put("ifsc_code", ifscCode);
        body.put("account_number", accountNumber);
        body.put("label", label);
        if (payoutPercentage != null) {
            body.put("payout_percentage", payoutPercentage);
        }

        Map raw = postJson(props.getDashboardBaseUrl() + "/split/v1/create", body);

        Map result = new HashMap<>();
        Object rawStatus = raw != null ? raw.get("status") : null;
        boolean apiOk = rawStatus instanceof Boolean && (Boolean) rawStatus;
        if (apiOk) {
            result.put("status", "success");
            result.put("msg", raw.get("msg"));
        } else {
            result.put("status", "failure");
            result.put("error", raw != null ? raw.getOrDefault("msg", raw.getOrDefault("error", "Split label creation failed")) : "Unknown error");
        }
        return result;
    }

    public Map updateTransactionSplit(String merchantRequestId, String easebuzzId,
                                       String amount, String description,
                                       List<Map<String, String>> configuration) {
        String hash = generateHash(
            props.getMerchantKey(), merchantRequestId, easebuzzId
        );

        Map<String, Object> body = new HashMap<>();
        body.put("key", props.getMerchantKey());
        body.put("merchant_request_id", merchantRequestId);
        body.put("easebuzz_id", easebuzzId);
        body.put("amount", amount);
        body.put("description", description);
        body.put("configuration", configuration);
        body.put("hash", hash);

        Map raw = postJson(props.getDashboardBaseUrl() + "/post-split/v1/create/", body);

        Map result = new HashMap<>();
        Object rawStatus = raw != null ? raw.get("status") : null;
        boolean apiOk = rawStatus instanceof Boolean && (Boolean) rawStatus;
        if (apiOk) {
            result.put("status", "success");
            result.put("request_status", raw.get("request_status"));
            result.put("meta", raw.get("meta"));
        } else {
            result.put("status", "failure");
            result.put("error", raw != null ? raw.getOrDefault("error", "Split update failed") : "Unknown error");
        }
        return result;
    }

    public Map retrieveTransactionSplit(String merchantRequestId) {
        String hash = generateHash(props.getMerchantKey(), merchantRequestId);

        Map<String, Object> body = new HashMap<>();
        body.put("key", props.getMerchantKey());
        body.put("merchant_request_id", merchantRequestId);
        body.put("hash", hash);

        Map raw = postJson(props.getDashboardBaseUrl() + "/post-split/v1/retrieve/", body);

        Map result = new HashMap<>();
        String rawStatus = raw != null ? (String) raw.get("status") : null;
        if ("success".equals(rawStatus)) {
            result.put("status", "success");
            result.put("merchant_request_id", raw.get("merchant_request_id"));
            result.put("split_configuration", raw.get("split_configuration"));
        } else {
            result.put("status", "failure");
            result.put("error", raw != null ? raw.getOrDefault("error", "Split retrieve failed") : "Unknown error");
        }
        return result;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#";
        StringBuilder sb = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private Map post(String url, Map<String, String> data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        data.forEach(body::add);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.debug("Easebuzz API responded {} status={}", url, response.getStatusCode());
            return response.getBody();
        } catch (Exception e) {
            log.error("Easebuzz API {} failed: {}", url, e.getMessage());
            return Map.of("status", "failure", "error", e.getMessage());
        }
    }

    private Map postJson(String url, Map<String, Object> data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.debug("Easebuzz JSON API responded {} status={}", url, response.getStatusCode());
            return response.getBody();
        } catch (Exception e) {
            log.error("Easebuzz JSON API {} failed: {}", url, e.getMessage());
            return Map.of("status", "failure", "error", e.getMessage());
        }
    }

    private String generateHash(String... parts) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                sb.append(parts[i] == null ? "" : parts[i]);
                if (i < parts.length - 1) sb.append("|");
            }
            sb.append("|").append(props.getSalt());
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder();
            for (byte b : digest) hash.append(String.format("%02x", b));
            return hash.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash generation failed", e);
        }
    }
}
