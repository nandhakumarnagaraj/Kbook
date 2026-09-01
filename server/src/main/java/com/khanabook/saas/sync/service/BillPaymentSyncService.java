package com.khanabook.saas.sync.service;

import com.khanabook.saas.entity.BillPayment;
import com.khanabook.saas.repository.BillPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Bill-payment idempotency and dedup logic extracted from GenericSyncService.
 * Handles gateway_txn_id and operation_id deduplication for new BillPayment
 * records that arrive with a fresh localId but already exist on the server.
 */
@Service
@RequiredArgsConstructor
public class BillPaymentSyncService {
    private static final Logger log = LoggerFactory.getLogger(BillPaymentSyncService.class);

    private final BillPaymentRepository billPaymentRepository;
    private final BillSyncService billSyncService;

    /**
     * Result of an idempotency check: either a matched existing payment (success)
     * or a conflict message (failure), or null if no identity match was found.
     */
    public record IdempotencyResult(
            boolean matched,
            BillPayment existingPayment,
            String conflictReason
    ) {
        public static IdempotencyResult success(BillPayment existing) {
            return new IdempotencyResult(true, existing, null);
        }

        public static IdempotencyResult conflict(String reason) {
            return new IdempotencyResult(true, null, reason);
        }

        public static IdempotencyResult notFound() {
            return new IdempotencyResult(false, null, null);
        }
    }

    /**
     * Checks if an incoming BillPayment is an idempotent retry by matching
     * gateway_txn_id or operation_id against existing records.
     *
     * When the identity already exists, compares full semantic fields:
     * - If they match exactly → idempotent success (return existing)
     * - If any field differs → conflict (caller must reject)
     * - If no identity match found → not found (caller proceeds with normal insert)
     */
    public IdempotencyResult checkIdempotency(Long tenantId, BillPayment newBillPayment) {
        // Priority 1: gateway_txn_id (globally-unique gateway transaction ID)
        String txnId = newBillPayment.getGatewayTxnId();
        if (txnId != null && !txnId.isBlank()) {
            Optional<BillPayment> existingGateway =
                    billPaymentRepository.findByRestaurantIdAndGatewayTxnId(tenantId, txnId);
            if (existingGateway.isPresent()) {
                BillPayment existing = existingGateway.get();
                if (billSyncService.isExactPaymentMatch(existing, newBillPayment)) {
                    log.info("Idempotent gateway payment retry localId={} txnId={} tenantId={}",
                            newBillPayment.getLocalId(), txnId, tenantId);
                    return IdempotencyResult.success(existing);
                } else {
                    log.error("CONFLICT: Gateway txnId={} reused with different semantics on restaurant={}: " +
                                    "existing billId={} amount={} mode={} vs incoming localId={} billId={} amount={} mode={}",
                            txnId, tenantId,
                            existing.getBillId(), existing.getAmount(), existing.getPaymentMode(),
                            newBillPayment.getLocalId(), newBillPayment.getBillId(),
                            newBillPayment.getAmount(), newBillPayment.getPaymentMode());
                    return IdempotencyResult.conflict(
                            "Payment gateway transaction ID conflicts with an existing payment with different details. Contact support.");
                }
            }
        }

        // Priority 2: operation_id (Android-generated payment component identity)
        String opId = newBillPayment.getOperationId();
        if (opId != null && !opId.isBlank()) {
            Optional<BillPayment> existingOp =
                    billPaymentRepository.findByRestaurantIdAndOperationId(tenantId, opId);
            if (existingOp.isPresent()) {
                BillPayment existing = existingOp.get();
                if (billSyncService.isExactPaymentMatch(existing, newBillPayment)) {
                    log.info("Idempotent operation payment retry localId={} opId={} tenantId={}",
                            newBillPayment.getLocalId(), opId, tenantId);
                    return IdempotencyResult.success(existing);
                } else {
                    log.error("CONFLICT: Operation opId={} reused with different semantics on restaurant={}: " +
                                    "existing billId={} amount={} mode={} vs incoming localId={} billId={} amount={} mode={}",
                            opId, tenantId,
                            existing.getBillId(), existing.getAmount(), existing.getPaymentMode(),
                            newBillPayment.getLocalId(), newBillPayment.getBillId(),
                            newBillPayment.getAmount(), newBillPayment.getPaymentMode());
                    return IdempotencyResult.conflict(
                            "Payment operation identity conflicts with an existing payment with different details. Contact support.");
                }
            }
        }

        return IdempotencyResult.notFound();
    }
}
