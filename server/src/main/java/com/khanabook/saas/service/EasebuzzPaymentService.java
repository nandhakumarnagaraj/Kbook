package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.BillRepository;
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
        data.put("email", "");
        data.put("phone", bill.getCustomerWhatsapp() != null ? bill.getCustomerWhatsapp() : "");
        data.put("sub_merchant_id", sm.getSubMerchantId());
        data.put("udf1", billId.toString());
        data.put("udf2", restaurantId.toString());
        data.put("udf3", "");
        data.put("udf4", "");
        data.put("udf5", "");

        Map result = easebuzzApi.initiatePayment(data);
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
        Map result = easebuzzApi.getTransactionStatus(bill.getGatewayTxnId());
        String easebuzzStatus = (String) result.getOrDefault("status", "failure");

        if ("success".equalsIgnoreCase(easebuzzStatus)) {
            String easebuzzId = (String) result.get("easebuzz_id");
            bill.setGatewayStatus("success");
            bill.setPaymentStatus("paid");
            bill.setPaidAt(System.currentTimeMillis());
            billRepo.save(bill);
            return Map.of("status", "success", "easebuzz_id", easebuzzId, "txnid", bill.getGatewayTxnId());
        }
        bill.setGatewayStatus(easebuzzStatus);
        billRepo.save(bill);
        return Map.of("status", easebuzzStatus, "txnid", bill.getGatewayTxnId());
    }

    @Transactional
    public Map<String, Object> initiateRefund(Long billId, BigDecimal amount, String reason) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));
        if (bill.getGatewayTxnId() == null) {
            return Map.of("status", "failure", "error", "No gateway transaction found for refund");
        }
        Map result = easebuzzApi.initiateRefund(
            bill.getGatewayTxnId(),
            bill.getTotalAmount().toString(),
            amount.toString(),
            reason
        );
        String status = (String) result.getOrDefault("status", "failure");
        log.info("Refund initiated billId={} amount={} status={}", billId, amount, status);
        return Map.of("status", status, "easebuzz_refund_id", result.getOrDefault("easebuzz_refund_id", ""));
    }
}
