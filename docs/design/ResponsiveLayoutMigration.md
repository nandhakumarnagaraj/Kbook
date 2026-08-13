# KhanaBook Responsive Layout Migration

Status: **Implemented** — validation in progress (2026-08)

## Why this migration happened

A responsive-architecture audit found that KhanaBook screens used 6+ different
layout strategies with no shared scaffolding. The concrete failures:

- The **Confirm Payment** button (the most critical POS tap target) was buried
  inside scrollable content and unreachable without scrolling on phones with
  viewport height <640dp when the keyboard was open.
- The NewBill flow changed layout architecture at every step (split-pane →
  full-scroll → centered), causing visible layout jank.
- List screens (ActiveOrders, Orders, Search) each implemented
  filter+list+empty differently.
- Hero elements (QR codes, logos, animations) were fixed dp and never adapted
  to device or font scale.
- `InitialSyncScreen` had no scroll at all — content clipped on large fonts
  and landscape.

The fix was not to replace individual dp values, but to introduce **layout
primitives** — reusable scaffolds sitting between the design-system atoms
(buttons, cards) and the feature screens.

## Layout primitives

All live in `Android/app/src/main/java/com/khanabook/lite/pos/ui/designsystem/`.

### StickyBottomScaffold

```kotlin
StickyBottomScaffold(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit,
    bottomBarContainerColor: Color = MaterialTheme.colorScheme.surface,
    bottomBarTonalElevation: Dp = 2.dp,
    bottomBarBorder: BorderStroke? = ...,
    content: @Composable BoxScope.() -> Unit
)
```

- **Manages:** header/content/footer slot positioning; nav-bar + IME insets on
  the bottom bar.
- **Deliberately does NOT manage:** scroll (screen owns it), theme colors
  (parameters default to MaterialTheme tokens).

### ScrollableCenteredLayout

```kotlin
ScrollableCenteredLayout(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
)
```

- **Manages:** guaranteed vertical scroll (centered content never clips),
  optional sticky bottom buttons.
- This is the one primitive that does own scrolling — message states are
  always static content, never LazyColumn.

### ListLayout

```kotlin
ListLayout(
    modifier: Modifier = Modifier,
    filterBar: (@Composable () -> Unit)? = null,
    isEmpty: Boolean = false,
    emptyState: @Composable () -> Unit = {},
    content: @Composable () -> Unit
)
```

- **Manages:** filter-bar positioning (pinned above list), empty↔content
  switching (crossfade).
- **Does NOT manage:** scroll — the screen provides its own LazyColumn.

## Responsive tokens

Added to `ResponsiveLayout` (`ui/theme/Responsive.kt`). Always access via
`KhanaBookTheme.layout.*`.

| Token | Compact height (<640dp) | Normal | Tall (>800dp) |
|-------|------------------------|--------|----------------|
| `heroImageSize` | `min(h*0.18, 100)` | `min(h*0.22, 140)` | `160` |
| `qrCodeSize` | `min(w*0.4, 160)` | `min(w*0.5, 200)` | `min(w*0.35, 220)` if expanded width |
| `logoSize` | `80` | `100` | `120` |
| `sectionSpacing` | `8` | `16` | `24` |
| `maxContentWidth` | fill | `560` | `720` |

Also: `isCompactHeight` threshold raised 480→640dp; new `isTallScreen`
(height >800dp).

## Migration guide

To migrate a screen to the new architecture:

1. **Classify the screen.** Transaction → `StickyBottomScaffold`;
   list → `ListLayout`; success/error/sync/empty → `ScrollableCenteredLayout`;
   dashboard/settings → existing patterns (column + scroll, or
   `KhanaBookScreenScaffold`).
2. **StickyBottomScaffold:** wrap content, move primary action button(s) into
   `bottomBar`. Keep scroll on the content (the scaffold does not add it).
3. **ListLayout:** move filter row(s) to `filterBar`, the LazyColumn to
   `content`, and the empty state to `emptyState`.
4. **ScrollableCenteredLayout:** place centered content in the lambda, action
   buttons in `bottomBar`.
5. **Replace hero dp with tokens** — `layout.qrCodeSize`, `layout.logoSize`,
   `layout.heroImageSize`. No raw dp >80dp for visual elements.
6. **Add `maxLines = 1` + `TextOverflow.Ellipsis`** to button labels — protects
   against font scale 2.0× clipping inside fixed-height buttons.
7. **Never** add scroll or colors inside the scaffolds.

## Screens migrated

| Screen | Primitive | Files |
|--------|-----------|-------|
| PaymentStep | `StickyBottomScaffold` | `newbill/PaymentStep.kt` |
| CartStep | `StickyBottomScaffold` | `newbill/CartStep.kt` |
| ActiveOrderScreen | `StickyBottomScaffold` | `ActiveOrderScreen.kt` |
| SuccessStep | `ScrollableCenteredLayout` | `newbill/OrderConfirmationSection.kt` |
| FailedStep | `ScrollableCenteredLayout` | `newbill/PaymentStep.kt` |
| InitialSyncScreen | `ScrollableCenteredLayout` | `InitialSyncScreen.kt` |
| ActiveOrdersScreen | `ListLayout` | `ActiveOrdersScreen.kt` |
| OrdersScreen | `ListLayout` | `OrdersScreen.kt` |
| Login / SignUp | adaptive tokens | `LoginScreen.kt`, `auth/SignUpScreen.kt` |
| Splash | adaptive tokens | `SplashScreen.kt` |
| Help / About | adaptive tokens | `applock/HelpSupportView.kt` |

## Correct usage examples

```kotlin
// Transaction screen — CTA always visible
StickyBottomScaffold(
    bottomBar = {
        KhanaPrimaryButton("Confirm Payment", onClick = onConfirm)
    }
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = layout.contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // content
    }
}
```

```kotlin
// List screen
ListLayout(
    filterBar = { FilterChips(...) },
    isEmpty = rows.isEmpty(),
    emptyState = { EmptyState() }
) {
    LazyColumn {
        items(rows) { row -> RowItem(row) }
    }
}
```

```kotlin
// Message state
ScrollableCenteredLayout(
    bottomBar = { KhanaPrimaryButton("Back to Home", onClick = onDone) }
) {
    SuccessBadge(modifier = Modifier.size(layout.heroImageSize))
    Text("Payment Successful!")
}
```

## Validation status

- ✅ `assembleDebug` + `lint` (0 errors)
- ✅ Unit tests: 148, 0 failures
- ✅ Static accessibility pass: `maxLines=1` added to all sticky-bar button
  labels; per-field `BringIntoViewRequester` preserved
- ✅ Static performance pass: primitives are stateless; no new recomposition
  paths
- ⏳ Device QA (budget phone 360×640, 412×915, landscape 640×360, tablet,
  font scale 1.3×/2.0×, display size large, TalkBack): requires an emulator or
  physical devices — see `LayoutGuidelines.md` and the manual QA checklist
  below
- ⏳ Compose UI tests for the primitives: added under
  `app/src/androidTest/`, require a device to execute

### Manual QA checklist

| Configuration | Verify |
|---------------|--------|
| 360×640 phone | Confirm/Continue/Settle buttons visible without scrolling |
| 412×915 phone | No excessive whitespace; content not stretched |
| 640×360 landscape | No clipping; all content reachable via scroll |
| 800×1280 tablet | Adaptive QR/logo sizing; `maxContentWidth` respected |
| Font scale 1.3× | No overlapping text |
| Font scale 2.0× | Buttons remain usable; labels ellipse instead of clip |
| Display size large | No button clipping |
| Keyboard open (PaymentStep) | Split-amount fields scroll into view; CTA stays above keyboard |
| TalkBack | Button semantics announced; focus order top→bottom |

## What intentionally did NOT change

No business logic, ViewModel, navigation, or state management changes. No new
dependencies. `MenuSelectionStep` (already correct) untouched. The layout
architecture is frozen — future screens adopt the primitives; existing screens
are only revisited if device QA uncovers issues.

See also: `docs/design/LayoutGuidelines.md` — the governance rules document
that every AI agent and developer should follow when touching screens.