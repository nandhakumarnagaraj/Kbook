package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzWebhookEvent;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EasebuzzWebhookService {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzWebhookService.class);
    private final BillRepository billRepo;
    private final SubMerchantService subMerchantService;
    private final EasebuzzWebhookEventRepository webhookEventRepo;
    private final EasebuzzProperties props;
    private final PostSplitService postSplitService;

    @Transactional
    public Map<String, Object> handlePaymentWebhook(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String status = payload.get("status");

        // Verify webhook hash before processing
        if (!verifyWebhookHash(payload)) {
            log.warn("Payment webhook hash mismatch txnid={}", txnid);
            return Map.of("status", "hash_mismatch");
        }

        String easebuzzId = payload.getOrDefault("easebuzz_id", payload.get("easepayid"));
        String amountStr = payload.get("amount");
        String udf1 = payload.get("udf1");

        log.info("Payment webhook received txnid={} status={}", txnid, status);
        if ("success".equalsIgnoreCase(status) && udf1 != null) {
            try {
                Long billId = Long.parseLong(udf1);
                billRepo.findById(billId).ifPresent(bill -> {
                    bill.setGatewayTxnId(txnid);
                    bill.setGatewayStatus("success");
                    bill.setPaymentStatus("paid");
                    bill.setPaidAt(System.currentTimeMillis());
                    if (amountStr != null) {
                        bill.setSettledAmount(new BigDecimal(amountStr));
                    }
                    billRepo.save(bill);
                    saveGatewayEvent(bill, txnid, easebuzzId, status, amountStr, payload);
                    log.info("Bill {} marked paid via webhook txnid={}", billId, txnid);

                    // Trigger post-transaction split
                    if (easebuzzId != null && !easebuzzId.isBlank()) {
                        postSplitService.createPostSplitAsync(billId, easebuzzId, txnid);
                    } else {
                        log.warn("Post-split skipped for billId={} : missing easebuzz_id", billId);
                    }
                });
            } catch (NumberFormatException e) {
                log.warn("Invalid billId in webhook udf1: {}", udf1);
            }
        }
        return Map.of("status", "received");
    }

    private void saveGatewayEvent(Bill bill, String txnid, String easebuzzId, String status,
                                  String amountStr, Map<String, String> payload) {
        if (txnid == null || txnid.isBlank()) {
            return;
        }
        EasebuzzWebhookEvent event = webhookEventRepo
                .findByRestaurantIdAndTxnId(bill.getRestaurantId(), txnid)
                .orElseGet(EasebuzzWebhookEvent::new);
        event.setRestaurantId(bill.getRestaurantId());
        event.setTxnId(txnid);
        event.setEasebuzzId(easebuzzId);
        event.setStatus(status != null ? status : "");
        if (amountStr != null && !amountStr.isBlank()) {
            event.setAmount(new BigDecimal(amountStr));
        }
        event.setRawPayload(payload.toString());
        event.setReceivedAt(System.currentTimeMillis());
        webhookEventRepo.save(event);
    }

    @Transactional
    public Map<String, Object> handleRefundWebhook(Map<String, String> payload) {
        String txnid = payload.get("txnid");

        // Verify webhook hash before processing
        if (!verifyWebhookHash(payload)) {
            log.warn("Refund webhook hash mismatch txnid={}", txnid);
            return Map.of("status", "hash_mismatch");
        }

        String status = payload.get("status");
        log.info("Refund webhook received txnid={} status={}", txnid, status);
        billRepo.findByGatewayTxnId(txnid).ifPresent(bill -> {
            bill.setGatewayStatus("refunded_" + status);
            billRepo.save(bill);
        });
        return Map.of("status", "received");
    }

    @Transactional
    public Map<String, Object> handlePayoutWebhook(Map<String, String> payload) {
        String payoutId = payload.get("payout_id");
        String status = payload.get("status");

        // Payout webhook hash verification: key|payout_id|salt
        if (!verifyPayoutHash(payload)) {
            log.warn("Payout webhook hash mismatch payoutId={}", payoutId);
            return Map.of("status", "hash_mismatch");
        }

        log.info("Payout webhook received payoutId={} status={}", payoutId, status);
        // Logic to update internal settlement/payout records could go here
        return Map.of("status", "received");
    }

    public void handleSubMerchantWebhook(Map<String, Object> payload) {
        subMerchantService.processWebhook(payload);
    }

    private boolean verifyWebhookHash(Map<String, String> payload) {
        try {
            String receivedHash = payload.get("hash");
            if (receivedHash == null || receivedHash.isBlank()) {
                return false;
            }

            // Easebuzz Payment Webhook Hash Sequence (Reversed):
            // sha512(salt|status|udf10|udf9|udf8|udf7|udf6|udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
            StringBuilder sb = new StringBuilder();
            sb.append(props.getSalt()).append("|");
            sb.append(nullSafe(payload.get("status"))).append("|");
            sb.append(nullSafe(payload.get("udf10"))).append("|");
            sb.append(nullSafe(payload.get("udf9"))).append("|");
            sb.append(nullSafe(payload.get("udf8"))).append("|");
            sb.append(nullSafe(payload.get("udf7"))).append("|");
            sb.append(nullSafe(payload.get("udf6"))).append("|");
            sb.append(nullSafe(payload.get("udf5"))).append("|");
            sb.append(nullSafe(payload.get("udf4"))).append("|");
            sb.append(nullSafe(payload.get("udf3"))).append("|");
            sb.append(nullSafe(payload.get("udf2"))).append("|");
            sb.append(nullSafe(payload.get("udf1"))).append("|");
            sb.append(nullSafe(payload.get("email"))).append("|");
            sb.append(nullSafe(payload.get("firstname"))).append("|");
            sb.append(nullSafe(payload.get("productinfo"))).append("|");
            sb.append(nullSafe(payload.get("amount"))).append("|");
            sb.append(nullSafe(payload.get("txnid"))).append("|");
            sb.append(props.getMerchantKey());

            String computedHash = sha512(sb.toString());
            return computedHash.equalsIgnoreCase(receivedHash);
        } catch (Exception e) {
            log.error("Payment hash verification failed", e);
            return false;
        }
    }

    private boolean verifyPayoutHash(Map<String, String> payload) {
        try {
            String receivedHash = payload.get("hash");
            if (receivedHash == null || receivedHash.isBlank()) return false;

            // Scenario 1: Settlement Payout Webhook (key|payout_id|salt)
            if (payload.containsKey("payout_id") && !payload.containsKey("beneficiary_account_number")) {
                StringBuilder sb = new StringBuilder();
                sb.append(props.getMerchantKey()).append("|");
                sb.append(nullSafe(payload.get("payout_id"))).append("|");
                sb.append(props.getSalt());
                if (sha512(sb.toString()).equalsIgnoreCase(receivedHash)) return true;
            }

            // Scenario 2: Transfer (Payout V2) Webhook 
            // Hash: key|beneficiary_account_number|ifsc|beneficiary_upi_handle|unique_request_number|amount|unique_transaction_reference|status|salt
            StringBuilder sb = new StringBuilder();
            sb.append(props.getMerchantKey()).append("|");
            sb.append(nullSafe(payload.get("beneficiary_account_number"))).append("|");
            sb.append(nullSafe(payload.get("ifsc"))).append("|");
            sb.append(nullSafe(payload.get("beneficiary_upi_handle"))).append("|");
            sb.append(nullSafe(payload.get("unique_request_number"))).append("|");
            sb.append(nullSafe(payload.get("amount"))).append("|");
            sb.append(nullSafe(payload.get("unique_transaction_reference"))).append("|");
            sb.append(nullSafe(payload.get("status"))).append("|");
            sb.append(props.getSalt());

            return sha512(sb.toString()).equalsIgnoreCase(receivedHash);
        } catch (Exception e) {
            log.error("Payout hash verification failed", e);
            return false;
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String sha512(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte b : digest) {
            hash.append(String.format("%02x", b));
        }
        return hash.toString();
    }
}
