# Figma — Design Token & Component Mapping

## Overview

This skill provides patterns for translating Figma design files into KhanaBook Angular code. It covers design token extraction, component mapping conventions, and maintaining parity between design and implementation.

---

## Design Token Extraction

### Color Tokens (Figma → CSS Variables)

Figma color styles map to KhanaBook CSS custom properties:

```typescript
// Figma Color Style → CSS Variable
interface FigmaColorToken {
  name: string;         // e.g., "Brand/Primary", "Surface/BG"
  hex: string;          // e.g., "#C85A00"
  opacity: number;      // 0-1
  description?: string;
}

// Mapping rule:
// "Brand/Primary" → --brand
// "Brand/Light"   → --brand-light
// "Surface/BG"    → --bg
// "Text/Primary"  → --ink
// "Text/Secondary" → --ink-secondary
// "Text/Muted"    → --muted
// "Border/Default" → --line
// "Border/Strong" → --line-strong
```

### Typography Tokens (Figma → CSS)

```typescript
interface FigmaTypeStyle {
  fontFamily: string;    // "Inter"
  fontSize: number;      // px
  fontWeight: number;    // 400, 500, 600, 700, 800
  lineHeight: number;    // px or percentage
  letterSpacing: number; // px
}

// Figma "Heading 1" → h1 style in component
// Figma "Body" → 14px, 1.5 line-height
// Figma "Label" → 0.75rem, 700 weight, 0.5px letter-spacing, uppercase
// Figma "Caption" → 0.65rem, 800 weight, 1.5px letter-spacing, uppercase
```

### Spacing Tokens (Figma → CSS)

```typescript
interface FigmaSpacingToken {
  name: string;     // "spacing-xs", "spacing-sm", "spacing-md", "spacing-lg", "spacing-xl"
  value: number;    // px
}

// Preferred spacing scale:
// xs: 4px    (rarely used)
// sm: 8px    (tight gaps)
// md: 16px   (default gap)
// lg: 24px   (section gaps)
// xl: 32px   (page padding)
// 2xl: 48px  (hero sections)
```

---

## Component Mapping Convention

Figma components map to Angular components as follows:

| Figma Component | Angular Implementation |
|-----------------|----------------------|
| Card / Panel | `div.panel` with CSS variables |
| Stat Card | Custom card with metric icon, value, label |
| Data Table | `mat-table` with `MatSortModule`, `MatPaginatorModule` |
| Dialog / Modal | `MatDialog` with `MatDialogModule` |
| Form Input | Custom `.custom-input` with icon wrapper (not `mat-form-field`) |
| Toggle Switch | `mat-slide-toggle` |
| Button (Primary) | `button[mat-flat-button]` with gradient |
| Button (Icon) | `button[mat-icon-button]` |
| Dropdown Menu | `mat-menu` |
| Navigation | `mat-list` with `routerLinkActive` |
| Tabs | `mat-tab-group` |
| Chips / Badges | `mat-chip` or custom `.status-badge` spans |
| Progress Indicator | `mat-progress-spinner` or custom skeleton `.shimmer-bg` |

---

## Layout Grid Mapping

Figma frame widths map to Angular breakpoints:

```typescript
const BREAKPOINT_MAP = {
  'Desktop HD':    '> 1200px',   // max page width 1480px
  'Desktop':       '960-1200px', // full sidebar visible
  'Tablet':        '768-960px',  // sidebar collapses
  'Mobile Large':  '480-768px',  // compact layout
  'Mobile':        '< 480px',    // stacked, minimal padding
};
```

---

## Automation Potential

> 💡 For teams using Figma plugins/API, consider automating token extraction:
> - **Figma Tokens plugin** → export JSON → script transforms to CSS variables
> - **Figma REST API** → pull design system data into build pipeline
> - Currently, this project uses manual extraction (record tokens from Figma, write into `styles.scss`)

---

## Export Guidelines

### How to Export from Figma for Web

1. **Colors** → Export as CSS Variables: match Figma style name to `--variable` name
2. **Typography** → Note the font size, weight, line-height, and letter-spacing
3. **Spacing** → Measure between elements and map to spacing scale
4. **Components** → Note the component structure and states (default, hover, active, disabled)
5. **Icons** → Use Material Icons font that matches closest to Figma icon (prefer Google Material icons)
6. **Images** → Export from Figma at 2x resolution, place in `web-admin/public/`

### Assets Directory

```
web-admin/public/
  khanabook_logo.png        — brand logo (sidebar 56×56)
  khanabook_logo_header.png — brand logo header variant
  khanabook_logo_home.png   — brand logo for login showcase
  privacy/                  — privacy policy pages
```

---

## Figma → Code Workflow

```
1. [DESIGN]  Export design tokens from Figma (colors, typography, spacing)
2. [TOKENS]  Map to CSS variables in styles.scss
3. [PAGES]   Create page component with page-shell pattern
4. [STATES]  Implement loading, empty, error, and success states
5. [RESPONSIVE] Apply responsive breakpoints matching Figma frame sizes
6. [DARK]    Verify dark mode variant renders correctly
7. [REVIEW]  Compare implementation against Figma specs
```

---

## Quality Checklist

- [ ] All colors use CSS variables (no hardcoded hex values)
- [ ] Responsive at all breakpoints (mobile → desktop)
- [ ] Dark mode renders correctly
- [ ] Loading state has skeleton/spinner
- [ ] Empty state has helpful message
- [ ] Error state has retry button
- [ ] Keyboard navigation works (Tab, Enter, Escape)
- [ ] Focus states are visible
- [ ] Animations respect `prefers-reduced-motion`
- [ ] Hover states match Figma
