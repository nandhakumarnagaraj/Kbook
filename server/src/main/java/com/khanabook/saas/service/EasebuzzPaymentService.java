package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzWebhookEvent;
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

    @Transactional
    public Map<String, Object> createOrder(Long billId, Long restaurantId) {
        Bill bill = billRepo.findById(billId)
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

        Map<String, Object> fraudScore = chargebackService.scoreTransaction(billId);
        String risk = (String) fraudScore.get("risk");
        double score = ((Number) fraudScore.get("score")).doubleValue();
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
        } catch (RuntimeException e) {
            log.info("No sub-merchant configured for restaurant {}, proceeding as parent-merchant payment", restaurantId);
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

        // Generate unique merchant refund reference
        String merchantRefundId = "REF" + billId + "_" + System.currentTimeMillis();

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

    @Transactional
    public Map<String, Object> cancelTransaction(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found");
        }
        return easebuzzApi.cancelTransaction(bill.getGatewayTxnId(), bill.getTotalAmount().toString());
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

    private String str(Object value) {
        return value != null ? value.toString() : "";
    }
}
