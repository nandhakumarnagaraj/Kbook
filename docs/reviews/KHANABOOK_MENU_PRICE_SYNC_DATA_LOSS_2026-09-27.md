# KhanaBook — Menu Price Crash & Silent Sync Data Loss

**Date:** 2026-09-27
**Type:** Incident review + root cause analysis
**Trigger:** Production report — menu item with variants could not be saved
**Status:** Fixed and verified. Some follow-ups deliberately deferred.
**Baseline:** `92ecdba3` on `main`

---

## 1. Executive summary

A cashier could not save a menu item that had variants but no base price. The dialog crashed.
The same crash blocked stock movements on *every* item, not just ones with variants.

Chasing that one crash uncovered a much larger problem: **the sync push path silently discards
any entity field that its DTO does not declare**, with no error, no log, and no failed request.
The HTTP call returns 200. The field is written as `null`. The symptom appears days later as a
value that mysteriously reverted.

Confirmed instances of that class in this codebase, all of them live:

| Field | Consequence of being dropped |
|---|---|
| `StockLog.delta` | Stock-log push **could never persist a row**. The whole stock ledger was dead. |
| `Bill.inventoryDeducted` | Reset to `false` → **stock deducted a second time** for the same bill. |
| `Bill.gatewayTxnId` / `gatewayStatus` | Nulled on gateway-following pushes → reconciliation loses its key. |
| `RestaurantProfile` marketplace fields (10) | Zomato/Swiggy credentials and outlet IDs **wiped** by a legacy push. |
| `BillItem.specialInstruction` | Kitchen special instructions **erased** on every line update. |
| `MenuItem.currentStock` / `foodType` / `barcode` / `lowStockThreshold` | Stock and dietary flags reset on every stock push. |
| `Category.isVeg` | Reset to `false` on every update. |
| `ItemVariant.sortOrder` | Dropped, so variant display order was lost. |

Twenty defects total across Android and server. All fixed except two, both deferred with reasons
in §9.

---

## 2. The reported problem, precisely

### 2.1 Symptom

Opening a menu item that has variants but a blank base price and tapping Save produced a crash.
The same item could not be added, edited, or saved via duplicate-name overwrite.

### 2.2 Root cause

`ItemEditDialog` seeded its price field and submitted through one path that converted a blank
price to a hard zero:

```kotlin
// ItemEditDialog.kt
val price = parsedPrice ?: 0.0
```

`0.0` was then rejected by `MenuPricingRules.normalizePrice`, which enforces `Rs. 1 – Rs. 1,00,000`.
The dialog had no valid fallback, so every variant-backed item with a blank base was unsaveable.

The design flaw: **a variant item legitimately has no typed base price** — the variants carry the
real prices — yet the parent row still needs a valid `base_price`, because the menu grid displays
it and billing falls back to it when a variant is not selected. The code never resolved that
conflict.

### 2.3 Why it presented as three separate bugs

Add, edit, and duplicate-overwrite all funnel through `ItemEditDialog`, so one defect surfaced as
three symptoms. Fixing the dialog fixed all three at once — worth noting because "three bugs" in a
report usually means "one root cause, three call sites", not three investigations.

---

## 3. The blast radius: price re-validation blocking unrelated writes

The dialog crash was the visible symptom. The more expensive problem was underneath it.

### 3.1 Root cause

`MenuRepository.updateItem` normalized `basePrice` unconditionally on every update:

```kotlin
if (normalizePrice) {
    item.basePrice = MenuPricingRules.normalizePrice(item.basePrice)  // always ran
}
```

`updateStock` called it to change **one integer column**. The item's price was never in the
statement, but it was re-validated anyway. So any item whose stored price was out of band became
**permanently immovable** — you could not restock it, could not toggle its availability, could not
do anything that wrote to the row.

`updateVariantStock` was worse: `ItemVariant` has no changed-fields mask at all, so the
read-modify-write had no way to signal "I only touched stock."

### 3.2 Fix

Mirror the server's own field-mask protocol. Resolve the change set first, then only normalize
what the caller actually declared:

```kotlin
// MenuRepository.kt
val fields = changedFields ?: computeChangedFields(item)
if (normalizePrice && fields.changesField("basePrice")) {
    item.basePrice = MenuPricingRules.normalizePrice(item.basePrice)
}
```

`updateVariantStock` passes `normalizePrice = false`, since variants carry no mask and the flag is
the only signal available.

**Prevention rule:** a partial update must never re-validate a field it did not write. If the write
statement does not mention a column, no validator should read it.

---

## 4. Duplicate orders from a post-commit failure

### 4.1 Symptom

A bill saved successfully, the UI said **"Failed to save bill"**, the cart did not clear, the
cashier tapped Save again — and a duplicate order was created.

### 4.2 Root cause

`BillRepository.insertFullBill` committed the bill, then did more work:

```kotlin
val billId = billDao.insertFullBill(bill, items, payments)   // COMMITTED
recordKotEvent(...)                                         // can throw
inventoryConsumptionManager?.consumeMaterialsForBill(items) // can throw
triggerBackgroundSync()                                     // can throw
return billId
```

Every statement after the commit could throw. `consumeMaterialsForBill` iterates items
individually and does database writes, so a stock or recipe problem mid-way was enough. The
exception propagated to `BillingViewModel`, which sets "Failed to save bill" and returns `false`.

The user-visible framing was the lie: the bill **was** saved. The message described the opposite.

### 4.3 Fix

Rule applied to everything after the commit — it may not surface as a failed save:

```kotlin
val billId = billDao.insertFullBill(bill, items, payments)
// ...each post-commit step individually guarded, then:
return billId
```

A secondary defect was found during review: `catch (e: Exception)` also caught
`CancellationException`, because it is a typealias for `java.util.concurrent.CancellationException`
which extends `IllegalStateException` → `Exception`. Swallowing it broke structured concurrency
and would have defeated `withTimeout`. Each guard now rethrows cancellation explicitly:

```kotlin
catch (e: CancellationException) { throw e }
catch (e: Exception) { Log.e(...) }
```

**Prevention rule:** once a commit returns, the record exists. The UI must never be told the save
failed. Post-commit side effects are logged and reconciled, never propagated. Re-throw
`CancellationException` before any broad catch in a `suspend` function.

### 4.4 Known remaining limitation

If `consumeMaterialsForBill` throws on item 5 of 10, items 1–4 are already deducted and stay that
way. There is no retry or reconciliation job — only a log line. The comment in the code claims
"stock drift is recoverable", which is aspirational, not true. See §9.

---

## 5. The systemic finding: silent DTO/entity drift

This is the part worth remembering.

### 5.1 The mechanism

`SyncMapper` maps every push with the two-argument form:

```java
BeanUtils.copyProperties(dto, entity)   // no ignore list
```

It matches by property name and **skips anything it cannot match, with no error**. Compounding it,
Spring Boot disables Jackson's `FAIL_ON_UNKNOWN_PROPERTIES` by default (confirmed for this project
by decompiling `Jackson2ObjectMapperBuilder.customizeDefaultFeatures` — the disable is Spring's,
not Jackson's, and `JacksonProperties` has only empty maps so nothing overrides it).

So a field missing from a DTO is discarded at the HTTP boundary, then discarded again at the
mapping boundary. The request succeeds. Two silent skips, no signal.

Verified behaviour of `BeanUtils` in this dependency set:

| Direction | Behaviour |
|---|---|
| Source property, no setter on target | silently skipped |
| Target property, absent from source | left untouched, then overwritten by field initializer on save |
| Type mismatch (`String` ↔ `LocalDate`) | silently skipped |
| Source property present but `null` | **does** overwrite target with `null` — a distinct failure mode |

### 5.2 Why `MenuItemDTO` slipped through

`preserveServerOwnedState` had a thoughtful inventory-cascade branch for `MenuItem` that restored
`isAvailable` and image fields from the existing row. It simply never mentioned the four missing
fields. The protection was written for the fields the author happened to think of, not derived from
the schema.

The merge protocol itself is also narrower than it looks. `applyChangedFieldsMerge` begins:

```java
if (!(incoming instanceof MenuItem && existing instanceof MenuItem)) {
    return;   // 8 of 9 DTO types skip field-level protection entirely
}
```

So `MenuItem` was the only type with any field-mask protection at all.

### 5.3 The fix, and why a test was the right shape

Three groups, each handled differently:

**Client-owned → add the field to the DTO.** Verified against the Android wire format first, since
the DTO must match what the client actually sends:
`CategoryDTO.isVeg`, `ItemVariantDTO.sortOrder`, `BillItemDTO.specialInstruction` — all three were
being sent by the app and dropped by the server.

**Server-owned → restore unconditionally in `preserveServerOwnedState`.** A device must never set
these, and they are not on the DTO, so they must come from the existing row:

- `Bill.inventoryDeducted` — `InventoryService.deductForFinalizedBill` early-returns only when
  `TRUE`. A push resetting it to `false` deducts stock again.
- `Bill.gatewayTxnId`, `gatewayStatus`, `refundId`, `settledAmount`, `settledAt`,
  `commissionAmount`
- 10 `RestaurantProfile` marketplace fields

**The unconditional part matters.** The pre-existing guard read:

```java
if ("paid".equalsIgnoreCase(existing.getPaymentStatus())
        && !"paid".equalsIgnoreCase(incoming.getPaymentStatus())) {
    incomingBill.setGatewayTxnId(existing.getGatewayTxnId());
    incomingBill.setGatewayStatus(existing.getGatewayStatus());
}
```

A device that *correctly* reports the bill as paid satisfies neither half, so the fields were
nulled on exactly the pushes most likely to follow a gateway payment. A conditional guard on
server-owned state fails open.

**`StockLogDTO.changeAmount` → `delta`.** The DTO called it `changeAmount`; the client sends
`delta`; the column is `delta NOT NULL`. Nothing bound, so every stock-log push hit a constraint
violation, `saveAll` threw, the per-record fallback also failed, and `recalculateStock` — which
drives `MenuItem.currentStock` and `ItemVariant.currentStock` — never ran. This is the most severe
instance because it is the root of stock tracking, and it fails loudly rather than silently.

### 5.4 The guard

`SyncDtoEntityFieldParityTest` reflects over all nine DTO/entity pairs and fails the build when:

- a client-owned entity field is missing from its DTO
- a field exists on both but the types differ
- a DTO declares a property that is neither an entity field nor explicitly allow-listed

Two allow-lists carry the judgement calls: `SERVER_OWNED` (entity fields the server owns, each with
a comment on why) and `KNOWN_UNMAPPED_DTO_FIELDS` (DTO properties with no column, kept explicit so
adding one is deliberate).

On first run it reported **10 real drifts**. It caught everything in this document, including the
original `MenuItemDTO` bug. That is the point: this class of bug is invisible at runtime and
structurally invisible at compile time, so it needs a mechanical check.

**Prevention rule:** any DTO paired with an entity by `BeanUtils` needs a parity test. Do not rely on
review to catch a missing field — a missing field is not a syntax error, and nothing in the request
fails.

---

## 6. R&D trail

Recorded because the path matters as much as the destination, including the parts that went wrong.

### 6.1 Assumptions that were wrong

**"The server enforces the same price range we do."** False. The DB constraint is
`chk_menuitems_price CHECK (base_price >= 0)` on `NUMERIC(12,2)` — a **floor and a column width, no
ceiling**. The Rs. 1,00,000 cap is a client authoring convention, not a server invariant.

This produced a regression I introduced and then caught. The first sync-pull fix reused the
*authoring* band to filter *server* data:

```kotlin
// First attempt — wrong
fun isValidPriceText(value: String?): Boolean {
    val normalized = amount.setScale(2, RoundingMode.HALF_UP)
    return normalized >= MIN_PRICE && normalized <= MAX_PRICE   // 1 .. 100000
}
```

The server's own AI import writes `BigDecimal.ZERO` for items where the model omits a price, and a
Rs. 2,50,000 banquet package is perfectly legal. This filter would have **silently and permanently
hidden both**, converting a visible wrong price into a missing menu item — strictly worse than the
bug it fixed. A test named `authoring band and synced band are deliberately different` now exists
specifically to stop the two being merged again.

**"Two of the five audit leads were solid."** Two were not:

- *GST `999` quarantine trap* — **refuted**. No `999` GSTIN exists anywhere in the repo, and the
  server has **zero** GSTIN validation. The real issue is the inverse of the claim: the server
  accepts malformed GSTINs that the Android client happens to block.
- *"Max price is not enforced for variants"* — **refuted**. `ItemVariantServiceImpl:84` does
  validate. The gap was real but on five *other* paths.

A third lead was correct in substance but named the wrong tables (`menu_items`/`item_variants`; the
actual names are `menuitems`/`itemvariants`).

The lesson: unverified audit claims were the least reliable input in this investigation. Every
fix here was confirmed by reading code or by a test that fails without the fix.

### 6.2 Test design as evidence

A regression test that passes against both the bug and the fix is decoration. Each new test was run
with its fix reverted:

| Test | Without fix |
|---|---|
| `GenericSyncMenuFieldMaskTest` (menu fields) | fails: `expected "nonveg" but was null` — the exact production symptom |
| `updateStock survives an out-of-band stored price` | fails: `IllegalArgumentException` |
| `updateVariantStock survives an out-of-band stored price` | fails: `IllegalArgumentException` |
| `ServerOwnedFieldPreservationTest` | 2 failures + 1 error |
| `updateItem still rejects an invalid price when basePrice changed` | passes — it is a **counterweight** |

That last one is deliberate. "Skip normalization for untouched fields" is one careless edit away
from "skip validation for the edited field," and nothing in the suite would have noticed. A fix
needs a test pinning what must *still* fail.

The first version of `GenericSyncMenuFieldMaskTest` was also wrong: it used a strict `ObjectMapper`
and failed with `UnrecognizedPropertyException` rather than reproducing the null-out. The test had
to mirror production — Spring's lenient config — to reproduce the real failure.

### 6.3 Caller-survival vs. validator-throws

The pre-existing suite could only assert "the validator throws." It could never assert "the caller
survives," which is exactly the blind spot that hid this whole class: the validator was working
correctly the entire time, and the defect was in who was calling it.

The new tests are named for the survival property, not the validation property.

### 6.4 Contract drift found on both sides

`SyncPayloadValidator` accepts `part_payment_cash`, `part_payment_card`, and `part_payment_upi`,
which the DB `CHECK` constraint rejects, and the Android app can send `easebuzz` and `payment_link`,
which the validator rejects. A contract test asserting *app-sendable ⊆ server-accepted* therefore
fails on arrival.

Not written, because fixing it means editing the payment-mode list — and `easebuzz` is in active
development. Flagged rather than silently resolved.

---

## 7. Other defects fixed in this pass

| Defect | Detail |
|---|---|
| **Paise lost on no-op save** | `ItemEditDialog` seeded the field with `initialPrice.toInt()`, so a stored `120.99` displayed as `120`; opening the item and tapping Save wrote `120.00` back. Now formats via 2dp `BigDecimal`. Worst case: silent money loss on the most common user action. |
| **Rounding window contradicts the floor** | `isValidPriceText` rounded to 2dp `HALF_UP` before range-checking, so `0.995`–`0.9999` were pulled *into* the band and rewritten to `1.00` — the opposite of what the doc comment claimed it prevented. |
| **Max price missing on update** | `updateExistingMenuItems` ran no validation at all, making `PUT /sync/menuitem/update-existing` a bypass. |
| **Max price missing on web admin** | `validateMenuItemFields` checked only `price <= 0`; both create and update were unbounded. |
| **QuickStart non-atomic** | 2+N writes with no transaction. A failure on item *k* left the shop name and category committed while `quickStartCompleted` stayed false; the retry re-inserted committed items as duplicates. Now one `withTransaction`, using the pattern already in `MenuViewModel`. |
| **`connectedAndroidTest` never ran** | 30 instrumented test files — menu config, part payment, KDS, migrations — had no job in any workflow. Added an emulator job. |

The `continue-on-error: true` on the new emulator job is deliberate and temporary. The suite has
never executed, so a blocking job is near-certainly red on first run and would block unrelated PRs.
It reports the true state without gating. Remove that line once green — the workflow file says so.

---

## 8. Verification

| Suite | Result |
|---|---|
| Android unit tests | **344 passing**, 0 failures, 52 suites (was 339) |
| Android `compileDebugKotlin` | clean |
| Server | 583 tests, **3 failures — all pre-existing**, 0 new |
| New server tests | 3 files, red-without-fix confirmed |

### The 3 pre-existing failures

`EasebuzzWebhookTest.testHandleFssaiRenewalWebhookSuccess`,
`EasebuzzWebhookServiceTest.fssaiRenewalSuccess_updatesRenewalAndTracker`,
`EasebuzzWebhookServiceTest.amountMismatch_doesNotMarkBillPaid`.

Confirmed unrelated by stashing the `MenuItemDTO` change and re-running: same 3 failures. They sit
in the Easebuzz area that is under active development, and were left alone by instruction.

### A stale-build trap

`mvn test` on this server intermittently fails test *discovery* with an unresolvable class name
against a source file that exists. `.\mvnw.cmd clean test` resolves it. A `target/test-classes`
stale from a previous layout is the likely cause. Worth knowing before spending time on a
phantom bug.

### Not yet covered

The full server suite was **not re-run to completion** after the final two edits (max-price
enforcement in `MenuItemServiceImpl` and `BusinessWriteService`); the run was interrupted. The
targeted run covering both files passed 27/27, but "full suite green" should not be claimed for that
exact tree state without re-running it.

Nothing in this work has been verified on a physical device. Every claim is from code reading and
JVM tests.

---

## 9. Open items

| Item | Why deferred |
|---|---|
| **Unique constraints on `menuitems` / `itemvariants` / `categories`** | `bills`, `bill_items`, `bill_payments` all carry `UNIQUE (restaurant_id, device_id, local_id)`; the menu master-data tables only have non-unique indexes. A check-then-insert at `GenericSyncService:357` → `saveAll:928` with no `ON CONFLICT` means an in-flight retry can insert permanent duplicates, and duplicate categories double-print on every bill. **Not written:** if those tables already contain duplicates the migration fails at deploy and takes the app down. Needs a dedupe step first, and a decision on which row survives. |
| **`part_payment_*` dead enum values** | Accepted by `SyncPayloadValidator`, rejected by the DB `CHECK`. Harmless today because no client sends them, but they are a trap. |
| **Client/server contract test** | Blocked on the `easebuzz` / `payment_link` decision (§6.4). |
| **AI/OCR import price cap** | `AiMenuExtractionService:324` writes `BigDecimal.ZERO` when the model omits a price; `:343` writes variant prices unvalidated. Overlaps services under active development. |
| **Partial inventory deduction repair** | §4.4. Items deducted before a mid-loop failure are never reconciled. |
| **Commit split** | 23 files across Android, server, and CI, mixed with 8 pre-existing unrelated UI changes. Splitting is a judgement call, not a mechanical one. |

---

## 10. Prevention rules

Durable, and the point of writing this down.

1. **A DTO paired with an entity by `BeanUtils` needs a parity test.** A missing field is not a
   compile error and produces no runtime error.
2. **Never reuse an authoring/UI validation band to filter server data.** The server's invariants
   are the DB constraints. Read them: `V1__init_schema.sql` is the source of truth, not the app.
3. **Server-owned state must be restored unconditionally, not under a status condition.** A
   conditional guard fails open exactly when a client behaves correctly.
4. **Nothing after a commit may surface as a failed save.** Post-commit work is logged and
   reconciled. The record exists; the message must say so.
5. **In a `suspend` function, rethrow `CancellationException` before any broad catch.**
6. **A partial update must not re-validate fields it did not write.** Resolve the change set first.
7. **Test the caller's survival, not just the validator's throw.** The validator was never broken.
8. **Prove a regression test fails without its fix.** A test that passes both ways is decoration.
9. **Format money with `BigDecimal`, never `toInt()`.** Truncation on a no-op save is invisible.
10. **A green test suite is not evidence for a claim you did not test.** Two of five audit leads
    were wrong, and the wrongness was only caught by reading the code.

---

## 11. File index

### Android — main
| File | Change |
|---|---|
| `feature/menu/domain/MenuPricingRules.kt` | `resolveBasePrice`; `isSyncedPriceText` split from the authoring band |
| `feature/menu/data/MenuRepository.kt` | change-mask-aware normalization; `changesField` |
| `feature/menu/ui/ItemEditDialog.kt` | variant price fallback; 2dp price formatting |
| `feature/billing/data/BillRepository.kt` | post-commit guards; cancellation rethrow |
| `feature/sync/domain/MasterSyncProcessor.kt` | pull-side price filter |
| `feature/settings/viewmodel/QuickStartViewModel.kt` | transactional seeding |

### Android — test
| File | Change |
|---|---|
| `domain/util/ValidationAndPricingTest.kt` | pricing + band-separation coverage |
| `data/repository/MenuRepositoryTest.kt` | caller-survival + counterweight |

### Server — main
| File | Change |
|---|---|
| `feature/menu/data/MenuItemDTO.java` | `foodType`, `barcode`, `currentStock`, `lowStockThreshold` |
| `feature/menu/data/CategoryDTO.java` | `isVeg` |
| `feature/menu/data/ItemVariantDTO.java` | `sortOrder`; documented `stock` mapping |
| `feature/billing/data/BillItemDTO.java` | `specialInstruction` |
| `feature/inventory/data/StockLogDTO.java` | `changeAmount` → `delta` |
| `feature/sync/service/GenericSyncService.java` | unconditional server-owned restoration |
| `feature/menu/service/MenuItemServiceImpl.java` | cap on the update path |
| `feature/business/service/BusinessWriteService.java` | cap on web-admin create/update |

### Server — test
| File | Change |
|---|---|
| `feature/sync/service/SyncDtoEntityFieldParityTest.java` | **the guard** |
| `feature/sync/service/ServerOwnedFieldPreservationTest.java` | inventory + settlement + marketplace |
| `feature/sync/service/GenericSyncMenuFieldMaskTest.java` | menu field round-trip |

### CI
| File | Change |
|---|---|
| `.github/workflows/android-ci.yml` | emulator job for `connectedAndroidTest` |

---

## 12. Related

- `docs/planning/ROOT_CAUSE_LOG.md` — existing root-cause log; this incident's prevention rules are
  candidates for that file's long-term list.
- `docs/reviews/KHANABOOK_PRODUCTION_RELIABILITY_AUDIT_2026-08-17.md` — prior reliability audit.
