# kb-button

## Purpose

Primary button system: primary, secondary, ghost, danger, link. Handles loading and disabled states, icon-only variant, size variants.

## Suggested selector

`kb-button` (or attribute selector `[kbButton]` if the host project prefers attribute components).

## Suggested standalone component

`KbButtonComponent`

## Inputs

- `variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'link'` (default `primary`).
- `size?: 'sm' | 'md' | 'lg' | 'icon'` (default `md`).
- `type?: 'button' | 'submit' | 'reset'` (default `button`).
- `disabled?: boolean`.
- `loading?: boolean` — shows a spinner and disables click, but keeps focus.
- `iconOnly?: boolean` — enforces square dimensions; `ariaLabel` becomes required.
- `ariaLabel?: string` — required when button contains only an icon.

## Outputs

- `click` — standard event; suppressed while `loading` or `disabled`.

## Content projection

Default slot: label / icon.

## Visual variants

| Variant   | Background           | Text                            | Border                     |
| --------- | -------------------- | ------------------------------- | -------------------------- |
| primary   | `--kb-color-primary` | `--kb-color-primary-foreground` | none                       |
| secondary | `--kb-color-surface` | `--kb-color-foreground`         | `--kb-color-border-strong` |
| ghost     | transparent          | `--kb-color-foreground`         | none                       |
| danger    | `--kb-color-danger`  | `#FFFFFF`                       | none                       |
| link      | transparent          | `--kb-color-primary`            | none, underlined on hover  |

## Sizes

- `sm`: height 32, padding 6×12, `--kb-fs-body-sm`.
- `md`: height 40, padding 8×16, `--kb-fs-body`.
- `lg`: height 44, padding 10×20, `--kb-fs-body`.
- `icon`: square 40×40 desktop, `min-h-11 min-w-11` on primary mobile actions.

## CSS classes

`.kb-btn`, `.kb-btn--<variant>`, `.kb-btn--<size>`, `.kb-btn--loading`, `.kb-btn--icon`.

## CSS variables consumed

Colour tokens above plus `--kb-color-primary-hover`, `--kb-color-ring`, `--kb-radius-lg`, `--kb-duration-fast`.

## States

- **Default**: base tokens.
- **Hover**: primary → `--kb-color-primary-hover`; secondary → surface + 4% ink; danger → 8% darker.
- **Focus**: 2px `--kb-color-ring` outset; keep hover styling as well.
- **Active** (pressed): translateY(1px), no shadow change.
- **Disabled**: opacity 0.5, no hover, `cursor: not-allowed`, `aria-disabled="true"`.
- **Loading**: spinner replaces label leading area; button width preserved; `aria-busy="true"`; click ignored.
- **Error**: not represented on the button itself — surface errors via `kb-form-field` or toast.

## Mobile behavior

Primary CTAs must be ≥ 44×44px on primary mobile actions (`--kb-touch-primary`).

## Keyboard behavior

Native `<button>`; Space + Enter activate. Loading state must not steal focus.

## Accessibility

- Icon-only: `ariaLabel` required; icon `aria-hidden="true"`.
- Loading: `aria-busy="true"` and keep the accessible name (spinner is decorative).
- Do NOT swap the accessible name mid-interaction (screen readers announce the change).

## Related prototype

Used across every prototype route.
