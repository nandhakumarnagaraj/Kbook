# Batch 06 — Menu & OCR

## Scope

Restyle `/business/menu` using Batch 02 primitives. Preserve menu CRUD, category API, availability toggle, OCR multipart field name, OCR job polling, and polling cleanup.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/06-menu.md`
- `docs/angular-handoff/components/kb-file-upload.md`, `kb-confirmation-dialog.md`, `kb-drawer.md`
- Prototype: `/proto/owner/menu`.
- Existing menu component, category service, availability endpoint, OCR upload + polling code.

## Actions

1. Confirm Batch 05 committed and passing.
2. Menu list: category chips + food-type filter + grid/table toggle. Grid tile + table row both feed into the same detail drawer.
3. Add/edit drawer wraps existing form controls; do NOT change validators or DTO field names.
4. Delete uses `KbConfirmationDialog` (danger tone, entity name in `entityLabel`).
5. OCR: `KbFileUpload` for the drop-zone. Reuse the existing multipart field name and polling endpoint. Guarantee polling teardown on: success, failure, timeout, dialog close, component destroy — verify with `takeUntil(this.destroy$)` or equivalent.

## Preservation rules

- Menu list / create / update / delete DTOs: **Unchanged**.
- Category API: **Unchanged**.
- Availability toggle endpoint + optimistic-update behavior: **Unchanged**.
- OCR multipart form field name and polling URL / cadence: **Unchanged**.
- Role restriction: **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, menu-related unit specs, `npm run build`.
- Runtime: create + edit + delete + toggle availability + OCR happy path + OCR partial-success + OCR error + cancel-mid-upload. Verify polling stops when dialog closes and when component is destroyed (leave the page mid-upload and check network tab).
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1440.
- Accessibility: availability toggle uses real `role="switch"`; grid/table toggle uses `role="tablist"`; upload progress bar has `aria-valuenow`; delete dialog default focus on Cancel.

## Required report

```
Batch: 06 Menu & OCR
Routes migrated: /business/menu
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (menu CRUD + OCR multipart field name verified)
Routes or guards changed: Unchanged
Validation changed: Unchanged
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: <describe scenarios exercised>
Responsive tested: <viewports>
Accessibility tested: switch role=PASS|FAIL, upload live region=PASS|FAIL, delete dialog focus=PASS|FAIL
Polling teardown verified: PASS|FAIL — describe evidence
Known issues: <list or none>
Commit: style(web-admin): migrate menu and OCR interfaces to Service Pass
```

## Stop conditions

- OCR field name renamed.
- Polling continues after dialog close, navigation, or component destroy.
- Availability toggle no longer optimistically updates or fails to roll back on error.

## Suggested commit

`style(web-admin): migrate menu and OCR interfaces to Service Pass`
