# KhanaBook — API Reference

> Orientation layer over the API. The machine-readable contract is
> `api-docs.json` (OpenAPI) in this folder; this page explains the shape, the auth,
> and which family is which.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17.

## 1. Base URL and versioning

| Environment | Base |
|---|---|
| Production | `https://kbook.iadv.cloud/api/v1/` (Apache → `127.0.0.1:8081`) |
| Local dev | `http://localhost:8081/api/v1/` |
| Android default | `BuildConfig.BACKEND_URL` (`https://kbook.iadv.cloud/`), paths in `KhanaBookApi.kt` are prefixed `api/v1/...` |

All business endpoints are versioned under `/api/v1`. Android pins to the production
host, and release builds hard-fail unless `BACKEND_URL` is HTTPS on the expected host.

## 2. Authentication and headers

| Header | When | Notes |
|---|---|---|
| `Authorization: Bearer <JWT>` | all authenticated calls | added by `AuthInterceptor`; JWT carries tenant + role |
| `X-Terminal-Token` | sync calls from a device | terminal identity; **strict in production** (`terminal.sync.strict=true`) — a push without it is rejected |
| `X-Forwarded-For` | behind the proxy | honoured by the rate limiter |

Public (no token) endpoints, from `SecurityConfig`:

```
/auth/login, /auth/refresh, /auth/signup, /auth/signup/**, /auth/google,
/auth/check-user, /auth/reset-password, /auth/reset-password/request,
/auth/forgot-password/**,
/public/**  (public invoice),
/actuator/health, /actuator/health/**
```

`/actuator/**` beyond health requires `KBOOK_ADMIN`. In production the exposed
actuator endpoints are `health,prometheus,info`; Springdoc/Swagger are **disabled**
(`springdoc.api-docs.enabled=false`) — use `docs/api/api-docs.json` instead.

## 3. Authorization rules (URL layer)

| Rule | Roles |
|---|---|
| `/admin/**` | `KBOOK_ADMIN` |
| `/business/terminals/**`, `/business/terminal-requests/**` | `OWNER` |
| `/business/**` | `OWNER` |
| `/sync/terminal/**` | `OWNER`, `SHOP_STAFF` |
| `GET /sync/master/pull` | `OWNER`, `SHOP_STAFF`, `KBOOK_ADMIN` |
| `/sync/**` (rest) | `OWNER`, `SHOP_STAFF` (per-key enforcement inside the services) |
| `/restaurants/logo` | `OWNER`, `KBOOK_ADMIN` |
| `/menus/**` | `OWNER` |
| everything else | any authenticated caller (falls through) — this is why controllers carry their own `@RequireRole` / inline checks |

Fine-grained gates are layered on top: `@RequireRole` + `RequireRoleAspect`,
`PermissionService.hasPermission`, `SyncPushGuard`, `MenuPushAuthorizer`.

## 4. Endpoint families

| Base path | Purpose | Client |
|---|---|---|
| `/auth` | login, signup, Google, password reset + OTP flows | Android, web |
| `/sync/bills`, `/sync/bills/items`, `/sync/bills/payments` | bill push/pull | Android |
| `/sync/menuitem`, `/sync/menu/categories`, `/sync/itemvariant` | menu push/pull (+ availability toggle) | Android |
| `/sync/restaurantprofile`, `/sync/config/users` | profile/user push, counters, mobile update | Android |
| `/sync/master` | paged master pull (menu, users, profile, permissions) | Android, KBOOK_ADMIN |
| `/sync/terminal` | activate, request status, complete activation, list, reclaim, status | Android |
| `/sync/stocklog` | legacy stock ledger — no client (see `../product/PROBLEM.md` item 8) | — |
| `/business` | owner console: dashboard, orders, bills status/payment-mode, staff, terminals, refunds, KYC/onboarding/BSP docs | Web (OWNER) |
| `/business/menu` (+ `/categories`, `/{id}/variants`, `/{id}/favourite`) | menu CRUD, category CRUD, variants, favourites | Web, Android (favourite) |
| `/business/tax`, `/business/customers`, `/business/financing`, `/business/commerce`, `/business/onboarding` | **orphan** — implemented, no client | — |
| `/business/settlements`, `/business/refunds`, `/business/chargebacks`, `/business/lookup` | owner financial views | Web |
| `/analytics` | dashboard, trends, item-sales, hourly-sales, food-cost, daily-closing, gst-ledger | Web |
| `/inventory` | materials, recipes, purchase orders, vendors, movements, wastage, physical count | Web (OWNER on writes) |
| `/menus` | menu item image upload/delete, AI text extraction | Web |
| `/payments/easebuzz` | create link / link-for-bill / FSSAI order, status, verify, refund, refund-status, cancel, return, webhooks (payment, refund, sub-merchant, payout) | Android + Easebuzz |
| `/restaurants/payment-config/easebuzz` | gateway config read/write, onboarding, resubmit, KYC key, OTP | Android (onboarding), Web (config) |
| `/restaurants` | profile, logo | Web |
| `/permissions` | my permissions, grant/revoke, templates | Web, Android (pull) |
| `/notifications` | list, unread count, mark read, device tokens | Android, web |
| `/notifications/test` | internal push test — `@Profile("!prod")` + KBOOK_ADMIN only | — |
| `/admin/notifications` | platform push to a tenant | Web (KBOOK_ADMIN) |
| `/admin/sub-merchants`, `/admin/settlements`, `/admin/transactions`, `/admin/payment-metrics`, `/admin/payment-routing`, `/admin/webhooks`, `/admin/commission`, `/admin/reports`, `/admin/dashboard`, `/admin/businesses`, `/admin/feature-flags`, `/admin/developer`, `/admin/kyc-document/{restaurantId}` | platform console | Web (KBOOK_ADMIN) |
| `/public/invoice` | public invoice view | unauthenticated |

Android's Retrofit client defines **67** endpoint methods (`core/network/KhanaBookApi.kt`).

## 5. Sync endpoint shapes

- **Push** (`POST`): body is a JSON array of records, max **200** per request
  (`413` beyond that). Response:

  ```json
  {
    "successfulLocalIds": [1, 2],
    "failedLocalIds": [3],
    "localToServerIdMap": { "1": 9001, "2": 9002 }
  }
  ```

- **Pull** (`GET`): master pull is paged; the client merges pages and commits the sync
  checkpoint only after the whole cycle succeeds.
- Conflict responses are **409** and are per record; a conflict must not discard the
  batch's other acknowledgements.

See `../engineering/SYNC_CONTRACT.md` for the full contract.

## 6. Errors

Controller-level errors are handled in `GlobalExceptionHandler` and return a JSON map:

| Status | Body keys | Notes |
|---|---|---|
| 400 | `error`, `path` (+ `parameter` for type mismatches) | validation / bad input |
| 403 | `message`, `path` | missing permission (`ResponseStatusException`) |
| 404 | `message`, `path` | tenant-scoped lookups return 404 rather than 403 for other tenants' data |
| 409 | `error`/`message`, `path` | optimistic-concurrency conflict — the sync conflict signal |
| 429 | (sent via `sendError`) | rate limited; respects `X-Forwarded-For` |
| 500 | `error`, `errorId`, `path` | generic message; **`errorId` is the greppable key in server logs** |

Log correlation:

```bash
# find the stack trace for a 500 the client reported
docker compose --env-file .env -f ops/docker-compose.production.yml logs server | grep <errorId>
```

## 7. Conventions for new endpoints

1. Put it in the owning feature package; add `@RequireRole` unless the URL rule already
   expresses the role exactly.
2. Resolve tenant from `TenantContext` — never accept a `restaurantId` from a client
   body for a tenant-scoped read/write.
3. Return 404 (not 403) for another tenant's record.
4. Add the client wrapper: Android `KhanaBookApi` (+ repository), or web
   `business-api.service.ts` / `admin-api.service.ts`.
5. Keep Springdoc off in production; regenerate/refresh `api-docs.json` when the
   contract changes materially.

## 8. Related

- `../engineering/SYNC_CONTRACT.md` — push/pull rules, conflict model, offline authorization
- `../engineering/DATA_MODEL.md` — what each endpoint persists
- `../integrations/easebuzz/EASEBUZZ.md` — payment/webhook specifics and the money model
- `../product/ROLES_AND_PERMISSIONS.md` — permission keys behind the fine-grained gates
