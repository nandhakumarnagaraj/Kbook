# kb-confirmation-dialog

## Purpose

Opinionated wrapper around `kb-dialog` for sensitive actions (refund, delete, deactivate, suspend, replace secret, terminal recovery). Enforces the standard confirmation, submission, success, and error pattern.

## Suggested selector

`kb-confirmation-dialog`

## Suggested standalone component

`KbConfirmationDialogComponent`

## Inputs

- `open: boolean`
- `title: string` — action + entity, e.g. "Refund order KB-40213".
- `entityLabel?: string` — shown on a bold line ("Order KB-40213 · ₹4,820").
- `consequence: string` — plain-language explanation of what will change.
- `tone?: 'default' | 'danger'` (default `danger` for destructive actions).
- `confirmLabel: string` — verb-based (e.g. "Refund order", "Deactivate staff", "Suspend business"). Never "OK" / "Yes".
- `cancelLabel?: string` (default "Cancel").
- `state: 'idle' | 'submitting' | 'success' | 'error'` — driven by the host.
- `errorMessage?: string` — displayed inline when `state === 'error'`.
- `successMessage?: string` — displayed inline when `state === 'success'`; primary button becomes "Done".
- `requireTypedConfirmation?: string` — if set, the user must type this exact string before the confirm button enables (used for suspend business, delete critical items).

## Outputs

- `openChange: EventEmitter<boolean>`
- `confirm: EventEmitter<void>` — the host performs the API call and updates `state`.
- `cancel: EventEmitter<void>`

## Content projection

- Default slot (optional): additional details, e.g. an itemised list or reason `<textarea>`.

## Visual variants

- `default`: primary variant `kb-button`.
- `danger`: `kb-button variant="danger"` for the confirm button.

## Sizes

Uses `kb-dialog size="sm"` by default; hosts may pass `md` if the consequence text is long.

## CSS classes

`.kb-confirm`, `.kb-confirm--danger`, `.kb-confirm__entity`, `.kb-confirm__consequence`, `.kb-confirm__error`, `.kb-confirm__success`.

## CSS variables consumed

Inherits from `kb-dialog` + `kb-button`.

## States

- **Idle**: confirm enabled (unless `requireTypedConfirmation` unmet).
- **Submitting**: confirm shows spinner, both buttons disabled, dialog becomes `dismissible=false`, `aria-busy="true"`.
- **Success**: confirm becomes "Done", closes the dialog; success banner shown before close if the host keeps the dialog open.
- **Error**: inline error message with a retry (confirm re-enabled).

## Mobile behavior

Renders as a bottom sheet (see `kb-dialog`).

## Keyboard behavior

- `Escape` closes only when `state === 'idle' | 'error'` and `dismissible` is true.
- Default focus lands on Cancel (safer default for destructive actions).
- `Enter` inside the typed-confirmation input does not submit unless the value matches.

## Accessibility

- `role="alertdialog"`, `aria-modal="true"`, `aria-labelledby`, `aria-describedby` pointing at the consequence text.
- Announce state changes via a polite live region inside the dialog body.

## Related prototype

`/proto/owner/orders` (refund), `/proto/owner/menu` (delete), `/proto/owner/staff` (deactivate), `/proto/owner/terminals` (reject / recover), `/proto/admin/businesses` (suspend).
