# KhanaBook Layout Guidelines

## Screen Type → Layout Primitive

| Screen Type | Layout | CTA Pattern |
|-------------|--------|-------------|
| Transaction (payment, checkout, order actions) | StickyBottomScaffold | Pinned bottom bar |
| List (orders, active orders, search results) | ListLayout | No sticky CTA (actions on items) |
| Success / Error / Sync / Empty | ScrollableCenteredLayout | Optional sticky bottom buttons |
| Dashboard (home, reports) | Custom Column + verticalScroll | Inline CTAs |
| Settings sections | KhanaBookScreenScaffold or StickyBottomScaffold | Save in sticky bottom or inline |

## Rules

1. **No screen should use `fillMaxSize().verticalScroll()` with a primary action button inside the scroll if it's a transactional screen.** Use StickyBottomScaffold instead.
2. Scaffolds NEVER manage scroll — the screen decides scroll strategy (Column+verticalScroll, LazyColumn, LazyGrid, Pager).
3. Scaffolds NEVER hardcode theme colors — use parameters defaulting to MaterialTheme tokens.
4. Hero elements (QR, logos, animations) MUST use responsive tokens: `layout.heroImageSize`, `layout.qrCodeSize`, `layout.logoSize`.
5. No raw dp values > 80dp for sizing visual elements — use responsive tokens.
6. Tablet content should use `Modifier.widthIn(max = layout.maxContentWidth)` to prevent over-stretching.
7. New layout scaffolds require team review — prefer composing existing primitives over creating new ones.

## Responsive Token Reference

| Token | Compact Height (<640dp) | Normal | Tall (>800dp) |
|-------|------------------------|--------|----------------|
| heroImageSize | ~100dp | ~140dp | 160dp |
| qrCodeSize | ~144dp (40% width) | ~180dp (50% width) | ~220dp |
| logoSize | 80dp | 100dp | 120dp |
| sectionSpacing | 8dp | 16dp | 24dp |
| maxContentWidth | fill | 560dp | 720dp |

## Layout Scaffolds

### StickyBottomScaffold
- **Purpose:** Any screen with a persistent primary action button
- **Manages:** Header/content/footer slot positioning, bottom insets (nav bar + IME)
- **Does NOT manage:** Scroll behavior, theme colors
- **Content slot:** BoxScope — screen decides Column+verticalScroll, LazyColumn, Grid, etc.

### ScrollableCenteredLayout
- **Purpose:** Success, error, empty, sync states
- **Manages:** Vertical scroll (always), centering, optional sticky bottom buttons
- **Content slot:** ColumnScope — content is always static (never LazyColumn)

### ListLayout
- **Purpose:** Screens showing filterable lists of items
- **Manages:** Filter bar positioning, empty/content state switching
- **Does NOT manage:** Scroll (screen provides its own LazyColumn)

## For AI Assistants

When generating or modifying KhanaBook screens:
- Do NOT create new layout scaffolds without extending existing ones
- Do NOT put primary action buttons inside scroll on transactional screens — use StickyBottomScaffold
- Do NOT use fixed dp for hero elements — use KhanaBookTheme.layout tokens
- Do NOT hardcode colors in layout scaffolds
- DO use Arrangement.spacedBy(layout.sectionSpacing) for adaptive vertical spacing
- DO let the screen decide its scroll mechanism (never assume verticalScroll is correct)
