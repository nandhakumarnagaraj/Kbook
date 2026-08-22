# Easebuzz Production Validation Runbook (₹1 Live Test)

> **Purpose:** Validate the full live-mode payment lifecycle with minimal real-money exposure.
> Complements `docs/easebuzz/easebuzz-live-test-plan.md` (which covers sub-merchant/KYC onboarding).
> Scope: Payment Link (Easy Collect) → webhook → Transaction Status API → refund → refund webhook → daily reconciliation.
>
> **Rule:** Every real transaction in this runbook is ₹1.00 and MUST be refunded in step 8.

---

## 0. Preconditions

| # | Check | How |
|---|---|---|
| 1 | Live keys configured as env vars — never in files | `EASEBUZZ_MERCHANT_KEY`, `EASEBUZZ_SALT` set on prod host; `application-prod.properties` contains no literals |
| 2 | Live base URLs active (defaults) | `EASEBUZZ_PAYMENT_BASE_URL=https://pay.easebuzz.in`, `EASEBUZZ_DASHBOARD_BASE_URL=https://dashboard.easebuzz.in` |
| 3 | Webhook URL reachable & HTTPS | `EASEBUZZ_NOTIFY_URL` / `EASEBUZZ_WEBHOOK_URL` point at `https://<prod-host>/api/v2/payments/easebuzz/webhook`; same URL registered in Easebuzz dashboard |
| 4 | Sandbox gate off in prod | `EasebuzzApiClient` test URL is DEBUG-build-gated; confirm prod build is release |
| 5 | Health green | `GET https://<prod-host>/api/v1/actuator/health` → `UP` |
| 6 | Reconciliation enabled for the test window | `EASEBUZZ_RECONCILIATION_ENABLED=true` (cron 06:00 IST) |
| 7 | Test bill exists | Create a bill from the Android POS for ₹1.00 |

**Security note:** `application-dev.properties` currently hardcodes sandbox key/salt (`ADNX3KYX5`/`Z4UFP4939`). See `docs/SECURITY_ROTATION_REQUIRED.md` — rotate before launch; sandbox creds must not be reused in prod.

---

## 1. Initiate — Payment Link for Bill (Easy Collect)

From the Android POS: New Bill → Payment → **Payment Link**, or via API:

```
POST /api/v1/payments/easebuzz/create-link-for-bill
{ "billId": <id> }
```

Expected:
- [ ] HTTP 200, response contains Easebuzz short URL
- [ ] `bill.gatewayTxnId` == `merchant_txn` returned by initiate call
- [ ] `bill.gatewayStatus` = `pending`
- [ ] Server log shows initiate POST to `https://pay.easebuzz.in/payment/initiateLink` (NOT testpay)

## 2. Pay ₹1 (real UPI on a real device)

- [ ] Open the link on a phone; pay exactly ₹1.00 via UPI
- [ ] Easebuzz hosted page shows success
- [ ] Note `txnid` and `easepayid` from the return URL / dashboard

## 3. Return-page handling (redirect)

- [ ] Browser lands on the configured `return-url`; app/bill screen reflects success
- ⚠️ Redirect is UX only — do NOT treat as confirmation (step 4 is source of truth)

## 4. Webhook — server-side verification (source of truth)

Check server logs for `Payment webhook received txnid=... status=success`:

- [ ] Reverse hash verified (a `hash_mismatch` log = STOP, investigate)
- [ ] Replay guard works: duplicate delivery logged as `already processed`, no double side-effects
- [ ] Bill updated: `gatewayStatus=success`, marked paid, `udf1`-resolved `billId` matched correctly
- [ ] `notification_events` row created (payment_received push fired to restaurant devices)

## 5. Transaction Status API (independent confirmation)

Hash: `SHA512(key|txnid|salt)` against:

```
POST https://dashboard.easebuzz.in/transaction/v2.1/retrieve   (via internal verify endpoint)
POST /api/v1/payments/easebuzz/verify/{billId}
```

- [ ] Response `status=success`, amount == ₹1.00, `easepayid` matches step 2

## 6. Refund ₹1

```
POST /api/v1/payments/easebuzz/refund/{billId}
```

Internally calls `transaction/v2/refund` with hash `key|merchant_refund_id|easebuzz_id|refund_amount|salt`.

- [ ] Refund initiated; `merchant_refund_id` stored (format `REF{billId}_{timestamp}`)
- [ ] Customer sees ₹1 credit in UPI/bank within T+0..T+3 days

## 7. Refund webhook

- [ ] Log shows `Refund webhook received ... status=refunded` (or similar), reverse-hash OK
- [ ] Bill refund fields updated (`updateBillRefund`); idempotent on redelivery

## 8. Reconciliation

Force or wait for cron (`0 0 6 * * * Asia/Kolkata`) / call `reconcileDate(<today>)`:

- [ ] The ₹1 txn appears settled and matches internal record; no unmatched/orphan rows
- [ ] Refund reflected in settlement data

## 9. Failure-path spot check (optional but recommended)

- [ ] Create a second ₹1 link, abandon at UPI page → after timeout, webhook `failure`/`userCancelled` path marks bill `gateway_status` accordingly without marking paid

---

## Abort / rollback criteria

STOP immediately if any of:
- `hash_mismatch` in any webhook (possible salt mismatch or tampering)
- Any non-₹1 amount observed
- Webhook never arrives within 10 min of successful payment (check public reachability + Easebuzz dashboard webhook config)
- Duplicate bill-paid side effects on webhook replay

## Evidence capture (attach to this doc)

1. Screenshot: Easebuzz dashboard txn detail (₹1, success, refunded)
2. Server log excerpt: webhook received + hash verified lines
3. DB row dumps: `bills` (gateway_txn_id, gateway_status), relevant `webhook_events`
4. Refund confirmation screenshot

| Field | Value |
|---|---|
| Executed by | ______ |
| Date | ______ |
| Result | PASS / FAIL |
