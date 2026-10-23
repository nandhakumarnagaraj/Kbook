package com.khanabook.saas.feature.payments.service;

import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import com.khanabook.saas.core.exception.EasebuzzApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EasebuzzApiClient {

	private static final Logger log = LoggerFactory.getLogger(EasebuzzApiClient.class);

	//
	// Retry policy for calls that are safe to repeat.
	//
	// Only read-only lookups and operations that carry a caller-generated idempotency
	// key (merchant_refund_id, merchant_request_id) are retried. Creating a sub-merchant,
	// uploading KYC, or opening a new payment link is *not* retried: a transport timeout
	// there may still mean the gateway acted, and a blind repeat creates a duplicate
	// entity rather than a duplicate answer. Three attempts with a jittered exponential
	// backoff keeps the worst case (3 x 30s read timeout + ~1.1s of sleep) inside the
	// client's own request budget.
	private static final int MAX_ATTEMPTS = 3;
	private static final long BASE_BACKOFF_MS = 250L;
	private static final long MAX_BACKOFF_MS = 1_000L;
	private static final int RETRYABLE_STATUS_TOO_MANY_REQUESTS = 429;
	/** Easebuzz documents a 20-row default and a 500-row maximum per settlements page. */
	private static final int SETTLEMENT_PAGE_SIZE = 500;
	/** Bound so a gateway bug in the {@code next} cursor cannot hang the reconciliation job. */
	private static final int SETTLEMENT_MAX_PAGES = 40;

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
		// Easebuzz allows up to 40 chars for txnid (regex: ^[a-zA-Z0-9_|\\-\\/]{1,40}$).
		// Truncating is the only option that still collects the payment, but it silently
		// splits the gateway id from the id we persist, so it must never be quiet.
		if (txnid.length() > 40) {
			log.warn("Truncating txnid to 40 characters for the gateway (length {})", txnid.length());
			txnid = txnid.substring(0, 40);
		}
		
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
			// The access key is 64 lowercase hex characters (the official integration kit
			// validates the same shape). Anything else is a "success" carrying a payload no
			// checkout page can use, so it must not become a payment URL.
			if (accessKey == null || !accessKey.matches("^[a-f0-9]{64}$")) {
				log.error("Easebuzz returned an unusable access key txnid={} length={}",
						txnid, accessKey != null ? accessKey.length() : 0);
				result.put("status", "failure");
				result.put("error", "Easebuzz returned an invalid access key");
				return result;
			}
			result.put("status", "success");
			result.put("access_token", accessKey);
			result.put("payment_url", props.getPaymentBaseUrl() + "/pay/" + accessKey);
		} else {
			result.put("status", "failure");
			result.put("error", raw != null ? raw.getOrDefault("error_desc", raw.getOrDefault("data", raw.get("error"))) : "No response");
		}
		return result;
	}

	public Map<String, Object> getTransactionStatus(String txnid) {
		checkCredentials();
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("key", props.getMerchantKey());
		params.add("txnid", txnid);
		params.add("hash", generateHash(props.getMerchantKey(), txnid));

		// Read-only lookup: safe to repeat. See postFormWithFallback for why both routes are tried.
		return postFormWithFallback(java.util.List.of(
				props.getDashboardBaseUrl() + "/transaction/v2.1/retrieve",
				props.getDashboardBaseUrl() + "/transaction/v2/retrieve"), params, true);
	}

	/**
	 * Initiate a refund via Easebuzz Refund API v2.
	 *
	 * Official docs hash sequence:
	 *   key|merchant_refund_id|easebuzz_id|refund_amount|salt
	 *
	 * @param merchantRefundId Unique refund reference (e.g. REF{billId}_{timestamp})
	 * @param easebuzzId       Easebuzz transaction ID from the original payment
	 * @param refundAmount     Amount to refund
	 */
	public Map<String, Object> initiateRefund(String merchantRefundId, String easebuzzId, String refundAmount) {
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), merchantRefundId, easebuzzId, refundAmount);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.set("key", props.getMerchantKey());
		params.set("merchant_refund_id", merchantRefundId);
		params.set("easebuzz_id", easebuzzId);
		params.set("refund_amount", refundAmount);
		params.set("hash", hash);

		// Form-encoded is the first-party content type for this route: the official kit posts
		// it that way (easebuzz-lib/refund.php). JSON acceptance was never documented.
		// Idempotent at the gateway via merchant_refund_id: a retry cannot double-refund.
		return post(props.getDashboardBaseUrl() + "/transaction/v2/refund", params, true);
	}

	/**
	 * Refund status contract: easebuzz_id lookup, optional merchant_refund_id, and the
	 * {@code key|easebuzz_id|salt} hash — all three match the official integration kit
	 * (easebuzz-lib/refund_status.php), which also posts the request form-encoded. That is the
	 * content type used here; JSON acceptance was an assumption with no first-party source.
	 */
	public Map<String, Object> getRefundStatus(String easebuzzId, String merchantRefundId) {
		if (easebuzzId == null || easebuzzId.isBlank()) {
			throw new IllegalArgumentException("Easebuzz payment ID is required for refund status");
		}
		checkCredentials();
		String hash = generateHash(props.getMerchantKey(), easebuzzId);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.set("key", props.getMerchantKey());
		params.set("easebuzz_id", easebuzzId);
		if (merchantRefundId != null && !merchantRefundId.isBlank()) {
			params.set("merchant_refund_id", merchantRefundId);
		}
		params.set("hash", hash);

		return post(props.getDashboardBaseUrl() + "/refund/v1/retrieve", params, true);
	}

	@Deprecated
	public Map<String, Object> cancelTransaction(String txnid, String amount) {
		// ERA confirmed: /transaction/v1/cancel does NOT exist. Unpaid txnids auto-expire in 15 min.
		return Map.of("status", "failure", "error", "Cancel API not supported by Easebuzz. Transactions auto-expire in 15 minutes.");
	}

	/**
	 * On-demand settlement: POST {dashboard}/settlements/v1/ondemand/initiate/.
	 *
	 * <p>The documented contract is <b>batch-shaped and has no top-level hash</b>: every entry of
	 * {@code transactions} carries its own
	 * sha512(key|easebuzz_id|merchant_txn_id|amount|salt). A single transaction still has to be
	 * wrapped in a one-element batch, and {@code merchant_batch_id} is the gateway's idempotency key.
	 */
	public Map<String, Object> initiateOnDemandSettlement(String merchantBatchId, String description,
			List<Map<String, String>> transactions) {
		checkCredentials();
		if (merchantBatchId == null || merchantBatchId.isBlank())
			throw new IllegalArgumentException("merchant_batch_id is required for an on-demand settlement");
		if (transactions == null || transactions.isEmpty())
			throw new IllegalArgumentException("At least one transaction is required for an on-demand settlement");

		List<Map<String, Object>> batch = new ArrayList<>();
		for (Map<String, String> txn : transactions) {
			String easebuzzId = txn.get("easebuzz_id");
			String merchantTxnId = txn.get("merchant_txn_id");
			String amount = txn.get("amount");
			if (easebuzzId == null || easebuzzId.isBlank()
					|| merchantTxnId == null || merchantTxnId.isBlank()
					|| amount == null || amount.isBlank()) {
				throw new IllegalArgumentException(
						"Each settlement transaction needs easebuzz_id, merchant_txn_id and amount");
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("easebuzz_id", easebuzzId);
			row.put("merchant_txn_id", merchantTxnId);
			row.put("amount", amount);
			row.put("hash", generateHash(props.getMerchantKey(), easebuzzId, merchantTxnId, amount));
			batch.add(row);
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("key", props.getMerchantKey());
		// Registered email of the parent merchant, mandatory on this call but not part of any hash.
		body.put("merchant_email", props.getMerchantEmail());
		body.put("merchant_batch_id", merchantBatchId);
		if (description != null && !description.isBlank()) {
			body.put("description", description);
		}
		body.put("transactions", batch);

		// Repeating a POST with the same merchant_batch_id cannot settle twice.
		return postJson(props.getDashboardBaseUrl() + "/settlements/v1/ondemand/initiate/", body, true);
	}

	private String normalizeDate(String date) {
		if (date == null) return "";
		String trimmed = date.trim();
		try {
			if (trimmed.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
				java.time.LocalDate d = java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d"));
				return d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
			} else if (trimmed.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
				java.time.LocalDate d = java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"));
				return d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
			} else if (trimmed.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
				java.time.LocalDate d = java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ofPattern("d-M-yyyy"));
				return d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
			}
		} catch (Exception ignored) {}
		return trimmed;
	}

	public Map<String, Object> retrieveSettlements(String date) {
		return retrieveSettlements(date, date, null);
	}

	/**
	 * Retrieves every settlements page for the range.
	 *
	 * <p>Easebuzz documents 20 rows per page by default (500 maximum) and hands back the cursor in
	 * {@code next}, which must be echoed in {@code page} to continue. Returning only the first page
	 * would silently hide transactions from reconciliation, which reads a missing row as "the
	 * webhook never arrived" — a busy restaurant settles more than one page a day.
	 */
	public Map<String, Object> retrieveSettlements(String startDate, String endDate, String subMerchantId) {
		checkCredentials();
		String normStart = normalizeDate(startDate);
		String normEnd = normalizeDate(endDate);
		String hash = generateHash(props.getMerchantKey(), normStart, normEnd);

		Map<String, Object> firstPage = requestSettlementPage(hash, normStart, normEnd, subMerchantId, null);
		if (!toBool(firstPage.get("status"))) {
			return firstPage;
		}

		List<Object> rows = new ArrayList<>();
		Object firstData = firstPage.get("data");
		if (firstData instanceof List<?> list) {
			rows.addAll(list);
		}

		Object next = firstPage.get("next");
		int pages = 1;
		while (next != null && !next.toString().isBlank() && pages < SETTLEMENT_MAX_PAGES) {
			Map<String, Object> page = requestSettlementPage(hash, normStart, normEnd, subMerchantId, next.toString());
			if (!toBool(page.get("status"))) {
				log.warn("Settlements page {} failed for {}..{}: {}", next, normStart, normEnd, page.get("error"));
				break;
			}
			Object pageData = page.get("data");
			if (pageData instanceof List<?> list) {
				rows.addAll(list);
			}
			next = page.get("next");
			pages++;
		}

		boolean truncated = next != null && !next.toString().isBlank();
		if (truncated) {
			// Fail loudly: a partially fetched period must not be reported as fully reconciled.
			log.error("Settlements pagination stopped after {} pages for {}..{} with next={}",
					pages, normStart, normEnd, next);
		}

		Map<String, Object> merged = new LinkedHashMap<>(firstPage);
		merged.put("data", rows);
		merged.put("pagesFetched", pages);
		merged.put("truncated", truncated);
		return merged;
	}

	private Map<String, Object> requestSettlementPage(String hash, String normStart, String normEnd,
			String subMerchantId, String page) {
		Map<String, Object> body = new HashMap<>();
		body.put("merchant_key", props.getMerchantKey());
		body.put("key", props.getMerchantKey());
		if (props.getMerchantEmail() != null && !props.getMerchantEmail().isBlank()) {
			body.put("merchant_email", props.getMerchantEmail());
		}
		body.put("hash", hash);

		Map<String, String> payoutDate = new HashMap<>();
		payoutDate.put("start_date", normStart);
		payoutDate.put("end_date", normEnd);
		body.put("payout_date", payoutDate);
		// Also provide top-level date fields for backward compatibility with older gateway proxies
		body.put("date", normStart);

		if (subMerchantId != null && !subMerchantId.isBlank()) {
			body.put("submerchant_id", subMerchantId);
		}
		body.put("page_size", SETTLEMENT_PAGE_SIZE);
		if (page != null) {
			body.put("page", page);
		}

		return postJson(props.getDashboardBaseUrl() + "/settlements/v1/retrieve", body, true);
	}

	public Map<String, Object> createPaymentLink(Map<String, String> data) {
		checkCredentials();

		// EasyCollect link creation: POST {dashboard}/easycollect/v1/create, JSON body.
		// The path is not /easy_collect — that is the hosted payment-page prefix on the checkout
		// host (pay.easebuzz.in/easy_collect/<token>), and posting an API call there returns 405.
		// Hash: key|merchant_txn|name|email|phone|amount|udf1|udf2|udf3|udf4|udf5|message|salt
		String merchantTxn = data.get("merchant_txn");
		String name = data.get("name");
		String email = data.get("email");
		String phone = data.get("phone");
		String amount = data.get("amount");
		String message = orEmpty(data.get("message"));
		String udf1 = data.getOrDefault("udf1", "");
		String udf2 = data.getOrDefault("udf2", "");
		String udf3 = data.getOrDefault("udf3", "");
		String udf4 = data.getOrDefault("udf4", "");
		String udf5 = data.getOrDefault("udf5", "");

		String hash = generateHash(
				props.getMerchantKey(), merchantTxn, name, email, phone, amount,
				udf1, udf2, udf3, udf4, udf5, message
		);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("key", props.getMerchantKey());
		body.put("merchant_txn", merchantTxn);
		body.put("name", name);
		body.put("email", email);
		body.put("phone", phone);
		body.put("amount", amount);
		body.put("message", message);
		body.put("udf1", udf1);
		body.put("udf2", udf2);
		body.put("udf3", udf3);
		body.put("udf4", udf4);
		body.put("udf5", udf5);
		body.put("hash", hash);

		// Mandatory on a sub-aggregator (master) account: without it the gateway answers ECSUBME02.
		// It is not part of the digest, so it is added after hashing.
		String subMerchantId = data.get("sub_merchant_id");
		if (subMerchantId != null && !subMerchantId.isBlank()) {
			body.put("sub_merchant_id", subMerchantId);
		}

		// Optional: restrict payment modes (e.g., "NB,DC,CC,UPI")
		String showPaymentMode = data.get("show_payment_mode");
		if (showPaymentMode != null && !showPaymentMode.isBlank()) {
			body.put("show_payment_mode", showPaymentMode);
		}

		Map<String, Object> raw = postJson(props.getDashboardBaseUrl() + "/easycollect/v1/create", body);

		Map<String, Object> result = new HashMap<>();
		if (raw == null || !toBool(raw.get("status"))) {
			result.put("status", "failure");
			result.put("error", raw == null ? "No response" : firstText(
					raw.get("error_desc"), raw.get("error"), raw.get("message"), raw.get("data")));
			return result;
		}

		// The documented response puts the link in an object: data.{id,merchant_txn,payment_url,
		// state,expiry_date}. A bare URL string is accepted too because the link-update variant
		// is only described by example, but a success with neither must not become a payment URL.
		Object dataObj = raw.get("data");
		String linkUrl = null;
		String resolvedMerchantTxn = merchantTxn;
		Object linkId = null;
		Object state = null;
		Object expiryDate = null;
		if (dataObj instanceof Map<?, ?> link) {
			linkUrl = firstText(link.get("payment_url"), link.get("link"));
			resolvedMerchantTxn = firstText(link.get("merchant_txn"), merchantTxn);
			linkId = link.get("id");
			state = link.get("state");
			expiryDate = link.get("expiry_date");
		} else if (dataObj instanceof String s && s.trim().startsWith("http")) {
			linkUrl = s.trim();
		} else if (dataObj instanceof String s && !s.isBlank()) {
			result.put("status", "failure");
			result.put("error", "Easebuzz rejected the link request: " + s);
			return result;
		}

		if (linkUrl == null || linkUrl.isBlank()) {
			result.put("status", "failure");
			result.put("error", "Easebuzz returned no payment link URL");
			return result;
		}

		result.put("status", "success");
		result.put("payment_url", linkUrl);
		result.put("merchant_txn", resolvedMerchantTxn);
		if (linkId != null) result.put("link_id", linkId);
		if (state != null) result.put("state", state);
		if (expiryDate != null) result.put("expiry_date", expiryDate);
		return result;
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> createSubMerchant(String subMerchantId, String subMerchantName, String email, String phone,
			String accountNumber, String ifsc, String bankName, String nameInBank, String branchName,
			String businessType, String pan, String gst, String businessAddress,
			String legalEntityName, String state, String fssaiNumber, String restaurantFormat,
			BigDecimal upiDeductionLtLimit, BigDecimal dcDeductionGtTwoThousand, String portalPassword) {
		checkCredentials();

		// Compliance (EaseBuzz CPV): the business name registered must be the verified
		// legal entity name matching PAN/GST — a display/trade name causes CPV mismatch.
		String legalName = (legalEntityName != null && !legalEntityName.isBlank())
				? legalEntityName : subMerchantName;
		String sanitizedLegalName = sanitizeName(legalName);
		String sanitizedDisplayName = sanitizeName(subMerchantName);

		// Do not submit placeholder data to a CPV pipeline — fail fast on missing essentials.
		if (sanitizedLegalName == null || sanitizedLegalName.isBlank())
			throw new IllegalArgumentException("Legal entity name is required for sub-merchant onboarding");
		if (businessAddress == null || businessAddress.isBlank())
			throw new IllegalArgumentException("Business address is required for sub-merchant onboarding");
		if (state == null || state.isBlank())
			throw new IllegalArgumentException("State is required for sub-merchant onboarding");

		String hash = generateHash(props.getMerchantKey(), email != null ? email : "", phone != null ? phone : "");
		Map<String, Object> merchantDetails = new HashMap<>();
		merchantDetails.put("merchant_key", props.getMerchantKey());
		merchantDetails.put("hash", hash);

		Map<String, Object> submerchantDetails = new HashMap<>();
		submerchantDetails.put("sub_merchant_name", sanitizedDisplayName);
		submerchantDetails.put("sub_merchant_email", email);
		submerchantDetails.put("sub_merchant_phone", phone);
		submerchantDetails.put("sub_merchant_name_in_bank", nameInBank);
		submerchantDetails.put("sub_merchant_account_number", accountNumber);
		submerchantDetails.put("sub_merchant_bank_name", bankName);
		submerchantDetails.put("sub_merchant_branch_name", branchName);
		submerchantDetails.put("sub_merchant_ifsc_code", ifsc);
		boolean isUpdate = subMerchantId != null && !subMerchantId.isBlank();
		if (isUpdate) {
			submerchantDetails.put("sub_merchant_id", subMerchantId);
		} else if (portalPassword != null && !portalPassword.isBlank()) {
			// Passwords belong to the creation example only; the documented update payload has no
			// password fields, and rotating one on a resubmit would silently change the merchant's
			// Easebuzz dashboard login. The caller supplies this one so it can keep an encrypted
			// copy: the merchant needs it to sign in once their KYC is approved.
			submerchantDetails.put("sub_merchant_password", portalPassword);
			submerchantDetails.put("sub_merchant_confirm_password", portalPassword);
		}

		Map<String, Object> businessDetails = new HashMap<>();
		String nature = "INDIVIDUAL/FREELANCERS";
		if ("SOLE_PROPRIETORSHIP".equalsIgnoreCase(businessType)) nature = "SOLE PROPRIETOR";
		else if ("PARTNERSHIP".equalsIgnoreCase(businessType)) nature = "PARTNERSHIP FIRM";
		else if ("PRIVATE_LIMITED".equalsIgnoreCase(businessType) || "PUBLIC_LIMITED".equalsIgnoreCase(businessType))
			nature = "PRIVATE LTD/PUBLIC LTD/OPC";

		businessDetails.put("sub_merchant_business_nature", nature);
		businessDetails.put("sub_merchant_business_type", businessType != null ? businessType : "SOLE PROPRIETOR");
		businessDetails.put("sub_merchant_business_name", sanitizedLegalName);
		businessDetails.put("sub_merchant_business_address", businessAddress);
		businessDetails.put("sub_merchant_state", state);
		// Easebuzz locks the MCC at creation (no post-update), so it must reflect the outlet.
		String mccCode = mccCodeForFormat(restaurantFormat);
		if (restaurantFormat == null || restaurantFormat.isBlank()) {
			log.warn("No restaurant format supplied for sub-merchant {} - defaulting MCC to {}",
				subMerchantName, mccCode);
		}
		businessDetails.put("sub_merchant_mcc_code", mccCode);

		if (gst != null && !gst.isBlank()) businessDetails.put("sub_merchant_gstin", gst);
		if (pan != null && !pan.isBlank()) businessDetails.put("sub_merchant_pan_number", pan);
		// FSSAI license is mandatory for food merchants per EaseBuzz compliance.
		if (fssaiNumber != null && !fssaiNumber.isBlank())
			businessDetails.put("sub_merchant_fssai_number", fssaiNumber);

		Map<String, Object> body = new HashMap<>();
		body.put("merchant_details", merchantDetails);
		body.put("submerchant_details", submerchantDetails);
		body.put("business_details", businessDetails);

		if (upiDeductionLtLimit != null || dcDeductionGtTwoThousand != null) {
			Map<String, Object> deductions = new HashMap<>();
			if (upiDeductionLtLimit != null)
				deductions.put("upi_deduction_percentage_lt_limit", upiDeductionLtLimit.toPlainString());
			if (dcDeductionGtTwoThousand != null)
				deductions.put("dc_deduction_percentage_gt_two_thousand", dcDeductionGtTwoThousand.toPlainString());
			body.put("submerchant_deduction_percentage", deductions);
		}

		Map<String, Object> created = postJson(props.getDashboardBaseUrl() + "/merchant/v1/submerchant/create/", body);
		log.info("Easebuzz submerchant/create ({}) request={} response={}",
				(subMerchantId == null || subMerchantId.isBlank()) ? "create" : "update " + subMerchantId,
				maskedForLog(body), maskedForLog(created));
		return created;
	}

	/**
	 * Maps the outlet format chosen at onboarding to the Easebuzz MCC code. Easebuzz accepts
	 * 5812 (dine-in), 5814 (QSR / fast food), 5813 (bar / drinking place) and 5811 (caterer)
	 * and cannot change the code after creation. An unknown or missing format falls back to
	 * 5812 (eating places and restaurants) so a submission is never rejected for this field.
	 */
	public static String mccCodeForFormat(String restaurantFormat) {
		if (restaurantFormat == null) return "5812";
		switch (restaurantFormat.trim().toUpperCase().replace(" ", "_").replace("-", "_")) {
			case "QSR":
			case "FAST_FOOD":
			case "QUICK_SERVICE_RESTAURANT":
				return "5814";
			case "BAR":
			case "PUB":
			case "DRINKING_PLACE":
				return "5813";
			case "CATERER":
			case "CATERING":
				return "5811";
			case "DINE_IN":
			case "RESTAURANT":
			case "CASUAL_DINING":
			case "FINE_DINING":
			case "CAFE":
			case "BAKERY":
			case "CLOUD_KITCHEN":
			case "FOOD_TRUCK":
				return "5812";
			default:
				return "5812";
		}
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
		Map<String, Object> issued = postJson(
				props.getDashboardBaseUrl() + "/submerchant/v1/generate_kyc_access_key", body);
		// The one call whose failure leaves a merchant unable to complete KYC, and whose success
		// is a URL holding an access key. The request is logged because "invalid hash" is
		// otherwise unfalsifiable; the response is logged with the URL reduced to its presence.
		log.info("Easebuzz generate_kyc_access_key request={} response={}",
				maskedForLog(body), maskedForLog(issued));
		return issued;
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
		// Read-only; the post-split *create* call is left un-retried because
		// PostSplitService already schedules its own +5m/+15m retry ladder.
		return postJson(props.getDashboardBaseUrl() + "/post-split/v1/retrieve/", body, true);
	}

	private Map<String, Object> post(String url, MultiValueMap<String, String> bodyMap) {
		return post(url, bodyMap, false);
	}

	private Map<String, Object> post(String url, MultiValueMap<String, String> bodyMap, boolean retryable) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		HttpEntity<String> request = new HttpEntity<>(encodeForm(bodyMap), headers);
		try {
			ResponseEntity<Map<String, Object>> response = exchangeWithRetry(url, request, retryable);
			log.debug("Easebuzz API {} success: {}", url, response.getStatusCode());
			return response.getBody();
		} catch (org.springframework.web.client.HttpStatusCodeException e) {
			String providerError = providerErrorBody(e);
			log.error("Easebuzz API {} error {} body={}", url, e.getStatusCode(), providerError);
			return Map.of("status", "failure", "error",
					providerError != null ? providerError : "Easebuzz gateway request failed");
		} catch (Exception e) {
			log.error("Easebuzz API {} failed: {}", url, e.getMessage());
			return Map.of("status", "failure", "error", e.getMessage());
		}
	}

	/**
	 * Builds the form body by hand so multi-valued entries are not rendered as {@code key[0]=}
	 * by a form message converter.
	 */
	private static String encodeForm(MultiValueMap<String, String> bodyMap) {
		StringBuilder sb = new StringBuilder();
		bodyMap.forEach((key, values) -> {
			if (!values.isEmpty()) {
				if (sb.length() > 0) sb.append("&");
				sb.append(java.net.URLEncoder.encode(key, StandardCharsets.UTF_8));
				sb.append("=");
				sb.append(java.net.URLEncoder.encode(values.get(0), StandardCharsets.UTF_8));
			}
		});
		return sb.toString();
	}

	/**
	 * POSTs a form-encoded body, advancing to the next candidate URL only when the gateway says the
	 * route itself is absent (404/405). Any other outcome is final: a 400 or a 500 means the route
	 * exists and the request was rejected, so trying a different path would only hide the reason.
	 *
	 * <p>This exists because two first-party Easebuzz sources disagree on the transaction-status
	 * route: the v2.0 integration kit posts to {@code /transaction/v2/retrieve}
	 * (easebuzz-lib/transaction.php) while the docs site documents
	 * {@code /transaction/v2.1/retrieve}. Both are tried, and the log records which one answered,
	 * so the ambiguity is settled by real traffic instead of being guessed at deploy time.
	 */
	private Map<String, Object> postFormWithFallback(java.util.List<String> urls,
			MultiValueMap<String, String> bodyMap, boolean retryable) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		HttpEntity<String> request = new HttpEntity<>(encodeForm(bodyMap), headers);

		for (int i = 0; i < urls.size(); i++) {
			String url = urls.get(i);
			boolean hasNext = i < urls.size() - 1;
			try {
				ResponseEntity<Map<String, Object>> response = exchangeWithRetry(url, request, retryable);
				if (hasNext) {
					log.debug("Easebuzz route {} answered; alternate route not needed", url);
				}
				return response.getBody();
			} catch (org.springframework.web.client.HttpStatusCodeException e) {
				int code = e.getStatusCode().value();
				if (hasNext && (code == 404 || code == 405)) {
					log.warn("Easebuzz route {} answered {}; trying alternate route {}", url, code, urls.get(i + 1));
					continue;
				}
				String providerError = providerErrorBody(e);
				log.error("Easebuzz API {} error {} body={}", url, e.getStatusCode(), providerError);
				return Map.of("status", "failure", "error",
						providerError != null ? providerError : "Easebuzz gateway request failed");
			} catch (Exception e) {
				log.error("Easebuzz API {} failed: {}", url, e.getMessage());
				return Map.of("status", "failure", "error",
						e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
			}
		}
		throw new IllegalStateException("No Easebuzz route candidate was supplied");
	}

	private Map<String, Object> postJson(String url, Map<String, Object> data) {
		return postJson(url, data, false);
	}

	private Map<String, Object> postJson(String url, Map<String, Object> data, boolean retryable) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
		if (log.isDebugEnabled()) {
			log.debug("Easebuzz JSON API {} request={}", url, maskedForLog(data));
		}
		try {
			ResponseEntity<Map<String, Object>> response = exchangeWithRetry(url, request, retryable);
			if (log.isDebugEnabled()) {
				log.debug("Easebuzz JSON API {} response={}", url, maskedForLog(response.getBody()));
			}
			return response.getBody();
		} catch (org.springframework.web.client.HttpStatusCodeException e) {
			String providerError = providerErrorBody(e);
			log.error("Easebuzz JSON API {} error {} body={}", url, e.getStatusCode(), providerError);
			return Map.of("status", false, "error",
					providerError != null ? providerError : "Easebuzz gateway request failed");
		} catch (Exception e) {
			log.error("Easebuzz JSON API {} failed: {}", url, e.getMessage());
			return Map.of("status", false, "error", e.getMessage());
		}
	}

	/**
	 * Reads the gateway's own failure text out of a non-2xx response.
	 *
	 * <p>Easebuzz explains a rejection in the response body ({@code error} / {@code error_desc}),
	 * not in the status line. Logging and returning only the code collapsed every rejection —
	 * an unenabled product, a bad hash, a missing field — into the same opaque "gateway request
	 * failed", which is unanswerable without a packet capture. The body is truncated because it
	 * is a provider string of unknown length, not because it contains secrets.
	 */
	private static String providerErrorBody(org.springframework.web.client.HttpStatusCodeException e) {
		try {
			String body = e.getResponseBodyAsString();
			if (body == null || body.isBlank()) return null;
			body = body.trim();
			return body.length() > MAX_PROVIDER_BODY ? body.substring(0, MAX_PROVIDER_BODY) + "..." : body;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static final int MAX_PROVIDER_BODY = 600;

	/** Fields that must never reach a log line: they authorise a transaction or identify an account. */
	private static final java.util.Set<String> MASKED_FIELDS = java.util.Set.of(
			"hash", "merchant_key", "key", "salt", "access_key",
			"sub_merchant_password", "sub_merchant_confirm_password",
			"sub_merchant_pan_number", "sub_merchant_account_number",
			// The portal URL embeds an access key: masking it keeps "did a link arrive?" answerable
			// while making the log useless to anyone who would sign in as the merchant.
			"kyc_dashboard_url", "kyc_url");

	/**
	 * Copies a request or response body with the credential fields replaced.
	 *
	 * <p>The two onboarding calls are the only place the exact bytes matter: a hash mismatched by
	 * one character is indistinguishable from a disabled product once the body is gone. So the
	 * payload is logged as it was built, minus the fields that would let a log reader transact —
	 * the digest, the merchant key, the sub-merchant password and PAN are replaced outright, and a
	 * bank account keeps its last four digits so two submissions can be told apart without being
	 * readable.
	 */
	private static Object maskedForLog(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> masked = new java.util.LinkedHashMap<>();
			map.forEach((k, v) -> {
				String field = String.valueOf(k);
				masked.put(field, MASKED_FIELDS.contains(field) ? maskValue(field, v) : maskedForLog(v));
			});
			return masked;
		}
		if (value instanceof java.util.List<?> list) {
			return list.stream().map(EasebuzzApiClient::maskedForLog).toList();
		}
		return value;
	}

	private static Object maskValue(String field, Object value) {
		if (value == null) return null;
		String text = value.toString();
		if ("sub_merchant_account_number".equals(field) && text.length() > 4) {
			return "****" + text.substring(text.length() - 4);
		}
		return "***";
	}

	/**
	 * Performs the POST, retrying only transient failures when the caller declared the
	 * operation safe to repeat.
	 *
	 * <p>Retried: transport failures (connect/read timeout, reset) and 5xx / 429
	 * responses. Not retried: 4xx, which means the request itself was rejected and a
	 * repeat would fail identically. A 5xx may have been emitted before the gateway
	 * committed anything, so it is treated as transient — callers that opt in are the
	 * ones whose repeat is harmless.
	 *
	 * <p>Backoff is exponential with jitter (250ms, 500ms, capped at 1s) so a gateway
	 * incident does not synchronise every in-flight request into a thundering herd.
	 */
	private ResponseEntity<Map<String, Object>> exchangeWithRetry(String url, HttpEntity<?> request, boolean retryable) {
		int maxAttempts = retryable ? MAX_ATTEMPTS : 1;
		for (int attempt = 1; ; attempt++) {
			try {
				return restTemplate.exchange(url, HttpMethod.POST, request,
						new ParameterizedTypeReference<Map<String, Object>>() {});
			} catch (RestClientResponseException e) {
				int statusCode = e.getStatusCode().value();
				if (!isRetryableStatus(statusCode) || attempt >= maxAttempts) {
					throw e;
				}
				log.warn("Easebuzz API {} returned {} (attempt {}/{}), retrying",
						url, statusCode, attempt, maxAttempts);
			} catch (ResourceAccessException e) {
				if (attempt >= maxAttempts) {
					throw e;
				}
				log.warn("Easebuzz API {} transport failure (attempt {}/{}): {} — retrying",
						url, attempt, maxAttempts, e.getMessage());
			}
			sleepBeforeRetry(attempt);
		}
	}

	private static boolean isRetryableStatus(int statusCode) {
		return statusCode >= 500 || statusCode == RETRYABLE_STATUS_TOO_MANY_REQUESTS;
	}

	private static void sleepBeforeRetry(int attempt) {
		long base = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS << (attempt - 1));
		long delay = base + ThreadLocalRandom.current().nextLong(base / 4 + 1);
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new EasebuzzApiException("Interrupted while waiting to retry an Easebuzz API call", e);
		}
	}

	public static String sanitizeName(String name) {
		if (name == null) return null;
		return name.replace("'", "").replace("\"", "").replace("\\", "");
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	/** First non-blank value as text, or "". Keeps error/link extraction free of null chains. */
	private static String firstText(Object... values) {
		for (Object value : values) {
			if (value != null && !value.toString().isBlank()) return value.toString().trim();
		}
		return "";
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
