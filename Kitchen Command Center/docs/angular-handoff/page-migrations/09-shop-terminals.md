# SHOP_ADMIN terminals

## Prototype route

`/proto/shop/terminals`

## Existing production route

Same route as OWNER terminals (`/business/terminals`) with role-aware presentation, OR whichever dedicated route the existing Angular app already uses for SHOP_ADMIN. **Do not add or rename routes.**

## Layout anatomy

Same shell as `08-owner-terminals.md`, with these differences:

- Actions available depend on SHOP_ADMIN's existing permissions in the Angular app. Present only the actions the role can actually perform.
- The screen emphasises operational scanning: fleet KPIs → terminal list → activation requests.

## Shared components required

Same set as OWNER terminals.

## Unique page components

- `KbTerminalDetailPanelComponent`
- `KbTerminalActivationRequestListComponent`
- `KbRecoveryTokenDialogComponent`

## Desktop / Tablet / Mobile layouts

Identical to OWNER terminals.

## Loading / empty / error

Identical to OWNER terminals.

## Validation state

Identical to OWNER terminals (rename label, reject reason).

## Success state

Identical to OWNER terminals.

## Sensitive actions

Same set (deactivate, reject request, recover) — with the same confirmation, submission, success, and error contract.

## Accessibility requirements

Identical to OWNER terminals.

## Role restriction — important

- Hidden navigation is not authorization. The existing `roleGuard` remains authoritative.
- If an action is not permitted for SHOP_ADMIN, it must be omitted from the UI — not disabled with a tooltip and then routed through anyway.
- If SHOP_ADMIN has a stricter subset of permissions than OWNER, follow the existing service definitions exactly. Do not invent capabilities.

## Existing functionality that must be retained

- Every SHOP_ADMIN terminal endpoint, payload, and existing behavior.
- Existing role guard behavior.
- Existing five-terminal-limit rule and existing recovery token TTL.

## Visual acceptance criteria

- Operational visual — compact table density preferred.
- Status badge tones consistent with OWNER terminals.
- No serif/gradient.
