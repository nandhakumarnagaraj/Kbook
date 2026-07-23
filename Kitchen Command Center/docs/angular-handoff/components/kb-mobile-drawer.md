# kb-mobile-drawer

## Purpose

Off-canvas navigation drawer shown below 1024px in place of the fixed sidebar. Slides in from the left and covers content with a backdrop.

## Suggested selector

`kb-mobile-drawer`

## Suggested standalone component

`KbMobileDrawerComponent`

## Inputs

- `open: boolean`
- `role: 'OWNER' | 'SHOP_ADMIN' | 'KBOOK_ADMIN'`
- `activeRoute: string`
- `entries?: KbNavEntry[]` — see `kb-sidebar.md`.

## Outputs

- `openChange: EventEmitter<boolean>`
- `navigate: EventEmitter<string>`

## Content projection

- `[slot="header"]`, `[slot="footer"]` mirroring `kb-sidebar`.

## Visual variants

Single variant (espresso surface, cream text). Width `min(320px, 88vw)`.

## Sizes

Width: 280–320px depending on viewport. Height: full viewport.

## CSS classes

- `.kb-mobile-drawer`
- `.kb-mobile-drawer__backdrop`
- `.kb-mobile-drawer__panel`
- `.kb-mobile-drawer__panel--open`

## CSS variables consumed

`--kb-color-espresso`, `--kb-color-espresso-foreground`, `--kb-z-backdrop`, `--kb-z-modal`, `--kb-shadow-lg`, `--kb-duration-slow`, `--kb-ease-decelerate`.

## States

- **Default (closed)**: panel translated off-screen (`translateX(-100%)`), backdrop opacity 0, `aria-hidden="true"`.
- **Open**: panel `translateX(0)`, backdrop opacity 1, `aria-hidden="false"`, focus trapped inside.
- **Opening / Closing**: animate for `--kb-duration-slow` with `--kb-ease-decelerate`.

## Mobile behavior

- Backdrop tap closes.
- Panel scrolls internally if entries overflow.

## Keyboard behavior

- Focus moves to the first focusable element on open.
- Tab / Shift+Tab loops inside the panel (focus trap).
- `Escape` closes and returns focus to the hamburger trigger.

## Accessibility

- Root: `role="dialog"`, `aria-modal="true"`, `aria-label="Primary navigation"`.
- Body scroll must be locked while open.
- Prefer Angular CDK (`FocusTrap`, `OverlayModule`) to implement focus management.

## Related prototype

`src/proto/AppShell.tsx` — `Drawer` behavior.
