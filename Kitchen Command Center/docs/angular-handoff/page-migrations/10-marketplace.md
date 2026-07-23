# Marketplace

## Prototype route

`/proto/owner/marketplace`

## Existing production route

`/business/marketplace`

## Layout anatomy

- `kb-page-header title="Marketplace"`.
- Two provider cards side-by-side (Zomato, Swiggy) on ≥ 1024px; stacked below.
- Each card contains: provider logo, connected state, `kb-form-field`s for credentials, webhook URL row with copy button, primary "Save" action.

## Shared components required

All shell primitives, `kb-form-field`, `kb-secret-input`, `kb-copy-button`, `kb-button`, `kb-status-badge`, `kb-confirmation-dialog`, `kb-toast`, `kb-empty-state`, `kb-error-state`, `kb-skeleton`.

## Unique page components

- `KbMarketplaceProviderCardComponent` — provider tile hosting the form and state.

## Desktop layout

- 2 columns of cards.
- Each card `--kb-radius-xl` with `--kb-shadow-xs`.

## Tablet / Mobile layout

- Single column; cards full-width.

## Card states

- **Connected**: green success badge; credentials shown as `kb-secret-input` in `stored` mode; webhook URL visible with copy button; primary action = "Update" (disabled until a field changes or a secret is in `replace` mode).
- **Incomplete**: warning badge; missing fields highlighted; primary action = "Save".
- **Disabled** (backend flag): entire card inert; message explains why; contact-support link.
- **Error**: `kb-error-state` inside the card body with retry.

## Loading / empty / error

- Loading: field skeletons.
- No providers configured: not applicable (providers are always listed).
- Error: per-card retry.

## Validation state

- Preserve existing per-provider validators (API key format, restaurant/store ID format).
- Save button disabled while invalid or unchanged.

## Success state

- Save success: toast (success tone) "Zomato updated"; card returns to `connected` state; all `kb-secret-input`s revert to `stored` mode with new masked preview.

## Sensitive actions

- **Replace marketplace secret**: the switch from `stored` to `replace` mode inside `kb-secret-input` is not itself a confirmation-gated action, but the save that follows is sensitive.
- If the existing backend logs credential changes for audit, surface a confirmation dialog before Save: `kb-confirmation-dialog title="Update Zomato credentials"`, consequence "Existing sessions may need to reauthenticate.", `confirmLabel="Update credentials"`, tone `default`.

## Critical credential rules

- `kb-secret-input` in `stored` mode is DISPLAY ONLY. The host form MUST omit the field from the update payload — never submit the masked string.
- The `maskedPreview` should never include more than the last 4 characters (backend metadata only).
- Webhook URL is safe to display in full and to copy.
- On save failure, DO NOT flip secret inputs back to `stored` mode automatically — the user's typed value should remain visible so they can retry.

## Accessibility requirements

- Each provider card is a `<section aria-labelledby="...">` with the provider name as heading.
- Webhook URL row: input `readonly` with copy button next to it; `aria-describedby` points at the copy button.
- Secret input toggle uses `aria-pressed`.
- Save action `aria-busy` while submitting.

## Existing functionality that must be retained

- Provider list endpoint.
- Credential update endpoint(s) — field names, unchanged.
- Webhook URL source of truth (backend or config).
- Existing role restriction (OWNER).

## Visual acceptance criteria

- Provider logos load with `alt="Zomato" / "Swiggy"`.
- Connected badges use `--kb-color-success` tone.
- No serif/gradient.
- Secret inputs visually distinct in `replace` mode (border colour shift to indicate an unsaved change).
