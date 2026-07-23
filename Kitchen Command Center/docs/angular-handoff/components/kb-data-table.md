# kb-data-table

## Purpose

Presentational wrapper for tabular data with consistent header, row, hover, empty, loading, and error styling. **Business logic (sorting, pagination, selection state) stays in the host page or a directive.**

## Suggested selector

`kb-data-table`

## Suggested standalone component

`KbDataTableComponent<T>`

## Inputs

- `columns: KbDataTableColumn<T>[]`
- `rows: T[]`
- `loading?: boolean`
- `empty?: { title: string; description?: string }` — shown when `rows.length === 0` and `!loading`.
- `error?: { title: string; description?: string; retryLabel?: string }` — shown when set; suppresses rows.
- `stickyHeader?: boolean` (default `true`)
- `density?: 'comfortable' | 'compact'`
- `mobileBreakpoint?: number` — below this width the host page should switch to `kb-mobile-data-card`; the table itself simply enables horizontal scroll.
- `trackBy?: (index: number, row: T) => string | number`

Where:

```
interface KbDataTableColumn<T> {
  key: string;
  header: string;
  align?: 'left' | 'right' | 'center';
  width?: string;   // CSS width
  ariaSort?: 'ascending' | 'descending' | 'none';
  cellTemplate?: TemplateRef<{ $implicit: T }>;
}
```

## Outputs

- `retry: EventEmitter<void>` — from the error state's retry button.
- `sortChange: EventEmitter<{ key: string; direction: 'asc' | 'desc' }>` — if header is clickable.

## Content projection

- `[slot="toolbar"]` (optional): filter/search row above the table (typically `kb-filter-toolbar`).
- `[slot="footer"]` (optional): pagination controls.

## Visual variants

Single variant. Header uses `--kb-color-surface-2`; body rows on `--kb-color-surface`.

## Sizes

- `comfortable`: row 48px, cell padding 12×16.
- `compact`: row 40px, cell padding 8×12.

## CSS classes

`.kb-table`, `.kb-table__thead`, `.kb-table__row`, `.kb-table__row--hover`, `.kb-table__cell`, `.kb-table__cell--num` (tabular-nums right-aligned), `.kb-table--loading`, `.kb-table--empty`, `.kb-table--error`.

## CSS variables consumed

`--kb-color-surface`, `--kb-color-surface-2`, `--kb-color-border`, `--kb-color-muted-foreground`, `--kb-radius-xl`, `--kb-shadow-xs`, `--kb-z-sticky`, `--kb-fs-body-sm`.

## States

- **Default**: hairline border between rows, no per-row shadow.
- **Hover**: row background tints to `--kb-color-muted`.
- **Focus** (row anchor): 2px saffron ring inset.
- **Loading**: header rendered; body replaced by 5–8 skeleton rows (see `kb-skeleton`).
- **Empty**: body replaced by `kb-empty-state` inside a single row spanning all columns.
- **Error**: body replaced by `kb-error-state` with retry button.
- **Disabled row**: opacity 0.55; not interactive.

## Mobile behavior

Below `mobileBreakpoint` (default 768px), the host page should render `kb-mobile-data-card` list instead. The table itself never introduces horizontal page overflow — it scrolls inside a bounded container.

## Keyboard behavior

- If rows are clickable, make the entire row a link/button (not the cell) and expose it in Tab order.
- Sortable headers: `<button>` inside `<th>`, with `aria-sort` on the `<th>`.

## Accessibility

- Native `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th scope="col">`, `<td>`.
- `<caption class="visually-hidden">` describing the table.
- Numeric columns right-aligned with `font-variant-numeric: tabular-nums`.

## Related prototype

`/proto/owner/orders`, `/proto/owner/staff`, `/proto/admin/businesses`.
