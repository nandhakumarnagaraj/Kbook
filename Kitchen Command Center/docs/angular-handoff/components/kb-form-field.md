# kb-form-field

## Purpose

Consistent form-field wrapper: label, description, control slot, error, hint. Handles validation state visuals.

## Suggested selector

`kb-form-field`

## Suggested standalone component

`KbFormFieldComponent`

## Inputs

- `label: string`
- `for?: string` — the control's `id` (used for the `<label>` association).
- `description?: string`
- `hint?: string`
- `error?: string` — presence toggles error styling.
- `required?: boolean`
- `disabled?: boolean`

## Outputs

None.

## Content projection

- Default slot: the control (`<input>`, `<select>`, `<textarea>`, `kb-secret-input`, etc.).
- `[slot="trailing"]` (optional): trailing action (e.g. "Show" toggle).

## Visual variants

Single variant. Error state overrides border + adds inline message.

## Sizes

Field vertical rhythm: label (12px, 500) → 4px → control (40px) → 4px → hint/error (12px, 400).

## CSS classes

`.kb-field`, `.kb-field--error`, `.kb-field--disabled`, `.kb-field__label`, `.kb-field__hint`, `.kb-field__error`, `.kb-field__required`.

## CSS variables consumed

`--kb-color-foreground`, `--kb-color-muted-foreground`, `--kb-color-danger`, `--kb-color-input`, `--kb-color-ring`, `--kb-radius-lg`, `--kb-fs-body-sm`, `--kb-fs-caption`.

## States

- **Default**: neutral border.
- **Focus** (inside control): 2px saffron ring outset.
- **Error**: border `--kb-color-danger`, error text below.
- **Disabled**: opacity 0.55, cursor `not-allowed`.

## Mobile behavior

Full width. Description and error wrap.

## Keyboard behavior

Focus applies to the projected control; the field wrapper itself is not focusable.

## Accessibility

- `<label for="{{for}}">` linked to the control.
- Required indicator (`*`) is decorative; the control also carries `required` / `aria-required="true"`.
- Error text has an `id`; the control uses `aria-describedby="<hint-id> <error-id>"` and `aria-invalid="true"` on error.

## Related prototype

`/proto/login`, `/proto/owner/staff` (add), `/proto/owner/menu` (add/edit), `/proto/owner/marketplace`.
