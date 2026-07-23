# kb-error-state

## Purpose

Standard error presentation for a failed data load. Provides a retry action and a concise reason.

## Suggested selector

`kb-error-state`

## Suggested standalone component

`KbErrorStateComponent`

## Inputs

- `title: string` (default "Something went wrong")
- `description?: string`
- `retryLabel?: string` (default "Try again")
- `showRetry?: boolean` (default `true`)
- `dense?: boolean` — for use inside a table row.

## Outputs

- `retry: EventEmitter<void>`

## Content projection

- `[slot="actions"]` — replace default retry with custom actions.

## Sizes

Same shape as `kb-empty-state`.

## CSS classes

`.kb-error`, `.kb-error--dense`, `.kb-error__title`, `.kb-error__description`.

## CSS variables consumed

`--kb-color-danger`, `--kb-color-muted-foreground`, `--kb-fs-section-title`, `--kb-fs-body`.

## States

Static; the retry button uses `kb-button` states.

## Mobile behavior

Reduced padding.

## Keyboard behavior

Retry button focusable; Enter/Space activates.

## Accessibility

- Announce via `role="alert"` when the state appears in response to a user action; use plain `<section>` when it renders as part of initial load.
- Never rely on icon colour alone for state — the title carries the message.

## Related prototype

Error slot in every list/table on `/proto/owner/*`, `/proto/admin/*`.
