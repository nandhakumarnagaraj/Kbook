# KhanaBook Load & Correctness Tests

Tailored to the REAL API surface (context-path `/api/v1`). Replaces the generic
`/api/bills`-style plan — KhanaBook is offline-first: bills are created on-device
and pushed through sync, not created server-side.

## Endpoint map (verified against source)

| Flow | Endpoint | Auth |
|---|---|---|
| QR menu | `GET /api/v1/public/restaurants/{rid}/menu` | none |
| QR order | `POST /api/v1/public/restaurants/{rid}/orders` | none (rate-limited 20/IP/10min) |
| QR self-pay | `POST /api/v1/public/restaurants/{rid}/orders/{oid}/pay` | none |
| Sync pull (master) | `GET /api/v1/sync/master/pull?lastSyncTimestamp=...&...` | JWT (+ X-Terminal-Token for transactional data) |
| Bill push | `POST /api/v1/sync/bills/push` | JWT + X-Terminal-Token |
| Master push | `POST /api/v1/sync/master/push` (per-type controllers under `/sync/*/push`) | JWT |
| Terminal activate | `POST /api/v1/sync/terminal/activate` | JWT |
| Easebuzz create-order | `POST /api/v1/payments/easebuzz/create-order` | JWT (tenant-checked) |
| Easebuzz link-for-bill | `POST /api/v1/payments/easebuzz/create-link-for-bill` | JWT |
| Payment webhook | `POST /api/v1/payments/easebuzz/webhook` | hash-verified (sandbox profile only) |
| Refund | `POST /api/v1/payments/easebuzz/refund/{billId}` | JWT (tenant-checked, eligibility-checked) |
| Analytics | `GET /api/v1/analytics/item-sales|hourly-sales|food-cost` | JWT |

## Files

- `qr-order-load.js` — k6, runnable as-is against any staging URL (no auth).
- `sync-push-load.js` — k6 skeleton for bill push bursts (fill tokens/ids).
- `curl-timing.sh` — P50/P95 sampler for any endpoint.
- `regression-checks.md` — curl checklist covering the 7 review fixes.

## Targets (baseline)

- Sync bill push P95 ≤ 600 ms (batch of 10)
- QR order create P95 ≤ 400 ms
- Error rate < 1% 5xx
- Rate limit: >20 orders/IP/10min → 429

## Known caveats discovered while mapping

1. Rate limiter trusts first `X-Forwarded-For` hop — rotating the header in k6
   bypasses the cap (this IS review finding #12; don't run header rotation in
   shared staging without expecting unlimited order creation).
2. In-memory QR `localId` counters reset on restart — after a server restart,
   collisions with persisted `(QR_ORDER, localId)` rows can 500 order creation
   until the counter passes old values (review finding #12b).
3. KOT cannot be load-tested server-side (device-local). Test on-device manually.
