# kb-toast

## Purpose

Transient status message anchored to the viewport (typically top-right on desktop, top on mobile). Used for success confirmations and non-blocking errors.

## Suggested selector

`kb-toast` (renders inside a `kb-toast-outlet` host).

## Suggested standalone component

`KbToastComponent` (individual toast) + `KbToastService` (host-side queue). The exact service shape is left to the Angular project.

## Inputs (per toast)

- `tone: 'success' | 'warning' | 'danger' | 'info'`
- `title: string`
- `description?: string`
- `duration?: number` — ms (default 5000; danger toasts default 8000).
- `actionLabel?: string`
- `dismissible?: boolean` (default `true`).

## Outputs

- `action: EventEmitter<void>`
- `dismiss: EventEmitter<void>`

## Content projection

None (toast content is compact).

## Visual variants

Left border colour + soft tinted background per tone.

## Sizes

Width min 320px, max 420px. Height auto.

## CSS classes

`.kb-toast`, `.kb-toast--<tone>`, `.kb-toast__title`, `.kb-toast__description`, `.kb-toast__actions`.

## CSS variables consumed

`--kb-color-surface`, tone tokens, `--kb-shadow-md`, `--kb-radius-lg`, `--kb-z-toast`.

## States

- **Enter**: slide + fade in for `--kb-duration-slow`.
- **Idle**: visible.
- **Exit**: fade + slide out.
- **Hover**: pause auto-dismiss timer.
- **Focus**: pause + show 2px saffron ring.

## Mobile behavior

Stack from the top of the viewport, full width minus 16px gutter.

## Keyboard behavior

- New toast does not steal focus.
- Focusable via a global keyboard shortcut (e.g. F6) or via Tab if visible.
- `Escape` inside a focused toast dismisses it.

## Accessibility

- Success / info: `role="status"`, `aria-live="polite"`.
- Warning / danger: `role="alert"`, `aria-live="assertive"`.
- Dismiss button carries `aria-label="Dismiss notification"`.

## Related prototype

Success and error toasts implied by refund success, staff temp password success, marketplace save.
