package com.khanabook.saas.feature.sync.service;

import com.khanabook.saas.feature.sync.service.GenericSyncService;

import com.khanabook.saas.feature.billing.data.Bill;
import com.khanabook.saas.feature.menu.data.ItemVariant;
import com.khanabook.saas.feature.menu.data.MenuItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the server-owned availability fix: when the inventory cascade hides
 * a menu item (zero stock), a device menu push must NOT flip it back to
 * available via last-write-wins.
 */
class PreserveServerOwnedStateTest {

    private MenuItem menuItem(boolean available) {
        MenuItem mi = new MenuItem();
        mi.setIsAvailable(available);
        return mi;
    }

    private ItemVariant variant(boolean available) {
        ItemVariant v = new ItemVariant();
        v.setIsAvailable(available);
        return v;
    }

    @Test
    void hiddenItem_staysHidden_whenDevicePushesAvailableTrue() {
        MenuItem incoming = menuItem(true);   // device still thinks it's available
        MenuItem existing = menuItem(false);  // server hid it via zero-stock cascade

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void hiddenVariant_staysHidden_whenDevicePushesAvailableTrue() {
        ItemVariant incoming = variant(true);
        ItemVariant existing = variant(false);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void ownerCanStillHideItem_viaDeviceToggle() {
        // Device marking an item unavailable must keep working.
        MenuItem incoming = menuItem(false);
        MenuItem existing = menuItem(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isFalse();
    }

    @Test
    void availableItem_isNotForceChanged() {
        MenuItem incoming = menuItem(true);
        MenuItem existing = menuItem(true);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getIsAvailable()).isTrue();
    }

    // ── Bill status: preserveServerOwnedState must NOT touch orderStatus ─────
    // It runs BEFORE the version-aware BillSyncService.protectBillState in the save
    // path; an unconditional finalized-status restore here erased the device's edit
    // before protectBillState could judge it (the 2026-09-29 "status change not
    // persistent" defect). orderStatus authority = protectBillState only.

    private Bill bill(String orderStatus, int statusVersion) {
        Bill b = new Bill();
        b.setOrderStatus(orderStatus);
        b.setPaymentStatus("pending");
        b.setStatusVersion(statusVersion);
        return b;
    }

    @Test
    void billOrderStatus_isLeftUntouched_evenWhenStale() {
        // Stale-looking push (older version): preserveServerOwnedState must NOT restore
        // the status — protectBillState owns that decision and the STALE_STATUS_PUSH
        // rejection.
        Bill incoming = bill("draft", 3);
        Bill existing = bill("completed", 9);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getOrderStatus()).isEqualTo("draft");
    }

    @Test
    void billGatewayPaidPaymentStatus_isStillRestoredUnconditionally() {
        Bill incoming = bill("draft", 3);
        incoming.setPaymentStatus("pending");
        Bill existing = bill("draft", 3);
        existing.setPaymentStatus("paid");
        existing.setPaidAt(42L);

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getPaymentStatus()).isEqualTo("paid");
        assertThat(incoming.getPaidAt()).isEqualTo(42L);
    }

    @Test
    void billSettlementFields_areStillRestoredUnconditionally() {
        Bill incoming = bill("completed", 7);
        Bill existing = bill("draft", 6);
        existing.setSettledAmount(new java.math.BigDecimal("900.00"));
        existing.setSettledAt(77L);
        existing.setGatewayTxnId("txn_1");

        GenericSyncService.preserveServerOwnedState(incoming, existing);

        assertThat(incoming.getSettledAmount()).isEqualByComparingTo(new java.math.BigDecimal("900.00"));
        assertThat(incoming.getSettledAt()).isEqualTo(77L);
        assertThat(incoming.getGatewayTxnId()).isEqualTo("txn_1");
    }
}
