# kb-sidebar

## Purpose

Persistent left navigation for desktop viewports (≥ 1024px). Renders role-appropriate nav entries in deep espresso.

## Suggested selector

`kb-sidebar`

## Suggested standalone component

`KbSidebarComponent`

## Inputs

- `role: 'OWNER' | 'SHOP_ADMIN' | 'KBOOK_ADMIN'`
- `activeRoute: string`
- `entries?: KbNavEntry[]` — optional override; if omitted, the component derives entries from `role`.

Where:

```
interface KbNavEntry {
  label: string;
  route: string;
  icon?: string;   // icon token/name; do NOT bundle React icon libs
  badge?: string;  // small count/status indicator
}
```

## Outputs

- `navigate: EventEmitter<string>`

## Content projection

- `[slot="header"]` (optional): brand block / logo.
- `[slot="footer"]` (optional): user block, sign-out button.

## Visual variants

Single variant (dark espresso). No light-mode variant in this release.

## Sizes

Width = `--kb-sidebar-width` (240px). Optional collapsed rail spec = `--kb-sidebar-collapsed` (64px) — not shipped by default.

## CSS classes

- `.kb-sidebar`
- `.kb-sidebar__brand`
- `.kb-sidebar__nav`
- `.kb-sidebar__entry`
- `.kb-sidebar__entry--active`
- `.kb-sidebar__footer`

## CSS variables consumed

`--kb-color-espresso`, `--kb-color-espresso-foreground`, `--kb-color-saffron`, `--kb-color-ring`, `--kb-space-2`, `--kb-space-3`, `--kb-radius-md`.

## States

- **Default**: transparent background on entries; label + icon at `--kb-color-espresso-foreground` at 80% opacity.
- **Hover**: label reaches 100% opacity; entry background lightens.
- **Focus**: 2px saffron ring inset.
- **Active**: 2px saffron accent (left border or background wash), label 100% opacity, `aria-current="page"`.
- **Disabled**: not shown; role guards omit entries entirely.
- **Loading/Error**: not applicable.

## Mobile behavior

Hidden below 1024px; the same entries render inside `kb-mobile-drawer`.

## Keyboard behavior

- Entries reachable via Tab in document order.
- Enter / Space activates.
- Arrow-key navigation is optional (not required by the prototype).

## Accessibility

- Wrap in `<nav aria-label="Primary">`.
- Each entry is a native anchor (`<a>`) with `routerLink`.
- Active entry: `aria-current="page"`.
- Icons are decorative (`aria-hidden="true"`) — labels carry the accessible name.

## Related prototype

`src/proto/AppShell.tsx` (desktop sidebar rendering).
