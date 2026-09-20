package com.khanabook.saas.feature.payments.service;

import com.khanabook.saas.feature.billing.service.PostSplitService;
import com.khanabook.saas.feature.compliance.data.FssaiTracker;
import com.khanabook.saas.feature.notifications.service.PushNotificationService;
import com.khanabook.saas.feature.payments.service.EasebuzzProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEvent;
import com.khanabook.saas.feature.payments.data.EasebuzzPayout;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzWebhookEventRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzPayoutRepository;
import com.khanabook.saas.feature.payments.data.EasebuzzSubMerchantRepository;
import com.khanabook.saas.feature.compliance.data.FssaiRenewalRepository;
import com.khanabook.saas.feature.compliance.data.FssaiTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EasebuzzWebhookService {

    private static final Logger log = LoggerFactory.getLogger(EasebuzzWebhookService.class);
    private final BillRepository billRepo;
    private final SubMerchantService subMerchantService;
    private final EasebuzzSubMerchantRepository subMerchantRepo;
    private final EasebuzzWebhookEventRepository webhookEventRepo;
    private final EasebuzzProperties props;
    private final PostSplitService postSplitService;
    private final EasebuzzPayoutRepository payoutRepo;
    private final org.springframework.core.env.Environment env;
    private final PushNotificationService pushNotificationService;
    private final FssaiRenewalRepository fssaiRenewalRepo;
    private final FssaiTrackerRepository fssaiTrackerRepo;
    private final WebhookRetryService webhookRetryService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EasebuzzWireApiClient wireApiClient;

    @Transactional
    public Map<String, Object> handlePaymentWebhook(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String status = payload.get("status");

        // Replay/duplicate webhook guard
        if (txnid != null && status != null) {
            boolean alreadyProcessed = webhookEventRepo.existsByTxnIdAndStatus(txnid, status);
            if (alreadyProcessed) {
                log.info("Duplicate webhook ignored txnid={} status={}", txnid, status);
                return Map.of("status", "ok", "message", "already processed");
            }
        }

        // Verify webhook hash before processing
        if (!verifyWebhookHash(payload)) {
            log.warn("Payment webhook hash mismatch txnid={}", txnid);
            return Map.of("status", "hash_mismatch");
        }

        String easebuzzId = payload.getOrDefault("easebuzz_id", payload.get("easepayid"));
        String amountStr = payload.get("amount");
        String udf1 = payload.get("udf1");

        log.info("Payment webhook received txnid={} status={}", txnid, status);
        if ("success".equalsIgnoreCase(status)) {
            if ("fssai_renewal".equalsIgnoreCase(udf1)) {
                handleFssaiRenewalSuccess(payload);
            } else {
                try {
                    resolveBillFromPayload(payload).ifPresent(bill -> {
                        Long billId = bill.getId();
                        // Idempotency guard: skip if already marked paid
                        if ("paid".equals(bill.getPaymentStatus())) {
                            log.info("Bill {} already paid, skipping duplicate webhook txnid={}", billId, txnid);
                            return;
                        }
                        bill.setGatewayTxnId(txnid);
                        bill.setGatewayStatus("success");
                        bill.setPaymentStatus("paid");
                        bill.setPaidAt(System.currentTimeMillis());
                        if (amountStr != null) {
                            bill.setSettledAmount(new BigDecimal(amountStr));
                        }
                        billRepo.save(bill);
                        saveGatewayEvent(bill, txnid, easebuzzId, status, amountStr, payload);
                        log.info("Bill {} marked paid via webhook txnid={}", billId, txnid);

                        // Race note: if a concurrent twin delivery wins the unique-index
                        // race on (txn_id, status) at saveGatewayEvent, the loser's insert
                        // throws DataIntegrityViolationException, which marks this whole
                        // transaction rollback-only — every side effect above (bill save,
                        // push, post-split) rolls back atomically. Easebuzz retries the
                        // delivery; the retry then hits the existsByTxnIdAndStatus guard
                        // above and no-ops. Net effect: duplicate deliveries can never
                        // double-process side effects.

                        // Send push notification for payment received
                        String displayOrder = bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : "#" + billId;
                        String amountDisplay = amountStr != null ? "₹" + amountStr : "";
                        pushNotificationService.pushToRestaurant(
                            bill.getRestaurantId(),
                            "Payment Received",
                            "Order " + displayOrder + " — " + amountDisplay + " paid successfully",
                            "payment_received",
                            String.valueOf(billId),
                            "bill",
                            amountStr != null ? new BigDecimal(amountStr) : null
                        );

                        // Trigger post-transaction split
                        if (easebuzzId != null && !easebuzzId.isBlank()) {
                            postSplitService.createPostSplitAsync(billId, easebuzzId, txnid);
                        } else {
                            log.warn("Post-split skipped for billId={} : missing easebuzz_id", billId);
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to process payment webhook for txnid={}, enqueueing for retry: {}", txnid, e.getMessage(), e);
                    try {
                        webhookRetryService.enqueue("PAYMENT", objectMapper.writeValueAsString(payload));
                    } catch (Exception jsonEx) {
                        webhookRetryService.enqueue("PAYMENT", payload.toString());
                    }
                }
            }
        } else if ("auto refunded".equalsIgnoreCase(status) || "auto_refunded".equalsIgnoreCase(status)) {
            // Handle auto-refunded: customer was debited but transaction failed, funds auto-returned
            // (ERA 2026-09-09: status arrives via the payment webhook; both spellings have been observed)
            resolveBillFromPayload(payload).ifPresent(bill -> {
                Long billId = bill.getId();
                if ("paid".equals(bill.getPaymentStatus())) {
                    log.warn("Bill {} was paid but received auto-refunded webhook txnid={} — possible reversal", billId, txnid);
                    return;
                }
                bill.setGatewayStatus("auto_refunded");
                bill.setPaymentStatus("failed");
                billRepo.save(bill);
                log.info("Bill {} marked as auto-refunded via webhook txnid={}", billId, txnid);
            });
        } else if ("expired".equalsIgnoreCase(status)
                || "failure".equalsIgnoreCase(status) || "dropped".equalsIgnoreCase(status)
                || "bounced".equalsIgnoreCase(status) || "userCancelled".equalsIgnoreCase(status)) {
            if (!"fssai_renewal".equalsIgnoreCase(udf1)) {
                resolveBillFromPayload(payload).ifPresent(bill -> {
                    Long billId = bill.getId();
                    if ("paid".equals(bill.getPaymentStatus())) {
                        log.info("Bill {} already paid, ignoring failure webhook txnid={} status={}", billId, txnid, status);
                        return;
                    }
                    bill.setGatewayStatus(status.toLowerCase());
                    // ERA 2026-09-09: EasyCollect links expire (~7 days) and the webhook
                    // reports "expired". Clear the stale link (and link_sent state) so the
                    // POS can create a fresh link for the same bill.
                    if ("expired".equalsIgnoreCase(status)) {
                        bill.setGatewayTxnId(null);
                        if ("link_sent".equalsIgnoreCase(bill.getPaymentStatus())) {
                            bill.setPaymentStatus("pending");
                        }
                    }
                    billRepo.save(bill);
                    log.info("Bill {} gateway status updated to {} via webhook txnid={}", billId, status, txnid);
                });
            }
        }
        return Map.of("status", "received");
    }

    private Optional<Bill> resolveBillFromPayload(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String udf1 = payload.get("udf1");
        
        if (udf1 != null && !udf1.isBlank()) {
            try {
                return billRepo.findById(Long.parseLong(udf1));
            } catch (NumberFormatException e) {
                log.warn("Invalid billId in udf1: {}", udf1);
            }
        }
        
        if (txnid != null && !txnid.isBlank()) {
            return billRepo.findByGatewayTxnId(txnid);
        }
        
        return Optional.empty();
    }

    private void handleFssaiRenewalSuccess(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String easebuzzId = payload.getOrDefault("easebuzz_id", payload.get("easepayid"));
        String amountStr = payload.get("amount");

        fssaiRenewalRepo.findByEasebuzzTxnId(txnid).ifPresent(renewal -> {
            if ("SUCCESS".equals(renewal.getStatus())) {
                log.info("FSSAI renewal transaction {} already processed", txnid);
                return;
            }
            renewal.setStatus("SUCCESS");
            renewal.setUpdatedAt(System.currentTimeMillis());
            fssaiRenewalRepo.save(renewal);

            // Update FssaiTracker status to RENEWAL_PAID
            fssaiTrackerRepo.findByRestaurantId(renewal.getRestaurantId()).ifPresent(tracker -> {
                tracker.setStatus("RENEWAL_PAID");
                tracker.setLastUpdatedOn(java.time.LocalDate.now());
                fssaiTrackerRepo.save(tracker);
            });

            log.info("FSSAI renewal status updated to SUCCESS for restaurantId={} txnid={}",
                    renewal.getRestaurantId(), txnid);

            // Send push notification to confirm FSSAI renewal payment received
            pushNotificationService.pushToRestaurant(
                    renewal.getRestaurantId(),
                    "FSSAI Renewal Paid",
                    "Your FSSAI renewal payment of ₹" + amountStr + " was received successfully. We are processing your compliance renewal.",
                    "system",
                    renewal.getFssaiNumber(),
                    "fssai",
                    amountStr != null ? new BigDecimal(amountStr) : BigDecimal.ZERO
            );
        });
    }

    @Transactional
    public Map<String, Object> handleRefundWebhook(Map<String, String> payload) {
        String txnid = payload.get("txnid");
        String status = payload.get("status");
        String easepayid = payload.get("easepayid");
        String refundId = payload.get("refund_id");
        String refundAmount = payload.get("refund_amount");

        log.info("Refund webhook received txnid={} status={} easepayid={} refundId={}", txnid, status, easepayid, refundId);

        // Verify refund webhook hash: key|easepayid|salt
        if (!verifyRefundWebhookHash(payload)) {
            log.warn("Refund webhook hash mismatch txnid={}", txnid);
            return Map.of("status", "hash_mismatch");
        }

        if (txnid == null || txnid.isBlank()) {
            log.warn("Refund webhook missing txnid");
            return Map.of("status", "received");
        }

        // Find bill by gateway txnId and update refund status
        try {
            billRepo.findByGatewayTxnId(txnid).ifPresent(bill -> {
                updateBillRefund(bill, status, refundId, refundAmount, payload);
            });
        } catch (Exception e) {
            log.error("Failed to process refund webhook for txnid={}, enqueueing for retry: {}", txnid, e.getMessage(), e);
            try {
                webhookRetryService.enqueue("REFUND", objectMapper.writeValueAsString(payload));
            } catch (Exception jsonEx) {
                webhookRetryService.enqueue("REFUND", payload.toString());
            }
        }

        return Map.of("status", "received");
    }

    private boolean verifyRefundWebhookHash(Map<String, String> payload) {
        try {
            String receivedHash = payload.get("hash");
            if (receivedHash == null || receivedHash.isBlank()) return false;

            // Refund webhook hash: sha512(key|easepayid|salt)
            // Easebuzz may send the ID as either easepayid or easebuzz_id
            String easepayid = payload.get("easepayid");
            if (easepayid == null || easepayid.isBlank()) {
                easepayid = payload.get("easebuzz_id");
            }
            if (easepayid == null || easepayid.isBlank()) {
                log.warn("Refund webhook missing easepayid/easebuzz_id, cannot verify hash");
                return false;
            }
            String hashInput = props.getMerchantKey() + "|" + easepayid.trim() + "|" + props.getSalt();
            String computedHash = sha512(hashInput);

            String receivedHashClean = receivedHash.trim().toLowerCase();
            String computedHashClean = computedHash.trim().toLowerCase();
            return MessageDigest.isEqual(computedHashClean.getBytes(StandardCharsets.UTF_8), receivedHashClean.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Refund webhook hash verification failed", e);
            return false;
        }
    }

    private void updateBillRefund(Bill bill, String status, String refundId, String refundAmount, Map<String, String> payload) {
        long now = System.currentTimeMillis();

        // Idempotency: skip if already marked refunded
        if ("refunded".equalsIgnoreCase(bill.getPaymentStatus())) {
            log.info("Bill {} already refunded, skipping duplicate refund webhook", bill.getId());
            return;
        }

        if (refundId != null && !refundId.isBlank()) {
            bill.setRefundId(refundId);
        }
        if (refundAmount != null && !refundAmount.isBlank()
                && (bill.getRefundAmount() == null || bill.getRefundAmount().compareTo(java.math.BigDecimal.ZERO) == 0)) {
            try {
                // The refund initiation path records the cumulative amount
                // before this asynchronous webhook arrives. Preserve it so a
                // partial-refund webhook cannot overwrite the running total.
                bill.setRefundAmount(new BigDecimal(refundAmount));
            } catch (Exception e) {
                log.warn("Invalid refund_amount in webhook: {}", refundAmount);
            }
        }

        if ("refunded".equalsIgnoreCase(status)) {
            bill.setPaymentStatus("refunded");
            bill.setGatewayStatus("refunded");

            // Push refund-completed notification to restaurant
            String displayOrder = bill.getDailyOrderDisplay() != null ? bill.getDailyOrderDisplay() : "#" + bill.getId();
            String amountDisplay = refundAmount != null ? "₹" + refundAmount : "";
            pushNotificationService.pushToRestaurant(
                bill.getRestaurantId(),
                "Refund Completed",
                "Order " + displayOrder + " — " + amountDisplay + " refunded to customer",
                "refund",
                String.valueOf(bill.getId()),
                "bill",
                refundAmount != null ? new java.math.BigDecimal(refundAmount) : null
            );
        } else if ("queued".equalsIgnoreCase(status) || "accepted".equalsIgnoreCase(status)) {
            bill.setGatewayStatus("refund_" + status.toLowerCase());
        } else {
            bill.setGatewayStatus("refund_" + (status != null ? status.toLowerCase() : "unknown"));
        }

        billRepo.save(bill);

        // Save/update webhook event for audit trail (upsert pattern)
        String easepayid = payload.get("easepayid");
        if (easepayid == null) easepayid = payload.get("easebuzz_id");
        EasebuzzWebhookEvent event;
        if (easepayid != null && !easepayid.isBlank()) {
            event = webhookEventRepo.findByRestaurantIdAndTxnId(bill.getRestaurantId(), bill.getGatewayTxnId())
                    .orElseGet(EasebuzzWebhookEvent::new);
            event.setEasebuzzId(easepayid);
        } else {
            event = new EasebuzzWebhookEvent();
        }
        event.setRestaurantId(bill.getRestaurantId());
        event.setTxnId(bill.getGatewayTxnId() != null ? bill.getGatewayTxnId() : "");
        event.setStatus("refund_" + (status != null ? status : "unknown"));
        if (refundAmount != null) {
            try { event.setAmount(new BigDecimal(refundAmount)); } catch (Exception ignored) {}
        }
        event.setRawPayload(payload.toString());
        event.setReceivedAt(now);
        webhookEventRepo.save(event);

        log.info("Bill {} refund status updated via webhook: {} refundId={}", bill.getId(), status, refundId);
    }

    @Transactional
    public Map<String, Object> handlePayoutWebhook(Map<String, ?> rawPayload) {
        Map<String, String> payload = new HashMap<>();
        Object dataObj = rawPayload.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    payload.put(entry.getKey().toString(), entry.getValue().toString());
                }
            }
        }
        // Copy top-level entries (if not already populated from data, or as fallback)
        for (Map.Entry<String, ?> entry : rawPayload.entrySet()) {
            if (!"data".equals(entry.getKey()) && entry.getValue() != null) {
                payload.putIfAbsent(entry.getKey(), entry.getValue().toString());
            }
        }

        String requestId = payload.getOrDefault("unique_request_number", payload.get("merchant_request_id"));
        String payoutId = payload.get("payout_id");
        String rawStatus = payload.get("status");
        if (rawStatus == null && rawPayload.get("status") != null) {
            rawStatus = rawPayload.get("status").toString();
        }
        final String resolvedStatus = ("1".equals(rawStatus) || "success".equalsIgnoreCase(rawStatus))
                ? "success"
                : ("0".equals(rawStatus) ? "failure" : (rawStatus != null ? rawStatus.toLowerCase() : "unknown"));

        String utr = payload.getOrDefault("bank_transaction_id", payload.get("unique_transaction_reference"));

        // Payout webhook hash verification
        if (!verifyPayoutHash(payload)) {
            log.warn("Payout webhook hash mismatch requestId={} payoutId={}", requestId, payoutId);
            return Map.of("status", "hash_mismatch");
        }

        log.info("Payout webhook received requestId={} payoutId={} status={} utr={}", requestId, payoutId, resolvedStatus, utr);

        java.util.Optional<EasebuzzPayout> foundPayout = (requestId != null && !requestId.isBlank())
                ? payoutRepo.findByMerchantRequestId(requestId)
                : ((payoutId != null && !payoutId.isBlank()) ? payoutRepo.findByPayoutId(payoutId) : java.util.Optional.empty());

        foundPayout.ifPresent(payout -> {
            payout.setStatus(resolvedStatus);
            if (utr != null && !utr.isBlank()) payout.setUtr(utr);
            if (payoutId != null && !payoutId.isBlank() && payout.getPayoutId() == null) {
                payout.setPayoutId(payoutId);
            }
            if ("failure".equalsIgnoreCase(resolvedStatus)) {
                payout.setErrorMessage(payload.get("failure_reason"));
            }
            payout.setUpdatedAt(System.currentTimeMillis());
            payoutRepo.save(payout);
            log.info("Updated payout record {} to status={}", requestId != null ? requestId : payoutId, resolvedStatus);
            
            try {
                String amountDisplay = payout.getAmount() != null ? "₹" + payout.getAmount() : "";
                pushNotificationService.pushToRestaurant(
                    payout.getRestaurantId(),
                    "Payout Status Updated",
                    "Payout of " + amountDisplay + " status updated to: " + resolvedStatus,
                    "settlement",
                    String.valueOf(payout.getId()),
                    "payout",
                    payout.getAmount()
                );
            } catch (Exception e) {
                log.warn("Failed to push payout status notification: {}", e.getMessage());
            }
        });

        return Map.of("status", "received");
    }

    public Map<String, Object> handleSubMerchantWebhook(Map<String, Object> payload) {
        // Verify sub-merchant webhook hash before processing
        if (!verifySubMerchantWebhookHash(payload)) {
            log.warn("Sub-merchant webhook hash mismatch");
            return Map.of("status", "hash_mismatch");
        }

        subMerchantService.processWebhook(payload);
        return Map.of("status", "received");
    }

    /**
     * Public wrapper for verifyWebhookHash — used by the return endpoint to verify
     * the reverse hash on Easebuzz redirect params (same hash sequence as payment webhook).
     */
    public boolean verifyPaymentReturnHash(Map<String, String> params) {
        return verifyWebhookHash(params);
    }

    private boolean isDevOrSandboxProfile() {
        if (env == null) return false;
        for (String profile : env.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile) || "sandbox".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private boolean verifyWebhookHash(Map<String, String> payload) {
        try {
            String receivedHash = payload.get("hash");
            if (receivedHash == null || receivedHash.isBlank()) {
                return false;
            }
            // Easebuzz Payment Webhook Hash Sequence (Reversed):
            // sha512(salt|status|udf10|udf9|udf8|udf7|udf6|udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
            StringBuilder sb = new StringBuilder();
            sb.append(props.getSalt()).append("|");
            sb.append(nullSafe(payload.get("status"))).append("|");
            sb.append(nullSafe(payload.get("udf10"))).append("|");
            sb.append(nullSafe(payload.get("udf9"))).append("|");
            sb.append(nullSafe(payload.get("udf8"))).append("|");
            sb.append(nullSafe(payload.get("udf7"))).append("|");
            sb.append(nullSafe(payload.get("udf6"))).append("|");
            sb.append(nullSafe(payload.get("udf5"))).append("|");
            sb.append(nullSafe(payload.get("udf4"))).append("|");
            sb.append(nullSafe(payload.get("udf3"))).append("|");
            sb.append(nullSafe(payload.get("udf2"))).append("|");
            sb.append(nullSafe(payload.get("udf1"))).append("|");
            sb.append(nullSafe(payload.get("email"))).append("|");
            sb.append(nullSafe(payload.get("firstname"))).append("|");
            sb.append(nullSafe(payload.get("productinfo"))).append("|");
            sb.append(nullSafe(payload.get("amount"))).append("|");
            sb.append(nullSafe(payload.get("txnid"))).append("|");
            sb.append(props.getMerchantKey());

            String computedHash = sha512(sb.toString());
            String receivedHashClean = receivedHash.trim().toLowerCase();
            String computedHashClean = computedHash.trim().toLowerCase();
            return MessageDigest.isEqual(computedHashClean.getBytes(StandardCharsets.UTF_8), receivedHashClean.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Payment hash verification failed", e);
            return false;
        }
    }

    private boolean verifyPayoutHash(Map<String, String> payload) {
        try {
            String receivedHash = payload.get("hash");
            if (receivedHash == null || receivedHash.isBlank()) return false;

            String receivedHashClean = receivedHash.trim().toLowerCase();

            // Scenario 1: Settlement Payout Webhook (key|payout_id|salt)
            if (payload.containsKey("payout_id") && !payload.containsKey("beneficiary_account_number")) {
                StringBuilder sb = new StringBuilder();
                sb.append(props.getMerchantKey()).append("|");
                sb.append(nullSafe(payload.get("payout_id"))).append("|");
                sb.append(props.getSalt());
                String computedHash1 = sha512(sb.toString()).trim().toLowerCase();
                if (MessageDigest.isEqual(computedHash1.getBytes(StandardCharsets.UTF_8), receivedHashClean.getBytes(StandardCharsets.UTF_8))) return true;
            }

            // Scenario 2: Transfer (Payout V2) Webhook 
            // Hash: key|beneficiary_account_number|ifsc|beneficiary_upi_handle|unique_request_number|amount|unique_transaction_reference|status|salt
            StringBuilder sb = new StringBuilder();
            sb.append(props.getMerchantKey()).append("|");
            sb.append(nullSafe(payload.get("beneficiary_account_number"))).append("|");
            sb.append(nullSafe(payload.get("ifsc"))).append("|");
            sb.append(nullSafe(payload.get("beneficiary_upi_handle"))).append("|");
            sb.append(nullSafe(payload.get("unique_request_number"))).append("|");
            sb.append(nullSafe(payload.get("amount"))).append("|");
            sb.append(nullSafe(payload.get("unique_transaction_reference"))).append("|");
            sb.append(nullSafe(payload.get("status"))).append("|");
            sb.append(props.getSalt());

            String computedHash2 = sha512(sb.toString()).trim().toLowerCase();
            return MessageDigest.isEqual(computedHash2.getBytes(StandardCharsets.UTF_8), receivedHashClean.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Payout hash verification failed", e);
            return false;
        }
    }

    private boolean verifySubMerchantWebhookHash(Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = payload.get("data") instanceof Map ? (Map<String, Object>) payload.get("data") : payload;

            String receivedHash = data.get("hash") != null ? data.get("hash").toString() :
                    (payload.get("hash") != null ? payload.get("hash").toString() : null);

            String subMerchantId = SubMerchantService.resolveSubMerchantId(data);

            // If hash is provided, verify it strictly: sha512(key|submerchant_id|salt)
            if (receivedHash != null && !receivedHash.isBlank()) {
                if (subMerchantId == null || subMerchantId.isBlank()) {
                    log.warn("Sub-merchant webhook has hash but missing submerchant_id");
                    return false;
                }
                String hashInput = props.getMerchantKey() + "|" + subMerchantId + "|" + props.getSalt();
                String computedHash = sha512(hashInput);

                String receivedHashClean = receivedHash.trim().toLowerCase();
                String computedHashClean = computedHash.trim().toLowerCase();
                boolean match = MessageDigest.isEqual(computedHashClean.getBytes(StandardCharsets.UTF_8), receivedHashClean.getBytes(StandardCharsets.UTF_8));
                if (!match) {
                    log.warn("Sub-merchant webhook hash mismatch for submerchant_id={}", subMerchantId);
                }
                return match;
            }

            // If no hash is provided, permit official Easebuzz MERCHANT_KYC_APPROVAL event only after verification
            Object eventObj = payload.get("event");
            if (eventObj != null && "MERCHANT_KYC_APPROVAL".equalsIgnoreCase(eventObj.toString())) {
                String email = data.containsKey("email") && data.get("email") != null ? data.get("email").toString() : null;
                boolean exists = false;
                if (subMerchantId != null && !subMerchantId.isBlank()) {
                    exists = subMerchantRepo.findBySubMerchantId(subMerchantId).isPresent();
                }
                if (!exists && email != null && !email.isBlank()) {
                    exists = subMerchantRepo.findByContactEmail(email).isPresent();
                }
                if (exists) {
                    if (isDevOrSandboxProfile()) {
                        // SECURITY_AUDIT: unhashed KYC acceptance is a trust-on-secondary-
                        // evidence decision. In prod this line should never fire (the WIRE
                        // API check above is the sanctioned path) — alert on it.
                        log.warn("SECURITY_AUDIT: unhashed MERCHANT_KYC_APPROVAL accepted via dev/sandbox bypass (subMerchantId={}, email={})", subMerchantId, email);
                        return true;
                    }
                    if (wireApiClient != null) {
                        try {
                            Map<String, Object> wireCheck = (email != null && !email.isBlank())
                                    ? wireApiClient.getSubMerchantByEmail(email)
                                    : (subMerchantId != null ? wireApiClient.getSubMerchantByKey(subMerchantId) : null);
                            if (wireCheck != null && Boolean.TRUE.equals(wireCheck.get("success"))) {
                                log.info("Verified unhashed MERCHANT_KYC_APPROVAL event via official WIRE API for email={}", email);
                                return true;
                            }
                        } catch (Exception e) {
                            log.warn("Server-to-server WIRE verification failed for unhashed webhook: {}", e.getMessage());
                        }
                    }
                    log.warn("Rejected unverified unhashed MERCHANT_KYC_APPROVAL event in production (subMerchantId={}, email={})", subMerchantId, email);
                    return false;
                } else {
                    log.warn("Rejected unhashed MERCHANT_KYC_APPROVAL event for unknown entity (subMerchantId={}, email={})", subMerchantId, email);
                    return false;
                }
            }

            log.warn("Sub-merchant webhook missing hash or submerchant_id in data");
            return false;
        } catch (Exception e) {
            log.error("Sub-merchant webhook hash verification failed", e);
            return false;
        }
    }

    private void saveGatewayEvent(Bill bill, String txnid, String easebuzzId, String status, String amount, Map<String, String> payload) {
        EasebuzzWebhookEvent event = new EasebuzzWebhookEvent();
        event.setRestaurantId(bill.getRestaurantId());
        event.setTxnId(txnid);
        event.setEasebuzzId(easebuzzId);
        event.setStatus(status);
        if (amount != null) event.setAmount(new BigDecimal(amount));
        event.setRawPayload(payload.toString());
        event.setReceivedAt(System.currentTimeMillis());
        webhookEventRepo.save(event);
    }

    private String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    private String sha512(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte b : digest) {
            hash.append(String.format("%02x", b));
        }
        return hash.toString();
    }
}
