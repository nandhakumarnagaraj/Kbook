# Login and password recovery

## Prototype route

`/proto/login`, `/proto/forgot`

## Existing production route

`/login` (plus any existing forgot-password / OTP / reset routes already in the Angular app — do not rename)

## Layout anatomy

- Full-viewport split on ≥ 1024px: left brand panel (espresso, warm illustration / logo), right form panel (surface).
- Below 1024px: single column, brand panel collapses to a compact header strip.

## Shared components required

- `kb-form-field` (email, password, OTP inputs)
- `kb-button` (primary, ghost)
- `kb-error-state` (for auth failures displayed inline above the form)
- `kb-toast` (success confirmations after password reset)

## Unique page components

- `KbAuthLayoutComponent` (left brand + right form panels)
- `KbPasswordVisibilityToggleComponent` (icon-only button; inputs `aria-label="Show password" / "Hide password"`).

## Desktop layout

- Left panel: 40% width, espresso surface, brand mark + short value-prop line. No form controls.
- Right panel: 60% width, surface white; form max-width 400px, centered vertically.

## Tablet layout (≥ 768px, < 1024px)

- Panels stack: brand panel becomes 25vh header, form below.

## Mobile layout (< 768px)

- Single column; brand strip 96px tall; form fills remaining viewport with 16px padding.

## Loading state

- Submit button uses `kb-button loading=true` (spinner + `aria-busy`). Inputs remain enabled but the form does not submit twice.
- Google sign-in button uses its own loading indicator while the OAuth handshake runs.

## Empty state

Not applicable (form).

## Error state

- Auth failures: inline error banner above the form; individual field errors surface via `kb-form-field.error`.
- Preserve existing error copy from the Angular auth service — do not invent new codes.

## Validation state

- Email: HTML `type="email"` + Angular validators (unchanged).
- Password: minimum length rule preserved from existing validators; error shown after blur or submit.
- OTP: numeric-only, 6 digits; auto-advance is optional and must not break paste.

## Success state

- Successful login: navigate using the existing role-based redirect (OWNER → `/business/dashboard`, SHOP_ADMIN → its landing, KBOOK_ADMIN → `/admin/dashboard`, restricted → `/limited-access`).
- Successful password reset: toast + redirect to `/login`.

## Sensitive actions

None on this screen (auth submission handled by existing service). Prevent duplicate submits by disabling the button while `loading`.

## Accessibility requirements

- `<h1>` for "Sign in" / "Reset password" / "Verify your phone".
- Every input inside a `<label>` (or associated `for`/`id`).
- Password visibility toggle: `aria-pressed` reflects state.
- Show inline errors and associate via `aria-describedby`; set `aria-invalid` on the control.
- Auth failure banner: `role="alert"`.
- Focus goes to the first invalid field on submit failure.

## Existing functionality that must be retained

- Existing auth service call (identical endpoint, payload, headers).
- Existing JWT / session storage behavior.
- Existing Google sign-in flow (button is presentation only — reuse the existing service).
- Existing role redirect logic and `authGuard` / `roleGuard`.
- Existing forgot-password / OTP / reset routes and services.

## Visual acceptance criteria

- Espresso brand panel matches `--kb-color-espresso` background.
- Primary "Sign in" button uses `--kb-color-primary` and `--kb-fs-body-strong`.
- Focus rings visible on all inputs and buttons.
- No serif/gradient bleed onto the form panel (Inter throughout).
- Form remains usable at 320px width with all controls ≥ 40px tall (44px on mobile primary).
