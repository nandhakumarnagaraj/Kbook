# kb-kpi-card

## Purpose

Displays a single key metric: label, value, optional trend indicator, optional sparkline.

## Suggested selector

`kb-kpi-card`

## Suggested standalone component

`KbKpiCardComponent`

## Inputs

- `label: string`
- `value: string | number`
- `format?: 'number' | 'currency-inr' | 'percent' | 'plain'` (default `plain`)
- `delta?: number` — signed percentage change (drives trend colour + arrow).
- `deltaLabel?: string` — e.g. "vs last week".
- `sparklineData?: number[]` — optional; renders a small trend line.
- `variant?: 'default' | 'hero'` — `hero` uses saffron→burnt gradient background, Instrument Serif value, and `--kb-radius-2xl`. **OWNER dashboard only.**
- `loading?: boolean`

## Outputs

None.

## Content projection

- `[slot="footer"]` (optional): small text/link under the value.

## Visual variants

- `default`: white surface, dark ink, `--kb-radius-xl`.
- `hero`: gradient background (`--kb-gradient-hero`), white ink, `--kb-shadow-warm`, `--kb-radius-2xl`. **Do not use outside OWNER dashboard.**

## Sizes

Auto-height. Min width ~200px; typically laid out in a 2/3/4-column responsive grid.

## CSS classes

- `.kb-kpi-card`, `.kb-kpi-card--hero`, `.kb-kpi-card__label`, `.kb-kpi-card__value`, `.kb-kpi-card__delta`, `.kb-kpi-card--loading`.

## CSS variables consumed

`--kb-color-surface`, `--kb-color-foreground`, `--kb-color-muted-foreground`, `--kb-color-success`, `--kb-color-danger`, `--kb-radius-xl`, `--kb-radius-2xl`, `--kb-shadow-xs`, `--kb-shadow-warm`, `--kb-gradient-hero`, `--kb-fs-kpi-value`, `--kb-fs-hero-value`.

## States

- **Default**: `--kb-shadow-xs`.
- **Hover** (only if actionable): `--kb-shadow-sm`, translateY(-1px) at `--kb-duration-fast`.
- **Focus** (if actionable): 2px saffron ring outset.
- **Loading**: label + value replaced by `kb-skeleton` blocks.
- **Error**: replaced by a compact inline error inside the card body ("Couldn't load").
- **Disabled**: not applicable (KPI cards are read-only).

## Mobile behavior

Single column below 640px. Value truncates with `overflow-wrap: anywhere` for large INR figures.

## Keyboard behavior

Non-interactive by default. If wrapped in a link, standard link behavior applies.

## Accessibility

- Label + value pair should be readable in DOM order (label first, then value).
- Trend indicator uses text ("+12.4%") in addition to colour + arrow — never colour alone.
- `format="currency-inr"` should be rendered by `kb-currency` for i18n consistency.

## Related prototype

`/proto/owner/dashboard` (both variants), `/proto/admin/dashboard` (default variant only).
