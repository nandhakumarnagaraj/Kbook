# KhanaBook Responsive Specification

Every primary screen was designed against the following viewports:
**320, 375, 430, 768, 1024, 1366, 1440, 1920** CSS px. This document
records what the Angular build must preserve.

## Breakpoint semantics

| Name | Min width | Layout                                                      |
| ---- | --------: | ----------------------------------------------------------- |
| xs   |       320 | Base mobile — stacked cards, mobile drawer nav              |
| sm   |       640 | 2-col KPI, still card-style tables                          |
| md   |       768 | Table markup switches to full `<table>`; side-by-side forms |
| lg   |      1024 | Sidebar visible; 3-col chart+aside                          |
| xl   |      1280 | 4-col KPI row                                               |
| 2xl  |      1536 | Content max-width 1400 stays centred                        |

## Global responsive contract

1. **No page-level horizontal overflow at any breakpoint.** Never rely on
   the viewport to scroll horizontally. Only intentional inner scrollers
   (long tables in card containers, log viewers) may scroll.
2. **Sidebar → mobile drawer.** Below `lg`, the sidebar is hidden and
   accessed via a top-left `Menu` button that opens a CDK overlay drawer
   (focus trap, Escape closes, scrim click closes).
3. **Primary actions remain accessible.** Page-header actions wrap below
   the title on `<sm` using
   `grid-cols-[minmax(0,1fr)_auto] items-start gap-4` and never leave a
   primary CTA off-screen. Long titles use `truncate` inside `min-w-0`.
4. **Tables → mobile cards.** Every `KbDataTableComponent` has a paired
   `KbMobileDataCardComponent` rendered under `md`. Column templates are
   reused inside the mobile card body so headers/values stay in sync.
5. **Filters wrap.** `KbFilterToolbarComponent` uses `flex-wrap`; search
   takes the full row on `<sm`, filter chips wrap onto subsequent lines.
6. **Drawer fits.** Full-width under `sm`; fixed width thereafter
   (`420 / 560 / 720 px`).
7. **Dialog fits.** `w-[calc(100%-2rem)]` centred, capped at
   `sm 384 / md 448 / lg 512 px`.
8. **Long restaurant names.** Sidebar user block, topbar restaurant
   switcher, order-detail drawer title all wrap `min-w-0` + `truncate`.
9. **Large INR values readable.** All monetary values use
   `font-variant-numeric: tabular-nums`. KPI values right-align inside
   flex containers when a spark line sits beside them.
10. **Order IDs / device IDs.** Rendered in `font-mono` with
    `font-size: 11px` in table cells; never wrap.
11. **Status badges.** Icon + text; wrap inside mobile card headers via
    `flex flex-wrap gap-2`.
12. **Form fields.** Inputs are `h-11` on mobile (44-px hit target), `h-10`
    on desktop.
13. **Touch targets.** All buttons, sidebar items, tab pills, and toggles
    are ≥ 40 × 40 px; primary CTAs on mobile are ≥ 44 × 44 px.
14. **Sticky elements.** Topbar (`z-30`) does not cover content — the page
    header sits below it and pushes content down; sticky table headers
    (`z-20`) stay inside their card scroll container so they never occlude
    unrelated regions.

## Screen-by-screen notes

### Login (`/proto/login`)

- Two-pane split at `lg` (espresso brand + form).
- Below `lg`, brand pane hidden; single-column form with brand mark stacked above.

### Forgot password (`/proto/forgot`)

- Single centred column, `max-w-md`, at all breakpoints.
- 6-digit OTP inputs use `flex justify-between gap-2` so they distribute in a 320-px viewport without clipping.

### OWNER dashboard (`/proto/owner/dashboard`)

- 4-col KPI at `xl`, 2-col at `sm`, 1-col at `<sm`.
- Chart card and Setup-progress card sit side-by-side at `lg`, stack under.
- Recent-orders table → mobile card list under `md`.

### Orders (`/proto/owner/orders`)

- Filter toolbar wraps; refunded rows carry a red left-edge accent that survives the desktop→mobile transition (mobile card uses the same accent).
- Order-details drawer widens from full-width mobile → `lg` (720 px) on desktop.

### Menu (`/proto/owner/menu`)

- Grid view: 1 → 2 → 3 → 4 cols at `sm / lg / xl`.
- OCR review preview table is scroll-capped at `max-h-64` and independently scrolls inside the dialog.

### Staff (`/proto/owner/staff`)

- Desktop table; mobile card list with avatar + role badge stacked below name.

### Terminals (`/proto/owner/terminals`, `/proto/shop/terminals`)

- Tabs stay horizontal at all breakpoints (only 3 tabs, short labels).
- Recovery dialog widens to `md` (448 px) — token stays on one line via `tracking-wider font-mono text-lg`.

### Marketplace (`/proto/owner/marketplace`)

- 1-col at `<lg`, 2-col at `lg`.
- Webhook URL uses `overflow-x-auto` via input `text-xs font-mono` so long URLs don't wrap awkwardly.

### KBOOK_ADMIN dashboard (`/proto/admin/dashboard`)

- Same KPI responsiveness as OWNER, no hero variant.
- Top-businesses table + status card side-by-side at `lg`, stack under.

### KBOOK_ADMIN businesses (`/proto/admin/businesses`)

- Table has 8 columns at `md`+; mobile card collapses to name + owner + status badge + revenue.

## Known responsive limitations

- The prototype's fixed state-switcher pill overlaps mobile drawer content
  when both are open at the same time. Production must not include the
  state-switcher.
- The topbar restaurant switcher is a display-only stub; the Angular build
  will render the existing multi-branch dropdown that already supports
  keyboard navigation.
