# Menu: moving items between categories + favourite categories — feasibility review and plan

**Date:** 2026-10-15
**Scope:** Menu master data across `server` (Spring Boot + Flyway), `Android` POS (`com.khanabook.lite.pos`), `web-admin` (Angular)
**Constraint from the owner:** both changes are **owner-only**.

**Method:** static analysis of the code paths that would carry these changes (schema, sync protocol, authorization, both UIs, billing read path). No server or Android tests were run and no move was executed against a live stack. Every claim below is read out of a named file and marked **[verified]**; the three questions reading code cannot settle are listed explicitly in §7 instead of being guessed at.

---

## 0. Verdict in one table

| Requirement | Possible on today's design? | What is actually missing |
|---|---|---|
| **A. Move a dish from one category to another** | **Yes — the design already carries it.** Schema, sync field-mask, permission diff and the web-admin write path all handle a category change today. | POS UI (there is none), silent-loss paths on both ends, no actionable conflict message, no push nudge on web writes. **No migration needed.** |
| **B. Favourite menu categories** | **New feature — nothing exists.** No `favourite`/`is_pinned`/`pinned` column, DTO field, Room column or UI anywhere in product code. | A column (or table) + sync-safe null handling + UI on both surfaces. **One migration needed.** |

Recommendation: ship **A** first (it is mostly wiring + guardrails and delivers the owner's workflow today), then **B** on top of the same category plumbing.

---

## 1. What exists today (the ground truth)

### 1.1 Schema

`categories` — `server/src/main/resources/db/migration/V1__init_schema.sql:80`

| column | notes |
|---|---|
| `id` | server id |
| `local_id`, `device_id` | device identity pair (the sync upsert key) |
| `restaurant_id` | tenant |
| `name`, `is_veg`, `sort_order`, `is_active` | the owner-visible fields |
| `updated_at`, `server_updated_at`, `is_deleted`, `created_at`, `version` | sync bookkeeping |

`menuitems` — same file, `:96`

| column | notes |
|---|---|
| `category_id` | **NOT NULL** — after resolution this holds a **server** category id |
| `server_category_id` | nullable; what the pull uses to find the device's local category |
| `name`, `base_price`, `food_type`, `description`, `barcode`, `image_url`, `is_available`, stock fields | item data |
| `has_variants` | V106; explicit pricing-mode flag (see §3.4 for why it matters here) |

Two constraints that decide the whole design:

- **`V5__menu_item_name_uniqueness.sql`** — `UNIQUE (restaurant_id, category_id, lower(normalized name)) WHERE is_deleted = false`. Item *names are unique per category*, so **moving an item into a category that already has that name is a hard DB conflict**.
- **`menuitems.category_id` is NOT NULL.** An item can never be category-less; there is no "Uncategorised" row to fall back to, and no server-side notion of one. **[verified]**

Android Room mirrors this: `MenuItemEntity.categoryId` (local id) + `serverCategoryId`, `CategoryEntity` with a **local** `UNIQUE (restaurant_id, name)` index, and `menu_items.category_id` declared as `ForeignKey(... onDelete = CASCADE)` (`feature/menu/data/MenuItemEntity.kt`). Two consequences: a hard delete of a category row **deletes its items locally**, and the local category-name uniqueness has **no server counterpart**. **[verified]**

### 1.2 A move is already a first-class operation in the sync protocol

This is the important finding: the pieces are already there, which is why the change is cheap.

1. **Field-mask merge already handles it.** `GenericSyncService.applyChangedFieldsMerge` (`:1344`) restores the server value for every field *not* named in the device's `changedFields` mask — and it names `categoryid` and `servercategoryid` explicitly:
   ```java
   if (!changed.contains("servercategoryid")) incomingItem.setServerCategoryId(existingItem.getServerCategoryId());
   if (!changed.contains("categoryid"))       incomingItem.setCategoryId(existingItem.getCategoryId());
   ```
   So "move" vs "everything else untouched" is already expressible. **[verified]**

2. **The permission diff already classifies it.** `MenuPushAuthorizer.metadataChanged` includes `getServerCategoryId()`, and `MenuChangeType.METADATA_ONLY` maps to `menu.edit_price` (satisfiable by `menu.edit_full`). A move is therefore authorized exactly like a rename — no new permission key, no new `MenuChangeType`. **[verified]**

3. **The server already resolves the destination category, tenant-safely.** `MenuItemServiceImpl.resolveServerCategoryId` (`:206`) has a documented 5-step chain and verifies ownership in every branch, so a client cannot point an item at another restaurant's category. **[verified]**

4. **The web admin can already perform the move.** The item form has a category `<select>`; `openEditModal` seeds `formCategoryId = item.categoryId`; `submitForm` puts `categoryId` in the PUT (`web-admin/.../menu-page.component.ts:1054-1070`); `BusinessWriteService.updateMenuItem` sets **both** `categoryId` **and** `serverCategoryId` to the destination and `touch()`es the timestamps. **[verified]**

5. **The device pull already lands it.** `MasterSyncProcessor` (`:1381-1385`) resolves the pulled row's category as `categoryIdMap[serverCategoryId] ?: serverCategoryId ?: categoryId` and writes the **local** category id into the FK. **[verified]**

6. **The item mask on the device already fires.** `MenuRepository.computeChangedFields` (`:155`) adds `"categoryId"` when it changes, and `updateItem` unions it with any still-unacknowledged mask. **[verified]**

### 1.3 Owner-only is already the enforcement posture

| Surface | Gate | Where |
|---|---|---|
| Web admin menu + categories (`/business/**`) | `hasRole("OWNER")` | `core/security/SecurityConfig.java:156` |
| POS → server category push (`/sync/menu/categories/**`) | `SyncPushGuard.requireMasterDataWriter()` → `OWNER`, `KBOOK_ADMIN` | `feature/sync/validation/SyncPushGuard.java:56` (roles at `:15`), `CategoryController` |
| POS → server item push | per-record rejection for non-owner roles with a 200 + `failedReasons` (keeps the sync loop alive) | `MenuItemServiceImpl.pushData:53-57` |
| Menu extraction (`/menus/**`) | `hasRole("OWNER")` | `SecurityConfig.java:172` |

So **"owner-only" needs no new gate for either requirement** — the two write surfaces the owner will use are already owner-only, and staff terminals are structurally excluded from menu master data. **[verified]**

### 1.4 Can the owner do this on **both** surfaces (web admin *and* POS)?

Yes — and it is one feature, not two. Both surfaces write the **same synced rows** (`categories`, `menuitems`), so a change made on either side reaches the other through the sync loop with no duplicated logic; each surface only adds UI. Readiness differs per surface:

| | **A. Move an item between categories** | **B. Favourite categories** |
|---|---|---|
| **Web admin** | **Works today** — item form has the category select, `updateMenuItem` sets `categoryId` + `serverCategoryId` (`menu-page.component.ts:1054-1070`, `BusinessWriteService:400`). Missing: precise clash error (G3), propagation nudge (G4), bulk move (A6). | Needs the column + a star in the category list (`menu-page.component.ts:438-461`). `/business/**` is already `hasRole("OWNER")`. |
| **POS (Android)** | Data layer is **ready** (`MenuRepository.computeChangedFields:155` already emits `categoryId`; `MenuViewModel.updateItem` exists). Missing: the UI — `ItemEditDialog` has **no** category field — plus the pull hardening (G5). | Needs Room migration + entity/DTO + a star in `ManageCategoriesDialog` + pinning order in the billing tab strip (`MenuSelectionStep:252-281`). |

**Three things that only appear once both surfaces write the same rows:**

1. **The two surfaces will fight over a favourite unless the column and its compatibility rule ship in the same release.** `Category` has no field mask, so a terminal's whole-record push (rename, reorder, active toggle) resets any column it does not know about — the `has_variants`/V106 failure mode. Set the star in the web admin, then let a terminal rename that category, and the star silently disappears on both surfaces. **Do not ship B1 before B2** (§3.4).
2. **On the POS, owner-only is a safety requirement, not a UI preference.** Categories are pushed as a **whole batch** to `/sync/menu/categories/push` (`MasterSyncProcessor:543-555`) behind `SyncPushGuard.requireMasterDataWriter()`, so a non-owner push is a **403 for the entire call** (unlike menu items, which fail per record inside a 200). And the POS worker treats a 403 as *"possible role change or terminal deactivation"* and calls `sessionManager.invalidateAuthSession()` (`MasterSyncWorker:130-133`). Consequence: if a staff member could star, reorder, rename or move a category on a terminal, that tap could **log the terminal out rather than just fail to sync**. The advisory UI gate must therefore match the server gate exactly (OWNER only), and if per-cashier pins are ever wanted they must be **device-local** (never pushed), not a synced category field.

   *The full chain is readable in the code: inside the shared `pushBatches` helper only **409** is special-cased (`MasterSyncProcessor:260`, and only when the caller passes `isolateHttpConflicts`), so a 403 falls through the generic `catch (e: Exception)` and propagates to `MasterSyncWorker`'s `onFailure`, whose 403 branch then invalidates the session. Nothing swallows it in between.*
3. **Concurrent moves are last-write-wins on a single-valued field, and the loser is never told.** The mask protects *other* fields (admin renames while a terminal moves → both land), but two moves of the same item to different categories resolve by `updatedAt` alone (`:723`), so one silently disappears. Acceptable for now; a "this dish was moved by the admin" notice would be a separate conflict-UI feature. Note the two directions are not equally safe: **web → device is clean**, while **device → server** depends on the destination category already being on the server (G2) and can drop/misplace on the pull (G5). Both of those failure modes are **POS-side only** — the owner's web-admin workflow never hits them.

---

## 2. Requirement A — move an item between categories

### 2.1 Target flow (web admin → terminals)

```
Owner edits item in web-admin, picks a different category
        │  PUT /business/menu/{id}  { categoryId: <destination server category id> }   [OWNER only]
        ▼
BusinessWriteService.updateMenuItem
        │  requireCategory(tenant, destId)        → 400 "Category not found" if not ours
        │  item.categoryId = destId; item.serverCategoryId = destId;  touch(now)
        ▼
menuitems row updated; serverUpdatedAt bumped
        │  ❌ no pushSyncNow() today  →  terminal waits for its 15-min periodic sync
        ▼
POS MasterSyncWorker pull  →  MasterSyncProcessor
        │  localCategoryId = categoryIdMap[serverCategoryId]
        │  item lands in the destination local category
        ▼
Billing tab strip (activeCategories) and Menu Configuration group the dish correctly
```

### 2.2 Gaps, each with the fix

**G1 — The POS cannot move an item at all.** `ItemEditDialog` edits name, price, food type, variants and photo; it contains **no** reference to category (`ui/ItemEditDialog.kt` — zero matches for `category`), and there is no `moveItem`/`changeCategory` anywhere in the menu feature. Today the move is web-admin-only.
*Fix:* add a category dropdown to `ItemEditDialog` (it already receives the item and emits a `MenuItemEntity`), pass `categories` in from `ManualMenuView`, and call the existing `MenuViewModel.updateItem(item)` — the mask computes itself. **~1 day, no server or data change.** Also worth a "Move to category…" row action on the item list for the one-tap case.

**G2 — A move can be silently swallowed server-side.** `resolveServerCategoryId` step 5 explicitly falls back to *the category the server row already points at* when nothing else resolves. For a rename that fallback is correct (a stale id must not fail an unrelated edit — the class of bug it was written for). For a **move** it is wrong: the destination category is usually the one that does not exist server-side yet, so the fallback rewrites `categoryId` back to the current category, the mask keeps that value, and the push returns **200 success** with the move discarded. The device then re-pulls and quietly reverts.
*Fix:* make the fallback mask-aware — when the incoming mask names `categoryid`/`servercategoryid`, failing to resolve must be a **recorded failure**, not a silent reuse:
```java
if (resolved.isEmpty()) {
    if (declaresCategoryChange(item)) {                 // changedFields names categoryId/serverCategoryId
        addFailure(failedLocalIds, failedReasons, item.getLocalId(),
            "Destination category has not reached the server yet - it will sync first");
        continue;
    }
    resolved = existingItemCategory(tenantId, item);    // unchanged behaviour for non-category edits
}
```
The device already surfaces `failedReasons` and retries on the next cycle, so the move lands one cycle after the category does. **~0.5 day + a test.**

**G3 — Moving into a name clash fails with an unhelpful message.** The unique index (V5) fires, `GlobalExceptionHandler.handleDataIntegrityViolation` (`:132`) returns **409** `{"error": "A data conflict occurred. A duplicate or invalid reference was detected."}` — with **no `message` field** — and the web-admin form only reads `err.error?.message`, so the owner sees *"Failed to update item. Please try again."* (`menu-page.component.ts:1078`). The sync path by contrast already produces a precise `DuplicateMenuItemException` → 409 `"Item already exists in this category"`. **[verified]**
*Fix:* in `BusinessWriteService.updateMenuItem`/`createMenuItem`, check the destination category for a normalised-name clash **before** saving and throw the existing `DuplicateMenuItemException` with a code + the conflicting item's name; add `message` to the data-integrity response; surface it in the web-admin dialog with the three real choices (**rename**, **merge into the existing dish**, **cancel**). **~1 day.** This is the single biggest UX gap: it is the one conflict an owner will actually hit, since the natural way to merge two small categories is to move dishes until one is empty.

**G4 — Web-admin edits do not nudge the terminals.** `pushSyncNow()` (FCM `sync_now`, honoured by `KhanaBookFirebaseMessagingService:56` → `triggerImmediateSync()`) is called from the sync-push paths and AI import only — never from `BusinessWriteService`. The POS periodic pull is **15 minutes** (WorkManager's floor; `MasterSyncWorker:81`). So a category move made in the web admin can take a quarter of an hour to reach the counter, while the same change made *on* a terminal propagates in seconds. **[verified]**
*Fix:* call `pushNotificationService.pushSyncNow(restaurantId)` on the menu/category write paths in `BusinessWriteService` (fire-and-forget, already safe under saturation). **~0.5 day.** This also fixes the same staleness for price/name/availability edits made in the web admin.

**G5 — The device pull can drop a moved item.** `MasterSyncProcessor` skips any pulled item whose resolved category is not in `knownCategoryIds` (`:1385`), and the `?: serverId` fallback (`:1382`) can map to a *coincidental* local id. If the destination category has not been pulled yet, the dish disappears from the terminal's menu until the category arrives — and if a local category happens to hold that id number, the dish lands in the **wrong** category. **[verified]**
*Fix:* never invent an id in the fallback; park the row (a small `pending_menu_item_category` retry table) or simply re-run the pull once after the category upsert — the category block runs earlier in the same pass, so ordering the resolution after a flush of category inserts removes the race. Add a unit test for "item moved to a category this device has not seen yet".

**G6 — There is no position within a category.** `menuitems` has no `sort_order` column (only `categories` does) and Android orders items `... ORDER BY name ASC` in every category query (`MenuDao.kt:50,53,194`). "Move to Beverages **and put it first**" is not expressible.
*Options:* (a) accept name ordering for v1 (recommended — the owner's ask is the move, not the rank); (b) `menuitems.sort_order` + the same field-mask pattern as categories. Do (a) now, and only do (b) if the owner asks for manual ordering inside a category.

**G7 — Unused/empty destination and delete asymmetry.** Web admin blocks deleting a non-empty category (`BusinessWriteService.deleteCategory:486` → `IllegalStateException` → 400 with a clear message). The POS by contrast has `CategoryRepository.deleteCategory`, which soft-deletes the category **and cascade-soft-deletes every item and variant in it** — the button was removed from `ManageCategoriesDialog` at the owner's request (`:211`), but the repository path remains callable. **[verified]**
*Fix:* keep delete web-admin-only (already effectively true), and when the UI reports "Cannot delete category containing active menu items", offer **"move all items to…"** in the same dialog. That is the human workflow the owner is asking for.

**G8 — Two silent data-shape hazards worth a guard test.** (i) The Room `ON DELETE CASCADE` FK means any future *hard* delete of a category row takes its items with it; every current path is a soft delete, so this is a latent trap rather than a live bug. (ii) Category names are unique **locally** but not on the server, so two devices creating the same category name concurrently can diverge. Neither blocks A; both deserve a test that pins current behaviour.

### 2.3 Conflict semantics an implementer must respect

- **LWW is `incoming.updatedAt >= existing.updatedAt`** (`GenericSyncService:723`); an older push is rejected with `"Incoming record is older than the server record"` (`:873`).
- **Categories additionally have Phase-3 stale-push protection** (`:730-747`, reason recorded at `:738`): if the server's `serverUpdatedAt` is ahead of the incoming record's, the push is **quarantined** with `STALE_PUSH_CONFLICT` rather than applied. Menu items are *not* in that list — their protection is the field mask instead.
- **Clock skew is guarded** (`sync.clock-skew.max-ms:300000`, offline grace 30 days, `:630-680`), but a device whose clock runs fast can still hold a "newer" local row and keep re-pushing a stale category; `MenuPullMergePolicy` deliberately lets a *dirty* row with `updatedAt >= pulled.updatedAt` survive, so the device eventually converges only via the server's LWW rejection.
- **Practical consequence:** a web-admin move is safe against a terminal editing *another* field of the same dish (the mask protects it) — that is the designed-in win. It is not safe against the *same* device re-pushing a whole-record edit from a build that never sends a mask (legacy `changedFields = null` = full-record LWW), which will revert the category along with everything else.

### 2.4 Plan A (sequenced)

| # | Work | Files | Acceptance test |
|---|---|---|---|
| A1 | Mask-aware failure instead of the silent old-category fallback | `MenuItemServiceImpl.resolveServerCategoryId` | Move with an unresolvable destination → record appears in `failedReasons`, item unchanged on server, lands on the next push after the category exists |
| A2 | Precise 409 for a name clash in the destination (+ `message` on the integrity response, + web dialog copy) | `BusinessWriteService`, `GlobalExceptionHandler`, `web-admin menu-page` | Moving "Tea" into a category that already has "Tea" shows "A dish called Tea is already in Hot Drinks" and offers rename/merge/cancel |
| A3 | `pushSyncNow` on web-admin menu/category writes | `BusinessWriteService` | After an admin save, a subscribed terminal runs an immediate sync (device log + `sync_now` FCM) |
| A4 | POS "Move to category…" — dropdown in `ItemEditDialog` + list row action | `ItemEditDialog.kt`, `ManualMenuView.kt`, `MenuViewModel` | Move on terminal → server row shows the destination; second terminal shows it after pull |
| A5 | Pull-side category resolution hardening (no invented ids; retry/park) | `MasterSyncProcessor` | Item moved to a category the device has not pulled yet ends up in the right category once it arrives, never in a wrong one, never missing |
| A6 | Web-admin "Move all items to…" from the delete-blocked panel | `menu-page.component.ts` (+ a small bulk endpoint or N sequential PUTs) | Owner empties a category and deletes it in one flow, with a per-item result summary |

Estimated total: **4–6 focused days** including tests; A1–A3 are the correctness core and are worth doing even if the UI work is deferred.

---

## 3. Requirement B — favourite categories

### 3.1 Nothing exists yet

`favourite` / `favorite` appears **only** in skill/tooling markdown under `.agents/`, `.claude/`, `.github/`, `.kiro/`, `.opencode/` — **zero** hits across `server/src`, `Android/app/src/main`, `web-admin/src`, and every migration. There is also no "popular / bestseller / most-ordered / recent" concept to repurpose. **[verified]**

### 3.2 First decide *whose* favourite (this changes the design)

| Interpretation | Data owner | Sync needed? | Cost |
|---|---|---|---|
| **B-owner** — the owner pins categories in the admin; the pin is a property of the restaurant | restaurant | **yes** (web → terminals) | medium |
| **B-cashier** — each cashier/terminal pins categories for fast billing | device (or user) | no (or per-user) | **low**, but not what the owner asked for |
| **B-customer** — a diner favourites a category in a QR/ordering surface | customer | yes + a customer identity | high; **no customer surface exists in this repo**, so this is a new product, not a feature |

Everything below assumes **B-owner**, with **B-cashier** as a possible cheap follow-up using the same UI.

### 3.3 Design options

| Option | Shape | Pros | Cons |
|---|---|---|---|
| **(a) boolean on `categories`** *(recommended)* | `is_favourite BOOLEAN NOT NULL DEFAULT FALSE` + reuse `sort_order` for the pinned order | one column, rides the existing category sync, both surfaces read it for free | touches a **mask-less** entity (§3.4); needs null-preserving handling |
| **(b) separate `favourite_categories` table** | `(restaurant_id, category_id, position)` | clean model, explicit order, no change to the synced category row, easy to move to per-user later | new entity/DTO/repo/controller, its own sync story or a REST-only path, more code |
| **(c) device-local only** | Room column, never pushed | zero server work | the owner cannot set it from the web admin — fails the stated requirement |

Recommend **(a)**. Reach for (b) only if per-cashier pins or a specific pinned order distinct from `sort_order` becomes a requirement.

### 3.4 The one real hazard: a new column on a mask-less synced entity

`Category` has **no field-mask merge** — only `MenuItem` and `RestaurantProfile` do (`GenericSyncService.applyChangedFieldsMerge` handles just those two types; the category equivalent does not exist). Category conflict handling is whole-record LWW, plus the Phase-3 `STALE_PUSH_CONFLICT` quarantine.

That is exactly the shape of the documented `has_variants` incident (V106): a client build that predates a column pushes `null` for it, and a whole-record write silently resets the new field. So **do not add the column without one of these**:

1. **Precedent-matching fix (recommended):** treat `null` as "this client does not know the column" and restore the stored value, the way `preserveServerOwnedState` already does for `has_variants` and `isAvailable`:
   ```java
   if (incoming instanceof Category in && existing instanceof Category ex) {
       if (in.isFavourite() == null) in.setIsFavourite(ex.getIsFavourite());   // never demote on an old client's push
   }
   ```
   plus `is_favourite NOT NULL DEFAULT FALSE` so reads are never ambiguous. Old builds keep working; the pin survives their pushes.
2. Give categories a field mask like `MenuItem` has — more correct long-term, materially more work (device mask computation, server merge, tests).
3. Version-gate the feature (server flag + min client build) — least work now, but leaves a permanent compatibility cliff.

Also note the interaction with **Phase-3 quarantine**: because the owner's web toggle bumps `serverUpdatedAt`, a terminal that pushes a *stale* category row is already rejected with `STALE_PUSH_CONFLICT` instead of clobbering the pin. The remaining hole is a terminal push that is genuinely *newer* than the server row — precisely what fix (1) closes.

### 3.5 Where it surfaces

- **Billing (the payoff):** the tab strip is built from `menuViewModel.activeCategories` (`MenuSelectionStep.kt:252-281`), i.e. `WHERE is_active = 1 ... ORDER BY sort_order ASC`. Favourites should float to the front of that strip, ordered by the category's `sort_order` **or** a separate pinned order — the owner's likely intent is "the six categories this counter actually uses are one tap away, everything else is behind a 'More' affordance".
- **Menu Configuration:** a star affordance on each category card; the dialog already has drag-to-reorder (`ManageCategoriesDialog`, `onReorderCategories`), so "pin one to the front" and "reorder" must not fight over the same field — decide whether `is_favourite` affects `sort_order` or merely the *presentation* order.
- **Web admin:** a star in the category list (`menu-page.component.ts:438-461`), owner-only by default since the whole `/business/**` surface is OWNER-gated.
- **Reporting/receipts:** out of scope; a pin must never affect printing or GST grouping.

### 3.6 Plan B (sequenced)

| # | Work | Files | Acceptance test |
|---|---|---|---|
| B1 | Migration `V###__category_is_favourite.sql`: `ADD COLUMN is_favourite BOOLEAN NOT NULL DEFAULT FALSE` (+ index on `(restaurant_id, is_favourite)` if the query needs it) | `server/src/main/resources/db/migration/` | Existing rows default false; migration is additive and reversible-by-null |
| B2 | Null-preserving server rule for the new column, next to the `has_variants` precedent | `GenericSyncService.preserveServerOwnedState` | A push from a pre-feature client leaves an existing `true` untouched (explicit test, mirroring `GenericSyncMenuFieldMaskTest`) |
| B3 | Entity/DTO/repo plumbing | `Category.java`, `CategoryDTO.java`, `CategoryRepository` | Round-trip test: push `isFavourite=true` → pull returns it |
| B4 | Web-admin star toggle | `menu-page.component.ts`, `business-api.service.ts` | Toggle → 200, survives reload, audit-logged (*see open question*) |
| B5 | POS: Room migration + entity/DTO mapper + star in Manage Categories + pin-to-front in the billing strip | `AppDatabase.kt`, `CategoryEntity.kt`, `SyncRequestDtos.kt`, `SyncEntityMappers.kt`, `ManageCategoriesDialog.kt`, `MenuSelectionStep.kt` | Room migration test (`AppDatabase` already adds columns via `ALTER TABLE`, e.g. `:420`); ordering test for the billing strip |

Estimated total: **3–5 days** with the compatibility work in B2 done properly. Doing B2 badly is how this becomes a second `has_variants` incident.

---

## 4. Tests and rollout

**Server**
- `MenuItemServiceImpl` — move with a resolvable destination; move with an unresolvable destination (**A1**); move where the mask does *not* name the category (server value must win); cross-tenant category id rejected.
- `BusinessWriteService` — move into a name clash → precise 409 (**A2**); move to an empty/new category; move + rename in one request.
- `GenericSyncService` — category push from a client that omits `is_favourite` preserves the stored value (**B2**, model it on `GenericSyncMenuFieldMaskTest`).
- `MenuPushAuthorizer` — a category-only change classifies as `METADATA_ONLY` and rejects a user without `menu.edit_price`/`menu.edit_full` (pin the existing behaviour before changing anything near it).

**Android**
- `MenuRepository.updateItem` — a category change produces `changedFields` containing `categoryId` (guard for the mask; the file already has this class of test).
- `MasterSyncProcessor` — a pulled move updates the local FK; a move whose destination category is unknown resolves correctly once the category lands and **never** lands in a wrong category (**A5**).
- `MenuPullMergePolicy` — a clean row always adopts the pulled category; a dirty row with a skewed-fast clock does not silently win forever.
- Room migration test for the new column, in the same style as the existing `server_category_id` addition.

**Rollout order** (server first — it must be able to serve both old and new clients)
1. Server: A1–A3 + B1–B3 (additive, no client dependency).
2. Web admin: A2 copy, A6, B4 — the owner can start using moves immediately.
3. POS: A4, A5, B5 — ship behind a build gate; verify against a pre-feature server and a pre-feature device.

**Do-not-ship checklist**
- Never let a move silently no-op (A1) or silently land in the wrong local category (A5).
- Never let a stale client's push clear a favourite (B2).
- Never let `business/menu` return 200 for a write that did not happen.
- Keep the POS's mass-delete-category path (cascade) out of the move workflow.

---

## 5. Risk register

| Risk | Sev | Likelihood | Mitigation |
|---|---|---|---|
| Move silently lost on the sync path | **High** (silent data divergence) | High without A1 | A1 + a test that asserts a recorded failure |
| Wrong-category landing on the device (id coincidence) | **High** | Low but silent | A5: never invent an id; park or retry |
| Owner hits the name-clash wall with no explanation | Med (blocks the workflow) | **High** (merging categories is the natural way to tidy a menu) | A2 — precise 409 + rename/merge/cancel |
| Category row reverted by a legacy full-record push | Med | Med (pre-mask builds in the field) | Masks already protect items; document that category edits from such builds are LWW |
| New `is_favourite` column cleared by an old client | **High** if unhandled (looks like a random bug) | **High** | B2 null-preserving rule + compatibility test |
| 15-minute propagation for web-admin edits misread as "not saved" | Med (trust) | High | A3 push nudge |
| Room `ON DELETE CASCADE` on `menu_items.category_id` | High if ever triggered | Low today (all paths soft-delete) | Guard test + keep hard deletes out of the codebase |
| Local-only category name uniqueness vs no server constraint | Low/Med | Med on concurrent create | Out of scope for A/B; note for a follow-up |
| A non-owner category change on a terminal gets the whole push a 403, which the POS reads as deactivation and logs the session out | **High** (blocks the counter) | Low if the UI gate matches the server gate; **High** if anyone "helpfully" exposes the control | Keep every category mutation (move, star, reorder, rename) OWNER-only in the POS UI; device-local pins only, if cashier pins are ever wanted (§1.4) |
| Star set on one surface, silently cleared by the other surface's category push | **High** (feature looks broken) | **High** unless the null-preserving rule ships with the column | Ship B1+B2 as one release; compatibility test in §4 |

---

## 6. Decisions I need from you

1. **What "favourite" means** — owner-pinned restaurant-level categories (B-owner), or per-cashier shortcuts for the billing screen (B-cashier)? The plan above recommends B-owner with B-cashier later; the answer changes B1–B5 substantially.
2. **Does a favourite change the order, or only the presentation?** i.e. does pinning also rewrite `sort_order` (the field drag-to-reorder already owns)?
3. **Colour/placement:** pinned categories float to the front of the billing strip, or a separate "Favourites" row above it? (Affects `MenuSelectionStep` and the KOT/print grouping review.)
4. **Which surfaces ship in v1?** Both are feasible (§1.4), but the POS half carries the two extra risks (403-invalidates-session if a non-owner can touch categories; G2/G5 move-loss). The web admin is the low-risk half and already performs moves — so "web admin first, POS one release later" is a legitimate answer to *can I do both*.
5. **Within-category ordering (G6):** is name-A→Z acceptable for v1, or does the owner expect "move it here and put it at the top"?
6. **Audit:** should pin/unpin and moves be written to `SecurityAuditService` (the bill-status and paymode changes already are)? I would default to **yes** for moves — they change what the counter can sell.

---

## 7. Honest limitations of this review

- Static analysis only: **no server test suite, no Android build, and no live move were executed** for this document. Behaviour is inferred from code. The three things I would confirm at runtime before writing production changes are: (i) an actual web-admin move observed on a terminal; (ii) a POS-originated move on a device that has never set `serverCategoryId` (nothing on Android writes that column, so every terminal push sends it as `null`); (iii) the duplicate-name move, to confirm the exact response body the menu dialog receives.
- Effort figures are engineering estimates for the work described, not commitments.
- The customer-facing interpretation of "favourite" (B-customer) is out of scope for this plan because **no customer ordering surface exists in this repository**; if the intent is diner-side favourites, that is a separate product decision.
