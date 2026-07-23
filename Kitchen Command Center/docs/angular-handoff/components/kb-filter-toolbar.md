# kb-filter-toolbar

## Purpose

Row of filter controls (search, chips, dropdowns, date-range) that sits above a list or table. Layout-only component — the actual filter state stays in the host page.

## Suggested selector

`kb-filter-toolbar`

## Suggested standalone component

`KbFilterToolbarComponent`

## Inputs

- `dense?: boolean` (default `false`)

## Outputs

None.

## Content projection

- Default slot: individual filter controls (`kb-search-input`, chip group, `<select>`, date-range).
- `[slot="end"]` — trailing actions (Export, Refresh).

## Visual variants

Single variant. Wraps to multiple rows when needed; never causes horizontal overflow.

## Sizes

Height auto. Gap `--kb-space-3`. Padding vertical `--kb-space-3`.

## CSS classes

`.kb-filter-toolbar`, `.kb-filter-toolbar--dense`, `.kb-filter-toolbar__end`.

## CSS variables consumed

`--kb-space-2`, `--kb-space-3`, `--kb-color-border`.

## States

No interactive states of its own.

## Mobile behavior

Controls wrap and stack. Trailing `[slot="end"]` actions move to a full-width row below.

## Keyboard behavior

Standard Tab order left-to-right, then trailing slot.

## Accessibility

- Wrap in `<div role="search">` if the toolbar's dominant purpose is search; otherwise no role.
- Individual controls carry their own labels.

## Related prototype

`/proto/owner/orders`, `/proto/owner/menu`, `/proto/admin/businesses`.
