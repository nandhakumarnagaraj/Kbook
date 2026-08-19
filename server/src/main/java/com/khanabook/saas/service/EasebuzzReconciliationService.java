package com.khanabook.saas.service;

import com.khanabook.saas.config.EasebuzzProperties;
import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Daily reconciliation job that compares Easebuzz settlement data against
 * internal bill records. Runs every day at 6:00 AM IST.
 *
 * ERA confirmed:
 * - Use /settlements/v1/retrieve with date range
 * - Match by txnid + amount
 * - No replay API exists, so self-reconciliation is mandatory
 * - Max 500 records per page (paginated)
 */
@Service
@RequiredArgsConstructor
public class EasebuzzReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzReconciliationService.class);
    private final EasebuzzApiClient easebuzzApi;
    private final BillRepository billRepo;
    private final EasebuzzProperties props;

    /**
     * Runs daily at 6:00 AM IST. Reconciles yesterday's settlements.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    public void runDailyReconciliation() {
        if (!props.isReconciliationEnabled()) {
            log.debug("Daily reconciliation is disabled");
            return;
        }

        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("Starting daily Easebuzz reconciliation for date={}", yesterday);

        try {
            Map<String, Object> result = reconcileDate(yesterday);
            int matched = (int) result.getOrDefault("matched", 0);
            int mismatches = (int) result.getOrDefault("mismatches", 0);
            int orphans = (int) result.getOrDefault("orphanTransactions", 0);
            int missingWebhooks = (int) result.getOrDefault("missingWebhooks", 0);

            if (mismatches > 0 || orphans > 0 || missingWebhooks > 0) {
                log.warn("RECONCILIATION ALERT date={}: matched={}, mismatches={}, orphanTxns={}, missingWebhooks={}",
                        yesterday, matched, mismatches, orphans, missingWebhooks);
            } else {
                log.info("Reconciliation complete date={}: all {} transactions matched", yesterday, matched);
            }
        } catch (Exception e) {
            log.error("Daily reconciliation failed for date={}: {}", yesterday, e.getMessage(), e);
        }
    }

    /**
     * Reconcile a specific date. Can also be called manually via admin endpoint.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> reconcileDate(String date) {
        Map<String, Object> settlementResponse = easebuzzApi.retrieveSettlements(date);

        boolean apiSuccess = toBool(settlementResponse.get("status"));
        if (!apiSuccess) {
            String error = String.valueOf(settlementResponse.getOrDefault("error", "Unknown error"));
            log.warn("Settlement retrieval failed for date={}: {}", date, error);
            return Map.of("status", "error", "error", error, "date", date);
        }

        // Parse settlement transactions from response
        List<Map<String, Object>> transactions = extractTransactions(settlementResponse);
        if (transactions.isEmpty()) {
            log.info("No settlement transactions found for date={}", date);
            return Map.of("status", "success", "date", date, "matched", 0,
                    "mismatches", 0, "orphanTransactions", 0, "missingWebhooks", 0);
        }

        int matched = 0;
        int mismatches = 0;
        List<Map<String, Object>> orphanTransactions = new ArrayList<>();
        List<Map<String, Object>> amountMismatches = new ArrayList<>();

        for (Map<String, Object> txn : transactions) {
            String txnid = str(txn.get("txnid"));
            String ebAmount = str(txn.get("amount"));
            String ebStatus = str(txn.get("status"));

            if (txnid.isBlank()) continue;

            // Find matching bill by gateway txnid
            Optional<Bill> billOpt = billRepo.findByGatewayTxnId(txnid);
            if (billOpt.isEmpty()) {
                // Easebuzz has a transaction we don't know about
                orphanTransactions.add(Map.of("txnid", txnid, "amount", ebAmount, "status", ebStatus));
                log.warn("RECONCILIATION: Orphan transaction at Easebuzz txnid={} amount={} status={} — no matching bill",
                        txnid, ebAmount, ebStatus);
                continue;
            }

            Bill bill = billOpt.get();

            // Check amount match
            if (!ebAmount.isBlank() && bill.getTotalAmount() != null) {
                try {
                    BigDecimal ebAmt = new BigDecimal(ebAmount);
                    if (ebAmt.compareTo(bill.getTotalAmount()) != 0) {
                        amountMismatches.add(Map.of(
                                "txnid", txnid,
                                "billId", bill.getId(),
                                "ebAmount", ebAmount,
                                "billAmount", bill.getTotalAmount().toPlainString()
                        ));
                        mismatches++;
                        log.warn("RECONCILIATION: Amount mismatch txnid={} billId={} eb={} bill={}",
                                txnid, bill.getId(), ebAmount, bill.getTotalAmount());
                        continue;
                    }
                } catch (NumberFormatException e) {
                    // Skip amount check if unparseable
                }
            }

            // Check status: if Easebuzz says success but our bill isn't paid
            if ("success".equalsIgnoreCase(ebStatus) && !"paid".equalsIgnoreCase(bill.getPaymentStatus())) {
                log.warn("RECONCILIATION: Missing webhook — Easebuzz shows success but bill {} is '{}'. Auto-fixing.",
                        bill.getId(), bill.getPaymentStatus());
                bill.setPaymentStatus("paid");
                bill.setGatewayStatus("success");
                bill.setPaidAt(System.currentTimeMillis());
                billRepo.save(bill);
                mismatches++;
                continue;
            }

            matched++;
        }

        // Also check: bills we marked as paid yesterday that DON'T appear in settlements
        // (potential over-marking from spoofed/duplicate webhooks)
        int missingWebhooks = checkMissingSettlements(date, transactions);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("date", date);
        result.put("totalEasebuzzTxns", transactions.size());
        result.put("matched", matched);
        result.put("mismatches", mismatches);
        result.put("orphanTransactions", orphanTransactions.size());
        result.put("missingWebhooks", missingWebhooks);
        if (!orphanTransactions.isEmpty()) {
            result.put("orphanDetails", orphanTransactions.subList(0, Math.min(20, orphanTransactions.size())));
        }
        if (!amountMismatches.isEmpty()) {
            result.put("amountMismatchDetails", amountMismatches.subList(0, Math.min(20, amountMismatches.size())));
        }
        return result;
    }

    /**
     * Check bills marked as paid on a given date that don't appear in Easebuzz settlements.
     * This catches cases where our webhook handler marked a bill paid but Easebuzz doesn't have it.
     */
    private int checkMissingSettlements(String date, List<Map<String, Object>> ebTransactions) {
        // Build a set of txnids from Easebuzz
        Set<String> ebTxnIds = new HashSet<>();
        for (Map<String, Object> txn : ebTransactions) {
            String txnid = str(txn.get("txnid"));
            if (!txnid.isBlank()) ebTxnIds.add(txnid);
        }

        // Find bills paid on this date with Easebuzz gateway
        LocalDate targetDate = LocalDate.parse(date);
        long dayStart = targetDate.atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        long dayEnd = targetDate.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();

        List<Bill> paidBills = billRepo.findByPaymentStatusAndPaidAtBetween("paid", dayStart, dayEnd);
        int missing = 0;
        for (Bill bill : paidBills) {
            if (bill.getGatewayTxnId() != null && !bill.getGatewayTxnId().isBlank()
                    && bill.getGatewayTxnId().startsWith("KB")
                    && !ebTxnIds.contains(bill.getGatewayTxnId())) {
                log.warn("RECONCILIATION: Bill {} (txnid={}) marked paid but NOT found in Easebuzz settlements for {}",
                        bill.getId(), bill.getGatewayTxnId(), date);
                missing++;
            }
        }
        return missing;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTransactions(Map<String, Object> response) {
        // Easebuzz settlement response may have transactions in different structures
        Object msg = response.get("msg");
        if (msg instanceof List) {
            return (List<Map<String, Object>>) msg;
        }
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        Object transactions = response.get("peb_transactions");
        if (transactions instanceof List) {
            return (List<Map<String, Object>>) transactions;
        }
        // Try nested msg.transactions
        if (msg instanceof Map) {
            Object nested = ((Map<String, Object>) msg).get("transactions");
            if (nested instanceof List) {
                return (List<Map<String, Object>>) nested;
            }
        }
        return Collections.emptyList();
    }

    private boolean toBool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        String s = value.toString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private String str(Object value) {
        return value != null ? value.toString().trim() : "";
    }
}
