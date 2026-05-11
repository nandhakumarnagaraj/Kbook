package com.khanabook.saas.billing.service;

import com.khanabook.saas.billing.domain.Bill;
import com.khanabook.saas.billing.domain.BillPayment;
import com.khanabook.saas.billing.domain.Payment;
import com.khanabook.saas.billing.dto.CreateEasebuzzOrderRequest;
import com.khanabook.saas.billing.dto.CreateEasebuzzOrderResponse;
import com.khanabook.saas.billing.dto.EasebuzzPaymentStatusResponse;
import com.khanabook.saas.billing.entity.*;
import com.khanabook.saas.billing.repository.PaymentRepository;
import com.khanabook.saas.billing.repository.PaymentWebhookLogRepository;
import com.khanabook.saas.billing.repository.BillPaymentRepository;
import com.khanabook.saas.billing.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EasebuzzPaymentService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookLogRepository webhookLogRepository;
    private final RestaurantPaymentConfigService paymentConfigService;
    private final EasebuzzSubMerchantService subMerchantService;
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

        MerchantRouting routing = resolveMerchantRouting(restaurantId);
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
                routing.merchantKey(),
                txnId,
                amount,
                "Bill payment",
                firstName,
                email,
                routing.salt()
        );

        EasebuzzGatewayClient.GatewaySession session = gatewayClient.createSession(
                routing.environment(),
                routing.merchantKey(),
                routing.subMerchantId(),
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
        payment.setGatewayMerchantKey(routing.merchantKey());
        payment.setGatewaySubMerchantId(routing.subMerchantId());
        payment.setGatewayEnvironment(routing.environment());
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
                .routingMode(routing.routingMode().name())
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
    public EasebuzzPaymentStatusResponse verifyWithGateway(Long restaurantId, String deviceId, Long localBillId) {
        Bill bill = billRepository.findByRestaurantIdAndDeviceIdAndLocalId(restaurantId, deviceId, localBillId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found for this device"));
        Payment payment = verifyBillWithGateway(restaurantId, bill);

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
    public Payment verifyServerBillWithGateway(Long restaurantId, Long billId) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return verifyBillWithGateway(restaurantId, bill);
    }

    private Payment verifyBillWithGateway(Long restaurantId, Bill bill) {
        Payment payment = paymentRepository.findTopByRestaurantIdAndBillIdOrderByCreatedAtDesc(restaurantId, bill.getId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for bill"));
        if (payment.getGateway() != PaymentGateway.EASEBUZZ) {
            throw new IllegalArgumentException("Latest payment is not an Easebuzz payment");
        }

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            MerchantRouting routing = resolveMerchantRouting(payment);
            String amount = payment.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String email = "noreply@khanabook.app";
            String phone = bill.getCustomerWhatsapp() == null || bill.getCustomerWhatsapp().isBlank()
                    ? "0000000000" : bill.getCustomerWhatsapp();
            String requestHash = hashService.buildTransactionStatusHash(
                    routing.merchantKey(),
                    payment.getGatewayTxnId(),
                    amount,
                    email,
                    phone,
                    routing.salt()
            );

            EasebuzzGatewayClient.TransactionLookup lookup = gatewayClient.fetchTransaction(
                    routing.environment(),
                    routing.merchantKey(),
                    payment.getGatewayTxnId(),
                    amount,
                    email,
                    phone,
                    requestHash
            );
            applyGatewayLookup(payment, bill, lookup);
        }

        return paymentRepository.findById(payment.getId()).orElse(payment);
    }

    @Transactional
    public Map<String, Object> processGatewayCallback(Map<String, String> params) {
        String txnId = params.getOrDefault("txnid", "");
        Payment payment = paymentRepository.findTopByGatewayTxnIdOrderByCreatedAtDescForUpdate(txnId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for transaction"));

        MerchantRouting routing = resolveMerchantRouting(payment);
        String merchantKey = payment.getGatewayMerchantKey() == null || payment.getGatewayMerchantKey().isBlank()
                ? params.getOrDefault("key", "")
                : payment.getGatewayMerchantKey();
        String expectedHash = hashService.buildWebhookHash(
                routing.salt(),
                params.getOrDefault("status", ""),
                params.getOrDefault("email", ""),
                params.getOrDefault("firstname", ""),
                params.getOrDefault("productinfo", ""),
                params.getOrDefault("amount", ""),
                txnId,
                merchantKey
        );
        boolean signatureValid = expectedHash.equalsIgnoreCase(params.getOrDefault("hash", ""));

        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        webhookLog.setPaymentId(payment.getId());
        webhookLog.setGateway(PaymentGateway.EASEBUZZ);
        webhookLog.setTxnId(txnId);
        webhookLog.setPayload(params.toString());
        webhookLog.setSignatureValid(signatureValid);
        webhookLog.setProcessed(false);
        webhookLog.setReceivedAt(System.currentTimeMillis());
        webhookLogRepository.save(webhookLog);

        if (!signatureValid) {
            throw new org.springframework.security.access.AccessDeniedException("Webhook hash mismatch");
        }

        String callbackAmount = params.getOrDefault("amount", "");
        if (!callbackAmount.isBlank()) {
            try {
                BigDecimal expectedAmount = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
                BigDecimal receivedAmount = new BigDecimal(callbackAmount).setScale(2, RoundingMode.HALF_UP);
                if (expectedAmount.compareTo(receivedAmount) != 0) {
                    log.warn("Payment amount mismatch: expected={}, received={}, txnId={}", expectedAmount, receivedAmount, txnId);
                    throw new IllegalArgumentException("Payment amount mismatch — possible tampering");
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid amount in callback: {}", callbackAmount);
            }
        }

        String gatewayStatus = params.getOrDefault("status", "pending");
        PaymentStatus finalStatus = mapGatewayStatus(gatewayStatus);
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && finalStatus != PaymentStatus.SUCCESS) {
            logProcessed(webhookLog);
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

        logProcessed(webhookLog);

        return Map.of("ok", true);
    }

    private void applyGatewayLookup(
            Payment payment,
            Bill bill,
            EasebuzzGatewayClient.TransactionLookup lookup
    ) {
        String gatewayStatus = blankToNull(lookup.getGatewayStatus());
        if (gatewayStatus == null) {
            return;
        }
        PaymentStatus finalStatus = mapGatewayStatus(gatewayStatus);
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && finalStatus != PaymentStatus.SUCCESS) {
            return;
        }
        payment.setGatewayStatus(gatewayStatus);
        if (lookup.getGatewayPaymentId() != null && !lookup.getGatewayPaymentId().isBlank()) {
            payment.setGatewayPaymentId(lookup.getGatewayPaymentId());
        }
        payment.setPaymentStatus(finalStatus);
        payment.setVerifiedAt(System.currentTimeMillis());
        payment.setUpdatedAt(System.currentTimeMillis());
        if (finalStatus != PaymentStatus.SUCCESS) {
            payment.setFailureReason(blankToNull(lookup.getMessage()) == null ? gatewayStatus : lookup.getMessage());
        } else {
            payment.setFailureReason(null);
        }
        paymentRepository.save(payment);
        updateBillFromPayment(bill, payment);
        ensureBillPaymentRow(bill, payment);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public Payment initiateGatewayRefund(Long restaurantId, Long billId, BigDecimal refundAmount, String reason) {
        Bill bill = billRepository.findById(billId)
                .filter(existing -> existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        Payment payment = requireLatestSuccessfulEasebuzzPayment(restaurantId, billId);
        BigDecimal canonicalRefundAmount = canonicalRefundAmount(refundAmount);
        validateRefundEligibility(bill, payment, canonicalRefundAmount);

        long now = System.currentTimeMillis();
        MerchantRouting routing = resolveMerchantRouting(payment);
        String normalizedReason = normalizeRefundReason(reason);
        String merchantRefundId = resolveMerchantRefundId(payment, canonicalRefundAmount, normalizedReason, now);
        String amount = canonicalRefundAmount.toPlainString();
        String requestHash = hashService.buildRefundRequestHash(
                routing.merchantKey(),
                merchantRefundId,
                payment.getGatewayPaymentId(),
                amount,
                routing.salt()
        );

        EasebuzzGatewayClient.RefundInitiation response;
        try {
            response = gatewayClient.initiateRefund(
                    routing.environment(),
                    routing.merchantKey(),
                    merchantRefundId,
                    payment.getGatewayPaymentId(),
                    amount,
                    requestHash
            );
        } catch (RuntimeException ex) {
            markGatewayRefundFailure(payment, bill, canonicalRefundAmount, merchantRefundId, now, ex.getMessage(), null);
            throw ex;
        }
        if (!isAcceptedRefundInitiation(response)) {
            String failureMessage = response.getMessage().isBlank()
                    ? "Easebuzz refund request was rejected"
                    : response.getMessage();
            markGatewayRefundFailure(
                    payment,
                    bill,
                    canonicalRefundAmount,
                    merchantRefundId,
                    now,
                    failureMessage,
                    response.getRawPayload()
            );
            throw new IllegalArgumentException(failureMessage);
        }

        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(mapRefundStatus(response.getRefundStatus(), RefundStatus.PENDING));
        payment.setRefundAmount(canonicalRefundAmount);
        payment.setRefundReason(normalizedReason);
        payment.setMerchantRefundId(merchantRefundId);
        payment.setRefundGatewayRefundId(response.getRefundId());
        payment.setRefundRequestedAt(now);
        payment.setRefundProcessedAt(payment.getRefundStatus() == RefundStatus.SUCCESS ? now : null);
        payment.setRefundLastGatewayPayload(response.getRawPayload());
        payment.setRefundLastGatewaySyncAt(now);
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
        PaymentWebhookLog webhookLog = new PaymentWebhookLog();
        webhookLog.setGateway(PaymentGateway.EASEBUZZ);
        webhookLog.setTxnId(blankToNull(params.getOrDefault("merchant_refund_id", params.getOrDefault("refund_id", params.get("easebuzz_id")))));
        webhookLog.setPayload(params.toString());
        webhookLog.setReceivedAt(System.currentTimeMillis());
        webhookLog.setProcessed(false);
        webhookLog.setPaymentId(payment != null ? payment.getId() : null);

        boolean sourceValid = payment != null && isRefundWebhookSourceValid(params, payment);
        webhookLog.setSignatureValid(sourceValid);
        webhookLogRepository.save(webhookLog);

        if (!sourceValid) {
            return Map.of("ok", true, "ignored", true);
        }

        synchronized (payment) {
            Payment lockedPayment = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Payment disappeared during refund webhook"));
            if (lockedPayment.getRefundStatus() == RefundStatus.SUCCESS) {
                log.info("Refund already processed for payment {}, ignoring duplicate webhook", payment.getId());
                logProcessed(webhookLog);
                return Map.of("ok", true, "ignored", true);
            }
            refreshRefundStatus(lockedPayment);
        }
        logProcessed(webhookLog);
        return Map.of("ok", true);
    }

    @Transactional
    public Payment refreshRefundStatus(Payment payment) {
        if (payment.getGatewayPaymentId() == null || payment.getGatewayPaymentId().isBlank()) {
            throw new IllegalArgumentException("Easebuzz payment id missing for refund lookup");
        }
        MerchantRouting routing = resolveMerchantRouting(payment);
        String requestHash = hashService.buildRefundStatusHash(routing.merchantKey(), payment.getGatewayPaymentId(), routing.salt());
        EasebuzzGatewayClient.RefundLookup lookup = gatewayClient.fetchRefundStatus(
                routing.environment(),
                routing.merchantKey(),
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
        payment.setRefundLastGatewayPayload(lookup.getRawPayload());
        payment.setRefundLastGatewaySyncAt(System.currentTimeMillis());
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
        if (payment.getRefundStatus() == RefundStatus.SUCCESS) {
            bill.setRefundAmount(payment.getRefundAmount() == null ? BigDecimal.ZERO : payment.getRefundAmount());
            bill.setCancelReason(payment.getRefundReason() == null ? "" : payment.getRefundReason());
            bill.setOrderStatus("cancelled");
            bill.setPaymentStatus("success");
        } else {
            bill.setRefundAmount(BigDecimal.ZERO);
            if (bill.getPaymentStatus() == null || bill.getPaymentStatus().isBlank() || "refunded".equalsIgnoreCase(bill.getPaymentStatus())) {
                bill.setPaymentStatus("success");
            }
            if ("cancelled".equalsIgnoreCase(bill.getOrderStatus()) && payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                bill.setOrderStatus("completed");
            }
            if (payment.getRefundStatus() != RefundStatus.FAILED) {
                bill.setCancelReason("");
            }
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

    private void markGatewayRefundFailure(
            Payment payment,
            Bill bill,
            BigDecimal refundAmount,
            String merchantRefundId,
            long now,
            String failureMessage,
            String rawPayload
    ) {
        payment.setRefundMode(RefundMode.EASEBUZZ);
        payment.setRefundStatus(RefundStatus.FAILED);
        payment.setRefundAmount(refundAmount);
        payment.setRefundReason((failureMessage == null || failureMessage.isBlank())
                ? "Easebuzz refund request failed"
                : failureMessage.trim());
        payment.setMerchantRefundId(merchantRefundId);
        payment.setRefundRequestedAt(now);
        payment.setRefundLastGatewayPayload(rawPayload);
        payment.setRefundLastGatewaySyncAt(now);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);
        syncBillRefundState(bill, payment);
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
        BigDecimal gatewayPaidAmount = payment.getAmount() == null
                ? BigDecimal.ZERO
                : payment.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (refundAmount.compareTo(gatewayPaidAmount) > 0) {
            throw new IllegalArgumentException("Refund amount cannot exceed Easebuzz paid amount");
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

    private boolean isRefundWebhookSourceValid(Map<String, String> params, Payment payment) {
        String merchantRefundId = blankToNull(params.get("merchant_refund_id"));
        String refundId = blankToNull(params.get("refund_id"));
        String gatewayPaymentId = blankToNull(params.getOrDefault("easepayid", params.get("easebuzz_id")));
        boolean matchesKnownReference =
                (merchantRefundId != null && merchantRefundId.equals(payment.getMerchantRefundId()))
                        || (refundId != null && refundId.equals(payment.getRefundGatewayRefundId()))
                        || (gatewayPaymentId != null && gatewayPaymentId.equals(payment.getGatewayPaymentId()));
        if (!matchesKnownReference) {
            return false;
        }

        String merchantKey = blankToNull(params.get("key"));
        if (merchantKey == null) {
            return false;
        }
        try {
            MerchantRouting routing = resolveMerchantRouting(payment);
            if (!merchantKey.equals(routing.merchantKey())) {
                return false;
            }
            String webhookHash = blankToNull(params.get("hash"));
            if (webhookHash != null) {
                String expectedHash = hashService.buildRefundWebhookHash(
                        routing.salt(),
                        params.getOrDefault("status", ""),
                        params.getOrDefault("refund_id", ""),
                        params.getOrDefault("amount", ""),
                        params.getOrDefault("merchant_refund_id", ""),
                        params.getOrDefault("easepayid", ""),
                        merchantKey
                );
                byte[] expectedBytes = expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] actualBytes = webhookHash.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return java.security.MessageDigest.isEqual(expectedBytes, actualBytes);
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
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

    private boolean isAcceptedRefundInitiation(EasebuzzGatewayClient.RefundInitiation response) {
        if (response.isApiAccepted()) {
            return true;
        }
        if (response.getRefundId() != null && !response.getRefundId().isBlank()) {
            return true;
        }
        if (response.getMessage() == null || response.getMessage().isBlank()) {
            return false;
        }
        String normalizedMessage = response.getMessage().trim().toLowerCase();
        return normalizedMessage.contains("refund initiated")
                || normalizedMessage.contains("request id:")
                || normalizedMessage.contains("request id ");
    }

    private String normalizeRefundReason(String reason) {
        return reason == null || reason.isBlank() ? "Refund issued" : reason.trim();
    }

    private BigDecimal canonicalRefundAmount(BigDecimal refundAmount) {
        if (refundAmount == null) {
            throw new IllegalArgumentException("Refund amount is required");
        }
        BigDecimal canonical = refundAmount.setScale(2, RoundingMode.HALF_UP);
        if (canonical.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        return canonical;
    }

    private String resolveMerchantRefundId(Payment payment, BigDecimal refundAmount, String reason, long now) {
        boolean sameRequest = payment.getRefundMode() == RefundMode.EASEBUZZ
                && payment.getMerchantRefundId() != null
                && payment.getRefundAmount() != null
                && payment.getRefundAmount().compareTo(refundAmount) == 0
                && Objects.equals(normalizeRefundReason(payment.getRefundReason()), reason)
                && payment.getRefundStatus() != RefundStatus.SUCCESS;
        return sameRequest
                ? payment.getMerchantRefundId()
                : "KBR_" + payment.getId() + "_" + now;
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

    private MerchantRouting resolveMerchantRouting(Long restaurantId) {
        return subMerchantService.findActiveByRestaurantId(restaurantId)
                .map(subMerchant -> new MerchantRouting(
                        subMerchantService.getMasterEnvironment(),
                        subMerchantService.getMasterMerchantKey(),
                        subMerchantService.getMasterSalt(),
                        subMerchant.getSubMerchantId(),
                        PaymentRoutingMode.MASTER_SUB_MERCHANT))
                .orElseGet(() -> {
                    RestaurantPaymentConfig config = paymentConfigService.getActiveConfig(restaurantId);
                    return new MerchantRouting(
                            config.getEnvironment(),
                            config.getMerchantKey(),
                            paymentConfigService.decryptSalt(config),
                            null,
                            PaymentRoutingMode.RESTAURANT_CONFIG);
                });
    }

    private MerchantRouting resolveMerchantRouting(Payment payment) {
        String merchantKey = blankToNull(payment.getGatewayMerchantKey());
        String environment = blankToNull(payment.getGatewayEnvironment());
        if (merchantKey != null) {
            String configuredMasterKey = subMerchantService.getConfiguredMasterMerchantKeyOrNull();
            boolean usesMaster = configuredMasterKey != null && merchantKey.equals(configuredMasterKey);
            if (usesMaster) {
                return new MerchantRouting(
                        environment == null ? subMerchantService.getMasterEnvironment() : environment,
                        merchantKey,
                        subMerchantService.getMasterSalt(),
                        blankToNull(payment.getGatewaySubMerchantId()),
                        PaymentRoutingMode.MASTER_SUB_MERCHANT);
            }
            RestaurantPaymentConfig config = paymentConfigService.getActiveConfigByMerchantKey(merchantKey);
            return new MerchantRouting(
                    environment == null ? config.getEnvironment() : environment,
                    merchantKey,
                    paymentConfigService.decryptSalt(config),
                    blankToNull(payment.getGatewaySubMerchantId()),
                    PaymentRoutingMode.RESTAURANT_CONFIG);
        }
        return resolveMerchantRouting(payment.getRestaurantId());
    }

    private record MerchantRouting(
            String environment,
            String merchantKey,
            String salt,
            String subMerchantId,
            PaymentRoutingMode routingMode
    ) {
    }
}
