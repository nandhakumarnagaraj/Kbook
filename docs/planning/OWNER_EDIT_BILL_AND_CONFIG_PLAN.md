# Owner Edit: Bill Mode, Bill Status and Configurations — Plan

Status: **plan only, no code yet** (owner decision: read both codebases first, write the
plan, review, then implement).
Scope decided by owner: **web-admin + Android POS**, closed days **editable always with
reason + audit**, configurations **owner-editable**.

---

## 1. The capability mostly exists already — here is exactly how far it goes

The plumbing for "deliberately change a bill's status / payment mode" is already built
end-to-end on the device path. The gap is *reach*, not foundations.

### Server (already built)

| Behaviour | Where |
|---|---|
| A deliberate edit passes the terminal-state guard only when it carries a **strictly newer `statusVersion`** (equal-version *forward* transitions allowed; never rolls the version backwards) | `BillSyncService.protectBillState` — `server/.../billing/service/BillSyncService.java:318-354` |
| A **stale** push is restored *and* loudly rejected: `STALE_STATUS_PUSH` + audit row naming device vs server version | `GenericSyncService.java:814-851` |
| Every device-originated status/mode change is **already audited**: `BILL_STATUS_CHANGE old->new` and `BILL_PAYMODE_CHANGE old->new`, keyed by bill `publicToken` + terminal id | `GenericSyncService.auditBillEdits` — `GenericSyncService.java:178-203` |
| `paymentStatus="paid"` is terminal and gateway-owned; gateway txn/status, `refundId`, `settledAmount/At`, `commissionAmount` and `inventoryDeducted` are restored unconditionally so a push can never un-settle money or double-deduct stock | `GenericSyncService.java:1255-1294` |
| Accepted vocabularies | `SyncPayloadValidator.java:73-82` — orderStatus `draft\|completed\|paid\|cancelled`; paymentStatus `pending\|success\|paid\|failed\|refunded`; paymentMode `upi\|cash\|pos\|part_payment_*` |
| Owner-only web write paths **that already exist for bills**: manual refund and void | `BusinessAdminController.java:83-99`; `BusinessReadService.markManualRefund:269`, `voidBill:292` |
| `voidBill` is the **reference implementation** of a server-side deliberate edit: sets status, sets `statusVersion + 1`, stamps `cancelReason`, audits `BILL_VOID` | `BusinessReadService.java:302-321` |
| `/business/**` is already **OWNER-only** | `SecurityConfig.java:156` |
| Permission keys `billing.edit` / `billing.void` / `billing.refund` / `billing.settle` exist | `PermissionKey.java:5-10` |

### Android (already built)

| Behaviour | Where |
|---|---|
| Status edit: normalises to completed/cancelled/draft, derives paymentStatus + paidAt, **bumps `statusVersion`**, deducts or restores inventory, synthesises/repairs the payment row | `BillRepository.updateOrderStatus` — `BillRepository.kt:316-398` |
| Payment-mode edit: normalises mode, **bumps `statusVersion`**, keeps the matching `bill_payments` row in step | `BillRepository.updatePaymentMode` — `BillRepository.kt:456-489` |
| Cancel: item snapshot *before* the flip, KOT cancel notice only if the kitchen already printed, prints `*** ORDER CANCELLED ***`, flushes the queue | `BillRepository.cancelOrder` — `BillRepository.kt:400-443` |
| Terminal-ownership guard on all three mutators: **only this terminal's operational bills** (`getOperationalBillById` + `isLocallyOwned`) | `BillRepository.kt:77`, `:320`, `:459`, `:494` |
| Row-level status dropdown + pay-mode dropdown in Reports | `ReportViews.kt:395-470`, wired at `ReportsScreen.kt:236-250` |

**So the two "known issue" notes are out of date.** `docs/meta/KNOWN_ISSUES.md:31` and
`docs/android/UI_IMPROVEMENT_FINDINGS.md:201` both claim `updatePaymentMode`,
`cancelOrder` and `updatePaymentStatus` load via the raw `getBillById` and bypass the
guard. As of this read, all three load through `getOperationalBillById`
(`BillRepository.kt:320`, `:459`, `:494`); the guard is present. Those two notes should
be corrected, not acted on.

---

## 2. What actually blocks the owner today

1. **Web-admin cannot change status or payment mode at all.** Only refund and void exist
   (`BusinessApiService.manualRefundOrder:79`, `voidBill:394`). An owner looking at a wrong
   mode in the Orders table has no action but void.
2. **Android gates the edit too tightly for an owner.** The row's edit affordance is
   `canEdit = !isCancelled && isSameDayAsTaken`, with explicit refusals
   (`ReportViews.kt:398-417`):
   - `"Paid orders can't be edited."` (completed bills locked),
   - `"Only today's orders can be edited."` (any previous day locked),
   - cancelled is permanently locked, and
   - a pay-after-food dine-in draft may only be cancelled, never completed.
   These rules are identical for the owner and for `SHOP_STAFF` — there is no
   "owner sees more" branch anywhere in the edit path.
3. **Staff/owner distinction is not enforced on the device.** `PermissionManager` defines
   `BILLING_EDIT` / `BILLING_VOID` (`PermissionManager.kt:258`, launcher table `:195`) but
   **no production code checks them** — only `BILLING_CREATE` is checked
   (`BillingViewModel.kt:818`) and only in tests (`PermissionManagerTest.kt`). Meanwhile the
   server grants both keys to `SHOP_STAFF` by role, since `SHOP_STAFF_GRANTED_KEYS` is
   "every key except the configuration families" (`PermissionService.java:189-211`). Net
   effect: today a staff member can already change status and mode on the tablet, while the
   owner cannot do the same on the web.
4. **One real revert bug to fix while we are here:** `updatePaymentStatus`
   (`BillRepository.kt:492-505`) writes `paymentStatus` **without bumping `statusVersion`**.
   That is exactly the shape that produced the 2026-09-29 "status change not persistent"
   defect: the server cannot tell it from a stale push, so `protectBillState` restores the
   old value. The other three mutators all bump the version.
5. **Configurations:** the owner is already all-powerful by role — the server keeps
   `settings.shop_profile`, `settings.payment`, `settings.gst` and all four menu-edit keys
   owner-only (`PermissionService.java:181-197`), and `/business/**` is OWNER-only. Web-admin
   Settings already writes profile, logo, tax and payment methods. The genuine gaps are the
   settings that exist **only on the tablet** — order payment flow mode
   (`SettingsViewModel.updateOrderPaymentFlowMode:1362`, exposed in
   `ShopConfigSection.kt:208`), printer/receipt configuration and shop-operational toggles —
   none of which the owner can reach from the web.

---

## 3. Workstream A — server: one owner edit seam (web-admin + POS share it)

Add to `BusinessAdminController` (both `@RequireRole(UserRole.OWNER)`, mirroring `/void`):

```
POST /business/bills/{billId}/status        body { orderStatus, reason }
POST /business/bills/{billId}/payment-mode  body { paymentMode, partAmount1?, partAmount2?, reason }
```

Service side, in a **write** service (move `markManualRefund` / `voidBill` out of
`BusinessReadService` while we are in there — a read service doing financial writes is how
this area got hard to reason about):

- validate against the accepted vocabularies (`SyncPayloadValidator`);
- set the fields **and `statusVersion = statusVersion + 1`** — without this, the next device
  push is judged stale and the owner's edit is silently reverted;
- write a `reason` (reuse `cancelReason` or add an edit-note column — decision below);
- audit with the **existing** action names so one audit stream covers both surfaces:
  `BILL_STATUS_CHANGE`, `BILL_PAYMODE_CHANGE` (and reuse `BILL_VOID` for void);
- refuse to fight the gateway: never let a web edit set `paymentStatus=paid` on a
  gateway-settled bill, and keep `inventoryDeducted` consistent when status moves across
  the completed boundary (deduct once, restore on the way back).

## 4. Workstream B — Android: give the owner the wider window, and actually enforce staff limits

- Introduce one predicate, e.g. `canEditBill(row, session, now)`:
  **OWNER** → editable for any non-cancelled bill, any day, including completed (the
  un-cancel case stays blocked as it is today unless the owner explicitly wants
  "reopen", see decisions);
  **SHOP_STAFF** → today's window only, and completed stays locked (i.e. today's behaviour),
  now gated by a real `PermissionManager.hasPermission(BILLING_EDIT)` check instead of by
  nothing;
  and **cancel** gated by `BILLING_VOID` for staff, always allowed for the owner.
- Keep the explanatory toasts, but make the message role-aware (`"Only today's orders can
  be edited — ask the owner to correct an earlier day."` for staff).
- Fix the missing `statusVersion` bump in `updatePaymentStatus` (bug 4 above).
- Route owner edits of **synced** bills through the new server endpoint rather than a local
  write, so an edit made on the tablet survives the pull. Today the local mutators only
  accept **this terminal's** locally-created bills (`isLocallyOwned`), so an owner editing
  another terminal's bill must go server-first and let the pull flow back.
- A second device holding the same bill will keep showing the old value until its next pull;
  the version bump is what makes its own later push lose to the server instead of winning.

## 5. Workstream C — configurations

- Nothing to loosen in the permission model: the owner is already unrestricted
  (`UserRole.OWNER` short-circuits `PermissionService.hasPermission`), and SHOP_STAFF is
  already locked out of profile/payment/GST/menu-edit.
- Real work is **surface parity**: expose the tablet-only settings the owner cannot reach
  from the web — order payment flow mode (pay-first / pay-after-food), receipt & printer
  defaults, and any shop-operational toggles — as owner-only web-admin controls writing
  through `/business/**`. Decide the list together (see questions) before building.

## 6. Invariants the implementation must not break

1. `statusVersion` monotonic, bumped on **every** deliberate edit, on both surfaces.
2. `paymentStatus="paid"` stays gateway-owned; device vocabulary can never produce it.
3. Gateway txn/status, refundId, settledAmount/At, commissionAmount stay server-owned.
4. `inventoryDeducted` flips in lockstep with the completed boundary — never deduct twice.
5. Cancel keeps its kitchen side effects (item snapshot, KOT cancel notice, queue flush).
6. Terminal isolation: a terminal may not write another terminal's bill directly — only via
   a server endpoint.
7. Closed-day edits recompute derived figures: Daily Closing and revenue reports read
   `isRevenueBillStatus` (`BusinessReadService.java:346`), so an edit after closing changes
   that day's totals — the day must be re-closable / clearly marked as amended.

## 7. Test plan

- **Server**: unit + property tests for the two new endpoints — version bump, audit row
  emitted, paid-bill refusal, inventory not double-deducted, and a following stale device
  push losing to the server. Existing property tests in `server/src/test/.../webadmin/`
  show the house style.
- **Android**: extend `BillingLogicTest` (it already covers the `updateOrderStatus`
  completion guard) with: owner-vs-staff edit window, `BILLING_EDIT`/`BILLING_VOID`
  enforcement, and the `updatePaymentStatus` version bump.
- **Web-admin**: shell/component spec for the edit panel (mirrors the six new
  `orders-page.component.spec.ts` tests just added for the tab shell).
- **Manual E2E**: edit a completed bill on the web → confirm the tablet shows it after a
  sync cycle and does **not** revert; then run Daily Closing for that day and check the
  totals moved as expected.

## 8. Decisions still open

1. **Un-cancel**: should the owner be able to move a `cancelled` bill back to
   `completed`? Today `cancelled` is terminal in `protectBillState` and the tablet says
   "Cancelled orders can't be changed." Allowing it means letting the server accept a
   backwards status transition from an owner.
2. **Edit note storage**: reuse `cancelReason` for every edit, or add a dedicated column /
   audit-only note? Reusing `cancelReason` on a non-cancelled bill reads oddly in the UI.
3. **Which tablet-only configs** to expose in web-admin (section 5).
4. **Re-closing**: after an edit to a closed day, should the owner be forced to re-run
   Daily Closing, or should the figures update silently?

## 9. Suggested order

1. Fix the `updatePaymentStatus` version bump (small, real bug, independent).
2. Server edit endpoints + audit + tests (A).
3. Web-admin edit panel in the order-detail modal for both tabs (A).
4. Android owner window + permission enforcement + server-first path for synced bills (B).
5. Config parity (C) once the list is agreed.
6. Correct the two stale notes (`KNOWN_ISSUES.md`, `UI_IMPROVEMENT_FINDINGS.md`).
