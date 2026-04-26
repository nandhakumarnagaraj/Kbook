package com.khanabook.saas.payment.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.payment.dto.CreateEasebuzzOrderRequest;
import com.khanabook.saas.payment.dto.CreateEasebuzzOrderResponse;
import com.khanabook.saas.payment.dto.EasebuzzPaymentStatusResponse;
import com.khanabook.saas.payment.entity.*;
import com.khanabook.saas.payment.repository.PaymentRepository;
import com.khanabook.saas.payment.repository.PaymentWebhookLogRepository;
import com.khanabook.saas.repository.BillPaymentRepository;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EasebuzzPaymentService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookLogRepository webhookLogRepository;
    private final RestaurantPaymentConfigService paymentConfigService;
    private final EasebuzzHashService hashService;
    private final EasebuzzGatewayClient gatewayClient;

    @Transactional
    public CreateEasebuzzOrderResponse createOrder(
            Long restaurantId,
            String deviceId,
            Long userId,
            CreateEasebuzzOrderRequest request
    ) {
        Bill bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(restaurantId, deviceId, request.getBillId())
                .orElseThrow(() -> new IllegalArgumentException("Bill not found for this device"));

        if (paymentRepository.existsByRestaurantIdAndBillIdAndPaymentStatus(restaurantId, bill.getId(), PaymentStatus.SUCCESS)) {
            throw new IllegalArgumentException("Bill is already paid");
        }

        if ("success".equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new IllegalArgumentException("Bill is already marked as paid");
        }

        RestaurantPaymentConfig config = paymentConfigService.getActiveConfig(restaurantId);
        String salt = paymentConfigService.decryptSalt(config);
        String amount = bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String txnId = buildTxnId(restaurantId, request.getBillId());
        String firstName = bill.getCustomerName() == null || bill.getCustomerName().isBlank()
                ? "Customer" : bill.getCustomerName();
        String email = "noreply@khanabook.app";
        String phone = bill.getCustomerWhatsapp() == null || bill.getCustomerWhatsapp().isBlank()
                ? "0000000000" : bill.getCustomerWhatsapp();
        String requestHash = hashService.buildRequestHash(
                config.getMerchantKey(),
                txnId,
                amount,
                "Bill payment",
                firstName,
                email,
                salt
        );

        EasebuzzGatewayClient.GatewaySession session = gatewayClient.createSession(
                config.getEnvironment(),
                config.getMerchantKey(),
                txnId,
                amount,
                "Bill payment",
                firstName,
                email,
                phone,
                requestHash
        );

        long now = System.currentTimeMillis();
        Payment payment = new Payment();
        payment.setRestaurantId(restaurantId);
        payment.setBillId(bill.getId());
        payment.setUserId(userId);
        payment.setAmount(bill.getTotalAmount());
        payment.setCurrency("INR");
        payment.setGateway(PaymentGateway.EASEBUZZ);
        payment.setGatewayTxnId(txnId);
        payment.setGatewayStatus("initiated");
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(request.getPaymentMethod().trim().toUpperCase());
        payment.setCheckoutUrl(session.getCheckoutUrl());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

        return CreateEasebuzzOrderResponse.builder()
                .paymentId(payment.getId())
                .billId(request.getBillId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .gateway(payment.getGateway().name())
                .gatewayTxnId(payment.getGatewayTxnId())
                .checkoutUrl(payment.getCheckoutUrl())
                .build();
    }

    @Transactional(readOnly = true)
    public EasebuzzPaymentStatusResponse getStatus(Long restaurantId, String deviceId, Long localBillId) {
        Bill bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(restaurantId, deviceId, localBillId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found for this device"));
        Payment payment = paymentRepository.findTopByRestaurantIdAndBillIdOrderByCreatedAtDesc(restaurantId, bill.getId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for bill"));
        return EasebuzzPaymentStatusResponse.builder()
                .billId(localBillId)
                .paymentId(payment.getId())
                .paymentStatus(payment.getPaymentStatus().name())
                .gatewayTxnId(payment.getGatewayTxnId())
                .amount(payment.getAmount())
                .message(messageFor(payment.getPaymentStatus()))
                .build();
    }

    @Transactional
    public Map<String, Object> processGatewayCallback(Map<String, String> params) {
        String txnId = params.getOrDefault("txnid", "");
        Payment payment = paymentRepository.findByRestaurantIdAndGatewayTxnId(
                        resolveRestaurantId(params.getOrDefault("key", "")), txnId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for transaction"));

        RestaurantPaymentConfig config = paymentConfigService.getActiveConfig(payment.getRestaurantId());
        String salt = paymentConfigService.decryptSalt(config);
        String expectedHash = hashService.buildWebhookHash(
                salt,
                params.getOrDefault("status", ""),
                params.getOrDefault("email", ""),
                params.getOrDefault("firstname", ""),
                params.getOrDefault("productinfo", ""),
                params.getOrDefault("amount", ""),
                txnId,
                params.getOrDefault("key", "")
        );
        boolean signatureValid = expectedHash.equalsIgnoreCase(params.getOrDefault("hash", ""));

        PaymentWebhookLog log = new PaymentWebhookLog();
        log.setPaymentId(payment.getId());
        log.setGateway(PaymentGateway.EASEBUZZ);
        log.setTxnId(txnId);
        log.setPayload(params.toString());
        log.setSignatureValid(signatureValid);
        log.setProcessed(false);
        log.setReceivedAt(System.currentTimeMillis());
        webhookLogRepository.save(log);

        if (!signatureValid) {
            throw new org.springframework.security.access.AccessDeniedException("Webhook hash mismatch");
        }

        String gatewayStatus = params.getOrDefault("status", "pending");
        PaymentStatus finalStatus = mapGatewayStatus(gatewayStatus);
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && finalStatus != PaymentStatus.SUCCESS) {
            logProcessed(log);
            return Map.of("ok", true, "ignored", true);
        }

        payment.setGatewayStatus(gatewayStatus);
        payment.setGatewayPaymentId(params.get("easepayid"));
        payment.setUpdatedAt(System.currentTimeMillis());
        payment.setPaymentStatus(finalStatus);
        payment.setVerifiedAt(System.currentTimeMillis());
        if (finalStatus != PaymentStatus.SUCCESS) {
            payment.setFailureReason(gatewayStatus);
        } else {
            payment.setFailureReason(null);
        }
        paymentRepository.save(payment);

        Bill bill = billRepository.findById(payment.getBillId())
                .filter(b -> b.getRestaurantId().equals(payment.getRestaurantId()))
                .orElseThrow(() -> new IllegalArgumentException("Bill not found for payment"));
        updateBillFromPayment(bill, payment);
        ensureBillPaymentRow(bill, payment);

        logProcessed(log);

        return Map.of("ok", true);
    }

    private void logProcessed(PaymentWebhookLog log) {
        log.setProcessed(true);
        webhookLogRepository.save(log);
    }

    private Long resolveRestaurantId(String merchantKey) {
        return paymentConfigService.getActiveConfigByMerchantKey(merchantKey).getRestaurantId();
    }

    private void updateBillFromPayment(Bill bill, Payment payment) {
        long now = System.currentTimeMillis();
        switch (payment.getPaymentStatus()) {
            case SUCCESS -> {
                bill.setPaymentStatus("success");
                bill.setOrderStatus("completed");
                bill.setPaidAt(now);
                bill.setCancelReason("");
            }
            case FAILED, CANCELLED -> {
                bill.setPaymentStatus("failed");
                bill.setOrderStatus("cancelled");
                bill.setCancelReason(payment.getFailureReason() == null ? payment.getGatewayStatus() : payment.getFailureReason());
            }
            default -> {
                bill.setPaymentStatus("pending");
                bill.setOrderStatus("draft");
            }
        }
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        billRepository.save(bill);
    }

    private void ensureBillPaymentRow(Bill bill, Payment payment) {
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            return;
        }
        boolean exists = billPaymentRepository.existsByRestaurantIdAndGatewayTxnId(
                bill.getRestaurantId(), payment.getGatewayTxnId());
        if (exists) {
            return;
        }
        BillPayment billPayment = new BillPayment();
        billPayment.setLocalId(System.currentTimeMillis());
        billPayment.setDeviceId(bill.getDeviceId());
        billPayment.setRestaurantId(bill.getRestaurantId());
        billPayment.setUpdatedAt(System.currentTimeMillis());
        billPayment.setCreatedAt(System.currentTimeMillis());
        billPayment.setServerUpdatedAt(System.currentTimeMillis());
        billPayment.setIsDeleted(false);
        billPayment.setBillId(bill.getId());
        billPayment.setServerBillId(bill.getId());
        billPayment.setPaymentMode("upi");
        billPayment.setAmount(payment.getAmount());
        billPayment.setGatewayTxnId(payment.getGatewayTxnId());
        billPayment.setGatewayStatus(payment.getGatewayStatus());
        billPayment.setVerifiedBy("easebuzz");
        billPaymentRepository.save(billPayment);
    }

    private String buildTxnId(Long restaurantId, Long localBillId) {
        return "KB_" + restaurantId + "_" + localBillId + "_" + System.currentTimeMillis();
    }

    private PaymentStatus mapGatewayStatus(String raw) {
        if (raw == null) {
            return PaymentStatus.PENDING;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "success", "successful", "completed", "captured" -> PaymentStatus.SUCCESS;
            case "failure", "failed", "cancelled", "usercancelled", "user_cancelled", "dropped", "bounced" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private String messageFor(PaymentStatus status) {
        return switch (status) {
            case SUCCESS -> "Payment successful";
            case FAILED -> "Payment failed";
            case CANCELLED -> "Payment cancelled";
            case PENDING -> "Payment pending";
        };
    }
}
