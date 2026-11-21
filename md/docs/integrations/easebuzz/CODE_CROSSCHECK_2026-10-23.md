# Code ↔ Official Documentation Cross-check — 2026-10-23

**Method:** every claim in [`OFFICIAL_API_CATALOGUE.md`](./OFFICIAL_API_CATALOGUE.md)
was checked against the working tree, and every documented contract was compared
with the bytes the code actually puts on the wire. No claim below is inherited
from an earlier audit; each cites the file and line it rests on.

**Verdicts:** ✅ CONFIRMED · ❌ CONTRADICTED · ⚠️ UNVERIFIED (no source of truth in
the repo) · 🔧 CORRECTED IN THIS PASS (the catalogue said something the code does
not support).

Account context: merchant `280498`, UAT key `ADNX3KYX5`, docs captured 2026-10-22.

## Status of these findings

This file is the evidence snapshot of the audit. Two findings were fixed later the
same day, with contract tests; the text below still describes what was found.

| § | Finding | Status |
|---|---|---|
| 2.2 | EasyCollect create: host, path, encoding, `data` cast | **Fixed.** `POST {dashboard}/easycollect/v1/create`, JSON body, reads `data.payment_url`, refuses a success with no link. Tests: `EasebuzzRequestContractTest.createPaymentLink*` |
| 2.3 | Refund webhook rejected the documented `data` envelope | **Fixed for both shapes.** Envelope flattened; state read from `refund_status` when `status` is `"1"`. Tests: `EasebuzzWebhookTest.refundWebhook*Envelope*` |
| 1.12 | On-demand settlement posted a single amount to `/settlement/v1/on_demand` | **Fixed.** Documented batch path, one digest per transaction, callers now select bills. Tests: `EasebuzzRequestContractTest.onDemandSettlement*` |
| 1.13, 1.17, 1.18, 1.22 | Four calls that exist in no specification (payout transfer, sub-merchant OTP verify/resend, split label) | **Removed on 2026-10-23** — client methods, service methods, controller routes, web-admin stubs, the Android OTP step and all their tests are gone |
| 2.5 | Unhashed KYC approval accepted in sandbox | **Fixed.** The dev/sandbox bypass is gone: an unhashed callback is accepted only after a WIRE lookup, so the active profile no longer changes the answer. Tests: `EasebuzzWebhookServiceTest.unhashedKycApproval*`, `EasebuzzWebhookTest.testUnhashedMerchantKycApproval*` |
| 2.1, 2.4, 2.6 | Undocumented fallback, refund encoding, commission | **Open / unchanged** — each needs an Easebuzz answer or captured traffic |

Live UAT re-verification of the two fixes is still outstanding; the tests pin the
request and response contracts, not the gateway's acceptance.

---

## 1. Outbound operations

| # | Operation | Doc contract | Code | Verdict |
|---|---|---|---|---|
| 1 | Create sub-merchant | `POST {dashboard}/merchant/v1/submerchant/create/`, JSON, hash `key\|email\|phone` | `EasebuzzApiClient.java:552`, hash `:494`, JSON | ✅ |
| 2 | Update sub-merchant | same path, trailing slash, 5 bank fields, upsert | `:619`, `:611` hash, `:601-608` all five fields, `:619` trailing slash | ✅ |
| 3 | KYC access key | `POST /submerchant/v1/generate_kyc_access_key`, hash `merchant_key\|sub_merchant_id\|name\|email\|phone` | `:633`, hash `:627` | ✅ |
| 4 | Initiate payment | `POST {checkout}/payment/initiateLink`, form, `udf1..udf10`, `sub_merchant_id` | `:154` on `paymentBaseUrl` (default `pay.easebuzz.in`), form-encoded `:688`, hash `:117`, sub-merchant `:138` | ✅ |
| 5 | Capture | `POST {checkout}/payment/v2/auth/capture/` | — | ➖ absent |
| 6 | Reverse | `POST {checkout}/payment/v1/auth/reversal/` | — | ➖ absent |
| 7 | Status V2.1 | `POST /transaction/v2.1/retrieve`, hash `key\|txnid` | `:191`, hash `:187` | ✅ |
| 8 | Status fallback | **not in the published API** | `:192` `/transaction/v2/retrieve` | 🔧 see §2.1 |
| 9 | Refund V2 | `POST /transaction/v2/refund`, hash `key\|merchant_refund_id\|easebuzz_id\|refund_amount` | `:219`, hash `:207` | ✅ sequence; ⚠️ §2.4 encoding |
| 10 | Refund status | `POST /refund/v1/retrieve`, hash `key\|easebuzz_id` | `:243`, hash `:233` | ✅ sequence; ⚠️ §2.4 encoding |
| 11 | Payout report V2 | `POST /settlements/v1/retrieve`, hash `merchant_key\|start_date\|end_date` | `:400`, hash `:328` | ✅ |
| 12 | On-demand initiate | `POST /settlements/v1/ondemand/initiate/`, batch, per-txn hash | `:260` documented batch + per-txn hash | ✅ fixed 2026-10-23 |
| 13 | Payout transfer | no such PG operation | — removed 2026-10-23 | ✅ removed |
| 14 | EasyCollect create | `POST {dashboard}/easycollect/v1/create`, **JSON**, `data` is an object | `:452` `{pay}/easy_collect`, **form**, `:457` casts `data` to `String` | ❌ ×3 |
| 15 | EasyCollect get/list/send | 6 operations | — | ➖ absent |
| 16 | SmartPay get | `POST /smartpay/v1/get` | — | ➖ absent |
| 17 | Sub-merchant OTP verify | no such operation | — removed 2026-10-23 | ✅ removed |
| 18 | Sub-merchant OTP resend | no such operation | — removed 2026-10-23 | ✅ removed |
| 19 | Native OTP confirm/resend/cancel | `/otp/v1/*`, digest field `checksum` | — | ➖ absent |
| 20 | Split create | `POST /post-split/v1/create/` | `:665` | ✅ path, 🚫 disabled |
| 21 | Split retrieve | `POST /post-split/v1/retrieve/` | `:677` | ✅ path, 🚫 disabled |
| 22 | Split label | no such operation | — removed 2026-10-23 | ✅ removed |
| 23 | Transaction cancel | no such operation | `:268` returns a local failure, no HTTP call | ✅ correct |
| 24 | WIRE auth | `Authorization: SHA512(key\|salt)` + `WIRE-API-KEY` | `EasebuzzWireApiClient.java:72-73`, hash `:63-64` | ✅ mechanism; ⚠️ §2.5 sequence |

**Call sites confirmed reachable.** `createPaymentLink` is called from
`EasebuzzPaymentService.java:478`, which serves `PaymentController.java:82`
(`/create-link`) and `:111` (`/create-link-for-bill`). The broken EasyCollect
paths are not dead code behind a flag — they are live endpoints the POS calls.

---

## 2. Findings from this pass

These are **new or corrected** relative to the earlier audit; the rest of that
audit's P0 list is reproduced in §4 and stands.

### 2.1 The `/transaction/v2/retrieve` fallback is deliberate, not a bug — CORRECTED

The catalogue listed this as a call that cannot succeed. The code says otherwise
and explains itself: `postFormWithFallback` (`EasebuzzApiClient.java:731-770`)
advances to the alternate route **only on 404/405**, and the comment at `:729-737`
records that Easebuzz's own v2.0 integration kit (`easebuzz-lib/transaction.php`)
posts to `/transaction/v2/retrieve` while the docs site publishes
`/transaction/v2.1/retrieve`. Both are tried; the log records which answered.

The published export contains only `/transaction/v1/retrieve` and
`/transaction/v2.1/retrieve` — so the fallback is undocumented, but it is a
**deliberate** resolution of a first-party contradiction, not an invented route.
Downgrade to ⚠️: keep it, and confirm with Easebuzz which path is canonical. Do not
"fix" it by deleting the fallback.

### 2.2 EasyCollect create also sends the wrong content type — NEW

`POST /easycollect/v1/create` is documented `application/json` only — the export
declares 25 JSON request bodies and zero form ones (`Easy-Collect-APIs.yaml`).
The client posts it with `post(...)` (`:452`), which sets
`APPLICATION_FORM_URLENCODED` (`:692-693`).

So there are **three** independent defects on this one call, any of which alone
breaks it: host (`pay` instead of `dashboard`), path (`/easy_collect` instead of
`/easycollect/v1/create`), and encoding (form instead of JSON). The fourth defect,
the `data` cast at `:457`, would then throw on the corrected response. The UAT
proof that `sub_merchant_id` is mandatory used a JSON body, so the fix must be a
full rewrite of this method, not a path edit.

### 2.3 The refund webhook does not accept the documented envelope — NEW, UNVERIFIED AT RUNTIME

Documented wire format (`official/articles/webhook-refund.md:52`):

```
status=1&data=<urlencoded JSON containing hash, txnid, easepayid,
                       refund_id, refund_status, refund_amount, ...>
```

The code (`PaymentController.java:301-310`) passes the raw `@RequestParam` map
straight to `handleRefundWebhook`, which reads a **top-level** `hash`,
`easepayid` and `status` (`EasebuzzWebhookService.java:278-300`). Against the
documented envelope:

- `hash` is absent at the top level → `verifyRefundWebhookHash` returns false → **401**;
- even after unwrapping `data`, the state field is `refund_status`, not `status`,
  which is `1` — the handler would read `1` as the refund state.

Compare the two handlers that *do* unwrap: payout (`PaymentController.java:358-372`)
and KYC (`:321-336`) both parse `data` into a nested map before dispatch. Refund does
not, and payment correctly does not need to (its documented payload is flat —
`official/articles/webhook-transaction.md:22-70`).

**Why this is ⚠️ and not ❌:** no captured traffic in this repo shows what Easebuzz
actually POSTs for a refund on this account. The unit tests
(`EasebuzzWebhookTest.java:345-353`) build a flat payload, so they cannot catch
this. Two possibilities, and they need different fixes:

1. Easebuzz delivers flat refund callbacks here → code is fine, docs are wrong;
2. Easebuzz delivers the documented envelope → every refund callback is rejected
   with 401 and refunds never reach a terminal local state.

**Action before coding:** capture one real refund callback (the ₹1 UPI refund in
`PRODUCTION_VALIDATION_RUNBOOK.md` produces one) and compare. If it is the
envelope, the fix is to unwrap `data` in the form handler **and** read
`refund_status` as the state, mirroring the payout handler.

### 2.4 Refund requests are sent form-encoded; the docs say JSON — UNVERIFIED

`Refund-APIs.yaml` declares `application/json` only (6 request bodies, 0 form).
The client posts both refund calls form-encoded (`EasebuzzApiClient.java:219`,
`:243`), justified in-code by the official PHP kit
(`easebuzz-lib/refund.php` / `refund_status.php`) and by
`EASEBUZZ_REFUND_STATUS_CONTRACT_2026-09-23.md`.

The UAT refund proof that established the response shape (a full ₹1 refund
returning `refund_id` + `"queued"`) was made with **JSON**, not form. So the
documented encoding is the one with evidence; the shipped encoding has a
first-party kit as evidence but no captured response from this account.

**Action:** either switch to JSON (matches docs + existing UAT proof) or record one
successful form-encoded refund. Do not assume both work.

### 2.5 The sub-merchant webhook can be accepted without a hash — security-relevant

`verifySubMerchantWebhookHash` (`EasebuzzWebhookService.java:685-756`) verifies
`sha512(key|submerchant_id|salt)` **when a hash is present**. When it is absent it
falls through to a second path for `MERCHANT_KYC_APPROVAL`:

1. dev/sandbox profile → accepted with a `SECURITY_AUDIT` warning (`:727-733`);
2. production → accepted only if a server-to-server WIRE lookup succeeds
   (`:734-745`), otherwise rejected (`:747`).

That is a defensible design given Easebuzz's unhashed KYC callbacks, but it means
in **sandbox** an unauthenticated POST with `event=MERCHANT_KYC_APPROVAL` and a
known sub-merchant id could activate a row with no proof at all. Production is
gated on WIRE — and WIRE is disabled on this account (`wire_allowed: false`), so on
this merchant path (2) cannot succeed either.

**Fixed 2026-10-23:** the dev/sandbox branch is removed, so an unhashed callback is
rejected in every profile unless WIRE confirms it. Sandbox activation is now an
explicit admin action (`PUT /admin/sub-merchants/{id}/status`) rather than a side
effect of an unsigned POST. The documented callback carries a hash
(`official/articles/webhook-kyc-approval.md`), so this costs nothing when the
callback is genuine.

### 2.6 Commission is pinned to zero in the schema — CONFIRMED, but it is a different fact

`V102__disable_khanabook_transaction_commission.sql:13` adds
`CHECK (commission_rate = 0)` on `easebuzz_sub_merchant`, after normalising rows to
0 (`:5`). So KhanaBook's own commission column cannot hold a non-zero value, by
design.

That is **not** the same claim as "Easebuzz's dashboard shows 0.0 MDR on both live
twins". The latter is an observation about merchant `280498` recorded in the UAT
verification block of `official/manifest.json`, and cannot be re-derived from this
repo. The catalogue now separates the two.

### 2.7 Everything else in §1 of the catalogue checked out

Confirmed as written, with the cited lines: the checkout-host requirement
(`application.properties:131` defaults to `pay.easebuzz.in`), hash assembly
including empty slots (`generateHash` at `EasebuzzApiClient.java:858-876` maps null
to `""`), sub-merchant id opacity, `kyc_dashboard_url: "NA"`, the `kyc_details`
omission guard (`EasebuzzRequestContractTest`), settlement pagination
(`:322-400`, `truncated` flag), access-key shape validation
(`^[a-f0-9]{64}$`, `:163`), and `txnid` truncation to 40 chars (`:74-77`).

---

## 3. Webhook contracts, documented vs implemented

| Webhook | Documented envelope | Code | Hash verified | Verdict |
|---|---|---|---|---|
| Transaction | **flat** form fields (`key=…&status=success&txnid=…&hash=…`) | `PaymentController:280` → `handlePaymentWebhook` | `salt\|status\|udf10..udf1\|email\|firstname\|productinfo\|amount\|txnid\|key` (`:578-612`) | ✅ |
| Refund | `status=1&data=<json>` | `:300` → `handleRefundWebhook` | `key\|easepayid\|salt` (`:329-350`), accepts `easebuzz_id` alias | ⚠️ §2.3 |
| Payout | `status=1&data=<json>` | `:357-372` unwraps `data` | 3 accepted shapes, incl. documented `key\|payout_id\|salt` (`:616-683`) | ✅ |
| Sub-merchant KYC | `status=1&data=<json>` | `:320-336` unwraps `data` | `key\|submerchant_id\|salt`, plus the unhashed path in §2.5 | ✅ gated / ⚠️ §2.5 |

Also confirmed as documented:

- **Verify before duplicate-guard**, with the reason recorded in code
  (`EasebuzzWebhookService.java:53-61`): checking duplicates first leaked which
  `(txnid, status)` pairs exist to an unauthenticated caller.
- **De-duplication** on `(txnId, status)` at `:66-74`, plus a `paid` short-circuit
  on the bill itself.
- **Constant-time compare** on every digest (`MessageDigest.isEqual`).
- **Amount and identity checks** before a bill is touched — `resolveBillFromPayload`
  (txnid → bill, `udf1` = bill id, `udf2` = restaurant id) and
  `paymentAmountMatchesBill` (exact `compareTo`) both deny on mismatch.
- **401 on mismatch** at every controller entry point.
- **Retry ladder** for processing failures (`webhookRetryService.enqueue`), separate
  from Easebuzz's own 30 min × 5 delivery retries.

**One nuance:** the payment JSON endpoint also routes `event=MERCHANT_KYC_APPROVAL`
to the sub-merchant handler (`PaymentController.java:262-268`). That means the
generic `/webhook` URL is a valid KYC callback receiver — worth knowing when
reading dashboard configuration, and it is why the KYC webhook may appear
registered twice.

---

## 4. Still standing from the earlier audit

Reproduced because this pass re-confirmed each one at the cited line:

| Defect | Line | Status |
|---|---|---|
| EasyCollect host + path + encoding + `data` cast | `:452`, `:457` | ❌ open |
| On-demand settlement path + batch shape + per-txn hash | `:260` | ✅ fixed 2026-10-23 |
| Payout transfer (wrong product, no such PG endpoint) | `:283` | ✅ removed 2026-10-23 |
| Sub-merchant OTP verify / resend (no such operation) | `:254`, `:264` | ✅ removed 2026-10-23 |
| Split label creation (no such path; product disabled) | `:650` | ✅ removed 2026-10-23 |
| WIRE auth sequence undocumented for every GET it uses | `EasebuzzWireApiClient:63-64` | ⚠️ moot while WIRE is off |

---

## 5. Corrections applied to the catalogue

| Catalogue claim | Was | Now |
|---|---|---|
| `/transaction/v2/retrieve` | "does not exist … the fallback can never succeed" | Undocumented but deliberate: the first-party kit posts it, and the code only falls back on 404/405 |
| EasyCollect defects | host + path + response cast | host + path + **encoding** + response cast |
| Webhook envelope | "all … body `status=1&data=<urlencoded JSON>`" | Transaction webhook is flat; refund/payout/KYC use the `data` envelope |
| Commission | "the commission block … all zero on both live twins" | Split into: DB `CHECK (commission_rate = 0)` (code fact, V102) and the dashboard MDR observation (UAT record, not code) |
| Fix order step 2 | "drop the non-existent fallback" | Confirm the canonical status path with Easebuzz; keep the fallback until then |

---

## 6. What cannot be verified from this repo

1. **Whether Easebuzz delivers refund callbacks flat or enveloped** (§2.3) — needs
   one captured callback.
2. **Whether form-encoded refunds are accepted** (§2.4) — needs one captured
   response, or switch to the documented JSON.
3. **Which status path is canonical** (§2.1) — needs an Easebuzz answer.
4. **The live dashboard's MDR/commission values** — needs dashboard access or an
   RM statement.
5. **`upload_kyc_documents`** — used historically in onboarding, present in no
   captured spec (`grep` across all 17 exports returns zero hits). Still the
   single unverified dependency in the onboarding path.
6. **WIRE hash sequences for GET endpoints** — undocumented in the vendored
   project.

Items 1–3 are cheap to close and each one decides a code change; items 4–6 are
questions for the RM, already listed in the catalogue's §9.
