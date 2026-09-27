# Menu Item & Variant Model — India Fit-Gap Analysis

**Date:** 2026-09-27
**Status:** Analysis complete. P0 recommendations partially implemented (see §7).
**Related:** `../reviews/KHANABOOK_MENU_PRICE_SYNC_DATA_LOSS_2026-09-27.md` — the crash that
prompted this.

---

## 1. The question

A restaurant menu item in India carries far more than name, photo, category, price. This document
answers three things:

1. What does a food menu item actually need, for the Indian market?
2. What do we already have, and what is missing?
3. What do the six competitor APKs in
   `Khanabook V1 -assest/competitor_apks/decompiled/` do differently?

---

## 2. Correction: the parent price should NOT be nullable

An earlier draft of this analysis recommended making the parent price nullable when variants
exist. **The competitor evidence contradicts that.** None of them do it.

| App | Parent price | Behaviour |
|---|---|---|
| Arow | non-null `double` | Variant price **overrides** parent; parent is the fallback — `ViewOnClickListenerC0329c0.java:224-232` |
| Khide | non-null `double` | Portion price **replaces** parent; a null price from the server is coerced to `0.0` — `com\khide\restaurant\components\e8.java:84-88` |

Khide's coercion is worth reading as a warning: it is the same crash we hit, patched at the
boundary. Ours was rejected outright by validation instead.

The pattern all of them share:

```java
item.price         // non-null, always a usable number
lineItem.variantId // nullable, null until the customer picks one
total = (variant != null ? variant.price : item.price) + addons
```

**The nullability belongs on the cart line, not on the item.** Making `basePrice` nullable would
move the problem, not solve it — every read site would need a null branch, and the billing path
would need a policy for "container with no variant chosen."

---

## 3. What an Indian food menu item needs

| Group | Fields | India-specific notes |
|---|---|---|
| Identity | name, description, photo (primary + gallery), display order | |
| Classification | category, cuisine, **veg / non-veg** | FSSAI requires the veg marking; it prints on the bill |
| Tax | tax rate, **inclusive or exclusive**, HSN/SAC | Indian menu MRPs are near-universally **inclusive** of GST |
| Pricing | single price **or** variant prices, offer price, **per-order-mode tiers** | Delivery platforms price differently from dine-in |
| Availability | available, **day part**, time window, order-type availability | "Lunch only", "dine-in only" |
| Stock | on-hand qty, low-stock threshold, or untracked | |
| **Portion** | portion size, weight in grams, servings | Half/full, 250g — very common and frequently missing from POS schemas |
| Operational | **kitchen station / KOT routing**, prep time, **recipe → raw material** | Multi-station kitchens; recipe link drives inventory deduction |
| Delivery | Zomato item ID, Swiggy item ID, delivery-only price | |
| Modifiers | **groups with enforced min/max selection** | "Choose any 2 toppings from 5" |
| Compliance | allergens, FSSAI declaration, nutrition | |

---

## 4. What we have today

`MenuItemEntity` + `ItemVariantEntity`.

| Field | Status |
|---|---|
| Name, description, image, `imageVersion` | Yes — `imageVersion` for sync conflict resolution is better than any competitor |
| Category | Yes |
| **Veg / non-veg** (`foodType`) | **Yes — absent from every competitor with an analyzable model** |
| Stock + `lowStockThreshold`, per variant | Yes — competitors do not track this per variant |
| Barcode | Yes |
| Variants with own price, `sortOrder`, stock | Yes |
| Tax inclusive/exclusive | **Shop-level only** (`RestaurantProfileEntity.isTaxInclusive`) |
| CGST + SGST | Yes |
| **IGST** | **Missing** — inter-state sales |
| Recipe → raw material, with `recipeFactor` scaling | Yes — Khide has this, Arow and BillKaro do not |
| KOT events | Yes, but **no per-item station** |
| **Explicit `hasVariants` / itemType** | **Added** — see §7 |
| Modifier groups, min/max selection | Missing |
| Price tiers per order mode | Missing |
| Day part / time window | Missing |
| Portion / weight / servings | Missing |
| HSN/SAC per item | Missing |
| Offer price, prep time, platform item mapping | Missing |

---

## 5. What the competitors do

### 5.1 Analyzability

| App | Stack | Field-level model readable? |
|---|---|---|
| Arow | Native Java + Firestore | **Yes — richest** |
| Khide | Kotlin + Firestore/Room | **Yes** |
| BillKaro | Native Java + Room | Yes, basic only |
| Petpooja | Flutter (Dart AOT) | **No** — domain is inside `libapp.so` |
| OfflineShop | Flutter (Dart AOT) | **No** |
| EZO | Kotlin | **No** — `in.co.ezo.data.repo.ItemRepo` is imported by every worker but absent from the decompile |
| Loyverse | — | Folder not decompiled |

**This is the most important caveat in the document.** The three closest Indian competitors —
Petpooja, EZO, and to a degree OfflineShop — are exactly the three we could not read. Recovering
them needs a Flutter AOT symbol/metadata dump, not jadx. Do not treat this analysis as covering
the Indian market leader.

### 5.2 The two analyzable models

**Arow** — `models/ItemModel.java:10-35`
```
active, addons, category, cookingNote, description, extra, id, imgUrl, isQrDisabled,
modifierGroups, name, order, price, priceTiers, qty, qtyAtSale, qtyAtSaleNum, station,
status, stock, stockNotifyAt, taxes, trackInventory, type, variant, variants
```
- `variants: Map<String, ItemModelMini>` — name → `{name, price, type}` (`ItemModelMini.java:20-28`)
- `modifierGroups: List<ModifierGroup>` — `{id, name, minSelection, maxSelection, options}` (`ModifierGroup.java:8-12`)
- `station: String` — kitchen station, defaults `"Station I"` (`ViewOnClickListenerC0329c0.java:244`)
- `priceTiers: Map<String,Double>` is **order-mode** pricing, not variants; `getActivePrice()`
  returns the tier price unless the tier is literally `"Standard"` (`ItemModel.java:64-73`)

**Khide** — `models/MenuItem.java:21-36`
```
createdAtMillis, displayOrder, extras, extrasSingleSelect, id, imageSource, imageUrl,
inStock, lowStockThreshold, name, photoAutoTried, portionWiseExtras, portions, price,
quantity, trackQuantity
```
- `MenuPortion`: `{extrasSingleSelect, name, price, recipeFactor}` (`MenuPortion.java:17-35`)
- `MenuExtra`: `{name, price}` (`MenuExtra.java:15-29`)
- `hasChoices()` is **derived** from non-empty children — no stored variant flag (`MenuItem.java:234-236`)

**BillKaro** — `products(id, name, price REAL NOT NULL, gstPercent, category, unit, createdAt)`,
four tables total, **no variants** (`data/local/ZBillDatabase_Impl.java:100-103`)

### 5.3 Selection constraints — a real opportunity

| App | min/max selection | Enforced? |
|---|---|---|
| Arow | `minSelection` + `maxSelection` on `ModifierGroup` | **max only.** `minSelection` is never passed to the option adapter; the UI reads "Select exactly N (Max: M)" while permitting zero — `ModifierGroupSelectionAdapter.java:65-69,86-88,142` |
| Khide | `extrasSingleSelect` / `portionWiseExtras` booleans | single-vs-multiple only; no "choose N" |
| BillKaro | none | — |

**Nobody in the set enforces "choose exactly N."** That is a genuine differentiator available to us.

### 5.4 Tax — only Arow is correct

- **Arow**: per-tax `taxType` of `inclusive`/`exclusive`. Inclusive computes `base = gross / (1 + rate/100)`
  and extracts without adding; exclusive adds on top (`CartActivity.java:680-695`). The report then
  splits CGST/SGST proportionally and writes `taxes.CGST.amount` / `taxes.SGST.amount`
  (`SettingsOrderDetailsActivity.java:586-596,706-709`). Plus a per-outlet `isRoundOffTaxes` toggle.
- **Khide**: shop-level `gstPercent` (String) + `calculate_gst_separately` Boolean, default `false`
  (`FirestoreRepository.kt:388`). `false` → inclusive, and the bill prints
  *"All prices are inclusive of N% GST"* (`BillHtmlBuilderKt.java:248,369`). CGST/SGST split exists
  **only in the GST report**, not in the cart.
- **BillKaro**: per-item `gstPercent`, exclusive only, no inclusive/exclusive flag, no split
  (`BillingViewModel.java:296-299`).

**No IGST anywhere.** Arow and Khide both assume intra-state; `igst` is absent from both packages.
For a platform that syncs across states, that is a real gap in all of them.

### 5.5 Indian field matrix

| Field | Arow | Khide | BillKaro | We |
|---|---|---|---|---|
| KOT station | **Per-item `station`**, KDS mode, `isKotPrinted`, `canVoidKotItems` | Order-level only | — | Events only |
| KDS | `isKdsEnabled()` | — | — | Yes |
| Barcode | — | — | — | **Yes** |
| HSN | — | Shop-level hardcoded `996331` | — | No |
| Veg/non-veg | — | — | — | **Yes** |
| Recipe / BOM | — | `Recipe`/`RecipeIngredient`/`RawMaterial`, `recipeFactor` scales stock | — | **Yes** |
| Service / other charges | — | `service_charges_percent`, `other_charges_percent` | — | No |
| Order-mode price tiers | **`priceTiers`** | — | — | No |
| Modifier groups | `modifierGroups` (min/max) | extras (single/multi) | — | No |
| Portions | variants map | `portions` | — | variants |
| Day part / time window | — | — | — | No |

EZO's worker class names reveal a richer surface than its decompile shows: `AddBarcodeWorker`,
`PullKotsWorker`, `PullEstimatesWorker`, `PullCutOffDaysWorker` — so it models barcode, KOT,
estimates, and cut-off days.

**Veg/non-veg is absent from every app that exposes a readable model.** It is an FSSAI requirement
and appears on the bill. This is a strength we should not lose sight of while closing gaps.

---

## 6. The root cause, restated

`hasVariants` did not exist before this change. Pricing mode was therefore **inferred** from
whether `basePrice` was blank or zero. Every code path had to guess, and a guess is what crashed:

```
ItemEditDialog: parsedPrice ?: 0.0   →  rejected by normalizePrice
```

The fix applied was a derived fallback (cheapest valid variant). That is right, but it is a
**band-aid on an implicit contract**. With an explicit flag, the parent price becomes a *derived
display value* and correctness stops depending on the reader interpreting a magic value.

---

## 7. What was implemented

### P0 — explicit variant mode

`has_variants` added to `menu_items` (Android Room v76→v77, server `V100`).

- **The flag is derived, never caller-supplied.** `MenuRepository.refreshHasVariantsFlag`
  recomputes it from a live variant count and is the single choke point, called from
  `insertVariant` and `deleteVariant`. Adding or removing a variant therefore cannot leave the
  parent claiming a mode it does not have.
- **The write is narrow.** It uses a targeted `UPDATE menu_items SET has_variants = ...`
  (`MenuDao.updateItemHasVariantsFlag`) rather than `updateItem`, because the flag is derived
  data and recomputing it must not roll back an unrelated field the user just edited. The flag
  is only written when it actually changed, so a no-op save stays a no-op.
- **Migrations backfill from reality, not from the default.** Both the Room migration and
  `V100` promote an item to a container if any live variant exists. Only ever promote, never
  demote: a stale `FALSE` on a real container is recoverable, a stale `TRUE` on a simple item
  would render a meaningless price.
- **Sync carries it in both directions**, and a pull from a pre-`V100` server falls back to the
  value already stored locally rather than resetting to `false`.
- `base_price` stays `NOT NULL`. That is deliberate and matches every competitor in §2.

`MenuPricingRules.resolveBasePrice` is retained as the derivation of the parent's display value,
and the separate sync-side price band is still guarded by
`ValidationAndPricingTest.authoring band and synced band are deliberately different`.

The menu grid still renders **"N variants · Starts from ₹X"** from the variant list it already
has loaded. That is intentional: on that screen the list is present and authoritative, so
`variants.isNotEmpty()` is both cheaper and less stale-prone than a stored flag. The flag earns
its keep on the read paths that do *not* load variants — sync, billing, and reporting.

Covered by 4 new tests in `MenuRepositoryTest`, including a counterweight asserting the refresh
never issues a whole-row `updateItem`.

### Not yet implemented

| Item | Status |
|---|---|
| Modifier groups with enforced min/max | Not started — highest functional gap |
| Per-item tax type + IGST | Not started |
| Price tiers per order mode | Not started |
| Day part / time window | Not started |
| Per-item kitchen station | Not started |
| Portion / weight / servings | Not started |
| HSN/SAC per item | Not started |
| Flutter AOT recovery for Petpooja / EZO / OfflineShop | Blocked on tooling |

---

## 8. Recommended order

1. **`hasVariants` / itemType** — done (§7). Prevents a repeat of the documented crash.
2. **Modifier groups with enforced `min`/`max`** — largest functional gap, and the one place
   competitors are demonstrably weak.
3. **Per-item tax type + IGST** — correctness, not features. Arow's arithmetic is the reference.
4. **Price tiers per order mode**, then Zomato/Swiggy item mapping.
5. **Day part + per-item kitchen station.**

Each of these touches the sync DTO surface, so the `SyncDtoEntityFieldParityTest` guard from the
incident review applies to every one of them: add the entity field, the DTO field, and let the test
confirm they match.

---

## 9. Method notes

- Findings are from decompiled sources. Obfuscated class names are flagged where they appear.
- Claims marked NOT FOUND were searched for and absent; they are not inferences.
- `station` and `priceTiers` are single-source (Arow), and only the call sites were read, not the
  full flows.
- Field-presence counts for KhanaBook were taken across all 271 `.kt` files under
  `Android/app/src/main/java`. An earlier pass used a non-recursive glob and reported a false zero
  for every field — a reminder to verify negative results.
