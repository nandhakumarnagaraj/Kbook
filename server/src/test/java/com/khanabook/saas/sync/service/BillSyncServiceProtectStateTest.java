package com.khanabook.saas.sync.service;

import com.khanabook.saas.feature.billing.service.BillSyncService;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.khanabook.saas.feature.billing.data.Bill;

/**
 * Unit tests for {@link BillSyncService#protectBillState}: the guard that keeps a stale
 * last-write-wins push from reverting finalized bill state, while still letting a
 * deliberate edit through.
 *
 * <p>The distinction is {@code statusVersion} — the device bumps it only on an explicit
 * user action, so a strictly greater incoming version means "the cashier did this", and
 * an equal-or-lower one means "this is an old copy replaying".
 */
class BillSyncServiceProtectStateTest {

	/** No repositories are touched by protectBillState, so nulls are fine here. */
	private final BillSyncService service = new BillSyncService(null, null);

	// ── Stale pushes must not undo terminal state ────────────────────────────

	@Test
	@DisplayName("stale push cannot revert completed -> draft")
	void stalePush_cannotRevertCompletedToDraft() {
		Bill existing = bill("completed", "pending", 3);
		Bill incoming = bill("draft", "pending", 3); // same version = not a deliberate edit

		service.protectBillState(incoming, existing);

		assertEquals("completed", incoming.getOrderStatus());
	}

	@Test
	@DisplayName("stale push cannot revert paid -> pending, and gateway fields survive")
	void stalePush_cannotRevertPaid() {
		Bill existing = bill("completed", "paid", 2);
		existing.setPaidAt(1_700_000_000_000L);
		existing.setGatewayTxnId("EZ-TXN-1");
		existing.setGatewayStatus("SUCCESS");

		Bill incoming = bill("completed", "pending", 1); // older version
		incoming.setPaidAt(null);
		incoming.setGatewayTxnId(null);

		service.protectBillState(incoming, existing);

		assertEquals("paid", incoming.getPaymentStatus());
		assertEquals(1_700_000_000_000L, incoming.getPaidAt());
		assertEquals("EZ-TXN-1", incoming.getGatewayTxnId());
		assertEquals("SUCCESS", incoming.getGatewayStatus());
	}

	@Test
	@DisplayName("a cancelled bill cannot be un-cancelled by a stale push")
	void stalePush_cannotUncancel() {
		Bill existing = bill("cancelled", "cancelled", 5);
		Bill incoming = bill("completed", "paid", 5);

		service.protectBillState(incoming, existing);

		assertEquals("cancelled", incoming.getOrderStatus());
	}

	@Test
	@DisplayName("stale push cannot roll statusVersion backwards")
	void stalePush_cannotRollVersionBackwards() {
		Bill existing = bill("completed", "paid", 7);
		Bill incoming = bill("completed", "paid", 2);

		service.protectBillState(incoming, existing);

		assertEquals(7, incoming.getStatusVersion());
	}

	// ── Deliberate edits are allowed through ─────────────────────────────────

	@Test
	@DisplayName("deliberate edit cancels a completed bill")
	void deliberateEdit_cancelsCompletedBill() {
		Bill existing = bill("completed", "paid", 1);
		Bill incoming = bill("cancelled", "cancelled", 2); // bumped by the device

		service.protectBillState(incoming, existing);

		assertEquals("cancelled", incoming.getOrderStatus());
		assertEquals("cancelled", incoming.getPaymentStatus());
		assertEquals(2, incoming.getStatusVersion());
	}

	@Test
	@DisplayName("deliberate edit changes payment mode on a settled bill")
	void deliberateEdit_changesPaymentModeOnSettledBill() {
		Bill existing = bill("completed", "paid", 4);
		existing.setPaymentMode("upi");
		Bill incoming = bill("completed", "paid", 5);
		incoming.setPaymentMode("cash");

		service.protectBillState(incoming, existing);

		assertEquals("cash", incoming.getPaymentMode());
		assertEquals("completed", incoming.getOrderStatus());
	}

	// ── Null-safety: rows predating the status_version column ────────────────

	@Test
	@DisplayName("null versions are treated as 0 and still protected")
	void nullVersions_areTreatedAsZeroAndProtected() {
		Bill existing = bill("completed", "paid", null);
		Bill incoming = bill("draft", "pending", null);

		service.protectBillState(incoming, existing);

		assertEquals("completed", incoming.getOrderStatus());
		assertEquals("paid", incoming.getPaymentStatus());
		assertEquals(0, incoming.getStatusVersion());
	}

	@Test
	@DisplayName("a bumped version beats a null stored version")
	void bumpedVersion_beatsNullStoredVersion() {
		Bill existing = bill("completed", "paid", null);
		Bill incoming = bill("cancelled", "cancelled", 1);

		service.protectBillState(incoming, existing);

		assertEquals("cancelled", incoming.getOrderStatus());
	}

	private Bill bill(String orderStatus, String paymentStatus, Integer statusVersion) {
		Bill bill = new Bill();
		bill.setLocalId(System.nanoTime());
		bill.setRestaurantId(1L);
		bill.setPublicToken(UUID.randomUUID());
		bill.setOrderStatus(orderStatus);
		bill.setPaymentStatus(paymentStatus);
		bill.setStatusVersion(statusVersion);
		return bill;
	}
}
