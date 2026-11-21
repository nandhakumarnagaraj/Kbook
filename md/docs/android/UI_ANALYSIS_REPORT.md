# UI / Layout Analysis Report — Android App

_Date: 2026-11-02 · Scope: `apps/android` (KhanaBookLite, package `com.khanabook.lite.pos`) · Method: static source audit_
_No code was changed to produce this report._

---

## 1. Executive summary

The Android app is **Jetpack Compose + Kotlin + Material 3**, and it already has a real design
system rather than scattered hardcoded values: `core/theme/Spacing.kt`, `Responsive.kt`,
`Type.kt`, `Shape.kt`, and a token-driven component library in `core/designsystem`.

Against the four constraints in the brief:

| Constraint | Verdict | Evidence |
|---|---|---|
| 8dp grid for spacing | **Mostly met** | 676 raw `dp` literals vs 187 token references; the most-used values are 16/8/4/12/24. Off-grid values (6/10/14/18/22/28/34/36/44) account for ~120 sites |
| sp typography mapped to M3 tokens | **Met, with a few escapes** | 650 `MaterialTheme.typography.*` references vs 11 hardcoded `fontSize =` in 5 files; full M3 scale defined in `Type.kt` |
| 48dp minimum touch targets | **One real violation** | `topBarIconButtonSize = 44.dp` on compact phones (`Responsive.kt:145`); no `minimumInteractiveComponentSize` usage anywhere |
| Graceful use of wide screens | **Met for content, not for navigation** | `maxContentWidth` used in 29 places; nav type is hardcoded to `NavigationBar` at every size (`MainScreen.kt:77`) |

The biggest structural observation: **the app adapts its content but not its navigation**.
The biggest hygiene issue: **raw `dp` literals still outnumber design tokens ~3.6:1**.

---

## 2. Stack and architecture snapshot

- **UI:** 100% Jetpack Compose, Material 3. No XML layouts.
- **Structure:** feature modules under `feature/*` (`auth`, `billing`, `menu`, `reports`,
  `settings`, `payments`, `printing`, `sync`, `notifications`, `onboarding`), with shared
  `core/designsystem`, `core/theme`, `core/navigation`, `core/database`.
- **DI:** Hilt (`@HiltViewModel`). **Persistence:** Room with per-tenant DAO scoping.
- **Navigation:** type-safe-ish route builders in `core/navigation/Routes.kt` (18 destinations,
  all registered in `AppNavGraph.kt`).
- **Design tokens:** `Spacing` (4/8/12/16/20/24/32), `IconSize` (18/24/32),
  `ResponsiveLayout` (window-size-class driven), M3 `Typography` with Poppins via
  downloadable Google Fonts.

This is a mature foundation. The findings below are refinements, not rewrites.

---

## 3. Spacing — 8dp grid

**Measured:** 676 raw `dp` literals, 187 `KhanaBookTheme.*` token references.

Most frequent raw values:

| Value | Count | On grid? |
|---|---|---|
| 1.dp / 2.dp / 3.dp | 102 / 57 / 9 | Borders and dividers — legitimate |
| 16.dp | 54 | Yes |
| 8.dp | 35 | Yes |
| 4.dp | 34 | Yes |
| 12.dp | 31 | Yes (spacing token) |
| 24.dp | 25 | Yes |
| 20.dp | 22 | Yes (mediumLarge token) |
| **14.dp** | **21** | **No** |
| **10.dp** | **21** | **No** |
| **6.dp** | **20** | **No** |
| 40.dp / 48.dp / 56.dp | 19 / 15 / 14 | Yes (touch/button sizes) |
| **28.dp**, **18.dp**, **36.dp**, **34.dp**, **22.dp**, **44.dp** | 15 / 14 / 13 / 8 / 8 / 11 | **No** |

Interpretation: the *rhythm* of the app is on-grid, but roughly **120 off-grid values** creep in
where a token already exists (`14.dp` where `12` or `16` was meant, `6.dp` where `4` or `8`).
These are not systematic errors; they are local drift.

**Files carrying the most raw `dp`** (excluding the token definition files themselves):

| File | Raw `dp` |
|---|---|
| `feature/billing/ui/MenuSelectionStep.kt` | 51 |
| `feature/menu/ui/ReviewDetectedItemsScreen.kt` | 29 |
| `core/designsystem/Shimmer.kt` | 29 |
| `feature/payments/ui/PaymentConfigSection.kt` | 15 |
| `feature/reports/ui/ReportViews.kt` | 14 |
| `feature/payments/ui/EasebuzzOnboardingScreen.kt` | 14 |
| `feature/menu/ui/ItemEditDialog.kt` | 13 |
| `core/components/CustomDateRangePickerDialog.kt` | 13 |

`Responsive.kt` (91) and `Spacing.kt` (34) are token *definitions*; raw values there are correct.

---

## 4. Typography

**Measured:** 650 `MaterialTheme.typography.*` references, 11 hardcoded `fontSize =` outside
`core/theme`, and 20 raw `sp` literals outside `Type.kt` (most of those are adaptive
`TextUnit` tokens inside `Responsive.kt`, which is the right place for them).

`Type.kt` defines the complete M3 scale (display → label) with Poppins and explicit
`lineHeight`, so text is token-driven by default.

**The 5 files that escape the type scale:**

- `core/designsystem/KhanaBookNotificationPanel.kt`
- `feature/billing/ui/OrderTableComponents.kt`
- `feature/menu/ui/ModeSelectionView.kt`
- `feature/reports/ui/ReportViews.kt`
- `ui/screens/MainScreen.kt`

Table headers/cells use adaptive `TextUnit` tokens (`tableHeaderFontSize`, `tableCellFontSize`
in `Responsive.kt`), which is a deliberate and reasonable exception for dense tables.

**Font-scale risk:** 66 `maxLines = 1` sites across the UI. Combined with fixed-height cards
and dense order tables, a user at 200% font scale is likely to see truncation in the orders
table, stat tiles and nav labels. Nothing in the app clamps or measures for this today.

---

## 5. Touch targets

Android's official guidance is **48dp x 48dp minimum** for any interactive element, and Compose
will expand a too-small clickable's touch area beyond its bounds — which risks overlapping
neighbouring targets ([Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps),
[API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)).
The docs also state that a `size` modifier placed *before* `minimumInteractiveComponentSize()`
limits the space it can reserve, so an explicit size wins over the built-in padding.

- 41 `IconButton` instances; `minimumInteractiveComponentSize()` is **never** called explicitly,
  so each target depends on the component's own sizing.
- No `Modifier.clickable` was found paired with a sub-48dp `size(...)` — rows and cards are
  full-width and comfortably tappable.
- **Concrete issue:** `Responsive.kt:145` sizes top-bar icon buttons at **44.dp on compact
  phones** (`CompactPhone -> 44.dp`; 48/50/60 on larger tiers). Because the size modifier is
  applied to `IconButton` before its internal `minimumInteractiveComponentSize()`, the button
  lays out at 44dp instead of the recommended 48dp. Compose's implicit touch expansion still
  makes the *effective* hit area larger, but that expansion can overlap the adjacent action in a
  dense top bar — the docs' own reason for asking for a real 48dp layout size. **This is a
  below-guideline size, not a completely dead tap.**
- Other sub-48 `size(...)` values (16/18/20/24/28/36/40) are icons, thumbnails and avatars
  inside larger clickable rows, so they do not reduce the effective target.

---

## 6. Screen utilisation and adaptivity

**What works well**

- `Responsive.kt` classifies by `WindowSizeClass` plus a `typeScaleTier` and height tier.
- `maxContentWidth` caps content at 560dp (medium) / 720dp (expanded), actively used in
  **29 places** (`ScrollableCenteredLayout`, `StickyBottomScaffold`, all list screens).
  This is exactly the "no awkward edge-to-edge stretching" requirement, already solved.
- Dialogs scale by width class: `dialogWidthFraction` 0.96 → 0.92 → 0.82 → 0.68 with a
  `dialogMaxWidth` cap; the date-range picker caps height at 92% of screen height.
- `menuGridColumns` steps 1 → 2 → 3; `homeActionColumns` becomes 2 in landscape.
- `isWideListDetail = screenWidthDp >= 840` enables list/detail layouts (5 usages).

**Gaps**

1. **Navigation never adapts.** `MainScreen.kt:77` hardcodes
   `NavigationSuiteType.NavigationBar`, and `Responsive.kt:218` hardcodes
   `useBottomNavigation = true`. A 10" tablet or foldable still gets a bottom bar, wasting the
   width Material 3 would give to a navigation rail.
2. **Ad-hoc adaptation in one hot screen.** `MenuSelectionStep.kt` uses `BoxWithConstraints`
   directly (two places) rather than the shared `ResponsiveLayout` tokens — the only screen
   that bypasses the system, and it is also the largest raw-`dp` offender (51).
3. **Wide layout is under-used.** `isWideListDetail` appears only 5 times; most list screens
   simply centre a capped column. That is acceptable, but a POS benefits more from
   menu-and-cart side-by-side on tablets than from a narrow centred column.

---

## 7. Other observations

- **Font portability:** Poppins loads through Google Fonts; on non-GMS devices it silently
  falls back to platform sans-serif. This is documented in `docs/android/DEVICE_COVERAGE_AUDIT.md`
  and is a deliberate trade-off, not a bug.
- **Theme duplication:** `ReportsScreen.kt` imports `core.theme.*` and `core.designsystem.*`
  three times each (redundant imports from repeated edits).
- **Navigation invariants:** staff management is correctly absent from Android
  (`feature/staff` has no management screen), matching the documented Web-Admin-only rule.

---

## 8. Prioritised recommendations

| # | Action | Effort | Impact |
|---|---|---|---|
| 1 | Raise `topBarIconButtonSize` compact tier from 44dp to 48dp | One line | Fixes the only real 48dp violation |
| 2 | Replace `NavigationSuiteType.NavigationBar` with a width-class decision (rail at ≥600dp) and derive `useBottomNavigation` from it | ~10 lines + visual check | Real tablet/foldable adaptation |
| 3 | Normalise off-grid literals to the nearest token (14→16/12, 6→8/4, 28→24/32, 44→48) in the 8 files listed in §3 | Mechanical | Enforces the grid the tokens already describe |
| 4 | Convert the 5 files' raw `fontSize` to `MaterialTheme.typography` roles (keep the table tokens) | Small | Consistency, automatic M3 scaling |
| 5 | Audit the 66 `maxLines = 1` sites at 200% font scale, starting with the orders table and nav labels | Medium | Prevents clipping on large-font devices |
| 6 | Move `MenuSelectionStep`'s `BoxWithConstraints` logic into `ResponsiveLayout` | Medium | One screen instead of two layout systems |
| 7 | Remove duplicate imports in `ReportsScreen.kt` | Trivial | Hygiene |

---

## 9. Standards references

Checked against the vendor documentation on 2026-11-02:

| Rule used in this report | Source |
|---|---|
| Interactive elements need a 48dp x 48dp focusable area; Compose expands smaller clickables' touch targets and can overlap neighbours; `minimumInteractiveComponentSize()` reserves 48dp of layout space but must come *before* any size modifier | [Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps), [API defaults — Minimum touch target sizes](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) |
| Text must be sized in `sp`; `lineHeight` in `sp`; never use `sp` for padding or fixed heights, because scaling is non-linear | [Android 14 features — Use scaled pixel (sp) units for text-sizes](https://developer.android.com/about/versions/14/features) |
| Test at the maximum font size (200%) and expect clipping if layouts assume fixed heights | [Android 14 features — Non-linear font scaling to 200%](https://developer.android.com/about/versions/14/features) |
| Material 3 recommends considering 48 x 48dp touch targets (~9mm physical) | [M3 accessibility — structure](https://m3.material.io/foundations/designing/structure) |

What this check changed in the report: the 44dp top-bar button was originally called a plain
violation; it is more precisely a **below-guideline layout size whose touch target is still
expanded implicitly**, with a real overlap risk in the top bar. The `sp`-for-text and
200%-font-scale findings were confirmed, and the report now also enforces the docs' rule that
`lineHeight` must be `sp` (which `Type.kt` already does).

## 10. Limitations

- This is a **static** audit: counts come from source, not from a running app. No screenshots,
  no RTL check, no large-font run, no tablet run were performed.
- Counts include graphics and border values; they measure drift, not user-visible defects,
  except where a specific line is cited (§5).
- Accessibility beyond touch-target size (content descriptions, contrast, semantics) was not
  assessed here and would need a separate pass.
