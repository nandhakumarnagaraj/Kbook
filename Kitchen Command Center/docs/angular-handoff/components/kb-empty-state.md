# kb-empty-state

## Purpose

Friendly placeholder for lists/tables/pages with no data. Explains the state and offers a primary action.

## Suggested selector

`kb-empty-state`

## Suggested standalone component

`KbEmptyStateComponent`

## Inputs

- `title: string`
- `description?: string`
- `illustration?: 'orders' | 'menu' | 'staff' | 'terminals' | 'marketplace' | 'businesses' | 'search' | 'generic'` — decorative token; the actual asset lives in the project.
- `dense?: boolean` — use inside table cells.

## Outputs

None.

## Content projection

- Default slot / `[slot="actions"]` — primary CTA (`kb-button`).

## Sizes

- Default: min-height 240px, centered.
- `dense` (inside a table row): 120px.

## CSS classes

`.kb-empty`, `.kb-empty--dense`, `.kb-empty__title`, `.kb-empty__description`, `.kb-empty__actions`.

## CSS variables consumed

`--kb-color-muted-foreground`, `--kb-space-4`, `--kb-space-6`, `--kb-fs-section-title`, `--kb-fs-body`.

## States

Static.

## Mobile behavior

Reduces vertical padding; illustration scales.

## Keyboard behavior

Only the CTA is focusable.

## Accessibility

- Illustration `aria-hidden="true"`.
- Title uses `<h2>` or `<h3>` depending on document outline.

## Related prototype

Empty-state slots in `/proto/owner/orders`, `/proto/owner/staff`, `/proto/admin/businesses`.
