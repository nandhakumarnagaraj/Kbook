# Known Issues

## Category push rejected with HTTP 409 — reorder/rename/delete do not persist across restart on affected tenant

**Status:** Open (pre-existing platform/sync behavior, not a regression from the Manage Categories feature)

**Symptom (verified on-device, Moto, 1.0.30):**
- Manage Categories drag-reorder applies instantly in-session (chip order updates on Save).
- After an app/process restart, category order reverts to server order.
- `logcat` on the device shows: `Conflict while pushing categories ... {"error":"This record was modified by another device. ...","path":"/api/v1/sync/menu/categories/push"}` (HTTP 409) on every category push attempt.

**Root cause trace:**
- Any category edit — reorder (`CategoryRepository.reorderCategories`), rename (`updateCategory`), delete — marks the row `isSynced = false` and bumps `updatedAt`, then triggers a background sync push.
- The server rejects the push with a per-record version (optimistic concurrency) 409.
- The client's conflict recovery pulls server state with `lastSyncTimestamp = 0`, which rewrites local categories to server order and marks them synced — silently discarding the local change.
- The version skew pre-exists new code: `updateCategory` (used by the existing Edit/Delete flow) sets identical fields, so category writes from a device have the same failure mode once server-side rows are seeded/modified outside that device's last sync.

**Scope:**
- In-process behavior of the Manage Categories dialog (drag-reorder, Save, inline add, rename/delete delegation, Cancel) is fully verified and correct.
- Persistence of reorder/rename/delete across restart depends on resolving this sync 409 (server-side transaction/version handling), which is out of scope for the UI feature.

## Orders Report payment-mode edit — write-path latent defects (UI symptom fixed, data path still open)

**Status:** Open (UI fix in working tree, uncommitted; write-path defects remain)

**Background:** editing a bill's payment mode in Report Details → Orders Report where the selected mode equals the current mode was a silent no-op write (identical value, `status_version` bump, `is_synced = 0`, sync trigger). Full root-cause trace: `docs/android/UI_IMPROVEMENT_FINDINGS.md` Issue 5.

**Fixed in working tree (uncommitted):** the dropdown now excludes the current mode (`selectablePayModes = enabledModes.filter { it != row.paymentMode }`, `ReportViews.kt:396`, `OrderTableComponents.kt:92`); blocked edits (cancelled / paid / previous-day) show a toast (`onEditBlocked`, `ReportViews.kt:402-409`); dine-in pay-after-food drafts can't open the mode menu (`ReportViews.kt:410-418`).

**Still open:**
- `BillRepository.updatePaymentMode` loads via raw `getBillById` (`BillRepository.kt:455`), bypassing the `isLocallyOwned` / `getOperationalBillById` guard that `updateBill()` enforces (`BillRepository.kt:135-139`, `BillDao.kt:202-215`). Same gap in `cancelOrder` and `updatePaymentStatus`.
- `bill_payments` is only synced when exactly one active payment record exists (`BillRepository.kt:469-483`); bills with 0 or ≥2 records leave `bills.payment_mode` and `bill_payments` diverging, and the server keys payment-level matching on `payment_mode` (`BillSyncService.isExactPaymentMatch`).
- `partAmount1`/`partAmount2` are unconditionally reset to "0.0" (`BillRepository.kt:461-462`) even for non-part modes.
- Dead DAO method `BillDao.updatePaymentMode` (`BillDao.kt:397-403`) — never called; the repository rewrites the whole entity via `@Update`.