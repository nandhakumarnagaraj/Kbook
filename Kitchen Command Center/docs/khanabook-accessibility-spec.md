# KhanaBook Accessibility Specification

The prototype implements the accessibility **contract** below. This is the
required behaviour for the Angular build. It is **not** a compliance
attestation — the Angular team owns keyboard, screen-reader, and
colour-contrast audits against the shipped implementation.

## 1. Landmarks and semantics

- Each authenticated page renders exactly one `<main>` (inside
  `KbAppShellComponent`).
- Sidebar is a `<nav>` with an `aria-label` naming the role
  (`"Restaurant navigation"`, `"Device navigation"`, `"Platform navigation"`).
- Topbar breadcrumbs live in a `<nav aria-label="Breadcrumb">`.
- Every interactive element is a semantic `<button>` or `<a>` — never a
  `<div onClick>`.
- Every icon-only button carries an `aria-label` (`Close`,
  `Open navigation`, `Show password`, `Copy`, `Notifications`).

## 2. Forms

- Every input has a visible `<label>` bound with `for` / `htmlFor` (or
  `aria-label` when the label would be redundant with a visible legend).
- Error messages are rendered inside the field container and linked to the
  input via `aria-describedby`; the input carries `aria-invalid="true"`
  when in error.
- Placeholder text is never used as the only label.
- Required fields carry `aria-required="true"`; visual asterisks pair with
  a "(required)" text hint on the label.
- Grouped inputs (radio food-type selector, OTP fields) live inside a
  `<fieldset>` with a `<legend>`.

## 3. Keyboard

- Tab order follows DOM order; nothing uses `tabindex > 0`.
- `Enter` / `Space` activate buttons and toggle switches.
- `Escape` closes the topmost dialog or drawer and restores focus to the
  element that opened it — prototype implements this via a `useModalA11y`
  hook; Angular uses `CdkDialog` + `CdkOverlay` which do it by default.
- Arrow keys navigate menus, tabs, and radio groups (delegated to CDK
  primitives).
- `⌘ / Ctrl + K` opens the global search from the topbar.

## 4. Focus

- Every interactive element shows a **4-px saffron ring at 44 % opacity**
  (`focus-visible:ring-4 focus-visible:ring-ring`).
- Focus is trapped inside open dialogs and drawers, then restored to the
  invoker on close.
- Autofocus is used only inside a freshly-opened dialog/drawer, and only
  when the first field is the natural starting point.
- Skip-to-content link may be added to `KbAppShellComponent` — recommended
  but not implemented in the prototype.

## 5. Colour and non-colour cues

- Status is **always** communicated by icon + text, never colour alone.
  See `OrderStatusBadge`, `DeviceStatusBadge`, `MarketplaceStatusBadge`.
- Food-type indicator (veg / egg / non-veg) pairs colour with a squared
  outline glyph — accessible even in monochrome.
- Refunded table rows use a red left-edge accent **and** a
  `Refunded` status badge with an icon.
- Setup-progress checklist uses a filled check glyph + strikethrough,
  not colour alone.

## 6. Colour contrast targets

- Body text (`--foreground` on `--background`): passes WCAG AA (≥ 4.5:1).
- Secondary text (`--muted-foreground` on `--background`): passes WCAG AA
  large-text (≥ 3:1); avoid using it for critical actions.
- Primary CTA (`--primary-foreground` on `--primary`): passes AA.
- Do not layer `text-muted-foreground/50` or arbitrary greys — always use
  the token.
- Angular team runs axe/Lighthouse against the shipped implementation and
  confirms AA for text and AA-Large for supporting text.

## 7. Live regions and status updates

- Toast surface is a single `role="region" aria-live="polite"`.
- Copy-to-clipboard success (secret input, recovery token, temp password)
  toggles a hidden `role="status"` live region with the message
  "Copied to clipboard".
- Sensitive-action dialogs use `role="alertdialog"` when in the
  destructive-confirm state; standard drawers/dialogs use `role="dialog"`
  with `aria-modal="true"`.

## 8. Reduced motion

- All non-essential animations are wrapped in a
  `@media (prefers-reduced-motion: reduce)` guard that removes transforms
  and caps opacity transitions at ≤ 100 ms.
- Skeleton `animate-pulse` still runs (opacity-only) — reduces perceived
  latency without triggering motion sensitivity.

## 9. Touch targets

- All interactive elements are ≥ 40 × 40 px.
- Primary CTAs on mobile are ≥ 44 × 44 px (`min-h-11`).
- Inputs on mobile are `h-11` (44 px).
- Icon buttons in dense tables are 32 × 32 px because they sit inside a
  clickable table row that is itself ≥ 40 px tall; on mobile-card
  rendering these become 44-px full-width action rows.

## 10. Modal / drawer ARIA contract

| Element          | Attributes                                                                                                                          |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Backdrop scrim   | `aria-hidden="true"`, click-to-dismiss (unless destructive-confirm in submitting state)                                             |
| Drawer container | `role="dialog"`, `aria-modal="true"`, `aria-labelledby` pointing to the drawer title                                                |
| Dialog container | `role="dialog"` (or `alertdialog` when destructive-confirm), `aria-modal="true"`, `aria-labelledby`                                 |
| Close button     | `aria-label="Close"`; `Escape` also closes                                                                                          |
| Confirm button   | Disabled + `aria-busy="true"` during submit                                                                                         |
| Success view     | Replaces body inside the same container; focus moves to the `Done`/primary button; `role="status"` live region announces the result |

## 11. What is not implemented in the prototype

The prototype demonstrates each behaviour but does not include:

- Automated axe / pa11y runs
- Screen-reader recordings on JAWS / NVDA / VoiceOver
- Full keyboard-only walkthrough transcripts
- High-contrast Windows theme audit
- RTL layout support

The Angular team must perform these audits against the shipped
implementation before claiming compliance. Any regression against the
contract above should block release.
