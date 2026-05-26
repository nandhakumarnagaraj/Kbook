# 03. Component Analysis

**Framework References:** UI/UX Pro Max (Components), Bencium UX Designer (Design System Templates), Premium SaaS Design, Vercel Web Interface Guidelines

---

## 3.1 Cards / Stat Cards

### Current Design
```
┌──────────────────────────────────────┐
│  [icon]  Metric Label                 │
│          $12,345                       │
│          ▲ 12.5% from last week       │
└──────────────────────────────────────┘
```

### Assessment

| Criteria | Score | Notes |
|----------|-------|-------|
| Visual hierarchy | 8/10 | Value is prominent, label is clear, trend is contextual |
| Icon consistency | 7/10 | Material icons, consistent sizing |
| Hover state | 6/10 | Recent session added `translateY(-2px)` + shadow — good |
| Responsive | 5/10 | Cards wrap at mobile but icons are too large → cramped |
| Dark mode | 7/10 | Glassmorphism works in both modes |

### Per UI/UX Pro Max: "Metric cards should show trend direction and magnitude"
> ✅ Trend arrows + percentages + color coding (green/red) are all present.

### Recommendation
- Reduce icon size on mobile (36px → 28px)
- Add loading skeleton variant (pulsing gray blocks)
- Add drill-down: clicking a card navigates to detailed view

---

## 3.2 Data Tables

### Current Design
```
┌─────────┬──────────┬──────────┬──────────┬──────────┐
│ Header  │ Header   │ Header   │ Header   │ Action   │
├─────────┼──────────┼──────────┼──────────┼──────────┤
│ Data    │ Data     │ Data     │ Data     │ [Edit]   │
│ Data    │ Data     │ Data     │ Data     │ [Edit]   │
│ ...     │ ...      │ ...      │ ...      │ ...      │
└─────────┴──────────┴──────────┴──────────┴──────────┘
```

### Assessment

| Criteria | Score | Notes |
|----------|-------|-------|
| Header styling | 7/10 | Recent uppercase header refactor improved consistency |
| Row hover | 6/10 | Some tables have hover, some don't |
| Striped rows | 3/10 | Most tables lack alternating row colors |
| Column alignment | 5/10 | Numeric columns not right-aligned; no tabular-nums |
| Empty state | 3/10 | Shows raw "No data" text — no illustrations or CTAs |
| Pagination | 7/10 | Material paginator present on most tables |
| Sort/filter | 5/10 | Some tables have sort, some don't; inconsistent |
| Row actions | 7/10 | Action buttons with icons present |

### Per Vercel Guidelines 🔍
**Rule: "font-variant-numeric: tabular-nums for number columns"**
> ❌ Not applied. Monetary values and counts shift width as digits change (e.g., $1.00 vs $1,000.00).

**Rule: "Handle empty states"**
> ❌ Most tables show bare "No data" or empty `<tbody>`. No contextual illustration, no CTA to add data.

**Rule: "Long content: truncate or line-clamp"**
> ⚠️ Some table cells with long content overflow without truncation.

### Recommendation
- Add `font-variant-numeric: tabular-nums` to all numeric/monetary `<td>` elements
- Create a `<app-empty-state>` component with illustration + message + CTA
- Standardize row hover: `background: var(--surface-hover)` on all tables
- Right-align numeric columns

---

## 3.3 Forms

### Current Design

| Page | Form Quality | Notes |
|------|-------------|-------|
| Login | 🟢 Good | Clean, branded, Google sign-in |
| Restaurant Settings | 🟢 Good | Sectioned layout, clear labels |
| Commission Config | 🟡 Basic | Minimal styling, no validation indicators |
| Staff Add/Edit | 🟡 Basic | Simple form, no inline validation |
| Marketplace Setup | 🟢 Premium | Beautiful cards, toggles, timeline |
| Menu Item Edit | 🟡 Basic | Essential fields only |

### Per Vercel Guidelines 🔍

**Rule: "Inputs need autocomplete and meaningful name"**
> ⚠️ Many forms lack `autocomplete` attributes and meaningful `name` values. This affects browser autofill and password manager behavior.

**Rule: "Labels clickable (htmlFor or wrapping control)"**
> ✅ Most forms use Angular Material which handles this correctly.

**Rule: "Errors inline next to fields; focus first error on submit"**
> ❌ Error handling varies. Some forms show global error messages, not inline field errors.

**Rule: "Submit button stays enabled until request starts; spinner during request"**
> ⚠️ Inconsistently applied. Some forms disable the button correctly; others don't show loading state.

**Rule: "Never block paste"**
> ✅ No onPaste prevention found.

**Rule: "Placeholders end with … and show example pattern"**
> ⚠️ Some placeholders lack the "…" convention. Example patterns inconsistent.

### UX Issues
1. **No unsaved changes warning** — Navigating away from a dirty form loses data silently
2. **No auto-save** — Complex forms (Restaurant Settings) don't save drafts
3. **Inconsistent validation timing** — Some validate on blur, others on submit
4. **No field-level help** — Complex fields (e.g., tax configuration) lack tooltip explanations

---

## 3.4 Modals & Dialogs

### Current State

| Modal Type | Implementation | Quality |
|------------|---------------|---------|
| Order Details | Side drawer/panel | 🟢 Good |
| Sub-Merchant Detail | `MatDialog` (recently converted) | 🟢 Clean |
| Business View/Edit | `MatDialog` (recently converted) | 🟢 Good |
| Confirmation Dialogs | Various | 🟡 Inconsistent |
| Commission Config | No modal | ⚠️ Uses inline editing |

### Per Vercel Guidelines 🔍

**Rule: "overscroll-behavior: contain in modals/drawers"**
> ❌ Not found. Body scrolls behind open modals on some pages.

**Rule: "Destructive actions need confirmation"**
> ⚠️ Some delete actions have confirmation; some don't. Inconsistent.

**Rule: "Deep-link all stateful UI"**
> ⚠️ Modal content is not URL-addressable. Refreshing the page loses modal state.

### Recommendation
- Add `overscroll-behavior: contain` to all modal/drawer containers
- Standardize confirmation dialogs with a reusable component
- Focus trap within modals (tab cycling)

---

## 3.5 Buttons & Actions

### Current Variants

| Variant | Usage | Quality |
|---------|-------|---------|
| Primary (brand) | Main CTAs | 🟢 Good — brand color, clear hierarchy |
| Secondary/Outline | Secondary actions | 🟡 Inconsistent border radius |
| Icon buttons | Table actions | 🟢 Good with tooltips |
| Danger (red) | Delete/destructive | 🟢 Clear red styling |
| Disabled state | grayed out | ⚠️ Some lack visual distinction |

### Per Vercel/AccessLint 🔍

**Rule: "Icon-only buttons need aria-label"**
> ⚠️ Not consistently applied. Some icon buttons lack `aria-label`.

**Rule: "Hover/active/focus states increase contrast"**
> ✅ Most buttons have hover states. Focus states may be missing.

**Rule: "Buttons/links use hover: state for visual feedback"**
> ✅ Present on primary and secondary buttons.

### Recommendation
- Add `aria-label` to ALL icon-only buttons (edit, delete, view actions)
- Ensure disabled buttons have `aria-disabled="true"` not just CSS
- Add `:focus-visible` ring to all interactive elements

---

## 3.6 Charts & Data Visualization

### Current Implementation (Chart.js via ng2-charts)

| Chart Type | Location | Quality |
|-----------|----------|---------|
| Doughnut (Revenue Split) | Business Dashboard | 🟢 Good — clean, colored segments |
| Bar (Weekly Revenue) | Business Dashboard | 🟢 Recently converted to interactive |
| Metric Sparklines | Stat cards | 🟡 Some not fully implemented |

### Per UI/UX Pro Max Charts Assessment 📊

**Rule: "Choose chart type for the data story"**
> ✅ Doughnut for composition, bar for trend comparison. Good choices.

**Rule: "Use direct labels, not legends"**
> ⚠️ Charts use legends that require back-and-forth matching. Data labels on the chart would be more readable.

**Rule: "Color-blind friendly palette"**
> ❌ Chart colors (`--chart-1` through `--chart-5`) are not verified for color-blind accessibility. Consider using ColorBrewer or similar safe palettes.

**Rule: "Show data on hover"**
> ✅ Tooltips show values on hover. But tooltip styling is basic.

### Recommendation
- Add data labels directly on chart segments
- Verify chart palette against color-blind simulators
- Add animation when charts come into view
- Ensure chart dark mode works (some blending observed)

---

## 3.7 Status Badges & Tags

### Current Design
```
[Paid]     → green, pill shape, check icon
[Pending]  → amber, pill shape
[Failed]   → red, pill shape
[Ready]    → blue, pill shape
[Completed]→ green, pill shape
```

### Assessment ✅
- **Pill shape** (`border-radius: 999px`) — consistent and modern
- **Color coding** — semantic colors match expectations
- **Emoji/icons** — used sparingly but effectively for status (✅, 🔄, 🚀)

### Recommendation
- Add icon prefix to all badges for color-blind users (e.g., ✓ Paid, ◷ Pending)
- Ensure badge text has WCAG AA contrast (white text on colored backgrounds)

---

## Component Score Summary

| Component | Score | Priority for Improvement |
|-----------|-------|--------------------------|
| Stat Cards | 7/10 | Medium |
| Data Tables | 5/10 | 🟠 HIGH |
| Forms | 6/10 | Medium |
| Modals & Dialogs | 6/10 | Medium |
| Buttons & Actions | 7/10 | Low |
| Charts | 7/10 | Medium |
| Status Badges | 8/10 | Low |
| Navigation | 6/10 | Medium |
