# kb-app-shell

## Purpose

Top-level presentational shell that composes the sidebar (desktop) / mobile drawer, the topbar, and the main content region. Wraps every authenticated route.

## Suggested selector

`kb-app-shell`

## Suggested standalone component

`KbAppShellComponent`

## Inputs

- `role: 'OWNER' | 'SHOP_ADMIN' | 'KBOOK_ADMIN'` — drives which navigation set the sidebar and mobile drawer render.
- `user: { name: string; email?: string; businessName?: string } | null` — displayed in topbar / drawer header. Presentation only.
- `activeRoute: string` — current route path for highlighting the active nav entry (fallback for when the shell can't read the router).

## Outputs

- `signOut: EventEmitter<void>` — the host page handles the actual auth service call.
- `navigate: EventEmitter<string>` — emitted when a sidebar / drawer entry is clicked (host may call Angular Router).

## Content projection

- Default slot: the routed page content (`<router-outlet />` typically lives here in the host).
- `[slot="topbar-actions"]` (optional): right-aligned topbar controls (search, notifications).

## Visual variants

Single variant. Warm off-white app background, deep espresso sidebar, saffron highlights on the active nav entry.

## Sizes

- Desktop: sidebar `--kb-sidebar-width` (240px), topbar `--kb-topbar-height` (56px).
- Mobile (< 1024px): sidebar hidden; hamburger button in topbar opens `kb-mobile-drawer`.

## CSS classes

- `.kb-app-shell` (root grid)
- `.kb-app-shell__sidebar`
- `.kb-app-shell__topbar`
- `.kb-app-shell__main`

## CSS variables consumed

`--kb-color-bg-app`, `--kb-color-espresso`, `--kb-color-espresso-foreground`, `--kb-sidebar-width`, `--kb-topbar-height`, `--kb-content-max-width`, `--kb-page-padding-x-desktop`, `--kb-page-padding-x-mobile`.

## States

- **Default**: sidebar visible ≥ 1024px, main scrolls independently.
- **Hover**: nav entries brighten background (espresso + 4% white).
- **Focus**: nav entries show 2px saffron ring inside espresso.
- **Disabled** (nav entry): rendered as inert with reduced opacity when the current role cannot access the entry — normally these entries are simply omitted.
- **Loading**: the host page controls loading; the shell itself is always rendered synchronously.
- **Error**: shell does not render error states; host page uses `kb-error-state`.

## Mobile behavior

- Sidebar collapses into `kb-mobile-drawer` triggered by a hamburger button in the topbar.
- Topbar remains sticky (`--kb-z-topbar`).
- Main content padding switches to `--kb-page-padding-x-mobile`.

## Keyboard behavior

- Tab order: skip-link → hamburger (mobile) → topbar actions → sidebar nav → main content.
- Provide a `Skip to main content` link that jumps to `main`.
- Sidebar entries navigable by Tab; Enter/Space activates.

## Accessibility

- Root uses `<div>`; sidebar uses `<nav aria-label="Primary">`; topbar uses `<header>`; main region uses `<main id="main">`.
- Active nav entry has `aria-current="page"`.
- Hamburger button has `aria-label="Open navigation"` and toggles `aria-expanded`.

## Related prototype

`/proto/owner/dashboard`, `/proto/admin/dashboard`, `/proto/shop/terminals` — all use `AppShell.tsx` in `src/proto/`.
