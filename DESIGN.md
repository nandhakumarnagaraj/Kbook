# KhanaBook — Design System Specification

> **Single source of truth for visual design tokens, components, and UX patterns across Android POS and Web Admin.**
> **Version:** 2.3.1 — audit-corrected against codebase ground truth (Android `KhanaBookLiteTheme`, Web `styles.css`).

---

## 1. Design Philosophy

| Principle | Application |
|-----------|-------------|
| **Utility-First, Not Generic SaaS** | Built for loud kitchens, greasy hands, flaky internet. High contrast, large touch targets, zero dark patterns. |
| **Predictable Semantic Color** | Gold/Orange = primary action (Android `PrimaryGold` `#C8960C`, Web `--kb-color-primary-strong` `#C2410C`), Green = success/active, Amber = warning/pending, Red = danger/error. No decorative gradients. |
| **Information Density with Breath** | Dense tables (cells use `padding: 0.7rem 0.85rem`; no fixed 52 px row height rule), inline action stacks (`white-space: nowrap; flex-wrap: nowrap`), no multi-line ballooning. |
| **Accessible by Default** | WCAG 2.2 AA minimum; 4.5:1 body, 3:1 large/UI; TalkBack / screen-reader labels on every interactive element. |
| **Platform-Native Feel** | Android: Material 3 dark theme + Compose. Web: Light workspace + Astryx tokens. Each uses its own token namespace. |

---

## 2. Platform Design Systems (Separate Token Namespaces)

> **Critical:** Android POS and Web Admin have **completely independent** design systems. They share only semantic meaning (success/warning/danger), not token values.

### 2.1 Android POS (Compose + Material 3 Dark)

> **Forced dark theme** — `KhanaBookLiteTheme` uses `darkColorScheme` with `DarkBrown1`/`DarkBrown2` surfaces. System bars forced to light-icons-on-dark.

| Category | Token / Value | Usage |
|----------|---------------|-------|
| **Brand Primary** | `PrimaryGold` `#C8960C` (M3 `primary`) | Primary buttons, FAB, selected tab, active navigation |
| **Secondary** | `LightGold` `#F0C040` (M3 `secondary`) | Secondary actions, tonal buttons |
| **Tertiary** | `TextGold` `#D4A843` (M3 `tertiary`) | Tertiary actions, accents |
| **Background** | `DarkBrown1` `#1A0A0A` (M3 `background`) | App canvas, scaffold |
| **Surface** | `DarkBrown2` `#2D1010` (M3 `surface`) | Cards, sheets, snackbars |
| **Dialog Surface** | `DarkBrown1` `#1A0A0A` (M3 `background`) | `KhanaBookDialog` (uses background, not surface); `KhanaBookLargeDialog` uses `DarkBrown2` |
| **On-Surface Text** | `TextLight` `#F5E6C8` (M3 `onSurface`/`onBackground`) | Body, captions, primary text |
| **On-Primary** | `Color.Black` (M3 `onPrimary`) | Text on gold primary buttons |
| **Semantic (borders on DarkBrown2)** | SuccessGreen `#388E3C`, DangerRed `#C62828`, WarningYellow `#FFB300`, PrimaryGold `#C8960C` | Toast borders, status chips, inline indicators |
| **Food Type** | VegGreen `#4CAF50`, NonVegRed `#E53935` | KOT badges, menu tags |
| **Part-Payment** | PartCashUpiTeal `#00A394`, PartCashPosOrange `#EF6C00`, PartUpiPosSky `#4FA3E3` | Payment mode chips |
| **Orders Surface** | OrdersBg `#170809`, OrdersCard `#260F10`, OrdersGold `#D9A514` | Orders screen + Order Details modal only |
| **Typography** | **Poppins** (Google Font, weights Light–ExtraBold); fallback to system sans-serif | All text |
| **Font Scale** | **Respects system font scale** (no pinning); `CompositionLocalProvider` passes `density.fontScale` through. Layouts accommodate via scrolling/wrapping. | Accessibility |
| **Spacing Grid** | 4 dp base (`extraSmall=4` … `extraHuge=64`); scales by window size tier: CompactPhone 0.9×, MediumPhone 1.0×, LargePhone 1.05×, Tablet 1.15× | Layout, padding, margins |
| **Touch Target** | Minimum 48×48 dp (`buttonHeightCompact=48dp`, `buttonHeight=52dp`, `inputHeight=56dp`) | Buttons, chips, inputs, list items |
| **Border Radius** | `KhanaRadii.card` = `lg` (14 dp), `KhanaRadii.modal` = `lg` (14 dp), `KhanaRadii.pill` (999 dp) | Component shape |
| **Motion** | Material 3 standard easing; respects system animation scale | Transitions, ripple |
| **Dark Mode** | **Forced dark-only** (no light theme); `isAppearanceLightStatusBars=false`, `isAppearanceLightNavigationBars=false` | — |
| **Iconography** | Material Icons Filled (24 dp) + Lottie for tab selection | System icons |

**Key Compose Implementation**: `apps/android/app/src/main/java/.../core/theme/`
- `Theme.kt` — `KhanaBookLiteTheme` wrapper with `DarkColorScheme`
- `Color.kt` — Raw colour constants (`PrimaryGold`, `DarkBrown1`, `DarkBrown2`, `TextLight`, semantic colours)
- `Spacing.kt` — `Spacing` data class + `spacingForTier()` responsive scaling
- `Type.kt` — `BaseTypography` (Poppins) + `typographyForTier()` responsive scaling
- `Shape.kt` — `KhanaRadii` (card, modal, pill, etc.)
- `core/designsystem/` — `KhanaBookSnackbar.kt` (toast), `KhanaBookCard.kt`, `KhanaBookDialog.kt`, etc.

### 2.2 Web Admin (Angular 18 + Astryx v0.7+)

> **Light workspace** — `color-scheme: light` in `:root`. Dark sidebar (`--pos-sidebar-bg`) is the only dark plane. Tokens defined in `apps/web-admin/src/styles.css`.

#### 2.2.1 Brand & Interactive Tokens
| Token | Value | Usage |
|-------|-------|-------|
| `--kb-color-primary` | `#FF5A0A` (Orange/Saffron) | Primary filled buttons (via `--kb-color-primary-strong`), focus ring base |
| `--kb-color-primary-strong` | `#C2410C` | Actual fill for `.primary-btn` (white label, 4.5:1) |
| `--kb-color-primary-hover` | `#E04D05` | Primary button hover |
| `--kb-color-primary-soft` | `rgba(255, 90, 10, 0.10)` | Primary tint for chips, focus rings |
| `--kb-color-primary-foreground` | `#FFFFFF` | Text on primary fills |
| `--kb-color-primary-rgb` | `255, 90, 10` | Alpha variants (`rgba(var(--kb-color-primary-rgb), 0.45)`) |
| `--kb-brand-purple` / `--kb-color-link` | `#552AAF` / `#4A21A6` | Sidebar active, links, brand text (violet from logo) |
| `--kb-color-link-hover` | `#38157F` | Link hover (no `--kb-brand-purple-hover` token) |
| `--kb-brand-orange` | `#EC8C17` | Saffron accent (money chips, bars) — NOT for text (2.9:1 on white) |
| `--kb-brand-orange-ink` | `#B45309` | Dark ink for saffron text (5:1 on white) |
| `--brand-focus-ring` | `rgba(var(--kb-color-primary-rgb), 0.45)` | Focus halo (orange-based, matches primary); `--kb-color-focus-ring` token does not exist |
| `--kb-color-focus-ring` | — | **Not defined** — components use `--brand-focus-ring` or `box-shadow: 0 0 0 2px var(--kb-color-primary)` |

#### 2.2.2 Semantic Status Tokens (Deep Steps for Light Mode)
| Token (fill) | Token (soft/tint) | Contrast on Surface | Usage |
|--------------|-------------------|---------------------|-------|
| `--kb-color-success` `#166534` | `--kb-color-success-soft` `rgba(22,101,52,0.10)` | ≥ 6:1 | Success badges, positive KPIs |
| `--kb-color-warning` `#9A3F08` | `--kb-color-warning-soft` `rgba(154,63,8,0.10)` | ≥ 5:1 | Pending, caution |
| `--kb-color-danger` `#B91C1C` | `--kb-color-danger-soft` `rgba(185,28,28,0.07)` | ≥ 5:1 | Error badges, destructive ghost |
| `--kb-color-danger-solid` `#B91C1C` | `--kb-color-danger-solid-hover` `#991B1B` | 6.4:1 (white on solid) | Destructive primary button |
| `--kb-color-info` `#4A21A6` | `--kb-color-info-soft` `rgba(74,33,166,0.09)` | ≥ 5:1 | Info badges, helper links |

#### 2.2.3 Surface & Neutral Tokens
| Role | Token | Value | Contrast |
|------|-------|-------|----------|
| Page canvas | `--kb-color-bg-app` | `#F3F8FE` | — |
| Cards, tables, panels | `--kb-color-surface` | `#FFFFFF` | 16.9:1 (`--kb-color-foreground`) |
| Table header, hover, nested tiles | `--kb-color-surface-2` | `#F1F5F9` | — |
| Modal, popover, raised controls | `--kb-color-surface-3` | `#FFFFFF` | — |
| Recessed input / well | `--kb-color-muted` | `#F1F5F9` | — |
| Dividers, card edges | `--kb-color-border` | `#D9E5F2` | — |
| Control borders | `--kb-color-border-strong` | `#4B5563` | 3.5:1 on canvas |
| Primary text | `--kb-color-foreground` | `#111827` | 16.9:1 |
| Values, table cells | `--kb-color-foreground-2` | `#334155` | 10.4:1 |
| Labels, metadata | `--kb-color-muted-foreground` | `#586F94` | 6.6:1 |

#### 2.2.4 Typography
| Role | Font | Size | Line Height | Weight | Token |
|------|------|------|-------------|--------|-------|
| Display / H1 | Plus Jakarta Sans | 32 px | 1.15 | 700 | `--kb-text-4xl` |
| Page Title / H2 | Plus Jakarta Sans | 28 px | 1.25 | 650 | `--kb-text-3xl` |
| Card Header / H3 | Plus Jakarta Sans | 24 px | 1.30 | 600 | `--kb-text-2xl` |
| Section Label / H4 | Plus Jakarta Sans | 20 px | 1.40 | 600 | `--kb-text-xl` |
| Body Large | Inter | 16 px | 1.50 | 400/500 | `--kb-text-lg` |
| Body Regular | Inter | 14 px | 1.50 | 400/500 | `--kb-text-md` |
| Caption / Small | Inter | 13 px | 1.40 | 500 | `--kb-text-sm` |
| Micro / Eyebrow | Inter | 11 px | 1.30 | 700 (uppercase) | `--kb-text-2xs` |
| Tabular / Mono | JetBrains Mono | per context | 1.5 | 400 | `--kb-font-mono` |

**Type scale tokens**: `--kb-text-2xs` (11px) → `--kb-text-4xl` (32px), 9 steps. Note: `--kb-text-3xl`=28px, `--kb-text-2xl`=24px, `--kb-text-xl`=20px, `--kb-text-lg`=16px, `--kb-text-md`=14px, `--kb-text-sm`=13px, `--kb-text-xs`=12px, `--kb-text-2xs`=11px.

#### 2.2.5 Spacing & Layout
| Token | Value | Usage |
|-------|-------|-------|
| `--kb-space-1` | 4 px | Base unit |
| `--kb-space-2` | 8 px | Compact gap |
| `--kb-space-3` | 12 px | Default gap |
| `--kb-space-4` | 16 px | Card padding, section gap |
| `--kb-space-5` | 20 px | — |
| `--kb-space-6` | 24 px | Page margin |
| `--kb-space-8` | 32 px | Section margin |
| `--kb-space-10` | 40 px | — |
| `--kb-space-12` | 48 px | Hero margin |
| `--kb-space-16` | 64 px | — |

**Table row height**: No fixed 52 px rule; cells use `padding: 0.7rem 0.85rem`. Inline action stacks use `white-space: nowrap; flex-wrap: nowrap;`.

#### 2.2.6 Border Radius
| Token | Value | Usage |
|-------|-------|-------|
| `--kb-radius-sm` | 4 px | Inputs, chips, badges |
| `--kb-radius-md` | 6 px | — |
| `--kb-radius-lg` | 8 px | Buttons, cards |
| `--kb-radius-xl` | 12 px | Large cards (`.panel`, `.card`) |
| `--kb-radius-2xl` | 16 px | Modals, popovers |
| `--kb-radius-full` | 9999 px | Pills, FAB |

#### 2.2.7 Elevation (Box Shadow)
| Level | Token | Value |
|-------|-------|-------|
| xs | `--kb-shadow-xs` | `0 1px 2px rgba(15,23,42,0.06)` |
| sm | `--kb-shadow-sm` | `0 1px 3px rgba(15,23,42,0.10), 0 1px 2px rgba(15,23,42,0.06)` |
| md | `--kb-shadow-md` | `0 4px 12px rgba(15,23,42,0.10)` |
| lg | `--kb-shadow-lg` | `0 12px 32px rgba(15,23,42,0.16)` |
| card | `--shadow-card` | `0 1px 3px rgba(15,23,42,0.06)` (legacy `--shadow-*` name, not `--kb-shadow-card`) |
| elevated | `--shadow-elevated` | `0 6px 20px rgba(15,23,42,0.12)` (legacy `--shadow-*` name) |
| warm | `--kb-shadow-warm` | `0 6px 20px rgba(15,23,42,0.12)` |

**Note:** `--kb-shadow-card` and `--kb-shadow-elevated` tokens do not exist; code uses legacy `--shadow-card` and `--shadow-elevated`.

#### 2.2.8 Motion
| Token | Value | Usage |
|-------|-------|-------|
| `--kb-motion-fast` | 150 ms ease-out | Tooltip, dropdown |
| `--kb-motion-base` | 200 ms ease-out | Modal, drawer, tab switch |
| `--kb-motion-slow` | 300 ms ease-out | Page transition |
| `--kb-ease-standard` | `cubic-bezier(0.2,0,0.2,1)` | Standard easing |

**Reduced motion**: `@media (prefers-reduced-motion: reduce)` → most durations become `0.01ms`. Exceptions: `.spinning`, `.spinner`, `.skeleton::after` are set to `1.5s !important` + `infinite` (styles.css:1539-1544).

#### 2.2.9 Z-Index Scale
| Layer | Token | Value |
|-------|-------|-------|
| Base | `--kb-z-raised` | 1 |
| Tooltip | `--kb-z-tooltip` | 10 |
| Sticky Header | `--kb-z-dock` | 30 |
| Dropdown / Flyout | `--kb-z-dropdown` | 60 |
| Mobile Topbar | `--kb-z-topbar` | 100 |
| Backdrop / Scrim | `--kb-z-backdrop` | 110 |
| Sidebar / Drawer | `--kb-z-sidebar` | 120 |
| Skip Link | `--kb-z-skip` | 130 |
| Collapsed Rail Hover | `--kb-z-peek` | 140 |
| Modal Overlay | `--kb-z-modal` | 1000 |
| Toast | `--kb-z-toast` | 1100 |

#### 2.2.10 Iconography
- Inline SVG via Astryx `<ax-icon>` components (semantic set: check, alert, info, chevron, etc.)
- 24×24 px default; 16×16 px compact; 32×32 px hero.

#### 2.2.11 Dark Mode
- **Not supported in v1** — light workspace only. Dark sidebar is isolated plane.
- Light mode toggle planned v2.0.

---

## 3. Cross-Platform Component Conventions

| Component | Android (Compose) | Web Admin (Angular + Astryx) |
|-----------|-------------------|------------------------------|
| **Primary Button** | `KhanaPrimaryButton` — filled `PrimaryGold`, label `DarkBrown1` (`#1A0A0A`), height scales with tier via `buttonHeightLarge` (56 dp MediumPhone → 68 dp Tablet), radius `KhanaRadii.lg` (14 dp) | `.primary-btn` — filled `--kb-color-primary-strong` (`#C2410C`), white label, **no fixed height** (padding: `0.55rem var(--kb-space-4)`), radius `--kb-radius-lg` (8 px) |
| **Secondary Button** | `KhanaSecondaryButton` — **outlined**, `PrimaryGold` border + text, label `PrimaryGold` | `.ghost-btn` (in `bento.css`) — surface, border `--kb-border-control` (`#64748B`), **not** `border-strong` |
| **Danger Button** | `KhanaDestructiveButton` — `DangerRed` fill, label `TextLight` (`#F5E6C8`) | `.danger-btn` / `.primary-btn.danger-btn` — `--kb-color-danger-solid` fill, white label |
| **Ghost Danger** | — | `.ghost-btn.danger-btn` — transparent, danger border, danger text |
| **Icon Button** | `IconButton` (M3) — 48 dp touch target | `.icon-btn` 32×32 px / `.icon-action-btn` 38×38 px — **no `ax-button[appearance="icon"]`** |
| **FAB** | `FloatingActionButton` — `PrimaryGold`, 56 dp, 28 dp radius | Not used (use primary button in header) |
| **Input Field** | `KhanaBookInputField` — outlined, M3 `OutlinedTextField` defaults (no explicit height/radius), `singleLine: Boolean = true` param only | `.field-control` / `.field-select` — `min-height: var(--kb-touch-min)` (44 px), 8 px radius; `.field-input` — 8 px radius, **no min-height** |
| **Select / Dropdown** | `ExposedDropdownMenuBox` — M3 menu | Native `<select>` + `.field-select` / `ax-select` |
| **Toggle / Switch** | `Switch` (M3) — `PrimaryGold` thumb | `.toggle-btn` — 42×24 px, **thumb = `--kb-color-surface` (white)**, track = brand |
| **Checkbox** | `Checkbox` (M3) | Native `<input type="checkbox">` + label |
| **Radio Group** | `RadioGroup` + `RadioButton` | Native radio + label |
| **Card** | `KhanaBookCard` — `DarkBrown2`, radius `KhanaRadii.lg` (14 dp) | `.panel` — white, radius `--kb-radius-xl` (12 px), border |
| **Table** | `LazyColumn` + `KhanaTableRow` — responsive row height | `<table>` + `.data-table` — **no 52 px row height rule** (cells use `padding: 0.7rem 0.85rem`), sticky header |
| **Badge / Chip** | `KhanaStatusBadge` — shape `KhanaRadii.sm` (6 dp), **no `KhanaChip`** | `.chip` / `.chip-pill` — soft bg, semantic text, full radius |
| **Tooltip** | `TooltipBox` — system default | Not used (use inline hint) |
| **Modal / Dialog** | `KhanaBookDialog` — `DarkBrown1` (background), radius `KhanaRadii.xl` (20 dp); `KhanaBookLargeDialog` — `DarkBrown2`, radius 18 dp | `.modal-box` — `--kb-color-surface-3`, shadow-lg, `max-height: min(88vh, 760px)` |
| **Drawer / Sidebar** | `ModalNavigationDrawer` / `PermanentNavigationDrawer` | `.sidebar` — `position: sticky`, expanded 232 px, **collapsed 64 px** (`--rail-width`), dark violet `--pos-sidebar-bg` |
| **Tabs** | `TabRow` + `ScrollableTabRow` — Lottie indicator | `.tab-pill` — **filled active pill**, **no `.tab-bar` / `ax-tabs` / underline indicator** |
| **Toast / Snackbar** | `KhanaToast` / `KhanaBookSnackbar` (§8) | `.global-toast` + `ToastService` (§8) — **`.toast` rule unused** |
| **Banner** | `InAppNotificationBanner` (slide-down, §8) | Not used (use toast + inline alert) |
| **Progress** | `CircularProgressIndicator` / `LinearProgressIndicator` | `.spinner` (brand ring, 22 px) |
| **Skeleton** | `Shimmer` (custom) | `.skeleton` — **shimmer 1.3s `translateX`** (not pulse) on `--kb-color-surface-2` |

---

## 4. Layout Patterns

### 4.1 Android POS — Single-Activity, Compose Navigation
- **Bottom Navigation** (3–5 tabs): Home (Billing), Orders, Menu, Reports, Settings
- **Top App Bar**: Contextual title + primary action (e.g., "New Bill")
- **Content**: `NavHost` with nested graphs per tab
- **Drawer**: Owner-only (Settings, Terminals, Sync) — **no Staff screen**
- **Safe Areas**: Edge-to-edge with `WindowInsets` (status bar transparent, nav bar translucent, `navigationBarColor=TRANSPARENT`)

### 4.2 Web Admin — Responsive Shell
| Breakpoint | Layout |
|------------|--------|
| **≥ 1200 px** | Sidebar (232 px) + main content (fluid, max 1680 px) + optional right rail |
| **768–1199 px** | Collapsible sidebar (icon-only **64 px**) + main content |
| **< 768 px** | Drawer (hamburger) + full-width content; mobile topbar (`--kb-z-topbar`) |

**Page Template**:
```
<kb-shell>
  <kb-sidebar slot="sidebar" />
  <main slot="content" class="page-shell">
    <kb-page-header>        <!-- Title + breadcrumb + primary action -->
    <section class="page-body">  <!-- Cards, tables, forms -->
  </main>
  <kb-right-rail slot="rail" />  <!-- Optional: filters, details -->
</kb-shell>
```

---

## 5. Data Display Standards

### 5.1 Currency (INR)
- **Format**: `₹{amount}` (symbol prefix, no space)
- **Decimals**: 2 digits always (₹100.00)
- **Font**: Tabular mono (`JetBrains Mono` web / `Poppins` tabular nums Android) for alignment
- **Negative**: Red (`DangerRed` / `--kb-color-danger`) with minus sign (₹-50.00)

### 5.2 Dates & Times
| Context | Format | Example |
|---------|--------|---------|
| **Bill timestamp** | `dd MMM yyyy, hh:mm a` | 19 Nov 2026, 02:30 PM |
| **Business day label** | `EEE, dd MMM` | Thu, 19 Nov |
| **Relative** | "Just now", "2 min ago", "3h ago", "Yesterday" | — |
| **Input (date picker)** | `dd/MM/yyyy` | 19/11/2026 |

### 5.3 Numbers
- **Quantities**: Integer (pcs) or 2 dp (kg/ltr) — tabular mono
- **Percentages**: 1 dp + `%` (5.5%)
- **Large numbers**: Indian grouping (1,00,000) — `Intl.NumberFormat('en-IN')`

### 5.4 Status Badges (Semantic, Never Colour-Only)
| Status | Android Chip | Web Chip | Label Required |
|--------|--------------|----------|----------------|
| Success / Paid / Active | `SuccessGreen` border on `DarkBrown2` | `--kb-color-success-soft` + `--kb-color-success` text | ✅ "Paid" |
| Warning / Pending / Partial | `WarningYellow` border on `DarkBrown2` | `--kb-color-warning-soft` + `--kb-color-warning` text | ✅ "Pending" |
| Danger / Failed / Cancelled | `DangerRed` border on `DarkBrown2` | `--kb-color-danger-soft` + `--kb-color-danger` text | ✅ "Failed" |
| Info / Draft / Processing | `PrimaryGold` border on `DarkBrown2` | `--kb-color-info-soft` + `--kb-color-info` text | ✅ "Draft" |

---

## 6. Form & Input Patterns

| Pattern | Android | Web |
|---------|---------|-----|
| **Required field** | `*` suffix on label + `error` text below | `*` suffix + `aria-required="true"` + inline error |
| **Validation** | Real-time on blur + submit; `KhanaBookInputField` `isError` | On blur + submit; `.field-control[aria-invalid='true']` danger border + focus ring |
| **Address / Multi-line** | `KhanaBookInputField` — `singleLine = false` (no `rows`/`minHeight` params; height grows with content) | `<textarea rows="3">` `min-height: 72px; resize: vertical` |
| **Search / Filter** | `OrderSearchBar` (feature-local) — debounced 300 ms in `MenuViewModel` | `.filter-chip` / `.field-control` — debounced 250 ms |
| **Date picker** | `DatePickerDialog` (M3) | Native `<input type="date">` |
| **Time picker** | `TimePickerDialog` (M3, 24 h) | Native `<input type="time">` |
| **Number stepper** | Custom — −/+ buttons, 48 dp | `.action-btn` −/+ in `.row-actions` |
| **Image upload** | `KhanaBookInputField` + camera/gallery intent | `ax-file-upload` — drag-drop + click, preview |

---

## 7. Empty, Loading, Error States

| State | Android | Web |
|-------|---------|-----|
| **Empty List** | `KhanaEmptyState` — illustration + title + action button | `.empty-state` — SVG + title + `.primary-btn` |
| **Loading (full page)** | `KhanaLoadingOverlay` / `Shimmer` | `.page-skeleton` — pulse on `--kb-color-surface-2` |
| **Loading (inline)** | `CircularProgressIndicator` (24 dp) in button / row | `.spinner` (22 px) in button / table cell |
| **Error (full page)** | `KhanaBookDialog` with error content | `.alert.error` / `.error-text` |
| **Error (inline)** | `KhanaBookInputField` `error` text | `.field-error` / `.error-text` |
| **Offline Banner** | `InAppNotificationBanner` — **no "Working offline" string**; default accent is violet `#7C5CDB` | Not applicable (Web requires network) |

---

## 8. Toast & Notification Design (Cross-Platform Spec)

> Mirrors PRODUCT.md §8 — included here for designer/developer reference.

### 8.1 Android — `KhanaToast` / `KhanaBookSnackbar`
| Property | Spec |
|----------|------|
| **Kinds** | Success (green border), Error (red), Warning (amber), Info (gold) |
| **Durations** | Success 2 s · Error 3.5 s · Warning 3.5 s · Info 2 s · Action = indefinite |
| **Visual** | Background `DarkBrown2` (`#2D1010`), 1.5 dp coloured border per kind: SuccessGreen `#388E3C`, DangerRed `#C62828`, WarningYellow `#FFB300`, PrimaryGold `#C8960C`. Content `TextLight` `#F5E6C8`. Icon tinted to border. Max 3 lines. Optional action button + dismiss ×. |
| **Deduplication** | Same message+kind within 2 s suppressed; single visible toast |
| **Accessibility** | `LiveRegionMode.Polite` set **unconditionally** on the snackbar Surface — errors are **not** assertive (unlike web parity) |
| **Placement** | Bottom, horizontal padding 24 dp (`spacing.large`), vertical 8 dp (`spacing.small`) + nav-bar inset (host adds `navigationBarsPadding()` + `spacing.extraLarge` = 32 dp bottom) |

### 8.2 Android — `InAppNotificationBanner` (Foreground Push)
| Property | Spec |
|----------|------|
| **Trigger** | Push received while app foreground (excludes login/setup/lock) |
| **Style** | Slide-down from under status bar; background `DarkBrown2`, **4 dp wide** coloured left accent bar + icon, 1 dp full perimeter border |
| **Accent Colours** | Payment/QR: `#16A34A` · Refund: `#EF4444` · KYC: `#8B5CF6` · Settlement: `#0284C7` · FSSAI expiry: `#F97316` · Default: `#7C5CDB` |
| **Auto-dismiss** | 6 s (`AUTO_DISMISS_MS`); tap → deep-link; dismiss × |
| **Animation** | `slideInVertically` + `fadeIn` / `slideOutVertically` + `fadeOut` |

### 8.3 Web Admin — `.global-toast` / `ToastService`
| Property | Spec |
|----------|------|
| **Types** | `info` (default), `success`, `error` (no `warning` type) |
| **Durations** | Error 7 s · Success/Info 3.5 s (configurable per call) |
| **Visual** | Fixed bottom-right (1.5 rem), max-width 380 px (`width: min(380px, calc(100vw - 2rem))`), rendered as `<aside class="global-toast">`, background `--kb-color-surface-3` (`#FFFFFF`), color `--kb-color-foreground` (`#111827`), border `1px solid var(--kb-color-border-strong)`, circular mark with glyph (! / ✓ / i), dismiss ×. The `.toast` CSS rule (360 px, no border) is **not used** by the component. |
| **Accessibility** | `role="alert"` (error, assertive) / `role="status"` (success/info, polite), `aria-live`, `aria-atomic` |
| **Responsive** | ≤ 480 px: 1 rem bottom/right |
| **Single-toast** | New call replaces current (timer reset) |

### 8.4 Consistency Rules
| Rule | Android | Web |
|------|---------|-----|
| **Colour coding** | 1.5 dp border accent per kind | Border `border-strong` + mark background (error/success) |
| **Icon + glyph** | Material icon per kind (tinted) | Circular mark with ! / ✓ / i |
| **Duration hierarchy** | Error/Warning > Success/Info | Error > Success/Info |
| **Action toasts** | Indefinite (action button) | Not supported |
| **Deduplication** | 2 s window per message+kind | None (last write wins) |
| **Z-order** | Above nav bar | `--kb-z-toast` (1100) |

---

## 9. Accessibility Checklist (Non-Negotiable)

### Android
- [ ] System font scale respected (no pinning); layouts scroll/wrap
- [ ] TalkBack `contentDescription` on every `Clickable`, `IconButton`, `Tab`
- [ ] Touch targets ≥ 48×48 dp
- [ ] Colour contrast ≥ 4.5:1 on dark surfaces (verified on `DarkBrown2` with `TextLight`)
- [ ] Focus order logical (left→right, top→bottom)
- [ ] `LiveRegion` on toasts/banners

### Web
- [ ] Semantic HTML (`<main>`, `<nav>`, `<button>`, `<label>`, `<table>`)
- [ ] Focus-visible: 2px solid primary (`#FF5A0A`) + 2px halo (`--brand-focus-ring` / `box-shadow: 0 0 0 2px var(--kb-color-primary)`) — **`--kb-color-focus-ring` token does not exist**
- [ ] `aria-live` on toast container (`polite`/`assertive`)
- [ ] Keyboard navigation: Tab, Shift+Tab, Enter, Space, Escape, Arrows
- [ ] `check-contrast.mjs` passes in CI
- [ ] Skip-to-main link on every page

---

## 10. Asset Guidelines

| Asset | Android | Web |
|-------|---------|-----|
| **App Icon** | Adaptive icon (foreground + background layers), 432×432 px source | Favicon 32×32, 180×180 apple-touch, 512×512 manifest |
| **Illustrations** | VectorDrawable (XML) or Lottie (JSON) — max 200 KB | SVG (inline or `<img>`), optimized via SVGO |
| **Photos** | WebP lossless (menu items), max 500 KB | WebP/AVIF, responsive `srcset` |
| **Logo** | `ic_launcher` + `ic_launcher_round` | `--kb-brand-purple` / `--kb-brand-orange` CSS variables (SVG data URI) |

---

## 11. Design Token Consumption Rules

1. **Components consume tokens — they never declare palette values.**
2. **Android**: Use `MaterialTheme.colorScheme.primary` (→ `PrimaryGold`), `MaterialTheme.typography.bodyMedium`, `KhanaBookTheme.spacing.medium`, `KhanaRadii.card`.
3. **Web**: Use `var(--kb-color-primary)`, `var(--kb-text-md)`, `var(--kb-space-4)`, `var(--kb-radius-xl)`.
4. **No hardcoded hex** in component stylesheets (except component-specific overrides documented here).
5. **Semantic tokens only for their meaning** — never use `--kb-color-success` for a primary button.

---

## 12. Versioning & Change Control

| Version | Date | Change |
|---------|------|--------|
| 2.3.1 | 2026-11-19 | **Audit fixes**: Android radius (14dp card/modal, 999dp pill), dialog surface (KhanaBookDialog=DarkBrown1), type role→token mapping, shadow tokens (legacy --shadow-*), motion reduced (0.01ms with exceptions), component names (KhanaPrimaryButton/KhanaSecondaryButton/KhanaDestructiveButton), KhanaStatusBadge (6dp), secondary button outlined, icon button sizes, toggle thumb white, sidebar collapsed 64px, tabs .tab-pill filled, toast .global-toast 380px, skeleton shimmer, offline banner removed, Android a11y Polite unconditionally. |
| 2.3.0 | 2026-11-19 | **Major correction**: Android = Dark Gold (`PrimaryGold`), Web = Light Orange (`#FF5A0A`). Fixed font (Poppins vs Inter), font scale (system vs pinned), theme (dark vs light). Aligned with `KhanaBookLiteTheme` and `styles.css`. |
| 2.2.0 | 2026-11-19 | Previous version (incorrect: Android light teal, Web dark indigo) |
| 2.1.0 | 2026-10-25 | Web Admin dark palette revision (indigo primary) — stale |
| 2.0.0 | 2026-09-15 | Initial design system split |

> **Source of truth for tokens**: Android → `Color.kt` + `Theme.kt` + `Spacing.kt` + `Type.kt` + `Shape.kt`; Web → `apps/web-admin/src/styles.css`.
> **Do not edit this file to change tokens** — update the source files, then regenerate this doc if needed.

---

## 13. Reference Implementation Paths

- **Android Theme & Tokens**: `apps/android/app/src/main/java/com/khanabook/lite/pos/core/theme/`
- **Android Design System Components**: `apps/android/app/src/main/java/com/khanabook/lite/pos/core/designsystem/`
- **Web Admin Styles**: `apps/web-admin/src/styles.css`, `apps/web-admin/src/styles/*.css`
- **Web Admin Components**: `apps/web-admin/src/app/shared/components/`
- **Astryx Library**: `node_modules/@astryxdesign/core/` (peer dependency)

---

> **Maintainers**: KhanaBook Core Team — see `AGENTS.md` for AI agent operating rules.
> **Cross-reference**: `PRODUCT.md` (product spec), `ARCHITECTURE.md` (tech stack), `API.md` (contracts).