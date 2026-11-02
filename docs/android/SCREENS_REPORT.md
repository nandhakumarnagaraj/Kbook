# Android Screens Report

_Generated: 2026-11-02 · App: `apps/android` (KhanaBookLite) · Source: `com.khanabook.lite.pos`_

Scope: every user-visible screen reachable from the app's navigation graph, plus the
sub-screens that are composed inside a host screen (tabs, settings sections, billing
steps). Everything below was read from the source; no screen in this report is inferred.

Single sources of truth:

- Destinations — `core/navigation/Routes.kt`
- Registration — `core/navigation/AppNavGraph.kt` (18 `composable(...)` destinations)
- Tabs — `core/navigation/NavigationItems.kt` + `ui/screens/MainScreen.kt`
- Settings sections — `feature/settings/ui/SettingsScreen.kt`

---

## 1. Top-level navigation destinations (18)

| # | Route | Screen composable | Feature | Purpose |
|---|-------|-------------------|---------|---------|
| 1 | `login` | `LoginScreen` | auth | Credential sign-in; entry to sign-up |
| 2 | `signup` | `SignUpScreen` | auth | New restaurant/account registration |
| 3 | `app_lock` | `AppLockScreen` | auth | PIN/cashier lock gate, overlays the stack on resume |
| 4 | `role_access` | `RoleAccessScreen` | auth | "Role cannot use POS" wall for non-POS roles |
| 5 | `initial_sync` | `InitialSyncScreen` | sync | First-run data pull before the app is usable |
| 6 | `quick_start` | `QuickStartScreen` | settings | First-run wizard to seed menu items |
| 7 | `main/{tab}?source=&highlightBillId=&section=` | `MainScreen` | ui/screens | Bottom-tab shell (Home / Reports / Settings) |
| 8 | `new_bill?resumePayment=&draftBillId=&targetStep=&quickMode=` | `NewBillScreen` | billing | Bill/order creation wizard (5 internal steps) |
| 9 | `active_orders` | `ActiveOrdersScreen` | billing | Open draft bills and pending orders |
| 10 | `active_order_detail/{billId}` | `ActiveOrderDetailScreen` | billing | Single draft order: add items, collect payment |
| 11 | `search_bill` | `SearchScreen` | billing | Find a past invoice/bill |
| 12 | `call_customer` | `CallCustomerScreen` | billing | Dial saved customers |
| 13 | `reprint_kds` | `ReprintKdsScreen` | printing | Reprint kitchen order tickets |
| 14 | `notifications` | `NotificationsScreen` | notifications | In-app notification inbox |
| 15 | `notifications_preferences` | `NotificationPreferencesScreen` | notifications | Per-channel notification settings |
| 16 | `ocr_scanner/{source}` | `OcrScannerScreen` | menu | Camera scan: menu OCR (`source=menu_config`) or barcode (`source=billing`) |
| 17 | `easebuzz_onboarding` | `EasebuzzOnboardingScreen` | payments | Payment-gateway (Easebuzz) onboarding/KYC |
| 18 | `compliance_documents` | `ComplianceDocumentsScreen` | onboarding | FSSAI/compliance document upload and status |

### Deep links

| URI pattern | Destination |
|-------------|-------------|
| `khanabook://active_orders` | Active orders |
| `khanabook://bill/{billId}` | Active order detail |
| `khanabook://notifications` | Notifications |
| `khanabook://easebuzz_onboarding` | Easebuzz onboarding |

Notification payloads are mapped to routes in `feature/notifications/domain/NotificationRouteManager.kt`
(e.g. `kyc` → `easebuzz_onboarding`, `fssai_expiry` → `compliance_documents`).

### Start-destination branches (`core/app/MainActivity.kt`)

1. Not signed in → `login` (or `signup`)
2. Role cannot use POS → `role_access`
3. Initial sync not done → `initial_sync` → `quick_start` (if not yet completed) → main shell
4. App lock enabled → `app_lock`
5. Otherwise → authenticated main shell

---

## 2. Main shell — bottom tabs (3 visible)

`NavigationTabs` constants: `TAB_HOME = 0`, `TAB_REPORTS = 1`, `TAB_SETTINGS = 3`, `TAB_PROFILE = 3`.
`NavigationUtils.getVisibleTabs()` returns exactly three tabs:

| Tab | Index | Host screen | Notes |
|-----|-------|-------------|-------|
| Home | 0 | `HomeScreen` | Quick bill CTA, active orders, find bill, reprint KOT, call customer, notification bell, warnings (payment/sync/clock drift) |
| Reports | 1 | `ReportsScreen` | Payments Report / Orders Report, date-range picker, export |
| Settings | 3 | `SettingsScreen` | Section host (see §3); the same tab serves "Profile" |

Shell behaviour: `NavigationSuiteScaffold` with a navigation bar, horizontal swipe between
tabs (`horizontalNavigationSwipe`), back gesture returns to Home, and `showBottomBar` is
toggled by full-screen settings sub-views.

### Reports surface (`feature/reports/ui/ReportsScreen.kt` + `ReportViews.kt`)

- Toggle: **Payments Report** / **Orders Report**
- Views: `PaymentLevelView` (payment-mode totals, `PaymentModeItem`, `PartPaymentCard`) and
  `OrderLevelView` (per-order rows via `OrderRowItem`)
- Cross-cutting: `CustomDateRangePickerDialog`, `ReportDownloadBottomBar`, `OrderDetailsDialog`
- Reports can cancel a bill and settle a pending part-payment from the list.

---

## 3. Settings sub-sections (14 + menu host)

`SettingsScreen` renders one section at a time from a `section` string; the rail/route value
comes from the `main/...?section=` argument.

| Section key | Title | Content |
|-------------|-------|---------|
| `menu` (default) | Settings | Settings home list + **`MenuConfigurationScreen`** (manual menu view, OCR mode selection, scan entry) |
| `shop` | Restaurant Configuration | Shop profile / branding (`ShopConfigSection`) |
| `payment` | Payment Configuration | Gateway + Easebuzz config (`PaymentConfigSection`) |
| `printer` | Printer Configuration | Printer targets (`PrinterConfigSection`) |
| `tax` | Tax Configuration | Tax rates (`TaxConfigSection`) |
| `ui_scale` | Display | UI scale |
| `interaction_feedback` | Interaction Feedback | Haptics/sound (`InteractionFeedbackSection`) |
| `security` | App Settings | Security options |
| `app_lock` | App Lock | `AppLockConfigSection` |
| `change_password` | Change Password | `ChangePasswordView` |
| `help_support` | Help & Support | `HelpSupportView` |
| `sync_center` | Sync Center | `SyncCenterView` (queue, quarantine, force sync) |
| `about_app` | About App | Version/legal |
| `compliance_documents` | Compliance | Reuses `ComplianceDocumentsScreen` |

From `security`, the screen also navigates to `notifications_preferences`.
From `payment`, it navigates to `easebuzz_onboarding`.

---

## 4. NewBill wizard — internal steps (5)

`NewBillScreen` switches on `step` (not separate routes). Entry step depends on arguments:
normal = 1, quick mode = 2, `resumePayment` = 3, `targetStep` overrides.

| Step | Composable | Content |
|------|-----------|---------|
| 1 | `CustomerInfoStep` | Customer details, source, order type |
| 2 | `MenuSelectionStep` | Category/menu browsing, cart, search, barcode scan |
| 3 | `PaymentStep` | Payment mode, split/part payment, gateway handoff |
| 4 | `SuccessStep` | Order confirmation / receipt |
| 5 | `FailedStep` | Payment failure state |

Supporting surfaces: `CartStep`, `OrderConfirmationSection`, `PaymentSection`,
`ActiveOrderComponents`, `SearchComponents`, `OrderTableComponents`.

---

## 5. Auth and gating surfaces

- `BrandedStartFrame` — shared branded splash/frame for auth screens
- `AppLockView` / `AppLockConfigSection` — lock overlay + its configuration
- `ForgotPasswordDialog`, `SignUpComponents` — auth dialogs/fields
- `PermissionBlockedDialog`, `KhanaBookSelectionDialog`, `KhanaBookLargeDialog`,
  `KhanaBookDialog` — modal surfaces in the design system

---

## 6. Screen count

| Group | Count |
|-------|-------|
| Registered nav destinations | 18 |
| Bottom tabs | 3 |
| Settings sub-sections | 14 |
| Billing wizard steps | 5 |
| Feature entry-point screens in `feature/*/ui` | 21 `*Screen` composables |

---

## 7. Observations

1. **Tab index collision.** `TAB_SETTINGS = 3` and `TAB_PROFILE = 3` are the same value while
   `MainScreen` also special-cases `initialTab == 4`. There is no tab `2`. Nothing breaks today
   (unknown indices fall back to Home), but the constants no longer describe the tab list and
   should be derived from `NavigationUtils.allTabs`.
2. **Duplicate compliance entry point.** Compliance documents are reachable both as a nav
   destination (`compliance_documents`) and as a settings section key of the same name, which
   opens a different code path with a different back behaviour.
3. **Staff is read-only on Android (invariant holds).** `feature/staff` has only
   `data/`, `domain/`, and an `InteractionFeedbackSection` UI file — no staff management screen.
   Staff management remains Web Admin-only, as required.
4. **`NotificationsScreen` / `NotificationsScreen` channel plumbing** lives in
   `feature/notifications/NotificationChannels.kt`, with the banner overlay
   (`InAppNotificationBanner`) rendered above every destination except login/signup/app-lock.
5. **The graph is current.** `graphify update .` re-extracted 1,462 files (246 uncached) with
   no topology changes, so `graphify-out/graph.json` matches this report's source state.
