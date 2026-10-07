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

---

## One device cannot bill in a second restaurant — terminal activation stuck at HTTP 202 PENDING_APPROVAL

**Status:** Open (server-side design gap, no Android change required to fix)

**Symptom (reproduced on Play Store build, single device, no reinstall):**
1. Install app → log in to restaurant A → works, bills normally.
2. Sign out → log in to restaurant B **on the same device/install** → login succeeds, then
   billing is blocked with a terminal-activation error.
3. The same restaurant B account works fine on a **different** device.

**Root cause trace:**
- Login itself has **no device gate** — `AuthServiceImpl.login` looks up the user by login
  identifier and issues a JWT for that user's restaurant. Authentication is unaffected.
- The failure is at `POST /api/v1/sync/terminal/activate`, called by the client during first
  sync (`SyncManager.ensureTerminalActivated()`). The server returns **HTTP 202**, which the
  client turns into `TerminalPendingApprovalException` (SyncManager.kt:148-163).
- `TerminalController.activate()` decides trust using a **per-restaurant** lookup only:
  - Case 1 "known device" uses `terminalRepository.findByRestaurantIdAndDeviceId(restaurantId, deviceId)`
    (TerminalController.java:250). A device activated in restaurant A is **not** found for
    restaurant B, so it is treated as a brand-new device.
  - Case 4 "first device" auto-creates Terminal A, but **only when the restaurant has zero
    terminals ever** (TerminalController.java:333-334). Restaurant B already has a terminal
    (created when it was first set up), so this shortcut is skipped.
  - Execution therefore falls through to Case 2 "unknown device" → creates a PENDING
    `DeviceRegistrationRequest` → **202 PENDING_APPROVAL** (TerminalController.java:375-401).
- The device then waits forever for a per-restaurant approval that the product never surfaces
  as an automatic step.

**Why a different device works:** on a fresh device restaurant B is still empty, so the same
call hits Case 4 and auto-activates as OWNER (201 CREATED). Log proof: `TERMINAL_FIRST_CREATED`
for the working device vs repeated `TERMINAL_PENDING_REQUEST` / `TERMINAL_RECOVERY_REQUEST` for
the reused device.

**Confirmed in production logs:**
- `POST /api/v1/sync/terminal/activate` returns `202` on every attempt for the reused device.
- `TERMINAL_FIRST_CREATED ... owner=dev_02dcb1e17ffb` (fresh device — succeeded).
- `TERMINAL_PENDING_REQUEST` / `TERMINAL_RECOVERY_REQUEST` for the same `dev_*` id in
  restaurants where it was not the first device.
- Note the DB does allow the same `deviceId` across restaurants — the unique index
  `ux_restaurant_terminal_device` is on `(restaurant_id, device_id)` (V26 migration), so this is
  **not** a schema constraint issue. The gap is purely the trust decision in the controller.

**Proposed solution (server-side only, no Android change):**
Add a cross-restaurant trusted-device branch to `TerminalController.activate()`, before the
Case 2 fallthrough:
- If the caller is `OWNER` **and** the requesting `deviceId` already has an `ACTIVE` terminal in
  **any** restaurant, auto-activate a terminal in the current restaurant (allocate the next
  series, honor the 5-active-terminal limit, issue the terminal token) and return 201, mirroring
  the Case 4 first-device path.
- Audit the outcome as `TERMINAL_AUTO_ACTIVATED` through the existing `SecurityAuditService`.
- Keep per-restaurant isolation intact: the issued token stays scoped to the current restaurant,
  and bill identity remains `(restaurant_id, device_id, local_id)`, so no data crosses tenants.

**Optional hardening:** a server-owned `device_registry` table (`deviceId` → restaurant terminals,
status, credential version) to make trusted-device membership auditable and revocable, rather
than relying on a cross-restaurant scan of `restaurant_terminal` at request time.

**Out of scope / related client-side limitations (informational, not the cause of this issue):**
- Only one session is active at a time; `auth_token` is a single slot, so switching restaurants
  requires sign-out then sign-in. This is a UX constraint, not a bug.
- Sign-out is blocked while settled bills are unsynced (`LogoutViewModel`), so a device with
  pending offline work cannot switch restaurants until it syncs.
- Signing out from the role-access screen clears all per-restaurant local state; the Settings
  sign-out path preserves it.
