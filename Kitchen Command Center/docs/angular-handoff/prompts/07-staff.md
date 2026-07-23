# Batch 07 — Staff

## Scope

Restyle `/business/staff` using Batch 02 primitives. Preserve staff create / update / role options / phone + email validation / deactivation behavior / temporary-password handling.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/07-staff.md`
- `docs/angular-handoff/components/kb-drawer.md`, `kb-confirmation-dialog.md`, `kb-copy-button.md`
- Prototype: `/proto/owner/staff`.
- Existing staff component, form, service, DTO, deactivation flow, temp-password display.

## Actions

1. Confirm Batch 06 committed and passing.
2. Directory: Active / Inactive tabs (`role="tablist"`), `KbDataTable` desktop + mobile cards below 768px. Role uses `KbStatusBadge` tone mapping.
3. Add / edit staff via `KbDrawer` around existing form controls.
4. Deactivate via `KbConfirmationDialog` (danger, entity name).
5. Temporary-password reveal dialog: value in `--kb-font-mono`, `KbCopyButton`, explicit "This password will not be shown again" warning. Clear the password from component state on close. Do NOT persist to any storage or global service.

## Preservation rules

- Staff CRUD DTOs + validators: **Unchanged**.
- Role enum + options: **Unchanged**.
- Deactivation endpoint: **Unchanged**.
- Temp-password generation / rotation source of truth: **Unchanged**.
- Role restriction (OWNER): **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, staff specs, `npm run build`.
- Runtime: create, edit, deactivate, verify temp-password appears once and cannot be re-shown. Capture network payloads and confirm identity with Batch 01 baseline.
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1440.
- Accessibility: deactivate dialog default focus on Cancel; copy button announces via live region; tabs proper ARIA; password reveal has `aria-live="polite"`.
- Security check: after closing the temp-password dialog, verify the value is not in Angular DevTools component state, not in `localStorage`, not in `sessionStorage`, and not present in any injected service.

## Required report

```
Batch: 07 Staff
Routes migrated: /business/staff
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (staff CRUD verified)
Routes or guards changed: Unchanged
Validation changed: Unchanged (phone / email regex confirmed)
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: <describe: create / edit / deactivate / temp-password lifecycle>
Responsive tested: <viewports>
Accessibility tested: tabs=PASS|FAIL, dialog focus=PASS|FAIL, copy live region=PASS|FAIL
Secret handling verified: temp password cleared on close = PASS|FAIL
Known issues: <list or none>
Commit: style(web-admin): migrate staff management to Service Pass
```

## Stop conditions

- Phone / email validators regress.
- Temp password persists after dialog close.
- Deactivation payload changes.

## Suggested commit

`style(web-admin): migrate staff management to Service Pass`
