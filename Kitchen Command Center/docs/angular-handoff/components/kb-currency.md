# kb-currency

## Purpose

Formats and displays Indian Rupee amounts consistently across the app: `₹` symbol, Indian digit grouping (`1,20,000`), tabular numerals, optional decimals.

## Suggested selector

`kb-currency`

## Suggested standalone component

`KbCurrencyComponent` (or an equivalent Angular pipe `kbInrCurrency` if the project prefers pipes for formatting).

## Inputs

- `value: number | string` — treat as major units (₹). If the backend returns paise, convert in the host.
- `decimals?: 0 | 2` (default 0 for lists, 2 for reports/invoices).
- `showSymbol?: boolean` (default `true`).
- `sign?: 'auto' | 'always' | 'never'` (default `auto`).
- `size?: 'inherit' | 'kpi' | 'hero'` — visual scale.

## Outputs

None.

## Content projection

None.

## Visual variants

- `inherit`: uses ambient font size.
- `kpi`: `--kb-fs-kpi-value`, 600, tabular-nums.
- `hero`: `--kb-fs-hero-value`, Instrument Serif — **OWNER dashboard hero only**.

## CSS classes

`.kb-currency`, `.kb-currency--kpi`, `.kb-currency--hero`, `.kb-currency--negative`.

## CSS variables consumed

`--kb-fs-kpi-value`, `--kb-fs-hero-value`, `--kb-font-display`, `--kb-color-danger`, `--kb-color-foreground`.

## States

Static.

## Mobile behavior

Numeric value stays on one line; parent should permit `overflow-wrap: anywhere` for extremely large amounts.

## Keyboard behavior

Non-interactive.

## Accessibility

- Symbol `₹` is meaningful text (do NOT mark as decorative).
- Use `aria-label="Twelve lakh rupees"` when the numeric formatting is ambiguous for screen readers (optional).
- Include a locale hint (`lang="en-IN"`) on the ancestor if the app supports multiple locales.

## Related prototype

KPI values across `/proto/owner/dashboard`, `/proto/owner/reports`, `/proto/owner/orders`.
