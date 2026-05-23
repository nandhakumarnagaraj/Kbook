package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import com.khanabook.saas.exception.EasebuzzApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
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
public class EasebuzzApiClient {

	private static final Logger log = LoggerFactory.getLogger(EasebuzzApiClient.class);
	private final RestTemplate restTemplate;
	private final EasebuzzProperties props;

	public EasebuzzApiClient(EasebuzzProperties props) {
		this.props = props;
		org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10_000);
		factory.setReadTimeout(30_000);
		this.restTemplate = new RestTemplate(factory);
	}

	private void checkCredentials() {
		if (props.getMerchantKey() == null || props.getMerchantKey().isBlank()) {
			log.error("Easebuzz Merchant Key is missing! API calls will fail. Ensure 'sandbox' profile is active.");
		}
	}

	public Map<String, Object> initiatePayment(Map<String, String> data) {
		checkCredentials();
		
		// 1. Extract and Clean Parameters
		String txnid = data.get("txnid");
		if (txnid.length() > 20) txnid = txnid.substring(0, 20);
		
		String amount = data.get("amount");
		String productinfo = data.get("productinfo").replaceAll("[^a-zA-Z0-9]", ""); 
		String firstname = data.get("firstname").replaceAll("[^a-zA-Z0-9]", "");
		String email = data.get("email");
		String phone = data.get("phone");
		String subMerchantId = data.get("sub_merchant_id");
		
		// 2. Ensure all 10 UDFs are present
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

		// 3. Generate Hash: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|udf6|udf7|udf8|udf9|udf10|salt
		// Per official Easebuzz docs: all 10 UDF fields (5+5) are required in the hash sequence
		String hash = generateHash(
				props.getMerchantKey(), txnid, amount, productinfo, firstname, email,
				udf1, udf2, udf3, udf4, udf5, udf6, udf7, udf8, udf9, udf10
		);

		// 4. Prepare Minimal POST Parameters
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.set("key", props.getMerchantKey());
		params.set("txnid", txnid);
		params.set("amount", amount);
		params.set("productinfo", productinfo);
		params.set("firstname", firstname);
		params.set("email", email);
		params.set("phone", phone);
		
		String surl = data.getOrDefault("surl", props.getReturnUrl());
		String furl = data.getOrDefault("furl", props.getReturnUrl());
		params.set("surl", surl);
		params.set("furl", furl);
		params.set("notify_url", props.getNotifyUrl());
		
		params.set("hash", hash);
		
		// Send UDF fields so Easebuzz can validate the hash correctly
		// (all 10 UDFs are used in the hash sequence, so they must be sent as params)
		params.set("udf1", udf1);
		params.set("udf2", udf2);
		params.set("udf3", udf3);
		params.set("udf4", udf4);
		params.set("udf5", udf5);
		params.set("udf6", udf6);
		params.set("udf7", udf7);
		params.set("udf8", udf8);
		params.set("udf9", udf9);
		params.set("udf10", udf10);
		
		if (subMerchantId != null && !subMerchantId.isBlank()) {
			params.set("sub_merchant_id", subMerchantId);
		}

		// Optional but recommended fields for Sandbox
		if (data.containsKey("address1")) params.add("address1", data.get("address1"));
		if (data.containsKey("city")) params.add("city", data.get("city"));
		if (data.containsKey("state")) params.add("state", data.get("state"));
		if (data.containsKey("zipcode")) params.add("zipcode", data.get("zipcode"));
		if (data.containsKey("country")) params.add("country", data.get("country"));

		if ("true".equalsIgnoreCase(data.get("upi_qr"))) {
			params.add("payment_mode", "UPI");
			params.add("upi_qr", "true");
		}

		Map<String, Object> raw = post(props.getPaymentBaseUrl() + "/payment/initiateLink", params);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
			String accessKey = (String) raw.get("data");
			log.info("Easebuzz access key generated txnid={} accessKeyLength={} payMode={}",
					txnid, accessKey != null ? accessKey.length() : 0, props.getPayMode());
			result.put("status", "success");
			result.put("access_token", accessKey);
			result.put("payment_url", props.getPaymentBaseUrl() + "/pay/" + accessKey);
		} else {
			result.put("status", "failure");
			result.put("error", raw != null ? raw.getOrDefault("data", raw.get("error")) : "No response");
		}
		return result;
	}

	public Map<String, Object> getTransactionStatus(String txnid) {
		checkCredentials();
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("key", props.getMerchantKey());
		params.add("txnid", txnid);
		params.add("hash", generateHash(props.getMerchantKey(), txnid));

		Map<String, Object> raw = post(props.getDashboardBaseUrl() + "/transaction/v2.1/retrieve", params);
		return raw;
	}

	public Map<String, Object> initiateRefund(String txnid, String amount) {
		checkCredentials();
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("key", props.getMerchantKey());
		params.add("txnid", txnid);
		params.add("amount", amount);
		// Refund API v2 hash: key|txnid|amount|salt
		params.add("hash", generateHash(props.getMerchantKey(), txnid, amount));

		return post(props.getPaymentBaseUrl() + "/transaction/v2/refund", params);
	}

	public Map<String, Object> getRefundStatus(String txnid, String refundId) {
		checkCredentials();
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("key", props.getMerchantKey());
		params.add("txnid", txnid);
		params.add("refund_id", refundId);
		params.add("hash", generateHash(props.getMerchantKey(), txnid, refundId));

		return post(props.getPaymentBaseUrl() + "/transaction/v2/refund_status", params);
	}

	public Map<String, Object> verifyOtp(String subMerchantId, String otp) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), subMerchantId, otp);
		Map<String, Object> body = new HashMap<>();
		body.put("merchant_key", props.getMerchantKey());
		body.put("sub_merchant_id", subMerchantId);
		body.put("otp", otp);
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/submerchant/v1/verify_otp", body);
	}

	public Map<String, Object> resendOtp(String subMerchantId) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), subMerchantId);
		Map<String, Object> body = new HashMap<>();
		body.put("merchant_key", props.getMerchantKey());
		body.put("sub_merchant_id", subMerchantId);
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/submerchant/v1/resend_otp", body);
	}

	public Map<String, Object> cancelTransaction(String txnid, String amount) {
		checkCredentials();
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("key", props.getMerchantKey());
		params.add("txnid", txnid);
		params.add("amount", amount);
		params.add("hash", generateHash(props.getMerchantKey(), txnid, amount));
		return post(props.getPaymentBaseUrl() + "/transaction/v1/cancel", params);
	}

	public Map<String, Object> initiatePayout(String merchantRequestId, String amount, Map<String, String> beneficiaryDetails) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), merchantRequestId, amount);
		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("amount", amount);
		body.put("hash", hash);
		body.putAll(beneficiaryDetails);
		return postJson(props.getDashboardBaseUrl() + "/payout/v2/transfer", body);
	}

	public Map<String, Object> initiateOnDemandSettlement(String merchantRequestId, String amount) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), merchantRequestId, amount);
		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("amount", amount);
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/settlement/v1/on_demand", body);
	}

	public Map<String, Object> retrieveSettlements(String date) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), date);
		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("date", date);
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/settlements/v1/retrieve", body);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> createSubMerchant(String subMerchantName, String email, String phone,
			String accountNumber, String ifsc, String bankName, String nameInBank, String branchName,
			String businessType, String pan, String gst, String businessAddress) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), email != null ? email : "", phone != null ? phone : "");
		Map<String, Object> merchantDetails = new HashMap<>();
		merchantDetails.put("merchant_key", props.getMerchantKey());
		merchantDetails.put("hash", hash);

		String sanitizedName = sanitizeName(subMerchantName);
		String password = generateRandomPassword();
		Map<String, Object> submerchantDetails = new HashMap<>();
		submerchantDetails.put("sub_merchant_name", sanitizedName);
		submerchantDetails.put("sub_merchant_email", email);
		submerchantDetails.put("sub_merchant_phone", phone);
		submerchantDetails.put("sub_merchant_name_in_bank", nameInBank);
		submerchantDetails.put("sub_merchant_account_number", accountNumber);
		submerchantDetails.put("sub_merchant_bank_name", bankName);
		submerchantDetails.put("sub_merchant_branch_name", branchName);
		submerchantDetails.put("sub_merchant_ifsc_code", ifsc);
		submerchantDetails.put("sub_merchant_password", password);
		submerchantDetails.put("sub_merchant_confirm_password", password);

		Map<String, Object> businessDetails = new HashMap<>();
		String nature = "INDIVIDUAL/FREELANCERS";
		if ("SOLE_PROPRIETORSHIP".equalsIgnoreCase(businessType)) nature = "SOLE PROPRIETOR";
		else if ("PARTNERSHIP".equalsIgnoreCase(businessType)) nature = "PARTNERSHIP FIRM";
		else if ("PRIVATE_LIMITED".equalsIgnoreCase(businessType) || "PUBLIC_LIMITED".equalsIgnoreCase(businessType)) 
			nature = "PRIVATE LTD/PUBLIC LTD/OPC";

		businessDetails.put("sub_merchant_business_nature", nature);
		businessDetails.put("sub_merchant_business_type", businessType != null ? businessType : "SOLE PROPRIETOR");
		businessDetails.put("sub_merchant_business_name", sanitizedName);
		businessDetails.put("sub_merchant_business_address", businessAddress != null ? businessAddress : "123 Test Street");
		businessDetails.put("sub_merchant_state", "Karnataka"); 
		businessDetails.put("sub_merchant_mcc_code", "5812");

		if (gst != null && !gst.isBlank()) businessDetails.put("sub_merchant_gstin", gst);
		if (pan != null && !pan.isBlank()) businessDetails.put("sub_merchant_pan_number", pan);

		Map<String, Object> body = new HashMap<>();
		body.put("merchant_details", merchantDetails);
		body.put("submerchant_details", submerchantDetails);
		body.put("business_details", businessDetails);
		return postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create/", body);
	}

	public Map<String, Object> updateSubMerchant(String subMerchantId, String subMerchantName, String email,
			String phone, String accountNumber, String ifsc, String bankName, String nameInBank, String branchName) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), email != null ? email : "", phone != null ? phone : "");
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
		return postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create/", body);
	}

	public Map<String, Object> generateKycAccessKey(String subMerchantId, String name, String email, String phone) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), subMerchantId, name, email != null ? email : "",
				phone != null ? phone : "");
		Map<String, Object> body = new HashMap<>();
		body.put("merchant_key", props.getMerchantKey());
		body.put("sub_merchant_id", subMerchantId);
		body.put("name", name);
		body.put("email", email != null ? email : "");
		body.put("phone", phone != null ? phone : "");
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/submerchant/v1/generate_kyc_access_key", body);
	}

	public Map<String, Object> createSplitLabel(String nameOnBank, String bankName, String branchName, String ifscCode,
			String accountNumber, String label, String payoutPercentage) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), nameOnBank, bankName, branchName, ifscCode, accountNumber, label);
		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("hash", hash);
		body.put("name_on_bank", nameOnBank);
		body.put("bank_name", bankName);
		body.put("branch_name", branchName);
		body.put("ifsc_code", ifscCode);
		body.put("account_number", accountNumber);
		body.put("label", label);
		if (payoutPercentage != null) body.put("payout_percentage", payoutPercentage);
		return postJson(props.getDashboardBaseUrl() + "/split/v1/create", body);
	}

	public Map<String, Object> updateTransactionSplit(String merchantRequestId, String easebuzzId, String amount,
			String description, List<Map<String, String>> configuration) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), merchantRequestId, easebuzzId);
		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("easebuzz_id", easebuzzId);
		body.put("amount", amount);
		body.put("description", description);
		body.put("configuration", configuration);
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/post-split/v1/create/", body);
	}

	public Map<String, Object> retrieveTransactionSplit(String merchantRequestId) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), merchantRequestId);
		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("hash", hash);
		return postJson(props.getDashboardBaseUrl() + "/post-split/v1/retrieve/", body);
	}

	private String generateRandomPassword() {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#";
		StringBuilder sb = new StringBuilder();
		java.security.SecureRandom random = new java.security.SecureRandom();
		for (int i = 0; i < 12; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
		return sb.toString();
	}

	private Map<String, Object> post(String url, MultiValueMap<String, String> bodyMap) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		
		// Manually build the query string to avoid [val] formatting issues
		StringBuilder sb = new StringBuilder();
		bodyMap.forEach((key, values) -> {
			if (!values.isEmpty()) {
				if (sb.length() > 0) sb.append("&");
				sb.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
				sb.append("=");
				sb.append(java.net.URLEncoder.encode(values.get(0), java.nio.charset.StandardCharsets.UTF_8));
			}
		});
		
		HttpEntity<String> request = new HttpEntity<>(sb.toString(), headers);
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
					new ParameterizedTypeReference<Map<String, Object>>() {});
			log.debug("Easebuzz API {} success: {}", url, response.getStatusCode());
			return response.getBody();
		} catch (org.springframework.web.client.HttpStatusCodeException e) {
			log.error("Easebuzz API {} error {}: {}", url, e.getStatusCode(), e.getResponseBodyAsString());
			return Map.of("status", "failure", "error", e.getResponseBodyAsString());
		} catch (Exception e) {
			log.error("Easebuzz API {} failed: {}", url, e.getMessage());
			return Map.of("status", "failure", "error", e.getMessage());
		}
	}

	private Map<String, Object> postJson(String url, Map<String, Object> data) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
					new ParameterizedTypeReference<Map<String, Object>>() {});
			return response.getBody();
		} catch (org.springframework.web.client.HttpStatusCodeException e) {
			log.error("Easebuzz JSON API {} error {}: {}", url, e.getStatusCode(), e.getResponseBodyAsString());
			return Map.of("status", false, "error", e.getResponseBodyAsString());
		} catch (Exception e) {
			log.error("Easebuzz JSON API {} failed: {}", url, e.getMessage());
			return Map.of("status", false, "error", e.getMessage());
		}
	}

	public static String sanitizeName(String name) {
		if (name == null) return null;
		return name.replace("'", "").replace("\"", "").replace("\\", "");
	}

	public static boolean toBool(Object value) {
		if (value == null) return false;
		if (value instanceof Boolean) return (Boolean) value;
		if (value instanceof Number) return ((Number) value).intValue() == 1;
		String s = value.toString().trim();
		return "1".equals(s) || "true".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s);
	}

	private String generateHash(String... parts) {
		try {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < parts.length; i++) {
				sb.append(parts[i] == null ? "" : parts[i]);
				if (i < parts.length - 1) sb.append("|");
			}
			sb.append("|").append(props.getSalt());
			String raw = sb.toString();
			if (log.isTraceEnabled()) {
				log.trace("Hashing Sequence: key|...|...|salt (length={})", raw.length());
			}
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
			StringBuilder hash = new StringBuilder();
			for (byte b : digest) hash.append(String.format("%02x", b));
			return hash.toString();
		} catch (Exception e) {
			throw new EasebuzzApiException("Hash generation failed", e);
		}
	}
}
