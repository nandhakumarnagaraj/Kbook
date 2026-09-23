package com.khanabook.saas.feature.billing.service;

import com.khanabook.saas.feature.payments.service.EasebuzzApiClient;
import com.khanabook.saas.feature.payments.service.SubMerchantService;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchant;
import com.khanabook.saas.feature.billing.data.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostSplitService {

    private static final Logger log = LoggerFactory.getLogger(PostSplitService.class);
    private final EasebuzzApiClient easebuzzApi;
    private final BillRepository billRepo;
    private final SubMerchantService subMerchantService;

    public boolean createPostSplit(Long billId, String easebuzzId, String txnid) {
        Bill bill = billRepo.findById(billId).orElse(null);
        if (bill == null) {
            log.warn("Post-split: Bill not found billId={}", billId);
            return false;
        }

        // A worker may stop after recording success but before completing the
        // queue row. Treat that replay as complete without consulting mutable
        // sub-merchant state or calling Easebuzz again.
        if (bill.getSettledAt() != null && bill.getCommissionAmount() != null) {
            log.info("Post-split: Bill {} already settled, skipping duplicate attempt", billId);
            return true;
        }

        EasebuzzSubMerchant sm;
        try {
            sm = subMerchantService.getByRestaurantId(bill.getRestaurantId());
        } catch (Exception e) {
            log.warn("Post-split: Sub-merchant not found for restaurantId={}", bill.getRestaurantId());
            return false;
        }

        if (!"ACTIVE".equals(sm.getStatus()) || sm.getSplitLabel() == null) {
            log.warn("Post-split: Sub-merchant not active or no split label billId={}", billId);
            return false;
        }

        // Generate merchantRequestId ONCE — reused across all retry attempts.
        // Easebuzz deduplicates on their side when same merchantRequestId is replayed.
        // Using billId + easebuzzId prefix ensures uniqueness per transaction.
        String idSuffix = easebuzzId.length() >= 8 ? easebuzzId.substring(0, 8) : easebuzzId;
        String merchantRequestId = "KB" + billId + "_" + idSuffix;

        BigDecimal totalAmount = bill.getTotalAmount();
        BigDecimal commissionAmount = BigDecimal.ZERO;
        BigDecimal restaurantAmount = totalAmount;

        List<Map<String, String>> configuration = new ArrayList<>();
        configuration.add(Map.of("label", sm.getSplitLabel(), "amount", restaurantAmount.setScale(2, RoundingMode.HALF_UP).toPlainString()));

        String description = "Split for order #" + bill.getDailyOrderDisplay();

        try {
            log.info("Post-split durable attempt billId={} merchantRequestId={}", billId, merchantRequestId);
            Map<String, Object> result = easebuzzApi.updateTransactionSplit(
                merchantRequestId, easebuzzId, totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(), description, configuration
            );
            if ("success".equals(result.get("status"))) {
                log.info("Post-split success billId={} merchantRequestId={}", billId, merchantRequestId);
                updateBillAfterSplit(billId, commissionAmount);
                return true;
            }
            log.warn("Post-split failed billId={} error={}", billId,
                    result.getOrDefault("error", "Unknown error"));
        } catch (Exception e) {
            log.error("Post-split exception billId={} error={}", billId, e.getMessage());
        }
        return false;
    }

    public void updateBillAfterSplit(Long billId, BigDecimal commissionAmount) {
        Bill bill = billRepo.findById(billId).orElse(null);
        if (bill != null) {
            bill.setCommissionAmount(commissionAmount);
            bill.setSettledAt(System.currentTimeMillis());
            billRepo.save(bill);
        }
    }
}
