# Android UI Conformance Audit

**Date:** 2026-09-24
**Scope:** Android Compose screens and the governing UI/responsive documentation
**Method:** Static source review against `ANDROID_DESIGN_REFERENCE_PACK.md`,
`docs/meta/ANDROID_UI_RULES.md`, and
`docs/design/KHANABOOK_RESPONSIVE_DESIGN_SPEC.md`. No files were changed as part
of the audit other than this report.

## Limits

This is a source-level audit, not a device-wide visual or TalkBack certification.
The current device session did not provide a validated build of the user's
expected app, so compact/expanded screenshots, large-font behavior, live
contrast rendering, touch-target measurement, and hardware-specific IME behavior
remain unverified. Contrast findings below are calculated from source palette
values and the declared dark surfaces.

## Audit health

| Dimension | Score | Evidence |
|---|---:|---|
| Accessibility | 2/4 | The shared palette is strong, but translucent gold used for small menu prices does not meet normal-text contrast on the menu surface. |
| Performance | 3/4 | Long collections use Compose lazy layouts; no runtime profiling was performed. |
| Appearance & Theming | 3/4 | Central Material 3 palette/tokens are widely used; one auth/start-frame component duplicates palette values. |
| Platform Conformance | 3/4 | Material 3, Scaffold, system insets, and Android Back patterns are present; larger-window navigation/panes remain deferred. |
| Adaptivity | 3/4 | Window tiers and responsive tokens exist and are used in major workflows; coverage is uneven and device-class behavior was not visually verified. |
| **Total** | **14/20 — Good** | Address contrast and compact-window form behavior; retain tablet items as tracked product decisions. |

## Executive summary

- **Findings:** 1 P1, 4 P2, 0 P0, 0 P3.
- The strongest foundation is the existing KhanaBook theme and responsive system: Material 3, `KhanaBookTheme.layout`, shared spacing, lazy collections, state lifecycle collection, and explicit shell insets are already used across key flows.
- Menu item price/variant metadata is rendered in `TextGold` at 60% opacity on dark cards. The source colors yield about **3.6:1** contrast on `DarkBrown2`, below the **4.5:1** WCAG AA target for small text.
- Inventory creation presents five inputs in a non-scrollable `AlertDialog`; the source does not add IME handling. This may make fields/actions difficult to reach on a compact or landscape window while the keyboard is open.
- Navigation rail and list-detail adaptation are deferred in the responsive spec. This is a known product decision that differs from Android’s larger-window recommendation, rather than an undocumented implementation accident.
- The older `UI_REDESIGN_AUDIT.md` contains legacy theme assumptions and completion notes that do not align cleanly with the current dark-only UI rules and responsive spec.

## Detailed findings

### [P1] Menu prices and metadata have low text contrast

- **Location:** `Android/app/src/main/java/com/khanabook/lite/pos/feature/menu/ui/ManualMenuView.kt:638` and `:644`; shared values in `Android/app/src/main/java/com/khanabook/lite/pos/core/theme/Color.kt:5`.
- **Category:** Accessibility / Appearance & Theming.
- **Evidence:** `TextGold` is `#D4A843`; at 60% alpha over `DarkBrown2` (`#2D1010`), the blended color is approximately 3.6:1. The menu price uses body-small and variant metadata uses label-small, both normal text sizes.
- **Impact:** Menu prices and starting-price information are harder to read for users with low vision, especially in dim restaurant environments.
- **Guideline:** WCAG 2.2 SC 1.4.3 AA requires 4.5:1 for normal text. Verify final rendered compositing against the actual card surface and any gradients.
- **Recommendation:** Use an opaque semantic text color for essential prices and supporting data; reserve lower alpha for nonessential disabled/decorative content. Check other low-alpha `TextGold` text usages across Settings, Reports, onboarding, and Sync in the same pass.
- **Suggested command:** `$impeccable harden`.

### [P2] Inventory creation dialog has no scroll or explicit IME accommodation

- **Location:** `Android/app/src/main/java/com/khanabook/lite/pos/feature/inventory/ui/InventoryScreen.kt:260` and `:272`.
- **Category:** Adaptivity / Accessibility.
- **Evidence:** `AddMaterialDialog` places five fields (name, unit, stock, threshold, cost) in a plain `Column` inside `AlertDialog`. The dialog content has no vertical scroll container or explicit IME inset handling; the screen itself uses a scrollable `LazyColumn`, which does not make the dialog content scroll.
- **Impact:** On a short window with the keyboard open, lower fields or Save/Cancel may be pushed out of reach. This is a source-level risk; exact clipping depends on the device/window and must be confirmed visually.
- **Guideline:** Android system-bar/IME inset guidance requires interactive content to remain reachable; project rules call for scroll and IME handling on form screens.
- **Recommendation:** Make the dialog content independently scrollable and preserve access to its actions with the IME visible, following an existing KhanaBook dialog/form pattern.
- **Suggested command:** `$impeccable adapt`.

### [P2] Tablet navigation and multi-pane coverage is intentionally incomplete

- **Location:** `Android/app/src/main/java/com/khanabook/lite/pos/core/theme/Responsive.kt:161-166`; `docs/design/KHANABOOK_RESPONSIVE_DESIGN_SPEC.md:175-176` and `:347-352`; app shell in `Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/MainScreen.kt:154`.
- **Category:** Adaptivity / Platform Conformance.
- **Evidence:** `useBottomNavigation` is always `true`; the spec explicitly defers NavigationRail to V2 and labels wide list-detail support as future. New Bill already uses `isWideListDetail`, but Orders and other list/detail candidates are not universally split into panes.
- **Impact:** On expanded windows, persistent bottom navigation and single-pane record workflows can use space less efficiently than Android’s adaptive navigation/list-detail patterns.
- **Guideline:** Android adaptive navigation recommends choosing navigation presentation by window size; canonical list-detail is appropriate where a selected record has substantial related detail.
- **Recommendation:** Keep this as a tracked product decision. For the next tablet pass, evaluate the app shell rail and Orders/Active Orders detail panes at the existing 840dp threshold; do not force a split where detail is too small or not useful.
- **Suggested command:** `$impeccable adapt`.

### [P2] Start-frame colors duplicate the shared theme palette

- **Location:** `Android/app/src/main/java/com/khanabook/lite/pos/feature/auth/ui/BrandedStartFrame.kt:28-34`.
- **Category:** Appearance & Theming / Platform Conformance.
- **Evidence:** The component declares private `DarkBg`, `DarkBgMid`, `DarkBgWarm`, `GoldPrimary`, `TextWhite`, and `TextMuted` values. Several duplicate or closely approximate values already represented by `DarkBrown1`, `DarkBrown2`, `PrimaryGold`, and `TextLight` in the shared theme.
- **Impact:** Later palette adjustments can leave onboarding/start surfaces visually inconsistent with the rest of the app; duplicated values bypass the project’s token-preservation rule.
- **Recommendation:** Reconcile these values with the shared tokens where they are intended to match. Retain a distinct value only if the screen has an explicit approved design requirement.
- **Suggested command:** `$impeccable document` or `$impeccable polish`.

### [P2] Legacy redesign audit conflicts with current UI governance

- **Location:** `docs/android/UI_REDESIGN_AUDIT.md:1-12`, `:53-63`; compare `docs/meta/AGENTS.md` Android UI conventions and `docs/design/KHANABOOK_RESPONSIVE_DESIGN_SPEC.md`.
- **Category:** Documentation integrity.
- **Evidence:** The older audit refers to a light-and-dark redesign and `Kb*` theme tokens, while current project guidance defines a dark-only `KhanaBookTheme` with `DarkBrown*`/gold tokens. The responsive spec separately records NavigationRail and list-detail as future work.
- **Impact:** Agents may follow stale theme names or treat old mockup gaps as current requirements, causing unrelated redesigns or duplicate styling.
- **Recommendation:** Mark the file as historical or update it to identify which findings and phases remain current. Keep the responsive spec as the source for current device behavior decisions.
- **Suggested command:** `$impeccable document`.

## Screen-family review

| Screen family | Source review | Status against the reference pack |
|---|---|---|
| App shell and top-level tabs | `MainScreen.kt`, `Theme.kt`, `Responsive.kt` | Material 3 `Scaffold`, explicit status/navigation insets, responsive width tiers, and navigation bar are present. Rail is deferred by the written V1 decision. |
| Home and Reports | `HomeScreen.kt`, `HomeComponents.kt`, `ReportsScreen.kt`, `ReportViews.kt` | Home uses content-width and spacing tokens; report rows use lazy collections. No general list-detail report flow is established; on-device expanded-width fit is unverified. |
| New Bill and payment | `NewBillScreen.kt`, `MenuSelectionStep.kt`, `CartStep.kt`, `PaymentStep.kt` | New Bill responds to `isWideListDetail`; menu uses a responsive column count; sticky actions and bring-into-view support appear in payment entry. This is the strongest documented adaptive workflow. |
| Orders and Active Orders | `OrdersScreen.kt`, `ActiveOrdersScreen.kt`, `ActiveOrderScreen.kt`, `ActiveOrderDetailScreen.kt` | Lazy lists, status filters, back callbacks, and sticky detail actions are present. Orders uses a detail dialog; expanded-window list-detail behavior is a candidate for the tracked tablet pass. |
| Menu configuration and OCR | `MenuConfigurationScreen.kt`, `ManualMenuView.kt`, `OcrScannerScreen.kt`, `ReviewDetectedItemsScreen.kt` | Category/item collections are lazy; category navigation is horizontal; OCR review uses lazy rows/lists and bring-into-view. Key visible menu price contrast needs correction. |
| Authentication and app lock | Login, Sign Up, App Lock, Change Password, `AuthFormContainer.kt` | Shared branded form container provides vertical scrolling and system/IME insets; flows use lifecycle-aware state collection. Large-font visual behavior was not device-verified. |
| Settings and configuration | Settings home, shop/tax/payment/printer, support, quick start | Settings uses width and form-layout tokens in main sections; configuration forms generally scroll and several use `imePadding` or sticky scaffolds. Some low-alpha secondary text requires contrast review. |
| Inventory and staff | `InventoryScreen.kt`, `StaffPermissionScreen.kt` | Main collections are lazy and actions have Material controls. Inventory’s multi-field add dialog is the clearest uncovered IME/scroll risk. |
| Sync and notifications | `InitialSyncScreen.kt`, `SyncCenterView.kt`, `NotificationsScreen.kt`, notification banner/panel | Centered/scrollable layouts and lazy notifications are used. Source scan shows lower-opacity secondary copy; essential status text should be included in the contrast pass. |
| Printer and reprint | `PrinterConfigSection.kt`, `PrinterTargetCard.kt`, `ReprintKdsScreen.kt` | Existing settings and screen patterns are used; scroll and status-bar handling are present in the main surfaces. Hardware-specific dialog and printer-flow behavior is outside this source-only audit. |
| Merchant/payment onboarding | Easebuzz onboarding, Compliance Documents, Merchant Agreement, Payment Link | Multi-step onboarding uses sticky bottom scaffolds and IME handling; compliance content scrolls. Merchant Agreement contains a fixed 180dp preview region, which should be checked in landscape/short-height windows. |

## System-level positives

- The app uses a single dark Material 3 scheme and named semantic/brand colors rather than an unthemed collection of raw values.
- `ResponsiveLayout` derives compact/medium/expanded tiers from window size and centralizes content width, padding, dialog width, and menu column decisions.
- `MainScreen` explicitly opts into controlled insets and applies status/navigation protection; the reusable `StickyBottomScaffold` handles navigation-bar and IME padding.
- Major data collections use `LazyColumn`, `LazyRow`, or `LazyVerticalGrid`, commonly with stable keys.
- Screens collect ViewModel flows with `collectAsStateWithLifecycle()` and use Material controls for primary interactions.
- The New Bill flow already demonstrates a conditional wider-window composition without requiring the whole application to become multi-pane.

## Recommended order

1. **[P1] `$impeccable harden`** — review all low-alpha small text on dark surfaces; prioritize menu prices, amounts, labels, and operational status.
2. **[P2] `$impeccable adapt`** — make Inventory’s add-material dialog scrollable and IME-safe; capture compact phone and short-height behavior.
3. **[P2] `$impeccable adapt`** — revisit NavigationRail and selected-order panes using the existing responsive breakpoints and the written V2 decision.
4. **[P2] `$impeccable document`** — reconcile the historical redesign audit with current theme and responsive documentation.
5. **`$impeccable polish`** — run after any remediation pass.

For a reliable visual closeout, capture the actual expected app on at least one
phone and one tablet, then check a large font scale, dark theme, landscape, and
the keyboard-open states for inventory and billing forms.
