# Angular migration inventory template

Copy this template into the KhanaBook Angular repository and fill it in before touching production code. Every row must reach `Complete` (or `Descoped, why`) before the migration ships.

> Legend: fill values as `Unchanged`, `Refactored (visual only)`, `Replaced`, `New`, or an explicit note. Never write "Unknown" — investigate first.

## Route inventory

| Production route        | Existing Angular component | Prototype route                                   | Existing APIs (service.method) | Payload builders                        | Guards                                     | Shared UI required                                                                   | Risk                                   | Status |
| ----------------------- | -------------------------- | ------------------------------------------------- | ------------------------------ | --------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------ | -------------------------------------- | ------ |
| `/login`                |                            | `/proto/login`                                    |                                |                                         | `authGuard`                                | `KbAuthLayout`, `KbFormField`, `KbButton`                                            |                                        |        |
| `/limited-access`       |                            | (reuses login layout)                             |                                |                                         |                                            | `KbAuthLayout`, `KbButton`                                                           |                                        |        |
| `/business/dashboard`   |                            | `/proto/owner/dashboard`                          |                                |                                         | `authGuard`, `roleGuard(OWNER)`            | Shell + `KbKpiCard` (hero+default), `KbDataTable`, chart lib, `KbEmpty/ErrorState`   | High (hero-only styling must not leak) |        |
| `/business/reports`     |                            | (see 04-reports)                                  |                                |                                         | `authGuard`, `roleGuard(OWNER)`            | Shell + `KbKpiCard` default, `KbDataTable`, date-range                               |                                        |        |
| `/business/orders`      |                            | `/proto/owner/orders`                             |                                | `RefundOrderRequest`                    | `authGuard`, `roleGuard(OWNER)`            | Shell + `KbDataTable`, `KbDrawer`, `KbConfirmationDialog`, `KbCurrency`              | High (refund)                          |        |
| `/business/menu`        |                            | `/proto/owner/menu`                               |                                | Menu payload, OCR multipart             | `authGuard`, `roleGuard(OWNER)`            | Shell + `KbDataTable`, `KbDrawer`, `KbFileUpload`, `KbConfirmationDialog`            | High (OCR polling teardown)            |        |
| `/business/staff`       |                            | `/proto/owner/staff`                              |                                | Staff payload                           | `authGuard`, `roleGuard(OWNER)`            | Shell + `KbDataTable`, `KbDrawer`, `KbConfirmationDialog`, `KbCopyButton` (temp pw)  | Medium                                 |        |
| `/business/terminals`   |                            | `/proto/owner/terminals`, `/proto/shop/terminals` |                                | Rename / deactivate / recovery payloads | `authGuard`, `roleGuard(OWNER/SHOP_ADMIN)` | Shell + `KbDataTable`, `KbDrawer`, `KbConfirmationDialog`, `KbCopyButton` (recovery) | High (recovery token)                  |        |
| `/business/marketplace` |                            | `/proto/owner/marketplace`                        |                                | Provider credential payloads            | `authGuard`, `roleGuard(OWNER)`            | Shell + `KbFormField`, `KbSecretInput`, `KbCopyButton`, `KbConfirmationDialog`       | High (masked secrets)                  |        |
| `/admin/dashboard`      |                            | `/proto/admin/dashboard`                          |                                |                                         | `authGuard`, `roleGuard(KBOOK_ADMIN)`      | Shell + `KbKpiCard` default, `KbDataTable`                                           | Low                                    |        |
| `/admin/businesses`     |                            | `/proto/admin/businesses`                         |                                | Activate / Suspend payloads             | `authGuard`, `roleGuard(KBOOK_ADMIN)`      | Shell + `KbDataTable`, `KbDrawer`, `KbConfirmationDialog`                            | Medium (suspend reason)                |        |

## Component inventory

| Existing component                      | Reuse | Refactor (presentation only) | Replace | Business logic retained | Tests required |
| --------------------------------------- | ----- | ---------------------------- | ------- | ----------------------- | -------------- |
| Existing auth layout                    |       |                              |         |                         |                |
| Existing app shell / sidebar            |       |                              |         |                         |                |
| Existing topbar                         |       |                              |         |                         |                |
| Existing KPI cards                      |       |                              |         |                         |                |
| Existing tables / grids                 |       |                              |         |                         |                |
| Existing drawer / modal service         |       |                              |         |                         |                |
| Existing form field wrapper             |       |                              |         |                         |                |
| Existing toast service                  |       |                              |         |                         |                |
| Existing chart wrapper                  |       |                              |         |                         |                |
| Existing date-range picker              |       |                              |         |                         |                |
| Existing file upload                    |       |                              |         |                         |                |
| Existing copy button / clipboard util   |       |                              |         |                         |                |
| Existing secret / API-key input         |       |                              |         |                         |                |
| Existing empty / error state components |       |                              |         |                         |                |
| Existing skeleton loader                |       |                              |         |                         |                |

## API equivalence per migrated page

| Page | Service.method | Endpoint | Method | Payload fields | Query params | Notes                           |
| ---- | -------------- | -------- | ------ | -------------- | ------------ | ------------------------------- |
|      |                |          |        |                |              | Before/after must be identical. |

Fill this before opening a PR for each batch.
