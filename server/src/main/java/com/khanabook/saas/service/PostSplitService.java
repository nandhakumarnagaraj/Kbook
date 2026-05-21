package com.khanabook.saas.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final int MAX_SPLIT_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5000;

    @Async("postSplitExecutor")
    public void createPostSplitAsync(Long billId, String easebuzzId, String txnid) {
        try {
            attemptPostSplit(billId, easebuzzId, txnid);
        } catch (Exception e) {
            log.error("Post-split async failed billId={} error={}", billId, e.getMessage());
        }
    }

    private void attemptPostSplit(Long billId, String easebuzzId, String txnid) {
        // Fetch data outside transaction
        Bill bill = billRepo.findById(billId).orElse(null);
        if (bill == null) {
            log.warn("Post-split: Bill not found billId={}", billId);
            return;
        }

        EasebuzzSubMerchant sm;
        try {
            sm = subMerchantService.getByRestaurantId(bill.getRestaurantId());
        } catch (Exception e) {
            log.warn("Post-split: Sub-merchant not found for restaurantId={}", bill.getRestaurantId());
            return;
        }

        if (!"ACTIVE".equals(sm.getStatus()) || sm.getSplitLabel() == null) {
            log.warn("Post-split: Sub-merchant not active or no split label billId={}", billId);
            return;
        }

        // Guard: skip if split already succeeded for this bill (idempotency)
        if (bill.getSettledAt() != null && bill.getCommissionAmount() != null) {
            log.info("Post-split: Bill {} already settled, skipping duplicate attempt", billId);
            return;
        }

        // Generate merchantRequestId ONCE — reused across all retry attempts.
        // Easebuzz deduplicates on their side when same merchantRequestId is replayed.
        // Using billId + easebuzzId prefix ensures uniqueness per transaction.
        String idSuffix = easebuzzId.length() >= 8 ? easebuzzId.substring(0, 8) : easebuzzId;
        String merchantRequestId = "KB" + billId + "_" + idSuffix;

        BigDecimal commissionRate = sm.getCommissionRate() != null ? sm.getCommissionRate() : BigDecimal.ZERO;
        BigDecimal totalAmount = bill.getTotalAmount();
        BigDecimal commissionAmount = totalAmount.multiply(commissionRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal restaurantAmount = totalAmount.subtract(commissionAmount);

        if (restaurantAmount.compareTo(BigDecimal.ZERO) < 0) {
            restaurantAmount = BigDecimal.ZERO;
            commissionAmount = totalAmount;
        }

        List<Map<String, String>> configuration = new ArrayList<>();
        configuration.add(Map.of("label", sm.getSplitLabel(), "amount", String.format("%.2f", restaurantAmount)));
        if (commissionAmount.compareTo(BigDecimal.ZERO) > 0) {
            configuration.add(Map.of("label", "kb_commission", "amount", String.format("%.2f", commissionAmount)));
        }

        String description = "Split for order #" + bill.getDailyOrderDisplay();

        int attempts = 0;
        while (attempts < MAX_SPLIT_ATTEMPTS) {
            attempts++;
            try {
                log.info("Post-split attempt {}/{} billId={} merchantRequestId={}", attempts, MAX_SPLIT_ATTEMPTS, billId, merchantRequestId);
                Map<String, Object> result = easebuzzApi.updateTransactionSplit(
                    merchantRequestId, easebuzzId, String.format("%.2f", totalAmount), description, configuration
                );

                if ("success".equals(result.get("status"))) {
                    log.info("Post-split success billId={} merchantRequestId={}", billId, merchantRequestId);
                    updateBillAfterSplit(billId, commissionAmount);
                    return;
                }

                String error = (String) result.getOrDefault("error", "Unknown error");
                log.warn("Post-split failed billId={} error={}", billId, error);

                if (error != null && error.contains("EBPTSURVE06")) {
                    log.error("Post-split max update attempts reached billId={}", billId);
                    break;
                }

            } catch (Exception e) {
                log.error("Post-split exception billId={} error={}", billId, e.getMessage());
            }

            if (attempts < MAX_SPLIT_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("Post-split exhausted all attempts billId={}", billId);
    }

    @Transactional
    public void updateBillAfterSplit(Long billId, BigDecimal commissionAmount) {
        Bill bill = billRepo.findById(billId).orElse(null);
        if (bill != null) {
            bill.setCommissionAmount(commissionAmount);
            bill.setSettledAt(System.currentTimeMillis());
            billRepo.save(bill);
        }
    }
}
