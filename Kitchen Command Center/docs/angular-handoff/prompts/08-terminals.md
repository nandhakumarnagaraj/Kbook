# Batch 08 — Terminals (OWNER + SHOP_ADMIN)

## Scope

Restyle `/business/terminals` for both OWNER and SHOP_ADMIN. Preserve rename / deactivate / reactivate / activation-request approve+reject / recovery / five-terminal-limit behavior. Preserve device ID and recovery token handling exactly.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/08-owner-terminals.md`, `09-shop-terminals.md`
- `docs/angular-handoff/components/kb-drawer.md`, `kb-confirmation-dialog.md`, `kb-copy-button.md`, `kb-kpi-card.md`
- Prototypes: `/proto/owner/terminals`, `/proto/shop/terminals`.
- Existing terminals component, activation-request flow, recovery-token flow.

## Actions

1. Confirm Batch 07 committed and passing.
2. Fleet overview: 3 default KPI cards (no hero, no gradient).
3. Terminals table + activation-request list. Terminal IDs mono; wrap-safe.
4. Rename + deactivate via drawer + confirmation dialog. Reactivate uses a confirmation dialog if the existing app confirms it today; otherwise a plain button.
5. Reject request requires a reason (textarea, min-8 chars).
6. Recovery: confirmation dialog issues token → new dialog shows token once with `KbCopyButton` and countdown to expiry. Clear token from state on close; never persist.
7. Role-aware rendering: OWNER sees everything the existing app grants OWNER; SHOP_ADMIN sees the subset the existing app grants SHOP_ADMIN — do not invent capabilities. Hidden navigation is NOT authorization; the existing `roleGuard` remains authoritative.

## Preservation rules

- All terminals endpoints + DTOs: **Unchanged**.
- Recovery token TTL and issuance semantics: **Unchanged**.
- Five-terminal limit is backend-enforced; UI reflects the state only.
- OWNER + SHOP_ADMIN role restrictions: **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, terminals specs, `npm run build`.
- Runtime with OWNER account and SHOP_ADMIN account separately: rename, deactivate, reactivate, approve request, reject request, issue recovery token, verify token clears on close. Attempt an OWNER-only action while signed in as SHOP_ADMIN — must be denied by the existing guard, not just hidden.
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1440.
- Accessibility: dialogs `role="alertdialog"` default focus on Cancel; countdown announced periodically via polite live region; copy live region announces.
- Security: recovery token not persisted anywhere after close.

## Required report

```
Batch: 08 Terminals (OWNER + SHOP_ADMIN)
Routes migrated: /business/terminals
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (rename / deactivate / recovery verified)
Routes or guards changed: Unchanged
Validation changed: Unchanged (reject reason min length)
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: <describe OWNER + SHOP_ADMIN scenarios>
Role restriction verified via guard (not just UI): PASS|FAIL
Responsive tested: <viewports>
Accessibility tested: dialogs=PASS|FAIL, countdown live region=PASS|FAIL, copy live region=PASS|FAIL
Recovery token cleared on close: PASS|FAIL
Known issues: <list or none>
Commit: style(web-admin): migrate terminal management to Service Pass
```

## Stop conditions

- SHOP_ADMIN gains an OWNER-only action (or vice versa).
- Recovery token persists beyond dialog close.
- Five-terminal-limit bypassed in the UI.

## Suggested commit

`style(web-admin): migrate terminal management to Service Pass`
