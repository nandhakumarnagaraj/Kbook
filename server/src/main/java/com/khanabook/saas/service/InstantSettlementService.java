package com.khanabook.saas.service;

import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.exception.BusinessRuleException;
import com.khanabook.saas.repository.BillRepository;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstantSettlementService {

    private static final Logger log = LoggerFactory.getLogger(InstantSettlementService.class);

    private final BillRepository billRepository;
    private final EasebuzzSubMerchantRepository subMerchantRepository;
    private final SubMerchantService subMerchantService;
    private final EmailNotificationService emailNotificationService;
    private final RestaurantProfileRepository profileRepository;

    private static final BigDecimal MIN_SETTLEMENT_AMOUNT = new BigDecimal("500");
    private static final BigDecimal BASE_FEE_RATE = new BigDecimal("0.0015");

    public Map<String, Object> getSettlementEstimate(Long restaurantId) {
        BigDecimal settledTotal = billRepository.sumCompletedRevenueByRestaurant(restaurantId);
        BigDecimal settledAmount = settledTotal != null ? settledTotal : BigDecimal.ZERO;
        BigDecimal availableForInstant = settledAmount.multiply(BigDecimal.ONE.subtract(BASE_FEE_RATE)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = settledAmount.multiply(BASE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        boolean eligible = settledAmount.compareTo(MIN_SETTLEMENT_AMOUNT) >= 0;

        Map<String, Object> estimate = new LinkedHashMap<>();
        estimate.put("restaurantId", restaurantId);
        estimate.put("totalSettled", settledAmount);
        estimate.put("fee", fee);
        estimate.put("netPayout", availableForInstant);
        estimate.put("feeRate", BASE_FEE_RATE.multiply(BigDecimal.valueOf(100)));
        estimate.put("minimumAmount", MIN_SETTLEMENT_AMOUNT);
        estimate.put("eligible", eligible);
        return estimate;
    }

    @Transactional
    public Map<String, Object> requestInstantSettlement(Long restaurantId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Settlement amount must be positive");
        }
        if (amount.compareTo(MIN_SETTLEMENT_AMOUNT) < 0) {
            throw new BusinessRuleException("Minimum settlement amount is " + MIN_SETTLEMENT_AMOUNT);
        }
        BigDecimal fee = amount.multiply(BASE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netPayout = amount.subtract(fee);

        EasebuzzSubMerchant sm = subMerchantRepository.findByRestaurantId(restaurantId).orElse(null);
        if (sm == null || !"ACTIVE".equals(sm.getStatus())) {
            throw new BusinessRuleException("No active sub-merchant configured for instant settlement");
        }

        try {
            Map<String, Object> easebuzzResult = subMerchantService.initiateOnDemandSettlement(amount.toPlainString());
            log.info("Instant settlement initiated for restaurantId={} amount={} easebuzzResult={}", restaurantId, amount, easebuzzResult);

            profileRepository.findByRestaurantId(restaurantId).ifPresent(profile -> {
                if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
                    emailNotificationService.sendInstantSettlementNotification(
                        profile.getEmail(), profile.getShopName(), amount, fee, netPayout);
                }
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "initiated");
            result.put("restaurantId", restaurantId);
            result.put("amount", amount);
            result.put("fee", fee);
            result.put("netPayout", netPayout);
            result.put("subMerchantId", sm.getSubMerchantId());
            result.put("settlementType", "instant");
            result.put("easebuzzResponse", easebuzzResult);
            return result;
        } catch (Exception e) {
            log.error("Failed to initiate instant settlement for restaurantId={}", restaurantId, e);
            throw new BusinessRuleException("Failed to initiate settlement: " + e.getMessage());
        }
    }
}