# Menu and OCR

## Prototype route

`/proto/owner/menu`

## Existing production route

`/business/menu`

## Layout anatomy

- `kb-page-header title="Menu"` with actions: "Add item", "Upload menu (OCR)".
- `kb-filter-toolbar`: search, category filter chips, food-type filter (veg / non-veg / egg), view toggle (grid / table).
- Grid view: item cards.
- Table view: `kb-data-table`.
- `kb-drawer` for add / edit item.
- `kb-dialog` (via `kb-confirmation-dialog`) for delete.
- `kb-dialog` housing the OCR upload flow.

## Shared components required

- All shell primitives, `kb-filter-toolbar`, `kb-data-table`, `kb-mobile-data-card`, `kb-status-badge`, `kb-currency`, `kb-drawer`, `kb-dialog`, `kb-confirmation-dialog`, `kb-file-upload`, `kb-form-field`, `kb-button`, `kb-empty-state`, `kb-error-state`, `kb-skeleton`, `kb-toast`.

## Unique page components

- `KbMenuItemCardComponent` — grid tile with image, name, price, food-type dot, availability toggle.
- `KbMenuItemFormComponent` — projected into `kb-drawer` for add / edit.
- `KbOcrUploadDialogComponent` — projected into `kb-dialog`; wraps `kb-file-upload` and drives polling states.

## Desktop layout

- Toolbar row + view toggle top-right.
- Grid: 4 columns; Table: full-width.
- Drawer opens `size="md"`.

## Tablet layout

- Grid: 3 columns; Table with reduced columns.

## Mobile layout

- Grid: 2 columns (or 1 if the existing app prefers).
- Table replaced by mobile card list.
- OCR dialog becomes bottom sheet.

## Loading state

- Grid: 6 skeleton tiles.
- Table: 5 skeleton rows.
- Drawer form: field-level skeletons.

## Empty state

- No items: `kb-empty-state title="Your menu is empty" description="Add items manually or upload a menu photo — we'll extract the items."` with two CTAs.
- Filter empty: same component with "No items match your filters".

## Error state

- List: `kb-error-state` with retry.
- OCR: file-upload state `error` with retry.

## Validation state

- Item name required, min 2 chars.
- Price required, > 0.
- Category required.
- Availability toggle immediate save; roll back on failure with a danger toast.

## Success state

- Add: drawer closes; toast "Item added".
- Edit: drawer closes; toast "Item updated".
- OCR success: dialog swaps to review list with extracted items; user confirms to save.
- OCR partial: warning tint + list of items needing prices; user completes them before saving.

## Sensitive actions

- **Delete menu item**: `kb-confirmation-dialog` with entity name, consequence "This will remove the item from your menu. Existing orders are unaffected.", `confirmLabel="Delete item"`, tone `danger`. Prevent double-submit.

## OCR workflow rules

- File input restricted to the existing accepted MIME types and size limit.
- On upload:
  1. `validating` (client-side check).
  2. `uploading` (POST with existing multipart form field name — do NOT rename).
  3. `processing` (poll existing OCR job endpoint at the existing interval).
  4. `success` | `partial` | `error`.
- Polling MUST stop on: success, failure, timeout, dialog close, component destroy. Use `takeUntil(destroy$)` or equivalent RxJS teardown — this is a common leak site.

## Accessibility requirements

- Item card: image `alt=""` (decorative) unless meaningful; name in `<h3>` inside a card link.
- Availability toggle: real `role="switch"` with `aria-checked`.
- Grid/Table view toggle: `role="tablist"` with two `role="tab"` buttons.
- OCR upload: dedicated live region announces state changes; progress bar has `aria-valuenow`.
- Delete dialog uses `role="alertdialog"`; default focus on Cancel.

## Existing functionality that must be retained

- Menu list, item detail, add, edit, delete endpoints — unchanged payloads and field names.
- Category API and availability API unchanged.
- OCR upload multipart field name and OCR job polling endpoint unchanged.
- Existing role restriction.
- Existing image upload / hosting behavior.

## Visual acceptance criteria

- Veg / non-veg dot: green square outline for veg, red square outline for non-veg — do not use colour alone; include `aria-label`.
- Prices right-aligned in the table; tabular-nums.
- No serif/gradient anywhere on this page.
