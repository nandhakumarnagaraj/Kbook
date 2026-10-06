# Android UI Improvement — Root Cause & Evidence Collection

> Status: **Investigation complete (2026-10-05). Fixes for Issues 1, 2a, 2b and the Issue 5 dropdown symptom are APPLIED in the working tree (uncommitted); the remaining latent defects are listed per issue.**
> Collected from the codebase on 2026-10-05. All references are `file:line`.

---

## Issue 1 — Space between last menu items and "Add New Item" (Menu Configuration → Manual Entry)

### Current layout evidence

`Android/app/src/main/java/com/khanabook/lite/pos/feature/menu/ui/ManualMenuView.kt`

- Items list is a `LazyColumn` with `weight(1f)` (line 310-317):
  ```kotlin
  LazyColumn(
      modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = spacing.medium),
      verticalArrangement = Arrangement.spacedBy(spacing.small),
      contentPadding = PaddingValues(top = spacing.small, bottom = spacing.bottomListPadding) // line 316
  )
  ```
- Fixed footer `Surface` with the "Add New Item" button sits directly below the list (line 372-410). Footer internal padding: `padding(horizontal = spacing.medium, vertical = spacing.smallMedium)` (line 378-380) plus a `Spacer(modifier = Modifier.height(spacing.small))` (line 384) above the button.
- `bottomListPadding` is a hard-coded **88.dp** token: `Android/app/src/main/java/com/khanabook/lite/pos/core/theme/Spacing.kt:22` (`val bottomListPadding: Dp = 88.dp`, scaled per type tier at line 105).

### Root cause

1. `bottomListPadding` (88dp) is a **FAB-era legacy constant** — it exists to keep the last list item clear of a *floating* action button. It is reused in `ActiveOrdersScreen.kt:144`, `SearchScreen.kt:515`, `CallCustomerScreen.kt:363`, and `MenuSelectionStep.kt:468`. In Manual Entry the footer is a **fixed, in-layout bar** (not floating), so the 88dp token is the wrong mechanism and produces an inconsistent gap (≈ 88 + 12 + 8 = **108dp** between the last item row and the button when scrolled to the end).
2. The gap is a magic constant, not derived from the footer height — so it cannot adapt to the actual footer, and phones/tablets only scale it via the type-tier multiplier (`GapScaleByTier`, Spacing.kt:46-51) rather than measuring the footer.
3. The footer itself carries no `navigationBarsPadding()`; it relies entirely on the host Scaffold's `contentWindowInsets = WindowInsets.systemBars` (`MenuConfigurationScreen.kt:184`), so bottom inset handling is implicit rather than owned by the footer.
4. Git history: the padding was introduced in `99c320d8` ("vertical-slice restructure") and untouched since; `92ecdba3` restyled the footer (removed the hint text) but did not touch the spacing logic — i.e. the spacing has never been designed for a fixed footer.

### Fix — APPLIED (working tree, uncommitted)

`ManualMenuView.kt:316`: `contentPadding` bottom changed `spacing.bottomListPadding` → **`spacing.extraLarge`** (32dp, `core/theme/Spacing.kt`). The footer is a fixed in-layout bar, so the FAB-era 88dp token was the wrong constant; `extraLarge` is the shared token for "space above an in-layout bottom bar".

Not applied (still open): deriving the inset from the measured footer height, and adding `navigationBarsPadding()` to the footer `Surface` (the host Scaffold's `contentWindowInsets = WindowInsets.systemBars` still owns the inset).

---

## Issue 2 — Same space in New Bill menu selections (last items → screen end) + shop logo handling

### 2a. New Bill menu-selection bottom spacing

`Android/app/src/main/java/com/khanabook/lite/pos/feature/billing/ui/MenuSelectionStep.kt`

- Grid: `LazyVerticalGrid(columns = GridCells.Fixed(paneColumns), modifier = Modifier.fillMaxSize().padding(spacing.medium), ...)` (line 320-325) — 16dp inset on **all four sides**.
- Trailing full-span spacer item (line 467-469):
  ```kotlin
  item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(paneColumns) }) {
      Spacer(modifier = Modifier.height(if (isWideScreen) spacing.medium else spacing.bottomListPadding))
  }
  ```
  → phones: **88dp**, wide screens/tablets: only **16dp**.
- The bottom "cart card" (phones only, line 474-588) is **in-layout** (the grid has `weight(1f)` inside the parent `Column`, line 157) with `padding(horizontal = spacing.medium, vertical = spacing.small)` (line 478-479). It is *labelled* "Floating" in a comment (line 475) but is not actually floating/overlapping.

### Root cause

1. Effective gap last-item → cart-card/screen-end on phones ≈ 88 + 16 + 8 = **112dp**; on wide screens only ≈ 16 + 16 + 8 = **40dp**. Different mechanism than Issue 1 (`trailing spacer item` vs `contentPadding`), so the two screens can never be consistent — this is the "same space" ask.
2. The 88dp constant is again the FAB-era token (see Issue 1). Because the cart card is in-layout (not overlaying), the 88dp is oversized; because the wide-screen branch uses `spacing.medium`, tablets get a different rhythm than phones.
3. `KhanaBookTheme.spacing.bottomListPadding` scales with type tier (Spacing.kt:105), so the gap also varies by device class (CompactPhone 0.90× → 79.2dp, Tablet 1.15× → 101.2dp).

### Fix — APPLIED (working tree, uncommitted)

`MenuSelectionStep.kt`: grid `padding(spacing.medium)` → start/end/top only (no bottom double-padding); trailing full-span `Spacer` now unconditionally `spacing.extraLarge` (removes the 88dp phone / 16dp wide-screen split); phone cart `LazyColumn` `contentPadding` bottom → `spacing.extraLarge`. Both this screen and Manual Entry now use the same `extraLarge` token above their in-layout bottom bars.

Not applied (still open): the shared footer-height-derived helper proposed below. `spacing.bottomListPadding` (88dp) is still used by `ActiveOrdersScreen.kt:144`, `SearchScreen.kt:515`, `CallCustomerScreen.kt:363` — those have real FAB-over-list layouts, so the token remains correct there.

### 2b. Shop logo "sometimes works, sometimes not, and also loading"

Two different loaders exist in the codebase:

**Loader A — `ShopLogoLoader`** (`Android/app/src/main/java/com/khanabook/lite/pos/core/util/ShopLogoLoader.kt:20-55`):
tries remote `logoUrl` via Coil (memory+disk cache) → local `logoPath` via `AppAssetStore.resolveAssetPath` → app default `R.drawable.khanabook_logo`.
Used by: UPI test-QR (`feature/payments/ui/PaymentConfigSection.kt:143`), payment/confirmation sections (`feature/billing/ui/PaymentSection.kt:121`, `OrderConfirmationSection.kt:520`), invoice text/PDF (`feature/billing/domain/InvoiceFormatter.kt:55`, `feature/printing/domain/InvoicePDFGenerator.kt:80`), report export (`feature/reports/domain/ReportExporter.kt:93`).

**Loader B — inline `AsyncImage`** in the settings Shop Profile screen (`Android/app/src/main/java/com/khanabook/lite/pos/feature/settings/ui/ShopConfigSection.kt:299-339`):
```kotlin
val logoModel = pendingLogoUri?.toString()
    ?: logoUrl?.takeIf { it.isNotBlank() }
    ?: AppAssetStore.resolveAssetPath(logoPath)          // line 307-309
...
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(logoModel)
        .crossfade(true)
        .memoryCacheKey("$logoModel:$logoUpdateTrigger") // line 317
        .diskCachePolicy(CachePolicy.ENABLED)             // line 318 — NO diskCacheKey
        .build(),
    onLoading = { isLogoLoading = true },
    onSuccess = { isLogoLoading = false },
    onError = { isLogoLoading = false }                   // line 324 — nothing shown on failure
)
```

### Root cause (why it is inconsistent with the food-picture behaviour)

1. **No failure placeholder (blank box).** When `logoUrl` is set but the image cannot load (offline, expired CDN URL, 404), `onError` only hides the spinner — `AsyncImage` draws nothing, leaving an empty white box (`ShopConfigSection.kt:300-305`). Contrast with the dish-photo component `MenuItemThumbnail` (`core/designsystem/MenuItemThumbnail.kt:80-133`), which flips `loadFailed = true` on error and renders a `Restaurant`/`AddPhotoAlternate` placeholder icon + FSSAI badge. This is the "something works, something doesn't" split: dish photos degrade gracefully, the shop logo does not.
2. **No remote→local fallback in the UI.** Loader B falls back to `logoPath` **only when `logoUrl` is blank** (line 308-309). Loader A tries remote first *then* local. So a device that is offline (or whose CDN URL is stale) shows a blank logo in Shop Profile even though a local copy exists and the invoice/QR path would still have used it.
3. **Loading-state bugs.** `var isLogoLoading by remember { mutableStateOf(true) }` (line 311) is position-keyed, **not keyed to `logoModel`**. After the first load it stays `false`; when the model changes (new logo after upload/sync) the spinner only re-appears if Coil fires `onLoading` again — which it may skip for a cached image — so the spinner behaviour is unpredictable. Also `memoryCacheKey("$logoModel:$logoUpdateTrigger")` changes on every pick (`logoUpdateTrigger`, line 276), forcing a re-load and a spinner flash even for the same image.
4. **Stale disk cache on replace.** `MenuItemThumbnail` keys **both** memory and disk cache by `"$imageUrl:$imageVersion"` (MenuItemThumbnail.kt:95-98) so an updated dish photo invalidates immediately. The shop-logo request sets only a `memoryCacheKey` and **no `diskCacheKey`** (line 317-318), so a replaced logo served at the same URL can display the old bitmap from Coil's disk cache.
5. **Upload requires internet and fails the whole save.** `SettingsViewModel.saveProfileWithLogo` (`feature/settings/viewmodel/SettingsViewModel.kt:1224-1261`) does `restaurantRepository.uploadLogo(part)` + `restaurantRepository.saveProfile(finalProfile)` in one coroutine — offline, the upload throws and the entire profile save is aborted with "Couldn't save settings" (caught at line 1251-1256). The picked image only previews locally via `pendingLogoUri` until Save succeeds.

### Fix — APPLIED (working tree, uncommitted)

`ShopConfigSection.kt`: the inline loader is now a **3-stage state machine keyed on the resolved logo model** — stage 0 primary (pending → remote → local), stage 1 local fallback (only when remote failed **and** a local copy exists), stage 2 placeholder. Added `diskCacheKey` + `CachePolicy.ENABLED` on both caches; cache key is now `"$effectiveModel:${profileLogoUrl?.logoVersion ?: 0}"` (server version, not a clock tick); explicit `Storefront` placeholder icon branch; removed the `logoUpdateTrigger` cache-buster.

`SettingsViewModel.saveProfileWithLogo`: logo upload is best-effort in its own try/catch — on failure it logs, sets `_logoUploadError` (sanitized "Logo upload failed. Other changes saved.") and **still saves the rest of the profile** (mirrors the menu-item save-then-photo split).

Not applied (still open): `mutableLongStateOf` import left unused in `ShopConfigSection.kt` after removing `logoUpdateTrigger`; reusing `ShopLogoLoader` directly instead of the inline loader.

---

## Issue 3 (user item #7) — What works offline vs. what needs internet (evidence map)

Connectivity detection: `feature/sync/domain/NetworkMonitor.kt` — `ConnectionStatus { Available, Unavailable }`, based on `ConnectivityManager` with `NET_CAPABILITY_INTERNET` **and** `NET_CAPABILITY_VALIDATED` (lines 46-63), exposed as a `Flow` and consumed e.g. by `BillingViewModel.connectionStatus` (`feature/billing/viewmodel/BillingViewModel.kt:135-140`). The New Bill screen shows an "Offline — bill will sync when back online" banner when `Unavailable` (`MenuSelectionStep.kt:200-225`).

### WORKS OFFLINE (local Room DB / on-device hardware)

| Capability | Evidence |
|---|---|
| View menu categories, items, variants, search | All from Room flows: `MenuViewModel.kt:79-148` (`categoryRepository.getAllCategoriesFlow()`, `menuRepository.getAllItemsFlow()`, `searchMenuWithVariants`) |
| Create a bill / add items to cart / compute totals | `BillingViewModel.completeOrder` writes to local Room via `billRepository.insertFullBill` (`BillingViewModel.kt:949-954`); invoice number allocated locally from terminal series + local sequence (`allocateInvoiceIdentity`, `BillingViewModel.kt:107-124`) |
| Save Table / draft orders, append items to draft | `saveDraftOrder` (`BillingViewModel.kt:1066`), `appendItemsToDraft` (`:1199`), `activeDraftBillsFlow` (`:244`) |
| Bill history, orders list, reports, daily counters | Local DB reads (`BillRepository`, `restaurantRepository.incrementAndGetTerminalDailyCounter`, `BillingViewModel.kt:870-874`) |
| Menu item add/edit/delete (master data rows) | `MenuViewModel.addItem/addItemWithVariants/updateItem/deleteItem` write local Room first (`:283-375`, `:377-396`, `:543-562`); sync is deferred |
| Disable an item (availability OFF) | `MenuViewModel.toggleItem` only calls the API when **enabling** (`:520-529`); disabling is local-only (`:530`) |
| On-device OCR menu import (PDF/image) | ML Kit `TextRecognition` runs on-device: `MenuViewModel.processMenuImage` (`:878-927`), `extractTextFromPdf` (`:790-876`); sharpness check `:929-972` |
| UPI QR generation (static test QR / invoice QR) | `QrCodeManager.generateUpiQrWithLogo` — ZXing encoding, purely local (`feature/payments/domain/QrCodeManager.kt:26-102`); logo bitmap may fail offline if only a remote URL exists (see Issue 2b) |
| Print to USB / Bluetooth / Wi-Fi LAN printers | `feature/printing/domain/` — `UsbPrinterTransport`, `BluetoothPrinterManager`, `NetworkPrinterScanner` (LAN discovery), `PrintRouter`, `PrintService`; kitchen print queue persisted in Room (`KitchenPrintQueueRepository`) |
| Invoice text / PDF generation, share | `InvoiceFormatter.kt`, `InvoicePDFGenerator.kt` — local rendering; logo falls back to local `logoPath`/default (`InvoiceFormatter.kt:55-64`) |
| Cart persistence across process death | `savedStateHandle` restore (`BillingViewModel.kt:153-164`) |
| Queued sync auto-retry | `MasterSyncWorker` (WorkManager) retries with exponential backoff (`feature/sync/worker/MasterSyncWorker.kt:86-91`); `SyncManager.triggerImmediateSync` debounces (`:59-84`) |

Contract tests proving the offline behaviour: `androidTest/.../test/screens/OfflineTest.kt` — login fails gracefully offline (`TC_OFFLINE_001_Login_FailsGracefully_Offline`), cached dashboard (`_HomeScreen_CachedData_Offline`), cached menu in New Bill (`_NewBillScreen_CachedMenu_Offline`), cached orders (`_OrdersScreen_CachedData_Offline`), reconnect refresh (`_Reconnect_RefreshesData`).

### NEEDS INTERNET

| Capability | Evidence |
|---|---|
| Login / OTP send & verify | `AuthViewModel.sendOtp` / `confirmMobileNumberUpdate` hit the API; offline login fails (`OfflineTest.kt:32-39`) |
| Initial & periodic master-data sync (pull) | `SyncManager.pullAndPersistMasterData` → `api.pullMasterSync` (`feature/sync/domain/SyncManager.kt:320-376`); `MasterSyncWorker` requires `NetworkType.CONNECTED` (`MasterSyncWorker.kt:68-73`), period 15 min (WorkManager minimum, comment `:75-79`) |
| Push of bills / profiles / menu changes to server | `SyncManager.runSyncCycle` → `masterSyncProcessor.pushAll()` (`SyncManager.kt:261`), `pushBillOnly` (`:287-301`) |
| Terminal activation / deactivation checks | `api.activateTerminal` (`SyncManager.kt:111-195`) |
| Dish photo upload / delete | `MenuViewModel.uploadItemPhoto` → `khanaBookApi.uploadMenuItemImage` (`MenuViewModel.kt:450`, with one 409 retry at `:449-458`); `deleteItemPhoto` → `khanaBookApi.deleteMenuItemImage` (`:497`). New-item photo waits up to 20s for a serverId (`uploadNewItemPhotoWhenReady`, `:404-423`) |
| **Enabling** an item (availability ON) | `khanaBookApi.markMenuItemAvailable(serverId)` (`MenuViewModel.kt:525`) — server must confirm; failure rolls back the toggle |
| Shop logo upload + profile save | `SettingsViewModel.saveProfileWithLogo` → `restaurantRepository.uploadLogo` + `saveProfile` (`SettingsViewModel.kt:1235-1244`); whole save fails offline (`:1251-1256`) |
| WhatsApp number change (user-exists check + OTP) | `SettingsViewModel.checkUserExists`, `AuthViewModel.sendOtp` (`ShopConfigSection.kt:372`, `:393`) |
| Online payments (Easebuzz gateway) | `EasebuzzPaymentRepository` payment-link creation & gateway session (`feature/payments/data/EasebuzzPaymentRepository.kt`); `BillingViewModel.createPaymentLinkForBill` (`:258-303`), `finalizeOnlineBill` (`:746`) |
| Staff/role/user management API calls | `UserRepository` / `PermissionManager.updateFromSync` (`SyncManager.kt:347`) |

### Sync architecture (how offline data reaches the cloud)

- `MasterSyncWorker` — periodic, 15 min, `NetworkType.CONNECTED`, exponential backoff 1 min (`MasterSyncWorker.kt:67-99`).
- `SyncManager.triggerImmediateSync()` — 5s debounce so bill-create bursts coalesce (`SyncManager.kt:54-84`); called right after local bill insert (`BillingViewModel.kt:958`).
- Push→pull cycle is atomic; the sync checkpoint timestamp is only advanced after a successful full pull (`SyncManager.kt:251-269`, `:364-375`).
- Conflict recovery: server-wins re-pull from timestamp 0, then re-push; unresolvable rows are quarantined (`SyncManager.kt:378-414`).
- 401/403 invalidate the session (`MasterSyncWorker.kt:125-134`); clock drift > 180s is logged (`SyncManager.kt:47`, `:369-371`).

---

## Issue 5 — Payment-mode edit in the Orders Report table (old = UPI, select UPI)

> Symptom reported: in Report Details → Orders Report table, editing a bill's payment mode where the current mode is UPI and the user selects UPI "does not work".

### Where mode editing actually lives

The **Order Details dialog is read-only** — it *displays* the mode (`OrderDetailsDialog.kt:162` `DetailRow("Payment Mode:", ...)`) but has **no mode editor**. The only payment-mode editor in the whole report feature is the **Mode dropdown in each Orders Report table row**:

- `OrderRowItem` Mode `Surface` + `DropdownMenu` — `feature/reports/ui/ReportViews.kt:443-479`.
- Entry guards on the Mode surface tap (`ReportViews.kt:446-452`):
  - `isCancelled || !isSameDayAsTaken` → blocking toast via `onEditBlocked()` (`:402-409`: "Cancelled orders can't be changed." / "Paid orders can't be edited." / "Only today's orders can be edited.").
  - `isPayAfterFoodDraft` → **silent no-op** (`Unit`) — dine-in pay-after-food drafts never open the mode dropdown (`:410-418`).
  - `selectablePayModes.isEmpty()` → silent no-op (`Unit`) — single-mode shops get no menu at all.
  - otherwise → `payModeExpanded = true` (dropdown opens).
- Dropdown lists **`selectablePayModes = enabledModes.filter { it != row.paymentMode }`** (`ReportViews.kt:396`, menu `:472-478`) — the current mode is **excluded** (APPLIED fix, see below), sourced from `ReportsScreen.kt:87-89` → `PaymentModeManager.getEnabledModes(profile)` (`feature/payments/domain/PaymentModeManager.kt:14-30`: CASH/UPI/POS + the three part-modes; Easebuzz/PaymentLink hidden).

### Full write path (all `file:line`)

1. `ReportsScreen.onPayModeChange` (`ReportsScreen.kt:231-238`): part-payment modes open `PartAmountDialog`; single modes → `viewModel.updatePaymentMode(billId, newMode.dbValue)`.
2. `ReportsViewModel.updatePaymentMode` (`feature/reports/viewmodel/ReportsViewModel.kt:231-263`): `PaymentMode.fromDbValue(newMode)` (`:234`), patches in-memory `_orderDetailsTable` (`:237-246`) and `_orderLevelRows` (`:248-260`), then `billRepository.updatePaymentMode(billId, newMode, partAmount1, partAmount2)` (`:261`), then `loadReports(currentFrom, currentTo)` (`:262`).
3. `BillRepository.updatePaymentMode` (`feature/billing/data/BillRepository.kt:453-485`):
   - `current = billDao.getBillById(id, restaurantId) ?: return` (`:455`) — **unguarded** read.
   - `if (current.orderStatus == "cancelled") return` (`:456`).
   - `billDao.updateBill(current.copy(paymentMode = mode.lowercase(), partAmount1, partAmount2, statusVersion = current.statusVersion + 1, isSynced = false, updatedAt = now))` (`:458-467`) — Room `@Update` (`BillDao.kt:82-83`).
   - `activePayments = billDao.getActivePaymentsForBill(id, restaurantId)` (`:469`; query `BillDao.kt:153-158`); **only if exactly 1 record** → `billDao.updateBillPayments(...)` with the new mode (`:470-482`; `@Update` `BillDao.kt:150-151`).
   - `triggerBackgroundSync()` (`:484`) → `workManager.enqueueMasterSyncOnce()` (`:116-118`).
4. Sync push: `MasterSyncProcessor.pushSingleBill` (`feature/sync/domain/MasterSyncProcessor.kt:381-442`).
5. Server apply: `BillSyncService.protectBillState` (`server/.../billing/service/BillSyncService.java:314-354`) — a **strictly greater `statusVersion`** is treated as a deliberate edit and applied (`isDeliberateStatusEdit`, `:361-363`).

### Read path (why the row shows what it shows)

- `ReportGenerator.getOrderLevelRows` (`feature/reports/domain/ReportGenerator.kt:66-83`) reads the mode from **`bills.payment_mode`** (`:73` `PaymentMode.fromDbValue(bill.paymentMode)`); `getOrderDetailsTable` likewise (`:99`).
- The report's row source `BillDao.getBillsByDateRange` (`BillDao.kt:433-436`) filters `created_terminal_id = :terminalId` — the table only ever lists **this terminal's** bills.

### Root cause (evidence-based)

1. **The path is mode-agnostic — there is no UPI-specific branch and no exception is thrown.** Selecting UPI when the bill is already UPI writes `payment_mode = 'upi'` (the identical value), bumps `status_version`, sets `is_synced = 0`, rewrites the single `bill_payments` record to `'upi'` (identical), and triggers a sync. The row already displays "UPI" and still displays "UPI" — **no visible delta, no toast, no confirmation**. This reads as "not working" but is a **silent no-op write**, not a crash or error.
2. **Latent defect — unguarded read bypasses the ownership guard.** `updatePaymentMode` loads via raw `getBillById` (`BillRepository.kt:455`), which returns *any* bill regardless of terminal ownership or `is_deleted`. The sibling mutator `updateBill()` guards with `isLocallyOwned(bill)` (`BillRepository.kt:135-139`; guard defined `:74-78`: `recordScope == "terminal_operational" && recordOrigin == "local_created"`), and the DAO's own contract comment says mutable workflows "must load bills through this method [`getOperationalBillById`, `BillDao.kt:202-215`] so a history record can never reach a DAO write". `updatePaymentMode` violates that contract. (Not the trigger here — the report table only lists this-terminal bills — but the same gap exists in `cancelOrder` and `updatePaymentStatus`.)
3. **Latent defect — `bill_payments` divergence.** The payment-record sync only runs when **exactly one** active payment record exists (`BillRepository.kt:469-483`). A bill with 0 or ≥2 active payment records gets `bills.payment_mode` changed while `bill_payments` keeps the old mode — and the server keys payment-level matching on `payment_mode` (`BillSyncService.isExactPaymentMatch`, `BillSyncService.java:248`), so the two tables can disagree after the edit.
4. **Latent defect — part-amount clobber.** `partAmount1`/`partAmount2` are unconditionally reset to `"0.0"` (`BillRepository.kt:461-462`) even for non-part modes — harmless for UPI, but it would wipe a previous split's stored amounts.
5. **Dead DAO method.** `BillDao.updatePaymentMode` (`BillDao.kt:397-403`, a targeted `UPDATE bills SET payment_mode = ...` query) is **never called** — the repository rewrites the whole entity via `@Update` instead.

### Fix — PARTIALLY APPLIED (working tree, uncommitted)

The reported symptom (re-picking the current mode looks broken) is fixed at the UI layer in both tables:

- `ReportViews.kt:396` (and `OrderTableComponents.kt:92`): `val selectablePayModes = enabledModes.filter { it != row.paymentMode }` — the dropdown is a "change to…" list, so the current mode is **excluded**; the chip opens the menu only when another mode exists (`selectablePayModes.isEmpty() -> Unit`), so a single-mode shop no longer gets an empty dropdown.
- Blocked edits now speak: `onEditBlocked()` (`ReportViews.kt:402-409`) toasts "Cancelled orders can't be changed." / "Paid orders can't be edited." / "Only today's orders can be edited." instead of a silent no-op.
- Pay-after-food drafts: explicit guard with comment (`ReportViews.kt:410-418`) — never open the mode menu, never mark Completed from the report.
- Same change in the New Bill orders table (`OrderTableComponents.kt:181,200`).

Still open (the write-path latent defects from the root-cause list above):

- Same-mode selection is now unreachable from the UI, but `ReportsViewModel.updatePaymentMode` still performs the full write (status_version bump + sync trigger) for any selected mode; skipping the write when `current.paymentMode == normalizedMode` would remove the last no-op write path.
- Load through `getOperationalBillById` (or add the `isLocallyOwned` guard) in `updatePaymentMode`/`cancelOrder`/`updatePaymentStatus` to match the `updateBill()` contract.
- Sync `bill_payments` for the 0/≥2-record cases (or delete+reinsert the payment set) so the two tables cannot diverge.
- Only write `partAmount1/2` when the target mode is a part-payment mode.
- Remove the dead `BillDao.updatePaymentMode` (`BillDao.kt:397-403`) or route the repository through it.

---

## Issue 6 — "Cancel Order" dialog: contents, purpose, and read access

### Where it is shown

`ReportsScreen.kt:304-316` — `CancelOrderDialog(onDismiss, onConfirm)` is rendered whenever `cancelBillId != null`. It is opened from two places:

- Orders Report table → Status dropdown → **"Cancel Order"** item (`ReportViews.kt:528-531`) → `onRequestCancel` → `ReportsScreen.kt:239-241`.
- Order Details dialog → `onCancelOrder` (`ReportsScreen.kt:298-300`).

### Element inventory and purpose

Dialog composable: `feature/billing/ui/OrderTableComponents.kt:279-326`, built on the shared `KhanaBookSelectionDialog` (`core/designsystem/KhanaBookSelectionDialog.kt:39-113`).

| # | Element | Code | Purpose |
|---|---------|------|---------|
| 1 | Title **"Cancel Order"** | `title` param (`:286`) | Dialog heading. |
| 2 | Message **"Select a reason:"** | `message` param (`:288`) | Instruction prompt. |
| 3 | 4 preset reason cards: **"Wrong order"**, **"Customer left"**, **"Test bill"**, **"Other"** | `presetReasons` (`OrderTableComponents.kt:285`), rendered as single-select cards (`KhanaBookSelectionDialog` options, `:64-103`) | Capture *why* the order is cancelled; the chosen value is stored as the bill's `cancel_reason`. **"Duplicate bill" was removed from the presets in the working tree (uncommitted)** — verify no analytics/reporting buckets on that reason string before relying on the change. |
| 4 | Default pre-selection **"Customer left"** | `selectedReason` init (`:282`) | Most common reason pre-chosen so a single tap suffices. |
| 5 | Red selection accent | `selectedAccent = DangerRed` (`:293`); card tinted at `KhanaBookSelectionDialog.kt:73-77` | Visual confirmation of which reason is picked; red signals destructive action. |
| 6 | **"Other Reason"** free-text field (label "Other Reason", placeholder "Describe the reason...") | `trailingContent` shown only when Other is chosen (`:301-312`); cleared when a preset is picked (`:296`) | Free-text reason when no preset fits. |
| 7 | **"Keep Order"** cancel button | `cancelLabel` (`:313`); dismiss button at `KhanaBookSelectionDialog.kt:108-110` | Dismiss the dialog **without** cancelling (dismiss = keep the order). |
| 8 | **"Cancel Order"** confirm button (red, bold) | `actions` (`:314-324`) | Enabled only when a reason is chosen (and non-blank for "Other"); calls `onConfirm(finalReason)`. |

### Read access — who may cancel, and the gates

- **Permission model:** `BILLING_VOID = "billing.void"` — display name **"Cancel/Void Bills"** — still exists in the model: Android `PermissionManager.kt:193` and `:256` (in `ALL_PERMISSION_KEYS` `:294`); server `PermissionKey.java:7`.
- **Granted to SHOP_STAFF by role (not owner-only):** `billing.void` is absent from the owner-only config families — server `PermissionService.SHOP_STAFF_CONFIG_KEYS` (`PermissionService.java:176-186`) and the mirrored Android `SHOP_STAFF_CONFIG_KEYS` (`PermissionManager.kt:230-240`) both exclude it, so `SHOP_STAFF_GRANTED_KEYS` (server `:195-199`, Android `:249-250`) includes it. Every SHOP_STAFF member has cancel/void by default; owners always can.
- **Client-side the key is vestigial — no production code checks it.** `hasPermission(BILLING_VOID)` is called nowhere in the app (only in tests, `PermissionManagerTest.kt:67,92,101`). `ReportsViewModel.cancelOrder` (`ReportsViewModel.kt:163-183`) performs no local permission gate — unlike `canViewFullReports()` / `canExportReports()` which do check `REPORTS_FULL` / `REPORTS_EXPORT` (`ReportsViewModel.kt:33-42`). The cancel action's only client gates are the UI same-day/not-cancelled rules and the VM's already-cancelled check below.
- **Enforcement happens server-side at sync:** `billing.void` is classified `REVALIDATED_ON_SYNC` (`OfflineAuthClass.java:37`) — a cancel made offline is accepted locally, and the server re-checks the permission when the cancelled bill is pushed (`OfflineAuthDecider.java:43`).
- **UI guards** (`ReportViews.kt:379-402`, `:481-531`): the Status dropdown that contains "Cancel Order" only opens when `!isCancelled && isSameDayAsTaken`. Cancelled orders and previous-day orders get a blocking toast ("Cancelled orders can't be changed." / "Paid orders can't be edited." / "Only today's orders can be edited."). → **cancellation is same-day-only from the table.**
- **ViewModel guard** (`ReportsViewModel.kt:168-170`): refuses an already-CANCELLED bill ("Order is already cancelled.").
- **Part-payment guard** (`ReportsViewModel.kt:172-181`): when a part-payment bill is cancelled, its in-memory breakdown is zeroed so the Payment report totals don't still count the cancelled amount.
- **Repository side effects** (`BillRepository.cancelOrder`, `BillRepository.kt:398-439`): snapshots items **before** the flip (so the kitchen CANCEL event captures the order as the kitchen last knew it); `BillDao.cancelBill` (`BillDao.kt:408-413`) sets `order_status='cancelled'`, `payment_status='failed'`, `cancel_reason`, `status_version+1`, `is_synced=0`; deletes the kitchen print queue for the bill (`:405`); records a **CANCEL KOT event only if the kitchen had already printed ≥1 KOT** for that `publicToken` (`:410-421` — orders the kitchen never saw get no notice); flushes the print queue immediately so the kitchen sees "*** ORDER CANCELLED ***" at once (`:426-438`); triggers background sync (`:439`).
- **Pay-after-food drafts** (`ReportViews.kt:403-411`): dine-in pay-after-food drafts may **only** be cancelled from here (never marked "Completed"), because their payment is captured at settlement on the billing screen.

---

## Summary — status of each item

| # | Item | Status |
|---|------|--------|
| 1 | Manual Entry spacing (88dp FAB-era token above a fixed footer) | **Fixed in working tree** — `spacing.extraLarge` at `ManualMenuView.kt:316`. Footer-height-derived inset + `navigationBarsPadding()` on the footer still open. |
| 2a | New Bill menu-selection bottom spacing | **Fixed in working tree** — grid padding loses the bottom side; trailing spacer and cart `contentPadding` use `spacing.extraLarge`. Shared helper still open. |
| 2b | Shop logo resilience + offline-safe profile save | **Fixed in working tree** — 3-stage model-keyed loader with disk cache key + placeholder; best-effort logo upload in `saveProfileWithLogo`. Unused `mutableLongStateOf` import remains. |
| 3 | Offline vs. internet evidence map | **Done** — informational, no code change required. |
| 5 | Payment-mode edit (UPI→UPI silent no-op) | **Partially fixed in working tree** — dropdown excludes the current mode; blocked edits toast; pay-after-food drafts guarded. Write-path latent defects (unguarded `getBillById`, `bill_payments` 0/≥2-record divergence, part-amount clobber, dead DAO method) remain open. |
| 6 | Cancel Order dialog inventory + access model | **Done** — contents/purpose/read-access documented. Preset list now 4 reasons ("Duplicate bill" removed in working tree — verify analytics impact). Access model: any SHOP_STAFF can cancel (`billing.void` role-granted); client-side key is vestigial, enforcement is server-side at sync (`REVALIDATED_ON_SYNC`). No code change required unless cancel should be restricted further. |

### Additional working-tree changes observed (uncommitted, same batch)

- `Android/app/proguard-rules.pro` — keep rules for the renamed SQLCipher package `net.zeteta.**` (alongside the now-dead `net.sqlcipher.**` rules) + Room/`androidx.room.**`/`androidx.sqlite.db.**`/`androidx.security.crypto.**` hardening.
- `core/database/DatabaseProvider.kt` — log-string `expectedRoomVersion` bump 78 → 79 (matches `AppDatabase` version 79).
- `DatabaseMigrationListConsistencyTest.kt` — rewritten from source-text scraping to a behavioural chain test asserting every step 17→18 … 78→79 exists in `AppDatabase.ALL_MIGRATIONS`.
