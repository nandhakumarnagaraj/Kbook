# kb-skeleton

## Purpose

Neutral placeholder shape for loading content. Used in KPI cards, tables, drawers.

## Suggested selector

`kb-skeleton`

## Suggested standalone component

`KbSkeletonComponent`

## Inputs

- `variant?: 'text' | 'rect' | 'circle'` (default `text`).
- `width?: string` — CSS width (default `100%`).
- `height?: string` — CSS height (default `1em` for text, `16px` for rect).
- `count?: number` — repeats the shape.

## Outputs

None.

## Sizes

Driven by `width` / `height`.

## CSS classes

`.kb-skeleton`, `.kb-skeleton--text`, `.kb-skeleton--rect`, `.kb-skeleton--circle`.

## CSS variables consumed

`--kb-color-muted`, `--kb-color-surface-2`, `--kb-radius-sm`, `--kb-duration-slow`.

## States

Animated shimmer between `--kb-color-muted` and `--kb-color-surface-2` (respecting `prefers-reduced-motion` — falls back to a static tint).

## Mobile behavior

Unchanged.

## Keyboard behavior

Non-interactive; skipped by focus.

## Accessibility

- Wrap the group in `role="status"` with `aria-live="polite"` and `aria-label="Loading"` when standing in for content.
- Individual skeletons carry `aria-hidden="true"`.

## Related prototype

`/proto/owner/dashboard`, `/proto/owner/orders`, `/proto/admin/businesses`.
