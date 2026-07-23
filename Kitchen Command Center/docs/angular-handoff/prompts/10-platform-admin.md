# Batch 10 — Platform administration (KBOOK_ADMIN)

## Scope

Restyle `/admin/dashboard` and `/admin/businesses`. Preserve KBOOK_ADMIN restriction, platform summary API, business list / detail, activate + suspend flows.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/11-admin-dashboard.md`, `12-admin-businesses.md`
- `docs/angular-handoff/components/kb-data-table.md`, `kb-drawer.md`, `kb-confirmation-dialog.md`
- Prototypes: `/proto/admin/dashboard`, `/proto/admin/businesses`.
- Existing admin dashboard + businesses components, services, DTOs.

## Actions

1. Confirm Batch 09 committed and passing.
2. Dashboard: neutral executive layout — default KPI cards, no hero / gradient / serif.
3. Businesses: filter toolbar + `KbDataTable` desktop + mobile cards. Row → drawer detail (profile, owner contact, plan, terminal count, recent activity, danger zone).
4. Activate via `KbConfirmationDialog` (default tone, entity name).
5. Suspend via `KbConfirmationDialog` (danger tone, requires reason textarea min-20 chars, `requireTypedConfirmation="{name}"` to reduce accidental suspensions).
6. Role gate: `roleGuard(KBOOK_ADMIN)` remains authoritative. If a lower-role user reaches the URL directly they must be redirected per existing behavior.

## Preservation rules

- Platform summary + businesses list / detail / activate / suspend endpoints + DTOs: **Unchanged**.
- Suspend reason visibility policy (visible to the restaurant): **Unchanged**.
- KBOOK_ADMIN role restriction: **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, admin specs, `npm run build`.
- Runtime with a KBOOK_ADMIN account: dashboard load, filter/search businesses, open drawer, activate a test business, suspend a test business (typed confirmation required). Do NOT test suspend against a real production tenant.
- Attempt direct URL access as OWNER — must redirect per existing guard.
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1440 / 1920.
- Accessibility: suspend dialog default focus on Cancel; typed-confirmation is a real `KbFormField`; reason textarea `aria-describedby` linked to error.

## Required report

```
Batch: 10 Platform administration
Routes migrated: /admin/dashboard, /admin/businesses
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (activate + suspend DTOs verified)
Routes or guards changed: Unchanged
Validation changed: Unchanged (suspend reason min length)
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: <describe: dashboard, activate, suspend>
Role restriction verified via guard: PASS|FAIL
Responsive tested: <viewports>
Accessibility tested: dialog focus=PASS|FAIL, typed confirmation=PASS|FAIL, reason association=PASS|FAIL
Known issues: <list or none>
Commit: style(web-admin): migrate platform administration to Service Pass
```

## Stop conditions

- Any decorative gradient / serif on platform screens.
- Suspend without typed confirmation succeeds.
- OWNER can reach `/admin/*` after direct URL entry.

## Suggested commit

`style(web-admin): migrate platform administration to Service Pass`
