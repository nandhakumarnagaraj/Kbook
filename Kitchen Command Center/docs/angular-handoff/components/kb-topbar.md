# kb-topbar

## Purpose

Sticky top application bar containing brand/section title, mobile hamburger, and right-aligned action slot.

## Suggested selector

`kb-topbar`

## Suggested standalone component

`KbTopbarComponent`

## Inputs

- `title?: string`
- `showHamburger: boolean` (true below 1024px)
- `user?: { name: string; businessName?: string } | null`

## Outputs

- `toggleDrawer: EventEmitter<void>`
- `signOut: EventEmitter<void>` (if user menu is used)

## Content projection

- `[slot="actions"]` — right-aligned actions (search, notifications, avatar menu).

## Sizes

Height `--kb-topbar-height` (56px). Full width.

## CSS classes

- `.kb-topbar`
- `.kb-topbar__title`
- `.kb-topbar__actions`

## CSS variables consumed

`--kb-color-surface`, `--kb-color-border`, `--kb-topbar-height`, `--kb-z-topbar`, `--kb-shadow-xs`.

## States

- **Default**: white surface, bottom hairline border.
- **Scrolled**: unchanged (no elevation shift in this release).
- **Focus / Hover** on hamburger and user avatar button.

## Mobile behavior

Hamburger visible below 1024px; hidden above. Title truncates with ellipsis.

## Keyboard behavior

Standard Tab order left-to-right. Hamburger and user menu behave like buttons.

## Accessibility

- Wrap in `<header>`.
- Hamburger: `aria-label="Open navigation"`, `aria-controls="kb-mobile-drawer"`, `aria-expanded`.
- User avatar (if a menu): use CDK Menu / a11y-compliant dropdown.

## Related prototype

`src/proto/AppShell.tsx`.
