# Batch 03 — Authentication

## Scope

Restyle `/login` and `/limited-access` using the shared primitives from Batch 02. Preserve the existing auth service, Google sign-in, forgot-password / OTP / reset flows, and role redirection.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/01-login.md`
- `docs/angular-handoff/page-migrations/02-limited-access.md`
- Existing auth service, existing forgot-password / OTP / reset components, existing role redirect logic.

## Actions

1. Confirm Batch 02 committed and passing.
2. Wrap the login and limited-access screens in the Service Pass auth layout (split brand + form on ≥ 1024px, stacked below).
3. Convert existing form fields to use the shared `KbFormField` (or equivalent). Keep the existing FormControl / validators.
4. Loading + disable-during-submit on the login submit and Google sign-in buttons — do not touch the actual auth service call.
5. Auth failures shown inline via `role="alert"` banner + `aria-invalid` + `aria-describedby` on the failing field.

## Preservation rules

- Login endpoint / method / payload: **Unchanged**.
- Google sign-in flow: **Unchanged** (only the button's presentation changes).
- Forgot-password / OTP / reset endpoints: **Unchanged**.
- Token storage & interceptor: **Unchanged**.
- Role redirect (OWNER / SHOP_ADMIN / KBOOK_ADMIN / limited-access): **Unchanged**.

## Tests to run

- `npx tsc --noEmit`.
- Auth-related unit specs (whatever names Batch 01 recorded).
- `npm run build`.
- Runtime smoke against a safe (non-production) tenant: successful login for each role, failed login, forgot-password / OTP / reset happy path. Redact JWTs and phone numbers in evidence.
- Keyboard: Tab from email → password → visibility toggle → submit → Google button. Escape does nothing here (no modal).
- Viewports: 320 / 375 / 768 / 1024 / 1440.

## Required report

```
Batch: 03 Authentication
Routes migrated: /login, /limited-access
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (verify login body field names + types match pre-migration exactly)
Routes or guards changed: Unchanged
Validation changed: Unchanged
Type-check: PASS|FAIL|BLOCKED
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: PASS|PARTIALLY TESTED (list what was executed)|NOT TESTED
Responsive tested: <list viewports actually checked in-browser>
Accessibility tested: keyboard=PASS|FAIL, focus=PASS|FAIL, aria-live on failure banner=PASS|FAIL
Known issues: <list or none>
Commit: style(web-admin): migrate authentication screens to Service Pass
```

## Stop conditions

- Login payload field names or types differ from Batch 01 baseline.
- Role redirect targets differ.
- Password visibility toggle triggers a form submit (regression).

## Suggested commit

`style(web-admin): migrate authentication screens to Service Pass`
