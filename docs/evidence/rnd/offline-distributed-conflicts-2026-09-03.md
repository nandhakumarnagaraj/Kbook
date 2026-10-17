# Offline Distributed Conflict Surface — KhanaBook (5-terminal)

**Date:** 2026-09-03 · **Author:** Kiro (acting PO) · Every claim grounded in code.

Question: across 5 offline terminals, what happens when two devices change the same
(or related) state and later sync? This maps the WHOLE surface, not just menu.

## Ownership models (the key to every case)

Our sync (`GenericSyncService.handlePushSync`) resolves conflicts by **whole-record
Last-Write-Wins on `updatedAt`** (`:454` `incoming.updatedAt >= existing.updatedAt`), with
three carve-outs:

| Model | Fields | Conflict behavior |
|---|---|---|
| **Server-owned** | `Bill.refundAmount`, finalized `paymentStatus`/`orderStatus`, `MenuItem/Variant.currentStock`, `MenuItem/Variant.isAvailable` (one-way: stays false), `RestaurantProfile.isSuspended`, `User.isActive/role/tokenInvalidatedAt` | Restored from server on every push (`preserveServerOwnedState:799`). Device cannot override. |
| **Field-merged** | `RestaurantProfile` counters (`mergeCounterState:771`, max of both), `User` identity (`mergeUserFields`) | Merged field-by-field, not clobbered. |
| **Whole-record LWW** | everything else on MenuItem (name, basePrice, description, category), Category, all other profile fields | Higher `updatedAt` wins; **the other device's concurrent field edits are silently lost.** |
| **Terminal-owned** | Bills, BillItems, BillPayments | Each terminal owns its own; cross-terminal child writes rejected. Not shared-edit conflicts. |

## The real conflict cases (by data type)

### 1. Menu item — price vs availability vs name (SHARED, LWW)
- **A sets unavailable, B (offline) edits price.** If server row is already unavailable when B lands, the `isAvailable` guard keeps it unavailable AND applies B's price. ✅ *only* because the guard is one-way.
- **A changes price ₹120→₹150; B edits description carrying stale ₹120.** B wins by timestamp → **price silently reverts to ₹120.** ❌ classic LWW field-clobber, no conflict surfaced.
- **A sets unavailable, but B's push lands FIRST (before A's).** Availability guard sees server still `available` → no protection → A's unavailable is lost when B wins. ❌ order-dependent.
- **currentStock** from any device → ignored (server recalculates from StockLog). ✅

### 2. Category (SHARED, LWW) — same class of bug as menu
- **A renames category "Starters"→"Snacks"; B (offline) reorders/toggles active on same category.** Whole-record LWW → one edit lost. ❌ No category field-merge exists (`validateCategory` only checks name; no merge).
- **A deletes category (isDeleted=true); B adds an item to it offline.** B's item may resolve to a now-deleted category (FK), or the delete loses to B's touch. Ordering-dependent orphan risk. ⚠️

### 3. Restaurant profile (SHARED, mixed)
- **Counters** (`dailyOrderCounter`, `lifetimeOrderCounter`): field-merged as `max(existing, incoming)` ✅ — good, prevents counter regression across devices.
- **isSuspended**: server-owned, device can't un-suspend ✅.
- **Everything else** (shopName, address, GST%, UPI handle, payment toggles, printer config, invoiceFooter, logo): **whole-record LWW.** So: **A enables Easebuzz + sets UPI handle; B (offline) edits shop address.** B wins → **A's payment-config changes silently revert.** ❌ High-impact: payment config is money-path and edited from the "primary/owner" device, but nothing enforces that — any terminal's profile push clobbers.
- **GST% change**: A sets 5%→18%; B offline bills at 5%. B's bills carry 5% (correct, snapshotted per bill), but a B profile push could revert the 18%. ❌

### 4. Bills / payments (TERMINAL-OWNED) — mostly safe, two edges
- Each terminal owns its bills; cross-terminal child writes rejected (`terminalOwnershipService`). ✅
- **Finalized-state protection**: a stale device push cannot revert paid/completed/cancelled ✅ (`:490`, `BillSyncService`).
- **refundAmount**: server-owned, never zeroed by push ✅.
- **Invoice/daily-order-id allocation**: local-state-dependent per terminal series. Two offline terminals allocating under the same series → unique-index collision on sync → now isolated (per-record fallback), colliding bill quarantined, not an infinite loop ✅ (the fixed 409 path). But the **quarantined bill needs manual/retry resolution** — an offline-allocation collision still means one bill must be re-numbered. ⚠️

### 5. Inventory raw materials (SERVER-derived + a gap)
- **Sale deduction** (server, recipe-based) is idempotent ✅.
- **Physical-count edit vs concurrent sale**: `RawMaterial.stockQuantity` has **no optimistic lock / CAS** — last-write-wins, one update lost (team's own `PhysicalCountRaceTest` documents it). ❌ ledger (`StockMovement`) is intact so it's reconstructable, but live qty drifts. (Already flagged in main report E1.)

### 6. Staff / permissions (SHARED, server-authoritative-ish)
- Permissions pulled server→device; monotonic revision + revocation marker ✅. A device cannot grant itself permissions.
- **User identity** field-merged ✅. **role/isActive** server-owned ✅.
- Edge: **owner changes staff permission on device A while staff device B is offline** → B keeps old permissions until next pull (up to 15 min). By design (offline grace), but a *revoked* permission stays usable on B until it syncs. ⚠️ security-relevant.

## Severity ranking (for our product)

| # | Case | Severity | Why |
|---|---|---|---|
| 1 | Profile payment-config clobber (LWW) | **P0** | money-path config silently reverts |
| 2 | Menu price clobber by unrelated field edit | **P1** | wrong price charged until noticed |
| 3 | Revoked permission usable on offline device | **P1** | security window |
| 4 | RawMaterial physical-count race | **P1** | stock drift (already flagged) |
| 5 | Category rename/reorder/delete conflicts | **P2** | cosmetic/orphan risk |
| 6 | Offline invoice-series collision → quarantine | **P2** | handled, needs manual re-number |

## Root cause (one sentence)
**Shared master data (menu, category, profile) uses coarse whole-record LWW; only a
hand-picked set of fields are server-owned or field-merged — so any two concurrent
offline edits to different fields of the same shared record silently lose one edit.**

## Fix directions (PO decision — none applied)
1. **Field-level merge for shared records** (extend the `mergeUserFields`/`mergeCounterState` pattern to MenuItem/Category/Profile via per-field timestamps). Best correctness; schema change (per-field `updatedAt` or a change-log).
2. **Make high-value shared fields server-authoritative** (menu price/availability, payment config) — edited via explicit endpoints, not device LWW. Matches the "primary device / owner controls config" design; smallest blast radius for the P0/P1s.
3. **Conflict detection + quarantine** for shared records (both changed since last common `serverUpdatedAt` → flag for owner) instead of silent clobber. Safety net regardless of 1/2.
4. **Shorten permission-revocation propagation** (push-triggered pull on revoke) for the security window.

My PO lean: **#2 for payment-config + menu price/availability (P0/P1) now**, **#3 as the
general safety net**, **#1 only if field-merge is truly needed elsewhere.**
