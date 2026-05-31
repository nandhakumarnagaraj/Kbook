package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.Chargeback;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.ChargebackRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChargebackPreventionService {

    private static final Logger log = LoggerFactory.getLogger(ChargebackPreventionService.class);

    private final ChargebackRepository chargebackRepository;
    private final BillRepository billRepository;
    private final EmailNotificationService emailNotificationService;
    private final RestaurantProfileRepository profileRepository;

    private static final int VELOCITY_WINDOW_HOURS = 24;
    private static final int MAX_CHARGEBACKS_PER_PHONE = 3;
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal VERY_HIGH_VALUE_THRESHOLD = new BigDecimal("50000");

    public static final List<Map<String, String>> REASON_CODES = List.of(
        Map.of("code", "fraudulent", "label", "Fraudulent Transaction"),
        Map.of("code", "duplicate", "label", "Duplicate Billing"),
        Map.of("code", "not_received", "label", "Product/Service Not Received"),
        Map.of("code", "defective", "label", "Defective/Misrepresented"),
        Map.of("code", "subscription_cancelled", "label", "Subscription Cancelled"),
        Map.of("code", "unrecognized", "label", "Unrecognized Charge"),
        Map.of("code", "other", "label", "Other")
    );

    @Transactional
    public Map<String, Object> scoreTransaction(Long billId) {
        Bill bill = billRepository.findById(billId).orElse(null);
        if (bill == null) return Map.of("score", 0, "risk", "unknown");

        double score = 0;
        List<String> factors = new ArrayList<>();

        if (bill.getTotalAmount() != null) {
            if (bill.getTotalAmount().compareTo(VERY_HIGH_VALUE_THRESHOLD) > 0) {
                score += 30;
                factors.add("Very high-value transaction (>" + VERY_HIGH_VALUE_THRESHOLD + ")");
            } else if (bill.getTotalAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
                score += 20;
                factors.add("High-value transaction (>" + HIGH_VALUE_THRESHOLD + ")");
            }
        }

        if (bill.getCustomerName() == null || bill.getCustomerName().isBlank()) {
            score += 15;
            factors.add("Missing customer name");
        }
        if (bill.getCustomerWhatsapp() == null || bill.getCustomerWhatsapp().isBlank()) {
            score += 10;
            factors.add("No contact info");
        } else {
            int velocityScore = checkVelocity(bill.getRestaurantId(), bill.getCustomerWhatsapp());
            if (velocityScore > 0) {
                score += velocityScore;
                factors.add("Velocity alert: " + velocityScore + " points");
            }
        }

        if (bill.getPaymentMode() != null && "easebuzz".equalsIgnoreCase(bill.getPaymentMode())) {
            score += 5;
        }

        List<Chargeback> priorChargebacks = chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(bill.getRestaurantId());
        if (priorChargebacks.size() > 10) {
            score += 20;
            factors.add("Restaurant has " + priorChargebacks.size() + " prior chargebacks");
        } else if (priorChargebacks.size() > 5) {
            score += 10;
            factors.add("Restaurant has " + priorChargebacks.size() + " prior chargebacks");
        }

        String risk = score >= 60 ? "critical" : score >= 40 ? "high" : score >= 25 ? "medium" : "low";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP));
        result.put("risk", risk);
        result.put("factors", factors);
        result.put("requiresReview", score >= 40);
        result.put("recommendedAction", getRecommendedAction(score, bill));
        return result;
    }

    private int checkVelocity(Long restaurantId, String phone) {
        long windowStart = System.currentTimeMillis() - (VELOCITY_WINDOW_HOURS * 60 * 60 * 1000L);
        List<Chargeback> recentChargebacks = chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
        long recentCount = recentChargebacks.stream()
                .filter(cb -> cb.getCustomerPhone() != null && cb.getCustomerPhone().equals(phone))
                .filter(cb -> cb.getCreatedAt() >= windowStart)
                .count();

        if (recentCount >= MAX_CHARGEBACKS_PER_PHONE) {
            log.warn("Velocity alert: phone={} has {} chargebacks in {}h window", phone, recentCount, VELOCITY_WINDOW_HOURS);
            return 25;
        } else if (recentCount >= 2) {
            return 10;
        }
        return 0;
    }

    private String getRecommendedAction(double score, Bill bill) {
        if (score >= 60) return "BLOCK_AND_REVIEW";
        if (score >= 40) return "MANUAL_REVIEW";
        if (score >= 25) return "ADDITIONAL_VERIFICATION";
        return "AUTO_APPROVE";
    }

    @Transactional
    public Chargeback createChargeback(Long restaurantId, Long billId, String reasonCode, String description) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new BusinessRuleException("Bill not found"));
        Chargeback cb = new Chargeback();
        cb.setRestaurantId(restaurantId);
        cb.setBillId(billId);
        cb.setEasebuzzTxnId(bill.getGatewayTxnId());
        cb.setAmount(bill.getTotalAmount());
        cb.setReasonCode(reasonCode);
        cb.setReasonDescription(description);
        cb.setStatus("open");
        cb.setCustomerName(bill.getCustomerName());
        cb.setCustomerPhone(bill.getCustomerWhatsapp());
        cb.setCreatedAt(System.currentTimeMillis());

        Map<String, Object> fraudScore = scoreTransaction(billId);
        cb.setFraudScore((BigDecimal) fraudScore.get("score"));

        Chargeback saved = chargebackRepository.save(cb);

        profileRepository.findByRestaurantId(restaurantId).ifPresent(profile -> {
            if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
                String orderCode = bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : "INV" + bill.getLifetimeOrderId();
                emailNotificationService.sendChargebackAlert(
                    profile.getEmail(), profile.getShopName(), orderCode, bill.getTotalAmount(), reasonCode);
            }
        });

        return saved;
    }

    @Transactional
    public Map<String, Object> resolveChargeback(Long restaurantId, Long chargebackId, String resolution) {
        Chargeback cb = chargebackRepository.findById(chargebackId)
                .orElseThrow(() -> new BusinessRuleException("Chargeback not found"));
        if (!cb.getRestaurantId().equals(restaurantId)) throw new BusinessRuleException("Unauthorized");
        cb.setStatus("resolved");
        cb.setResolvedAt(System.currentTimeMillis());
        chargebackRepository.save(cb);
        return Map.of("status", "resolved", "chargebackId", chargebackId, "resolution", resolution);
    }

    public Map<String, Object> getChargebackSummary(Long restaurantId) {
        long totalChargebacks = chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).size();
        BigDecimal unresolvedAmount = chargebackRepository.sumUnresolvedAmountByRestaurant(restaurantId);
        List<Object[]> statusCounts = chargebackRepository.countByStatusForRestaurant(restaurantId);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : statusCounts) byStatus.put((String) row[0], ((Number) row[1]).longValue());

        long last30Days = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        long recentChargebacks = chargebackRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId).stream()
                .filter(cb -> cb.getCreatedAt() >= last30Days)
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restaurantId", restaurantId);
        result.put("totalChargebacks", totalChargebacks);
        result.put("recentChargebacks30d", recentChargebacks);
        result.put("unresolvedAmount", unresolvedAmount != null ? unresolvedAmount : BigDecimal.ZERO);
        result.put("byStatus", byStatus);
        result.put("chargebackRate", totalChargebacks > 0 ? BigDecimal.valueOf(recentChargebacks * 100.0 / Math.max(totalChargebacks, 1)).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        return result;
    }

    public List<Map<String, String>> getReasonCodes() {
        return REASON_CODES;
    }
}