# kb-status-badge

## Purpose

Compact pill representing a discrete status: order state, terminal state, business state, integration state, etc.

## Suggested selector

`kb-status-badge`

## Suggested standalone component

`KbStatusBadgeComponent`

## Inputs

- `tone: 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'primary'`
- `label: string`
- `size?: 'sm' | 'md'` (default `sm`)
- `icon?: string` (optional leading icon)

## Outputs

None.

## Content projection

Default slot may replace `label` for rich content, but prefer the input.

## Visual variants

Tone-driven soft pill: soft-tinted background + strong text colour.

| Tone    | Background                | Text                    | Example uses                  |
| ------- | ------------------------- | ----------------------- | ----------------------------- |
| neutral | `--kb-color-muted`        | `--kb-color-foreground` | Draft, Unassigned             |
| success | `--kb-color-success-soft` | `--kb-color-success`    | Paid, Active, Connected       |
| warning | `--kb-color-warning-soft` | `--kb-color-warning`    | Pending, Incomplete           |
| danger  | `--kb-color-danger-soft`  | `--kb-color-danger`     | Refunded, Suspended, Recovery |
| info    | `--kb-color-info-soft`    | `--kb-color-info`       | Informational badges          |
| primary | `--kb-color-primary-soft` | `--kb-color-primary`    | Featured, In-review           |

## Sizes

- `sm`: height 20px, padding 2×6px, `--kb-fs-caption` (11px), `--kb-radius-sm`.
- `md`: height 24px, padding 4×8px, `--kb-fs-body-xs` (12px), `--kb-radius-md`.

## CSS classes

- `.kb-badge`, `.kb-badge--<tone>`, `.kb-badge--sm`, `.kb-badge--md`.

## CSS variables consumed

All `--kb-color-*` tone/soft variables above, `--kb-radius-sm`, `--kb-radius-md`, `--kb-fs-caption`, `--kb-fs-body-xs`.

## States

Static; no hover/focus/disabled variants.

## Mobile behavior

Unchanged. Use `sm` in dense tables, `md` in mobile card headers.

## Keyboard behavior

Non-interactive.

## Accessibility

- Never rely on colour alone — the label text carries the state.
- If a badge conveys real-time changes, wrap the value in an ARIA live region at the page level (not on every badge).

## Related prototype

All prototype pages with tables (`orders`, `terminals`, `staff`, `businesses`).
