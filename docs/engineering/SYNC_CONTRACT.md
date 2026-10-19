# KhanaBook — Sync Contract

> The rules that make offline-first billing safe. Read this before touching
> `feature/sync`, `MasterSyncProcessor`, `GenericSyncService`, `BillSyncService`, or any
> entity that participates in sync.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17. Companion docs:
> `ARCHITECTURE.md` (blast radius), `DATA_MODEL.md` (both schemas),
> `../CONTEXT.md` (vocabulary), `../product/PROBLEM.md` (open sync defects).

## 1. Vocabulary

| Term | Meaning |
|---|---|
| **Terminal** | One installed POS identity (`terminalId`, device id, series). Scopes DB access and print ownership. |
| **Push** | Local unsynced rows (`isSynced = false`) → server. |
| **Pull** | Server master data → local DB, inside one transaction. |
| **Checkpoint** | `lastSyncTimestamp`, committed only after a full push+pull cycle succeeds. |
| **statusVersion** | Monotonic per-bill version; the only signal that a status/payment change was deliberate. |
| **operationId** | Idempotency key (`restaurant:terminal:token:action`) preventing duplicate bills across retries. |
| **Quarantine** | `sync_quarantine_records` — rows that failed to sync, with reason and snapshot. |

Authority: **the server is the source of truth**; the device is the source of truth for
its own unsynced work. Neither may silently discard the other's acknowledged writes.

## 2. Push

### 2.1 Triggers

| Trigger | Where | Behaviour |
|---|---|---|
| Business save | `SyncManager.triggerImmediateSync()` | debounced 5 s, then a full cycle |
| Periodic | `MasterSyncWorker` | WorkManager, `NetworkType.CONNECTED`, backoff |
| Targeted | `SyncManager.pushBillOnly(billLocalId)` | single bill, e.g. payment completion |

### 2.2 Serialization and cancellation

- A single `syncMutex` serializes full cycles — **two cycles never interleave**.
- The whole cycle runs inside `withContext(NonCancellable)`. An interrupted push would
  leave the server transaction uncommitted and the next attempt would see a 409, so
  acknowledgement must not be cancelled by lifecycle events.

### 2.3 Order and paging

- Master data is pushed before its dependents (categories → menu items → variants),
  then bills → bill items → bill payments.
- Page sizes: **bills 100**, **bill items and payments 200**.
- Server cap: `SyncPushGuard.MAX_PUSH_BATCH_SIZE = 200` — a larger batch is rejected
  with **413 PAYLOAD_TOO_LARGE**. Client page sizes must stay ≤ this.

### 2.4 Acknowledgement contract

Every push returns:

```
PushSyncResponse { successfulLocalIds, failedLocalIds, localToServerIdMap }
```

Rules:

1. **Only acknowledged ids are marked synced.** No optimistic global flag flip.
2. `localToServerIdMap` is written back so child rows can reference server ids.
3. An ack is honoured only if the local row still matches what was pushed; a row that
   changed mid-push is re-pushed.
4. A stale `updatedAt` echo is rejected by the server with **409**.

### 2.5 Conflict isolation

A 409 on one record must not discard the batch's other acknowledged work:

- **Bill family** conflicts are resolved by re-push.
- **Other families** escalate to a timestamp = 0 recovery on the next cycle.

### 2.6 Server-side push guards

| Guard | Rule |
|---|---|
| `SyncPushGuard.validateBatchSize` | ≤ 200 records, else 413 |
| `SyncPushGuard.requirePermission` | OWNER/KBOOK_ADMIN pass; others must hold the permission key or the **whole batch** is 403 |
| `SyncPushGuard.isMasterDataWriter` | only OWNER / KBOOK_ADMIN may mutate master data |
| `MenuPushAuthorizer` | per-record revalidation for menu edits, using `MenuChangeType.satisfies` (holding `menu.edit_full` satisfies `menu.edit_price` and `menu.toggle_availability`) |
| Tenant scoping | every write resolves `restaurantId` from `TenantContext`; `restaurantId` filtering is enforced on read and write |
| Terminal identity | strict `X-Terminal-Token` in production (`terminal.sync.strict=true`) |

## 3. Pull

1. `SyncManager.runSyncCycle` captures `syncCheckpointTimestamp = getLastSyncTimestamp()`
   **before** any work.
2. `MasterSyncProcessor` pulls master data page by page and merges pages
   (`SyncManager.mergeMasterSyncPages`).
3. All local writes happen inside `db.withTransaction`.
4. **The checkpoint is committed only after the entire push+pull cycle succeeds.** If
   push succeeds and pull fails, the checkpoint stays old and the next cycle re-pulls
   the missed window — no record loss.

### 3.1 Server-owned fields on pull

| Field | Rule |
|---|---|
| `statusVersion` | pulled and clamped to `max(local, remote)` (`BillDao`) so a deliberate local edit is never overwritten |
| `paymentStatus` = paid, `paidAt`, gateway txn fields | server-owned; a stale device push cannot revert them |
| `is_favourite` (categories, menu items) | server-owned; never pushed — a category sync push is a whole-row write and the server restores its own value |
| `local_favourite` (menu items) | device-local pin, never leaves the device |
| restaurant `joinedAt` | server-owned; kept out of the till's reach |
| `permission_cache` | granted keys + revision, so offline authorization can be revalidated |

## 4. Conflict model

### 4.1 statusVersion

- A **deliberate** edit is a strictly greater version, **or** an equal-version forward
  transition (device read the server's current version, then advanced the bill out of a
  draft state).
- **Demotions out of finalized states and un-cancel transitions require a strictly
  greater version** — a stale push can never cancel a settled bill.
- `paid` is terminal: `BillSyncService.protectBillState` restores the paid state when a
  stale push tries to unset it.

### 4.2 Reporting

A rejected push must surface in `failedLocalIds` so the actor learns the write did not
land. Silent restore is a defect (see `../product/PROBLEM.md` §11 for the untested
two-writer case).

### 4.3 Known open defect

Category reorder/rename/delete can be lost across restart on affected tenants: the push
returns 409, and conflict recovery pulls with `lastSyncTimestamp = 0`, which rewrites
local categories to server order. Tracked in `../meta/KNOWN_ISSUES.md` and
`../product/PROBLEM.md` item 1. **Do not paper over it by disabling the version guard.**

### 4.4 Restaurant profile: per-field stale check

`restaurant_profile` keeps a per-field revision map (`field_revisions`, format
`field=revision,...`, `*` for whole-record writes; migration `V115`). A masked profile push
(one that names `changedFields`) is rejected only if a named field changed after the till's
claimed revision. Pushes without a mask, and rows without a revision map, use the old
whole-row check. Android profile pushes do not set a mask today, so they still get the
whole-row check.

## 5. Identity and scoping

- Bill identity is `(restaurant_id, device_id, local_id)` — unique-constrained on both
  sides (`bills_restaurant_id_device_id_local_id_key` and siblings).
- Android DAO reads are **terminal-scoped** (`created_terminal_id = :terminalId`).
  Cross-terminal bills are not present in the device's data, so never add a UI-level
  "cross-terminal" check to compensate.
- Device onboarding precedes login-dependent sync:
  `SyncManager.ensureTerminalActivated()` → `POST /sync/terminal/activate`.
  A 202 means `TerminalPendingApprovalException` and billing stays blocked.

## 6. Offline authorization

Approved model (see `core/security/authz/OfflineAuthClass`):

| Class | Treatment at sync |
|---|---|
| `OFFLINE_ALLOWED` | accept, no revalidation (offline-first core) |
| `REVALIDATED_ON_SYNC` | allowed offline, server re-checks the permission |
| `ONLINE_ONLY` | offline-created operation is quarantined |
| `NEVER_OFFLINE` | must never be created offline; quarantined for review |

`OfflineAuthDecider.decide(...)` returns `ACCEPT` / `REJECT` / `QUARANTINE` and performs
no I/O. Terminal validity is checked first (`TERMINAL_REVOKED` wins over any permission
state). Permission revisions are stamped on the device and pulled back in
`permission_cache`, so a revoked permission cannot keep working offline forever.

## 7. Invariants — do not break these

1. Push acknowledgement is per record; never flip `isSynced` for unacked rows.
2. The checkpoint is written only after a fully successful cycle.
3. Every full cycle is serialized by `syncMutex` and wrapped in `NonCancellable`.
4. Server-owned fields (paid state, favourites, revision, join date) are never trusted
   from a device push.
5. Never exceed 200 records per push.
6. A 409 on one family must not erase the batch's other acknowledgements.
7. Terminal scoping lives in the DAO, not the UI.
8. Any new synced entity needs: local table + migration, DTO, mapper, server entity +
   endpoint, and a decision on its `OfflineAuthClass`.

## 8. Where the code lives

| Concern | Android | Server |
|---|---|---|
| Cycle orchestration | `feature/sync/domain/SyncManager.kt` | — |
| Push/pull processing | `feature/sync/domain/MasterSyncProcessor.kt` | `feature/sync/service/GenericSyncService.java` |
| Endpoints | `core/network/KhanaBookApi.kt` | `feature/sync/controller/MasterSyncController.java` + per-entity controllers |
| Guards | — | `feature/sync/validation/SyncPushGuard.java`, `core/security/authz/MenuPushAuthorizer.java` |
| Conflict rules | `BillDao` clamp, `MasterSyncProcessor` | `feature/billing/service/BillSyncService.java` (`protectBillState`) |
| Queue | `sync_quarantine_records` (`SyncQuarantineEntity`) | — |
| Worker | `feature/sync/worker/MasterSyncWorker.kt` | — |

## 9. Testing hooks

```bash
# Android unit tests for sync behaviour
./gradlew.bat testDebugUnitTest --tests "*MasterSyncProcessor*"

# Server sync tests
cd services/server && ./mvnw test -Dtest='*Sync*Test'
```

Existing coverage worth extending: `MasterSyncProcessorCategoryFavouriteTest`,
`MasterSyncProcessorConflictIsolationTest`, `MasterSyncProcessorLocalFavouriteTest`,
`MasterSyncProcessorMenuRejectionTest`, `MenuPushAuthorizationIntegrationTest`,
`MultiDeviceInvoiceSyncIntegrationTest`.

See `../qa/TESTING.md` for suite-level commands and CI gates.
