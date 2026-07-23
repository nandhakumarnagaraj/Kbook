# OWNER dashboard

## Prototype route

`/proto/owner/dashboard`

## Existing production route

`/business/dashboard`

## Layout anatomy

- `kb-page-header` variant `owner-welcome` (Instrument Serif "Good morning, {name}").
- Hero KPI row (1 hero card + 3 default KPI cards).
- Two-column region: revenue chart (left, spans 2 columns on ≥ 1024px) + setup-progress card (right).
- Recent orders table (`kb-data-table`, compact) with a "View all" link to `/business/orders`.
- Quick-actions strip along the bottom (four `kb-button` variant `secondary` tiles).

## Shared components required

- `kb-app-shell`, `kb-page-header` (welcome variant), `kb-kpi-card` (both variants), `kb-data-table`, `kb-currency`, `kb-status-badge`, `kb-button`, `kb-skeleton`, `kb-empty-state`, `kb-error-state`.

## Unique page components

- `KbRevenueChartComponent` — thin wrapper around the existing Angular chart library (do NOT introduce a new chart lib; do NOT port the prototype SVG sparkline if a chart lib is already present).
- `KbSetupProgressCardComponent` — cream-gradient card (`--kb-gradient-cream`) with checklist of onboarding steps.
- `KbQuickActionTileComponent` — icon + label secondary tile.

## Desktop layout (≥ 1280px)

- KPI row: 4 columns (1 hero + 3 default).
- Chart + setup: 2/3 + 1/3.
- Recent orders: full-width table.
- Quick actions: 4 columns.

## Tablet layout (768–1279px)

- KPI row: 2 columns × 2 rows (hero spans full width on top).
- Chart + setup stack; setup card below.
- Recent orders: table with reduced columns.
- Quick actions: 2 columns × 2 rows.

## Mobile layout (< 768px)

- KPI cards stack full-width; hero first.
- Chart in a scrollable card.
- Setup progress next.
- Recent orders switches to `kb-mobile-data-card` list.
- Quick actions: 2 columns × 2 rows.

## Loading state

- KPI values → `kb-skeleton` rects.
- Chart → skeleton block matching chart height.
- Recent orders → 5 skeleton rows via `kb-data-table loading=true`.

## Empty state

- No orders yet: `kb-empty-state title="No orders yet" description="Once your terminals start taking payments, recent orders appear here."` with CTA linking to `/business/terminals`.

## Error state

- Any panel failure: replace only that panel with `kb-error-state` + retry that re-runs the panel's fetch (do not fail the whole page).

## Validation state

Not applicable.

## Success state

Data loads; hero KPI animates value once (respect `prefers-reduced-motion`).

## Sensitive actions

None.

## Accessibility requirements

- `<h1>` welcome heading; chart wrapped with an accessible summary (e.g. off-screen text "Revenue trend, last 7 days, currently ₹4,20,000").
- KPI trend arrows include text (`+12.4%`), never colour alone.
- Recent-orders row anchors go to the order detail — full-row link, single focusable element.
- `<main>` wraps the dashboard body; single `<h1>` per page.

## Existing functionality that must be retained

- All existing dashboard API calls (KPI aggregates, chart series, recent orders, setup checklist).
- Existing date-range control if present.
- Existing role restriction (OWNER only) via `roleGuard`.
- Existing quick-action link targets.

## Visual acceptance criteria

- Hero KPI uses `--kb-gradient-hero`, `--kb-shadow-warm`, `--kb-radius-2xl`, Instrument Serif value.
- Welcome heading uses Instrument Serif at `--kb-fs-welcome`.
- All other typography Inter.
- Setup progress uses `--kb-gradient-cream` — **this is the only other place gradients appear on the page**.
- No serif/gradient on chart, table, quick-actions.
