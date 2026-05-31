package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.exception.EntityNotFoundException;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    private final EasebuzzPaymentService easebuzzPaymentService;
    private final BillRepository billRepository;
    private final EmailNotificationService emailNotificationService;
    private final ScheduledExecutorService refundScheduler = Executors.newScheduledThreadPool(2);

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
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new EntityNotFoundException("Bill", billId));
        if (!bill.getRestaurantId().equals(restaurantId)) {
            throw new BusinessRuleException("Order does not belong to this business");
        }
        if (bill.getGatewayTxnId() == null || !"paid".equalsIgnoreCase(bill.getPaymentStatus()) && !"success".equalsIgnoreCase(bill.getPaymentStatus()) && !"partially_refunded".equalsIgnoreCase(bill.getPaymentStatus())) {
            throw new BusinessRuleException("Bill is not eligible for refund");
        }
        BigDecimal existingRefund = bill.getRefundAmount() != null ? bill.getRefundAmount() : BigDecimal.ZERO;
        if (existingRefund.compareTo(bill.getTotalAmount()) >= 0) {
            throw new BusinessRuleException("Bill is already fully refunded (₹" + existingRefund + " of ₹" + bill.getTotalAmount() + ")");
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Refund amount must be positive");
        }
        BigDecimal remainingRefundable = bill.getTotalAmount().subtract(existingRefund);
        if (refundAmount.compareTo(remainingRefundable) > 0) {
            throw new BusinessRuleException("Refund amount (₹" + refundAmount + ") exceeds remaining refundable amount (₹" + remainingRefundable + ")");
        }

        log.info("Initiating refund billId={} amount={} reason={}", billId, refundAmount, reason);
        Map<String, Object> result = easebuzzPaymentService.initiateRefund(billId, refundAmount, reason);

        if ("success".equals(result.get("status"))) {
            BigDecimal newTotalRefund = existingRefund.add(refundAmount);
            bill.setRefundAmount(newTotalRefund);

            if (newTotalRefund.compareTo(bill.getTotalAmount()) >= 0) {
                bill.setPaymentStatus("refunded");
                bill.setOrderStatus("cancelled");
            } else {
                bill.setPaymentStatus("partially_refunded");
            }
            bill.setCancelReason(reason);
            billRepository.save(bill);
            result.put("refundStatus", "initiated");
            result.put("totalRefunded", newTotalRefund);
            result.put("remainingRefundable", bill.getTotalAmount().subtract(newTotalRefund));

            if (bill.getCustomerWhatsapp() != null && !bill.getCustomerWhatsapp().isBlank()) {
                String orderCode = bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : "INV" + bill.getLifetimeOrderId();
                emailNotificationService.sendRefundConfirmation(
                    bill.getCustomerWhatsapp(), bill.getCustomerName(), orderCode, refundAmount, reason);
            }
        }
        result.put("billId", billId);
        return result;
    }

    @Transactional
    public Map<String, Object> cancelAndAutoRefund(Long billId, Long restaurantId, String reason, int delayMinutes) {
        Bill bill = billRepository.findById(billId)
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
            return Map.of("status", "cancelled", "billId", billId, "refundApplied", true, "refund", refundResult);
        }

        BigDecimal finalRefundAmount = bill.getTotalAmount();
        refundScheduler.schedule(() -> {
            try {
                log.info("Executing delayed refund for billId={} after {} minutes", billId, delayMinutes);
                initiatePartialRefund(billId, restaurantId, finalRefundAmount, "ORDER_CANCELLED");
                log.info("Delayed refund completed for billId={}", billId);
            } catch (Exception e) {
                log.error("Delayed refund failed for billId={}", billId, e);
            }
        }, delayMinutes, TimeUnit.MINUTES);

        return Map.of("status", "cancelled", "billId", billId, "refundScheduled", true, "refundDelayMinutes", delayMinutes, "refundAmount", bill.getTotalAmount());
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