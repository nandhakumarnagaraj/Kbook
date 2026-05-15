package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EasebuzzWebhookService {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzWebhookService.class);
    private final BillRepository billRepo;
    private final SubMerchantService subMerchantService;

    @Transactional
    public Map<String, Object> handlePaymentWebhook(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String easebuzzId = payload.get("easebuzz_id");
        String status = payload.get("status");
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
        String status = payload.get("status");
        log.info("Refund webhook received txnid={} status={}", txnid, status);
        billRepo.findByGatewayTxnId(txnid).ifPresent(bill -> {
            bill.setGatewayStatus("refunded_" + status);
            billRepo.save(bill);
        });
        return Map.of("status", "received");
    }

    public void handleSubMerchantWebhook(Map<String, String> payload) {
        subMerchantService.processWebhook(payload);
    }
}
