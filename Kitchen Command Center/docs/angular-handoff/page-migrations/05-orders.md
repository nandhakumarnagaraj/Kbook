# Orders and refunds

## Prototype route

`/proto/owner/orders`

## Existing production route

`/business/orders`

## Layout anatomy

- `kb-page-header title="Orders"` with search + status filter in `[slot="filters"]`.
- `kb-filter-toolbar`: search input, status chip group (all / paid / pending / refunded), date filter, terminal filter.
- `kb-data-table` (desktop) / `kb-mobile-data-card` list (mobile).
- `kb-drawer` for order details.
- `kb-confirmation-dialog` for manual refund.

## Shared components required

- `kb-app-shell`, `kb-page-header`, `kb-filter-toolbar`, `kb-data-table`, `kb-mobile-data-card`, `kb-status-badge`, `kb-currency`, `kb-drawer`, `kb-confirmation-dialog`, `kb-button`, `kb-skeleton`, `kb-empty-state`, `kb-error-state`, `kb-toast`.

## Unique page components

- `KbOrderDetailPanelComponent` — content projected inside `kb-drawer`; shows itemised bill, payment breakdown, and refund history.
- `KbRefundFormComponent` — reason `<textarea>` + amount input, projected into `kb-confirmation-dialog`.

## Desktop layout

- Toolbar row above the table.
- Table columns: Order ID, Time, Terminal, Items, Amount (right-aligned), Status, ⋯.
- Drawer opens from the right (`size="md"`).

## Tablet layout

- Same as desktop with reduced column set (drop Terminal or Time).

## Mobile layout

- Toolbar wraps; status chips scroll horizontally.
- Table replaced by `kb-mobile-data-card` list: title = Order ID, subtitle = time, meta = amount + terminal, badge = status.
- Drawer becomes full-width slide-in.
- Refund dialog becomes bottom sheet.

## Loading state

- Table skeleton (5–8 rows).
- Drawer body skeleton for `KbOrderDetailPanelComponent`.

## Empty state

- No orders: `kb-empty-state title="No orders match your filters" description="Adjust your search or clear filters."` with a "Clear filters" CTA.

## Error state

- Table `kb-error-state` with retry.
- Drawer error: inline error inside the drawer body with a retry button.

## Validation state (refund dialog)

- Amount required, > 0, ≤ order amount (use existing validators).
- Reason required, min 8 chars.
- Amount step matches existing rules.

## Success state

- Refund success: dialog swaps to success message with reference ID (mono font), primary button becomes "Done"; row status updates to `refunded`.
- Toast (success tone) also confirms.

## Sensitive actions — Manual refund

Use `kb-confirmation-dialog` with:

- `title="Refund order {id}"`
- `entityLabel="Order {id} · ₹{amount}"`
- `consequence="This will return the customer's payment via the original method. Refunds cannot be reversed."`
- `tone="danger"`
- `confirmLabel="Refund order"`
- `state` cycles: `idle → submitting → success | error`
- Prevent duplicate submits: the dialog is `dismissible=false` while `submitting`; the confirm button is disabled; on error, `state="error"` re-enables retry.

## Accessibility requirements

- Table row navigation via full-row link or `<button>` inside the row.
- Drawer focus trap; Escape closes; focus returns to the invoking row.
- Refund dialog uses `role="alertdialog"`; default focus lands on Cancel.
- Refund amount uses `inputmode="decimal"` and locale-formatted display in `kb-currency`.
- Announce refund success via a polite live region.

## Existing functionality that must be retained

- Orders list endpoint, query params (search, status, date, terminal, pagination).
- Order detail endpoint.
- `RefundOrderRequest` payload — field names, types, currency unit (paise vs rupees) preserved exactly.
- Existing role restriction (OWNER; SHOP_ADMIN if the existing app allows).
- Existing polling / refresh cadence, if any.

## Visual acceptance criteria

- Status badges use tone mapping: paid=success, pending=warning, refunded=danger, unknown=neutral.
- Order IDs render in `--kb-font-mono` for scannability.
- Amounts right-aligned with tabular-nums.
- No horizontal page overflow at 320px.
