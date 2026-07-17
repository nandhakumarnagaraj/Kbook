# Spec: KhanaBook v1 Web-Admin — Device Management, Invoice Viewer, OCR Upload, Marketplace Fix

**Status:** Draft (backlog-ready)
**Author:** AI agent (gstack /spec)
**Branch context:** `main` @ `4ad54d10`
**Scope decision:** This IS version 1. Per the agreed v1 feature set (manual UPI,
pay-before/after, dine-in, takeaway, offline+online, OCR menu, dual printer,
syncing, KOT, SMS/WhatsApp share, shop admin, device management, Swiggy/Zomato),
the web-admin must cover: device management, OCR menu upload, Swiggy/Zomato
marketplace config, and invoice view/share. All of these are server-ready today
except the marketplace write endpoint (a small missing controller). Menu/Staff/
Profile CRUD and Reports are NOT v1 — they are explicitly out of scope (they need
new server write APIs and were never claimed as v1 web features).

---

## 1. Why

The Android POS (v1) supports device/terminal management, bill invoicing, and OCR
menu import, but the web-admin (Angular, `web-admin/`) cannot do any of these from
the browser. The owner/shop-admin has no way to manage their registered devices,
view/re-send an invoice, or upload a menu PDF from the web. Separately, the existing
marketplace setup page calls `POST /marketplace/config`, which **does not exist on
the server** — so saving Swiggy/Zomato config silently fails.

Who is affected: restaurant OWNER and SHOP_ADMIN roles (web-admin users). KBOOK_ADMIN
gets device visibility via the existing businesses page only.

Today's behavior (verified from code):
- Web-admin has read-only views of dashboard, orders, menu, staff.
- No device management UI exists, though `TerminalManagementController`
  (`/business/terminals`) is fully implemented server-side.
- No invoice view; `InvoiceController` (`/public/invoice/{restaurantId}/{billId}/{token}`)
  renders token-protected HTML but web-admin never calls it.
- No OCR upload; `MenuExtractionController` (`/menus/upload`, `/jobs/{jobId}`) exists
  and is used by Android, not the web.
- Marketplace page `marketplace-setup-page.component.ts` calls
  `business-api.service.saveMarketplaceConfig` -> `POST /marketplace/config` -> 404.

Done means: an owner can manage devices, open an invoice in the browser and copy its
link, upload a menu image/PDF from the web and watch extraction progress, and the
marketplace page saves successfully (or is clearly read-only if deferred).

---

## 2. In scope (server-ready)

### 2.1 Device / Terminal Management page (OWNER + SHOP_ADMIN)
- New route `business/terminals` in `web-admin/src/app/app.routes.ts`, gated by
  `roleGuard` with `roles: ['OWNER', 'SHOP_ADMIN']`.
- New standalone page `web-admin/src/app/pages/terminals/terminals-page.component.ts`
  following the existing `sidebar-layout` + `api-state` (loading/error/retry) pattern.
- New service methods in `web-admin/src/app/core/services/business-api.service.ts`
  against `TerminalManagementController` (`server/.../webadmin/controller/TerminalManagementController.java`,
  path `/business/terminals`):
  - `GET /business/terminals` -> list of terminals (id, name, status, lastActive, deviceId)
  - `POST /business/terminals/{id}/rename` (body: name)
  - `POST /business/terminals/{id}/deactivate`
  - `GET /business/terminals/terminal-requests` -> pending activation requests
  - `POST /business/terminals/terminal-requests/{id}/approve`
  - `POST /business/terminals/terminal-requests/{id}/reject`
  - `POST /business/terminals/{id}/recover`
- Add TS models to `api.models.ts` (`BusinessTerminal`, `TerminalRequest`,
  `RenameTerminalRequest`).
- UI: table of terminals with rename inline-edit, deactivate action, status chip;
  a "Pending device requests" panel with Approve/Reject; a toast on success/error
  using the same patterns as `orders-page` (manual refund already wires toasts).
- Add a sidebar link in `sidebar-layout.component.ts` (OWNER/SHOP_ADMIN only).
- Failure modes: 401 -> redirect login; 403 -> limited-access page; approve of an
  already-approved request -> show server error message as toast, no crash; empty
  list -> empty-state block.

### 2.2 Invoice Viewer
- On `business/orders`, add a "View invoice" action per order that opens the server
  HTML invoice in a new tab:
  `https://kbook.iadv.cloud/api/v1/public/invoice/{restaurantId}/{billId}/{token}`.
- Need the invoice `token` per order. Confirm `BusinessOrder` (api.models.ts:37) or a
  new `GET /business/orders/{id}/invoice-token` endpoint returns it. If the server
  does not expose the token to web-admin today, add a minimal
  `BusinessAdminController.GET /bills/{billId}/invoice-token` returning the token
  (read-only, OWNER/SHOP_ADMIN). This is a tiny server addition; flag if out of scope.
- Also offer "Copy invoice link" (clipboard) and "Send via WhatsApp" (open wa.me link
  with the invoice URL) — parity with Android `shareInvoiceViaWhatsAppLink`.
- Failure modes: missing token -> disable the button with tooltip; 403/404 from
  invoice URL -> open a toast, do not navigate to a broken page.

### 2.3 OCR Menu Upload from web
- New route `business/menu/upload` (or a modal/section in existing `business/menu`
  page) gated for OWNER/SHOP_ADMIN.
- Upload flow against `MenuExtractionController` (`/menus`):
  - `POST /menus/upload` (multipart file) -> `{ jobId }`
  - `GET /menus/jobs/{jobId}` -> status (PENDING/EXTRACTING/DONE/FAILED) + extracted
    items preview
- UI: drag/drop or file picker (PDF/image), progress via polling `GET /menus/jobs/{jobId}`
  every 2s until terminal state, show extracted item list, then "Import to menu"
  (which triggers the existing Android-style sync pull — confirm whether web can
  push extracted items or only preview; if push requires a new endpoint, scope it as
  preview-only and note the gap).
- Add models `MenuExtractionJob`, `MenuExtractionResult` to `api.models.ts`.
- Failure modes: unsupported file type -> reject before upload; job FAILED -> show
  error + retry; network error mid-poll -> show toast + manual refresh.

### 2.4 Marketplace config fix (server-side)
- Build `MarketplaceConfigController` (`@RequestMapping("/marketplace")`) in
  `server/src/main/java/com/khanabook/saas/webadmin/controller/` with:
  - `GET /marketplace/config` -> `MarketplaceConfig` (api.models.ts:114; return
    masked keys, never raw secrets)
  - `POST /marketplace/config` -> accept `MarketplaceConfigRequest` (api.models.ts:125),
    persist Zomato/Swiggy enabled flags + encrypted keys. Mirror the masking pattern
    already used for `zomatoApiKeyMasked`/`swiggyApiKeyMasked`.
- Wire `BusinessMarketplaceSetup` (read) + `MarketplaceConfig` (write) so the existing
  `marketplace-setup-page.component.ts` save button works.
- Security: only OWNER/SHOP_ADMIN; never return raw `apiKey`/`webhookSecret` in GET
  (the model already uses masked fields — enforce server-side).
- Failure modes: invalid key format -> 400 with field error; concurrent save ->
  last-write-wins is acceptable for v1.

---

## 3. Out of scope for v1 (NOT deferred-then-built — excluded from v1)

These were never claimed as v1 web-admin features and require new server write
APIs. They are excluded from this spec entirely; do not build them in v1.

- **Menu CRUD** (create/edit categories, items, variants, price, availability) —
  server only has sync push/pull (`CategoryController`, `MenuItemController`,
  `ItemVariantController`), no owner-facing REST write endpoint. Menu is edited on
  the Android app in v1, not the web.
- **Staff CRUD** (add/disable user, assign role) — sync-only today; edited on Android.
- **Shop profile editor** (name, address, GSTIN, FSSAI, UPI/QR, logo, tax, invoice
  footer) — `RestaurantProfileController` is sync-only; edited on Android.
- **Reports / analytics page** — no reports API server-side; Android `ReportGenerator`
  is offline-only. Reporting stays on Android for v1.
- **Full SHOP_ADMIN route parity** — web grants SHOP_ADMIN access to terminals only
  for v1; other pages stay OWNER-only.

If any of these become v1, open a separate spec rather than expanding this one.

---

## 4. Acceptance criteria (measurable)

1. OWNER can open `/business/terminals`, see all devices, rename one, deactivate one,
   approve/reject a pending request, and see a success toast. Each action verified
   against the live API (curl on VPS or browser).
2. `POST /marketplace/config` returns 200 and a subsequent GET returns the saved
   (masked) values; raw secrets never appear in GET response.
3. From `/business/orders`, "View invoice" opens a valid server-rendered invoice in a
   new tab for an order that has a token; missing-token orders show a disabled button.
4. From the menu page, uploading a PDF yields a job that reaches DONE and shows
   extracted items; unsupported types are rejected pre-upload.
5. All new pages follow existing conventions: `sidebar-layout`, `api-state` wrapper,
   `roleGuard`, signals (no RxJS subscriptions left dangling), responsive CSS from
   `styles.css`.
6. `npm run build` succeeds; `ng test` (existing tests) still pass.

---

## 5. Files to touch (concrete)

**Web-admin (frontend):**
- `web-admin/src/app/app.routes.ts` — add `business/terminals`, `business/menu/upload`
- `web-admin/src/app/pages/terminals/terminals-page.component.ts` (NEW)
- `web-admin/src/app/pages/menu/menu-page.component.ts` — add upload section/modal
- `web-admin/src/app/pages/orders/orders-page.component.ts` — invoice actions
- `web-admin/src/app/core/services/business-api.service.ts` — terminals + OCR + invoice token
- `web-admin/src/app/core/models/api.models.ts` — `BusinessTerminal`, `TerminalRequest`, `MenuExtractionJob`, `MenuExtractionResult`
- `web-admin/src/app/layout/sidebar-layout/sidebar-layout.component.ts` — terminal link

**Server (Java):**
- `server/src/main/java/com/khanabook/saas/webadmin/controller/MarketplaceConfigController.java` (NEW)
- `server/src/main/java/com/khanabook/saas/webadmin/controller/BusinessAdminController.java` — optional `GET /bills/{billId}/invoice-token`
- New DTOs as needed (reuse `MarketplaceConfig`/`MarketplaceConfigRequest` models)

**Deploy:**
- `deploy-web.sh` already exists; rebuild + `./deploy-web.sh` after frontend changes.
- Marketplace controller is backend -> must pass the Flyway-history gate before
  `deploy-production.sh` (see prior session notes on V27/V30 rename risk).

---

## 6. Open questions for reviewer

- Q1: Does the server expose the invoice `token` to web-admin, or must we add
  `GET /bills/{billId}/invoice-token`? (Affects 2.2 scope.)
- Q2: Can the web OCR flow push extracted items into the menu, or is it preview-only
  until a new write endpoint exists? (Affects 2.3 "Import to menu" button.)
- Q3: Should SHOP_ADMIN get terminals-only or full parity with OWNER for these pages?
  (This spec assumes terminals-only for v1.)
