# 01. Design System Analysis

**Framework Reference:** Premium SaaS Design (Style Guide artifact), Color Palette skill, UI/UX Pro Max (Design Systems → colors/typography)

---

## 1.1 CSS Variable Architecture

The project has an **extensive CSS custom property system** defined in `styles.scss` covering:

### Strengths ✅

| Category | Variables | Quality |
|----------|-----------|---------|
| **Brand Colors** | `--brand`, `--brand-light`, `--brand-dark` | 🟢 Excellent |
| **Surface Colors** | `--surface`, `--surface-hover`, `--panel`, `--panel-hover` | 🟢 Excellent |
| **Text Colors** | `--text-primary`, `--text-secondary`, `--text-muted` | 🟢 Excellent |
| **Border Radius** | `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-xl` | 🟢 Excellent |
| **Spacing** | `--space-xs` through `--space-3xl` | 🟢 Excellent |
| **Shadows** | `--shadow-sm`, `--shadow-md`, `--shadow-lg`, `--shadow-xl` | 🟢 Excellent |
| **Animation Tokens** | `--transition-fast`, `--transition-normal`, `--transition-slow` | 🟢 Good |
| **Status Colors** | `--success`, `--warning`, `--error`, `--info` | 🟢 Excellent |
| **Chart Colors** | `--chart-1` through `--chart-5` | 🟢 Good |

### Gaps ❌

| Missing | Impact | Severity |
|---------|--------|----------|
| **Z-index scale** (`--z-dropdown`, `--z-modal`, `--z-toast`) | Inconsistent stacking across modals/toasts | 🟠 Medium |
| **Font family variables** (`--font-heading`, `--font-body`, `--font-mono`) | Hard to swap fonts consistently | 🟡 Low |
| **Font weight tokens** (`--fw-normal`, `--fw-semibold`, `--fw-bold`) | Inline weight declarations instead of tokens | 🟡 Low |
| **Line height tokens** (`--lh-tight`, `--lh-normal`, `--lh-relaxed`) | Inconsistent line-height usage across pages | 🟡 Low |
| **Opacity tokens** (`--opacity-disabled`, `--opacity-hover`) | Opacity values scattered as literals | 🟢 Trivial |
| **Dark mode elevation tokens** | Shadows hardcoded per component | 🟠 Medium |

---

## 1.2 Color Palette

### Brand Color: `#C85A00` (Warm Orange)

```
Primary:     #C85A00 — CTAs, brand elements, active states
Light:       #E8832A — hover states, secondary brand elements
Dark:        #A34700 — pressed states, active navigation
```

### Surface Spectrum

```
--surface:          #ffffff / #0f172a (dark)
--surface-hover:    #f8fafc / #1e293b (dark)
--panel:            #f1f5f9 / #1e293b (dark)
--panel-hover:      #e2e8f0 / #334155 (dark)
--border:           #e2e8f0 / #334155 (dark)
```

### Semantic Colors

```
--success:    #10b981 (green)  — paid, completed, active
--warning:    #f59e0b (amber)  — pending, partial
--error:      #ef4444 (red)    — failed, expired
--info:       #3b82f6 (blue)   — informational
```

### Assessment per UI/UX Pro Max Framework 🎨

**Rule: "Use 60-30-10 color distribution"**
> ✅ Brand orange is used sparingly (~10%) — primary on buttons, active nav items, status badges. Neutrals dominate (~60%) with semantic colors at ~30%. This is correct for admin dashboards.

**Rule: "Ensure WCAG AA contrast on all text"**
> ⚠️ `--text-muted: #94a3b8` on white background = 3.4:1 contrast ratio — **fails WCAG AA** for both normal text (4.5:1) and large text (3:1 borderline). See Accessibility section.

**Rule: "Define light/dark palette for every token"**
> ✅ Most tokens have both light and dark variants. Some tokens like `--chart-*` lack dark mode equivalents, causing chart elements to blend into dark backgrounds.

---

## 1.3 Typography

### Current Setup

```scss
--font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
```

### Assessment per Anthropic Frontend Design 🎨

**Rule: "Choose distinctive fonts, avoid generic choices like Inter/Arial"**
> ⚠️ Inter is the most common SaaS font — functional but **not distinctive**. For a restaurant/hospitality platform, consider:
> - **Headings:** `DM Serif Display` (warm, editorial feel) or `Plus Jakarta Sans` (modern with character)
> - **Body:** Keep Inter for readability, or switch to `Outfit` for a rounded warmth

**Rule: "Use text-wrap: balance on headings"**
> ❌ No instances of `text-wrap: balance` found. Headings with multi-line text may have awkward line breaks.

**Rule: "font-variant-numeric: tabular-nums for number columns"**
> ⚠️ Not consistently applied in data tables. Monetary values and counts shift width as digits change, causing visual jitter.

### Missing Typography Scale

The project has no explicit typography scale variables:

```scss
// MISSING — should exist in styles.scss
--text-2xs: 0.625rem;   // 10px — data table cells
--text-xs:  0.75rem;     // 12px — labels, metadata
--text-sm:  0.875rem;    // 14px — body, descriptions
--text-base: 1rem;       // 16px — default body
--text-lg:  1.125rem;    // 18px — section titles
--text-xl:  1.25rem;     // 20px — card titles
--text-2xl: 1.5rem;      // 24px — page titles
--text-3xl: 1.875rem;    // 30px — hero headings
```

Instead, font sizes are declared inline (`font-size: 14px`, `font-size: 1.25rem`) across components. This leads to inconsistency.

---

## 1.4 Spacing System

### Current State ✅

The spacing scale is well-defined:

```scss
--space-xs:   4px;     // tight icon gaps
--space-sm:   8px;     // element-internal padding
--space-md:   16px;    // card padding, section gaps
--space-lg:   24px;    // page margins, section spacing
--space-xl:   32px;    // major section separation
--space-2xl:  48px;    // page-level spacing
--space-3xl:  64px;    // large viewport breaks
```

### Issues

- **Inconsistent usage** — Some pages use hardcoded padding (`padding: 20px`) instead of `var(--space-md)` or `var(--space-lg)`
- **No gap token for grid layouts** — Grid gaps mix `16px`, `20px`, `24px` across pages
- **Card padding varies** — Some cards use `24px`, others `16px`, creating visual inconsistency

---

## 1.5 Shadow System

### Current ✅

```scss
--shadow-sm:   0 1px 2px rgba(0,0,0,0.05);
--shadow-md:   0 4px 6px -1px rgba(0,0,0,0.1);
--shadow-lg:   0 10px 15px -3px rgba(0,0,0,0.1);
--shadow-xl:   0 20px 25px -5px rgba(0,0,0,0.1);
```

### Assessment

**Rule (UI/UX Pro Max): "Use shadow depth to communicate elevation"**
> ⚠️ Dark mode shadows use the same opacity values, making them too subtle on dark backgrounds. Dark mode should use higher opacity shadows or a white shadow tint (`rgba(255,255,255,0.05)`).

---

## 1.6 Glassmorphism Pattern

The project has a glassmorphism pattern defined:

```scss
.glass-panel {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.3);
}
```

### Assessment ✅
Good implementation. Glass panels are used effectively in:
- Marketplace setup cards (premium feel)
- Login page background
- Sub-merchant detail dialog

**Suggestion:** Add dark mode variant:
```scss
.dark-theme .glass-panel {
  background: rgba(15, 23, 42, 0.7);
  border-color: rgba(255, 255, 255, 0.08);
}
```

---

## 1.7 Color Palette Score Card

| Criteria | Score | Notes |
|----------|-------|-------|
| Cohesive brand identity | 8/10 | Orange + slate is a strong, warm-professional combo |
| WCAG AA text contrast | 5/10 | --text-muted fails; some status badge text may fail |
| Dark mode completeness | 6/10 | Chart colors, shadow tokens missing dark variants |
| Token coverage | 7/10 | Brand/surface/border covered; font/weight/opacity missing |
| Consistent application | 6/10 | Many inline values skip the token system |
| Distinctive character | 4/10 | Inter + slate is functional but generic |

**Overall: 6/10**
