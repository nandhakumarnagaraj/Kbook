# Theme Factory — KhanaBook

## Overview

Generates complete dark/light theme CSS variable systems from a single brand hex color. The KhanaBook theme system uses CSS custom properties with both light (`:root`) and dark (`.dark-theme`) variants.

---

## Architecture

```
ThemeService (Angular)
  ↓ stores in localStorage: 'khanabook-theme'
  ↓ toggles .dark-theme class on <body>
  ↓ uses prefers-color-scheme for initial detection

styles.scss
  ↓ :root { ... } — light theme variables
  ↓ .dark-theme { ... } — dark theme variable overrides
  ↓ @media (prefers-reduced-motion: reduce) — accessibility
```

---

## Theme Variable Structure

### Generated Variable Groups

Every theme must define these categories:

```
1. Surface Colors    -- bg, bg-elevated, panel, panel-hover, surface, surface-hover
2. Text Colors       -- ink, ink-secondary, muted
3. Border Colors     -- line, line-strong
4. Brand Colors      -- brand, brand-light, brand-dark, brand-soft, brand-glow
5. Semantic Colors   -- accent, danger, warn, info, purple (each with -soft variant)
6. Shadows           -- shadow-sm, shadow-md, shadow-lg, shadow-xl
7. Radii             -- radius-sm, radius-md, radius-lg, radius-xl
```

### Light Theme Base

```css
:root {
  /* Surfaces — warm off-white */
  --bg: #F7F3EE;
  --bg-elevated: #ffffff;
  --panel: #ffffff;
  --panel-hover: #FDF0E6;
  --surface: #EAE3DC;
  --surface-hover: #DDD5CC;

  /* Text — dark warm */
  --ink: #17130F;
  --ink-secondary: #5C5248;
  --muted: #8C8075;

  /* Borders — subtle dark */
  --line: rgba(23, 19, 15, 0.08);
  --line-strong: rgba(23, 19, 15, 0.16);
}
```

### Dark Theme Inversion Formula

```css
.dark-theme {
  /* Surfaces — invert lightness */
  --bg: darken(#F7F3EE) → #17130F;        /* ~10% lightness */
  --bg-elevated: darken(#ffffff) → #211A14;
  --panel: darken(#ffffff) → #211A14;
  --panel-hover: warm highlight → #2E1F0D;
  --surface: darken(#EAE3DC) → #332A22;
  --surface-hover: → #3E332A;

  /* Text — invert */
  --ink: lighten(#17130F) → #F7F3EE;
  --ink-secondary: lighten(#5C5248) → #C1B7AD;
  --muted: keep similar → #8E8276;

  /* Borders — lighter on dark */
  --line: rgba(247, 243, 238, 0.08);
  --line-strong: rgba(247, 243, 238, 0.16);

  /* Brand — slightly lighter for visibility */
  --brand-soft: rgba(232, 131, 42, 0.1);
  --brand-glow: rgba(232, 131, 42, 0.2);
}
```

---

## Brand Color Generation

Given a primary brand hex (e.g., `#C85A00`), generate:

```css
--brand: #C85A00;           /* Original */
--brand-light: #E8832A;     /* Lighten by ~20% */
--brand-dark: #994500;      /* Darken by ~20% */
--brand-soft: rgba(200, 90, 0, 0.08);  /* At 8% opacity */
--brand-glow: rgba(200, 90, 0, 0.16);  /* At 16% opacity */
```

> **See also:** `brand-guidelines` skill for the complete brand color token hierarchy, dark mode brand overrides, and WCAG contrast requirements.

---

## Semantic Color Generation

Generate semantic colors that complement the brand:

```css
/* Green — success */
--accent: #16A34A;
--accent-light: #22C55E;
--accent-soft: rgba(22, 163, 74, 0.08);

/* Red — danger/error */
--danger: #DC2626;
--danger-soft: rgba(220, 38, 38, 0.08);

/* Amber — warning */
--warn: #D97706;
--warn-soft: rgba(217, 119, 6, 0.08);

/* Blue — info */
--info: #0284C7;
--info-soft: rgba(2, 132, 199, 0.08);

/* Purple — special */
--purple: #7c5cdb;
--purple-soft: rgba(124, 92, 219, 0.1);
```

---

## Shadow Generation

```css
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
--shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
--shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
```

In dark mode, shadows are naturally less visible — increase opacity by 1.5–2x if needed.

---

## Angular Material Theme Integration

```scss
@use '@angular/material' as mat;

// Define palettes using Material Design built-in palettes
$khanabook-primary: mat.m2-define-palette(mat.$m2-orange-palette, 800);
$khanabook-accent: mat.m2-define-palette(mat.$m2-blue-grey-palette, A200, A100, A400);
$khanabook-warn: mat.m2-define-palette(mat.$m2-red-palette);

// Light theme
$light-theme: mat.m2-define-light-theme((
  color: (primary: $khanabook-primary, accent: $khanabook-accent, warn: $khanabook-warn),
  typography: mat.m2-define-typography-config(),
  density: 0,
));

// Dark theme
$dark-theme: mat.m2-define-dark-theme((
  color: (primary: $khanabook-primary, accent: $khanabook-accent, warn: $khanabook-warn),
  typography: mat.m2-define-typography-config(),
  density: 0,
));

@include mat.all-component-themes($light-theme);
.dark-theme { @include mat.all-component-colors($dark-theme); }
```

---

## Glassmorphism Theme Variants

### Light Mode Glass

```css
.glass-panel {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px) saturate(120%);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: var(--shadow-md);
}
```

### Dark Mode Glass

```css
.dark-theme .glass-panel {
  background: rgba(33, 26, 20, 0.65);
  backdrop-filter: blur(16px) saturate(120%);
  border: 1px solid rgba(247, 243, 238, 0.05);
}
```

---

## Accessibility: Reduced Motion

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

---

## ThemeService Pattern

```typescript
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly THEME_KEY = 'khanabook-theme';
  isDarkMode = signal<boolean>(this.loadTheme());

  constructor() {
    effect(() => {
      const isDark = this.isDarkMode();
      document.body.classList.toggle('dark-theme', isDark);
      localStorage.setItem(this.THEME_KEY, isDark ? 'dark' : 'light');
    });
  }

  toggleTheme() { this.isDarkMode.update(v => !v); }

  private loadTheme(): boolean {
    const saved = localStorage.getItem(this.THEME_KEY);
    return saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
  }
}
```
