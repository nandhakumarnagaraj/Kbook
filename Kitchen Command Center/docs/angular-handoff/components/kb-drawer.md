# kb-drawer

## Purpose

Right-side sliding panel for detail views and multi-field forms. Preserves the underlying list so the user retains context.

## Suggested selector

`kb-drawer`

## Suggested standalone component

`KbDrawerComponent`

## Inputs

- `open: boolean`
- `title: string`
- `description?: string`
- `size?: 'sm' | 'md' | 'lg'` — 420 / 560 / 720px.
- `dismissible?: boolean` (default `true`).

## Outputs

- `openChange: EventEmitter<boolean>`
- `dismiss: EventEmitter<'backdrop' | 'escape' | 'close-button'>`

## Content projection

- Default slot: body (scrollable).
- `[slot="footer"]` — sticky footer for primary/secondary actions.

## Visual variants

Single variant: `--kb-color-surface`, `--kb-shadow-lg`, backdrop scrim.

## Sizes

Width tokens `--kb-drawer-width-sm|md|lg`. Height 100vh.

## CSS classes

`.kb-drawer__backdrop`, `.kb-drawer`, `.kb-drawer__header`, `.kb-drawer__body`, `.kb-drawer__footer`.

## CSS variables consumed

`--kb-color-surface`, `--kb-shadow-lg`, `--kb-z-backdrop`, `--kb-z-modal`, `--kb-duration-slow`, `--kb-ease-decelerate`.

## States

Mirror `kb-dialog` — open / closed / submitting / success / error.

## Mobile behavior

Below 768px, drawer becomes full-width (100vw) and slides in from the right; header remains sticky, footer remains sticky.

## Keyboard behavior

Mirror `kb-dialog` — focus trap, Escape close, focus restoration.

## Accessibility

- Root: `role="dialog"`, `aria-modal="true"`, `aria-labelledby=<title id>`, `aria-describedby=<description id>`.
- Prefer CDK `Overlay` + `FocusTrap`.
- Body scroll locked; drawer body scrolls internally.

## Related prototype

`/proto/owner/orders` (order details), `/proto/owner/menu` (add/edit item), `/proto/admin/businesses` (business detail).
