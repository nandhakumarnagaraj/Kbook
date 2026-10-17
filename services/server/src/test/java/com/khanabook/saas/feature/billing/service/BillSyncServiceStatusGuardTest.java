package com.khanabook.saas.feature.billing.service;

import com.khanabook.saas.feature.billing.data.Bill;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@link BillSyncService#protectBillState} after the 2026-09-29 fix for
 * "status change comes back old form".
 *
 * <p>Root cause of the defect: the guard required a STRICTLY greater statusVersion for
 * any status transition. A same-day edit pushed from the terminal that took the order
 * raced the server row created/advanced in the same sync cycle and arrived with an EQUAL
 * version — rejected as STALE_STATUS_PUSH, quarantined on the device, and the next pull
 * re-imported the old status. Payment-mode edits were never guarded, which is why mode
 * persisted while status reverted.
 *
 * <p>Rules under test:
 * <ol>
 *   <li>Equal version + FORWARD transition (into completed/paid, or a cancellation of a
 *       non-finalized bill) is a deliberate edit and passes untouched.</li>
 *   <li>Demotion OUT of a finalized state still requires a strictly greater version.</li>
 *   <li>Un-cancel still requires a strictly greater version.</li>
 *   <li>A gateway-paid paymentStatus can never be reverted, at any version.</li>
 *   <li>Strictly greater versions behave exactly as before (all transitions allowed).</li>
 * </ol>
 */
class BillSyncServiceStatusGuardTest {

    private final BillSyncService service = new BillSyncService(null, null);

    private Bill bill(String orderStatus, int statusVersion) {
        Bill bill = new Bill();
        bill.setOrderStatus(orderStatus);
        bill.setPaymentStatus("pending");
        bill.setStatusVersion(statusVersion);
        return bill;
    }

    // ── 1. Equal-version forward transitions pass ────────────────────────────

    @Test
    void equalVersion_draftToCompleted_passes() {
        Bill incoming = bill("completed", 6);
        Bill existing = bill("draft", 6);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("completed");
        assertThat(incoming.getStatusVersion()).isEqualTo(6);
    }

    @Test
    void equalVersion_draftToCancelled_passes() {
        Bill incoming = bill("cancelled", 4);
        Bill existing = bill("draft", 4);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("cancelled");
    }

    @Test
    void equalVersion_pendingToPaidOrderStatus_passes() {
        Bill incoming = bill("paid", 3);
        Bill existing = bill("draft", 3);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("paid");
    }

    // ── 2. Demotions out of finalized states still protected at equal version ──

    @Test
    void equalVersion_completedToDraft_isRestored() {
        Bill incoming = bill("draft", 6);
        Bill existing = bill("completed", 6);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("completed");
        assertThat(incoming.getStatusVersion()).isEqualTo(6);
    }

    @Test
    void equalVersion_completedToCancelled_isRestored() {
        Bill incoming = bill("cancelled", 6);
        Bill existing = bill("completed", 6);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("completed");
    }

    // ── 3. Un-cancel still protected at equal version ────────────────────────

    @Test
    void equalVersion_cancelledToCompleted_isRestored() {
        Bill incoming = bill("completed", 5);
        Bill existing = bill("cancelled", 5);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("cancelled");
    }

    // ── 4. Gateway-paid paymentStatus never reverted ─────────────────────────

    @Test
    void equalVersionStalePush_cannotRevertGatewayPaidPaymentStatus() {
        Bill incoming = bill("draft", 6);
        incoming.setPaymentStatus("pending");
        Bill existing = bill("draft", 6);
        existing.setPaymentStatus("paid");
        existing.setPaidAt(1234L);
        existing.setGatewayTxnId("txn_9");
        existing.setGatewayStatus("success");
        service.protectBillState(incoming, existing);
        assertThat(incoming.getPaymentStatus()).isEqualTo("paid");
        assertThat(incoming.getPaidAt()).isEqualTo(1234L);
        assertThat(incoming.getGatewayTxnId()).isEqualTo("txn_9");
    }

    // ── 5. Strictly greater versions behave exactly as before ────────────────

    @Test
    void strictlyGreater_anyTransition_isDeliberateAndUntouched() {
        Bill incoming = bill("draft", 8);
        Bill existing = bill("completed", 6);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("draft");
        assertThat(incoming.getStatusVersion()).isEqualTo(8);
    }

    @Test
    void strictlyGreater_cancelledToDraft_deliberateUnCancelAllowed() {
        Bill incoming = bill("draft", 9);
        Bill existing = bill("cancelled", 5);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("draft");
    }

    @Test
    void lowerVersion_finalizedToDraft_isRestoredAndVersionSnapped() {
        Bill incoming = bill("draft", 4);
        Bill existing = bill("completed", 6);
        service.protectBillState(incoming, existing);
        assertThat(incoming.getOrderStatus()).isEqualTo("completed");
        assertThat(incoming.getStatusVersion()).isEqualTo(6);
    }
}
