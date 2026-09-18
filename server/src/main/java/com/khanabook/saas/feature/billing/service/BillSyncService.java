package com.khanabook.saas.feature.billing.service;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.billing.data.BillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Bill-specific sync logic extracted from GenericSyncService.
 * Handles bill validation, idempotency, state machine protection,
 * and payment matching.
 */
@Service
@RequiredArgsConstructor
public class BillSyncService {
    private static final Logger log = LoggerFactory.getLogger(BillSyncService.class);

    private final BillRepository billRepository;
    private final com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository restaurantProfileRepository;

    /**
     * Stamps the bill's business day ({@code lastResetDate}) from its {@code createdAt}
     * in the restaurant's configured timezone.
     *
     * <p>This is deliberately server-authoritative and unconditional: the client also
     * computes a day (BillCreationUseCase) but its local duplicate guard uses a
     * {@code createdAt} timestamp window while the server's guard
     * ({@link BillRepository#findConflictingDailyOrder}) keys on this string. Two bases
     * for one fact drift around midnight and under clock skew, producing spurious
     * "Duplicate order #X already exists" rejections. Deriving it here — exactly as
     * {@code BillServiceImpl.pushData} already does — means only one side decides.
     *
     * <p>Mirrors the existing treatment of {@code terminalSeries} / {@code deviceId},
     * which are likewise overwritten from trusted server-side context.
     */
    public void applyServerBusinessDate(Long tenantId, Bill bill) {
        if (bill == null || bill.getCreatedAt() == null) {
            return;
        }
        java.time.ZoneId zone = restaurantProfileRepository.findByRestaurantId(tenantId)
                .map(profile -> resolveZoneId(profile.getTimezone()))
                .orElseGet(() -> java.time.ZoneId.of(com.khanabook.saas.core.utility.AppConstants.DEFAULT_TIMEZONE));
        String businessDate = java.time.Instant.ofEpochMilli(bill.getCreatedAt())
                .atZone(zone)
                .toLocalDate()
                .toString();
        if (!businessDate.equals(bill.getLastResetDate())) {
            log.debug("Business date normalised for bill localId={} from '{}' to '{}' (zone={})",
                    bill.getLocalId(), bill.getLastResetDate(), businessDate, zone);
        }
        bill.setLastResetDate(businessDate);
    }

    /** Falls back to the default timezone when the profile value is absent or invalid. */
    private java.time.ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return java.time.ZoneId.of(com.khanabook.saas.core.utility.AppConstants.DEFAULT_TIMEZONE);
        }
        try {
            return java.time.ZoneId.of(timezone);
        } catch (RuntimeException ignored) {
            return java.time.ZoneId.of(com.khanabook.saas.core.utility.AppConstants.DEFAULT_TIMEZONE);
        }
    }

    /**
     * Mirrors the client's OrderIdManager format: zero-padded to two digits, prefixed with
     * the terminal series when there is one ("F-02"), bare otherwise ("02").
     */
    static String buildDailyOrderDisplay(String terminalSeries, long dailyOrderId) {
        String counter = String.format("%02d", dailyOrderId);
        if (terminalSeries == null || terminalSeries.isBlank()) {
            return counter;
        }
        return terminalSeries + "-" + counter;
    }

    /**
     * Checks if an order status is considered finalized (terminal state).
     * Once a bill reaches "completed" or "paid", it cannot be reverted.
     */
    public static boolean isFinalizedOrderStatus(String orderStatus) {
        return orderStatus != null
                && (orderStatus.equalsIgnoreCase("completed") || orderStatus.equalsIgnoreCase("paid"));
    }

    /**
     * Resolves numbering conflicts for an incoming bill.
     *
     * <p>Daily order number conflicts are <em>repaired in place</em>: the bill is reassigned
     * the next free number in its (date, terminal series) space. Invoice number conflicts are
     * rejected, because the invoice number is the tax-legal identifier and must never be
     * silently rewritten.
     *
     * @throws IllegalStateException if the bill identity is missing, or a duplicate invoice
     *                               number is detected
     */
    public void validateBillNumberConflicts(
            Long tenantId,
            Bill incomingBill,
            BillRepository billRepo) {
        validateBillNumberConflicts(tenantId, incomingBill, billRepo, new java.util.HashMap<>());
    }

    /**
     * @param batchAssignments numbers already handed out during this push, keyed by
     *                         tenant|date|series. {@code findMaxDailyOrderId} reads committed
     *                         rows only, so without this two conflicting bills in one batch
     *                         would both be assigned the same number and then collide with
     *                         each other — tripping the unique index at {@code saveAll} time,
     *                         which is exactly what the invoice check below is careful to avoid.
     *                         Callers should reuse one map for the whole push.
     */
    public void validateBillNumberConflicts(
            Long tenantId,
            Bill incomingBill,
            BillRepository billRepo,
            java.util.Map<String, Long> batchAssignments) {
        if (Boolean.TRUE.equals(incomingBill.getIsDeleted())) {
            return;
        }
        if (incomingBill.getDeviceId() == null || incomingBill.getLocalId() == null) {
            throw new IllegalStateException("Bill identity missing. Sync again after opening Sync Center.");
        }
        if (incomingBill.getDailyOrderId() != null
                && incomingBill.getLastResetDate() != null
                && !incomingBill.getLastResetDate().isBlank()) {
            billRepo.findConflictingDailyOrder(
                    tenantId,
                    incomingBill.getLastResetDate(),
                    incomingBill.getDailyOrderId(),
                    incomingBill.getDeviceId(),
                    incomingBill.getLocalId(),
                    incomingBill.getTerminalSeries(),
                    incomingBill.getPublicToken())
                    .ifPresent(conflict -> {
                        // Repair instead of reject. Rejecting produced an unrecoverable
                        // loop: the client re-pushed, the server re-rejected, and the
                        // local auto-heal could renumber to the same value because it can
                        // only see local rows. The daily order number is an operational
                        // display value — the tax-legal identifier is invoiceNumber, which
                        // is validated separately below and never rewritten here — so
                        // assigning the next free number in this series is safe and ends
                        // the failure class. The conflict query already excludes the same
                        // publicToken, so this is always a genuinely different bill.
                        String batchKey = tenantId + "|" + incomingBill.getLastResetDate() + "|"
                                + (incomingBill.getTerminalSeries() == null ? "" : incomingBill.getTerminalSeries());
                        long highest = 0L;
                        Long currentMax = billRepo.findMaxDailyOrderId(
                                tenantId,
                                incomingBill.getLastResetDate(),
                                incomingBill.getTerminalSeries());
                        if (currentMax != null) {
                            highest = currentMax;
                        }
                        Long reserved = batchAssignments.get(batchKey);
                        if (reserved != null && reserved > highest) {
                            highest = reserved;
                        }
                        long nextFree = highest + 1L;
                        batchAssignments.put(batchKey, nextFree);
                        String previousDisplay = incomingBill.getDailyOrderDisplay();
                        incomingBill.setDailyOrderId(nextFree);
                        incomingBill.setDailyOrderDisplay(
                                buildDailyOrderDisplay(incomingBill.getTerminalSeries(), nextFree));
                        log.warn("Daily order conflict repaired: bill localId={} device={} was {} "
                                        + "(clashed with billId={}) reassigned to {} for {}",
                                incomingBill.getLocalId(), incomingBill.getDeviceId(), previousDisplay,
                                conflict.getId(), incomingBill.getDailyOrderDisplay(),
                                incomingBill.getLastResetDate());
                    });
        }
        // Pre-flight check mirroring ux_bills_restaurant_invoice_series_active: an
        // identical (financial_year, invoice_series, invoice_sequence) tuple on an
        // active bill means a duplicate invoice is being pushed. Failing it here (as
        // IllegalStateException) routes the bill to failedLocalIds without tripping the
        // unique index at batch save time — a batch DIVE would abort the whole
        // PostgreSQL transaction and burn the rest of the payload.
        if (incomingBill.getInvoiceSeries() != null
                && incomingBill.getFinancialYear() != null
                && incomingBill.getInvoiceSequence() != null) {
            String invoiceNumber = incomingBill.getInvoiceNumber();
            billRepo.findConflictingInvoiceSeries(
                    tenantId,
                    incomingBill.getFinancialYear(),
                    incomingBill.getInvoiceSeries(),
                    incomingBill.getInvoiceSequence(),
                    incomingBill.getDeviceId(),
                    incomingBill.getLocalId())
                    .ifPresent(conflict -> {
                        throw new IllegalStateException(
                                "Duplicate invoice " + (invoiceNumber != null ? invoiceNumber : "")
                                        + " already exists for "
                                        + (conflict.getTerminalSeries() != null
                                                ? "terminal " + conflict.getTerminalSeries()
                                                : "this restaurant")
                                        + ". Resolve it in Sync Center.");
                    });
        }
    }

    /**
     * Compares immutable financial semantics of two BillPayment records
     * to determine whether they represent the exact same logical payment.
     *
     * Matching fields:
     *   - bill_id (or server_bill_id)
     *   - amount (normalized BigDecimal comparison)
     *   - payment_mode
     *   - gateway_txn_id (null-safe)
     *   - gateway_status (null-safe)
     *   - verified_by
     *   - is_deleted state
     *
     * Ignores:
     *   - local primary key (id)
     *   - timestamps
     *   - sync metadata (server_updated_at, is_synced, sync_status)
     *   - server ID populated later
     */
    public boolean isExactPaymentMatch(BillPayment existing, BillPayment incoming) {
        // isDeleted: treat null as false (active) to match Android's default.
        // If one is deleted and the other is active (or null = active), they conflict.
        boolean existingDeleted = existing.getIsDeleted() != null && existing.getIsDeleted();
        boolean incomingDeleted = incoming.getIsDeleted() != null && incoming.getIsDeleted();
        if (existingDeleted != incomingDeleted) {
            return false;
        }
        // Bill identity: match either bill_id (local FK) or server_bill_id.
        boolean billMatch = Objects.equals(existing.getBillId(), incoming.getBillId())
                || (existing.getServerBillId() != null && incoming.getServerBillId() != null
                    && existing.getServerBillId().equals(incoming.getServerBillId()));
        if (!billMatch) {
            return false;
        }
        // Amount: compare BigDecimal values, not scale.
        if (existing.getAmount() == null && incoming.getAmount() != null) return false;
        if (existing.getAmount() != null && incoming.getAmount() == null) return false;
        if (existing.getAmount() != null && existing.getAmount().compareTo(incoming.getAmount()) != 0) {
            return false;
        }
        // Payment mode.
        if (!Objects.equals(existing.getPaymentMode(), incoming.getPaymentMode())) {
            return false;
        }
        // Gateway transaction ID (null-safe).
        if (!Objects.equals(existing.getGatewayTxnId(), incoming.getGatewayTxnId())) {
            return false;
        }
        // Gateway status (null-safe).
        if (!Objects.equals(existing.getGatewayStatus(), incoming.getGatewayStatus())) {
            return false;
        }
        // Verification source.
        if (!Objects.equals(existing.getVerifiedBy(), incoming.getVerifiedBy())) {
            return false;
        }
        return true;
    }

    /**
     * Checks if an incoming bill update is an idempotent retry of a
     * transactional entity (Bill, BillItem, BillPayment).
     */
    public boolean isTransactionalIdempotentRetry(
            com.khanabook.saas.feature.sync.data.BaseSyncEntity incoming,
            com.khanabook.saas.feature.sync.data.BaseSyncEntity existing) {
        boolean transactional = incoming instanceof Bill
                || incoming instanceof com.khanabook.saas.feature.billing.data.BillItem
                || incoming instanceof BillPayment;
        if (!transactional || existing == null || incoming.getLocalId() == null
                || existing.getLocalId() == null
                || !incoming.getLocalId().equals(existing.getLocalId())
                || !Objects.equals(incoming.getDeviceId(), existing.getDeviceId())
                || !Objects.equals(incoming.getRestaurantId(), existing.getRestaurantId())
                || existing.getId() == null) {
            return false;
        }
        // For bills, also verify that the payment/order status hasn't changed —
        // a retry with a different paymentStatus (e.g. pending -> paid) is NOT idempotent.
        if (incoming instanceof Bill incomingBill && existing instanceof Bill existingBill) {
            return Objects.equals(incomingBill.getPaymentStatus(), existingBill.getPaymentStatus())
                    && Objects.equals(incomingBill.getOrderStatus(), existingBill.getOrderStatus());
        }
        return true;
    }

    /**
     * Prevents LWW (Last-Write-Wins) from reverting finalized bill state, while still
     * allowing a deliberate edit through.
     *
     * <p>Bills sync last-write-wins by timestamp, so a stale offline device holding an
     * old copy can push with a newer clock and undo a transition the server already
     * recorded — including a payment the gateway confirmed. Timestamps alone cannot tell
     * that apart from a cashier genuinely cancelling a completed bill.
     *
     * <p>{@code statusVersion} carries the intent: the device increments it only on an
     * explicit user action. A strictly greater incoming version is a deliberate edit and
     * is applied; anything else is treated as stale and the finalized state is restored.
     */
    public void protectBillState(Bill incomingBill, Bill existingBill) {
        if (isDeliberateStatusEdit(incomingBill, existingBill)) {
            return;
        }
        // paymentStatus: "paid" is terminal. Gateway webhook sets it.
        // A stale device push must not revert paid → pending.
        if ("paid".equalsIgnoreCase(existingBill.getPaymentStatus())
                && !"paid".equalsIgnoreCase(incomingBill.getPaymentStatus())) {
            incomingBill.setPaymentStatus(existingBill.getPaymentStatus());
            incomingBill.setPaidAt(existingBill.getPaidAt());
            incomingBill.setGatewayTxnId(existingBill.getGatewayTxnId());
            incomingBill.setGatewayStatus(existingBill.getGatewayStatus());
        }
        // orderStatus: completed/paid/cancelled are terminal.
        // A stale device push must not revert completed → draft.
        if (isFinalizedOrderStatus(existingBill.getOrderStatus())
                && !isFinalizedOrderStatus(incomingBill.getOrderStatus())) {
            incomingBill.setOrderStatus(existingBill.getOrderStatus());
        }
        // cancelled is also terminal — don't un-cancel
        if ("cancelled".equalsIgnoreCase(existingBill.getOrderStatus())
                && !"cancelled".equalsIgnoreCase(incomingBill.getOrderStatus())) {
            incomingBill.setOrderStatus(existingBill.getOrderStatus());
        }
        // Never let a stale push roll the version backwards.
        incomingBill.setStatusVersion(versionOf(existingBill));
    }

    /**
     * True when the incoming push carries a strictly newer {@code statusVersion} than the
     * stored bill, i.e. the device deliberately changed status or payment mode rather than
     * replaying an old copy.
     */
    private boolean isDeliberateStatusEdit(Bill incomingBill, Bill existingBill) {
        return versionOf(incomingBill) > versionOf(existingBill);
    }

    private int versionOf(Bill bill) {
        return bill.getStatusVersion() == null ? 0 : bill.getStatusVersion();
    }

    /**
     * Attempts idempotent recovery for a failed bill save.
     * If the publicToken already exists on the server, treat as success
     * (the previous push succeeded but client didn't get the response).
     *
     * @return the existing bill if idempotent match found, null otherwise
     */
    public Bill attemptIdempotentRecovery(Bill failedBill, Long restaurantId) {
        if (failedBill.getPublicToken() == null) {
            return null;
        }
        var idempotentMatch = billRepository.findByRestaurantIdAndPublicToken(
                restaurantId, failedBill.getPublicToken());
        if (idempotentMatch.isPresent()) {
            Bill existing = idempotentMatch.get();
            log.info("Idempotent recovery: bill publicToken={} already persisted serverId={}",
                    failedBill.getPublicToken(), existing.getId());
            return existing;
        }
        return null;
    }

    /**
     * Generates a public_token for a new bill that doesn't have one.
     */
    public void ensurePublicToken(Bill bill) {
        if (bill.getPublicToken() == null) {
            bill.setPublicToken(java.util.UUID.randomUUID());
        }
    }
}
