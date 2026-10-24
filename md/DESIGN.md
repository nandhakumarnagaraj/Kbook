# KhanaBook — Design System & UI/UX Specification (DESIGN.md)

**Document Version:** 2.1.0  
**Status:** Single Source of Truth for Visual Design & UX (Verified against Codebase Ground Truth)  
**Design Philosophy:** Purpose-Built Hospitality Utility, Tactical Clarity, High Contrast  

---

## 1. Visual Direction & Design Principles

1. **Utility-First, Not Generic SaaS:** Designed for fast-paced, high-stress kitchen and restaurant counter environments. Controls must be clear, high-contrast, and impossible to misclick.
2. **Predictable Semantic Color System:** Colors convey exact operational state. Blue is primary action, Green is active/success, Amber is caution/pending, Red is danger/error. No leftover purple or arbitrary decorative gradients.
3. **Information Density with Breathable Tables:** High-density data tables must remain legible with uniform row heights (~52px), inline horizontal action stacks, and no awkward multi-line row ballooning.
4. **Accessible by Default:** All text, badges, and toggle states must strictly comply with WCAG 2.1 AA contrast ratios (minimum 4.5:1 for body text, 3:1 for large headings and UI controls).

---

## 2. Color Palette & Design Tokens

> **Web Admin palette revision (2026-10-25).** The values below are the ones
> `apps/web-admin/src/styles.css` actually ships: a dark, low-glare command
> centre. They replace the warm-cream light palette this document recorded,
> which no longer existed in code. Android remains a separate palette — see the
> Android section. Only one colour source of truth exists per app.

### 2.1 Brand & Interactive Tokens
* **Primary Brand Blue (fill):** `--kb-color-primary: #2563EB;` — primary buttons,
  active navigation, selected pills. White content on it = 5.17:1.
* **Primary Hover:** `--kb-color-primary-hover: #1D4ED8;`
* **Primary Foreground (on the fill):** `--kb-color-primary-foreground: #FFFFFF;`
* **Link / brand-as-text:** `--kb-color-link: #60A5FA;` (hover `#93C5FD`) — links,
  active tab labels, brand-coloured icons. The fill colour only reaches 3.4:1 as
  text on a card, so text never uses `--kb-color-primary`.
* **Focus ring:** `rgba(var(--kb-color-primary-rgb), 0.45)` halo, plus a solid
  2px outline in `--kb-color-primary`.

### 2.2 Semantic Status Tokens
Each status has a text/icon ink tuned to sit on its own tint, and a solid fill
for badges and destructive buttons that carry white content.

| Token | Value | On its tint |
|---|---|---|
| `--kb-color-success` / `-soft` | `#34D399` / `rgba(52, 211, 153, 0.14)` | 6.9:1 |
| `--kb-color-warning` / `-soft` | `#FBBF24` / `rgba(251, 191, 36, 0.14)` | 7.8:1 |
| `--kb-color-danger` / `-soft` | `#F87171` / `rgba(248, 113, 113, 0.14)` | 5.2:1 |
| `--kb-color-info` / `-soft` | `#60A5FA` / `rgba(96, 165, 250, 0.14)` | 5.4:1 |
| `--kb-color-danger-solid` / `-hover` | `#DC2626` / `#B91C1C` | white on it: 4.8:1 |

### 2.3 Surface & Neutral Tokens
Surfaces are separated by a deliberate lightness step **and** a border. A
near-identical tint alone reads as one flat plane.

| Role | Token | Value |
|---|---|---|
| Page canvas | `--kb-color-bg-app` | `#0B0E1A` |
| Cards, tables, panels | `--kb-color-surface` | `#15192C` |
| Table header, hover, nested tiles | `--kb-color-surface-2` | `#1F2440` |
| Modal, popover, raised controls | `--kb-color-surface-3` | `#272D50` |
| Sidebar rail | `--pos-sidebar-bg` | `#12162A` |
| Recessed input / well | `--kb-color-muted` | `#101425` |
| Dividers, card edges | `--kb-color-border` | `#343B66` |
| Control borders | `--kb-color-border-strong` | `#4C5590` |
| Primary text | `--kb-color-foreground` | `#F3F6FB` (16.1:1 on a card) |
| Values, table cells | `--kb-color-foreground-2` | `#C3CBDA` (10.7:1) |
| Labels, metadata | `--kb-color-muted-foreground` | `#93A0B8` (6.6:1) |

**Rules.** Neutral surfaces do the structural work; brand blue is reserved for
the single primary action per screen plus focus; semantic colours appear only
inside their meaning; status is never communicated by colour alone (the label
or glyph always carries it too). Component stylesheets consume tokens — they do
not declare their own palette.

---

## 3. Typography System

* **Heading Font Family:** `"Plus Jakarta Sans", Inter, -apple-system, sans-serif`
* **Body Font Family:** `Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`
* **Tabular / Monospace Numbers:** `'SF Mono', 'Roboto Mono', Menlo, monospace` (For currencies, bills, order numbers, phone numbers)

| Token | Size | Line Height | Weight | Typical Application |
| :--- | :--- | :--- | :--- | :--- |
| **Display / H1** | 36px – 42px | 1.15 | 700 | Hero Login Titles, Splash Screens |
| **Page Title / H2** | 24px – 28px | 1.25 | 650 | Top-level section headings |
| **Card Header / H3** | 18px – 20px | 1.30 | 600 | Card titles, Modal headers |
| **Section Label / H4** | 15px – 16px | 1.40 | 600 | Panel subtitles, Table section dividers |
| **Body Large** | 15px | 1.50 | 400 / 500 | Form inputs, modal field values |
| **Body Regular** | 14px | 1.50 | 400 / 500 | Standard table cells, descriptions |
| **Caption / Small** | 12px – 13px | 1.40 | 500 | Badges, status pills, table headers |
| **Micro / Eyebrow** | 10.5px – 11.5px | 1.30 | 700 (Uppercase)| Eyebrow labels, compact counter badges |

---

## 4. Reusable UI Components

### 4.1 Buttons
* **Primary Button (`.primary-btn`, `.header-action-btn--primary`):**
  Solid blue background (`#0066cc`), crisp white text (`#ffffff`), `12px` border-radius, `min-height: 40px` (or `52px` for auth).
* **Secondary / Ghost Button (`.ghost-btn`, `.header-action-btn`):**
  White surface, border `#efe9de`, text `#1c1a17`. Hover applies subtle warm background `rgba(0, 0, 0, 0.04)`.
* **Danger Ghost Button (`.ghost-btn.danger-btn`):**
  White surface, light red border `rgba(192, 57, 43, 0.35)`, text `#c0392b`. Hover applies `rgba(192, 57, 43, 0.08)`.
* **Sidebar Sign Out (`.logout-btn`):**
  Secondary ghost button (`background: transparent; color: var(--kb-color-muted); border: 1px solid var(--kb-color-border)`). On hover, gently transitions to light danger tint (`color: #c0392b; background: rgba(192, 57, 43, 0.08)`).
* **Disabled Button State:**
  Neutral background (`#e5e7eb`), muted gray text (`#9ca3af`), cursor `not-allowed`, no box shadow. Never use washed-out purple or low-contrast tints.

### 4.2 Status Badges & Pills
* **Active / Success Badge:** `background: rgba(47, 133, 90, 0.10); color: #2f855a; font-weight: 600; font-size: 11.5px;`
* **Inactive / Pending Badge:** `background: #e5e7eb; color: #374151; font-weight: 600; font-size: 11.5px;` (Guarantees >= 4.5:1 contrast against surface).
* **Role Pills:**
  * Owner: `rgba(0, 102, 204, 0.10)` background, `#0066cc` text.
  * Staff: `#f4efe6` background, `#1c1a17` text.
* **White-Space Rule:** Badges must always specify `white-space: nowrap;` to prevent breaking across multiple lines.

### 4.3 Toggle Buttons (`.toggle-btn`)
* **State `[On]` / `[Available]`:**
  `background: #0066cc; border-color: #0066cc; color: #ffffff; font-weight: 600;`
  *(Strictly avoids white-on-white text).*
* **State `[Off]` / `[Unavailable]`:**
  `background: #ffffff; border-color: #c0392b; color: #c0392b; font-weight: 500;`

### 4.4 Data Tables (`.table-wrap table`)
* **Headers (`th`):** Uppercase, `font-size: 11.5px; letter-spacing: 0.05em; color: var(--kb-color-muted); font-weight: 650;`
* **Row Height:** Standard rows maintain ~`52px` height.
* **Action Stacks (`.action-stack`, `.action-cell`):**
  Must use `display: flex; gap: 0.4rem; align-items: center; flex-wrap: nowrap; white-space: nowrap;` so buttons never stack vertically and inflate row height.
* **First Column Padding:** Minimum `1.25rem` padding to ensure zero left-edge clipping.

### 4.5 Modals & Dialogs (`.modal-box`)
* **Desktop Viewport Fit:**
  `max-height: min(88vh, 760px); overflow-y: auto; max-width: 520px; width: 100%; border-radius: 20px; padding: 1.5rem;`
* **Header & Footer:**
  Sticky header with title and close button (`×`); fixed footer actions ("Cancel", "Save / Add Item") that stay accessible even when the form content scrolls.

### 4.6 Forms & Inputs
* **Single-line Inputs (`.field-control`):** `height: 44px; border: 1px solid var(--kb-color-border-strong); border-radius: 10px; font-size: 14.5px;`
* **Addresses & Multi-line Text:**
  Must use `<textarea class="field-control" rows="3">` with `min-height: 72px; resize: vertical;` to prevent text truncation.
* **Keyboard Focus:**
  `outline: 3px solid rgba(0, 102, 204, 0.25); outline-offset: 2px; border-color: #0066cc;`

---

## 5. Loading, Empty & Error States

### 5.1 Empty States (`.empty-state`, `.all-clear-card`)
* Rendered when orders, staff, or menu categories have zero items.
* Includes an intuitive icon/graphic, helpful friendly explanation, and an immediate primary CTA button (e.g. "Add First Dish" or "View Order History").

### 5.2 Custom 404 KOT Error Ticket (`.receipt-404-card`)
* Replaces sterile browser 404s with a branded kitchen order ticket theme:
  `TERMINAL #01 • KITCHEN TICKET #404 - VOID / 404`
  Explains the route does not exist and provides a clear "Return to POS Dashboard" fallback button.

---

## 6. Responsive Behavior & Viewport Breakpoints

| Breakpoint | Window Width | Layout Strategy |
| :--- | :--- | :--- |
| **Desktop** | `>= 1024px` | Fixed `240px` left sidebar navigation; full multi-column grid layouts; comprehensive data tables. |
| **Tablet** | `768px – 1023px` | Collapsible sidebar; horizontally scrolling data tables with sticky primary column. |
| **Mobile** | `< 768px` (Target 375×812) | Hidden sidebar; slide-out drawer triggered by accessible top hamburger button; data tables adapt into clean mobile data cards (`.mobile-data-card`). |

### Mobile Drawer Rule
The drawer container and backdrop must maintain strict `z-index` layering (`z-index: 99` for backdrop, `z-index: 100` for drawer panel, `z-index: 101` for toggle) so pointer events never block subsequent interactions or prevent closing the drawer.

---

## 7. Accessibility Checklist (WCAG 2.1 AA)

- [x] All body text meets at least 4.5:1 contrast against surface.
- [x] All large text and interactive UI controls meet at least 3:1 contrast.
- [x] Active toggle states (`[On]` / `[Available]`) have high contrast labels.
- [x] Skip link (`"Skip to main content"`) is the first focusable element.
- [x] All interactive controls have visible keyboard focus rings.
- [x] Icon-only buttons include `aria-label` or `title` attributes.
- [x] Zero horizontal overflow on mobile viewports down to 375px width.
