# KhanaBook Design Tokens

The canonical machine-readable source is `src/proto/design-tokens.json`. This
document is the human-readable mirror. Every value must be referenced by name
in Angular SCSS — no raw hex or arbitrary Tailwind colour utilities in
component templates.

## 1. Colour tokens (OKLCH, with sRGB approximation)

### Neutral & structural

| Token                | OKLCH                       | Hex         | Usage                        |
| -------------------- | --------------------------- | ----------- | ---------------------------- |
| `--background`       | `oklch(0.976 0.008 78)`     | `#FAF7F2`   | Application background       |
| `--surface`          | `oklch(1 0 0)`              | `#FFFFFF`   | Cards, popovers, sheets      |
| `--surface-2`        | `oklch(0.982 0.006 78)`     | `#F8F5EF`   | Table headers, raised subtle |
| `--foreground`       | `oklch(0.185 0.012 55)`     | `#1C1A17`   | Primary text                 |
| `--muted`            | `oklch(0.955 0.008 78)`     | `#F1EDE4`   | Chips, inline fill           |
| `--muted-foreground` | `oklch(0.48 0.012 55)`      | `#6B655C`   | Secondary text, labels       |
| `--border`           | `oklch(0.92 0.012 78)`      | `#EFE9DE`   | Dividers, input borders      |
| `--border-strong`    | `oklch(0.86 0.014 78)`      | `#E1D9CA`   | Emphasised divider           |
| `--input`            | `oklch(0.92 0.012 78)`      | `#EFE9DE`   | Input outline                |
| `--ring`             | `oklch(0.72 0.16 55 / 0.4)` | `#E87A1E66` | Focus ring                   |

### Brand

| Token                     | Hex       | Usage                                 |
| ------------------------- | --------- | ------------------------------------- |
| `--espresso`              | `#2A1F17` | Sidebar, strong ink                   |
| `--espresso-foreground`   | `#F1ECDF` | Cream on espresso                     |
| `--cream`                 | `#F5EFE3` | Warm neutral                          |
| `--saffron` / `--primary` | `#E87A1E` | Primary action / focus                |
| `--turmeric`              | `#E4B233` | Secondary warm                        |
| `--burnt`                 | `#D2643A` | Hero gradient end, hospitality accent |
| `--primary-hover`         | `#D66A18` | Primary hover state                   |
| `--primary-soft`          | `#FDF0E0` | Primary badge / soft fill             |

### Semantic

Each semantic colour ships with a foreground and a `-soft` variant used for
alert/badge backgrounds. Use tone + icon + text — never colour alone.

| Token            | Hex       | Usage                             |
| ---------------- | --------- | --------------------------------- |
| `--success`      | `#2F855A` | Paid · Active · Connected         |
| `--success-soft` | `#E7F4EC` | Success badge / alert bg          |
| `--warning`      | `#B7791F` | Pending · Incomplete              |
| `--warning-soft` | `#FBF3DF` | Warning badge / alert bg          |
| `--danger`       | `#C0392B` | Destructive · Refunded · Recovery |
| `--danger-soft`  | `#F9E7E3` | Danger badge / alert bg           |
| `--info`         | `#2B6CB0` | Informational · plan badges       |
| `--info-soft`    | `#E5EEF7` | Info badge / alert bg             |

### Sidebar (espresso rail)

| Token                         | Hex       |
| ----------------------------- | --------- |
| `--sidebar`                   | `#2A1F17` |
| `--sidebar-foreground`        | `#EFE9DE` |
| `--sidebar-muted`             | `#9E9689` |
| `--sidebar-active`            | `#332619` |
| `--sidebar-active-foreground` | `#FFFFFF` |
| `--sidebar-border`            | `#3A2C1E` |

### Gradients (restricted use)

| Token              | Value                                       | Usage                                          |
| ------------------ | ------------------------------------------- | ---------------------------------------------- |
| `--gradient-hero`  | `linear-gradient(135deg, #E87A1E, #D2643A)` | **OWNER dashboard hero KPI only**              |
| `--gradient-cream` | `linear-gradient(180deg, #FBF7EF, #F5EFE3)` | **OWNER dashboard "Setup progress" card only** |

## 2. Typography

| Token            | Value                                                     | Usage                                                                               |
| ---------------- | --------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `--font-sans`    | `Inter, ui-sans-serif, system-ui, sans-serif`             | Everything — all pages, tables, forms                                               |
| `--font-display` | `Instrument Serif, Iowan Old Style, Georgia, serif`       | **OWNER dashboard only:** welcome heading, hero KPI value, "Setup progress" heading |
| `--font-mono`    | `JetBrains Mono, ui-monospace, SF Mono, Menlo, monospace` | Order IDs, device IDs, keys, references                                             |

### Type scale

| Style         | Family | Size |      Weight | Tracking         | Example                |
| ------------- | ------ | ---: | ----------: | ---------------- | ---------------------- |
| meta-label    | sans   |   11 |         600 | 0.05em uppercase | `STATUS`               |
| caption       | sans   |   11 |         400 | –                | `Expires in 10:00`     |
| body-xs       | sans   |   12 |         400 | –                | Table meta             |
| body-sm       | sans   |   13 |         400 | –                | Table cells            |
| body          | sans   |   14 |         400 | –                | Body copy              |
| body-strong   | sans   |   14 |         500 | –                | Emphasised body        |
| section-title | sans   |   18 |         600 | -0.01em          | "Revenue trend"        |
| page-title    | sans   |   24 |         600 | -0.02em          | "Orders"               |
| kpi-value     | sans   |   30 | 600 tabular | -0.02em          | `₹1,42,00,000`         |
| hero-value    | serif  |   38 |         400 | –                | OWNER hero KPI only    |
| welcome       | serif  |   32 |         400 | –                | "Good afternoon, Ravi" |

## 3. Spacing (4-px scale)

| Token      |  px |
| ---------- | --: |
| `space-1`  |   4 |
| `space-2`  |   8 |
| `space-3`  |  12 |
| `space-4`  |  16 |
| `space-5`  |  20 |
| `space-6`  |  24 |
| `space-8`  |  32 |
| `space-10` |  40 |
| `space-12` |  48 |
| `space-16` |  64 |

## 4. Radius

| Token          |   px | Usage                        |
| -------------- | ---: | ---------------------------- |
| `--radius-sm`  |    4 | Chips, tight badges          |
| `--radius-md`  |    6 | Denser inputs, small buttons |
| `--radius-lg`  |    8 | Inputs default, buttons      |
| `--radius-xl`  |   12 | Cards, tables, drawers       |
| `--radius-2xl` |   16 | OWNER hero KPI, warm cards   |
| pill / full    | 9999 | State switcher, chip filters |

## 5. Shadow

| Token           | Usage                   |
| --------------- | ----------------------- |
| `--shadow-xs`   | Cards at rest           |
| `--shadow-sm`   | Card hover, dropdown    |
| `--shadow-md`   | Sticky pill, toast      |
| `--shadow-lg`   | Dialog, drawer          |
| `--shadow-warm` | **OWNER hero KPI only** |

## 6. Breakpoints

| Name | Min width | Behaviour                                      |
| ---- | --------: | ---------------------------------------------- |
| xs   |       320 | Base mobile — stacked cards, mobile drawer nav |
| sm   |       640 | 2-column KPI, still card-style tables          |
| md   |       768 | Tables switch to full rows; side-by-side forms |
| lg   |      1024 | Sidebar visible; 3-col chart+aside             |
| xl   |      1280 | 4-col KPI row                                  |
| 2xl  |      1536 | Content max-width 1400 stays centred           |

## 7. Motion

| Duration |  ms | Usage                       |
| -------- | --: | --------------------------- |
| instant  |  80 | Hover feedback              |
| fast     | 150 | Colour, small transforms    |
| base     | 200 | Dropdowns, pills, toggles   |
| slow     | 300 | Drawer, dialog fade + slide |

| Easing     | Curve                          |
| ---------- | ------------------------------ |
| standard   | `cubic-bezier(0.2, 0, 0.2, 1)` |
| decelerate | `cubic-bezier(0, 0, 0.2, 1)`   |
| accelerate | `cubic-bezier(0.4, 0, 1, 1)`   |

Reduced-motion: `@media (prefers-reduced-motion: reduce)` — remove transforms,
keep opacity under 100 ms.

## 8. Z-index layers

| Token    | Value | Usage                                  |
| -------- | ----: | -------------------------------------- |
| base     |     0 | Content                                |
| raised   |    10 | Sticky page-header content             |
| sticky   |    20 | Sticky table header                    |
| topbar   |    30 | App topbar                             |
| backdrop |    40 | Dialog/drawer scrim                    |
| modal    |    50 | Dialog/drawer surface + state switcher |
| toast    |    60 | Toast stack                            |

## 9. Layout constants

| Constant                 |                          Value |
| ------------------------ | -----------------------------: |
| Sidebar width            |                         240 px |
| Sidebar collapsed (rail) | 64 px (spec only, not shipped) |
| Topbar height            |                          56 px |
| Content max-width        |                        1400 px |
| Page padding — mobile x  |                          16 px |
| Page padding — desktop x |                          32 px |
| Page padding — y         |                          24 px |
| Card padding (default)   |                          20 px |
| Drawer widths (sm/md/lg) |             420 / 560 / 720 px |
| Dialog widths (sm/md/lg) |             384 / 448 / 512 px |
| Minimum touch target     |                     40 × 40 px |
| Primary CTA on mobile    |      ≥ 44 × 44 px (`min-h-11`) |

## 10. Icons

Icon library: **lucide** (matches the prototype). Angular projects should use
`lucide-angular`. Icon sizes: 12 · 14 · 16 · 20 · 24 px. Icons in badges are
14 px, in KPI cards 20 px, in empty states 24 px. Icons in interactive
elements without visible text require `aria-label` on the parent button.
