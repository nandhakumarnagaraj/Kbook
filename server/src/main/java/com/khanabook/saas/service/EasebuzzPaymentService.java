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

    @Transactional
    public Map<String, Object> createOrder(Long billId, Long restaurantId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));

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
            if (sm.getSubMerchantId() != null) {
                data.put("sub_merchant_id", sm.getSubMerchantId());
            }
            if (sm.getContactEmail() != null) {
                data.put("email", sm.getContactEmail());
            }
            if (phone.isBlank() && sm.getContactPhone() != null) {
                phone = sm.getContactPhone();
            }
            if (!"ACTIVE".equals(sm.getStatus())) {
                log.warn("Sub-merchant status is {}, proceeding with subMerchantId={}", sm.getStatus(), sm.getSubMerchantId());
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

        log.debug("Initiating Easebuzz payment with full payload: {}", data);
        Map<String, Object> result = easebuzzApi.initiatePayment(data);
        String status = (String) result.getOrDefault("status", "failure");

        bill.setGatewayTxnId(txnid);
        bill.setGatewayStatus(status);
        billRepo.save(bill);

        if ("success".equalsIgnoreCase(status)) {
            String accessToken = (String) result.get("access_token");
            String paymentUrl = (String) result.get("payment_url");
            log.info("Payment order created billId={} txnid={}", billId, txnid);
            return Map.of(
                "status", "success",
                "txnid", txnid,
                "access_token", accessToken != null ? accessToken : "",
                "payment_url", paymentUrl != null ? paymentUrl : "",
                "amount", bill.getTotalAmount()
            );
        }
        log.warn("Payment order creation failed billId={} response={}", billId, result);
        return Map.of("status", "failure", "error", result.getOrDefault("error", "Payment initiation failed"));
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> verifyPayment(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found");
        }
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
            return Map.of("status", "success", "easebuzz_id", easebuzzId, "txnid", bill.getGatewayTxnId());
        }

        bill.setGatewayStatus(easebuzzStatus);
        billRepo.save(bill);
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

        Map<String, Object> result = easebuzzApi.initiateRefund(txnid, amount.toString());
        log.info("Refund initiation response for billId={}: {}", billId, result);

        boolean success = toBool(result.get("status"));
        if (success) {
            Object msgObj = result.get("msg");
            Map<String, Object> msg = msgObj instanceof Map ? (Map<String, Object>) msgObj : null;
            String ebRefundId = msg != null ? str(msg.get("refund_id")) : "";
            bill.setRefundId(ebRefundId != null && !ebRefundId.isBlank() ? ebRefundId : "REF_" + txnid);
            bill.setRefundAmount(amount);
            bill.setGatewayStatus("refund_initiated");
            billRepo.save(bill);

            return Map.of(
                "status", "success",
                "easebuzz_refund_id", bill.getRefundId(),
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
