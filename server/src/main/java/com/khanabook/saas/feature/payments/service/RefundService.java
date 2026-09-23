package com.khanabook.saas.feature.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.core.exception.BusinessRuleException;
import com.khanabook.saas.core.exception.EntityNotFoundException;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.RefundAttempt;
import com.khanabook.saas.feature.payments.data.RefundAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private final EasebuzzPaymentService easebuzzPaymentService;
    private final BillRepository billRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final WebhookRetryService webhookRetryService;
    private final ObjectMapper objectMapper;

    public static final List<Map<String, String>> REASON_TAXONOMY = List.of(
        Map.of("code", "CUSTOMER_REQUEST", "label", "Customer Request"),
        Map.of("code", "ORDER_CANCELLED", "label", "Order Cancelled"),
        Map.of("code", "DUPLICATE_CHARGE", "label", "Duplicate Charge"),
        Map.of("code", "ITEM_UNAVAILABLE", "label", "Item Unavailable"),
        Map.of("code", "QUALITY_ISSUE", "label", "Quality Issue"),
        Map.of("code", "WRONG_ORDER", "label", "Wrong Order Delivered"),
        Map.of("code", "LATE_DELIVERY", "label", "Late Delivery"),
        Map.of("code", "PARTIAL_ORDER", "label", "Partial Order"),
        Map.of("code", "FRAUD", "label", "Suspected Fraud"),
        Map.of("code", "OTHER", "label", "Other")
    );

    public List<Map<String, String>> getReasonTaxonomy() {
        return REASON_TAXONOMY;
    }

    @Transactional
    public Map<String, Object> initiatePartialRefund(Long billId, Long restaurantId, BigDecimal refundAmount, String reason) {
        // Pessimistic write lock: serializes concurrent refunds for the same bill.
        // The second attempt blocks here, then re-reads the committed refundAmount
        // and fails eligibility checks instead of double-refunding.
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (!bill.getRestaurantId().equals(restaurantId)) {
            throw new BusinessRuleException("Order does not belong to this business");
        }
        if (bill.getGatewayTxnId() == null || !"paid".equalsIgnoreCase(bill.getPaymentStatus()) && !"success".equalsIgnoreCase(bill.getPaymentStatus()) && !"partially_refunded".equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new BusinessRuleException("Bill is not eligible for refund");
        }
        BigDecimal completedRefund = bill.getRefundAmount() != null ? bill.getRefundAmount() : BigDecimal.ZERO;
        BigDecimal pendingRefund = refundAttemptRepository.sumAmountByBillIdAndStatus(billId, "INITIATED");
        if (pendingRefund == null) pendingRefund = BigDecimal.ZERO;
        BigDecimal reservedRefund = completedRefund.add(pendingRefund);
        if (reservedRefund.compareTo(bill.getTotalAmount()) >= 0) {
            throw new BusinessRuleException("Bill is already fully refunded or reserved for refund (₹" + reservedRefund + " of ₹" + bill.getTotalAmount() + ")");
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Refund amount must be positive");
        }
        if (refundAmount.scale() > 2) {
            throw new BusinessRuleException("Refund amount must have at most two decimal places");
        }
        BigDecimal remainingRefundable = bill.getTotalAmount().subtract(reservedRefund);
        if (refundAmount.compareTo(remainingRefundable) > 0) {
            throw new BusinessRuleException("Refund amount (₹" + refundAmount + ") exceeds remaining refundable amount (₹" + remainingRefundable + ")");
        }

        log.info("Initiating refund billId={} amount={} reason={}", billId, refundAmount, reason);
        Map<String, Object> gatewayResult = easebuzzPaymentService.initiateRefund(billId, refundAmount, reason);
        Map<String, Object> result = gatewayResult == null
                ? new LinkedHashMap<>(Map.of("status", "failure", "error", "Refund gateway returned no response"))
                : new LinkedHashMap<>(gatewayResult);

        if ("success".equals(result.get("status"))) {
            String merchantRefundId = String.valueOf(result.getOrDefault("merchant_refund_id", ""));
            String gatewayRefundId = String.valueOf(result.getOrDefault("easebuzz_refund_id", ""));
            String easebuzzPaymentId = String.valueOf(result.getOrDefault("easebuzz_payment_id", ""));
            if (merchantRefundId.isBlank() || gatewayRefundId.isBlank() || easebuzzPaymentId.isBlank()) {
                throw new IllegalStateException("Easebuzz accepted refund without required attempt identifiers");
            }
            RefundAttempt attempt = new RefundAttempt();
            attempt.setBillId(billId);
            attempt.setRestaurantId(restaurantId);
            attempt.setGatewayTxnId(bill.getGatewayTxnId());
            attempt.setEasebuzzPaymentId(easebuzzPaymentId);
            attempt.setMerchantRefundId(merchantRefundId);
            attempt.setGatewayRefundId(gatewayRefundId);
            attempt.setAmount(refundAmount);
            attempt.setStatus("INITIATED");
            attempt.setReason(reason);
            attempt.setCreatedAt(System.currentTimeMillis());
            attempt.setUpdatedAt(attempt.getCreatedAt());
            refundAttemptRepository.save(attempt);
            bill.setCancelReason(reason);
            billRepository.save(bill);
            result.put("refundStatus", "initiated");
            result.put("totalRefunded", completedRefund);
            result.put("pendingRefund", pendingRefund.add(refundAmount));
            result.put("remainingRefundable", remainingRefundable.subtract(refundAmount));

            // Gateway acceptance only initiates a refund. Customer-facing
            // completion notices must wait for a matched completion callback.
        } else {
            result.put("refundStatus", "failed");
            result.put("totalRefunded", completedRefund);
            result.put("pendingRefund", pendingRefund);
            result.put("remainingRefundable", remainingRefundable);
        }
        result.put("billId", billId);
        return result;
    }

    @Transactional
    public Map<String, Object> cancelAndAutoRefund(Long billId, Long restaurantId, String reason, int delayMinutes) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (!bill.getRestaurantId().equals(restaurantId)) {
            throw new BusinessRuleException("Order does not belong to this business");
        }

        bill.setOrderStatus("cancelled");
        bill.setCancelReason(reason);
        billRepository.save(bill);

        boolean isPaid = "paid".equalsIgnoreCase(bill.getPaymentStatus()) || "success".equalsIgnoreCase(bill.getPaymentStatus());
        if (!isPaid) {
            return Map.of("status", "cancelled", "billId", billId, "refundApplied", false);
        }

        if (delayMinutes <= 0) {
            Map<String, Object> refundResult = initiatePartialRefund(billId, restaurantId, bill.getTotalAmount(), "ORDER_CANCELLED");
            boolean refundInitiated = "success".equals(refundResult.get("status"));
            return Map.of("status", "cancelled", "billId", billId,
                    "refundApplied", refundInitiated,
                    "refundStatus", refundInitiated ? "initiated" : "failed",
                    "refund", refundResult);
        }

        long scheduledAt = System.currentTimeMillis() + Duration.ofMinutes(delayMinutes).toMillis();
        String jobKey = "DELAYED_REFUND:" + restaurantId + ":" + billId;
        Map<String, Object> payload = Map.of(
                "billId", billId,
                "restaurantId", restaurantId,
                "amount", bill.getTotalAmount().toPlainString(),
                "reason", "ORDER_CANCELLED");
        try {
            var job = webhookRetryService.enqueueAt(
                    "DELAYED_REFUND", objectMapper.writeValueAsString(payload), scheduledAt, jobKey);
            return Map.of("status", "cancelled", "billId", billId,
                    "refundScheduled", true, "refundJobId", job.getId(),
                    "refundDelayMinutes", delayMinutes, "refundAmount", bill.getTotalAmount());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not persist delayed refund job", e);
        }
    }

    public Map<String, Object> getRefundSummary(Long restaurantId) {
        List<Bill> bills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        long totalOrders = bills.size();
        long refundedOrders = bills.stream().filter(b -> b.getRefundAmount() != null && b.getRefundAmount().compareTo(BigDecimal.ZERO) > 0).count();
        BigDecimal totalRefundAmount = bills.stream().map(b -> b.getRefundAmount() != null ? b.getRefundAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRevenue = bills.stream().filter(b -> "paid".equalsIgnoreCase(b.getPaymentStatus()) || "success".equalsIgnoreCase(b.getPaymentStatus())).map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundRate = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalRefundAmount.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", totalOrders);
        summary.put("refundedOrders", refundedOrders);
        summary.put("totalRefundAmount", totalRefundAmount);
        summary.put("totalRevenue", totalRevenue);
        summary.put("refundRate", refundRate);
        return summary;
    }

    @Transactional
    public Map<String, Object> getRefundableOrders(Long restaurantId) {
        List<Bill> bills = billRepository.findByRestaurantIdAndIsDeletedFalse(restaurantId);
        List<Map<String, Object>> refundable = bills.stream()
                .filter(b -> ("paid".equalsIgnoreCase(b.getPaymentStatus()) || "success".equalsIgnoreCase(b.getPaymentStatus()) || "partially_refunded".equalsIgnoreCase(b.getPaymentStatus())))
                .filter(b -> {
                    BigDecimal refunded = b.getRefundAmount() != null ? b.getRefundAmount() : BigDecimal.ZERO;
                    return refunded.compareTo(b.getTotalAmount()) < 0;
                })
                .map(b -> {
                    BigDecimal refunded = b.getRefundAmount() != null ? b.getRefundAmount() : BigDecimal.ZERO;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("billId", b.getId());
                    m.put("orderCode", b.getDailyOrderDisplay() != null ? b.getDailyOrderDisplay() : "INV" + b.getLifetimeOrderId());
                    m.put("totalAmount", b.getTotalAmount());
                    m.put("refundedAmount", refunded);
                    m.put("remainingRefundable", b.getTotalAmount().subtract(refunded));
                    m.put("customerName", b.getCustomerName());
                    m.put("gatewayTxnId", b.getGatewayTxnId());
                    m.put("createdAt", b.getCreatedAt());
                    return m;
                })
                .toList();
        return Map.of("count", refundable.size(), "orders", refundable);
    }
}
