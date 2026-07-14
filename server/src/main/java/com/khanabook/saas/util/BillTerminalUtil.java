package com.khanabook.saas.util;

import com.khanabook.saas.entity.Bill;
import java.math.BigDecimal;

/**
 * Pure helpers for terminal ownership decisions on bills and their child records
 * (BillItem / BillPayment). Centralised so Bill push, BillItem push and BillPayment
 * push enforce identical rules and cannot drift.
 */
public final class BillTerminalUtil {

	public static final String LEGACY_UNRESOLVED = "LEGACY_UNRESOLVED";

	private BillTerminalUtil() {
	}

	/** Owner terminal of a bill: the current owner, falling back to the creating terminal. */
	public static String ownerTerminalId(Bill bill) {
		if (bill == null) return null;
		if (bill.getCurrentOwnerTerminalId() != null && !bill.getCurrentOwnerTerminalId().isBlank()) {
			return bill.getCurrentOwnerTerminalId();
		}
		return bill.getCreatedTerminalId();
	}

	public static boolean isLegacyUnresolved(Bill bill) {
		return LEGACY_UNRESOLVED.equals(ownerTerminalId(bill));
	}

	/**
	 * A bill is immutable history once it is finalized. Child records (items/payments)
	 * must not be attached to or mutated against a finalized bill.
	 */
	public static boolean isFinalized(Bill bill) {
		if (bill == null) return false;
		String os = bill.getOrderStatus() == null ? "" : bill.getOrderStatus().toLowerCase();
		if (os.equals("completed") || os.equals("paid") || os.equals("cancelled")) {
			return true;
		}
		if (bill.getRefundAmount() != null && bill.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
			return true;
		}
		String sc = bill.getSourceChannel() == null ? "" : bill.getSourceChannel().toLowerCase();
		return sc.equals("zomato") || sc.equals("swiggy") || sc.equals("own_website");
	}

	/**
	 * Whether a given trusted terminal context may modify a bill (or attach children to it).
	 *
	 * @param parent             the parent bill (already tenant-filtered)
	 * @param trustedTerminalId  terminal id from the X-Terminal-Token, or null for legacy clients
	 * @param trustedDeviceId    device id bound to the trusted terminal, or null
	 * @param isAdmin            true when the caller is KBOOK_ADMIN
	 * @return true if the modification is permitted
	 */
	public static boolean isModifiableByTerminal(Bill parent, String trustedTerminalId,
			String trustedDeviceId, boolean isAdmin) {
		if (parent == null) return false;
		if (isFinalized(parent)) return false;
		if (isAdmin) return true;
		String owner = ownerTerminalId(parent);
		if (owner == null) {
			// Pre-terminal bill with no owner recorded: modifiable for backward compatibility.
			return true;
		}
		if (LEGACY_UNRESOLVED.equals(owner)) {
			// Legacy bill: only the original tablet's terminal (or admin) may keep touching it,
			// preventing a different terminal from hijacking it. Admin recovery path covers the rest.
			return trustedDeviceId != null && trustedDeviceId.equals(parent.getCreatedDeviceId());
		}
		if (trustedTerminalId == null) {
			// TEMPORARY compatibility fallback: a client that does not yet send an
			// X-Terminal-Token (legacy Android builds) is not per-terminal-enforced. This path
			// is only reachable when terminal.sync.strict=false; strict mode rejects such pushes
			// earlier in GenericSyncService. Remove once all clients ship the terminal-token build.
			return true;
		}
		return owner.equals(trustedTerminalId);
	}
}
