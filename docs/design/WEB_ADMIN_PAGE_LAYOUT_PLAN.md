# Web-Admin Inner Page Layout Plan

Companion to `DESIGN_SYSTEM.md` (tokens, states, a11y) and `LayoutGuidelines.md`
(primitives). Where those define *what* the parts are, this defines **how each
page is composed from top to bottom** — the order of its bands, its one primary
action, the states it must cover, and the rules that stop a page from growing a
second way of doing something.

Status: **target design for the current routes.** Anything marked *(target)* is
not built yet; everything else exists and is test-pinned.

---

## 1. The page contract (every page, no exceptions)

```
┌──────────────────────────────────────────────────────────────┐
│ <app-page-header>  h1 + subtitle        [actions] [refresh]  │  Zone 1
├──────────────────────────────────────────────────────────────┤
│ [stale banner]  only when a refresh failed with data on hand  │  Zone 2 (optional)
├──────────────────────────────────────────────────────────────┤
│ KPI band        <app-kpi-row> — counted from loaded data only │  Zone 3 (optional)
├──────────────────────────────────────────────────────────────┤
│ Filter / toolbar band   chips · select · reset               │  Zone 4 (optional)
├──────────────────────────────────────────────────────────────┤
│ Primary content  table (desktop) │ card list (≤640px)        │  Zone 5
├──────────────────────────────────────────────────────────────┤
│ <app-pagination-bar>                             (list pages) │  Zone 6 (optional)
└──────────────────────────────────────────────────────────────┘
```

**Rules**

1. One `<h1>` per page, owned by `<app-page-header>`. No page-local title markup.
2. One primary action per page (`header-action-btn--primary`). Everything else is
   neutral or ghost.
3. `<app-refresh-button>` is the only refresh control; it spins *and* says
   "Refreshing…". A page that shows data must also show a skeleton while a
   **manual** refresh is in flight.
4. Loading = skeleton blocks, never a centred spinner. Tables swap to skeletons;
   they do not keep stale rows under a spinner. A record skeleton copies the
   **anatomy** of the row it replaces — photo tile or avatar, name, meta text,
   chip, action — using one empty shape per slot. Every shape is content-free:
   no initials, no photo, no dish name, no order code, no price, no guest name.
   The detail stays invisible until the record exists (`skeleton-avatar`,
   `skeleton-thumb`, `skeleton-line--name/meta/chip/action` in `styles.css`).
5. Errors **replace** content only when there is nothing to show. If data is on
   screen, use the stale banner (`role="status"`) and keep the numbers.
6. Background polls are silent: no skeleton, no disabled refresh button.
7. The table design (padding, type, header band, row states) lives once in
   `styles.css`. Pages own column layout only.
8. Badges use the shared `.kpi-tag` / `.chip` vocabulary. No `.page-foo-badge`.
9. Every band is separated by the same vertical rhythm (`var(--kb-space-3/4)`),
   never a bespoke margin.
10. Every page passes `check:type-scale`, `check:colors`, `check:spacing`,
    `check:inline-styles`, `check:tokens`, `check:breakpoints`.

---

## 2. Page-by-page composition

### 2.1 Dashboard — `/business/dashboard`

**Job:** answer "how is today going?" in one screen, without scrolling on a laptop.

| Zone | Content |
|---|---|
| Header | Shop name as title, subtitle `Real-time sales, order settlements, and terminal activity.`; fleet pill (active/registered terminals), low-stock pill, freshness chip, `<app-refresh-button>` |
| KPI band | `<app-kpi-row ariaLabel="Key Performance Indicators">` — 4 tiles: gross sales, seated tables, kitchen queue, tender split |
| Full width | **Hourly Revenue Velocity** — bar track, peak badge, "View as table" disclosure, 3 summary tiles (busiest hour, items sold, hours with sales) |
| Split grid | Left (8 cols): **Live Floor** (floor filter + table chips per table). Right (4 cols): **Live Ledger Feed**, **Low Stock** summary, **Top Selling Items**, **POS terminals** |

States: skeleton header + 4 shimmer KPIs + 2 intel shimmers on load and manual
refresh; offline error panel with retry; stale banner when a poll fails.
Rules: 60s poll is silent; terminal fleet pill and low-stock pill open modals,
they do not navigate away; the hourly chart always offers the table alternative.

### 2.2 Orders — `/business/orders`

**Job:** two halves of one job — unsettled bills now, settled history after.

| Zone | Content |
|---|---|
| Header | Title `Orders`; on the **history** tab only: search input + date-range toolbar (download + refresh) |
| Tabs | `Live orders` / `Order history` — dark raised pill on `surface-2`, active tab is `surface-3` + link ink |
| Live panel | Active-orders grid, 30s poll, order detail + cancel modals |
| History panel | Filters, orders table (order #, customer/table, status, payment, total, time), pagination, detail modal, CSV export |

States: per-tab skeletons; the hidden tab is **destroyed** so its polling stops;
history empty state distinguishes "no orders in range" from "nothing matches the search".
Rules: tab lives in `?tab=`; `/business/active-orders` bookmarks land on `live`.

### 2.3 Menu — `/business/menu`

**Job:** keep the catalogue sellable — prices, variants, stock, stars.

| Zone | Content |
|---|---|
| Header | Search, Favourites toggle (with count), Categories, **Add New Dish** (primary), refresh |
| KPI band | Total dishes, available, out of stock, categories |
| Filter band | Status chips (All / Available / Out of stock, each with count) · Category select · Clear filters |
| Favourites panel | Only when the toggle is on: starred count + pinned category chips |
| Table | Item (thumb, name, description) · Category · Type · **Price (base + every variant in one cell)** · Availability (**the chip is the control**) · Updated · Actions (star/edit/delete) |
| Footer | Pagination |

States: skeleton rows; empty state with "Add Item" for owners; stale banner on
refresh failure. Mobile ≤640: card list with the same facts, actions as ghost buttons.
Rules: availability appears once (as the control), never as a second read-only badge;
variant prices are not a separate column.

### 2.4 Inventory — `/business/inventory`

**Job:** know what is in the store, what it is worth, and where it went.

| Zone | Content |
|---|---|
| Header | **Add Material** (primary), refresh |
| Summary line | N materials · N low stock · estimated value (all counted client-side) |
| Tabs | Materials · Item Recipes · Purchase Orders · Stock Movements · Variance Report |
| Materials tab | Low-stock filter + grid of material cards (name, stock vs threshold, value, actions) |
| Movements tab | Material picker + movement list (type, quantity ±, time) |
| Variance tab | Date/range controls + expected vs counted |
| Detail modal | Widens to 640px, tabs: Stock In · Wastage · Physical Count |

Rules: a card is low-stock only when stock < threshold, and says so in words as
well as colour; destructive actions sit in the modal footer, not on the card.

### 2.5 Staff — `/business/staff`

**Job:** who can sign in, on what role, and who has been switched off.

| Zone | Content |
|---|---|
| Header | **Add Staff** (primary), refresh |
| Table | Staff Member · Login ID · Assigned Role · Contact · Status · Updated · Actions |
| Modal | Create/edit staff (`staff-form-modal`) |

Rules: sign-out is **per row**; there is no "sign out all devices" bulk action in
the header (removed deliberately — one misclick logged out the whole counter).
Deactivating keeps the row and its history.

### 2.6 Terminals — `/business/terminals`

**Job:** is every billing device alive, paired and syncing?

| Zone | Content |
|---|---|
| Header | Pairing action *(target: single "Pair terminal" primary)*, refresh |
| Section | **Registered terminals** — fleet cards: name, online dot, last seen, primary switch, recover |
| Section | **Device Pairing Requests** — pending devices with confirm/reject |
| Section | **Terminal recovery ready** — devices needing attention, with the reason |
| Dialog | Confirm device number |

Rules: online/offline is a word plus a dot, never colour alone; recovery never
hides the device (it stays listed, visibly flagged); primary-terminal change is
confirmed.

### 2.7 Daily Closing — `/business/daily-closing`

**Job:** count the drawer, explain the variance, lock the day.

| Zone | Content |
|---|---|
| Header | Date-range toolbar with **Print slip** and refresh |
| Section | **Physical Cash Drawer Reconciliation** — expected vs counted vs variance, per channel |
| Section | **Payment Channel Breakdown** — cash / UPI / card totals |
| Section | **Ticket Status Audit** — settled, open, cancelled counts |

Rules: variance is signed and labelled (short/excess), not just red; figures are
withheld during a manual reload so the skeleton is unmistakable; print layout is
owned by the `@media print` block in `styles.css`.

### 2.8 Reports — `/business/reports`

**Job:** the period's books, exportable.

| Zone | Content |
|---|---|
| Header | Date-range toolbar (download + refresh) |
| KPI strip | Skeleton-fed summary row, then the financial strip (revenue, tax, discounts, net) |
| Section | **Tax Invoices & Bill Breakdown** — invoice table with GST columns |
| Section | **Item-Wise Sales & Quantity Volume** — dish, qty, revenue |
| Footnote | Audit note: what the numbers include/exclude |

Rules: the report is withheld during a manual reload; the export respects the
selected range; no page-local tab design (shared pill pattern).

### 2.9 Business Settings — `/business/settings`

**Job:** the restaurant's identity, taxes, payments and security — the only place
these live.

| Zone | Content |
|---|---|
| Header | **Save** (primary), refresh, `Live Sync Ready` badge |
| Section 1 | Restaurant identity & invoicing + logo uploader |
| Section 2 | Tax (`app-tax-settings-section`) — GST toggle, GSTIN, %, custom tax, FSSAI + expiry |
| Section 3 | Payment channels (`app-payment-methods-section`) — cash/UPI/POS, UPI handle validation + ₹1 test QR, Easebuzz sub-merchant status |
| Section 4 | Change password |
| Sticky dock | Appears only when dirty: "Unsaved changes" + Discard |

Rules: one Save for the whole page; `canDeactivate` guards unsaved edits;
payment and tax are **sections here**, not sidebar destinations, until they earn a
page of their own.

### 2.10 Help & Support — `/business/help`

**Job:** route people to the screen that fixes their problem, and out to support.

| Zone | Content |
|---|---|
| Header | Title + subtitle only (no actions) |
| Section | **Start here** — 3 cards → Menu / Terminals / Daily Closing |
| Section | **Common questions** — native `<details>` disclosures |
| Section | **Contact support** — one external action |

Rules: no API calls (help must work when the backend does not); no ticket list
until a ticketing backend exists.

### 2.11 Admin surface (platform role)

- **Platform Dashboard** — KPI mini row, per-tab refresh, tabs; same header/refresh contract.
- **Businesses** — search + table of tenants; row opens the tenant.
- **Feature Flags** — flags table with kill switch per row + audit panel per flag.

Admin rail stays flat (three destinations do not earn headings).

---

## 3. Responsive behaviour (all pages)

| Breakpoint | Behaviour |
|---|---|
| ≥1101px | Full rail + 8/4 split grids; tables show all columns |
| 1025–1100 | Rail collapses to 64px icons (headings hidden, hover/focus peek chip names the target) |
| 769–1024 | Rail becomes a drawer; `app-bottom-action-bar` appears (Dashboard, Orders, Closing, Settings) |
| ≤768 | KPI grid 2-up; split grids stack |
| ≤640 | Tables become card lists; filter bands stack; pagination centres |

---

## 4. Build order (next work)

1. **Terminals**: "Pair terminal" as the single primary action; make recovery a
   state on the card rather than a separate section header.
2. **Reports**: give the financial strip the same KPI anatomy as the dashboard so
   the two money screens read alike.
3. **Inventory**: move `Add Material` out of the header on the movements tab
   (wrong action for that context).
4. **Orders history**: surface refund state in the row, not only in the modal.
5. **Settings**: group the four sections under a sticky section index on >1100px.
6. **Payments page** *(only when it earns one)*: gateway status, refund history,
   reconciliation — currently a Settings section plus Orders detail.

## 5. Guardrails

- Do not add a sidebar destination before its page exists and does something.
- Do not add a second table design, badge vocabulary, refresh control or modal shell.
- Do not put configuration on a dashboard; dashboards read, settings write.
- Do not let a poll flash skeletons or disable controls.
- Do not restate `styles.css` table rules inside a page.
