# Angular acceptance checklist

Tick each item per migrated page. A page ships only when all applicable items are green.

## Route preservation

- [ ] All existing production routes remain at the same paths.
- [ ] No routes renamed or duplicated.
- [ ] No new top-level routes introduced by this migration.
- [ ] `authGuard` and `roleGuard` still applied to every previously-guarded route.
- [ ] Role redirects unchanged (OWNER, SHOP_ADMIN, KBOOK_ADMIN, limited-access).

## Role preservation

- [ ] OWNER sees only OWNER routes / actions.
- [ ] SHOP_ADMIN sees only SHOP_ADMIN routes / actions.
- [ ] KBOOK_ADMIN sees only platform routes / actions.
- [ ] Hidden navigation is NOT treated as authorization — guards are authoritative.
- [ ] Deep-linking a restricted route redirects per existing behavior.

## API preservation

- [ ] Same service method invoked as before this migration.
- [ ] Same HTTP method.
- [ ] Same endpoint path and path parameters.
- [ ] Same query parameters and defaults.
- [ ] Same request headers (including Authorization behavior via existing interceptor).
- [ ] Same request payload field names and types.
- [ ] Same response handling / mapping.
- [ ] Same pagination / polling cadence.

## Payload preservation (per sensitive action)

- [ ] `RefundOrderRequest` field names, types, unit (paise vs rupees) unchanged.
- [ ] Menu add/edit/delete payloads unchanged.
- [ ] OCR multipart form field name unchanged.
- [ ] Staff add / deactivate payloads unchanged.
- [ ] Terminal rename / deactivate / activation approve / reject / recovery payloads unchanged.
- [ ] Marketplace credential payloads unchanged.
- [ ] Business activate / suspend payloads unchanged (including reason field).

## Validation preservation

- [ ] All existing validators active (login, staff, menu, terminals, marketplace, businesses).
- [ ] No new validators added silently.
- [ ] Error messages match existing copy where the copy is user-visible.

## States

- [ ] Loading state implemented for every list, table, KPI, drawer, dialog.
- [ ] Empty state implemented for every list / table.
- [ ] Error state implemented with retry for every list / table / panel.
- [ ] Validation state visible with `aria-invalid` and `aria-describedby`.
- [ ] Success state visible via toast or in-place message; success dialogs cannot be missed.

## Responsive behavior

Verify at 320, 375, 430, 768, 1024, 1366, 1440, 1920px on each migrated page:

- [ ] No page-level horizontal overflow.
- [ ] Sidebar collapses into `kb-mobile-drawer` below 1024px.
- [ ] Tables switch to mobile cards below 768px (or as configured).
- [ ] Dialogs become bottom sheets < 640px.
- [ ] Drawers become full-width slide-ins < 768px.
- [ ] Filters wrap; primary CTAs remain reachable.
- [ ] Long restaurant names / order IDs / INR amounts do not break layout.
- [ ] Touch targets ≥ 44px on primary mobile actions.
- [ ] Sticky UI does not obscure content.

## Keyboard navigation

- [ ] Skip-link to main content present and functional.
- [ ] Tab order logical on every page.
- [ ] Every interactive element reachable via keyboard.
- [ ] Enter / Space activate buttons; forms submit via Enter on inputs.
- [ ] Icon-only buttons have `aria-label`.

## Focus handling

- [ ] Visible focus indicator on all interactive elements (2px `--kb-color-ring`).
- [ ] Focus moves into opened dialogs / drawers.
- [ ] Focus trap prevents Tab from escaping open dialogs / drawers.
- [ ] Escape closes dismissible dialogs / drawers.
- [ ] Focus returns to the triggering element on close.
- [ ] Toast does not steal focus.

## Dialog behavior

- [ ] Body scroll locked while a dialog / drawer is open.
- [ ] Background not interactable via mouse or keyboard.
- [ ] `role="dialog"` / `role="alertdialog"` and `aria-modal="true"` present.
- [ ] `aria-labelledby` and `aria-describedby` set.
- [ ] Confirmation dialogs default focus to Cancel.
- [ ] Sensitive actions cannot be double-submitted (button disabled + `aria-busy` + dialog `dismissible=false` while submitting).

## Secret masking

- [ ] `kb-secret-input` in `stored` mode is never included in outgoing payloads.
- [ ] Masked preview never contains more than the last 4 characters.
- [ ] Recovery tokens / temp passwords cleared from component state on dialog close.
- [ ] Recovery tokens / temp passwords never persisted to `localStorage`, `sessionStorage`, or app state.

## Duplicate-submit prevention

- [ ] Login submit disabled while `loading`.
- [ ] Refund confirm disabled + `aria-busy` while submitting.
- [ ] Menu / staff / terminal / marketplace / business form submits disabled while submitting.
- [ ] OCR upload cannot be triggered twice concurrently.

## Prototype-only removal

- [ ] No floating state-switcher pill anywhere.
- [ ] No prototype route selectors.
- [ ] No mock-data toggles.
- [ ] No React or TanStack Router imports in the Angular app.
- [ ] No mock data hard-coded into production Angular services.

## Runtime verification (per batch)

- [ ] `npx tsc --noEmit` clean.
- [ ] Existing unit tests pass.
- [ ] Existing e2e tests pass.
- [ ] Production build succeeds.
- [ ] Browser console clean on every migrated page.
- [ ] Network tab shows the same requests as pre-migration for a smoke journey.

## Sign-off

- [ ] Migration inventory row marked Complete for this page.
- [ ] API equivalence table filled and reviewed.
- [ ] Accessibility spot-check performed with keyboard + screen reader.
- [ ] Responsive spot-check performed at all listed viewports.
