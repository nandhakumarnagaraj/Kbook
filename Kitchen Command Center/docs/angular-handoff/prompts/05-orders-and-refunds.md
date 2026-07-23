# Batch 05 — Orders & refunds

## Scope

Restyle `/business/orders` using Batch 02 primitives. Preserve the orders list, order-detail, and manual-refund contracts, including refund validation, refund limits, and duplicate-submit prevention.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/05-orders.md`
- `docs/angular-handoff/components/kb-data-table.md`, `kb-mobile-data-card.md`, `kb-drawer.md`, `kb-confirmation-dialog.md`
- Prototype: `/proto/owner/orders`.
- Existing orders component, refund form, service methods, DTOs.

## Actions

1. Confirm Batch 04 committed and passing.
2. Orders list: filter toolbar (search, status chips, date, terminal), `KbDataTable` on ≥ 768px, `KbMobileDataCard` list below. Order IDs in `--kb-font-mono`.
3. Order-detail drawer projected via CDK `Overlay` + `FocusTrap`.
4. Refund dialog: `KbConfirmationDialog role="alertdialog"`, `entityLabel`, consequence text, `confirmLabel="Refund order"`, tone `danger`. State machine `idle → submitting → success | error`. Dialog non-dismissible while submitting; confirm disabled + `aria-busy`; on error re-enable retry.
5. Do NOT change the `RefundOrderRequest` DTO. Verify amount units (paise vs rupees) match Batch 01 baseline exactly.

## Preservation rules

- Orders list / detail / refund endpoints and query params: **Unchanged**.
- `RefundOrderRequest` field names + types + units: **Unchanged**.
- Refund amount / reason validators: **Unchanged**.
- Existing role restriction: **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, orders-related unit specs, `npm run build`.
- Runtime against a safe tenant: filter, open detail drawer, attempt refund happy path + rejected path + duplicate-submit attempt (double-click confirm should submit exactly once).
- Network capture: confirm refund request body matches baseline payload exactly. Redact PII.
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1366 / 1920.
- Accessibility: drawer focus trap, Escape close, focus restoration to invoking row; refund dialog default focus on Cancel; live-region announcement on success.

## Required report

```
Batch: 05 Orders & refunds
Routes migrated: /business/orders
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (RefundOrderRequest verified byte-for-byte against Batch 01)
Routes or guards changed: Unchanged
Validation changed: Unchanged
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: <describe: refund happy path + reject + duplicate-submit>
Responsive tested: <viewports>
Accessibility tested: drawer focus trap=PASS|FAIL, refund dialog default focus=PASS|FAIL, aria-busy on submit=PASS|FAIL, live region=PASS|FAIL
Known issues: <list or none>
Commit: style(web-admin): migrate orders and refund flow to Service Pass
```

## Stop conditions

- Refund payload differs from Batch 01 baseline.
- Duplicate-submit is possible (double-click issues two requests).
- Drawer allows Tab to escape or leaves the body scrollable.

## Suggested commit

`style(web-admin): migrate orders and refund flow to Service Pass`
