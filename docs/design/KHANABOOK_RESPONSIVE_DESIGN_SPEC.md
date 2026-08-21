# KhanaBook Responsive Design Specification

**Version:** 1.0
**Date:** 2026-08-21
**Status:** Canonical — single source of truth for all responsive layout decisions

---

## 1. Overview

KhanaBook targets Android phones (360–430dp wide) as the primary POS device, with
tablets (600–840dp) as a secondary form factor. The responsive system adapts layout,
typography, spacing, and hero sizing to ensure usability across all supported devices
without runtime feature flags or separate layouts.

### Target Devices

| Profile | Width × Height | Tier | Example |
|---------|---------------|------|---------|
| Small phone | 360 × 640 dp | CompactPhone | Galaxy A03, Redmi A1 |
| Standard phone | 412 × 915 dp | MediumPhone | Pixel 7, OnePlus Nord |
| Large phone | 430 × 932 dp | LargePhone | iPhone 15 Pro Max (equivalent) |
| Tablet | 800 × 1280 dp | Tablet | Lenovo Tab M10, Galaxy Tab A8 |

### Design Principles

1. **CTA visibility** — The primary action button is always visible without scrolling on any transactional screen.
2. **Content-first** — Layout adapts by adjusting spacing and hero sizing, never by hiding content.
3. **Single codebase** — No per-device layouts; everything resolves from responsive tokens at runtime.
4. **Frozen palette** — Colors, typography scale, shapes, and spacing scale are locked (see `DESIGN_SYSTEM_FREEZE.md`). Only layout geometry adapts.

---

## 2. Window Classification

### Width Tiers (`WindowWidthTier`)

| Tier | Range | Behavior |
|------|-------|----------|
| Compact | < 600dp | Full-width content, single-column, no content cap |
| Medium | 600–839dp | Content capped at 560dp (`maxContentWidth`) |
| Expanded | ≥ 840dp | Content capped at 720dp, list-detail possible |

### Height Breakpoints

| Condition | Threshold | Impact |
|-----------|-----------|--------|
| `isCompactHeight` | < 640dp | Reduced hero sizes, tighter section spacing |
| `compactHomeHeight` | ≤ 640dp | Home screen trims to fit 5 actions without scrolling |
| `isTallScreen` | ≥ 800dp | Extra padding in cards, larger heroes |

### Typography Tier (`TypeScaleTier`)

Resolved from current window dimensions (not device model):

| Tier | Condition | Impact |
|------|-----------|--------|
| CompactPhone | height < 640 OR width < 390 | Tightest card padding, smallest icon containers |
| MediumPhone | Default (neither compact nor large/tablet) | Standard spacing |
| LargePhone | width ≥ 420 AND height ≥ 880 | Slightly larger card padding, icon containers |
| Tablet | width ≥ 600 AND height ≥ 700 | Maximum card padding, largest icon containers |

### Orientation

| Condition | Threshold | Impact |
|-----------|-----------|--------|
| `isLandscape` | width > height | Home action cards switch to 2-column grid |

---

## 3. Layout Primitives

All live in `ui/designsystem/`. Screens compose these primitives rather than
implementing layout from scratch.

### 3.1 StickyBottomScaffold

**Purpose:** Any screen with a persistent primary action button (transactional flows).

```kotlin
StickyBottomScaffold(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit,       // Always visible above nav/IME
    bottomBarContainerColor: Color = MaterialTheme.colorScheme.surface,
    bottomBarTonalElevation: Dp = 2.dp,
    bottomBarBorder: BorderStroke? = ...,
    content: @Composable BoxScope.() -> Unit  // Screen owns scroll
)
```

**Manages:** Header/content/footer slot positioning; `navigationBarsPadding()` + `imePadding()` on bottom bar.
**Does NOT manage:** Scroll, theme colors.

**Usage rule:** The screen inside `content` decides its own scroll mechanism (Column+verticalScroll, LazyColumn, Pager, etc.). The scaffold never adds scroll.

### 3.2 ScrollableCenteredLayout

**Purpose:** Success, error, empty, sync states — static centered content that must never clip.

```kotlin
ScrollableCenteredLayout(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

**Manages:** Guaranteed vertical scroll (centered), optional sticky bottom buttons, insets.
**Does NOT manage:** Theme colors.

**Usage rule:** Only for static message screens. Never put a LazyColumn inside this.

### 3.3 ListLayout

**Purpose:** Filterable list screens (orders, active orders, search results).

```kotlin
ListLayout(
    modifier: Modifier = Modifier,
    filterBar: (@Composable () -> Unit)? = null,
    isEmpty: Boolean = false,
    emptyState: @Composable () -> Unit = {},
    content: @Composable () -> Unit
)
```

**Manages:** Filter bar pinned above list; crossfade between empty/content.
**Does NOT manage:** Scroll (screen provides its own LazyColumn), bottom insets.

---

## 4. Responsive Tokens

Accessed via `KhanaBookTheme.layout.*`. Resolved once at theme root from `LocalConfiguration`.

### 4.1 Hero Element Sizing

| Token | Compact Height (<640dp) | Normal | Tall (>800dp) |
|-------|------------------------|--------|----------------|
| `heroImageSize` | min(h×0.18, 100)dp | min(h×0.22, 140)dp | 160dp |
| `qrCodeSize` | min(w×0.4, 160)dp | min(w×0.5, 200)dp | min(w×0.35, 220)dp |
| `logoSize` | 80dp | 100dp | 120dp |

**Rule:** No raw dp > 80dp for visual/hero elements. Always use these tokens.

### 4.2 Spacing & Layout

| Token | Compact | Medium | Expanded | Notes |
|-------|---------|--------|----------|-------|
| `contentPadding` | 16dp | 20dp | 24dp | Horizontal content margins |
| `sectionSpacing` | 8dp (<640h) | 16dp | 24dp (>800h) | Vertical rhythm between sections |
| `maxContentWidth` | Unspecified (fill) | 560dp | 720dp | Prevents over-stretching on tablets |
| `dialogWidthFraction` | 0.92 | 0.82 | 0.68 | Dialog width relative to screen |
| `dialogMaxWidth` | 420dp | 600dp | 680dp | Hard cap on dialog width |

### 4.3 Card & Touch Target Sizing

| Token | CompactPhone | MediumPhone | LargePhone | Tablet |
|-------|-------------|-------------|------------|--------|
| `cardPaddingHorizontal` | 14dp | 16dp | 18dp | 20dp |
| `cardPaddingVertical` | 10dp (+4 tall) | 12dp (+4 tall) | 14dp (+4 tall) | 16dp (+4 tall) |
| `primaryCardVertical` | 20dp (+8 tall) | 24dp (+8 tall) | 28dp (+8 tall) | 48dp (+8 tall) |
| `actionIconContainerSize` | 40dp | 44dp | 48dp | 52dp |
| `actionIconSize` | 20dp | 22dp | 24dp | 26dp |
| `primaryIconContainerSize` | 44dp | 48dp | 52dp | 56dp |
| `primaryIconSize` | 24dp | 24dp | 26dp | 28dp |

### 4.4 Grid & Navigation

| Token | Value | Notes |
|-------|-------|-------|
| `menuGridColumns` | 1 (<600w), 2 (600–839w), 3 (≥840w) | Menu item grid |
| `homeActionColumns` | 1 (portrait), 2 (landscape) | Home quick-action cards |
| `useBottomNavigation` | true (all sizes) | V1 decision; NavigationRail deferred to V2 |
| `isWideListDetail` | true if width ≥ 840dp | Enables list-detail split (future) |

---

## 5. Typography System

Four fixed `Typography` variants are resolved from `TypeScaleTier`:

| Tier | Body font size | Heading adjustment | Usage |
|------|---------------|-------------------|-------|
| CompactPhone | Standard M3 | Slightly reduced display/headline | Budget phones, small viewports |
| MediumPhone | Standard M3 | Standard | Default for most phones |
| LargePhone | Standard M3 | Slightly larger labels | Flagships with large viewports |
| Tablet | Standard M3 | Larger across the board | Tablets |

Typography is frozen (Poppins font, M3 scale). See `Type.kt` for exact values.

---

## 6. Spacing Scale (Fixed)

From `Spacing.kt` — **locked, do not add new values**:

| Token | Value | Usage |
|-------|-------|-------|
| `hairline` | 2dp | Minimal divider spacing |
| `extraSmall` | 4dp | Tight element spacing |
| `small` | 8dp | Compact gaps |
| `smallMedium` | 12dp | Bottom bar internal padding |
| `medium` | 16dp | Standard gaps |
| `mediumLarge` | 20dp | Generous gaps |
| `large` | 24dp | Section separators |
| `extraLarge` | 32dp | Major sections |
| `huge` | 48dp | Hero spacing |
| `extraHuge` | 64dp | Screen-level top padding |
| `screenContentPadding` | 16dp | Legacy compat (prefer `layout.contentPadding`) |
| `bottomListPadding` | 88dp | Below last list item (clears FAB/nav) |

### Touch Targets

| Token | Value |
|-------|-------|
| `buttonHeightCompact` | 48dp |
| `buttonHeight` | 52dp |
| `buttonHeightLarge` | 56dp |
| `inputHeight` | 56dp |

All interactive controls meet the 48dp minimum touch target requirement.

---

## 7. Screen Migration Map

### Screens Using Layout Primitives

| Screen | Primitive | Status |
|--------|-----------|--------|
| PaymentStep | StickyBottomScaffold | ✅ Migrated |
| CartStep | StickyBottomScaffold | ✅ Migrated |
| ActiveOrderScreen (detail) | StickyBottomScaffold | ✅ Migrated |
| SuccessStep (OrderConfirmationSection) | ScrollableCenteredLayout | ✅ Migrated |
| FailedStep | ScrollableCenteredLayout | ✅ Migrated |
| InitialSyncScreen | ScrollableCenteredLayout | ✅ Migrated |
| ActiveOrdersScreen (list) | ListLayout | ✅ Migrated |
| OrdersScreen | ListLayout | ✅ Migrated |

### Screens Using Adaptive Tokens (No Scaffold Migration Needed)

| Screen | Pattern | Status |
|--------|---------|--------|
| HomeScreen | Custom Column + responsive sectionSpacing + typeScaleTier cards | ✅ Responsive |
| LoginScreen | Box + gradient + adaptive tokens | ✅ Responsive |
| SignUpScreen | Box + gradient + adaptive tokens | ✅ Responsive |
| SplashScreen | Box + gradient + adaptive logoSize | ✅ Responsive |
| HelpSupportView | Settings pattern + adaptive tokens | ✅ Responsive |
| MainScreen | NavigationBar + navigationBarsPadding | ✅ Correct |

### Screens Not Requiring Migration (Already Correct)

| Screen | Reason |
|--------|--------|
| MenuSelectionStep (NewBill) | Already uses LazyGrid with `menuGridColumns` |
| SearchScreen | Pattern C scaffold + LazyColumn (no sticky CTA) |
| ReportsScreen | Pattern C scaffold + custom report views (no transactional CTA) |
| SettingsScreen | KhanaBookScreenScaffold + verticalScroll (no transactional CTA) |
| MenuConfigurationScreen | Pattern C scaffold + sub-views (no transactional CTA in parent) |
| NotificationsScreen | List pattern (no sticky CTA needed) |
| CallCustomerScreen | Pattern C scaffold (form + inline CTA appropriate) |

### Screens Requiring Review (Potential Gaps)

| Screen | Concern | Recommended Action |
|--------|---------|-------------------|
| ~~QuickStartScreen~~ | ~~Form with "Save" CTA inside scroll~~ | ✅ Migrated to StickyBottomScaffold |
| ~~EasebuzzOnboardingScreen~~ | ~~Multi-step form with CTA in scroll~~ | ✅ BusinessDetailsStep + BankDetailsStep migrated to StickyBottomScaffold |
| ~~PaymentLinkScreen~~ | ~~Payment action inside scroll content~~ | ✅ Migrated to StickyBottomScaffold (also fixed double padding bug) |
| ~~EasebuzzPaymentScreen~~ | ~~Payment flow with primary action~~ | ✅ PaymentResultContent now has verticalScroll |
| ~~RoleAccessScreen~~ | ~~Settings form with save action~~ | ✅ Migrated to ScrollableCenteredLayout |
| StaffPermissionScreen | Permission list with inline actions | No change needed (correct as-is) |

---

## 8. Accessibility Integration

### Font Scale Safety

- All button labels in sticky bottom bars: `maxLines = 1` + `TextOverflow.Ellipsis`
- Interactive controls: minimum 48dp touch target maintained via spacing tokens
- Large font (1.3×–2.0×): tokens absorb extra space; buttons remain tappable

### Keyboard (IME) Handling

- `StickyBottomScaffold` applies `imePadding()` to bottom bar — CTA floats above keyboard
- Form fields use `BringIntoViewRequester` for scroll-to-field behavior
- Split-amount fields in PaymentStep remain accessible when keyboard open

### Screen Reader

- Focus order: top→bottom (enforced by Column composition order)
- Semantic button labels: announced by TalkBack
- Crossfade in ListLayout: empty state vs content properly announced

---

## 9. Performance Constraints

- Layout primitives are **stateless** — no internal `remember`, no recomposition triggers
- `ResponsiveLayout` is an **immutable data class** — computed once at theme root, never changes mid-composition
- `staticCompositionLocalOf` used for layout/spacing/typography — no propagation overhead
- No new dependencies introduced by the responsive system

---

## 10. Validation Checklist

### Build Validation

- [x] `./gradlew assembleDebug` — 0 errors
- [x] `./gradlew lint` — 0 responsive-related warnings (only pre-existing typo/locale warnings)
- [x] Unit tests pass (148+, 0 failures)

### Device Profile Validation

| Configuration | Verify |
|---------------|--------|
| **360×640** (small phone) | Primary CTAs visible without scrolling; no content clipping |
| **412×915** (standard phone) | No excessive whitespace; proper section spacing |
| **800×1280** (tablet) | `maxContentWidth` respected; QR/logo sized by tokens; no stretching |
| 640×360 (landscape) | All content reachable via scroll; 2-column home actions |
| Font scale 1.3× | No overlapping text; buttons usable |
| Font scale 2.0× | Labels ellipsis instead of clip; CTAs remain tappable |
| Keyboard open (PaymentStep) | Fields scroll into view; CTA stays above keyboard |

---

## 11. Governance Rules

1. **No new layout scaffolds** without team review. Prefer composing existing primitives.
2. **No raw dp > 80dp** for sizing visual elements — always use responsive tokens.
3. **Scaffolds never manage scroll** — the screen decides its scroll strategy.
4. **Scaffolds never hardcode colors** — parameters default to MaterialTheme tokens.
5. **New screens must classify themselves** before implementation:
   - Transaction → StickyBottomScaffold
   - List → ListLayout
   - Success/Error/Sync/Empty → ScrollableCenteredLayout
   - Dashboard/Settings → Custom Column or KhanaBookScreenScaffold
6. **maxContentWidth** must be applied on expanded-width devices to prevent over-stretching.
7. **Responsive tokens are read-only** at screen level — never override locally.

---

## 12. Future Considerations (Deferred to V2)

- **NavigationRail** for expanded-width devices (currently using bottom nav everywhere)
- **List-detail split** on `isWideListDetail` (≥840dp) devices
- **Foldable awareness** — inner/outer display adaptation
- **ChromeOS/DeX** — free-form window resizing

---

## Related Documents

- `docs/design/LayoutGuidelines.md` — Layout rule enforcement for developers/agents
- `docs/design/ResponsiveLayoutMigration.md` — Migration history and primitive usage examples
- `docs/design/DESIGN_SYSTEM_FREEZE.md` — Locked palette, typography, shapes, spacing
- `docs/meta/ANDROID_UI_RULES.md` — Existing UI pattern preservation rules
- `Android/.../ui/theme/Responsive.kt` — Token implementation
- `Android/.../ui/designsystem/StickyBottomScaffold.kt` — Scaffold implementation
- `Android/.../ui/designsystem/ScrollableCenteredLayout.kt` — Centered layout implementation
- `Android/.../ui/designsystem/ListLayout.kt` — List layout implementation
