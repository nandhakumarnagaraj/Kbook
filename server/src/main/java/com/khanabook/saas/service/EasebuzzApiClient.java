package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
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

		// Official hash sequence:
		// key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||salt
		String hash = generateHash(props.getMerchantKey(), txnid, amount, productinfo, firstname, email, udf1, udf2,
				udf3, udf4, udf5, "", "", "", "", "");

		Map<String, String> params = new HashMap<>(data);
		params.put("key", props.getMerchantKey());
		params.put("hash", hash);
		params.put("surl", props.getReturnUrl());
		params.put("furl", props.getReturnUrl());

		Map<String, Object> raw = post(props.getPaymentBaseUrl() + "/payment/initiateLink", params);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
			String accessKey = (String) raw.get("data");
			result.put("status", "success");
			result.put("access_token", accessKey);
			result.put("payment_url", props.getPaymentBaseUrl() + "/pay/" + accessKey);
		} else {
			result.put("status", "failure");
			result.put("error",
					raw != null ? raw.getOrDefault("data", "Payment initiation failed") : "No response from gateway");
		}
		return result;
	}

	public Map<String, Object> getTransactionStatus(String txnid) {
		checkCredentials();
		Map<String, String> params = new HashMap<>();
		params.put("key", props.getMerchantKey());
		params.put("txnid", txnid);
		params.put("hash", generateHash(props.getMerchantKey(), txnid));

		Map<String, Object> raw = post(props.getPaymentBaseUrl() + "/transaction/v2.1/retrieve", params);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> msgList = (List<Map<String, Object>>) raw.get("msg");
			if (msgList != null && !msgList.isEmpty()) {
				Map<String, Object> txnData = msgList.get(0);
				result.put("status", txnData.getOrDefault("status", "failure"));
				result.put("easebuzz_id", txnData.getOrDefault("easepayid", txnData.get("easebuzz_id")));
			} else {
				result.put("status", "failure");
				result.put("error", "No transaction data found");
			}
		} else {
			result.put("status", "failure");
			result.put("error",
					raw != null ? raw.getOrDefault("error", "Transaction status check failed") : "Unknown error");
		}
		return result;
	}

	public Map<String, Object> initiateRefund(String easebuzzId, String merchantRefundId, String refundAmount) {
		checkCredentials();
		Map<String, String> params = new HashMap<>();
		params.put("key", props.getMerchantKey());
		params.put("merchant_refund_id", merchantRefundId);
		params.put("easebuzz_id", easebuzzId);
		params.put("refund_amount", refundAmount);

		// Refund API v2 hash: key|merchant_refund_id|easebuzz_id|refund_amount|salt
		String hash = generateHash(props.getMerchantKey(), merchantRefundId, easebuzzId, refundAmount);
		params.put("hash", hash);

		return post(props.getPaymentBaseUrl() + "/transaction/v2/refund", params);
	}

	public Map<String, Object> getRefundStatus(String txnid, String refundId) {
		checkCredentials();
		Map<String, String> params = new HashMap<>();
		params.put("key", props.getMerchantKey());
		params.put("txnid", txnid);
		params.put("refund_id", refundId);

		// Refund Status API v2 hash: key|txnid|refund_id|salt
		String hash = generateHash(props.getMerchantKey(), txnid, refundId);
		params.put("hash", hash);

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

		return postJson(props.getDashboardBaseUrl() + "/submerchant/v1/verify_otp/", body);
	}

	public Map<String, Object> resendOtp(String subMerchantId) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), subMerchantId);
		Map<String, Object> body = new HashMap<>();
		body.put("merchant_key", props.getMerchantKey());
		body.put("sub_merchant_id", subMerchantId);
		body.put("hash", hash);

		return postJson(props.getDashboardBaseUrl() + "/submerchant/v1/resend_otp/", body);
	}

	public Map<String, Object> cancelTransaction(String txnid, String amount) {
		checkCredentials();
		Map<String, String> params = new HashMap<>();
		params.put("key", props.getMerchantKey());
		params.put("txnid", txnid);
		params.put("amount", amount);

		// Cancel API hash: key|txnid|amount|salt
		String hash = generateHash(props.getMerchantKey(), txnid, amount);
		params.put("hash", hash);

		return post(props.getPaymentBaseUrl() + "/transaction/v1/cancel", params);
	}

	public Map<String, Object> initiatePayout(String merchantRequestId, String amount,
			Map<String, String> beneficiaryDetails) {
		checkCredentials();
		// Payout API v2 hash: key|merchant_request_id|amount|salt
		String hash = generateHash(props.getMerchantKey(), merchantRequestId, amount);

		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("amount", amount);
		body.put("hash", hash);
		body.putAll(beneficiaryDetails);

		return postJson(props.getDashboardBaseUrl() + "/payout/v2/transfer/", body);
	}

	public Map<String, Object> initiateOnDemandSettlement(String merchantRequestId, String amount) {
		checkCredentials();
		// Settlement API hash: key|merchant_request_id|amount|salt
		String hash = generateHash(props.getMerchantKey(), merchantRequestId, amount);

		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("amount", amount);
		body.put("hash", hash);

		return postJson(props.getDashboardBaseUrl() + "/settlement/v1/on_demand/", body);
	}

	public Map<String, Object> retrieveSettlements(String date) {
		checkCredentials();
		// Settlement Retrieve API hash: key|date|salt
		String hash = generateHash(props.getMerchantKey(), date);

		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("date", date);
		body.put("hash", hash);

		return postJson(props.getDashboardBaseUrl() + "/settlements/v1/retrieve/", body);
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

		// ERA recommendation: Include business details and MCC code
		Map<String, Object> businessDetails = new HashMap<>();
		// Standardization based on Easebuzz Sandbox error requirements
		String nature = "INDIVIDUAL/FREELANCERS";
		if ("SOLE_PROPRIETORSHIP".equalsIgnoreCase(businessType)) nature = "SOLE PROPRIETOR";
		else if ("PARTNERSHIP".equalsIgnoreCase(businessType)) nature = "PARTNERSHIP FIRM";
		else if ("PRIVATE_LIMITED".equalsIgnoreCase(businessType) || "PUBLIC_LIMITED".equalsIgnoreCase(businessType)) 
			nature = "PRIVATE LTD/PUBLIC LTD/OPC";

		businessDetails.put("sub_merchant_business_nature", nature);
		businessDetails.put("sub_merchant_business_type", businessType != null ? businessType : "SOLE PROPRIETOR");
		businessDetails.put("sub_merchant_business_name", subMerchantName);
		businessDetails.put("sub_merchant_business_address", businessAddress != null ? businessAddress : "123 Test Street");
		businessDetails.put("sub_merchant_state", "Karnataka"); 
		businessDetails.put("sub_merchant_mcc_code", "5812"); // Restaurants

		if (gst != null && !gst.isBlank()) businessDetails.put("sub_merchant_gstin", gst);
		if (pan != null && !pan.isBlank()) businessDetails.put("sub_merchant_pan_number", pan);

		Map<String, Object> body = new HashMap<>();
		body.put("merchant_details", merchantDetails);
		body.put("submerchant_details", submerchantDetails);
		body.put("business_details", businessDetails);

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create/", body);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		boolean apiStatus = toBool(statusObj);
		result.put("status", apiStatus);
		if (apiStatus && raw != null) {
			String subMerchantId = (String) raw.get("submerchant_id");
			Map<String, Object> submerchant = (Map<String, Object>) raw.get("submerchant");
			if (subMerchantId == null && submerchant != null) {
				subMerchantId = (String) submerchant.get("submerchant_id");
			}
			result.put("submerchant_id", subMerchantId);
		} else {
			result.put("error", raw != null ? raw.getOrDefault("error_desc", raw.get("error")) : "Unknown error");
		}
		return result;
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

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create/", body);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		boolean apiStatus = toBool(statusObj);
		result.put("status", apiStatus);
		if (apiStatus && raw != null) {
			result.put("submerchant_id", raw.get("submerchant_id"));
		} else {
			result.put("error", raw != null ? raw.getOrDefault("error_desc", raw.get("error")) : "Unknown error");
		}
		return result;
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

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/submerchant/v1/generate_kyc_access_key/",
				body);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
			result.put("status", "success");
			result.put("kyc_url", raw.getOrDefault("kyc_dashboard_url", raw.get("msg")));
		} else {
			result.put("status", "failure");
			result.put("error",
					raw != null ? raw.getOrDefault("msg", raw.getOrDefault("error", "KYC access key generation failed"))
							: "Unknown error");
		}
		return result;
	}

	public Map<String, Object> createSplitLabel(String nameOnBank, String bankName, String branchName, String ifscCode,
			String accountNumber, String label, String payoutPercentage) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), nameOnBank, bankName, branchName, ifscCode, accountNumber,
				label);

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

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/split/v1/create/", body);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
			result.put("status", "success");
			result.put("msg", raw.get("msg"));
		} else {
			result.put("status", "failure");
			result.put("error",
					raw != null ? raw.getOrDefault("msg", raw.getOrDefault("error", "Split label creation failed"))
							: "Unknown error");
		}
		return result;
	}

	public Map<String, Object> updateTransactionSplit(String merchantRequestId, String easebuzzId, String amount,
			String description, List<Map<String, String>> configuration) {
		checkCredentials();
		// Hash: key|merchant_request_id|easebuzz_id|salt
		String hash = generateHash(props.getMerchantKey(), merchantRequestId, easebuzzId);

		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("easebuzz_id", easebuzzId);
		body.put("amount", amount);
		body.put("description", description);
		body.put("configuration", configuration);
		body.put("hash", hash);

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/post-split/v1/create/", body);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
			result.put("status", "success");
			result.put("request_status", raw.get("request_status"));
			result.put("meta", raw.get("meta"));
		} else {
			result.put("status", "failure");
			result.put("error", raw != null ? raw.getOrDefault("error", "Split update failed") : "Unknown error");
		}
		return result;
	}

	public Map<String, Object> retrieveTransactionSplit(String merchantRequestId) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), merchantRequestId);

		Map<String, Object> body = new HashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_request_id", merchantRequestId);
		body.put("hash", hash);

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/post-split/v1/retrieve/", body);

		Map<String, Object> result = new HashMap<>();
		Object statusObj = raw != null ? raw.get("status") : null;
		if (toBool(statusObj) && raw != null) {
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

	private Map<String, Object> post(String url, Map<String, String> data) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		data.forEach(body::add);
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			log.debug("Easebuzz API responded {} status={}", url, response.getStatusCode());
			return response.getBody();
		} catch (Exception e) {
			log.error("Easebuzz API {} failed: {}", url, e.getMessage());
			Map<String, Object> error = new HashMap<>();
			error.put("status", "0");
			error.put("error", e.getMessage());
			return error;
		}
	}

	private Map<String, Object> postJson(String url, Map<String, Object> data) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
		try {
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.POST, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			log.debug("Easebuzz JSON API responded {} status={}", url, response.getStatusCode());
			return response.getBody();
		} catch (Exception e) {
			log.error("Easebuzz JSON API {} failed: {}", url, e.getMessage());
			Map<String, Object> error = new HashMap<>();
			error.put("status", false);
			error.put("error", e.getMessage());
			return error;
		}
	}

	private static boolean toBool(Object value) {
		if (value == null)
			return false;
		if (value instanceof Boolean)
			return (Boolean) value;
		if (value instanceof Number)
			return ((Number) value).intValue() == 1;
		String s = value.toString().trim();
		return "1".equals(s) || "true".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s);
	}

	private String generateHash(String... parts) {
		try {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < parts.length; i++) {
				sb.append(parts[i] == null ? "" : parts[i]);
				if (i < parts.length - 1)
					sb.append("|");
			}
			sb.append("|").append(props.getSalt());
			MessageDigest md = MessageDigest.getInstance("SHA-512");
			byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hash = new StringBuilder();
			for (byte b : digest)
				hash.append(String.format("%02x", b));
			return hash.toString();
		} catch (Exception e) {
			throw new RuntimeException("Hash generation failed", e);
		}
	}
}
