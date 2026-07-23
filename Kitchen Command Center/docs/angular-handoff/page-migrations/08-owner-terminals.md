# OWNER terminals

## Prototype route

`/proto/owner/terminals`

## Existing production route

`/business/terminals` (OWNER view)

## Layout anatomy

- `kb-page-header title="Terminals"` with primary "Request activation" action.
- Fleet overview: 3 default `kb-kpi-card`s (Active, Pending, Suspended).
- `kb-data-table` of terminals (desktop) / mobile card list.
- `kb-drawer` for terminal details (rename, deactivate).
- `kb-confirmation-dialog`s for deactivate, reject activation, recovery.

## Shared components required

All shell primitives, `kb-kpi-card` (default), `kb-data-table`, `kb-mobile-data-card`, `kb-status-badge`, `kb-drawer`, `kb-confirmation-dialog`, `kb-form-field`, `kb-button`, `kb-copy-button`, `kb-empty-state`, `kb-error-state`, `kb-skeleton`, `kb-toast`.

## Unique page components

- `KbTerminalDetailPanelComponent` — projected into the drawer; shows terminal ID (mono), status, assigned staff, actions.
- `KbTerminalActivationRequestListComponent` — section listing pending activation requests with Approve / Reject actions (OWNER only when the existing app grants OWNER this permission — otherwise omit; do not invent permissions).
- `KbRecoveryTokenDialogComponent` — projects into `kb-dialog`; shows a one-time recovery token with expiry countdown and copy action.

## Desktop layout

- Fleet KPI row at top.
- Table columns: Terminal ID, Label, Status, Last seen, Assigned staff, ⋯.

## Tablet layout

- KPIs 3 columns; table with reduced columns.

## Mobile layout

- KPI stack; table becomes mobile card list.

## Loading / empty / error

- Loading: KPI + table skeleton.
- Empty: `kb-empty-state title="No terminals yet"` + CTA "Request activation".
- Five-terminal-limit reached: replace CTA with a disabled button and an info banner explaining the limit; contact support link.
- Error: retry.

## Validation state

- Rename: label required, min 2 / max 32 chars — mirror existing rules.
- Reject reason (activation request): required, min 8 chars.

## Success state

- Rename: drawer closes; toast.
- Deactivate: row updates status; toast.
- Approve request: request removed; new terminal appears with status `pending` or `active` per existing backend behavior.
- Reject request: request removed; toast.
- Recovery: dialog shows token + expiry; token cleared on dialog close.

## Sensitive actions

Use `kb-confirmation-dialog` for each:

- **Deactivate terminal**: consequence "This terminal will stop accepting payments immediately.", `confirmLabel="Deactivate terminal"`, tone `danger`.
- **Reject activation request**: requires a reason in the projected slot; `confirmLabel="Reject request"`, tone `danger`.
- **Recover terminal**: consequence "A recovery token will be issued. It expires in 10 minutes and is shown once.", `confirmLabel="Issue recovery token"`, tone `danger`.

Prevent double-submit for all three.

## Recovery-token rules

- Token displayed in `--kb-font-mono`.
- `kb-copy-button` copies once; component clears the token from state on dialog close.
- Never persist to storage; never log.
- Countdown timer uses `Date.now()`; on expiry, dialog swaps to an expired state with an option to re-issue.

## Accessibility requirements

- Terminal IDs mono font, wrapped so they don't break layout.
- Dialogs use `role="alertdialog"`; default focus on Cancel.
- Copy button announces via live region.
- Countdown announced periodically via polite live region ("9 minutes remaining", "1 minute remaining") — do not spam every second.

## Existing functionality that must be retained

- Terminal list, detail, rename, deactivate, reactivate, activation-request approve / reject, recovery-token issue endpoints — all unchanged.
- Existing role restrictions between OWNER and SHOP_ADMIN — the UI only presents actions the current role is allowed to perform.
- Existing five-terminal-limit rule — the backend enforces; the UI reflects the state.

## Visual acceptance criteria

- KPI cards flat default (no hero / gradient).
- Status badge tones: active=success, pending=warning, suspended=danger, offline=neutral.
- Table density `compact` acceptable for operational scanning.
- No serif/gradient anywhere on this page.
