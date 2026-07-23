# Batch 04 — OWNER dashboard & reports

## Scope

Restyle `/business/dashboard` and `/business/reports` using Batch 02 primitives. The Warm Kitchen accent (hero KPI gradient + `Instrument Serif` welcome heading) applies to the OWNER dashboard **only**. Reports stays operational and neutral.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/03-owner-dashboard.md`
- `docs/angular-handoff/page-migrations/04-reports.md`
- Rendered prototypes: `/proto/owner/dashboard`, `/proto/owner/reports`.
- Existing dashboard / reports components, services, DTOs, chart wrapper, date-range picker.

## Actions

1. Confirm Batch 03 committed and passing.
2. Dashboard: welcome heading (Instrument Serif) + hero KPI (`--kb-gradient-hero`, `--kb-shadow-warm`, `--kb-radius-2xl`) + 3 default KPI cards + two-column chart/setup + recent-orders table + quick actions.
3. Reports: `KbPageHeader` + date-range control in actions slot + 4 default KPI cards + revenue trend (reuse existing chart lib) + payment-mode breakdown (soft list, not a new chart) + daily breakdown table with mobile card fallback.
4. Reuse the existing Angular chart library — do NOT port the prototype SVG or introduce a new chart dep.
5. Reuse the existing date-range picker — do NOT introduce a new one.

## Preservation rules

- Dashboard KPI / chart / recent-orders / setup endpoints: **Unchanged**.
- Reports date-query parameter names + formats: **Unchanged**.
- Export mechanism (if present): **Unchanged**.
- OWNER role restriction via existing guard: **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, unit specs, `npm run build`.
- Runtime: load dashboard + reports for an OWNER tenant; capture network requests and confirm URLs / query params / payloads match Batch 01 baseline.
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1366 / 1440 / 1920 — verify no horizontal overflow, hero KPI legibility, table → mobile card transition on reports.
- Accessibility: single `<h1>`, chart summary alt-text, KPI trend text (not colour-only), keyboard tab order.

## Required report

```
Batch: 04 OWNER dashboard & reports
Routes migrated: /business/dashboard, /business/reports
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (date query names + formats verified)
Routes or guards changed: Unchanged
Validation changed: Unchanged
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: PASS|PARTIALLY TESTED (list requests captured)
Responsive tested: <viewports>
Accessibility tested: <what was actually exercised>
Known issues: <list or none>
Commit: style(web-admin): migrate owner dashboard and reports to Service Pass
```

## Stop conditions

- Warm Kitchen accent (gradient / serif) leaks onto reports or any operational surface.
- Chart data source or shape changes.
- Date-range control emits different query params than pre-migration.

## Suggested commit

`style(web-admin): migrate owner dashboard and reports to Service Pass`
