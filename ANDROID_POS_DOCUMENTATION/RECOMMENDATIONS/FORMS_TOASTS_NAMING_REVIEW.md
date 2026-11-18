# Forms, Toasts, Notifications & Naming — Static Review

**Review date:** 2026-11-18
**Session:** 019 (`SES-20261118-019`)
**Scope:** Android POS (`apps/android`), server (`services/server`), web-admin (`apps/web-admin`)
**Method:** Static source inspection only. No APK was built, installed, or exercised; no device was used. Counts and line references come from the working tree at review time.
**Status:** Findings recorded. **Nothing was implemented** — see `RECOMMENDATIONS/README.md` rule.

---

## 1. Input fields and header names

### 1.1 Inventory (Android)

Shared components:

- `core/designsystem/KhanaBookInputField.kt` — ~17 call sites; supports `label`, `placeholder`, `supportingText`, icons, `isError`.
- `core/components/CommonComponents.kt` — `ParchmentTextField` wrapper + date/time pickers.
- ~36 direct `OutlinedTextField` call sites bypass the shared component (auth, cart, menu, payments, printing, onboarding).

### 1.2 Findings

**IN-01 (P1) — Change Password contradicts the account password policy.**
`ChangePasswordView.kt:252` says "Minimum 6 characters, use letters & numbers" and `:344` enforces only `length < 6`. Signup and Forgot Password require 8 characters with uppercase, digit and special character (`ValidationUtils.kt:24-31`). An owner can downgrade a strong password through Change Password.
Suggested: reuse `ValidationUtils.isValidPassword` + `passwordStrengthMessage()`.

**IN-02 (P1) — Required markers do not match save validation.**
`ShopConfigSection.kt` labels "Shop Name *" and "Shop Address *", but `saveConfiguration()` (`:369-386`) validates only phone, email and business hours. WhatsApp Number is effectively required (changed number blocks save until OTP verification) yet has no asterisk.

**IN-03 (P1) — GST percentage rejects decimals in the UI but accepts them in validation.**
`TaxConfigSection.kt:176` filters to digits only (`take(3)`), while `ValidationUtils.isValidTaxPercentage` allows `0.0..100.0`. `5.5` is untypable.

**IN-04 (P1) — Price keyboard does not match price rules.**
`ItemEditDialog` base and variant price fields use `KeyboardType.Number`, but `formatPriceInput` preserves paise (e.g. `120.99`). QuickStart and part-payment use `KeyboardType.Decimal`. Decimal prices may be unreachable on some IMEs.

**IN-05 (P1) — Variant price field truncates decimals and cannot display zero.**
`ItemEditDialog.kt:449` renders `variantDraft.price.toInt().toString()`. Typing `12.5` re-renders `12`; `0` displays empty.

**IN-06 (P2) — Neutral helper text is painted as an error.**
`ParchmentTextField` forces `supportingText` to `DangerRed` (`CommonComponents.kt:154`), so hints such as "Enter exactly 14 digits" always read as failures.

**IN-07 (P2) — Header names are inconsistent.**

| Current | Where | Suggested |
| --- | --- | --- |
| `Login ID` | Login | `Phone or email` |
| `Shop Name *` / `Shop name` | Settings / QuickStart | `Shop name` + required marker |
| `WhatsApp` | Signup | `WhatsApp number` |
| `Enter OTP` / `Enter 6-digit OTP` / `Enter 6-digit Email OTP` | Auth + Settings | `6-digit OTP` (+ helper naming the destination) |
| `Base Price (₹)` / `Price (₹)` / `₹ Price` / `Price` | Menu + QuickStart | `Price (₹)` everywhere |
| `Table name / Customer name` | Cart (dine-in) | `Customer name`, helper "or table label, e.g. A01" |
| `Other Reason` | Cancel dialog | `Other reason` |
| `Printer name`, `Port` | Wi-Fi printer dialog | Add placeholders/helpers; `Port` states default 9100 |

**IN-08 (P2) — Search placeholders enumerate backend field names.**
`ActiveOrdersScreen.kt:179` and `ReportViews.kt:489` show `OrderNo, CustomerName, PhoneNumber, ItemName, BillAmount…`. Replace with plain-language help: "Order no, customer, phone or item".

**IN-09 (P2) — OTP/password accessibility.**
Several visibility toggles pass `contentDescription = null` (`SignUpScreen.kt:367`, `:428`). OTP fields do not use `KeyboardType.NumberPassword`. No field anywhere declares an autofill content type (zero `ContentType` usages).

**IN-10 (P2) — Capitalization not set on name fields.**
`ItemEditDialog` "Item Name" and Settings "Shop Name"/"Shop Address" omit `KeyboardCapitalization.Words`; QuickStart already sets it.

**IN-11 (P3) — Date fields are `enabled = false`.**
`CommonComponents.kt:71` disables the field while attaching a click modifier, so assistive tech announces it as disabled.

**IN-12 (P3) — Missing counters/limits.**
`Review Link`, `Invoice Footer` and `Shop Address` accept free text with no visible limit, although they print on receipts.

**IN-13 (P3) — Labels are hardcoded.**
`stringResource` appears in two files only; every header is a Kotlin literal. Blocks localisation.

**IN-14 (P3) — A saved setting has no control.**
`ShopConfigSection.kt:133` holds `consent` from `profile.emailInvoiceConsent`, threads it into `isDirty` (`:173-178`), restores it on profile change (`:269`) and writes it back on save (`:394`), but no composable binds it. The value can only change via sync, and the dirty-check can never be triggered by it. Session 013 first noticed this; recorded here as a formal finding. Decide whether to expose an "Email invoice consent" switch or delete the state.

---

## 2. Toasts and snackbars

Architecture: one `KhanaToast` (`core/designsystem/KhanaBookSnackbar.kt`), four kinds, fixed 2 s success / 3.5 s error windows, duplicate suppression, single visible message, `liveRegion = Polite`. Adoption: **81 `KhanaToast.show` sites, 1 raw `Toast.makeText`** (`NotificationActionReceiver.kt:43`).

**TO-01 (P2) — Dedupe window is global.**
`DUPLICATE_WINDOW_MS = 2_000` keyed on message+kind, not screen or intent. A repeat action ("Scan" → same "No USB printers found") produces no feedback at all. Verified in `KhanaToast.show`.

**TO-02 (P2) — Errors are not actionable.**
Only ~2 of 81 call sites pass `actionLabel`, despite the component supporting it. "USB permission denied…", "Couldn't save profile…", "Invalid photo" disappear after 3.5 s with no Retry.

**TO-03 (P2) — Errors can be superseded.**
Every new toast cancels the previous one, so a success can replace an error raised milliseconds earlier.

**TO-04 (P2) — Copy is inconsistent.** Verified examples:

- Five phrasings for "nothing can open this": `No app available to open PDF`, `No browser available`, `No email app is available`, `Cannot open Email app`, `Cannot open SMS app`.
- `OTP Sent to your WhatsApp!` / `Payment link copied to clipboard!` vs the rest without `!` and without Title Case.
- `"pending"` — lowercase, content-free.
- `Invalid menu-item photo for localId=$menuItemId` — leaks an internal ID.
- `Customer not found`, `Invalid photo` — no next step.
- `Opening and closing time cannot be the same (must be less than 24 hours)` — two rules in one sentence.

Suggested pattern: sentence case, no exclamation marks, "<what happened> — <what to do>", never echo internal IDs.

**TO-05 (P3) — Timing ignores accessibility.**
Fixed durations ignore `AccessibilityManager` recommended timeout and message length (up to 3 lines). Errors have no close affordance (only action toasts get `withDismissAction`).

**TO-06 (P3) — Copy is hardcoded** in all 81 call sites.

---

## 3. Notifications

Registered: 7 channels in 3 groups; the preferences screen reads the actual registered channels (good, no drift).

**NF-01 (P1) — Permission requested at launch, denial is invisible.**
`MainActivity.kt:190` calls `requestNotificationPermissionIfNeeded()` in `onCreate`, before any rationale; the result callback only logs (`:162`). No `areNotificationsEnabled` check exists anywhere, and Notification Preferences has no "OS notifications are off" state or settings deep link.

**NF-02 (P1) — Every channel is `IMPORTANCE_HIGH` with sound/vibration**, including "System Alerts" (sync status) and "Low Stock Alerts". Recommended: HIGH for Payment/Settlement/Shift; DEFAULT for Refund/KYC/System; LOW for Inventory.

**NF-03 (P2) — Foreground push produces a tray notification and an in-app banner.**
`onMessageReceived` always calls `showNotification(...)`, and `insertLocal` → Room → `watchForNoticeBanners` also promotes the item to `activeBanner`. No foreground/background check exists (`ProcessLifecycleOwner` is unused).

**NF-04 (P2) — Group summary pluralisation bug.**
`"$count new ${domainLabel(type)}${if (count > 1) "s" else ""}"` with `domainLabel("order_cancelled") = "order cancelled"` yields "3 new order cancelleds". Use explicit singular/plural pairs or a `plurals` resource.

**NF-05 (P2) — Group summaries are never cleared on read.**
`groupPushCounts` / `groupChildren` are in-memory and reset on process death; nothing cancels `summaryId(groupKey)` in `markAsRead`/`markAllAsRead`. The tray can claim "6 new payment alerts" after the inbox is empty.

**NF-06 (P2) — `Long` notification IDs are truncated to `Int`.**
`notificationManager.notify(id.toInt(), …)` where `id` may be `System.currentTimeMillis()` (overflows `Int`). Collisions silently overwrite; `PendingIntent` request codes share the flaw.

**NF-07 (P2) — `runBlocking` in FCM callbacks.**
`insertLocal` and token registration run under `runBlocking` on the service main thread (ANR risk). Prefer `goAsync()`/WorkManager.

**NF-08 (P2) — Payload logged in release builds.**
`Log.d(TAG, "Message received: data=${message.data} …")` is not gated by `BuildConfig.DEBUG`; payloads can carry customer names and amounts. `NotificationHelper.getCircularLargeIcon` also calls `e.printStackTrace()`.

**NF-09 (P3) — Channel naming/copy.**
`"Payment Received"` is an event, not a channel category. `"FSSAI licence"` (worker) vs `"FSSAI License"` (settings). Group labels are declared twice (enum + helper). Names/descriptions are Kotlin literals shown in Android Settings, so not localisable.

**NF-10 (P3) — Colour tokens duplicated with drift.**
Saffron `0xFFF97316` (FCM, banner) vs `0xFFF59E0B` (shift worker); purple `0xFF7C5CDB` repeated in three files.

**NF-11 (P3) — Banner accessibility.**
Fixed `AUTO_DISMISS_MS = 6_000` with no pause on focus/touch, no visible action, and no `liveRegion`.

**NF-12 (P3) — Smaller items.**
No `setOnlyAlertOnce`; group summary always opens the `"system"` route regardless of group; `MAX_CHILDREN_PER_GROUP = 6` cancels older children from the tray but not the inbox; channel IDs carry `_v2` with no documented bump process.

---

## 4. Screen and function naming

### 4.1 Screen names

Cross-platform matrix:

| Concept | Android | Web admin | Server |
| --- | --- | --- | --- |
| Live work queue | `OrdersScreen`, `ActiveOrdersScreen`, `ActiveOrderDetailScreen` | `OrdersPage` tabs "Live orders" + "Order history"; `LiveOrdersTabComponent` | `BillController`, `BillService` |
| New order | `NewBillScreen`, route `new_bill` | — | `/sync/bills` |
| Menu | `MenuConfigurationScreen`, "Menu Configuration" | `MenuPage`, "Menu & Dishes Catalog" | `/menus`, `MenuItemController` |
| Tenant settings | "Restaurant Configuration" → `ShopConfigSection` | `BusinessSettingsPage`, "Shop Configuration", section "Restaurant profile" | `/restaurants` + `/business` |
| Devices | `DeviceSetupScreen`, `device_setup` | `TerminalsPage`, "POS Devices & Terminals" | `/sync/terminal`, `/restaurants/terminals` |
| Closing | "Business Hours" + `CashierShiftEntity` + `ClosingShiftReminderWorker` | `DailyClosingPage`, "Daily Closing & Settlement" | `BusinessDayConfigService`, `CashierShiftController`, `DailyClosingReportService` |

**NM-01 (P2) — One screen, four names:** Active Orders / Live orders / Live floor / bills.

**NM-02 (P2) — Android suffix drift.** 24 composables end in `Screen`; 6 end in `View` (`ChangePasswordView`, `HelpSupportView`, `AppLockView`, `SyncCenterView`, `ManualMenuView`, `ModeSelectionView`). `*Section` and `*Components` are used interchangeably (`ShopConfigSection` vs `ShopConfigComponents`). Noun order is mixed (`ItemEditDialog` vs `ManageCategoriesDialog`).

**NM-03 (P3) — Dead magic tab index.** `NavigationTabs` uses `0, 1, 3`; index 2 is gone with only a comment. Use an enum/mapping.

**NM-04 (P2) — Web page file names do not match routes.** `admin/dashboard` → `platform-dashboard-page`, `admin/sales` → `sales-overview-page`; `business/reports` → `reports-page` (ambiguous owner) while `business/dashboard` → `business-dashboard-page`. Adopt `<scope>-<feature>-page`.

**NM-05 (P3) — Modal vs dialog.** `*-modal` (3) and `*-dialog` (1) coexist.

```
business/active-orders → business/orders redirect (with comment) is the right
pattern for renaming a screen without breaking deep links; replicate it.
```

### 4.2 Function names

Observed verb counts:

| Layer | Verbs |
| --- | --- |
| Android ViewModels | `clear` 22, `update` 20, `set` 18, `refresh` 12, `save` 9, `load` 9, `get` 9, `toggle` 6 |
| Web-admin services | `get` 46, `update` 10, `set` 9, `create` 9, `delete` 7, `lookup` 3, `mark` 2, `revoke` 2 |
| Server controllers | domain verbs: `login`, `signup`, `push`, `pull`, `listTerminals`, `reclaim`, `approveRequest` |

**NM-06 (P1) — Three password-reset families exist.**
`AuthController` exposes `resetPassword` + `requestResetPasswordOtp` (`/auth/reset-password*`), `forgotPasswordRequestOtp` / `VerifyOtp` / `ResetPassword` (`/auth/forgot-password/*`), and `changePassword`. Android calls the first (`KhanaBookApi.kt:87-91`); web-admin calls the second (`auth.service.ts:93-101`) from `password-change-section.component.ts`.

**NM-07 (P1) — Terminal lifecycle has three verbs for one step:** `reclaim` (Android/server), `recover` (server/web), `reactivate` (web). `listTerminals()` is declared twice in the same package (`TerminalController`, `TerminalManagementController`).

**NM-08 (P2) — Menu availability has two semantics:** Android `markMenuItemAvailable` (one-way) vs web `toggleMenuItemAvailability` (two-way).

**NM-09 (P2) — Method/parameter drift:** `getOrderDetail(billId)`, `getBusinessDetail(restaurantId)`, `manualRefundOrder(billId)`.

**NM-10 (P3) — Admin prefix inconsistency:** `AdminNotificationController` (prefix) vs `FeatureFlagAdminController` (suffix).

### 4.3 Routes

**NM-11 (P1) — One resource, two base paths.** `/api/v1/restaurants/**` and `/api/v1/business/**` both serve the tenant. Menu appears as `/menus/items/...`, `/business/menu/...` and `/sync/menuitem/...`. Sync paths use singular nouns (`/sync/menuitem`, `/sync/itemvariant`) while `/sync/bills` is plural.

**NM-12 (P1) — One entity, four field names.**

| Server field | Occurrences |
| --- | --- |
| `shopName` | 58 |
| `businessName` | 10 |
| `restaurantName` | 2 |
| `restaurantAddress` | 2 |

`DailyClosingReportService.java:153-154` maps `getShopName()` → `restaurantName`. Web models pair `restaurantId` with `shopName`, and `AdminCreateBusinessRequest` carries `shopName`.

---

## 5. Proposed canonical glossary

| Concept | Code term | UI term | Retire |
| --- | --- | --- | --- |
| Tenant | `restaurant` (`restaurantId`) | Restaurant (or Shop, once decided) | `business*`, `shop*`, `outlet*` in code |
| Transaction | `bill` (`billId`) | Order | `transaction` for a bill |
| Live queue | `activeOrder` / `active_orders` | Active orders | "Live orders", "Live floor" |
| Device | `terminal` (`terminalId`) | Terminal | device/register/counter as separate nouns |
| Shop employee | `staff` | Staff | `user` for shop staff |
| Console login | `platformUser` | Users & access | unqualified `user` |
| Shift close | `shiftClose` | Shift closing | "daily closing" when it means shift |
| Day rollover | `businessDay` | Business day | "closing" |
| Reports | `report` | Reports | `analytics` in UI |

Suggested function rules:

- Reads: `getX(id)`, `listX()`, `searchX(query)` in data layers; `loadX()` for initial screen load and `refreshX()` for user-triggered reload in ViewModels only.
- Writes: `createX`, `updateX` (persist changed model), `setX` (single local field), `toggleX` (boolean flip), `deleteX`.
- Parameters must match the method noun (`getOrderDetail(billId)` is the anti-pattern).
- One endpoint per operation; delete duplicate families rather than aliasing them.

### Existing items this review intersects

These are already tracked and are **not** superseded by the findings above:

- `NEXT_ACTIONS.md` item **C** (unify the phone/email OTP flows in `ShopConfigSection`) covers part of IN-07/IN-09. Fix C once and the duplicated OTP labels/handlers shrink with it.
- `NEXT_ACTIONS.md` items **K**, **L** and **M** (Orders row actions, status changes, period summary) are unrelated to this review and were left untouched.
- Session 013's Restaurant Configuration field inventory is the evidence base for IN-02 and IN-07; IN-14 formalises its `emailInvoiceConsent` observation.
- Session 018 changed only the dish-photo copy, cache keys and spinner in `ItemEditDialog`. The price findings IN-04/IN-05 remain valid because Session 018 did not touch price handling.
- Session 016's finding #5 (dish-photo upload has no inline Retry) is the same gap as TO-02 from the toast side; treat them as one backlog item.

### Implemented in Session 019 (2026-11-18)

| ID | Fix |
| --- | --- |
| IN-01 / BUG-010 | Change Password now uses `ValidationUtils.isValidPassword` and the shared strength message. |
| IN-02 | Blank Shop Name / Shop Address now block save with inline errors; WhatsApp Number carries the required marker. |
| IN-03 / BUG-012 | GST % accepts one decimal separator and seeds stored decimals without truncation. |
| IN-04 | Base price, variant price and GST % use `KeyboardType.Decimal`. |
| IN-05 / BUG-011 | Variant price keeps raw text, so decimals and `0` survive editing. |
| IN-06 | `ParchmentTextField` paints helper text red only when `isError` is true. |

Verification: `:app:compileDebugKotlin` and the focused unit tests pass (see `ValidationUtilsTest`, `VariantDraftReconcilerTest`). Not device-verified and not committed.

**Still open:** IN-07..IN-14, all TO/NF/NM findings. In particular, the server still enforces only a 6-character minimum, so BUG-010 is client-side only.

---

## 6. Priority order

1. IN-01 password policy (security).
2. NM-12 tenant vocabulary; then NM-11 route bases.
3. NM-06 duplicate password-reset families.
4. NM-07 terminal verbs; Menu availability semantics.
5. IN-02..IN-05 form validation/input defects.
6. NF-01/NF-02 permission flow and channel importance.
7. NF-03..NF-06 notification correctness.
8. TO-01..TO-04 toast behaviour and copy.
9. IN-06..IN-14, NM-02..NM-05, TO-05/06, NF-07..NF-12 polish.

---

## 7. Verification status

- **VERIFIED by reading:** every file, line reference and count above.
- **NOT VERIFIED:** no runtime behaviour was observed in this session. The foreground tray/banner duplication (NF-03) is a code-path reading, not a device observation. No APK was built and no test was run.
- **NOT IMPLEMENTED:** no recommendation in this document has been applied.
