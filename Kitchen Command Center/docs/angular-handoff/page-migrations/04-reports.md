# Reports

## Prototype route

`/proto/owner/reports` — rendered reference with `ready`, `loading`, `refreshing`, `empty`, and `error` states. Includes the four summary cards (recognized revenue, bills recorded, pending payments, net revenue), a revenue-trend visualization, a payment-mode breakdown (soft list), and a daily-breakdown table with a mobile card fallback.

## Existing production route

`/business/reports`

## Layout anatomy

- `kb-page-header title="Reports"` with date-range control in `[slot="actions"]`.
- Summary KPI row (4 default cards — no hero variant here).
- Financial breakdown table.
- Downloads / export action row.

## Shared components required

- `kb-app-shell`, `kb-page-header` (default), `kb-kpi-card` (default only — no hero, no gradient), `kb-data-table`, `kb-currency`, `kb-filter-toolbar`, `kb-button`, `kb-skeleton`, `kb-empty-state`, `kb-error-state`.

## Unique page components

- `KbDateRangeControlComponent` — reuse the existing Angular date-picker; do not introduce a new one.
- `KbReportsExportButtonComponent` — wraps `kb-button` with the existing export service.

## Desktop layout

- KPI row: 4 columns.
- Table full-width.
- Export button in header actions and above the table.

## Tablet layout

- KPI row: 2 × 2.
- Table with reduced columns.

## Mobile layout

- KPI stack full-width.
- Table becomes `kb-mobile-data-card` list.

## Loading / empty / error

- Loading: KPI + table skeleton.
- Empty: `kb-empty-state` when the selected date range has no data ("No reports for this range" with a "Reset range" CTA).
- Error: `kb-error-state` with retry that re-runs the report fetch.

## Validation state

- Date range: end date must be ≥ start date; use existing Angular validators.
- Export button disabled while a fetch is in flight.

## Success state

- Data loads.
- Export button returns to idle after download; a toast confirms "Report downloaded" (success tone).

## Sensitive actions

None.

## Accessibility requirements

- `<h1>` = "Reports".
- Date range control announces its selected range in an off-screen live region.
- Numeric columns right-aligned with tabular-nums.
- Export button includes an accessible label ("Export report as CSV").

## Existing functionality that must be retained

- All existing report endpoints, payloads, and export mechanism.
- Existing date-range defaults and boundaries.
- Existing role restriction (OWNER).

## Visual acceptance criteria

- Neutral, operational — no gradients, no serif.
- KPI cards flat white with `--kb-shadow-xs`.
- Table density `comfortable` on desktop; `compact` acceptable if the project already uses that.
