# Server-side analysis prompt — "Menu item category could not be resolved"

Copy everything inside the fenced block into the server-side AI CLI tool.
It is written to be self-contained: symptom, raw client evidence, the exact client
request contract, and the server code path to start from.

```text
ROLE
You are a senior Spring Boot / JPA engineer working on the KhanaBook SaaS backend.
This is a READ-ONLY root-cause investigation first. Do not modify production data.
Propose a plan and a regression test before changing any code.

GOAL
Explain, with evidence, why the Android POS receives a record-level push rejection
"Menu item category could not be resolved" for menu items, why it repeats on every
sync cycle for the same items, and what the correct fix is on the server side.

──────────────────────────────────────────────────────────────────────────────
1. OBSERVED SYMPTOM (Android logcat, debug build, 2026-10-07)
──────────────────────────────────────────────────────────────────────────────
User-visible: editing a menu item's NAME, changing VEG -> NON-VEG, changing dish
PHOTOS, and editing VARIANTS does not persist. The edit appears saved, then reverts.

Client log, verbatim:

  17:44:26.516 MasterSyncProcessor  I  Pushing 1 menu items record(s) in 1 batch(es)
  17:44:27.595 MasterSyncProcessor  I  Pushed menu items batch 1/1: success=0, failed=1
  17:44:27.595 MasterSyncProcessor  W  Server rejected menu items localIds=553
                                       reasons={553=Menu item category could not be resolved}
  17:44:27.596 SyncManager          W  Push conflict detected; pulling latest server data
                                       before resolving
      com.khanabook.lite.pos.core.util.SyncConflictException:
          Data changed on another device. Please sync and retry.
      Caused by: java.lang.IllegalStateException:
          Server rejected menu items localIds=553

The same rejection repeats at 17:45:20 for localId=553, and again for localId=551.
Two different items, same reason, every cycle. The push body contains exactly ONE
record, and that single record fails, so `success=0`.

──────────────────────────────────────────────────────────────────────────────
2. HOW THE CLIENT SENDS THE REQUEST (this is what the server receives)
──────────────────────────────────────────────────────────────────────────────
HTTP:      POST /api/v1/sync/menuitem/push
Body:      a JSON ARRAY of MenuItemSyncDto objects
Response:  HTTP 200 whose body is PushSyncResponse { successfulLocalIds,
           failedLocalIds, failedReasons } — record-level failures are reported
           INSIDE a 200, they are not an HTTP error status.

Headers added by the Android interceptor on every sync call:
  X-Device-Id, X-Request-Id, X-App-Version, X-App-Platform,
  Authorization: Bearer <jwt>, X-Terminal-Token: <terminal token>

Body fields sent per item (SerializedName in brackets):
  localDbId, restaurantId, deviceId, localId, serverId,
  categoryId, serverCategoryId,
  name, basePrice, foodType, description, isAvailable,
  currentStock, lowStockThreshold, barcode, hasVariants,
  createdAt, updatedAt, isDeleted, serverUpdatedAt,
  permissionRevisionAtCreation, changedFields

Two of those matter most here:
  categoryId       = the item's LOCAL category id on the device (never a server id)
  serverCategoryId = the server id IF the device already knows it, otherwise null

Push order within one sync cycle (parents before children):
  restaurantprofile -> users -> categories -> menu items -> item variants -> bills

So categories are pushed before menu items in the same cycle. A menu item can still
reference a category whose server id the device has never learned — for example when
the category push itself failed earlier, or the category was created on another
device, or the item was moved into a category that was never acknowledged.

CLIENT FAILURE SEMANTICS (why this is destructive, not just noisy):
  - MasterSyncProcessor.pushBatches treats a batch where ALL records failed as a
    conflict and throws SyncConflictException (MasterSyncProcessor.kt:142-150).
  - Only reasons matching isPermissionRejection() are treated as permanent
    (MasterSyncProcessor.kt:169-177); it matches ONLY: "not permitted",
    "permission_not_granted", "revoked_after_creation", "cannot authorize",
    "stale_push_conflict".
  - "Menu item category could not be resolved" matches none of those, so the client
    treats it as a transient conflict.
  - SyncManager.handleRecoveredConflict -> recoverFromSyncConflict ->
    pullAndPersistMasterData(0L, deviceId) — a FULL re-pull from timestamp 0, which
    rewrites local rows from server state and marks them synced. The user's local
    edit is discarded and never reaches the server.

──────────────────────────────────────────────────────────────────────────────
3. SERVER CODE PATH TO START FROM
──────────────────────────────────────────────────────────────────────────────
  server/src/main/java/com/khanabook/saas/feature/menu/service/MenuItemServiceImpl.java
    pushData(tenantId, payload)  — lines ~35-100
    The rejection string is emitted at line ~83.

Relevant logic, verbatim in intent:
  If serverCategoryId == null && categoryId != null, the server tries to resolve it:
    (a) categoryRepository.findByRestaurantIdAndDeviceIdAndLocalId(
            tenantId, item.getDeviceId(), item.getCategoryId())
    (b) categoryRepository.findById(item.getCategoryId())
          + require serverCategory.getRestaurantId().equals(tenantId)
    (c) categoryRepository.findByRestaurantIdAndLocalIdIn(tenantId, [categoryId])
          .stream().findFirst()
  If serverCategoryId is STILL null, the record is added to failedLocalIds with
  "Menu item category could not be resolved" and skipped (continue).

Note precisely: lookup (a) uses item.getDeviceId() — the deviceId carried in the
payload — combined with the local categoryId. Resolution therefore depends on the
(category localId, deviceId) PAIR, not on the arbitrary localId alone.

Also note: the resolution block is only entered when serverCategoryId == null. An
item that arrives WITH a serverCategoryId never reaches this failure — so if these
items keep failing, the device genuinely has serverCategoryId == null on the row.

──────────────────────────────────────────────────────────────────────────────
4. HYPOTHESES TO TEST (confirm or eliminate each, with evidence)
──────────────────────────────────────────────────────────────────────────────
H1. The item's categoryId is a local id belonging to a DIFFERENT deviceId than the
    item's deviceId, so lookup (a) misses; (b) misses because no category row has
    that primary key; (c) misses because no category in the tenant has that localId.
    Ask: can a device hold a categoryId it never created (e.g. after pulling an item
    whose category was mapped from another device)? Check how the client maps
    categoryId on pull.

H2. The category exists in the tenant but with a different localId for that device —
    i.e. the device created a duplicate category after a failed push, and the item
    points at the orphan.

H3. The category was soft-deleted (is_deleted = 1) server-side. Inspect whether
    the three repository lookups filter on is_deleted / is_active, and whether a
    soft-deleted category can still resolve. If a lookup uses findById, a deleted
    category would resolve — if it uses a filtered query, it would not.

H4. The category push for that category failed in the same cycle (auth, validation
    or optimistic-lock), leaving the device permanently without its server id.
    Check the categories push path and whether its failures are logged apart from
    the item failures.

H5. The delete/soft-delete path: an item whose category was deleted server-side is
    now unresolvable, so even an unrelated name edit can never be pushed.

──────────────────────────────────────────────────────────────────────────────
5. WHAT TO PRODUCE
──────────────────────────────────────────────────────────────────────────────
1. The exact condition under which pushData emits this rejection, stated as a
   predicate over (deviceId, categoryId, serverCategoryId, tenantId).
2. Evidence for which hypothesis holds (repository queries, schema/migrations for
   categories, existing tests, and — if you can query it read-only — the actual
   rows for the failing items).
3. Why it repeats every cycle (i.e. why nothing self-heals).
4. A recommended fix, with trade-offs, choosing deliberately between:
     (i)  server-side: resolve more robustly (e.g. fall back to any category in the
          tenant with that localId regardless of deviceId, or accept the item's
          category NAME), and/or
     (ii) client-side: classify this reason as PERMANENT so the row is quarantined
          and the destructive conflict-recovery pull is not triggered.
   State explicitly whether this reason should be permanent, because today it makes
   the client discard the user's local edit.
5. A regression test (test class + scenario) that fails today and passes after the
   fix, plus the exact command to run it.
6. Anything you could NOT determine from the code, listed as an explicit unknown.

──────────────────────────────────────────────────────────────────────────────
6. CONSTRAINTS
──────────────────────────────────────────────────────────────────────────────
- Read-only investigation first; no production data changes, no migrations run.
- Do NOT weaken SyncPushGuard or the "master data is single-writer" rule.
- Do not silence the failure; the goal is that a real user edit either lands or is
  quarantined with an actionable reason — never silently discarded.
- Report file:line evidence for every claim. Do not guess at behaviour.
```
