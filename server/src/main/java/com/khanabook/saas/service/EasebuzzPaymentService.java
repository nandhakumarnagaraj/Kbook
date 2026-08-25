package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.entity.FssaiRenewal;
import com.khanabook.saas.repository.FssaiRenewalRepository;
import com.khanabook.saas.repository.FssaiTrackerRepository;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.exception.EntityNotFoundException;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EasebuzzPaymentService {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzPaymentService.class);
    private final EasebuzzApiClient easebuzzApi;
    private final BillRepository billRepo;
    private final EasebuzzWebhookEventRepository webhookEventRepo;
    private final SubMerchantService subMerchantService;
    private final com.khanabook.saas.config.EasebuzzProperties props;
    private final ChargebackPreventionService chargebackService;
    private final FssaiRenewalRepository fssaiRenewalRepo;
    private final FssaiTrackerRepository fssaiTrackerRepo;

    @Transactional
    public Map<String, Object> createOrder(Long billId, Long restaurantId) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if ("paid".equalsIgnoreCase(bill.getPaymentStatus()) || "success".equalsIgnoreCase(bill.getPaymentStatus())) {
            log.warn("Blocked Easebuzz order creation for already paid billId={} restaurantId={} existingTxnid={}",
                    billId, restaurantId, bill.getGatewayTxnId());
            return Map.of(
                    "status", "failure",
                    "code", "ALREADY_PAID",
                    "error", "Bill is already paid. Payment retry is not allowed.",
                    "txnid", bill.getGatewayTxnId() != null ? bill.getGatewayTxnId() : ""
            );
        }

        // ERA-CONFIRMED (2026-08-17): Easebuzz txnid behavior:
        // - Each txnid can ONLY be used ONCE — never reuse after success, failure, or timeout
        // - Access token expires in 15 minutes — customer cannot pay after that
        // - No cancel API exists — unpaid txnids auto-expire in 15 min
        // - Multiple txnids for same bill CAN both succeed (double-charge risk)
        // - No webhook for abandoned txnids — poll /transaction/v2.1/retrieve if needed
        // Strategy (P0 fix): Poll old txnid status BEFORE clearing. Block if pending/success.
        if (bill.getGatewayTxnId() != null && !bill.getGatewayTxnId().isBlank()
                && !"paid".equalsIgnoreCase(bill.getPaymentStatus())) {
            String oldTxnId = bill.getGatewayTxnId();
            String oldTxnStatus = pollOldTxnStatus(oldTxnId);
            log.info("Polled old txnid status for billId={} txnid={}: {}", billId, oldTxnId, oldTxnStatus);

            if ("success".equalsIgnoreCase(oldTxnStatus)) {
                // Old txnid actually succeeded — mark bill as paid, do NOT create new order
                log.warn("Old txnid={} is SUCCESS at gateway — marking bill paid, blocking new order creation", oldTxnId);
                bill.setGatewayStatus("success");
                bill.setPaymentStatus("paid");
                bill.setPaidAt(System.currentTimeMillis());
                billRepo.save(bill);
                return Map.of(
                        "status", "failure",
                        "code", "ALREADY_PAID",
                        "error", "Payment already completed for this bill. No new payment needed.",
                        "txnid", oldTxnId
                );
            }

            if ("pending".equalsIgnoreCase(oldTxnStatus) || "initiated".equalsIgnoreCase(oldTxnStatus)) {
                // Old txnid is still in-flight — block new order to prevent double-charge
                log.warn("Old txnid={} is {} at gateway — blocking new order creation for billId={}",
                        oldTxnId, oldTxnStatus, billId);
                return Map.of(
                        "status", "failure",
                        "code", "PAYMENT_PENDING",
                        "error", "A payment is already in progress. Please wait for it to complete or expire before retrying.",
                        "txnid", oldTxnId
                );
            }

            if ("unknown".equalsIgnoreCase(oldTxnStatus)) {
                // FAIL CLOSED: the status API failed or returned something unparseable.
                // The old txnid may still be in-flight at the gateway — creating a new
                // one here is the exact multi-txnid double-charge scenario. The customer
                // can retry once the gateway recovers; verifyPayment() will resolve it.
                log.warn("Old txnid={} status unknown at gateway — blocking new order creation for billId={} (fail closed)",
                        oldTxnId, billId);
                return Map.of(
                        "status", "failure",
                        "code", "PAYMENT_STATUS_UNKNOWN",
                        "error", "Cannot verify the previous payment attempt right now. Please retry in a few minutes.",
                        "txnid", oldTxnId
                );
            }

            // Confirmed terminal/expired statuses only
            // (failure, dropped, bounced, userCancelled, preInitiated) — safe to clear.
            log.info("Old txnid={} status={} is terminal/expired — clearing stale gateway data for billId={}",
                    oldTxnId, oldTxnStatus, billId);
            bill.setGatewayTxnId(null);
            bill.setGatewayStatus(null);
            billRepo.save(bill);
        }

        Map<String, Object> fraudScore = chargebackService.scoreTransaction(billId);
        Object riskObj = fraudScore.get("risk");
        String risk = riskObj != null ? (String) riskObj : "unknown";
        Object scoreObj = fraudScore.get("score");
        double score = scoreObj != null ? ((Number) scoreObj).doubleValue() : 0;
        if ("critical".equals(risk) || score >= 60) {
            log.warn("Payment blocked by fraud scoring billId={} score={} risk={}", billId, score, risk);
            return Map.of(
                    "status", "failure",
                    "code", "FRAUD_RISK",
                    "error", "Transaction flagged as high risk. Please contact support.",
                    "fraudScore", fraudScore
            );
        }
        if ("high".equals(risk)) {
            log.warn("Payment flagged for review billId={} score={} risk={}", billId, score, risk);
        }

        // Build payment data from real bill
        String amount = String.format("%.2f", bill.getTotalAmount());
        String productinfo = "KhanaBook Order " +
            (bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : billId.toString());
        String firstname = bill.getCustomerName() != null
            ? bill.getCustomerName().replaceAll("[^a-zA-Z0-9 ]", "").trim()
            : "Customer";
        // firstname will be further sanitized by EasebuzzApiClient (removes spaces)
        String phone = bill.getCustomerWhatsapp() != null ? bill.getCustomerWhatsapp() : "";

        // Unique txnid: always exactly 20 chars (Easebuzz max limit = 20).
        // Format: KB{5-digit billId tail}{5-digit restaurantId tail}{8-hex UUID}
        // Guaranteed globally unique per bill due to UUID suffix.
        String txnSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String billTail = String.format("%05d", billId % 100000);
        String restTail = String.format("%05d", restaurantId % 100000);
        String txnid = "KB" + billTail + restTail + txnSuffix;
        log.info("Creating Easebuzz payment attempt billId={} restaurantId={} txnid={} amount={}",
                billId, restaurantId, txnid, amount);

        Map<String, String> data = new HashMap<>();
        data.put("txnid", txnid);
        data.put("amount", amount);
        data.put("productinfo", productinfo);
        data.put("firstname", firstname);
        data.put("surl", props.getReturnUrl());
        data.put("furl", props.getReturnUrl());

        // Look up sub-merchant — use its ID and contact info if available
        try {
            EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
            boolean subMerchantActive = "ACTIVE".equals(sm.getStatus());
            boolean isTestMode = "test".equalsIgnoreCase(props.getPayMode());
            String subMerchantId = sm.getSubMerchantId();
            if (subMerchantId != null && !subMerchantId.isBlank() && (subMerchantActive || isTestMode)) {
                data.put("sub_merchant_id", subMerchantId);
                log.info("Using sub-merchant for payment: {} (active={}, testMode={})", subMerchantId, subMerchantActive, isTestMode);
            } else {
                log.warn("Sub-merchant not active or missing ID (status={}, id={}), processing as parent merchant", sm.getStatus(), subMerchantId);
            }
            if (sm.getContactEmail() != null) {
                data.put("email", sm.getContactEmail());
            }
            if (phone.isBlank() && sm.getContactPhone() != null) {
                phone = sm.getContactPhone();
            }
        } catch (EntityNotFoundException e) {
            log.info("No sub-merchant configured for restaurant {}, proceeding as parent-merchant payment", restaurantId);
        } catch (Exception e) {
            log.warn("Error looking up sub-merchant for restaurant {}: {}", restaurantId, e.getMessage(), e);
        }

        // Set phone after sub-merchant fallback, then email fallback
        data.put("phone", phone);

        // Email is mandatory for Easebuzz — fallback if not set
        if (!data.containsKey("email") || data.get("email") == null || data.get("email").isBlank()) {
            data.put("email", "customer@khanabook.in");
        }

        data.put("udf1", billId.toString());
        data.put("udf2", restaurantId.toString());

        log.debug("Initiating Easebuzz payment billId={} txnid={} payload={}", billId, txnid, data);
        Map<String, Object> result = easebuzzApi.initiatePayment(data);
        String status = (String) result.getOrDefault("status", "failure");

        bill.setGatewayTxnId(txnid);
        bill.setGatewayStatus(status);
        billRepo.save(bill);

        if ("success".equalsIgnoreCase(status)) {
            String accessToken = (String) result.get("access_token");
            String paymentUrl = (String) result.get("payment_url");
            log.info("Payment order created billId={} txnid={} accessKeyLength={} paymentUrlPresent={}",
                    billId, txnid, accessToken != null ? accessToken.length() : 0, paymentUrl != null && !paymentUrl.isBlank());
            return Map.of(
                "status", "success",
                "txnid", txnid,
                "access_token", accessToken != null ? accessToken : "",
                "payment_url", paymentUrl != null ? paymentUrl : "",
                "amount", bill.getTotalAmount(),
                "pay_mode", props.getPayMode()
            );
        }
        log.warn("Payment order creation failed billId={} txnid={} response={}", billId, txnid, result);
        return Map.of("status", "failure", "error", result.getOrDefault("error", "Payment initiation failed"));
    }

    @Transactional
    public Map<String, Object> getPaymentStatus(Long billId, boolean refresh) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (refresh && bill.getGatewayTxnId() != null
                && !"paid".equalsIgnoreCase(bill.getPaymentStatus())
                && !"success".equalsIgnoreCase(bill.getPaymentStatus())) {
            log.info("Refreshing Easebuzz payment status billId={} txnid={}", billId, bill.getGatewayTxnId());
            verifyPayment(billId);
            bill = billRepo.findById(billId)
                    .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        }
        String paymentStatus = bill.getPaymentStatus() != null ? bill.getPaymentStatus() : "unknown";
        String gatewayTxnId = bill.getGatewayTxnId() != null ? bill.getGatewayTxnId() : "";
        BigDecimal amount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        log.info("Payment status read billId={} paymentStatus={} gatewayStatus={} txnid={}",
                billId, paymentStatus, bill.getGatewayStatus(), gatewayTxnId);
        return Map.of(
                "billId", billId,
                "paymentId", bill.getId(),
                "paymentStatus", paymentStatus,
                "gatewayTxnId", gatewayTxnId,
                "amount", amount,
                "message", bill.getGatewayStatus() != null ? bill.getGatewayStatus() : paymentStatus
        );
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> verifyPayment(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));

        // Double-charge guard: if bill is already paid, return success without re-processing
        if ("paid".equalsIgnoreCase(bill.getPaymentStatus())) {
            log.info("Payment already confirmed billId={} txnid={} — returning existing result", billId, bill.getGatewayTxnId());
            return Map.of("status", "success", "txnid", bill.getGatewayTxnId() != null ? bill.getGatewayTxnId() : "",
                    "alreadyPaid", true);
        }

        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found");
        }
        log.info("Verifying Easebuzz payment billId={} txnid={}", billId, bill.getGatewayTxnId());
        Map<String, Object> raw = easebuzzApi.getTransactionStatus(bill.getGatewayTxnId());

        // v2.1 response has top-level status (API call success) and nested msg with transaction data
        if (!toBool(raw.get("status"))) {
            String err = str(raw.getOrDefault("error", "Transaction status check failed"));
            bill.setGatewayStatus("error");
            billRepo.save(bill);
            return Map.of("status", "failure", "error", err);
        }

        Object msgObj = raw.get("msg");
        if (msgObj == null) {
            return Map.of("status", "failure", "error", "No transaction data in response");
        }

        // msg can be a list or a single object depending on Easebuzz version
        Map<String, Object> txnData;
        if (msgObj instanceof List) {
            List<Map<String, Object>> msgList = (List<Map<String, Object>>) msgObj;
            if (msgList.isEmpty()) {
                return Map.of("status", "failure", "error", "Empty transaction data");
            }
            txnData = msgList.get(0);
        } else {
            txnData = (Map<String, Object>) msgObj;
        }

        String easebuzzStatus = str(txnData.getOrDefault("status", "failure"));
        String easebuzzId = str(txnData.getOrDefault("easebuzz_id", txnData.getOrDefault("easepayid", "")));

        if ("success".equalsIgnoreCase(easebuzzStatus)) {
            bill.setGatewayStatus("success");
            bill.setPaymentStatus("paid");
            bill.setPaidAt(System.currentTimeMillis());
            billRepo.save(bill);
            saveGatewayEventIfPresent(bill, easebuzzId, "success");
            log.info("Payment verification success billId={} txnid={} easebuzzId={}", billId, bill.getGatewayTxnId(), easebuzzId);
            return Map.of("status", "success", "easebuzz_id", easebuzzId, "txnid", bill.getGatewayTxnId());
        }

        bill.setGatewayStatus(easebuzzStatus);
        billRepo.save(bill);
        log.info("Payment verification result billId={} txnid={} status={}", billId, bill.getGatewayTxnId(), easebuzzStatus);
        return Map.of("status", easebuzzStatus, "txnid", bill.getGatewayTxnId());
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> initiateRefund(Long billId, BigDecimal amount, String reason) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found for refund");
        }
        String txnid = bill.getGatewayTxnId();

        // Look up easebuzz_id from the original payment webhook event
        String easebuzzId = "";
        java.util.Optional<EasebuzzWebhookEvent> webhookEvent = webhookEventRepo
                .findByRestaurantIdAndTxnId(bill.getRestaurantId(), txnid);
        if (webhookEvent.isPresent() && webhookEvent.get().getEasebuzzId() != null) {
            easebuzzId = webhookEvent.get().getEasebuzzId();
        }
        if (easebuzzId.isBlank()) {
            log.warn("Could not find easebuzz_id for billId={} txnid={}, proceeding with txnid as fallback", billId, txnid);
            easebuzzId = txnid;
        }

        // Deterministic merchant_refund_id PER REFUND ATTEMPT: Easebuzz treats a
        // duplicate merchant_refund_id as idempotent (returns the existing refund,
        // never moves money twice). The ID includes the amount already refunded
        // BEFORE this attempt (bill.refundAmount is advanced only AFTER a successful
        // initiation by RefundService), so:
        //  - retry of the same attempt -> same baseline -> same ID (gateway dedups)
        //  - next partial refund       -> new baseline -> new ID (money can move)
        //  - two concurrent partials   -> same baseline -> one wins (over-refund guard)
        BigDecimal refundedSoFar = bill.getRefundAmount() != null ? bill.getRefundAmount() : BigDecimal.ZERO;
        String merchantRefundId = "REF_" + billId + "_"
                + refundedSoFar.stripTrailingZeros().toPlainString();

        log.info("Initiating refund billId={} txnid={} easebuzzId={} merchantRefundId={} amount={}",
                billId, txnid, easebuzzId, merchantRefundId, amount);

        Map<String, Object> result = easebuzzApi.initiateRefund(merchantRefundId, easebuzzId, amount.toString());
        log.info("Refund initiation response for billId={}: {}", billId, result);

        boolean success = toBool(result.get("status"));
        if (success) {
            // v2 refund response returns refund_id directly (not nested in msg)
            String ebRefundId = str(result.get("refund_id"));
            if (ebRefundId.isBlank()) {
                Object msgObj = result.get("msg");
                if (msgObj instanceof Map) {
                    ebRefundId = str(((Map<String, Object>) msgObj).get("refund_id"));
                }
            }
            bill.setRefundId(ebRefundId.isBlank() ? merchantRefundId : ebRefundId);
            bill.setRefundAmount(amount);
            bill.setGatewayStatus("refund_initiated");
            billRepo.save(bill);

            return Map.of(
                "status", "success",
                "easebuzz_refund_id", bill.getRefundId(),
                "merchant_refund_id", merchantRefundId,
                "txnid", txnid
            );
        }

        return Map.of("status", "failure", "error", result.getOrDefault("error", "Refund initiation failed"));
    }

    @Transactional
    public Map<String, Object> getRefundStatus(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found");
        }
        if (bill.getRefundId() == null || bill.getRefundId().isBlank()) {
            return Map.of("status", "failure", "error", "No refund initiated for this bill");
        }

        Map<String, Object> result = easebuzzApi.getRefundStatus(bill.getGatewayTxnId(), bill.getRefundId());
        String status = str(result.getOrDefault("status", "failure"));
        Object refundMsg = result.get("msg");

        return Map.of(
            "status", status,
            "refund_id", bill.getRefundId(),
            "txnid", bill.getGatewayTxnId(),
            "msg", refundMsg != null ? refundMsg : ""
        );
    }

    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        String s = value.toString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    @Deprecated
    @Transactional
    public Map<String, Object> cancelTransaction(Long billId) {
        // ERA confirmed: No cancel API exists. Unpaid txnids auto-expire in 15 min.
        return Map.of("status", "failure", "error", "Cancel API not supported. Transactions auto-expire in 15 minutes.");
    }

    @Transactional
    public Map<String, Object> createFssaiRenewalOrder(Integer years, String fssaiNumber, Long restaurantId) {
        if (years == null || years < 1 || years > 5) {
            return Map.of("status", "failure", "error", "years must be between 1 and 5");
        }
        if (fssaiNumber == null || fssaiNumber.length() != 14) {
            return Map.of("status", "failure", "error", "fssaiNumber must be 14 digits");
        }

        BigDecimal amount = new BigDecimal(years).multiply(new BigDecimal("1000.00")); // ₹1000 per year
        String amountStr = String.format("%.2f", amount);

        // Generate unique txnid: max 40 chars
        String txnSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String restTail = String.format("%05d", restaurantId % 100000);
        String txnid = "KBF" + restTail + txnSuffix;

        FssaiRenewal renewal = new FssaiRenewal();
        renewal.setRestaurantId(restaurantId);
        renewal.setFssaiNumber(fssaiNumber);
        renewal.setYears(years);
        renewal.setAmount(amount);
        renewal.setStatus("PENDING");
        renewal.setEasebuzzTxnId(txnid);
        renewal.setCreatedAt(System.currentTimeMillis());
        renewal.setUpdatedAt(System.currentTimeMillis());
        fssaiRenewalRepo.save(renewal);

        Map<String, String> data = new HashMap<>();
        data.put("txnid", txnid);
        data.put("amount", amountStr);
        data.put("productinfo", "FSSAIRenewal" + years + "Years");
        data.put("firstname", "Shop" + restaurantId);
        data.put("surl", props.getReturnUrl());
        data.put("furl", props.getReturnUrl());

        data.put("udf1", "fssai_renewal");
        data.put("udf2", restaurantId.toString());
        data.put("udf3", fssaiNumber);
        data.put("udf4", years.toString());

        try {
            EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
            if (sm.getContactEmail() != null) {
                data.put("email", sm.getContactEmail());
            }
            if (sm.getContactPhone() != null) {
                data.put("phone", sm.getContactPhone());
            }
            String subMerchantId = sm.getSubMerchantId();
            if (subMerchantId != null && !subMerchantId.isBlank()) {
                data.put("sub_merchant_id", subMerchantId);
            }
        } catch (EntityNotFoundException e) {
            log.info("No sub-merchant configured for restaurant {}, proceeding as parent-merchant payment", restaurantId);
        } catch (Exception e) {
            log.warn("Error looking up sub-merchant for restaurant {}: {}", restaurantId, e.getMessage(), e);
        }

        if (!data.containsKey("phone") || data.get("phone").isBlank()) {
            data.put("phone", "9000000000");
        }
        if (!data.containsKey("email") || data.get("email").isBlank()) {
            data.put("email", "info@khanabook.in");
        }

        log.info("Initiating Easebuzz payment for FSSAI renewal restaurantId={} txnid={} amount={}",
                restaurantId, txnid, amountStr);
        Map<String, Object> result = easebuzzApi.initiatePayment(data);
        String status = (String) result.getOrDefault("status", "failure");

        if ("success".equalsIgnoreCase(status)) {
            String accessToken = (String) result.get("access_token");
            String paymentUrl = (String) result.get("payment_url");
            return Map.of(
                    "status", "success",
                    "txnid", txnid,
                    "access_token", accessToken != null ? accessToken : "",
                    "payment_url", paymentUrl != null ? paymentUrl : "",
                    "amount", amount,
                    "pay_mode", props.getPayMode()
            );
        }

        renewal.setStatus("FAILED");
        fssaiRenewalRepo.save(renewal);
        return Map.of("status", "failure", "error", result.getOrDefault("error", "Payment initiation failed"));
    }

    @Transactional
    public Map<String, Object> createPaymentLink(Map<String, Object> request) {
        Long restaurantId = ((Number) request.get("restaurantId")).longValue();
        String amount = request.get("amount").toString();
        String customerName = (String) request.get("customerName");
        String customerEmail = (String) request.get("customerEmail");
        String customerPhone = (String) request.get("customerPhone");
        String message = (String) request.get("message");
        String merchantTxn = (String) request.get("merchantTxn");
        if (merchantTxn == null || merchantTxn.isBlank()) {
            merchantTxn = "PL" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }

        // Look up sub-merchant
        String subMerchantId = null;
        String subMerchantEmail = null;
        String subMerchantPhone = null;
        try {
            EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
            String subMerchantIdFromSm = sm.getSubMerchantId();
            if (subMerchantIdFromSm != null && !subMerchantIdFromSm.isBlank()
                    && ("ACTIVE".equals(sm.getStatus()) || "test".equalsIgnoreCase(props.getPayMode()))) {
                subMerchantId = subMerchantIdFromSm;
            }
            if (sm.getContactEmail() != null) subMerchantEmail = sm.getContactEmail();
            if (sm.getContactPhone() != null) subMerchantPhone = sm.getContactPhone();
        } catch (EntityNotFoundException e) {
            log.info("No sub-merchant configured for restaurant {}, proceeding as parent-merchant payment", restaurantId);
        } catch (Exception e) {
            log.warn("Error looking up sub-merchant for restaurant {}: {}", restaurantId, e.getMessage(), e);
        }

        String email = customerEmail != null && !customerEmail.isBlank() ? customerName : subMerchantEmail;
        String phone = customerPhone != null && !customerPhone.isBlank() ? customerPhone : subMerchantPhone;
        if (email == null || email.isBlank()) email = "customer@khanabook.in";
        if (phone == null || phone.isBlank()) phone = "9000000000";
        if (customerName == null || customerName.isBlank()) customerName = "Customer";

        // Build data for Easy Collect
        Map<String, String> data = new HashMap<>();
        data.put("merchant_txn", merchantTxn);
        data.put("name", customerName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("amount", amount);
        data.put("message", message != null ? message : "Payment for KhanaBook order");
        data.put("udf1", request.getOrDefault("udf1", "").toString());
        data.put("udf2", restaurantId.toString());
        data.put("udf3", request.getOrDefault("udf3", "").toString());
        data.put("udf4", request.getOrDefault("udf4", "").toString());
        data.put("udf5", request.getOrDefault("udf5", "").toString());
        if (subMerchantId != null) {
            data.put("sub_merchant_id", subMerchantId);
        }

        // Optional: restrict payment modes
        String showPaymentMode = (String) request.get("show_payment_mode");
        if (showPaymentMode != null && !showPaymentMode.isBlank()) {
            data.put("show_payment_mode", showPaymentMode);
        }

        log.info("Creating Easebuzz payment link restaurantId={} merchantTxn={} amount={}", restaurantId, merchantTxn, amount);
        Map<String, Object> result = easebuzzApi.createPaymentLink(data);
        return result;
    }

    /**
     * Creates a payment link for an existing bill, pulling customer/amount data from the bill entity.
     * This is used by the New Bill → Payment → "Send Payment Link" flow.
     * The link is tied to the bill via udf1=billId so the webhook can reconcile it.
     */
    @Transactional
    public Map<String, Object> createPaymentLinkForBill(Long billId, Long restaurantId) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));

        // Block if already paid
        if ("paid".equalsIgnoreCase(bill.getPaymentStatus()) || "success".equalsIgnoreCase(bill.getPaymentStatus())) {
            log.warn("Blocked payment link creation for already paid billId={}", billId);
            return Map.of("status", "failure", "code", "ALREADY_PAID",
                    "error", "Bill is already paid.");
        }

        // Block if a link was already generated and is still active
        if (bill.getGatewayTxnId() != null && !bill.getGatewayTxnId().isBlank()
                && "link_sent".equalsIgnoreCase(bill.getPaymentStatus())) {
            log.info("Payment link already exists for billId={} merchantTxn={}", billId, bill.getGatewayTxnId());
            return Map.of("status", "success", "code", "LINK_EXISTS",
                    "payment_url", "", // Client should poll status
                    "merchant_txn", bill.getGatewayTxnId(),
                    "message", "Payment link already sent for this bill.");
        }

        String amount = String.format("%.2f", bill.getTotalAmount());
        String customerName = bill.getCustomerName() != null
                ? bill.getCustomerName().replaceAll("[^a-zA-Z0-9 ]", "").trim()
                : "Customer";
        String customerPhone = bill.getCustomerWhatsapp() != null ? bill.getCustomerWhatsapp() : "";
        String message = "Payment for KhanaBook Order "
                + (bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : "#" + billId);

        // Generate unique merchant_txn (max 20 chars for Easebuzz)
        String txnSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String billTail = String.format("%05d", billId % 100000);
        String merchantTxn = "PL" + billTail + txnSuffix; // 15 chars total

        // Build request map — reuse existing createPaymentLink infrastructure
        Map<String, Object> request = new HashMap<>();
        request.put("restaurantId", restaurantId);
        request.put("amount", amount);
        request.put("customerName", customerName);
        request.put("customerEmail", ""); // Fallback handled inside createPaymentLink
        request.put("customerPhone", customerPhone);
        request.put("message", message);
        request.put("merchantTxn", merchantTxn);
        request.put("udf1", billId.toString());  // CRITICAL: webhook uses this to find the bill
        request.put("show_payment_mode", "CC,DC,NB,UPI,WALLET"); // Exclude QR per product requirement

        Map<String, Object> result = createPaymentLink(request);

        String status = (String) result.getOrDefault("status", "failure");
        if ("success".equalsIgnoreCase(status)) {
            // Store merchant_txn on the bill so webhook can reconcile
            bill.setGatewayTxnId(merchantTxn);
            bill.setGatewayStatus("link_created");
            bill.setPaymentStatus("link_sent");
            bill.setPaymentMode("payment_link");
            billRepo.save(bill);
            log.info("Payment link created for billId={} merchantTxn={} amount={}", billId, merchantTxn, amount);
        } else {
            log.warn("Payment link creation failed for billId={}: {}", billId, result);
        }

        return result;
    }

    private void saveGatewayEventIfPresent(Bill bill, String easebuzzId, String status) {
        if (easebuzzId == null || easebuzzId.isBlank() || bill.getGatewayTxnId() == null) {
            return;
        }
        EasebuzzWebhookEvent event = webhookEventRepo
                .findByRestaurantIdAndTxnId(bill.getRestaurantId(), bill.getGatewayTxnId())
                .orElseGet(EasebuzzWebhookEvent::new);
        event.setRestaurantId(bill.getRestaurantId());
        event.setTxnId(bill.getGatewayTxnId());
        event.setEasebuzzId(easebuzzId);
        event.setStatus(status);
        event.setAmount(bill.getTotalAmount());
        event.setRawPayload("payment_status_lookup");
        event.setReceivedAt(System.currentTimeMillis());
        webhookEventRepo.save(event);
    }

    /**
     * Polls Easebuzz /transaction/v2.1/retrieve for the given txnid and extracts the
     * transaction status string. Returns "unknown" if the API call fails or response
     * is unparseable — callers treat "unknown" as safe-to-clear (same as terminal).
     */
    @SuppressWarnings("unchecked")
    private String pollOldTxnStatus(String txnid) {
        try {
            Map<String, Object> raw = easebuzzApi.getTransactionStatus(txnid);
            if (!toBool(raw.get("status"))) {
                log.warn("Easebuzz status poll API failure for txnid={}: {}", txnid, raw);
                return "unknown";
            }
            Object msgObj = raw.get("msg");
            if (msgObj == null) {
                return "unknown";
            }
            Map<String, Object> txnData;
            if (msgObj instanceof List) {
                List<Map<String, Object>> msgList = (List<Map<String, Object>>) msgObj;
                if (msgList.isEmpty()) return "unknown";
                txnData = msgList.get(0);
            } else if (msgObj instanceof Map) {
                txnData = (Map<String, Object>) msgObj;
            } else {
                return "unknown";
            }
            String status = str(txnData.getOrDefault("status", "unknown"));
            return status.isBlank() ? "unknown" : status;
        } catch (Exception e) {
            log.warn("Exception polling old txnid={} status: {}", txnid, e.getMessage());
            return "unknown";
        }
    }

    private String str(Object value) {
        return value != null ? value.toString() : "";
    }

    /**
     * Re-initiate payment with the same txnid to get a fresh access token/URL.
     * Easebuzz allows re-initiating the same txnid if it hasn't been paid yet —
     * this returns a new access key pointing to the same transaction.
     */
    private Map<String, Object> reinitiateExistingOrder(Bill bill, Long restaurantId) {
        String txnid = bill.getGatewayTxnId();
        String amount = String.format("%.2f", bill.getTotalAmount());
        String productinfo = "KhanaBook Order " +
            (bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : bill.getId().toString());
        String firstname = bill.getCustomerName() != null
            ? bill.getCustomerName().replaceAll("[^a-zA-Z0-9 ]", "").trim()
            : "Customer";
        String phone = bill.getCustomerWhatsapp() != null ? bill.getCustomerWhatsapp() : "";

        Map<String, String> data = new HashMap<>();
        data.put("txnid", txnid);
        data.put("amount", amount);
        data.put("productinfo", productinfo);
        data.put("firstname", firstname);
        data.put("surl", props.getReturnUrl());
        data.put("furl", props.getReturnUrl());

        try {
            EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
            if (sm.getSubMerchantId() != null && !sm.getSubMerchantId().isBlank()
                    && ("ACTIVE".equals(sm.getStatus()) || "test".equalsIgnoreCase(props.getPayMode()))) {
                data.put("sub_merchant_id", sm.getSubMerchantId());
            }
            if (sm.getContactEmail() != null) data.put("email", sm.getContactEmail());
            if (phone.isBlank() && sm.getContactPhone() != null) phone = sm.getContactPhone();
        } catch (EntityNotFoundException e) {
            log.info("No sub-merchant configured for restaurant {}, proceeding as parent-merchant payment", restaurantId);
        } catch (Exception e) {
            log.warn("Error looking up sub-merchant for restaurant {}: {}", restaurantId, e.getMessage(), e);
        }

        data.put("phone", phone);
        if (!data.containsKey("email") || data.get("email") == null || data.get("email").isBlank()) {
            data.put("email", "customer@khanabook.in");
        }
        data.put("udf1", bill.getId().toString());
        data.put("udf2", restaurantId.toString());

        log.info("Re-initiating Easebuzz payment (idempotent) billId={} txnid={}", bill.getId(), txnid);
        Map<String, Object> result = easebuzzApi.initiatePayment(data);
        String status = (String) result.getOrDefault("status", "failure");

        if ("success".equalsIgnoreCase(status)) {
            String accessToken = (String) result.get("access_token");
            String paymentUrl = (String) result.get("payment_url");
            return Map.of(
                "status", "success",
                "txnid", txnid,
                "access_token", accessToken != null ? accessToken : "",
                "payment_url", paymentUrl != null ? paymentUrl : "",
                "amount", bill.getTotalAmount(),
                "pay_mode", props.getPayMode()
            );
        }

        // If re-initiation fails, clear stale txnid and let a fresh order be created on next attempt
        log.warn("Re-initiation failed for billId={} txnid={}, clearing stale gateway data", bill.getId(), txnid);
        bill.setGatewayTxnId(null);
        bill.setGatewayStatus(null);
        billRepo.save(bill);
        return Map.of("status", "failure", "error", result.getOrDefault("error", "Payment re-initiation failed. Please retry."));
    }
}
