# kb-secret-input

## Purpose

Two-mode input for sensitive strings (API keys, webhook secrets, tokens). Enforces the rule that **masked display values are never submitted as real values**.

## Suggested selector

`kb-secret-input`

## Suggested standalone component

`KbSecretInputComponent`

## Inputs

- `id: string`
- `hasStoredValue: boolean` — true when the backend already holds a secret.
- `maskedPreview?: string` — e.g. `"••••••••••••ab12"` (last 4 chars from backend metadata only; NEVER the full secret).
- `placeholder?: string`
- `disabled?: boolean`

## Outputs

- `mode: 'stored' | 'replace'` — emitted when the user toggles.
- `valueChange: EventEmitter<string>` — only emitted in `replace` mode.
- `cancelReplace: EventEmitter<void>` — user reverts to `stored` without saving.

## Modes

1. **Stored (masked)** — input is read-only, shows `maskedPreview`, with a `Replace` action button. This value must NEVER be sent back to the backend as an update.
2. **Replace** — input is empty and editable; `Cancel` reverts to stored mode without notifying the parent form of a change. Submitting the form in this mode sends the newly typed value.

## Content projection

None.

## Visual variants

Single variant. Trailing button label: `Replace` (stored) / `Cancel` (replace).

## Sizes

Matches `kb-form-field` control height (40px).

## CSS classes

`.kb-secret`, `.kb-secret--stored`, `.kb-secret--replace`, `.kb-secret__mask`, `.kb-secret__trigger`.

## CSS variables consumed

`--kb-color-surface`, `--kb-color-border`, `--kb-color-muted-foreground`, `--kb-color-primary`, `--kb-radius-lg`.

## States

- **Stored (default)**: read-only input with masked preview, `Replace` button.
- **Replace**: empty editable input, `Cancel` button.
- **Focus** (replace mode): 2px saffron ring.
- **Error**: red border and message via the containing `kb-form-field`.
- **Disabled**: both modes rendered as inert.

## Mobile behavior

Full width. `Replace` / `Cancel` remains touch-friendly (≥ 40px height).

## Keyboard behavior

- In stored mode, only the `Replace` button is focusable (the masked input is read-only and skipped via `tabindex="-1"`).
- In replace mode, the input is focusable and `Cancel` sits after it.

## Accessibility

- Masked input: `aria-readonly="true"`, `type="text"`; do NOT use `type="password"` for the masked preview (it's not a real secret).
- Replace input: `type="password"` with `autocomplete="new-password"`; `aria-describedby` includes any accompanying hint.
- Toggle button carries `aria-pressed` reflecting mode.

## Critical rule

Host forms MUST detect that a `kb-secret-input` field is in `stored` mode and OMIT it from the update payload. Submitting the masked string as a real secret is a security bug.

## Related prototype

`/proto/owner/marketplace` (Zomato / Swiggy credentials).
