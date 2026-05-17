package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
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

    @Transactional
    public Map<String, Object> createOrder(Long billId, Long restaurantId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        EasebuzzSubMerchant sm = subMerchantService.getByRestaurantId(restaurantId);
        if (!"ACTIVE".equals(sm.getStatus())) {
            throw new RuntimeException("Sub-merchant not active. Status: " + sm.getStatus());
        }
        String txnid = "KB" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);

        Map<String, String> data = new HashMap<>();
        data.put("txnid", txnid);
        data.put("amount", bill.getTotalAmount().toString());
        data.put("productinfo", "Restaurant Bill #" + bill.getDailyOrderDisplay());
        data.put("firstname", bill.getCustomerName() != null ? bill.getCustomerName() : "Customer");
        data.put("email", sm.getContactEmail() != null ? sm.getContactEmail() : "customer@kbook.com");
        data.put("phone", bill.getCustomerWhatsapp() != null ? bill.getCustomerWhatsapp() : "");
        data.put("udf1", billId.toString());
        data.put("udf2", restaurantId.toString());
        data.put("udf3", "");
        data.put("udf4", "");
        data.put("udf5", "");

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

    @Transactional
    public Map<String, Object> verifyPayment(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found");
        }
        Map<String, Object> result = easebuzzApi.getTransactionStatus(bill.getGatewayTxnId());
        String easebuzzStatus = str(result.getOrDefault("status", "failure"));

        if ("success".equalsIgnoreCase(easebuzzStatus)) {
            String easebuzzId = str(result.get("easebuzz_id"));
            bill.setGatewayStatus("success");
            bill.setPaymentStatus("paid");
            bill.setPaidAt(System.currentTimeMillis());
            billRepo.save(bill);
            saveGatewayEventIfPresent(bill, easebuzzId, "success");
            return Map.of("status", "success", "easebuzz_id", nullToEmpty(easebuzzId), "txnid", bill.getGatewayTxnId());
        }
        bill.setGatewayStatus(easebuzzStatus);
        billRepo.save(bill);
        return Map.of("status", easebuzzStatus, "txnid", bill.getGatewayTxnId());
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> initiateRefund(Long billId, BigDecimal amount, String reason) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found for refund");
        }
        String easebuzzId = resolveEasebuzzId(bill);
        if (easebuzzId == null || easebuzzId.isBlank()) {
            return Map.of("status", "failure", "error", "Easebuzz transaction ID not found. Verify payment status first.");
        }
        String merchantRefundId = "KB_REFUND_" + billId + "_" + System.currentTimeMillis();

        Map<String, Object> result = easebuzzApi.initiateRefund(easebuzzId, merchantRefundId, amount.toString());
        log.info("Refund initiation response for billId={}: {}", billId, result);

        boolean success = toBool(result.get("status"));
        if (success) {
            Map<String, Object> msg = (Map<String, Object>) result.get("msg");
            String ebRefundId = msg != null ? str(msg.get("refund_id")) : "";
            bill.setRefundId(ebRefundId != null && !ebRefundId.isBlank() ? ebRefundId : merchantRefundId);
            bill.setRefundAmount(amount);
            bill.setGatewayStatus("refund_initiated");
            billRepo.save(bill);

            return Map.of(
                "status", "success",
                "easebuzz_refund_id", bill.getRefundId(),
                "merchant_refund_id", merchantRefundId,
                "easebuzz_id", easebuzzId
            );
        }

        return Map.of("status", "failure", "error", result.getOrDefault("error", "Refund initiation failed"));
    }

    @Transactional
    public Map<String, Object> getRefundStatus(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
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
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found");
        }
        return easebuzzApi.cancelTransaction(bill.getGatewayTxnId(), bill.getTotalAmount().toString());
    }

    private String resolveEasebuzzId(Bill bill) {
        return webhookEventRepo.findByRestaurantIdAndTxnId(bill.getRestaurantId(), bill.getGatewayTxnId())
                .map(EasebuzzWebhookEvent::getEasebuzzId)
                .filter(id -> id != null && !id.isBlank())
                .orElseGet(() -> {
                    Map<String, Object> result = easebuzzApi.getTransactionStatus(bill.getGatewayTxnId());
                    if (!"success".equalsIgnoreCase(str(result.getOrDefault("status", "")))) {
                        return null;
                    }
                    String easebuzzId = str(result.get("easebuzz_id"));
                    saveGatewayEventIfPresent(bill, easebuzzId, "success");
                    return easebuzzId;
                });
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

    private String nullToEmpty(Object value) {
        return value != null ? value.toString() : "";
    }
}
