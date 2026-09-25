# Menu Configuration UX Spec (Competitor-driven)

Comparison of how competitors (KHIDE, AROW, BillKaro, EZO) handle menu configuration UX against KhanaBook, and the concrete UX improvements to pull in. Grounded in decompiled app analysis + on-device verification, not marketing sites.

## Competitor landscape (relevant apps only)

| | KHIDE (reference) | AROW | BillKaro |
|---|---|---|---|
| Entry point | Dedicated Menu screen from Home | Dedicated "Menu" screen from Home; POS tab drag | Products via Dashboard card + FAB |
| Layout | Categories + items, Manage Categories modal | Two-pane master/detail: category list (name + "N Items" badge) + item list | Flat product list (no visible structure) |
| Category reorder | Drag + explicit Save ("No Changes to Save" ⇄ "Save Category Order (N menus)") | Live drag, no save (immediate persist) | None |
| Category delete | Pending-orders gated; warns item counts | Warns "Items belonging exclusively to this category will also be deleted"; ghost-item cleanup | N/A (free-text string, no entity) |
| Item availability | — | ⋮ toggle "Mark Stock Out"/"Mark Available"; inactive dimmed 50% | None (hard delete only) |
| Category hide/show | — | — | — |

PetPooja + Offline Shop = Flutter shells (Dart logic not in decompile). EZO = sync layer only; UI not analyzable.

## KhanaBook baseline (already at parity — no work needed)

Verified in `Android/app/src/main/java/com/khanabook/lite/pos/feature/menu/ui/`:

- **Item availability toggle + dimmed card** — `ManualMenuView.kt:583` dims unavailable cards (alpha 0.5), edit dialog switch at `:629`, `Available`/`Unavailable` pill at `:713`. Matches AROW's dimmed-inactive pattern.
- **Delete-category warning with item counts** — `ManualMenuView.kt:436-457`: `Delete "X" and all N item(s) inside it? This cannot be undone.`
- **Manage Categories dialog** — drag + explicit Save, item-count badges, inline add, Cancel gating (verified on-device 1.0.30).
- **Category rename/delete** — chip ⋮ menu (`ManualMenuView.kt:176-208`): Edit / Delete / Manage Categories. (No rename cascade needed — items reference `categoryId`, not a string.)

## Recommended improvements

### P1-1. Home "Menu" shortcut tile — REJECTED

- **Decision (product)**: do NOT add a Menu tile to Home. Menu entry stays under Settings → Menu Configuration; no home-screen menu shortcut exists, and nothing menu-related is deletable/removable from Home. (Competitor Home-entry pattern intentionally not adopted.)

### P1-2b. Category hide/show in the chip ⋮ menu — SHIPPED (1.0.30 debug, installed)

- Verified on-device: chip ⋮ dropdown now lists `Edit Category / Hide from billing / Delete Category / Manage Categories`.
- Behavior: hiding a category marks it `is_active = 0` (existing `toggleCategory` path), dims the config chip to 50% alpha, and excludes the category (and its items) from the billing category chips — billing screens now read `activeCategories` instead of `categories`.
- Files changed: `MenuViewModel.kt` (+`activeCategories`), `ManualMenuView.kt` (dropdown item + chip dimming), `MenuConfigurationScreen.kt` (wiring), `billing/ui/MenuSelectionStep.kt` + `billing/ui/ActiveOrderComponents.kt` (use active categories + reselect guard).
- On-device verification of the toggle flip + billing exclusion is pending — USB link dropped mid-test; re-run when the Moto is reconnected.

### P2-1. Delete gating on pending/unpaid orders (safety)

- **Problem**: KHIDE blocks destructive menu edits while orders are pending ("N pending orders"). KhanaBook's delete warning counts items but ignores open orders — deleting a category mid-billing orphans live tickets.
- **Spec**: Before the delete confirm, check active (non-completed) orders referencing items in the category. If any, show `Delete "X" and all N item(s)? M pending order(s) use these items — orders won't be affected, but items leave billing.` and keep the destructive path (soft-delete, orders snapshot prices already) but make it explicit. If none, keep current message.
- **Acceptance**: With an open order containing an item from the category, delete shows the pending-order variant; without, current message unchanged.
- **Touches**: `ManualMenuView.kt:436` (message branch), MenuViewModel/repository (pending-order count query). Unblocks if query/history model is heavy — defer count, only add copy "Orders using these items remain valid."
- **Risk**: medium (needs a reliable open-order query); low-risk fallback copy-only version.

### P2-2. Conflict-recovery awareness (reorder persistence)

- **Problem**: When server 409 conflict recovery runs, local category order is silently rewritten to server state — a merchant's drag-reorder disappears after restart with no signal. This is the one behavior competitors don't have (last-write-wins instead) and the one they'd curse.
- **Spec**: When a pull-with-full-reset (recovery) changes local categories' `sort_order`, show a one-time banner/snackbar on Menu Configuration: "Category order was synced from another device." Surface only on recovery, never on normal sync.
- **Acceptance**: Reproduce recovery pull → banner appears; normal pull → no banner. Non-blocking, auto-dismiss.
- **Touches**: sync pull callback → menu screen event. Needs a signal from the sync layer (small; ownership shared with sync work).
- **Risk**: low-medium (requires a hook between sync engine and menu screen).

### P3-1. Item drag-reorder (data-model gap — deferred)

- **Problem**: KHIDE (`ReorderItemsDialog`) and AROW (per-item `order`) support item reorder; KhanaBook items have no order column — the only structural gap left.
- **Spec**: Add `sort_order` to `menu_items`, DAO order-by + reorder write mirroring `CategoryRepository.reorderCategories`, item drag in Manual Entry / Manage dialog. Sync must carry the new field (server schema change → higher risk, ties into P2-2 conflict handling).
- **Acceptance**: None this iteration. Explicitly deferred.

## Not adopting (with reason)

- **AROW live-drag-no-save**: too error-prone; the explicit Save footer (already built) is better UX.
- **BillKaro free-text categories**: creates phantom categories; KhanaBook's real entity + dropdown is correct.
- **Two-pane master/detail**: chips + horizontal pager is touch-first and faster for billing; revisit only if tables/desktop form factor demands it.