# Brand Guidelines — KhanaBook

## Brand Identity

KhanaBook is a modern restaurant management platform with a warm, trustworthy brand identity centered around the signature orange (#C85A00). The brand communicates reliability, warmth, and premium quality.

### Brand Color

**Primary Brand Color: `#C85A00`** (warm orange — appetite + trust + energy)

| Token | Hex | Usage |
|-------|-----|-------|
| `--brand` | `#C85A00` | Primary buttons, active links, key accents |
| `--brand-light` | `#E8832A` | Hover states, secondary brand elements |
| `--brand-dark` | `#994500` | Deep press states, gradients |
| `--brand-soft` | `rgba(200, 90, 0, 0.08)` | Subtle backgrounds, selection highlights |
| `--brand-glow` | `rgba(200, 90, 0, 0.16)` | Glow effects on hover/focus |

### Dark Mode Brand Override

```css
.dark-theme {
  --brand-soft: rgba(232, 131, 42, 0.1);
  --brand-glow: rgba(232, 131, 42, 0.2);
}
```

---

## Design Tokens

### Surface Colors

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `--bg` | `#F7F3EE` | `#17130F` | Page background |
| `--bg-elevated` | `#ffffff` | `#211A14` | Sidebar, elevated surfaces |
| `--panel` | `#ffffff` | `#211A14` | Cards, modals, panels |
| `--panel-hover` | `#FDF0E6` | `#2E1F0D` | Card hover state |
| `--surface` | `#EAE3DC` | `#332A22` | Secondary surfaces |
| `--surface-hover` | `#DDD5CC` | `#3E332A` | Surface hover state |

### Text Colors

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `--ink` | `#17130F` | `#F7F3EE` | Primary text |
| `--ink-secondary` | `#5C5248` | `#C1B7AD` | Secondary/label text |
| `--muted` | `#8C8075` | `#8E8276` | Placeholder, disabled text |

### Border Colors

| Token | Light | Dark |
|-------|-------|------|
| `--line` | `rgba(23, 19, 15, 0.08)` | `rgba(247, 243, 238, 0.08)` |
| `--line-strong` | `rgba(23, 19, 15, 0.16)` | `rgba(247, 243, 238, 0.16)` |

### Semantic Colors

| Token | Hex | Usage |
|-------|-----|-------|
| `--accent` | `#16A34A` | Success, positive metrics |
| `--accent-light` | `#22C55E` | Success hover |
| `--danger` | `#DC2626` | Errors, deletions, critical |
| `--warn` | `#D97706` | Warnings, pending states |
| `--info` | `#0284C7` | Informational |
| `--purple` | `#7c5cdb` | Special/feature highlights |

### Shadow Scale

```css
--shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
--shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
--shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
```

### Border Radius Scale

```css
--radius-sm: 6px;
--radius-md: 10px;
--radius-lg: 14px;
--radius-xl: 18px;
```

---

## Typography

- **Primary Font:** Inter (system-ui fallback)
- **Stack:** `"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
- **Font Smoothing:** `-webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale`

### Text Scale

| Element | Size | Weight | Letter-spacing |
|---------|------|--------|----------------|
| Page Title (h1) | 2rem–2.75rem | 800 | -0.5px to -1px |
| Section Title (h2) | 1.6rem | 800 | -0.5px |
| Card Title (h3) | 0.95rem–1.1rem | 700 | normal |
| Body Text | 14px (0.875rem) | 400 | normal |
| Small Text | 0.8rem | 400 | normal |
| Label Text | 0.75rem | 700 | 0.5px |
| Badge/Stat | 0.65rem | 800 | 1.5px |
| Page Hero Title | 2.75rem | 800 | -1px |

### Brand Voice

- Professional but warm
- Concise and direct (suitable for CLI/API context)
- Use emoji sparingly — only for status indicators (🚀, ✅, 🔄, 🔑, 🏷️, 📱)
- Restaurant industry terminology: "bills", "orders", "menu items", "staff", "settlements"

---

## Logo Usage

### Logo Files

| File | Usage |
|------|-------|
| `khanabook_logo.png` | Web admin sidebar (56×56), login page (96×96) |
| `khanabook_logo_header.png` | Login page showcase header, favicon, og:image |

### Logo Display Rules

- Always render with white background behind the logo (it may have transparent areas)
- Sidebar: 56×56, border-radius: 10px, box-shadow elevation
- Login page: 96×96, border-radius: 20px
- Always wrap in `<img>` with `object-fit: contain` and `image-rendering: auto`

---

## Glassmorphism Pattern

```css
.glass-panel {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px) saturate(120%);
  -webkit-backdrop-filter: blur(16px) saturate(120%);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: var(--shadow-md);
}

.dark-theme .glass-panel {
  background: rgba(33, 26, 20, 0.65);
  backdrop-filter: blur(16px) saturate(120%);
  border: 1px solid rgba(247, 243, 238, 0.05);
}
```

---

## Animation Tokens

```css
/* Fade In Up — page content entrance */
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-up {
  animation: fadeInUp 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

/* Shimmer — loading skeleton */
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.shimmer-bg {
  background: linear-gradient(90deg, var(--bg) 25%, var(--line-strong) 37%, var(--bg) 63%);
  background-size: 200% 100%;
  animation: shimmer 1.4s ease infinite;
}

/* Pulse Glow — accent elements */
@keyframes pulse-glow {
  0%, 100% { transform: scale(0.95); box-shadow: 0 0 0 0 var(--glow-color); }
  70% { transform: scale(1); box-shadow: 0 0 0 8px var(--glow-color-transparent); }
}

/* Hover transitions — default curve */
transition: all 0.2s ease;           /* quick micro-interactions */
transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);  /* card hover */
```

---

## Accessibility Requirements

- All interactive elements must have visible focus states
- Use `aria-label`, `aria-live="assertive"` for dynamic content
- Respect `prefers-reduced-motion`: disable animations when detected
- Color contrast: brand `#C85A00` on white = 3.1:1 (use for large text/decorative only)
- Use `--accent` (#16A34A) for actionable success states
- Google Sign-In button: never hardcode width, use `max-width: 100%` for responsive
