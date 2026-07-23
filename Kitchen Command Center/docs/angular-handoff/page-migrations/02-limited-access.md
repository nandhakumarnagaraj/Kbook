# Limited access

## Prototype route

No dedicated prototype route — reuse the login layout with a limited-access variant. See `01-login.md`.

## Existing production route

`/limited-access`

## Layout anatomy

- Reuses `KbAuthLayoutComponent` (left brand, right message panel).
- Message panel shows: page title, plain-language explanation, current signed-in user meta (email), sign-out button, and a support contact hint.

## Shared components required

- `kb-page-header` (title + description) — inside the right panel.
- `kb-button` (secondary "Sign out", link "Contact support").

## Unique page components

None. The screen is entirely presentational.

## Desktop layout

Split layout identical to login; right panel content centered.

## Tablet layout

Same as login — brand strip on top.

## Mobile layout

Single column; message content padded 16px; sign-out button full-width.

## Loading / empty / error state

- No loading state (page renders synchronously from route data).
- No empty state.
- If the sign-out call fails, surface via `kb-toast` (tone `danger`) and keep the user on the page.

## Validation state

Not applicable.

## Success state

Sign-out navigates to `/login` using the existing auth service.

## Sensitive actions

Sign-out is not destructive but must be single-click; disable the button during the request to prevent double-submit.

## Accessibility requirements

- `<h1>` = "Access limited" (or existing copy).
- Description associated via `aria-describedby` on the sign-out button.
- Support contact link opens with `rel="noopener noreferrer"` when it targets an external URL.

## Existing functionality that must be retained

- Existing role-based routing decision that lands users here.
- Existing sign-out service call.
- Existing copy / support contact — do not invent new copy.

## Visual acceptance criteria

- Same espresso brand panel as login.
- No gradient, no serif on this screen.
- Message copy uses `--kb-fs-body` with `--kb-lh-normal`.
