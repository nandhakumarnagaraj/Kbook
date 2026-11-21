# Easebuzz Official API Catalogue — Every Operation KhanaBook Needs

**What this is:** the collected, verified set of Easebuzz operations for the
KhanaBook sub-merchant (aggregator) product, each with its exact endpoint, host,
hash sequence, request/response contract, and **how KhanaBook uses it**.
**Collected:** 2026-10-22 · **Account ground truth:** merchant `280498`, UAT key `ADNX3KYX5`.
**Raw sources:** [`official/`](./official) — 17 OpenAPI exports + 32 guide pages + `manifest.json`.
**Checked against the code:** [`CODE_CROSSCHECK_2026-10-23.md`](./CODE_CROSSCHECK_2026-10-23.md) — every
operation below verified against the working tree with file:line evidence.

This page is the interpretation layer. Where it and the raw exports disagree, the
export wins and this page has a bug; where either disagrees with what the live UAT
gateway actually answered, **UAT wins** — every such case is marked.

**Authority order when sources conflict**

1. A request that was actually executed and answered (UAT, 2026-10-22).
2. `official/openapi/*` — the exported OpenAPI document.
3. `official/articles/*` — the guide pages.
4.   `sub-merchant-apis.md` and older transcripts in this folder — **these contained
   invented endpoints** (annotated in place and removed from the code on
   2026-10-23); see §8.2.

---

## 1. The two backends

| Backend | Production | UAT | Auth | Product |
|---|---|---|---|---|
| Dashboard / reports | `https://dashboard.easebuzz.in` | `https://testdashboard.easebuzz.in` | SHA-512 `hash` (or `checksum`) in the body | PG onboarding, refunds, links, status, settlements |
| Checkout | `https://pay.easebuzz.in` | `https://testpay.easebuzz.in` | same | `initiateLink`, `capture`, `reverse` |
| WIRE / neobanking | `https://wire.easebuzz.in` | `https://testwire.easebuzz.in` | SHA-512 in `Authorization` header + `WIRE-API-KEY` | Payout transfers (off for this account) |

The **checkout host is not optional**. `POST /payment/initiateLink` against the
dashboard host returns nginx **405** — verified on UAT. The same four operations
listed as `{checkout}` below are served there and nowhere else.

`easebuzz.payment-base-url` already defaults to `pay.easebuzz.in` in
`application.properties`, so the checkout calls in this repo are on the right host.
The failures are paths, not hosts (§8).

---

## 2. Authentication: hashing rules that decide success

```
hash = SHA512( "field1|field2|…|fieldN" + "|" + salt )   → lowercase hex
```

Rules that broke integrations before and are easy to get wrong:

1. **Every value is trimmed** before it is joined.
2. **An empty optional field still occupies its slot.** Leaving `merchant_email`
   out of the date-range hash yields `key||start_date|end_date|salt` — two pipes,
   not one.
3. **The hash is computed from exactly the strings you send.** Build the body
   first, then hash the body's values; never hash the DTO and then normalise the
   body.
4. **The salt never leaves the server.**
5. Some operations name the digest field **`checksum`, not `hash`**: capture,
   reversal, and all three Native-OTP calls. Sending `hash` there is a silent
   failure.
6. `udf8`, `udf9`, `udf10` **must be empty** on `payment/initiateLink`, yet they
   stay in the digest.

**Token alternative.** `POST /auth/v1/token` (`Token-Authentication-API.yaml`)
issues a bearer token that can replace the body hash on EasyCollect endpoints.
KhanaBook does not use it; noted because EasyCollect's schema requires exactly
one of `hash` or `token`.

**WIRE.** `Authorization: <hex digest>` (no `Bearer` prefix) plus
`WIRE-API-KEY: <merchant key>`. Disabled on this merchant.

---

## 3. Machine-readable twin

[`official/manifest.json`](./official/manifest.json) holds the same 31 operations
plus the 4 webhooks with: `path`, `base`, `hash[]`, `required[]`, `idempotencyKey`,
`enabled`, and the `uatVerification` block. During coding, read it rather than the
tables below — it is the one artifact a script can consume.

---

## 4. Operation catalogue

Legend for the **Code** column: ✅ correct · ⚠️ works, wrong semantics ·
❌ calls a path that is not in the published API · ➖ not implemented ·
🚫 blocked by account configuration.

### 4.1 Sub-merchant onboarding — 3 operations

This is not one call. It is: **create → (bank/business/charges) → hosted KYC
portal → ops review → webhook**. There is **no polling endpoint** and no
delete/deactivate operation.

| # | Operation | Endpoint | Hash | Idempotency | Code |
|---|---|---|---|---|---|
| 1 | Create sub-merchant | `POST {dashboard}/merchant/v1/submerchant/create/` | `key\|submerchant_email\|submerchant_phone` | email | ✅ |
| 2 | Update sub-merchant | `POST {dashboard}/merchant/v1/submerchant/create/` | same | `sub_merchant_id` | ✅ |
| 3 | Generate KYC access key | `POST {dashboard}/submerchant/v1/generate_kyc_access_key` | `merchant_key\|sub_merchant_id\|name\|email\|phone` | none | ✅ |

**How KhanaBook uses it.** `SubMerchantService.submitToEasebuzz()` creates one
twin per restaurant; `updateSubMerchant()` backs the owner's "resubmit KYC" action;
`generateKycAccessKey()` is the refresh path when the create response carried no
portal link. The row then waits for `MERCHANT_KYC_APPROVAL` (§5) to become
`ACTIVE`.

**Contracts and traps**

- The trailing slash is **mandatory** on `/create/`. Without it nginx answers
  **301** and nothing changes — including on update, which is an **upsert on the
  create path**, not a separate route. The export also contains a slash-less
  `/merchant/v1/submerchant/create` entry, which is why this keeps biting.
- Update must resend **all five bank fields**
  (`name_in_bank`, `account_number`, `bank_name`, `branch_name`, `ifsc_code`)
  even for a name-only change; UAT rejects it otherwise.
- Create body is three documented sections — `merchant_details`,
  `submerchant_details` (10 fields), and optionally `business_details`, plus
  `submerchant_deduction_percentage`. **`kyc_details` is not in the JSON Schema**;
  it appears only in `x-examples`. KhanaBook deliberately does not send it, and
  `EasebuzzRequestContractTest` fails the build if it is added.
- `submerchant_id` comes back as `S<parent merchant id><4 chars>` (e.g.
  `S280498CAWF`). KhanaBook never generates it.
- `kyc_details.kyc_dashboard_url` is the literal string `"NA"` on UAT, not a URL.
  Do not parse it as a link; check `startsWith("http")`.
- The commission block (`submerchant_deduction_percentage`) is where margin lives.
  Two separate facts, often conflated: KhanaBook's own column cannot hold a
  non-zero value — `V102__disable_khanabook_transaction_commission.sql:13` adds
  `CHECK (commission_rate = 0)` — and the dashboard showed `0.0` for every
  instrument on both live twins when the account was inspected. Treat as a
  commercial decision, not a code path.

### 4.2 Collection — 4 operations, all on the checkout host

| # | Operation | Endpoint | Hash | Idempotency | Code |
|---|---|---|---|---|---|
| 4 | Initiate hosted payment | `POST {checkout}/payment/initiateLink` | `key\|txnid\|amount\|productinfo\|firstname\|email\|udf1\|udf2\|udf3\|udf4\|udf5\|udf6\|udf7\|udf8\|udf9\|udf10` | `txnid` | ✅ |
| 5 | Initiate auth & capture | `POST {checkout}/payment/initiateLink` | `key\|txnid\|amount\|productinfo\|firstname\|email\|udf1..udf10` + `payment_category=NCAP` | `txnid` | ➖ |
| 6 | Capture | `POST {checkout}/payment/v2/auth/capture/` | `merchant_key\|amount\|txnid` → **`checksum`** | `txnid` | ➖ |
| 7 | Void / reverse | `POST {checkout}/payment/v1/auth/reversal/` | `merchant_key\|txnid` → **`checksum`** | `txnid` | ➖ |

**How KhanaBook uses it.** `initiatePayment()` creates the hosted checkout link
the POS opens after a bill is finalised. `sub_merchant_id` travels with the request
so the money is attributed to the restaurant twin; `udf1=billId`,
`udf2=restaurantId` are the local join keys.

**Contract**

```json
POST {checkout}/payment/initiateLink        (form-encoded)
key, txnid, amount, productinfo, firstname, email, phone, surl, furl,
udf1..udf7, udf8="", udf9="", udf10="", hash,
sub_merchant_id (required on a sub-aggregator account)

→ {"status": 1, "data": "<access_key>"}
customer URL = {checkout}/pay/<access_key>
```

**Traps**

- `status` on initiate is the **number** `1`, not `true`. Compare loosely.
- The `surl`/`furl` redirect is a **browser signal**, not proof of payment. Only a
  verified webhook or a status-API confirmation may mark a bill paid.
- `access_key` expires; an abandoned checkout becomes `Bounced`, not failed.
- Auth & capture exists for bar tabs / deposits. KhanaBook does not use it today;
  the docs mark `payment_category` as required there, unlike the standard flow.
- **There is no `/transaction/*/cancel` operation.** `cancelTransaction()` in this
  repo correctly returns a local failure instead of calling one. `/otp/v1/cancel`
  (§4.7) cancels a *native-OTP payment*, not a hosted checkout.

### 4.3 Status and listing — 4 operations

| # | Operation | Endpoint | Hash | Code |
|---|---|---|---|---|
| 8 | Status V2.1 | `POST {dashboard}/transaction/v2.1/retrieve` | `key\|txnid` | ✅ |
| 9 | Status V1 | `POST {dashboard}/transaction/v1/retrieve` | `key\|txnid\|amount\|email\|phone` | ➖ |
| 10 | By date | `POST {dashboard}/transaction/v1/retrieve/date` | `merchant_key\|merchant_email\|transaction_date` | ➖ |
| 11 | By date range | `POST {dashboard}/transaction/v2/retrieve/date` | `key\|merchant_email\|start_date\|end_date` | ➖ |

**How KhanaBook uses it.** #8 is the reconciliation fallback when a webhook never
arrives; the payment sweep re-queries unconfirmed links every 2 minutes.
#11 is the missing primitive for daily gateway-vs-local matching: one range call
replaces N per-transaction lookups. #9 is legacy — use #8. #10 is subsumed by #11.

**The `/transaction/v2/retrieve` fallback is undocumented but deliberate.**
`getTransactionStatus()` tries `/transaction/v2.1/retrieve` first and falls back to
`/transaction/v2/retrieve` **only on 404/405**, because Easebuzz's own v2.0
integration kit posts the latter while the docs publish the former. Keep it. The
open question is which path Easebuzz considers canonical.

**Response shape (UAT-verified)**

```json
{"status": true, "msg": [{"txnid": "...", "easepayid": "S261022...", "status": "success", ...}]}
```

Rows live under **`msg[]`**, not `data`. UAT returns lowercase `"success"`
although the reference writes `Success` — compare case-insensitively. `easepayid`
may start with `S` even though the reference shows `E`.

Date formats: **`dd-mm-yyyy`** everywhere except EasyCollect list (§4.6).

### 4.4 Refunds — 2 operations worth using

| # | Operation | Endpoint | Hash | Idempotency | Code |
|---|---|---|---|---|---|
| 12 | Refund V2 | `POST {dashboard}/transaction/v2/refund` | `key\|merchant_refund_id\|easebuzz_id\|refund_amount` | `merchant_refund_id` | ✅ |
| 13 | Refund status | `POST {dashboard}/refund/v1/retrieve` | `key\|easebuzz_id` | — | ✅ |
| 12a | Refund V1 (legacy) | `POST {dashboard}/transaction/v1/refund` | different, weaker sequence | — | ➖ never use |

**How KhanaBook uses it.** POS-initiated refunds (`POST
/payments/easebuzz/refund/{billId}`) create the V2 refund, then poll #13 for the
state. Refund value comes out of the **parent** settlement; there is no automatic
split reversal.

**Contract (UAT-verified with a full ₹1 refund)**

```json
POST {dashboard}/transaction/v2/refund        (form-encoded)
→ {"status": true, "message": "Refund initiated, Your Request Id:RP3Q5YLQZ3",
   "easebuzz_id": "S261022076AWEF", "refund_id": "RP3Q5YLQZ3",
   "refund_amount": 1, "merchant_refund_id": "KBREF..."}
```

**Traps**

- A successful create means **queued**, not refunded. `refund_status` walks
  `queued → accepted|approved → refunded`.
- Refunds are **balance-gated**: Easebuzz forwards only if it can hold funds from a
  future payout. A merchant that stops transacting has refunds sitting in
  `Queued` forever. That is a policy problem; retrying makes it worse.
- #13 takes `easebuzz_id`, **not** `txnid`. They are different identifiers.
- `refund_amount` is documented as integer but is sent as a float-cast value.

### 4.5 Settlement and payout reporting — 4 operations

| # | Operation | Endpoint | Hash | Code |
|---|---|---|---|---|
| 14 | Payout report V1 | `POST {dashboard}/payout/v1/retrieve` | `key\|merchant_email\|payout_date` | ➖ |
| 15 | Payout report V2 | `POST {dashboard}/settlements/v1/retrieve` | `merchant_key\|start_date\|end_date` | ✅ |
| 16 | On-demand initiate | `POST {dashboard}/settlements/v1/ondemand/initiate/` | **per-transaction**: `key\|easebuzz_id\|merchant_txn_id\|amount` | ✅ fixed 2026-10-23 (no live UAT) |
| 17 | On-demand status | `POST {dashboard}/settlements/v1/ondemand/status/` | `key\|merchant_email\|merchant_batch_id` | ➖ |

**How KhanaBook uses it.** #15 is the settlement reconciler: `pending`/`settled`
rows are matched against local bills, and `splitOrphan` / `splitMismatch` flags are
raised when the numbers disagree. `retrieveSettlements()` already paginates via the
`next` cursor, which matters because a busy restaurant settles more than one page a
day and a truncated fetch reads as "no payout".

**Contract notes**

- #15 dates are `DD-MM-YYYY`; body uses `merchant_key` (not `key`) with
  `merchant_email`, `payout_date.{start_date,end_date}`, optional
  `submerchant_id`, plus echoed pagination (`page`, `next`).
- **There is no payout transfer operation in the Payment Gateway API.** `payout`
  is report-only. Moving money to an arbitrary account is WIRE.
- #16 is **batch-shaped with no top-level hash**:

```json
{"key": "...", "merchant_email": "...", "merchant_batch_id": "...",
 "transactions": [{"easebuzz_id": "...", "merchant_txn_id": "...", "amount": "12345.0",
                   "hash": "sha512(key|easebuzz_id|merchant_txn_id|amount|salt)"}]}
```

  A single transaction must be wrapped in a one-element array. `status: true` can
  still contain per-transaction failures.

- Implemented this way since 2026-10-23 (`EasebuzzApiClient:260`): `merchant_batch_id`
  is the idempotency key, `merchant_email` comes from `easebuzz.merchant-email`, and
  `InstantSettlementService` builds the list from the paid bills an admin selects
  (`POST /business/settlements/instant` with `billIds`). Not yet exercised against
  the gateway — the contract is pinned by `EasebuzzRequestContractTest`.

### 4.6 Payment links (EasyCollect + SmartPay) — 8 operations

| # | Operation | Endpoint | Hash | Code |
|---|---|---|---|---|
| 18 | Create / update link | `POST {dashboard}/easycollect/v1/create` | `key\|merchant_txn\|name\|email\|phone\|amount\|udf1..udf5\|message` | ✅ fixed 2026-10-23 |
| 19 | Get link | `GET {dashboard}/easycollect/v1/get` | `key\|merchant_txn` | ➖ |
| 20 | List links | `POST {dashboard}/easycollect/v1/list` | `key\|start_date\|end_date` | ➖ |
| 21 | Send via SMS | `POST {dashboard}/easycollect/v1/send_sms` | `key\|sms_template_name\|payment_link\|msg_type` | ➖ |
| 22 | Send via WhatsApp | `POST {dashboard}/easycollect/v1/send_whatsapp_msg` | `key\|whatsapp_template_name\|payment_link\|msg_type` | ➖ |
| 23 | Send via Email | `POST {dashboard}/easycollect/v1/send_email` | `key\|sms_template_name\|payment_link\|msg_type` | ➖ |
| 24 | Send via OBD call | `POST {dashboard}/easycollect/v1/send_obd_call` | per spec | ➖ |
| 25 | Fetch SmartPay link | `POST {dashboard}/smartpay/v1/get` | `key\|payment_url_suffix` | ➖ |

**How KhanaBook uses it.** #18 is the collection path for online orders and
catering/advance deposits: POS creates the link and shows/sends the URL to the
customer. The same link is returned for a bill, so the bill page can re-display it
without a second create. #19 resolves a link's current state; #20 lists links for a
day; #21–#23 push the link to the customer instead of the cashier reading it out.
#25 resolves links authored in the Easebuzz dashboard, which the API cannot create.

> **Scope flag.** `docs/meta/AGENTS.md` lists "payment links" under *do not build*,
> while the server already ships `/payments/easebuzz/create-link` and the app calls
> it. This is a product decision to settle before repairing #18 — either the paths
> are in scope (then fix them) or they are not (then delete the endpoints and the
> Android callers together). Do not fix half.

**Contract (UAT-verified)**

```json
POST {dashboard}/easycollect/v1/create      (JSON)
{"key": "...", "merchant_txn": "KBLINK...", "name": "...", "email": "...",
 "phone": "...", "amount": "1.00", "udf1": "", ... "udf5": "",
 "message": "", "sub_merchant_id": "S280498CAWF",
 "hash": "sha512(key|merchant_txn|name|email|phone|amount|udf1|udf2|udf3|udf4|udf5|message|salt)"}

→ {"status": true, "data": {"id": 12842792, "merchant_txn": "KBLINK...",
    "payment_url": "https://testpay.easebuzz.in/easy_collect/...",
    "state": "active", "expiry_date": "27-10-2026",
    "submerchant_id": "S280498CAWF", "payment_made": 0}}
```

**Traps — all four verified**

1. **The endpoint is `/easycollect/v1/create`.** `/easy_collect` is a *payment page
   prefix* (`pay.easebuzz.in/easy_collect/<token>`), not an API. The client used to
   post to that prefix on the checkout host.
2. **`data` is an object.** It carries `{id, merchant_txn, payment_url, state,
   expiry_date}`; casting it to `String` throws. The client now reads
   `data.payment_url` and refuses a `status: true` that carries no link.
3. **`sub_merchant_id` is mandatory on a sub-aggregator account**; omitting it
   returns `ECSUBME02`. It is **not** part of the digest.
4. **`easycollect_list` is the only endpoint that wants `YYYY-MM-DD`.**
   `dd-mm-yyyy` returns `ECLISTE03`. Its success envelope is
   `{"status":"200","count":N,"data":[...]}` — status is a **string** here.
5. **Create takes `application/json`, not a form body.** Every request body in
   `Easy-Collect-APIs.yaml` is JSON; the current client posts form-encoded, which
   is the fourth independent defect in that method (§8.2).

**Variants.** `easycollect/v1/create` has ten documented variants (standard,
partial payment, min/max, split, sub-merchant, non-seamless auth, eNACh auth, …)
sharing one path but **different hash sequences**. Pick the variant first; hashing
is not interchangeable. See `official/articles/easycollect-*.md`.

**Send-endpoint naming traps.** The hash slot for the SMS template is documented as
`sms_template_name` even on `send_email`, where the request field is
`email_template_name`; and the hash slot named `payment_link` carries the request
field `payment_url`. Copy the documented names literally.

### 4.7 Native OTP (seamless card flow) — 3 operations

| # | Operation | Endpoint | Digest (`checksum`) | Code |
|---|---|---|---|---|
| 26 | Confirm OTP | `POST {dashboard}/otp/v1/confirm` | `key\|otp\|easepayid` | ❌ |
| 27 | Resend OTP | `POST {dashboard}/otp/v1/resend` | `key\|easepayid` | ❌ |
| 28 | Cancel transaction | `POST {dashboard}/otp/v1/cancel` | `key\|easepayid` | ➖ |

**How KhanaBook uses it.** **It does not** — and it cannot with the current
onboarding design. These are *card-payment* OTPs keyed by `easepayid`; the hosted
checkout never exposes an OTP to us. **There is no documented sub-merchant
onboarding OTP endpoint at all.** The code used to call
`/submerchant/v1/verify_otp` and `/submerchant/v1/resend_otp`, which exist
nowhere in 17 captured specs; both calls and the app's onboarding OTP screen were
**removed on 2026-10-23** (§8.2). If Easebuzz asks the merchant for a phone OTP,
that happens on Easebuzz's own dashboard.

Do not reintroduce this as `/otp/v1/*` — that would silently repurpose a
card-payment OTP as an onboarding step.

### 4.8 Post-transaction split — 2 operations, 🚫 blocked

| # | Operation | Endpoint | Hash | Code |
|---|---|---|---|---|
| 29 | Create split | `POST {dashboard}/post-split/v1/create/` | `key\|merchant_request_id\|easebuzz_id` | ✅ path, 🚫 disabled |
| 30 | Retrieve split | `POST {dashboard}/post-split/v1/retrieve/` | `key\|merchant_request_id` | ✅ path, 🚫 disabled |

**How KhanaBook uses it.** This is the mechanism that actually moves a
restaurant's share to its own bank account: one configuration entry of
`total − commission`, with the commission implicitly staying at the parent. Max 3
attempts per transaction; blocked after settlement or once a refund exists.

**Blocked on `split: false` for merchant `280498`.** Two consequences worth
carrying into coding:

- Every call fails permanently (`EBPTSURVE02`), so the code path must not be
  retried, and no tool/endpoint should advertise it as available.
- `EasebuzzApiClient.createSplitLabel()` called `/split/v1/create`, which is
  **not a published path** — the label concept lives in the split *dashboard*,
  not this API. That method was doubly dead (wrong path, disabled product) and
  was **removed on 2026-10-23** (§8.2) along with `rotateSplitLabelIfBankChanged`.
  The `split_label` column is now written only by an explicit admin action:
  `PUT /admin/sub-merchants/{id}/split-label` (Sub-Merchants tab of the platform
  dashboard), which also refreshes the bank snapshot and can clear the label to
  disarm post-split. The label must exist on the Easebuzz split dashboard already;
  we cannot create it there over the API.

Until split is enabled, settlement is a parent-pool payout and the restaurant's
share reaches them by whatever arrangement the business team uses outside this
API.

### 4.9 WIRE / neobanking — reference only, 🚫 blocked

`wire_allowed: false` on this merchant. Full specifications are vendored at
`official/openapi/Wire-Stoplight.yaml`, `Sub-Merchant.yaml`,
`Beneficiary-Accounts.yaml`, `Contacts.yaml`, `InstaCollect.yaml`.

| Operation | Endpoint | Auth header sequence |
|---|---|---|
| Create sub-merchant | `POST {wire}/api/v1/merchants/create/` | `key\|name\|email\|phone` |
| Retrieve sub-merchant | `GET {wire}/api/v1/merchants/retrieve/` | undocumented |
| Initiate transfer | `POST {wire}/api/v1/transfers/initiate/` | `key\|beneficiary_code\|unique_request_number\|amount` |
| Transfer detail | `GET {wire}/api/v1/transfers/` | undocumented |
| Ledger | `GET {wire}/api/v1/ledger/` | undocumented |
| Beneficiaries | `POST {wire}/api/v1/beneficiaries/` | per spec |
| Webhook config | `PUT {wire}/api/v1/merchants/webhooks/` | undocumented |

**How KhanaBook might use it.** Direct payouts to a restaurant's bank account,
independent of a payment. Not needed while settlement runs through the PG payout
cycle.

**Warning for the coding phase.** `EasebuzzWireApiClient` builds
`SHA512(key|salt)` for every call. The spec documents that sequence for *one*
operation (sub-merchant create) and **nothing** for the GET lookups the client
actually uses. It is a guess. Moot while WIRE is off, but it will fail on first
use. Do not harden WIRE before Easebuzz confirms the sequences.

### 4.10 Manifest id → section index

Every id in [`official/manifest.json`](./official/manifest.json), so a script or a
reader can jump between the two without guessing.

| Manifest id | Section | Manifest id | Section |
|---|---|---|---|
| `submerchant.create` | 4.1 #1 | `refund.create` | 4.4 #12 |
| `submerchant.update` | 4.1 #2 | `refund.status` | 4.4 #13 |
| `submerchant.kycAccessKey` | 4.1 #3 | `split.create` | 4.8 #29 |
| `payment.initiate` | 4.2 #4 | `split.retrieve` | 4.8 #30 |
| `payment.initiateAuthCapture` | 4.2 #5 | `settlement.ondemand.initiate` | 4.5 #16 |
| `payment.capture` | 4.2 #6 | `settlement.ondemand.status` | 4.5 #17 |
| `payment.reverse` | 4.2 #7 | `payout.retrieve.v1` | 4.5 #14 |
| `transaction.status` | 4.3 #8 | `payout.retrieve.v2` | 4.5 #15 |
| `transaction.statusV1` | 4.3 #9 | `easycollect.create` | 4.6 #18 |
| `transaction.byDate` | 4.3 #10 | `easycollect.get` | 4.6 #19 |
| `transaction.byDateRange` | 4.3 #11 | `easycollect.list` | 4.6 #20 |
| `otp.confirm` | 4.7 #26 | `easycollect.sendSms` | 4.6 #21 |
| `otp.resend` | 4.7 #27 | `easycollect.sendWhatsapp` | 4.6 #22 |
| `otp.cancel` | 4.7 #28 | `easycollect.sendEmail` | 4.6 #23 |
| `wire.submerchant.create` | 4.9 | `smartpay.get` | 4.6 #25 |
| `wire.transfer.initiate` | 4.9 | | |

Webhook ids: `webhook.transaction`, `webhook.refund`, `webhook.payout`,
`webhook.submerchantKyc` → §5.

Two documented operations are catalogued but deliberately absent from the
manifest, because KhanaBook must never call them: the legacy refund
`/transaction/v1/refund` (#12a) and EasyCollect `send_obd_call` (#24).

---

## 5. Webhooks

Four endpoints exist in this repo, all accepting both
`application/x-www-form-urlencoded` and JSON. The two envelopes are **not**
interchangeable and the docs use both:

- **Transaction** — flat form fields: `key=…&status=success&txnid=…&hash=…`.
- **Refund, payout, KYC** — `status=1&data=<urlencoded JSON>`.

Refund, payout and KYC all unwrap `data` before verifying. The refund handler also
accepts `refund_status` as the state when `status` is the transport flag `"1"`,
while flat legacy callbacks keep working. Evidence and the original mismatch:
[`CODE_CROSSCHECK_2026-10-23.md`](./CODE_CROSSCHECK_2026-10-23.md) §2.3.

| Webhook | Local path | Verify sequence | Meaning |
|---|---|---|---|
| Transaction | `/payments/easebuzz/webhook` | `salt\|status\|udf10..udf1\|email\|firstname\|productinfo\|amount\|txnid\|key` | payment outcome |
| Refund | `/payments/easebuzz/refund/webhook` | `key\|easepayid` | refund progression |
| Payout | `/payments/easebuzz/payout/webhook` | `key\|payout_id` | settlement batch paid |
| Sub-merchant KYC | `/payments/easebuzz/sub-merchant/webhook` | `key\|submerchant_id` | activation / rejection |

**The transaction sequence is the request sequence reversed, with the salt
leading.** The request hashes `key|…|txnid`; the webhook hashes
`txnid|…|key`. That asymmetry is the single most common verification bug and it is
already handled correctly here.

**Delivery contract**

- Return **200 within 10 s**.
- Retries every 30 min, **5 times**, then the webhook is **disabled** until someone
  re-enables it in the dashboard.
- Order is not guaranteed; duplicates are expected. De-duplicate on
  `(txnid, status)` / `(easepayid, refund status)`.
- `status` inside the transaction hash is the **transaction** status
  (`data.status`, e.g. `success`), not the envelope's `status=1`.

**How KhanaBook uses it.** The webhook is the only authoritative payment signal.
`EasebuzzWebhookService` verifies with constant-time comparison, de-duplicates, and
feeds the retry/dead-letter pipeline. Sub-merchant KYC is the only approval
signal — there is no polling endpoint, so a missed webhook means a human phones the
RM.

**Known defect.** The sub-merchant KYC webhook is registered on the dashboard with
a **trailing space** in the URL. Raise with Easebuzz; see §9.

---

## 6. State machines coding must respect

**Easebuzz transaction**

```
PreInitiated → Initiated → Pending → Success | Failure | Usercancelled | Dropped | Bounced
```

`Bounced` = abandoned/expired checkout, **not** a failure. `Dropped` = customer
left on the bank page. Compare case-insensitively.

**KhanaBook payment**

```
CREATED → PENDING → SUCCESS
              ├→ FAILED / CANCELLED / EXPIRED
              └→ late webhook → reconcile, never regress a terminal state
```

**Sub-merchant**

```
CREATED (kyc 0) → KYC_SUBMITTED → ACTIVE
                        └→ REJECTED (resubmit via portal)
```

Activation requires **`kyc_status` AND `bank_status`**; they arrive independently,
there is no webhook for a later bank change, and no expiry webhook (local
`SubMerchantKycExpiryService` enforces that).

**Refund**

```
Queued → Accepted | Approved → Refunded (ARN)
```

---

## 7. What UAT proved that the documents do not say

| # | Finding | Consequence |
|---|---|---|
| 1 | Checkout paths 405 on the dashboard host | `initiate`, `capture`, `reverse` must use `{checkout}` |
| 2 | Initiate returns `{"status":1,"data":"<access key>"}` | `status` is a number; URL is `{checkout}/pay/<key>` |
| 3 | Status rows live in `msg[]`, lowercase `"success"` | read `msg`, compare case-insensitively |
| 4 | Refund V2 returns `refund_id` + human message; status starts `queued` | create ≠ refunded |
| 5 | `easycollect/v1/list` accepts only `YYYY-MM-DD` (`ECLISTE03` otherwise) | one date format exception in the whole API |
| 6 | `easycollect/v1/create` needs `sub_merchant_id` (`ECSUBME02` without) | sub-aggregator accounts must always send it |
| 7 | `easycollect/v1/create` returns `data` as an **object** | never cast `data` to `String` |
| 8 | Sub-merchant ids look like `S280498CAWF` | do not assume a format; treat as opaque |
| 9 | `kyc_dashboard_url` is `"NA"` on UAT | fall back to `generate_kyc_access_key` |
| 10 | Sub-merchant update = upsert on `/create/`; slash mandatory; resend 5 bank fields | one path for create and update |
| 11 | International test cards rejected; UPI `success@easebuzz` works | UAT card-path evidence must come from Easebuzz |

---

## 8. What KhanaBook does today (verified against this repo)

### 8.1 Coverage matrix

| Operation | Manifest id | Code location | Verdict |
|---|---|---|---|
| Create sub-merchant | `submerchant.create` | `EasebuzzApiClient.java:470` | ✅ |
| Update sub-merchant | `submerchant.update` | `:598` | ✅ |
| Generate KYC access key | `submerchant.kycAccessKey` | `:623` | ✅ |
| Initiate payment | `payment.initiate` | `:68` | ✅ |
| Initiate auth & capture | `payment.initiateAuthCapture` | — | ➖ |
| Capture | `payment.capture` | — | ➖ |
| Void / reverse | `payment.reverse` | — | ➖ |
| Status V2.1 | `transaction.status` | `:182` | ✅ (fallback deliberate) |
| Status V1 | `transaction.statusV1` | — | ➖ |
| By date | `transaction.byDate` | — | ➖ |
| By date range | `transaction.byDateRange` | — | ➖ |
| Refund V2 | `refund.create` | `:205` | ✅ |
| Refund status | `refund.status` | `:228` | ✅ |
| Payout report V2 | `payout.retrieve.v2` | `:294` | ✅ (paginated) |
| Payout report V1 | `payout.retrieve.v1` | — | ➖ |
| On-demand initiate | `settlement.ondemand.initiate` | `:260` | ✅ fixed 2026-10-23 |
| On-demand status | `settlement.ondemand.status` | — | ➖ |
| EasyCollect create | `easycollect.create` | `:369` | ✅ fixed 2026-10-23 |
| EasyCollect get / list / send ×3 | `easycollect.*` | — | ➖ |
| SmartPay get | `smartpay.get` | — | ➖ |
| Sub-merchant OTP verify / resend | *(no such operation)* | — removed 2026-10-23 | ✅ removed |
| Payout transfer | *(no such operation)* | — removed 2026-10-23 | ✅ removed |
| Split create / retrieve | `split.*` | `:637`, `:652` | 🚫 disabled |
| WIRE anything | `wire.*` | `EasebuzzWireApiClient` | 🚫 disabled |
| Native OTP confirm / resend / cancel | `otp.*` | — | ➖ |

Counted against the 31 documented operations: **12 implemented against the correct
contract** (two of them, post-split create/retrieve, cannot run because the account
has `split: false`), **17 absent**, **2 WIRE and unavailable**.
The **4 operations that existed in no specification** — sub-merchant OTP verify,
OTP resend, payout transfer and split-label creation — were **removed on
2026-10-23**; none of them is called anywhere in the codebase.

### 8.2 Calls that existed in no specification — removed 2026-10-23

Four outbound calls had no published contract. Each was deleted from
`EasebuzzApiClient` together with its `SubMerchantService` method, its
`AdminSubMerchantController` route, the web-admin service stub, and the Android
onboarding OTP screen, repository and API declarations. None has a caller or a
route left.

| Method (was at) | Called | Published reality |
|---|---|---|
| `verifyOtp` (`:254`) | `/submerchant/v1/verify_otp` | no such path; Native OTP is `{dashboard}/otp/v1/confirm` |
| `resendOtp` (`:264`) | `/submerchant/v1/resend_otp` | no such path |
| `initiatePayout` (`:283`) | `/payout/v2/transfer` | no transfer operation exists in the PG API |
| `createSplitLabel` (`:650`) | `/split/v1/create` | no such path |

No wrong-path calls remain. The last one, `initiateOnDemandSettlement`, was
rewritten on 2026-10-23 to the documented `POST /settlements/v1/ondemand/initiate/`
batch contract (§4.5): one digest per transaction, `merchant_batch_id` as the
idempotency key, `merchant_email` from `easebuzz.merchant-email`. Its callers now
select bills, because the gateway settles transactions rather than an amount.

The Android client calls `api/v1/payments/easebuzz/...` and the server's context
path **is** `/api/v1` (`application.properties:5`), so the earlier `/api/v1` vs
`/api/v2` concern is closed — no defect there.

### 8.3 What is already right and must not be "improved"

- The transaction webhook's reversed hash, constant-time compare, duplicate guard,
  and retry/dead-letter pipeline.
- `initiatePayment`'s full `udf1..udf10` hash and `sub_merchant_id` handling.
- Refund V2 with `merchant_refund_id` idempotency and the `easebuzz_id` status call.
- Settlement report pagination with an explicit `truncated` flag.
- The sub-merchant create payload guard test that forbids a `kyc_details` block.
- The status route fallback (`:191-192`): it advances only on 404/405, and exists
  because Easebuzz's own v2.0 kit and its docs disagree about the path. Do not
  delete it to "clean up" the non-published route.

---

## 9. Open questions for Easebuzz (blocking the coding phase)

1. **Enable `split` and `wire_allowed` on merchant `280498`**, or confirm which plan
   does. Four operations stay unimplementable otherwise.
2. **Is on-demand settlement available to this account?** `insta_allowed: false` and
   it is absent from `merchantApps`. If not, the instant-settlement path gets
   deleted rather than fixed.
3. **Sub-merchant onboarding OTP** — does an endpoint exist? If not, the flow is
   removed.
4. **`kyc_details` on create** — full schema, required document types per entity,
   and whether `file_url` must be publicly GET-able.
5. **`upload_kyc_documents`** — appears in our code, in no published spec.
   Confirm or delete; the hosted portal is the documented path.
6. **Commission** — all instrument rates read `0.0`; is zero-MDR intentional?
7. **WIRE webhook signature scheme** — undocumented; the current `key|salt` guess
   is unverified.
8. **The sub-merchant webhook URL has a trailing space** in the dashboard.
9. **Domestic UAT card numbers**, or international cards enabled on the test key.
10. **Sub-merchant rejection reasons / deactivation** — boolean KYC status only, no
    polling, no delete operation.

Full text and the ready-to-send email:
[`../easebuzz-review/easebuzz-review-email.md`](../easebuzz-review/easebuzz-review-email.md)
and `easebuzz-mcp/docs/RM-QUESTIONS.md`.

---

## 10. Collection status and the coding hand-off

**Collection: complete for the PG product.** 17 OpenAPI exports, 32 guide pages,
one machine-readable manifest, and this catalogue cover every operation the
accounts, payments, refunds, links, reporting and webhook surfaces need. WIRE is
vendored for reference only, because the account cannot use it.

What the coding phase gets, in dependency order:

| Step | Work | Depends on |
|---|---|---|
| 0 | Settle the payment-links scope question (§4.6) and the onboarding-OTP question (§4.7) | product decision |
| 1 | ~~Fix `createPaymentLink`~~ — done 2026-10-23: dashboard host, `/easycollect/v1/create`, JSON, `data.payment_url`; live UAT re-check still outstanding | nothing |
| 2 | ~~Align the refund webhook~~ — envelope + `refund_status` handled 2026-10-23; still capture one real callback and one refund response to settle the request encoding | live UAT traffic |
| 3 | Delete or re-point the four impossible calls (payout transfer, on-demand, OTP ×2) | questions 1–3 |
| 4 | Add transaction range listing (`/transaction/v2/retrieve/date`) | nothing |
| 5 | Add EasyCollect get/list/send | scope decision |
| 6 | Delete dead split/payout controllers, or gate them behind capability flags | question 1 |

Each fix has an acceptance shape in §4 and in
`docs/integrations/easebuzz-review/OFFICIAL_DOC_CROSSCHECK_2026-09-20.md`.
Nothing in steps 1–2 needs an answer from Easebuzz; those are pure implementation
defects and are the safe starting point.
