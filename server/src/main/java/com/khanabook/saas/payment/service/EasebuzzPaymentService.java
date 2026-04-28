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
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
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
        BigDecimal billTotal = bill.getTotalAmount();
        BigDecimal chargeAmount = request.getGatewayAmount() != null
                ? request.getGatewayAmount()
                : billTotal;
        if (chargeAmount.signum() <= 0 || chargeAmount.compareTo(billTotal) > 0) {
            throw new IllegalArgumentException("Invalid gateway amount: must be > 0 and <= bill total");
        }
        chargeAmount = chargeAmount.setScale(2, RoundingMode.HALF_UP);
        String amount = chargeAmount.toPlainString();
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
        payment.setAmount(chargeAmount);
        payment.setCurrency("INR");
        payment.setGateway(PaymentGateway.EASEBUZZ);
        payment.setGatewayTxnId(txnId);
        payment.setGatewayStatus("initiated");
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(request.getPaymentMethod().trim().toUpperCase());
        payment.setRefundStatus(RefundStatus.NOT_REFUNDED);
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

    @Transactional
    public Payment initiateGatewayRefund(Long restaurantId, Long billId, BigDecimal refundAmount, String reason) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        Payment payment = requireLatestSuccessfulEasebuzzPayment(restaurantId, billId);
        validateRefundEligibility(bill, payment, refundAmount);

        long now = System.currentTimeMillis();
        RestaurantPaymentConfig config = paymentConfigService.getActiveConfig(restaurantId);
        String salt = paymentConfigService.decryptSalt(config);
        String merchantRefundId = "KBR_" + payment.getId() + "_" + now;
        String amount = refundAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String requestHash = hashService.buildRefundRequestHash(
                config.getMerchantKey(),
                merchantRefundId,
                payment.getGatewayPaymentId(),
                amount,
                salt
        );

        EasebuzzGatewayClient.RefundInitiation response = gatewayClient.initiateRefund(
                config.getEnvironment(),
                config.getMerchantKey(),
                merchantRefundId,
                payment.getGatewayPaymentId(),
                amount,
                requestHash
        );

        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(mapRefundStatus(response.getRawStatus(), RefundStatus.PENDING));
        payment.setRefundAmount(refundAmount);
        payment.setRefundReason(normalizeRefundReason(reason));
        payment.setMerchantRefundId(merchantRefundId);
        payment.setRefundGatewayRefundId(response.getRefundId());
        payment.setRefundRequestedAt(now);
        payment.setRefundProcessedAt(payment.getRefundStatus() == RefundStatus.SUCCESS ? now : null);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

        syncBillRefundState(bill, payment);
        return payment;
    }

    @Transactional
    public void markManualRefund(Long restaurantId, Long billId, BigDecimal refundAmount, String reason) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        Payment payment = paymentRepository.findTopByRestaurantIdAndBillIdOrderByCreatedAtDesc(restaurantId, billId)
                .filter(existing -> existing.getPaymentStatus() == PaymentStatus.SUCCESS)
                .orElse(null);
        validateManualRefundEligibility(bill, payment, refundAmount);

        long now = System.currentTimeMillis();
        if (payment != null) {
            payment.setRefundMode(RefundMode.MANUAL);
            payment.setRefundStatus(RefundStatus.SUCCESS);
            payment.setRefundAmount(refundAmount);
            payment.setRefundReason(normalizeRefundReason(reason));
            payment.setMerchantRefundId("MANUAL_" + payment.getId() + "_" + now);
            payment.setRefundRequestedAt(now);
            payment.setRefundProcessedAt(now);
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);
            syncBillRefundState(bill, payment);
            return;
        }

        syncBillManualRefundState(bill, refundAmount, normalizeRefundReason(reason));
    }

    @Transactional
    public Map<String, Object> processRefundWebhook(Map<String, String> params) {
        Payment payment = resolveRefundPayment(params);
        if (payment == null) {
            return Map.of("ok", true, "ignored", true);
        }
        refreshRefundStatus(payment);
        return Map.of("ok", true);
    }

    @Transactional
    public Payment refreshRefundStatus(Payment payment) {
        if (payment.getGatewayPaymentId() == null || payment.getGatewayPaymentId().isBlank()) {
            throw new IllegalArgumentException("Easebuzz payment id missing for refund lookup");
        }
        RestaurantPaymentConfig config = paymentConfigService.getActiveConfig(payment.getRestaurantId());
        String salt = paymentConfigService.decryptSalt(config);
        String requestHash = hashService.buildRefundStatusHash(config.getMerchantKey(), payment.getGatewayPaymentId(), salt);
        EasebuzzGatewayClient.RefundLookup lookup = gatewayClient.fetchRefundStatus(
                config.getEnvironment(),
                config.getMerchantKey(),
                payment.getGatewayPaymentId(),
                requestHash
        );

        RefundStatus status = mapRefundStatus(lookup.getRawStatus(), payment.getRefundStatus() == null ? RefundStatus.PENDING : payment.getRefundStatus());
        payment.setRefundStatus(status);
        if (lookup.getRefundId() != null && !lookup.getRefundId().isBlank()) {
            payment.setRefundGatewayRefundId(lookup.getRefundId());
        }
        if (lookup.getArnNumber() != null && !lookup.getArnNumber().isBlank()) {
            payment.setRefundArnNumber(lookup.getArnNumber());
        }
        if (lookup.getMessage() != null && !lookup.getMessage().isBlank() && (payment.getRefundReason() == null || payment.getRefundReason().isBlank())) {
            payment.setRefundReason(lookup.getMessage());
        }
        if (status == RefundStatus.SUCCESS && payment.getRefundProcessedAt() == null) {
            payment.setRefundProcessedAt(System.currentTimeMillis());
        }
        payment.setUpdatedAt(System.currentTimeMillis());
        paymentRepository.save(payment);

        Bill bill = billRepository.findById(payment.getBillId())
                .filter(existing -> existing.getRestaurantId().equals(payment.getRestaurantId()))
                .orElseThrow(() -> new IllegalArgumentException("Bill not found for refund"));
        syncBillRefundState(bill, payment);
        return payment;
    }

    @Transactional
    public Payment refreshRefundStatus(Long restaurantId, Long billId) {
        Payment payment = requireLatestSuccessfulEasebuzzPayment(restaurantId, billId);
        if (payment.getRefundMode() != RefundMode.EASEBUZZ) {
            throw new IllegalArgumentException("This order does not use an Easebuzz gateway refund");
        }
        if (payment.getRefundStatus() == null || payment.getRefundStatus() == RefundStatus.NOT_REFUNDED) {
            throw new IllegalArgumentException("No Easebuzz refund has been initiated for this order");
        }
        return refreshRefundStatus(payment);
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

    private void syncBillRefundState(Bill bill, Payment payment) {
        long now = System.currentTimeMillis();
        bill.setRefundAmount(payment.getRefundAmount() == null ? BigDecimal.ZERO : payment.getRefundAmount());
        bill.setCancelReason(payment.getRefundReason() == null ? "" : payment.getRefundReason());
        bill.setOrderStatus("cancelled");
        if ("refunded".equalsIgnoreCase(bill.getPaymentStatus())) {
            bill.setPaymentStatus("success");
        }
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
        billRepository.save(bill);
    }

    private void syncBillManualRefundState(Bill bill, BigDecimal refundAmount, String reason) {
        long now = System.currentTimeMillis();
        bill.setRefundAmount(refundAmount == null ? BigDecimal.ZERO : refundAmount);
        bill.setCancelReason(reason == null ? "" : reason);
        bill.setOrderStatus("cancelled");
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
        billPayment.setPaymentMode("easebuzz");
        billPayment.setAmount(payment.getAmount());
        billPayment.setGatewayTxnId(payment.getGatewayTxnId());
        billPayment.setGatewayStatus(payment.getGatewayStatus());
        billPayment.setVerifiedBy("easebuzz");
        billPaymentRepository.save(billPayment);
    }

    private String buildTxnId(Long restaurantId, Long localBillId) {
        return "KB_" + restaurantId + "_" + localBillId + "_" + System.currentTimeMillis();
    }

    private Payment requireLatestSuccessfulEasebuzzPayment(Long restaurantId, Long billId) {
        List<Payment> payments = paymentRepository.findByRestaurantIdAndBillIdOrderByCreatedAtDesc(restaurantId, billId);
        return payments.stream()
                .filter(existing -> existing.getPaymentStatus() == PaymentStatus.SUCCESS)
                .filter(existing -> existing.getGateway() == PaymentGateway.EASEBUZZ)
                .filter(existing -> existing.getGatewayPaymentId() != null && !existing.getGatewayPaymentId().isBlank())
                .max(Comparator.comparing(Payment::getCreatedAt))
                .orElseThrow(() -> new IllegalArgumentException("No successful Easebuzz payment found for this order"));
    }

    private void validateRefundEligibility(Bill bill, Payment payment, BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        if (refundAmount.compareTo(bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP)) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed order total");
        }
        if (payment.getRefundStatus() == RefundStatus.PENDING) {
            throw new IllegalArgumentException("A gateway refund is already pending for this order");
        }
        if (payment.getRefundStatus() == RefundStatus.SUCCESS) {
            throw new IllegalArgumentException("This order is already refunded");
        }
        if (payment.getVerifiedAt() != null && System.currentTimeMillis() - payment.getVerifiedAt() > Duration.ofDays(180).toMillis()) {
            throw new IllegalArgumentException("Easebuzz refunds are allowed only within 180 days of payment");
        }
        if (!"completed".equalsIgnoreCase(bill.getOrderStatus()) && bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("This order already has a refund record");
        }
    }

    private void validateManualRefundEligibility(Bill bill, Payment payment, BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        if (refundAmount.compareTo(bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP)) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed order total");
        }
        if (!isRefundableOrderStatus(bill.getOrderStatus())) {
            throw new IllegalArgumentException("Only completed or cancelled orders can be refunded");
        }
        if (isEasebuzzBill(bill) || isEasebuzzPayment(payment)) {
            throw new IllegalArgumentException("Easebuzz payments must be refunded via Easebuzz");
        }
        if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("This order already has a refund record");
        }
        if (payment != null && payment.getRefundStatus() == RefundStatus.PENDING) {
            throw new IllegalArgumentException("A gateway refund is already pending for this order");
        }
        if (payment != null && payment.getRefundStatus() == RefundStatus.SUCCESS) {
            throw new IllegalArgumentException("This order is already refunded");
        }
    }

    private boolean isRefundableOrderStatus(String orderStatus) {
        return "completed".equalsIgnoreCase(orderStatus) || "cancelled".equalsIgnoreCase(orderStatus);
    }

    private boolean isEasebuzzPayment(Payment payment) {
        return payment != null
                && payment.getGateway() == PaymentGateway.EASEBUZZ
                && payment.getGatewayPaymentId() != null
                && !payment.getGatewayPaymentId().isBlank();
    }

    private boolean isEasebuzzBill(Bill bill) {
        return bill.getPaymentMode() != null
                && bill.getPaymentMode().toLowerCase().contains("easebuzz");
    }

    private Payment resolveRefundPayment(Map<String, String> params) {
        String refundId = blankToNull(params.get("refund_id"));
        if (refundId != null) {
            var byGatewayRefundId = paymentRepository.findTopByRefundGatewayRefundIdOrderByUpdatedAtDesc(refundId);
            if (byGatewayRefundId.isPresent()) {
                return byGatewayRefundId.get();
            }
        }
        String merchantRefundId = blankToNull(params.get("merchant_refund_id"));
        if (merchantRefundId != null) {
            var byMerchantRefundId = paymentRepository.findTopByMerchantRefundIdOrderByUpdatedAtDesc(merchantRefundId);
            if (byMerchantRefundId.isPresent()) {
                return byMerchantRefundId.get();
            }
        }
        String gatewayPaymentId = blankToNull(params.getOrDefault("easepayid", params.get("easebuzz_id")));
        return gatewayPaymentId == null
                ? null
                : paymentRepository.findTopByGatewayPaymentIdOrderByUpdatedAtDesc(gatewayPaymentId).orElse(null);
    }

    private RefundStatus mapRefundStatus(String raw, RefundStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "success", "successful", "completed", "processed", "settled", "refunded" -> RefundStatus.SUCCESS;
            case "failed", "failure", "error", "declined", "rejected", "cancelled", "canceled" -> RefundStatus.FAILED;
            case "accepted", "queued", "pending", "processing", "initiated", "requested", "in_progress" -> RefundStatus.PENDING;
            case "not_refunded", "none" -> RefundStatus.NOT_REFUNDED;
            default -> fallback;
        };
    }

    private String normalizeRefundReason(String reason) {
        return reason == null || reason.isBlank() ? "Refund issued" : reason.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
