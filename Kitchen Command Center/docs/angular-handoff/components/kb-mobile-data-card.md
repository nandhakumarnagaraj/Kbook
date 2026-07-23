# kb-mobile-data-card

## Purpose

Mobile-first list card that replaces `kb-data-table` rows below the table's `mobileBreakpoint`. Presents a single record as a stacked, tappable card with title, meta, status, and primary action.

## Suggested selector

`kb-mobile-data-card`

## Suggested standalone component

`KbMobileDataCardComponent`

## Inputs

- `title: string`
- `subtitle?: string`
- `meta?: Array<{ label: string; value: string }>` — 2–4 items.
- `status?: { tone: KbStatusBadgeTone; label: string }` — rendered as a top-right `kb-status-badge`.
- `href?: string` — makes the entire card a link.
- `disabled?: boolean`

## Outputs

- `activate: EventEmitter<void>` — when the card is tapped and `href` is not set.

## Content projection

- `[slot="actions"]` — bottom-row buttons (`kb-button`).

## Visual variants

Single variant: `--kb-color-surface`, `--kb-radius-xl`, `--kb-shadow-xs`.

## Sizes

Full width of its container; min-height 88px.

## CSS classes

`.kb-mobile-card`, `.kb-mobile-card__title`, `.kb-mobile-card__meta`, `.kb-mobile-card__status`, `.kb-mobile-card--disabled`.

## CSS variables consumed

`--kb-color-surface`, `--kb-color-border`, `--kb-radius-xl`, `--kb-shadow-xs`, `--kb-space-4`, `--kb-fs-body-strong`, `--kb-fs-body-xs`.

## States

- **Default**, **Hover** (subtle background tint), **Focus** (2px saffron ring outset), **Pressed**, **Disabled** (opacity 0.55).

## Mobile behavior

Primary layout target. Above 768px the host page uses `kb-data-table` instead.

## Keyboard behavior

Full card is a single focusable element. Enter/Space activates.

## Accessibility

- Prefer `<a>` when `href` is set; otherwise a `<button type="button">`.
- Do not nest interactive elements inside the tappable card — put row-level actions in the `[slot="actions"]` outside the primary hit area.

## Related prototype

`/proto/owner/orders` on mobile.
