# Root Cause: Cancelled/Status-Changed Bill Reverts After Screen Navigation

> **Status:** Verified against source on 2026-09-18. Android + server traced end-to-end.
> **Symptom:** Changing a bill's status (completed → cancelled) updates the UI instantly, but navigating to another screen and back shows the **old status** — the change silently reverts.
> **Scope:** Root cause + analysis + solution + implementation plan. No code changes made.

---

## The symptom, decoded

"Instantly updated in UI" + "reverts when I come back" means **two different data sources**:

1. The instant update is an **in-memory optimistic patch** to ViewModel state lists.
2. Coming back re-runs `loadReports()` which **re-reads Room** — and Room still (or again) holds the old status.

So the question splits into: (a) did the local Room write happen correctly? (b) did something overwrite it afterwards? The verified answer: (a) yes — (b) yes, by the sync pull, in a two-stage race. Full chain below.

---

## Android side — what actually happens on cancel

### The write path is correct ✅

`ReportsViewModel.cancelOrder()` (ReportsViewModel.kt:185) → `BillRepository.cancelOrder()` (BillRepository.kt:379) → `BillDao.cancelBill` (BillDao.kt:406):

```sql
UPDATE bills SET order_status = 'cancelled', cancel_reason = :reason,
status_version = status_version + 1, is_synced = 0, updated_at = :updatedAt
```

This is a *good* write: status set, `status_version` bumped (deliberate-edit signal), `is_synced = 0` (will be pushed), `updated_at` refreshed. `updateOrderStatus` (BillDao.kt:386) is equally correct. The ViewModel also patches `_orderDetailsTable` / `_orderLevelRows` in memory — that's the instant UI update. `updateOrderStatus` even calls `loadReports()` afterwards, so its own screen re-reads Room and stays correct.

**Cancel has a UI-state bug worth noting (RC-UI, minor):** `cancelOrder` patches the lists but does **not** call `loadReports()` like `updateOrderStatus` does. The in-memory patch also leaves `paymentStatus` untouched on the row, while the repository-level flow (updateOrderStatus via UI, or cancelBill from other paths) sets `payment_status = 'failed'`. Cosmetic inconsistency; not the revert.

### The revert mechanism — two-stage sync race 🔴

The bill is now `cancelled`, `status_version = N+1`, `is_synced = 0`, waiting for push.

**Stage 1 — the pull can land BEFORE the push.** `BillRepository.cancelOrder` ends with `triggerBackgroundSync()` → `enqueueMasterSyncOnce()` (WorkManager). The SyncManager cycle is **push-then-pull inside one checkpoint-guarded transaction** (SyncManager.runSyncCycle), which would be safe — but a *separate* trigger (another bill saved, notifications refresh, periodic WorkManager, another terminal's change) can run a **pull** whose response arrives while our bill is still unsynced-local. The pull response carries our own bill echoed from the server (or the merge below happens on the next full cycle after push-before-persist).

**Stage 2 — the push result marks the row synced with the OLD status.** Two concrete overwrite paths exist in the push/pull plumbing:

**Path A — `acknowledgeUnchangedBills` race (MasterSyncProcessor.kt:642).**
After `pushBatches(bills)` succeeds, the processor calls `markBillAsSyncedIfUnchanged(billId, pushedUpdatedAt, pushedOrderStatus, pushedPaymentStatus)`:

```sql
UPDATE bills SET is_synced = 1 ... WHERE id = :billId AND is_synced = 0
  AND updated_at = :pushedUpdatedAt
  AND order_status = :pushedOrderStatus
  AND payment_status = :pushedPaymentStatus
```

This is a compare-and-mark snapshot guard: it only marks synced if the row is *exactly what was pushed*. **But `BillRepository.cancelOrder` (and `updateOrderStatus`) call `billDao.updateBill(current.copy(...))` AFTER the targeted `cancelBill`/`updateOrderStatus` query in the same function** (BillRepository.kt:362–372): the copy writes `orderStatus`, `paymentStatus`, `paidAt`, `statusVersion`, `isSynced=false`, `updatedAt=now` — all over the same row. The pushed snapshot captured by `pushedBillsById` was read *before* (`getUnsyncedBills`). If the user's second edit (or the repository's own second write) lands between snapshot and acknowledge, the acknowledge silently marks a *mixed* row synced. Worse, in `cancelOrder` the repo-level `updateOrderStatus`-style second write does **not** exist — but `updateOrderStatus()` does exactly this double-write (BL1 in the earlier audit). Any concurrent payment-record insert (`addBillPayment` → `insertBillPayment` + `triggerBackgroundSync`) also mutates `updated_at` between snapshot and ack.

**Path B — `insertSyncedBills` merge: pulled copy wins on the `isSynced` tie-break (BillDao.kt:1103).**
When the pull delivers the bill (server echo or another terminal's view of it):

```kotlin
val shouldOverwrite =
    bill.updatedAt > localBill.updatedAt ||
        (bill.updatedAt == localBill.updatedAt && localBill.isSynced)
if (shouldOverwrite) { updateBill(bill.copy(id = localBill.id)) }
```

The pulled entity is mapped with `orderStatus = remoteBill.orderStatus.orFallback("completed")` and **`isSynced = true` hardcoded** (MasterSyncProcessor.kt:1471). Two failure modes:

1. **Echo-after-cancel race:** cancel happens locally (`updated_at = T1`), push batch snapshot read at T2, server persists, **pull returns the server copy with `updatedAt = T1` (client-supplied timestamp echoed back)**. If the local row's `updated_at` is still T1 and `localBill.isSynced == true` (because Path A already marked it synced!), the equal-timestamp tie-break **overwrites the local row with the server copy** — which has the *correct* cancelled status in this case, so this direction is benign. The dangerous direction:

2. **Stale-server-wins:** the pull response for a bill whose server row was **not yet updated by our in-flight push** (Stage 1: pull landed before push persisted, e.g., debounced/duplicate cycle where the second cycle's pull response was generated from the pre-push snapshot) arrives with `server.updatedAt = T0` (older) while local has `T1` — then `bill.updatedAt > localBill.updatedAt` is false and the local cancel survives **only while `localBill.isSynced == false`**. But if Path A already flipped `is_synced = 1` (mixed-row ack), then on the **next** equal-timestamp pull the tie-break `localBill.isSynced == true` lets the stale server row overwrite the local cancellation. And even without equal timestamps, `reconcilePulledBillsByClientFingerprint` (MasterSyncProcessor.kt:667) calls the same `markBillAsSyncedIfUnchanged` with the **pulled** row's status — marking the just-cancelled row synced if the pulled copy coincidentally matches on `updated_at`/`order_status`/`payment_status` — skipping it from future pushes.

**The killer detail — `statusVersion` is dropped on pull round-trips:** the pulled-bill mapper (MasterSyncProcessor.kt:1400–1496) maps **every** field explicitly and `version = remoteBill.version ?: 0L` — but has **no `statusVersion` mapping at all** (verified: `grep statusVersion MasterSyncProcessor.kt` → 0 matches). The entity's `statusVersion` column therefore keeps whatever the row had — normally fine — but in the Path-A mixed-row case the local `status_version` bump can be **lost when `updateBill(bill.copy(id=localBill.id))` in `insertSyncedBills` overwrites the whole row with the pulled entity whose statusVersion defaults to 0/unset** (BillEntity.kt:94 default `= 0`; the mapper doesn't set it, so the constructed entity carries the default 0). After that overwrite, the local row has `order_status = 'completed'` (pulled), `status_version = 0`, `is_synced = 1`. The user's cancel is gone **and cannot be re-pushed** (not unsynced), and even a fresh cancel would now win (`1 > 0`) — which is why the revert *sticks*.

### Why the UI shows the revert only after navigation

`ReportsViewModel` holds `_orderDetailsTable`/`_orderLevelRows` as plain StateFlows loaded once by `loadReports(from,to)`. They are **not** Room-derived Flows — no `distinctUntilChanged` observation of the bills table. So:
- Cancel → in-memory patch → screen shows "cancelled" instantly.
- Navigate away → ViewModel survives (or is recreated) → **returning re-triggers `loadReports()`** (via `setTimeFilter`/LaunchedEffect in `ReportsScreen`) → `getOrderDetailsTable()` re-reads Room → sees the reverted row → old status renders.

---

## Server side — what the server does with the cancel push

### `protectBillState` — the terminal-state guard (BillSyncService.java:299–334)

On every bill **update**, `GenericSyncService` (line ~649) calls `billSyncService.protectBillState(incomingBill, existingBill)`:

- If `incoming.statusVersion > existing.statusVersion` → **deliberate edit**, guard skipped, cancel applied. ✅
- Otherwise (equal or lower version):
  - `"paid".paymentStatus` is terminal — can't revert to pending.
  - `completed/paid` orderStatus is terminal — can't revert to draft.
  - **`"cancelled"` orderStatus is terminal — can't un-cancel.**
  - `incoming.statusVersion` is forced to `existing.statusVersion`.

So the server is *designed* to accept this exact cancel (client bumps version → `isDeliberateStatusEdit` true). Two server-side caveats verified:

1. **`cancelled` is treated as terminal in BOTH directions**: if the server row is already `cancelled` and a stale push says `completed`, the guard **reverts it to cancelled server-side** — correct for un-cancel protection. But it also means: if the cancel push **lost the version bump** (client sent `statusVersion` equal — see Path A version-loss above), the guard's `isFinalizedOrderStatus(existing='completed') && !isFinalized(incoming='cancelled')` branch **forces `incoming.orderStatus = 'completed'`** — the cancel push is *silently discarded server-side*, the server responds success (the record saved, with restored status), and the next pull spreads the completed status back to the device. **This is the server-side half of the revert.** The client "wins" only if `status_version` strictly increases in the *payload*.

2. **`isTransactionalIdempotentRetry` (BillSyncService.java:270–288)**: a retry whose `paymentStatus` AND `orderStatus` both match the server row is acknowledged as idempotent **without applying**. If Path A marked the row synced *after* a partial/failed first push, the genuine cancel push can arrive and be compared against an existing server row that has the same statuses → acknowledged as replay → **cancel never applied**. The response is `success` for that localId, so the client marks it synced. Silent drop.

3. **BillDTO carries `statusVersion`** (BillDTO.java:47) and the push DTO (`SyncRequestDtos.kt:41`) sends it — the transport is fine. The loss is purely the client-side entity-default overwrite described above.

### Server-side conclusion

The server's guard is **correct for its purpose** (anti-LWW-revert of gateway-settled bills) but has **no tolerance for a version-less cancel**: a cancel push arriving with `statusVersion <= existing` is treated as stale and *silently* restored, while the response still reports the record as saved. Combined with the client bug that can zero the version, the system has a state where both sides agree the cancel "didn't happen," and nothing logs it.

---

## Consolidated root cause (one sentence each)

- **Android RC-1:** `insertSyncedBills`' pulled-bill mapper never sets `statusVersion`, so any full-row overwrite by a pulled copy resets it to the entity default 0, destroying the deliberate-edit signal.
- **Android RC-2:** `markBillAsSyncedIfUnchanged` (used by both push-ack and fingerprint-reconcile) can mark a mixed/outdated row as `is_synced=1`, which both (a) skips the re-push of the cancel and (b) enables `insertSyncedBills`' equal-timestamp tie-break to let a stale server copy overwrite the local row.
- **Android RC-3 (contributing):** `updateOrderStatus` double-writes the row (targeted query + full `updateBill(copy)`), widening the snapshot-vs-ack race window (BL1).
- **Android RC-4 (UI):** Reports lists are in-memory snapshots, not Room Flows — the UI cannot self-heal and faithfully renders whatever Room ends up with; `cancelOrder` also skips the post-action `loadReports()` that `updateOrderStatus` performs.
- **Server RC-5:** `protectBillState` discards a cancel whose `statusVersion` isn't strictly greater **without failing the record** — the push is acknowledged, so the client never learns the cancel was refused. `isTransactionalIdempotentRetry` can similarly swallow a genuine cancel as a "replay."

---

## Solution (design, not yet implementation)

**Goal:** a cancel/status change must (1) survive the pull merge locally, (2) be accepted server-side exactly once, (3) be visible on every screen re-entry.

1. **Preserve `statusVersion` through pulls (fixes RC-1).** Map `statusVersion = remoteBill.statusVersion ?: existingLocal` in the pulled-bill mapper — better: since `insertSyncedBills` already loads `localBill`, carry `bill.copy(id = localBill.id, statusVersion = maxOf(bill.statusVersion, localBill.statusVersion))` before `updateBill`. A pulled row must never *decrease* the local version.
2. **Protect terminal transitions in the merge, not just the ack (fixes RC-2a/2b).** In `insertSyncedBills`' `shouldOverwrite` decision, refuse an overwrite when `localBill` has a **newer `status_version`** or when the local row is unsynced-and-newer than the pulled row. Server-wins remains the default for pure history, but an unsynced local edit always beats a stale pull.
3. **Single-write status mutations (fixes RC-3).** Collapse `updateOrderStatus`'s targeted-query + `updateBill(copy)` into one `@Transaction` DAO call; `cancelOrder` repository path already single-writes via `cancelBill` — keep it that way and stop the repo double-write.
4. **Make the ack honest (fixes RC-2 remainder).** `acknowledgeUnchangedBills` should compare `status_version` too (add it to the WHERE clause) so a row edited mid-push is never marked synced on a stale snapshot; fingerprint reconcile likewise.
5. **Server: fail loudly instead of silently restoring (fixes RC-5).** When `protectBillState` overrides `orderStatus`/`paymentStatus` on a **non-idempotent** update, add the localId to `failedLocalIds` with reason `"stale status push rejected (statusVersion X <= Y)"` instead of saving the restored record as success. The client already has quarantine machinery (`quarantineFailedBills`) that will surface it in Sync Center. Optionally: accept `cancelled` as a deliberate edit when `cancelReason` is non-blank AND `statusVersion >= existing` (belt-and-braces for clock-skewed devices that legitimately cancelled).
6. **UI self-heal (fixes RC-4).** Replace the manual list-patching in `cancelOrder`/`updateOrderStatus` with a single `loadReports(currentFrom, currentTo)` call after the repository write completes (Room is the single source of truth); or derive the tables from `billRepository.getBillsByDateRange(...)` Flows so navigation re-entry can't diverge.

Nothing above changes the sync protocol wire format, the terminal-ownership model, or the LWW semantics for non-status fields.

---

## Implementation plan (ordered, each step independently shippable)

### Step 1 — Client: stop losing `statusVersion` (Android, ~6 lines)
**File:** `MasterSyncProcessor.kt` pulled-bill mapper (~line 1493) + `BillDao.insertSyncedBills` overwrite branch (~1108).
- Mapper: add `statusVersion = remoteBill.statusVersion ?: 0` next to `version = remoteBill.version ?: 0L`.
- Overwrite branch: `updateBill(bill.copy(id = localBill.id, statusVersion = maxOf(bill.statusVersion, localBill.statusVersion)))`.
- **Verify:** unit test in `SyncEntityMappersTest`/`MasterSyncProcessorConflictIsolationTest` style: pull a bill over a locally-cancelled row → `status_version` preserved, `order_status` behaviour asserted.

### Step 2 — Client: merge guard for unsynced local edits (Android, ~10 lines)
**File:** `BillDao.insertSyncedBills` (line ~1100).
- Change `shouldOverwrite` to:
  ```kotlin
  val localIsAhead = localBill.statusVersion > bill.statusVersion
  val shouldOverwrite = !localIsAhead && !localBill.isSynced ||
      bill.updatedAt > localBill.updatedAt && !localIsAhead && localBill.isSynced ||
      (bill.updatedAt == localBill.updatedAt && localBill.isSynced && !localIsAhead)
  ```
  (exact boolean flattening at implementation time; the invariant is: **never overwrite a row whose local statusVersion is higher, never overwrite an unsynced row with an older-or-equal pull**).
- **Verify:** test: local cancelled+unsynced vs pulled completed+older → local survives; pulled strictly-newer server row (real other-terminal update) still overwrites (regression guard).

### Step 3 — Client: single-write + honest ack (Android, ~15 lines)
**Files:** `BillRepository.kt` (`updateOrderStatus` — remove the redundant `updateBill(copy)` + redundant `updateOrderStatus`/`updatePaymentStatus` DAO calls, keep one `@Transaction` write), `BillDao.kt` (`markBillAsSyncedIfUnchanged` — add `AND status_version = :pushedStatusVersion`, thread the new parameter through `MasterSyncProcessor.acknowledgeUnchangedBills` and `reconcilePulledBillsByClientFingerprint`).
- **Verify:** `BillingLogicTest` + new test: mutate row between snapshot and ack → ack returns 0 → row stays unsynced → next push carries the cancel.

### Step 4 — UI: render from Room after every mutation (Android, ~8 lines)
**File:** `ReportsViewModel.kt` (`cancelOrder` — replace both `_orderDetailsTable.update`/`_orderLevelRows.update` blocks with `loadReports(currentFrom, currentTo)`; same for `updateOrderStatus`'s manual patch).
- **Verify:** cancel → status shows cancelled; navigate away + back → still cancelled (once Steps 1–3 keep Room truthful); no flicker regression acceptable — measure by eye on device.

### Step 5 — Server: loud rejection of stale status pushes (Java, ~20 lines)
**File:** `GenericSyncService.java` (the `protectBillState` call site, ~line 649) + `BillSyncService.java`.
- After `protectBillState`, detect override: `boolean orderStatusWasOverridden = !Objects.equals(preGuardStatus, incomingBill.getOrderStatus())` (capture pre-guard values before the call).
- If overridden and `!isTransactionalIdempotentRetry(...)`-class replay: `failedLocalIds.add(localId); failedReasons.put(localId, "stale status push: statusVersion " + versionOf(incoming) + " <= " + versionOf(existing))` and `continue` (skip staging the record).
- Keep refundAmount restore and cancelledBills/finalizedBills bookkeeping as-is.
- **Compat check:** existing clients treat `failedLocalIds` by quarantining with the reason string — already-implemented behavior path, no client change required.
- **Verify:** server test: push cancel with equal version → response contains failedLocalId + reason; push with version+1 → applied. Integration: Android against staging → cancel round-trips, Sync Center shows nothing on happy path.

### Step 6 — (Optional hardening) accept cancel-with-reason at equal version (server, ~4 lines)
**File:** `BillSyncService.isDeliberateStatusEdit`.
- `return versionOf(incoming) > versionOf(existing) || ("cancelled".equalsIgnoreCase(incoming.getOrderStatus()) && incoming.getCancelReason() != null && !incoming.getCancelReason().isBlank() && versionOf(incoming) >= versionOf(existing))`.
- Trade-off note: weakens un-cancel protection marginally (a device that legitimately re-completes at equal version still can't; only cancel gains the exemption). Recommend only if field reports show version-less cancels from older app builds.

### Rollout order & regression gates
1. Ship Steps 1–4 together (one client release; all are additive guards) — gate: full existing suite (`SyncManagerTest`, `MasterSyncProcessorConflictIsolationTest`, `MasterSyncProcessorMenuRejectionTest`, `BillingLogicTest`, `SyncEntityMappersTest`, `DatabaseMigrationListConsistencyTest`) + manual: cancel on device, airplane-mode cancel → sync after reconnection, two-terminal same-bill cancel, draft → completed → cancel sequence.
2. Ship Step 5 server-side after client release is in the field (the client already handles failedLocalIds; older clients simply see the same silent behavior as today — no regression, just no fix).
3. Step 6 only with field evidence.

**Freeze list (must not change):** push/pull checkpoint atomicity, terminal-ownership guards, KOT events, payment idempotency (`operationId`), refund server-ownership, debounce/WorkManager triggers.
