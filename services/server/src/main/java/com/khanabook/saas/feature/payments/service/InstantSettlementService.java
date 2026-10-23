package com.khanabook.saas.feature.payments.service;

import com.khanabook.saas.feature.notifications.service.EmailNotificationService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.core.exception.BusinessRuleException;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstantSettlementService {

    private static final Logger log = LoggerFactory.getLogger(InstantSettlementService.class);

    private final BillRepository billRepository;
    private final EasebuzzSubMerchantRepository subMerchantRepository;
    private final EasebuzzWebhookEventRepository webhookEventRepository;
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

    /**
     * Settles a chosen set of paid bills through Easebuzz's on-demand settlement.
     *
     * <p>The gateway settles per <b>transaction</b>, not per amount: each entry needs the
     * Easebuzz-issued id, our txnid and the captured amount. Bills are therefore the unit of
     * selection, and every one must have been paid through Easebuzz (a stored gateway txn id and
     * a webhook row carrying the Easebuzz id). One bad bill rejects the whole batch rather than
     * settling part of what the merchant asked for.
     */
    @Transactional
    public Map<String, Object> requestInstantSettlement(Long restaurantId, List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one transaction to settle");
        }

        EasebuzzSubMerchant sm = subMerchantRepository.findByRestaurantId(restaurantId).orElse(null);
        if (sm == null || !"ACTIVE".equals(sm.getStatus())) {
            throw new BusinessRuleException("No active sub-merchant configured for instant settlement");
        }

        List<Map<String, String>> transactions = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;

        for (Long billId : new LinkedHashSet<>(billIds)) {
            Bill bill = billRepository.findById(billId).orElse(null);
            if (bill == null || !restaurantId.equals(bill.getRestaurantId())
                    || bill.getPaymentStatus() == null
                    || !"paid".equalsIgnoreCase(bill.getPaymentStatus())
                    || bill.getGatewayTxnId() == null || bill.getGatewayTxnId().isBlank()) {
                rejected.add(String.valueOf(billId));
                continue;
            }

            EasebuzzWebhookEvent event = webhookEventRepository
                    .findFirstByRestaurantIdAndTxnIdAndEasebuzzIdNotNullOrderByReceivedAtDesc(
                            restaurantId, bill.getGatewayTxnId())
                    .orElse(null);
            if (event == null) {
                rejected.add(String.valueOf(billId));
                continue;
            }

            BigDecimal txnAmount = event.getAmount() != null ? event.getAmount() : bill.getTotalAmount();
            if (txnAmount == null || txnAmount.compareTo(BigDecimal.ZERO) <= 0) {
                rejected.add(String.valueOf(billId));
                continue;
            }

            Map<String, String> transaction = new LinkedHashMap<>();
            transaction.put("easebuzz_id", event.getEasebuzzId());
            transaction.put("merchant_txn_id", bill.getGatewayTxnId());
            transaction.put("amount", txnAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            transactions.add(transaction);
            amount = amount.add(txnAmount);
        }

        if (!rejected.isEmpty()) {
            throw new BusinessRuleException(
                    "Not settleable (unpaid, another outlet, or no Easebuzz transaction): "
                            + String.join(", ", rejected));
        }
        if (amount.compareTo(MIN_SETTLEMENT_AMOUNT) < 0) {
            throw new BusinessRuleException("Minimum settlement amount is " + MIN_SETTLEMENT_AMOUNT);
        }

        BigDecimal fee = amount.multiply(BASE_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netPayout = amount.subtract(fee);
        String batchId = "IS_" + restaurantId + "_" + System.currentTimeMillis();

        try {
            Map<String, Object> easebuzzResult = subMerchantService.initiateOnDemandSettlement(
                    batchId, "Instant settlement for restaurant " + restaurantId, transactions);
            log.info("Instant settlement initiated for restaurantId={} batchId={} amount={} easebuzzResult={}",
                    restaurantId, batchId, amount, easebuzzResult);

            final BigDecimal settledAmount = amount;
            profileRepository.findByRestaurantId(restaurantId).ifPresent(profile -> {
                if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
                    emailNotificationService.sendInstantSettlementNotification(
                        profile.getEmail(), profile.getShopName(), settledAmount, fee, netPayout);
                }
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "initiated");
            result.put("restaurantId", restaurantId);
            result.put("amount", amount);
            result.put("fee", fee);
            result.put("netPayout", netPayout);
            result.put("subMerchantId", sm.getSubMerchantId());
            result.put("batchId", batchId);
            result.put("transactions", transactions.size());
            result.put("settlementType", "instant");
            result.put("easebuzzResponse", easebuzzResult);
            return result;
        } catch (Exception e) {
            log.error("Failed to initiate instant settlement for restaurantId={}", restaurantId, e);
            throw new BusinessRuleException("Failed to initiate settlement: " + e.getMessage());
        }
    }
}