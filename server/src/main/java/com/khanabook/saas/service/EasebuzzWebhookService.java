package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
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
    private final EasebuzzProperties props;

    @Transactional
    public Map<String, Object> handlePaymentWebhook(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String status = payload.get("status");

        // Verify webhook hash before processing
        if (!verifyWebhookHash(payload)) {
            log.warn("Payment webhook hash mismatch txnid={}", txnid);
            return Map.of("status", "hash_mismatch");
        }

        String easebuzzId = payload.get("easebuzz_id");
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
                    log.info("Bill {} marked paid via webhook txnid={}", billId, txnid);
                });
            } catch (NumberFormatException e) {
                log.warn("Invalid billId in webhook udf1: {}", udf1);
            }
        }
        return Map.of("status", "received");
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

    public void handleSubMerchantWebhook(Map<String, Object> payload) {
        subMerchantService.processWebhook(payload);
    }

    private boolean verifyWebhookHash(Map<String, String> payload) {
        try {
            String receivedHash = payload.get("hash");
            if (receivedHash == null || receivedHash.isBlank()) {
                return false;
            }

            // Build hash string: key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|||||||salt
            StringBuilder sb = new StringBuilder();
            sb.append(props.getMerchantKey()).append("|");
            sb.append(nullSafe(payload.get("txnid"))).append("|");
            sb.append(nullSafe(payload.get("amount"))).append("|");
            sb.append(nullSafe(payload.get("productinfo"))).append("|");
            sb.append(nullSafe(payload.get("firstname"))).append("|");
            sb.append(nullSafe(payload.get("email"))).append("|");
            sb.append(nullSafe(payload.get("udf1"))).append("|");
            sb.append(nullSafe(payload.get("udf2"))).append("|");
            sb.append(nullSafe(payload.get("udf3"))).append("|");
            sb.append(nullSafe(payload.get("udf4"))).append("|");
            sb.append(nullSafe(payload.get("udf5"))).append("|");
            sb.append("|||||"); // empty udf6-udf10
            sb.append(props.getSalt());

            String computedHash = sha512(sb.toString());
            return computedHash.equalsIgnoreCase(receivedHash);
        } catch (Exception e) {
            log.error("Hash verification failed", e);
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
