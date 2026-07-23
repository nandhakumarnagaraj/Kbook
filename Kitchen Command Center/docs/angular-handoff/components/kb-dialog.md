# kb-dialog

## Purpose

Modal dialog for small focused interactions: forms, confirmations, one-time reveals. Centered on desktop, bottom-sheet on mobile.

## Suggested selector

`kb-dialog`

## Suggested standalone component

`KbDialogComponent`

## Inputs

- `open: boolean`
- `title: string`
- `description?: string`
- `size?: 'sm' | 'md' | 'lg'` — 384 / 448 / 512px.
- `dismissible?: boolean` (default `true`) — controls whether backdrop click and Escape close the dialog.
- `role?: 'dialog' | 'alertdialog'` (default `dialog`; use `alertdialog` for destructive confirmations).

## Outputs

- `openChange: EventEmitter<boolean>`
- `dismiss: EventEmitter<'backdrop' | 'escape' | 'close-button'>`

## Content projection

- Default slot: body.
- `[slot="footer"]` — action buttons (typically right-aligned).

## Visual variants

Single variant: `--kb-color-surface`, `--kb-radius-xl`, `--kb-shadow-lg`, backdrop scrim.

## Sizes

Width tokens `--kb-dialog-width-sm|md|lg`. Height auto with max `min(90vh, 640px)`; body scrolls internally.

## CSS classes

`.kb-dialog__backdrop`, `.kb-dialog`, `.kb-dialog__header`, `.kb-dialog__title`, `.kb-dialog__description`, `.kb-dialog__body`, `.kb-dialog__footer`.

## CSS variables consumed

`--kb-color-surface`, `--kb-radius-xl`, `--kb-shadow-lg`, `--kb-z-backdrop`, `--kb-z-modal`, `--kb-duration-slow`, `--kb-ease-decelerate`.

## States

- **Closed**: not rendered / removed from tree.
- **Open**: focus trapped inside; body scroll locked; `aria-modal="true"`.
- **Submitting** (host-controlled): footer primary button uses `loading` state; dismissible=false during submission.
- **Success / Error** (host-controlled): body content swaps; keep dialog open until user dismisses so state is visible.

## Mobile behavior

Below 640px, dialog becomes a bottom sheet: full width, `--kb-radius-xl` top corners, animates from bottom, max-height 88vh.

## Keyboard behavior

- Focus moves to the first focusable element (usually the close button or first form field).
- Tab / Shift+Tab loops inside.
- `Escape` closes when `dismissible` is `true`.
- Focus returns to the triggering element on close.

## Accessibility

- Root: `role="dialog"` or `role="alertdialog"`, `aria-modal="true"`, `aria-labelledby=<title id>`, `aria-describedby=<description id>`.
- Prefer Angular CDK `Dialog` / `OverlayModule` and `FocusTrap`.
- Body scroll must be locked while open.

## Related prototype

`/proto/owner/orders` (refund), `/proto/owner/menu` (delete), `/proto/owner/staff` (temp password).
