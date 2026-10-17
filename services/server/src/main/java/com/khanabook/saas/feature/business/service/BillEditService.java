package com.khanabook.saas.feature.business.service;

import com.khanabook.saas.feature.auth.service.SecurityAuditService;
import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.billing.data.BillPayment;
import com.khanabook.saas.feature.billing.data.BillPaymentRepository;
import com.khanabook.saas.feature.billing.data.BillRepository;
import com.khanabook.saas.feature.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Owner-only corrections to a bill that has already been recorded.
 *
 * <p>This is the web-admin counterpart of the device's
 * {@code BillRepository.updateOrderStatus / updatePaymentMode}. It deliberately follows the
 * same contract, because the two surfaces feed one sync protocol:
 *
 * <ul>
 *   <li>every accepted edit <b>bumps {@code statusVersion}</b>. Without that bump
 *       {@code BillSyncService.protectBillState} cannot distinguish the owner's deliberate
 *       change from a stale device push and silently restores the previous value — the
 *       2026-09-29 "status change not persistent" defect.</li>
 *   <li>money the payment gateway owns is never rewritten: a bill whose
 *       {@code paymentStatus} is {@code paid}, or that carries a {@code gatewayTxnId}, is
 *       refused rather than edited.</li>
 *   <li>stock deduction is triggered on the way <i>into</i> a finalized state, using the
 *       idempotent {@link InventoryService#deductForFinalizedBill}, which is guarded by the
 *       committed {@code bills.inventory_deducted} flag so a bill can never deduct twice.</li>
 *   <li>every accepted edit is audited, reusing the action names the device path already
 *       writes ({@code BILL_STATUS_CHANGE}, {@code BILL_PAYMODE_CHANGE}, {@code BILL_VOID})
 *       so one audit query covers both surfaces.</li>
 * </ul>
 *
 * <p><b>Deliberately not supported:</b> moving a {@code completed} bill back to
 * {@code draft}. The platform has no stock-reversal path (there is no inverse of
 * {@code deductForFinalizedBill}), so reopening a completed bill would leave the raw-material
 * ledger claiming stock was consumed by an order that no longer counts as a sale. The owner
 * is directed to the refund/void flows instead. {@code cancelled} stays terminal for the same
 * class of reason and mirrors {@code protectBillState}, which restores un-cancels.
 */
@Service
public class BillEditService {

    private static final Logger log = LoggerFactory.getLogger(BillEditService.class);

    /** Statuses an owner may set. {@code paid} is intentionally absent — it is a legacy alias of {@code completed}. */
    static final Set<String> EDITABLE_ORDER_STATUSES = Set.of("draft", "completed", "cancelled");

    /**
     * Payment modes the system already recognises. Mirrors
     * {@code SyncPayloadValidator.VALID_PAYMENT_MODES} — both spellings of the split modes are
     * accepted so a mode a device legitimately wrote can always be corrected from the web.
     */
    static final Set<String> PAYMENT_MODES = Set.of(
            "cash", "upi", "pos",
            "part_payment_upi_cash", "part_payment_cash_pos", "part_payment_upi_pos",
            "part_cash_upi", "part_cash_pos", "part_upi_pos");

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final InventoryService inventoryService;
    private final SecurityAuditService securityAuditService;

    public BillEditService(BillRepository billRepository,
                           BillPaymentRepository billPaymentRepository,
                           InventoryService inventoryService,
                           SecurityAuditService securityAuditService) {
        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.inventoryService = inventoryService;
        this.securityAuditService = securityAuditService;
    }

    // ─── Order status ─────────────────────────────────────────────────────────────

    @Transactional
    public void updateOrderStatus(Long restaurantId, Long billId, String orderStatus, String reason) {
        String target = lower(orderStatus);
        if (target == null || !EDITABLE_ORDER_STATUSES.contains(target)) {
            throw new IllegalArgumentException(
                    "orderStatus must be one of: " + EDITABLE_ORDER_STATUSES);
        }

        Bill bill = loadBill(restaurantId, billId);
        String current = lower(bill.getOrderStatus());

        refuseIfGatewayOwned(bill);
        if (target.equals(current)) {
            throw new IllegalStateException("This bill is already " + target + ".");
        }

        boolean deductStock = false;
        switch (target) {
            case "completed" -> {
                // Only a pending bill can be settled. Cancelled and completed are both
                // terminal incoming states here (see the class comment).
                if (!"draft".equals(current)) {
                    throw new IllegalStateException("Only a pending bill can be marked completed"
                            + describeCurrent(current) + ".");
                }
                bill.setOrderStatus("completed");
                bill.setPaymentStatus("success");
                if (bill.getPaidAt() == null) {
                    bill.setPaidAt(System.currentTimeMillis());
                }
                deductStock = true;
            }
            case "cancelled" -> {
                if ("cancelled".equals(current)) {
                    throw new IllegalStateException("This bill is already cancelled.");
                }
                // Same field writes as BusinessReadService.voidBill so both entry points
                // leave an identical row.
                bill.setOrderStatus("cancelled");
                bill.setPaymentStatus("cancelled");
                bill.setCancelReason(reason);
            }
            case "draft" -> throw new IllegalStateException(
                    "Reopening a completed bill is not supported: the stock it consumed cannot be "
                            + "returned, so the inventory ledger would be wrong. Use refund or void instead.");
            default -> throw new IllegalArgumentException("Unsupported orderStatus: " + orderStatus);
        }

        bumpStatusVersion(bill);
        billRepository.save(bill);

        if (deductStock) {
            // Idempotent (bills.inventory_deducted): safe even if the device already
            // finalized this bill a moment ago.
            inventoryService.deductForFinalizedBill(bill);
        }

        if ("cancelled".equals(target)) {
            audit("BILL_VOID", "SUCCESS", bill, reason);
        } else {
            audit("BILL_STATUS_CHANGE", current + "->" + target, bill, reason);
        }
    }

    // ─── Payment mode ─────────────────────────────────────────────────────────────

    @Transactional
    public void updatePaymentMode(Long restaurantId, Long billId, String paymentMode,
                                  BigDecimal partAmount1, BigDecimal partAmount2, String reason) {
        String mode = lower(paymentMode);
        if (mode == null || !PAYMENT_MODES.contains(mode)) {
            throw new IllegalArgumentException("paymentMode must be one of: " + PAYMENT_MODES);
        }

        Bill bill = loadBill(restaurantId, billId);
        if ("cancelled".equals(lower(bill.getOrderStatus()))) {
            throw new IllegalStateException("A cancelled bill's payment mode cannot be changed.");
        }
        refuseIfGatewayOwned(bill);

        String currentMode = lower(bill.getPaymentMode());
        if (mode.equals(currentMode)) {
            throw new IllegalStateException("This bill already uses " + mode + ".");
        }

        List<BillPayment> payments = activePayments(restaurantId, billId);
        if (payments.size() > 1) {
            // Rewriting the mode of a split bill from one number would misstate how the
            // money arrived. Refuse rather than create bills/bill_payments divergence —
            // the server keys payment-level matching on payment_mode.
            throw new IllegalStateException("This bill has " + payments.size()
                    + " payment records, so its payment mode cannot be changed from here.");
        }

        bill.setPaymentMode(mode);
        if (isPartPayment(mode)) {
            bill.setPartAmount1(partAmount1);
            bill.setPartAmount2(partAmount2);
        } else if (isPartPayment(currentMode)) {
            // Leaving a split mode must not leave stale split amounts behind.
            bill.setPartAmount1(null);
            bill.setPartAmount2(null);
        }

        if (payments.size() == 1) {
            BillPayment payment = payments.get(0);
            payment.setPaymentMode(mode);
            long now = System.currentTimeMillis();
            payment.setUpdatedAt(now);
            payment.setServerUpdatedAt(now);
            billPaymentRepository.save(payment);
        }

        bumpStatusVersion(bill);
        billRepository.save(bill);
        audit("BILL_PAYMODE_CHANGE", currentMode + "->" + mode, bill, reason);
    }

    // ─── Internals ────────────────────────────────────────────────────────────────

    /**
     * Loads the bill under a row lock and re-checks tenancy, so a concurrent device push
     * cannot interleave between the checks below and the save.
     */
    private Bill loadBill(Long restaurantId, Long billId) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .filter(existing -> existing.getRestaurantId() != null
                        && existing.getRestaurantId().equals(restaurantId))
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Order not found");
        }
        return bill;
    }

    /**
     * A bill the payment gateway has settled is not ours to rewrite: the gateway owns the
     * money, and {@code protectBillState} would restore its version of the row anyway.
     */
    private void refuseIfGatewayOwned(Bill bill) {
        if ("paid".equalsIgnoreCase(bill.getPaymentStatus()) || bill.getGatewayTxnId() != null) {
            throw new IllegalStateException("This bill was settled through the payment gateway, "
                    + "so KhanaBook will not rewrite it here.");
        }
    }

    private List<BillPayment> activePayments(Long restaurantId, Long billId) {
        return billPaymentRepository.findByRestaurantIdAndServerBillIdIn(restaurantId, List.of(billId))
                .stream()
                .filter(payment -> !Boolean.TRUE.equals(payment.getIsDeleted()))
                .toList();
    }

    /**
     * The deliberate-edit signal the sync protocol reads. See the class comment: without
     * this bump the owner's change is treated as a stale push and reverted.
     */
    private void bumpStatusVersion(Bill bill) {
        bill.setStatusVersion((bill.getStatusVersion() == null ? 0 : bill.getStatusVersion()) + 1);
        long now = System.currentTimeMillis();
        bill.setUpdatedAt(now);
        bill.setServerUpdatedAt(now);
    }

    private void audit(String action, String outcome, Bill bill, String detail) {
        try {
            String target = bill.getPublicToken() != null
                    ? bill.getPublicToken().toString()
                    : "bill:" + bill.getId();
            securityAuditService.record(action, outcome, target, detail);
        } catch (Exception e) {
            // An audit failure must never undo a committed business edit; the mutation is
            // already validated and the log line below still records the attempt.
            log.error("Failed to audit {} for bill {} (non-fatal)", action, bill.getId(), e);
        }
    }

    private static boolean isPartPayment(String mode) {
        return mode != null && mode.startsWith("part");
    }

    private static String describeCurrent(String current) {
        return current == null ? "" : " (this one is " + current + ")";
    }

    private static String lower(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
