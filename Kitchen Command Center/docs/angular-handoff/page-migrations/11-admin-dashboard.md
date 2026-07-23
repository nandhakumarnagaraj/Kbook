# KBOOK_ADMIN dashboard

## Prototype route

`/proto/admin/dashboard`

## Existing production route

`/admin/dashboard`

## Layout anatomy

- `kb-page-header title="Platform overview"` (neutral, no welcome variant).
- Platform KPI row (5–6 default cards: total businesses, active businesses, MRR, orders today, terminals online, pending activation requests).
- Two-column region: top businesses by revenue (table) + status breakdown (compact table or list).

## Shared components required

All shell primitives, `kb-kpi-card` (default), `kb-data-table`, `kb-status-badge`, `kb-currency`, `kb-button`, `kb-skeleton`, `kb-empty-state`, `kb-error-state`.

## Unique page components

- `KbPlatformKpiRowComponent` — layout helper.
- `KbTopBusinessesTableComponent` — thin wrapper over `kb-data-table`.

## Desktop layout

- KPI row: 5 or 6 columns.
- Two-column region.

## Tablet layout

- KPI row: 2 or 3 columns.
- Two-column region stacks.

## Mobile layout

- KPI stack; both tables become mobile card lists.

## Loading / empty / error

- Loading: KPI + table skeleton.
- Empty: not typical; if a table is empty, show `kb-empty-state`.
- Error: retry.

## Restricted state

- If the current user is not KBOOK_ADMIN, the existing `roleGuard` redirects them away; the page itself does not need a "restricted" render because the guard runs first.
- If the guard cannot decide (e.g. slow claims fetch), show a page-level `kb-skeleton` while resolving; never render admin data before the guard confirms.

## Validation state

Not applicable.

## Success state

Data loads.

## Sensitive actions

None on this page.

## Accessibility requirements

- `<h1>` = "Platform overview".
- KPI trend indicators use text + colour.
- Tables use `<caption class="visually-hidden">` for context.

## Existing functionality that must be retained

- Every existing platform KPI endpoint.
- Existing role restriction (KBOOK_ADMIN) via `roleGuard`.
- Existing refresh cadence, if any.

## Visual acceptance criteria

- Neutral, executive tone — no gradients, no serif.
- KPI cards flat with `--kb-shadow-xs`.
- Numeric emphasis via tabular-nums, not colour.
