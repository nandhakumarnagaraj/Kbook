# Batch 09 — Marketplace

## Scope

Restyle `/business/marketplace` using `KbSecretInput`. Preserve marketplace configuration API, Zomato + Swiggy fields, enabled flags, masked-value semantics, and webhook display.

## Inputs to inspect

- `docs/angular-handoff/page-migrations/10-marketplace.md`
- `docs/angular-handoff/components/kb-secret-input.md`, `kb-copy-button.md`, `kb-form-field.md`, `kb-confirmation-dialog.md`
- Prototype: `/proto/owner/marketplace`.
- Existing marketplace component, credential service, DTOs, webhook URL source.

## Actions

1. Confirm Batch 08 committed and passing.
2. Two provider cards (Zomato, Swiggy), side by side ≥ 1024px.
3. Each credential field uses `KbSecretInput` with two modes: `stored` (masked, read-only, `Replace` toggle) and `replace` (empty editable, `Cancel` reverts).
4. Save handler MUST detect `stored`-mode fields and OMIT them from the update payload. Verify with a captured network request: a save that toggles only the "enabled" flag must not include any secret in the body.
5. Webhook URL shown with `KbCopyButton`; announces via live region.
6. Wrap the Save action in a `KbConfirmationDialog` if the existing backend already required a confirmation; otherwise a direct save with `aria-busy` while submitting.

## Preservation rules

- Marketplace configuration endpoints: **Unchanged**.
- Zomato + Swiggy DTO field names + types + enabled flags: **Unchanged**.
- Webhook URL source of truth: **Unchanged**.
- Role restriction (OWNER): **Unchanged**.

## Tests to run

- `npx tsc --noEmit`, marketplace specs, `npm run build`.
- Runtime: connect, save without touching secrets (payload must OMIT them), replace a secret and save (payload must include the newly typed value), toggle enabled off/on. Redact real credentials in evidence.
- Viewports: 320 / 375 / 430 / 768 / 1024 / 1440.
- Accessibility: `KbSecretInput` toggle uses `aria-pressed`; masked input has `aria-readonly="true"`; webhook copy announces.
- Security: masked preview never contains more than the last 4 characters; masked string is never sent as an update.

## Required report

```
Batch: 09 Marketplace
Routes migrated: /business/marketplace
Files changed: <list>
Shared components added: <list or none>
Shared components updated: <list or none>
API services changed: Unchanged
Payload builders changed: Unchanged (provider DTOs verified)
Routes or guards changed: Unchanged
Validation changed: Unchanged
Type-check: PASS|FAIL
Unit tests: PASS|FAIL|NOT AVAILABLE
Build: PASS|FAIL
Runtime tested: <describe: save-without-secrets, replace-secret, toggle-enabled>
Masked secret never submitted (evidence): PASS|FAIL
Responsive tested: <viewports>
Accessibility tested: secret toggle=PASS|FAIL, webhook copy live region=PASS|FAIL
Known issues: <list or none>
Commit: style(web-admin): migrate marketplace configuration to Service Pass
```

## Stop conditions

- Any captured save request contains a masked string as a credential value.
- Provider DTO field names change.
- Webhook URL is editable in the UI when the backend treats it as read-only.

## Suggested commit

`style(web-admin): migrate marketplace configuration to Service Pass`
