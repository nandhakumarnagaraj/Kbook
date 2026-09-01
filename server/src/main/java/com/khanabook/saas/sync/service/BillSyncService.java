package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.Bill;
import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.repository.BillRepository;
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

    /**
     * Checks if an order status is considered finalized (terminal state).
     * Once a bill reaches "completed" or "paid", it cannot be reverted.
     */
    public static boolean isFinalizedOrderStatus(String orderStatus) {
        return orderStatus != null
                && (orderStatus.equalsIgnoreCase("completed") || orderStatus.equalsIgnoreCase("paid"));
    }

    /**
     * Validates that a bill doesn't conflict with existing daily order numbers
     * for the same terminal series, device, and date.
     *
     * @throws IllegalStateException if a conflict is detected
     */
    public void validateBillNumberConflicts(
            Long tenantId,
            Bill incomingBill,
            BillRepository billRepo) {
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
                    incomingBill.getTerminalSeries())
                    .ifPresent(conflict -> {
                        throw new IllegalStateException(
                                "Duplicate order #" + incomingBill.getDailyOrderDisplay()
                                        + " already exists for " + incomingBill.getLastResetDate()
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
            com.khanabook.saas.sync.entity.BaseSyncEntity incoming,
            com.khanabook.saas.sync.entity.BaseSyncEntity existing) {
        boolean transactional = incoming instanceof Bill
                || incoming instanceof com.khanabook.saas.entity.BillItem
                || incoming instanceof BillPayment;
        return transactional
                && existing != null
                && incoming.getLocalId() != null
                && existing.getLocalId() != null
                && incoming.getLocalId().equals(existing.getLocalId())
                && Objects.equals(incoming.getDeviceId(), existing.getDeviceId())
                && Objects.equals(incoming.getRestaurantId(), existing.getRestaurantId())
                && existing.getId() != null;
    }

    /**
     * Prevents LWW (Last-Write-Wins) from reverting finalized bill state.
     * Once a bill reaches a terminal state (paid, completed, cancelled),
     * a stale device push with a higher timestamp must not undo the transition.
     */
    public void protectBillState(Bill incomingBill, Bill existingBill) {
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
