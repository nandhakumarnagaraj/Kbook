# kb-copy-button

## Purpose

Small button that copies a supplied string to the clipboard and shows transient "Copied" feedback.

## Suggested selector

`kb-copy-button`

## Suggested standalone component

`KbCopyButtonComponent`

## Inputs

- `value: string` — the string to copy.
- `label?: string` (default "Copy").
- `copiedLabel?: string` (default "Copied").
- `variant?: 'ghost' | 'secondary'` (default `ghost`).
- `size?: 'sm' | 'md'` (default `sm`).
- `ariaLabel?: string` — required if icon-only.

## Outputs

- `copied: EventEmitter<string>` — the value that was copied.
- `copyError: EventEmitter<unknown>`

## Content projection

Default slot may override label; otherwise `label`/`copiedLabel` are used.

## Visual variants

Wraps `kb-button`. The copied-state briefly shows a checkmark icon for `--kb-duration-slow` × 8 (~2.4s).

## Sizes

`sm`: 28×28 (icon) or 28 tall with 8px horizontal padding.
`md`: 36×36 or 36 tall.

## CSS classes

`.kb-copy`, `.kb-copy--copied`.

## CSS variables consumed

`--kb-color-primary`, `--kb-color-success`, `--kb-duration-slow`.

## States

- **Default**: label + copy icon.
- **Hover / Focus**: standard button behavior.
- **Copied**: label swaps to `copiedLabel`, icon swaps to check, colour tint shifts to `--kb-color-success` for ~2.4s.
- **Error**: label reverts to default; host may display an inline error via toast.

## Mobile behavior

Touch target ≥ 40px when used as a standalone action.

## Keyboard behavior

Enter / Space triggers copy. Screen readers hear the label change via a polite live-region announcement.

## Accessibility

- Icon-only: `ariaLabel` required.
- On copy, announce state via an off-screen live region (`aria-live="polite"`), not by mutating the button's accessible name.

## Related prototype

`/proto/owner/staff` (temp password), `/proto/owner/terminals` (recovery token), `/proto/owner/marketplace` (webhook URL).
