# Easebuzz Sub-Merchant & Payment API Reference

> **Official Easebuzz Documentation**: https://docs.easebuzz.in/
> All endpoint URLs, hash sequences, and request/response schemas below are aligned with the official Easebuzz Stoplight documentation.

---

## Table of Contents

1. [Create Sub-Merchant](#1-create-sub-merchant)
2. [Update Sub-Merchant](#2-update-sub-merchant)
3. [Generate KYC Access Key](#3-generate-kyc-access-key)
4. [Confirm OTP / Verify OTP](#4-confirm-otp--verify-otp)
5. [Resend OTP](#5-resend-otp)
6. [Create Split Label](#6-create-split-label)
7. [Post-Transaction Split — Create](#7-post-transaction-split--create)
8. [Post-Transaction Split — Retrieve](#8-post-transaction-split--retrieve)
9. [Payment — Initiate API](#9-payment--initiate-api)
10. [Payment — Status API](#10-payment--status-api)
11. [Cancel Transaction](#11-cancel-transaction)
12. [Refund API v2](#12-refund-api-v2)
13. [Refund Status API](#13-refund-status-api)
14. [Payout API v2](#14-payout-api-v2)
15. [On-Demand Settlement APIs](#15-on-demand-settlement-apis)
16. [WIRE: InstaCollect QR Webhook Configuration](#16-wire-instacollect-qr-webhook-configuration)
17. [WIRE: Get Sub-Merchant by Email](#17-wire-get-sub-merchant-by-email)
18. [WIRE: Get Sub-Merchant by ID](#18-wire-get-sub-merchant-by-id)
19. [WIRE: Payout Webhook Configuration](#19-wire-payout-webhook-configuration)
20. [WIRE: Get KYC Profile URL](#20-wire-get-kyc-profile-url)
21. [WIRE: Get Sub-Merchant by Key](#21-wire-get-sub-merchant-by-key)

---

## 1. Create Sub-Merchant

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/af94640eeae86-create-sub-merchant-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/merchant/v1/submerchant/create/`
  (the create page shows a trailing slash, the update page shows none — both reach the same handler; KhanaBook sends `/create/`)
- **Content-Type**: `application/json`
- **Hash**: `sha512(key|submerchant_email|submerchant_phone|salt)`

> Collected 2026-10-21. Every claim below is tagged with where it comes from:
> **[DOC]** official Stoplight page, **[ERA]** Easebuzz relationship manager (written answers),
> **[CODE]** this repository. The official pages are client-rendered, so their bodies cannot be
> fetched programmatically — the request/response samples here are transcriptions held in this repo,
> not live scrapes.

### 1.1 Request body — sections

| Section | Official doc | KhanaBook sends | Note |
|---|---|---|---|
| `merchant_details` | required | always | `merchant_key` + `hash` |
| `submerchant_details` | required, 10 keys | always, exactly those 10 | `sub_merchant_id` added only when Easebuzz already issued one |
| `business_details` | absent from the create sample; **ERA lists it as optional** | always | CPV needs legal name, address, state, MCC — see 1.2 |
| `submerchant_deduction_percentage` | optional | only when a rate is configured | UPI/DC deduction overrides |
| `kyc_details` | **never appears in any first-party sample we hold** | never | see 1.3 |

```json
{
  "merchant_details": { "merchant_key": "YOUR_MERCHANT_KEY", "hash": "sha512(key|email|phone|salt)" },
  "submerchant_details": {
    "sub_merchant_name": "Business Name",
    "sub_merchant_email": "email@example.com",
    "sub_merchant_phone": "9999999999",
    "sub_merchant_name_in_bank": "Name as per bank",
    "sub_merchant_account_number": "1234567890",
    "sub_merchant_bank_name": "HDFC",
    "sub_merchant_branch_name": "Branch Name",
    "sub_merchant_ifsc_code": "HDFC0000123",
    "sub_merchant_password": "AutoGenPass@123",
    "sub_merchant_confirm_password": "AutoGenPass@123"
  }
}
```

`sub_merchant_password` / `sub_merchant_confirm_password` are auto-generated per onboarding and never
shown to the owner. `sub_merchant_id` is **not** in the required set — it is accepted, but only
meaningful for a re-submit after Easebuzz has issued an ID.

### 1.2 `business_details` (sent by KhanaBook; ERA: optional)

```json
{
  "sub_merchant_business_name":    "Registered legal entity name",
  "sub_merchant_business_nature":  "INDIVIDUAL/FREELANCERS | SOLE PROPRIETOR | PARTNERSHIP FIRM | PRIVATE LTD/PUBLIC LTD/OPC",
  "sub_merchant_business_type":    "SOLE_PROPRIETORSHIP | PARTNERSHIP | PRIVATE_LIMITED | PUBLIC_LIMITED | ...",
  "sub_merchant_business_address": "Full physical address",
  "sub_merchant_state":            "Maharashtra",
  "sub_merchant_mcc_code":         "5812 (restaurant) — locked at creation, no post-update",
  "sub_merchant_gstin":            "optional",
  "sub_merchant_pan_number":       "optional",
  "sub_merchant_fssai_number":     "optional (mandatory for food merchants per Easebuzz compliance)"
}
```

`sub_merchant_pan_number` and `sub_merchant_fssai_number` are **business identifiers, not document
uploads** — sending them is not "KYC in create".

### 1.3 Response

Success — both shapes occur (ERA's reference puts `submerchant_id` at the top level; the create sample
nests it). `SubMerchantService` reads top-level first, then `submerchant` / `data` / `msg`:

```json
{
  "status": true,
  "submerchant": {
    "email": "email@example.com",
    "account_number": "xxxxxxxxxxxx",
    "submerchant_id": "S360DILA",
    "name": "Business Name",
    "mobile_number": "xxxxxxxxxx"
  },
  "submerchant_id": "S360DILA"
}
```

Failure — `error` is a code, `error_desc` is the sentence a human can act on:

```json
{ "status": false, "error": "error_code", "error_desc": "Sub merchant already exists" }
```

KhanaBook extracts provider text in the order `error_desc` → `error` → `msg` → `message`, skipping
boolean/numeric values, and falls back to the raw map only when nothing textual exists. This is what
stops `{status=false, error=null, error_desc=...}` from surfacing as raw JSON.

The same call can also hand back the hosted KYC link. This is not in the create sample we hold, but
it has been observed on this project's account and it removes a round trip, so it is consumed when
present rather than assumed:

```json
{
  "status": true,
  "submerchant_id": "S360DILA",
  "kyc_details": {
    "status": true,
    "message": "Token generated successfully",
    "kyc_dashboard_url": "https://kyc.easebuzz.in/...?access_key=..."
  }
}
```

`kyc_details.status` / `message` mean **the link was generated**, not that KYC passed. The row
stays `PENDING_KYC` until a KYC webhook says otherwise; nothing here may be read as approval.

`SubMerchantService.extractKycPortalUrl` reads `kyc_dashboard_url` / `kyc_url` from the top level,
then from `kyc_details`, `submerchant` or `data`, then accepts a bare `msg` **only when it is an
absolute http(s) URL** (the access-key response is documented as `{"status":true,"msg":"https://…"}`,
so both shapes are real). The URL is stored on the row, which means the owner can open the portal
immediately; `generate_kyc_access_key` is then only the fallback when the create response carried no
link, and the refresh path when a link has expired.

Immediately after a successful create the sub-merchant is `PENDING_KYC`; it reaches `ACTIVE`,
`CPV_PENDING` or `REJECTED` only through the KYC webhook.

### 1.4 KYC — two mutually exclusive paths

**Path A — portal (implemented, sanctioned).**

```
1. POST /merchant/v1/submerchant/create/            → submerchant_id, status=PENDING_KYC   (no docs)
   └─ may also return kyc_details.kyc_dashboard_url → stored, portal usable immediately
2. POST /submerchant/v1/generate_kyc_access_key     → kyc_dashboard_url
       hash merchant_key|sub_merchant_id|name|email|phone|salt
       only when step 1 returned no link; also the refresh path for an expired link
3. OWNER uploads documents on the Easebuzz portal   (documents never touch KhanaBook)
4. KYC webhook → ACTIVE / CPV_PENDING / REJECTED
```

**Secret handling.** The portal URL embeds an `access_key`: anyone holding it opens that
sub-merchant's KYC session. KhanaBook therefore

- stores the link in `kyc_portal_url` and never logs its value (only that one was captured/issued);
- redacts it — along with any `access_key` string — from the raw gateway response kept in
  `easebuzz_response`, because that column is surfaced as the onboarding `message`;
- returns it only to `OWNER`: `GET /restaurants/payment-config/easebuzz` and
  `GET …/sub-merchant-status` blank it for every other role, and `POST …/kyc-access-key` is
  owner-only so a counter terminal cannot mint a KYC session.

A failed refresh never blanks a stored link: the merchant keeps the URL they already had.

**No OTP stop (2026-10-22).** The create contract carries no OTP field, so onboarding no longer
waits for one: `submitToEasebuzz` stores a create-time link, and when the create response carries
none it issues one with `generate_kyc_access_key` immediately (best-effort — a failed call cannot
turn a successful registration into a failure). `POST …/onboard` and `…/resubmit` return that link
as `kycUrl` (blank for every role except `OWNER`). `POST …/verify-otp` and `…/resend-otp` remain
available for accounts that still send an OTP, reachable from the KYC screen as a secondary action.

**Path B — `kyc_details` inside create (not implemented, deliberately).**

| Round | ERA's answer | Consequence |
|---|---|---|
| 1 | not mentioned at all | nothing to build |
| 2 | `kyc_details` exists, documents passed as `file_url` | a URL implies a publicly fetchable file |
| 3 | "optional fields" on create | optional ≠ specified; still no field reference |

Blockers, in order of weight:
1. No first-party field reference — not in the create sample, not in the failure sample, not in the
   access-key page. Field names, nesting and per-business-type mandatory doc sets are all guesses.
2. `file_url` needs a file Easebuzz's servers can GET. `V89__add_sub_merchant_kyc_storage_keys.sql`
   deliberately moved KYC files into **private** storage with no public URL, so no compliant value
   can be produced without reversing that privacy decision.
3. Sending an unknown block to a CPV pipeline risks a mismatch rejection that is hard to unwind.

Guard: `EasebuzzRequestContractTest
.createSubMerchantSendsTheDocumentedSectionsAndNeverAKycDetailsBlock` asserts the three documented
sections are present **and** that `kyc_details` is absent — adding it to the create payload fails the build.

### 1.5 Sources collected in this repository

| File | What it proves |
|---|---|
| `docs/integrations/easebuzz/sub-merchant-apis.md` (this file) | transcribed request/response contracts for all sub-merchant, split, payment, refund, payout routes |
| `docs/integrations/easebuzz/easebuzz-complete-implementation-summary.txt` | per-endpoint implementation status; records that the OpenAPI **YAML export** (`Submerchant-APIs.yaml`) was consulted for update and lists `kyc_details` as an optional block |
| `docs/integrations/easebuzz/easebuzz-era-crosscheck-2026-10-18.md` | ERA Q&A log, including the `upload_kyc_documents` non-answer and the route probe |
| `docs/integrations/easebuzz/easebuzz-submerchant-business-model.md` | business/compliance model, private-storage KYC keys (`id_proof_url`, `bank_proof_url` columns) |
| `docs/integrations/easebuzz/EASEBUZZ.md`, `EASEBUZZ_LIFECYCLE.md` | integration and lifecycle overview |
| `md/EASEBUZZ_DOCUMENTATION.md` | consolidated implementation notes |
| `services/server/.../EasebuzzApiClient.java` | what is actually on the wire |

### 1.6 Open questions for Easebuzz (exact asks)

1. Full schema of `kyc_details` on create: key names, nesting, which document types are mandatory per
   business type, and whether `file_url` must be publicly GET-able or may be a time-limited signed URL.
2. Is `business_details` required at create? ERA says optional; the create sample omits it entirely.
3. Canonical path: `/submerchant/create/` or `/submerchant/create`.
4. A copy of the OpenAPI export (`Submerchant-APIs.yaml`) — our summary references it, but it is not in
   this repository, and that is the single artifact that would settle all three questions above.

---

## 2. Update Sub-Merchant

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/94318e0f55958-update-sub-merchant-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/merchant/v1/submerchant/create` (same endpoint as create)
- **Content-Type**: `application/json`
- **Hash**: `key|submerchant_email|submerchant_phone|salt`

### Request
```json
{
  "merchant_details": {
    "merchant_key": "YOUR_MERCHANT_KEY",
    "hash": "sha512(key|email|phone|salt)"
  },
  "submerchant_details": {
    "sub_merchant_name": "Updated Business Name",
    "sub_merchant_email": "email@example.com",
    "sub_merchant_phone": "9999999999",
    "sub_merchant_name_in_bank": "Name as per bank",
    "sub_merchant_account_number": "1234567890",
    "sub_merchant_bank_name": "HDFC",
    "sub_merchant_branch_name": "Branch Name",
    "sub_merchant_ifsc_code": "HDFC0000123",
    "sub_merchant_id": "S360DILA"
  }
}
```

### Response (Success)
```json
{
  "status": true,
  "submerchant_id": "S360DILA"
}
```

---

## 3. Generate KYC Access Key

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/2b48a38b084b9-generate-sub-merchant-kyc-access-key-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/generate_kyc_access_key`
- **Content-Type**: `application/json`
- **Hash**: `merchant_key|sub_merchant_id|name|email|phone|salt`

### Request
```json
{
  "merchant_key": "YOUR_MERCHANT_KEY",
  "sub_merchant_id": "S360DILA",
  "name": "Business Name",
  "email": "email@example.com",
  "phone": "9999999999",
  "hash": "sha512(merchant_key|sub_merchant_id|name|email|phone|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "https://kyc.easebuzz.in/..."
}
```

The OpenAPI export documents the field as `kyc_dashboard_url` instead of `msg`; KhanaBook accepts
either (`SubMerchantService.extractKycPortalUrl`). The link is a credential — see the secret-handling
rules in §1.4. Treat this call as a **refresh**: a create response may already have supplied the link,
and a failed refresh must not replace a working URL with an empty one.

### Response (Failure)
```json
{
  "status": false,
  "msg": "Error message"
}
```

---

## 4. Confirm OTP / Verify OTP

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/ifudkz7jpfadk-confirm-otp-verify-otp-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/verify_otp`
- **Content-Type**: `application/json`
- **Purpose**: Verify the OTP sent to the sub-merchant's registered mobile/email during onboarding or password reset.
- **Note**: KhanaBook uses this API via `POST /admin/sub-merchants/{id}/verify-otp` from the web admin UI.

### Request
```json
{
  "merchant_key": "YOUR_MERCHANT_KEY",
  "sub_merchant_id": "S360DILA",
  "otp": "123456",
  "hash": "sha512(merchant_key|sub_merchant_id|otp|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "OTP verified successfully"
}
```

---

## 5. Resend OTP

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/3b9hki8thr6pn-resend-otp-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/resend_otp`
- **Content-Type**: `application/json`
- **Purpose**: Resend OTP to the sub-merchant's registered contact.
- **Note**: KhanaBook uses this API via `POST /admin/sub-merchants/{id}/resend-otp` from the web admin UI.

### Request
```json
{
  "merchant_key": "YOUR_MERCHANT_KEY",
  "sub_merchant_id": "S360DILA",
  "hash": "sha512(merchant_key|sub_merchant_id|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "OTP resent successfully"
}
```

---

## 6. Create Split Label

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/e383e3fb60ce3-create-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/split/v1/create`
- **Content-Type**: `application/json`
- **Hash**: `key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt`

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "hash": "sha512(key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt)",
  "name_on_bank": "Name as per bank",
  "bank_name": "HDFC",
  "branch_name": "Branch Name",
  "ifsc_code": "HDFC0000123",
  "account_number": "1234567890",
  "label": "sm_S360DILA"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "Label created successfully"
}
```

---

## 7. Post-Transaction Split — Create

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2 (split creation is part of the payout/split suite)
- **Endpoint**: `POST https://dashboard.easebuzz.in/post-split/v1/create/`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|easebuzz_id|salt`
- **Note**: Max 3 update attempts per transaction

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "UMRID_1712345678",
  "easebuzz_id": "E250XXXXXXXXXX",
  "amount": "100.00",
  "description": "Split for order #123",
  "configuration": [
    { "label": "sm_S360DILA", "amount": "95.00" },
    { "label": "master_commission", "amount": "5.00" }
  ],
  "hash": "sha512(key|merchant_request_id|easebuzz_id|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "request_status": "success",
  "merchant_request_id": "UMRID_1712345678",
  "configuration": [
    { "label": "sm_S360DILA", "status": "success" },
    { "label": "master_commission", "status": "success" }
  ],
  "meta": {
    "description": "Split Configuration for a transaction can be changed upto 3 times",
    "total_attempts": "3",
    "remaining_attempts": "2"
  }
}
```

### Error Codes
| Code | Description |
|------|-------------|
| EBPTSURVE01 | Duplicate merchant_request_id |
| EBPTSURVE02 | Post-split feature not activated |
| EBPTSURVE04 | Split payouts disabled |
| EBPTSURVE06 | Max update attempts reached |
| EBPTSURVE07 | Empty configuration array |
| EBPTSURVE08 | Split total ≠ transaction amount |
| EBPTSUTVE01 | Transaction not found |
| EBPTSUTVE02 | Transaction not successful |
| EBPTSUTVE03 | Amount mismatch |
| EBPTSUTVE04 | Transaction already settled |
| EBPTSUTVE05 | Refunds exist against transaction |

---

## 8. Post-Transaction Split — Retrieve

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/post-split/v1/retrieve/`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|salt`

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "UMRID_1712345678",
  "hash": "sha512(key|merchant_request_id|salt)"
}
```

### Response (Success)
```json
{
  "status": "success",
  "merchant_request_id": "UMRID_1712345678",
  "description": "Split for order #123",
  "error": null,
  "split_configuration": [
    { "label": "sm_S360DILA", "amount": 95.00 },
    { "label": "master_commission", "amount": 5.00 }
  ]
}
```

---

## 9. Payment — Initiate API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api
- **Endpoint**: `POST https://pay.easebuzz.in/payment/initiateLink` (or SDK equivalent)
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|||||||salt`
- **Note**: This is the standard Easebuzz payment initiation. KhanaBook backend calls this and returns params to the Android SDK V2.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "amount": "100.00",
  "productinfo": "Order #123",
  "firstname": "Rahul",
  "email": "rahul@example.com",
  "phone": "9999999999",
  "surl": "https://kbook.iadv.cloud/api/v2/payments/easebuzz/return",
  "furl": "https://kbook.iadv.cloud/api/v2/payments/easebuzz/return",
  "hash": "sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|||||||salt)"
}
```

### Response
```json
{
  "status": 1,
  "data": "PAYMENT_LINK_OR_SDK_PARAMS"
}
```

---

## 10. Payment — Status API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/j6v1nmy74m325-status-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v2.1/retrieve`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|salt`
- **Note**: KhanaBook uses this via `GET /api/v2/payments/easebuzz/status/{billId}` which proxies to Easebuzz.

### Request
```json
{
  "txnid": "KB1712345678",
  "key": "YOUR_MERCHANT_KEY",
  "hash": "sha512(key|txnid|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "txnid": "KB1712345678",
    "easebuzz_id": "E250XXXXXXXXXX",
    "status": "success",
    "amount": "100.00",
    "mode": "UPI"
  }
}
```

---

## 11. Cancel Transaction

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/vjykxhu9v65zf-cancel-transaction-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v1/cancel`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|amount|salt`
- **Purpose**: Cancel a pending transaction before it is captured/settled.
- **Note**: KhanaBook currently does not expose this to POS. Documented for future use (e.g., cancelling unconfirmed payments after timeout).

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "amount": "100.00",
  "hash": "sha512(key|txnid|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": "Transaction cancelled successfully"
}
```

---

## 12. Refund API v2

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v2/refund`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|amount|salt`
- **Note**: KhanaBook backend calls this when `POST /api/v2/payments/easebuzz/refund/{billId}` is invoked from Android POS.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "amount": "50.00",
  "reason": "Customer request",
  "hash": "sha512(key|txnid|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "refund_id": "REF250XXXXXXXXXX",
    "refund_amount": "50.00",
    "status": "initiated"
  }
}
```

---

## 13. Refund Status API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v2/refund_status`
- **Content-Type**: `application/json`
- **Hash**: `key|txnid|refund_id|salt`
- **Purpose**: Check the current status of a refund initiated via Refund API v2.
- **Note**: KhanaBook can use this in the refund polling loop inside `OrdersScreen`.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "txnid": "KB1712345678",
  "refund_id": "REF250XXXXXXXXXX",
  "hash": "sha512(key|txnid|refund_id|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "refund_id": "REF250XXXXXXXXXX",
    "refund_amount": "50.00",
    "status": "completed",
    "refunded_at": "1712345678"
  }
}
```

---

## 14. Payout API v2

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2
- **Endpoint**: `POST https://dashboard.easebuzz.in/payout/v2/transfer`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|amount|salt`
- **Purpose**: Direct payout to a bank account or UPI ID. Can be used for instant commission payouts or emergency settlements.
- **Note**: KhanaBook does not currently use this. Future enhancement for on-demand restaurant payouts.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "KB_PAYOUT_1712345678",
  "amount": "500.00",
  "purpose": "Restaurant settlement",
  "beneficiary_name": "Business Name",
  "beneficiary_account_number": "1234567890",
  "beneficiary_ifsc": "HDFC0000123",
  "hash": "sha512(key|merchant_request_id|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "merchant_request_id": "KB_PAYOUT_1712345678",
    "status": "initiated",
    "amount": "500.00"
  }
}
```

---

## 15. On-Demand Settlement APIs

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is
- **Endpoint**: `POST https://dashboard.easebuzz.in/settlement/v1/on_demand`
- **Content-Type**: `application/json`
- **Hash**: `key|merchant_request_id|amount|salt`
- **Purpose**: Request an immediate settlement of available balance instead of waiting for T+1 auto-settlement.
- **Note**: KhanaBook does not currently expose this. Can be added as a premium feature for high-volume partners.

### Request
```json
{
  "key": "YOUR_MERCHANT_KEY",
  "merchant_request_id": "KB_SETTLE_1712345678",
  "amount": "5000.00",
  "hash": "sha512(key|merchant_request_id|amount|salt)"
}
```

### Response (Success)
```json
{
  "status": true,
  "msg": {
    "merchant_request_id": "KB_SETTLE_1712345678",
    "status": "initiated",
    "estimated_credit_time": "2-4 hours"
  }
}
```

---

## 16. WIRE: InstaCollect QR Webhook Configuration

- **Base URL**: `https://wire.easebuzz.in`
- **Endpoint**: `PUT /api/v1/insta-collect/merchants/webhooks/`
- **Content-Type**: `application/json`
- **Auth**: `Authorization: SHA-512("{key}|{salt}")` + `WIRE-API-KEY` header
- **Official Doc**: Easebuzz WIRE platform documentation

### Request
```json
{
  "key": "PARENT_MERCHANT_KEY",
  "merchant_key": "SUB_MERCHANT_KEY",
  "merchant_email": "submerchant@test.in",
  "webhook_conf": [
    {
      "event_type": "ORDER_STATUS_UPDATE",
      "status": "enable",
      "url": "https://your-domain.com/api/v2/payments/easebuzz/webhook",
      "interval_unit": "hours",
      "interval_value": 20,
      "max_attempts": 2
    }
  ]
}
```

**Supported Event Types**: `ORDER_STATUS_UPDATE`, `TRANSACTION_CREDIT`, `INSTA_COLLECT_VIRTUAL_ACCOUNT_KYC_APPROVAL`, and more (full list in Easebuzz docs).

### Response (Success)
```json
{
  "success": true,
  "data": {
    "message": "Webhook configuration updated successfully.",
    "success": true
  }
}
```

### Request Body Parameters
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `key` | string | ✅ | Parent merchant key (from config) |
| `merchant_key` | string | ✅ (or email) | Sub-merchant identifier (Easebuzz ID like `S360DILA` or sub-merchant key) |
| `merchant_email` | string | ✅ (or key) | Sub-merchant registered email |
| `webhook_conf` | array | ✅ | Array of webhook configuration objects |

### Webhook Config Object
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event_type` | string | ✅ | Event to trigger on (e.g., `ORDER_STATUS_UPDATE`) |
| `status` | string | ✅ | `"enable"` or `"disable"` |
| `url` | string | ✅ | HTTPS webhook callback URL |
| `interval_unit` | string | ✅ | Retry interval unit (`minutes`, `hours`, `days`) |
| `interval_value` | int | ✅ | Retry interval value |
| `max_attempts` | int | ✅ | Max webhook delivery attempts |

### Response (Failure)
```json
{
  "success": false,
  "error": "400 Bad Request: Invalid webhook configuration"
}
```

### Notes
- Either `merchant_email` or `merchant_key` is mandatory. If both are provided, `merchant_key` takes precedence.
- The `merchant_key` field expects the Easebuzz sub-merchant **ID** (e.g., `S360DILA`) or the sub-merchant key returned from the Create Sub-Merchant API.
- Webhook URLs must be HTTPS.
- **KhanaBook endpoint**: `POST /admin/sub-merchants/wire/insta-collect-webhook`

---

## 17. WIRE: Get Sub-Merchant by Email

- **Base URL**: `https://wire.easebuzz.in`
- **Endpoint**: `GET /api/v1/merchants/retrieve/?key={key}&email={email}`
- **Content-Type**: `application/json`
- **Auth**: `Authorization: SHA-512("{key}|{salt}")` + `WIRE-API-KEY` header
- **Official Doc**: Easebuzz WIRE platform documentation

### Request Parameters
| Param | Description |
|-------|-------------|
| `key` | Parent merchant key |
| `email` | Sub-merchant registered email |

### Response (Success)
```json
{
  "success": true,
  "data": {
    "merchant": {
      "id": 4344,
      "email": "testsubmerchant@test.in",
      "kyc_status": true,
      "virtual_account": {
        "account_number": "000119170604174344",
        "bank_name": "RBL Bank"
      }
    }
  }
}
```

### Response (Failure)
```json
{
  "success": false,
  "error": "404 Not Found: No merchant found with email test@example.com"
}
```

### Notes
- The `email` query parameter is automatically URL-encoded by the client (using `UriComponentsBuilder`). Special characters in email addresses (like `+` or `.`) are handled safely.
- **KhanaBook endpoint**: `GET /admin/sub-merchants/wire/lookup-by-email?email={email}`

---

## 18. WIRE: Get Sub-Merchant by ID

- **Base URL**: `https://wire.easebuzz.in`
- **Endpoint**: `GET /api/v1/merchants/{submerchant_id}/`
- **Content-Type**: `application/json`
- **Auth**: `Authorization: SHA-512("{key}|{salt}")` + `WIRE-API-KEY` header
- **Official Doc**: Easebuzz WIRE platform documentation

### Path Parameter
| Param | Description |
|-------|-------------|
| `submerchant_id` | Easebuzz sub-merchant ID (e.g., `S360DILA`) |

### Response (Success)
```json
{
  "success": true,
  "data": {
    "merchant": {
      "id": 4344,
      "email": "testsubmerchant@test.in",
      "kyc_status": true,
      "virtual_account": {
        "account_number": "000119170604174344",
        "bank_name": "RBL Bank"
      }
    }
  }
}
```

### Response (Failure)
```json
{
  "success": false,
  "error": "404 Not Found: No merchant found with ID S360DILA"
}
```

### Notes
- The sub-merchant ID is appended directly to the URL path. Ensure it is URL-safe (alphanumeric, typically like `S360DILA`).
- **KhanaBook endpoint**: `GET /admin/sub-merchants/wire/lookup-by-id/{subMerchantId}`

---

## 19. WIRE: Payout Webhook Configuration

- **Base URL**: `https://wire.easebuzz.in`
- **Endpoint**: `PUT /api/v1/merchants/webhooks/`
- **Content-Type**: `application/json`
- **Auth**: `Authorization: SHA-512("{key}|{salt}")` + `WIRE-API-KEY` header
- **Official Doc**: Easebuzz WIRE platform documentation

### Request
```json
{
  "key": "PARENT_MERCHANT_KEY",
  "merchant_key": "SUB_MERCHANT_KEY",
  "webhook_conf": [
    {
      "event_type": "TRANSFER_INITIATED",
      "status": "enable",
      "url": "https://your-domain.com/api/v2/payments/easebuzz/payout/webhook",
      "interval_unit": "minutes",
      "interval_value": 1,
      "max_attempts": 2
    }
  ]
}
```

**Supported Event Types**: `TRANSFER_INITIATED`, `TRANSFER_STATUS_UPDATE`, `LOW_BALANCE_ALERT`, and more (full list in Easebuzz docs).

### Response (Success)
```json
{
  "success": true,
  "data": {
    "message": "Webhook configuration updated successfully.",
    "success": true
  }
}
```

### Request Body Parameters
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `key` | string | ✅ | Parent merchant key (from config) |
| `merchant_key` | string | ✅ | Sub-merchant identifier (Easebuzz ID like `S360DILA` or sub-merchant key) |
| `webhook_conf` | array | ✅ | Array of webhook configuration objects |

### Webhook Config Object
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event_type` | string | ✅ | Event to trigger on (e.g., `TRANSFER_INITIATED`) |
| `status` | string | ✅ | `"enable"` or `"disable"` |
| `url` | string | ✅ | HTTPS webhook callback URL |
| `interval_unit` | string | ✅ | Retry interval unit (`minutes`, `hours`, `days`) |
| `interval_value` | int | ✅ | Retry interval value |
| `max_attempts` | int | ✅ | Max webhook delivery attempts |

### Response (Failure)
```json
{
  "success": false,
  "error": "400 Bad Request: Invalid event_type"
}
```

### Notes
- Unlike the InstaCollect webhook (section 16), this endpoint does **not** accept a `merchant_email` field — only `merchant_key`.
- **KhanaBook endpoint**: `POST /admin/sub-merchants/wire/payout-webhook`
- The existing `POST /admin/sub-merchants/payout` endpoint handles initiating payouts via the dashboard API.
- This WIRE endpoint configures which payout events trigger webhooks.

---

## 20. WIRE: Get KYC Profile URL

- **Base URL**: `https://wire.easebuzz.in`
- **Endpoint**: `GET /api/v1/merchants/{submerchant_id}/kyc/url/`
- **Content-Type**: `application/json`
- **Auth**: `Authorization: SHA-512("{key}|{salt}")` + `WIRE-API-KEY` header
- **Official Doc**: Easebuzz WIRE platform documentation

### Path Parameter
| Param | Description |
|-------|-------------|
| `submerchant_id` | Easebuzz sub-merchant ID |

### Response (Success)
```json
{
  "success": true,
  "data": {
    "kyc_url": "https://kyc.easebuzz.in/...?access_key=..."
  }
}
```

### Response (Failure)
```json
{
  "success": false,
  "error": "404 Not Found: No KYC profile found for sub-merchant S360DILA"
}
```

### Notes
- **Different from** the existing Generate KYC Access Key API (section 3):
  - Generate KYC Access Key: Creates a *new* KYC portal session URL via `dashboard.easebuzz.in`.
  - Get KYC Profile URL (WIRE): Retrieves the *existing* KYC profile URL without creating a new session.
- KhanaBook endpoint: `POST /admin/sub-merchants/{id}/wire/kyc-profile-url`

---

## 21. WIRE: Get Sub-Merchant by Key

- **Base URL**: `https://wire.easebuzz.in`
- **Endpoint**: `GET /api/v1/merchants/{submerchant_key}`
- **Content-Type**: `application/json`
- **Auth**: `Authorization: SHA-512("{key}|{salt}")` + `WIRE-API-KEY` header
- **Official Doc**: Easebuzz WIRE platform documentation

### Path Parameter
| Param | Description |
|-------|-------------|
| `submerchant_key` | Sub-merchant key from Create Sub-Merchant API |

### Response (Success)
```json
{
  "success": true,
  "data": {
    "merchant": {
      "id": 4344,
      "email": "testsubmerchant@test.in",
      "kyc_status": true,
      "virtual_account": {
        "account_number": "000119170604174344",
        "bank_name": "RBL Bank"
      }
    }
  }
}
```

### Response (Failure)
```json
{
  "success": false,
  "error": "404 Not Found: No merchant found with key SUB_MERCHANT_KEY"
}
```

### Notes
- The `submerchant_key` is the key returned from the Create Sub-Merchant API response — this is **different** from the sub-merchant ID (`S360DILA`).
- **KhanaBook endpoint**: `GET /admin/sub-merchants/wire/lookup-by-key/{subMerchantKey}`

---

## WIRE Platform Summary

All 6 WIRE APIs share the same authentication mechanism:
- **Base URLs**: `https://wire.easebuzz.in` (production), `https://testwire.easebuzz.in` (sandbox)
- **Auth Header**: `Authorization: SHA-512("{key}|{salt}")` (produces a **lowercase hex-encoded** SHA-512 digest)
- **Required Header**: `WIRE-API-KEY` (your WIRE-specific API key from Easebuzz)

### Error Handling Pattern
All WIRE client methods wrap API calls in try-catch blocks. When an API call fails:
- The response body from Easebuzz is returned if available (with `success: false` and an `error` string).
- If a network/connection error occurs, the client returns `{"success": false, "error": "<exception message>"}`.
- HTTP 4xx/5xx errors from the WIRE server include status codes in the error message (e.g., `"404 Not Found: ..."`).

### ID vs Key vs Email: Lookup Identifier Reference
| Lookup Type | Field | Example | Source |
|-------------|-------|---------|--------|
| By Email | `email` | `submerchant@example.com` | Sub-merchant registration |
| By ID | `submerchant_id` | `S360DILA` | Create Sub-Merchant API response |
| By Key | `submerchant_key` | (UUID-like string) | Create Sub-Merchant API response |

The Easebuzz **sub-merchant ID** (short alphanumeric like `S360DILA`) and **sub-merchant key** (longer UUID-like string) are different identifiers returned by the Create Sub-Merchant API. The lookup-by-Key API searches using the key, while lookup-by-ID searches using the ID.

KhanaBook backend exposes these through a dedicated `EasebuzzWireApiClient` class and prefixing all WIRE endpoints with `/wire/` under `/admin/sub-merchants/`.

---

## Sub-Merchant Onboarding Flow

```
1. Create sub-merchant draft in local DB
       │
2. POST /merchant/v1/submerchant/create  (Create Sub-Merchant API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/af94640eeae86-create-sub-merchant-api
       │  Returns: submerchant_id
       ▼
3. POST /submerchant/v1/generate_kyc_access_key  (Generate KYC Access Key API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/2b48a38b084b9-generate-sub-merchant-kyc-access-key-api
       │  Returns: KYC portal URL
       ▼
4. Sub-merchant uploads KYC docs via portal
       │
5. Webhook received: {"status":"1","data":{"submerchant_id":"...","status":"True/False/Pending"}}
       │
6. POST /split/v1/create  (Create Split Label API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/e383e3fb60ce3-create-api
       │
7. For each transaction → POST /post-split/v1/create/  (Split Create API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2
       │
8. POST /post-split/v1/retrieve/  (Split Retrieve API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api
```

## Payment & Refund Flow

```
Customer pays via Android POS
       │
1. POST /payment/initiateLink  (Initiate API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api
       ▼
2. Customer completes payment on Easebuzz checkout
       │
3. Webhook / Polling: POST /transaction/v2.1/retrieve  (Status API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/j6v1nmy74m325-status-api
       ▼
4. If success → trigger Post-Split Create
       │
5. If refund needed → POST /transaction/v2/refund  (Refund API v2)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2
       ▼
6. Poll refund status → POST /transaction/v2/refund_status  (Refund Status API)
       │  Official: https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api
```

## Our Backend Endpoints

| Method | Path | Action | Underlying Easebuzz API |
|--------|------|--------|------------------------|
| `GET` | `/admin/sub-merchants` | List all | — |
| `GET` | `/admin/sub-merchants/{id}` | Get by ID | — |
| `POST` | `/admin/sub-merchants` | Create draft | — |
| `PUT` | `/admin/sub-merchants/{id}` | Update draft | — |
| `POST` | `/admin/sub-merchants/{id}/assign-id` | Manually assign sub_merchant_id | — |
| `PUT` | `/admin/sub-merchants/{id}/status` | Update status | — |
| `POST` | `/admin/sub-merchants/{id}/submit-to-easebuzz` | Create in Easebuzz | Create Sub-Merchant API |
| `POST` | `/admin/sub-merchants/{id}/update-on-easebuzz` | Update in Easebuzz | Update Sub-Merchant API |
| `POST` | `/admin/sub-merchants/{id}/kyc-access-key` | Generate KYC portal URL | Generate KYC Access Key API |
| `POST` | `/admin/sub-merchants/{id}/verify-otp` | Verify OTP | Confirm OTP / Verify OTP API |
| `POST` | `/admin/sub-merchants/{id}/resend-otp` | Resend OTP | Resend OTP API |
| `POST` | `/admin/sub-merchants/{id}/split-label` | Create split label | Create Split Label API |
| `POST` | `/admin/sub-merchants/{id}/cancel-transaction` | Cancel a pending transaction | Cancel Transaction API |
| `POST` | `/admin/sub-merchants/{id}/payout` | Initiate payout to sub-merchant | Payout API v2 |
| `POST` | `/admin/sub-merchants/{id}/split-retrieve` | Retrieve split configuration | Post-Transaction Split — Retrieve |
| `POST` | `/admin/settlements/on-demand` | Request on-demand settlement | On-Demand Settlement API |
| `POST` | `/api/v2/payments/easebuzz/create-order` | Initiate payment | Initiate API |
| `GET` | `/api/v2/payments/easebuzz/status/{billId}` | Check payment status | Status API (v2.1) |
| `POST` | `/api/v2/payments/easebuzz/refund/{billId}` | Raise refund | Refund API v2 |
| `GET` | `/api/v2/payments/easebuzz/refund-status/{billId}` | Check refund status | Refund Status API |
| `POST` | `/api/v2/payments/easebuzz/webhook` | Receive Easebuzz webhooks | All webhook events |
| `POST` | `/api/v2/payments/easebuzz/submerchant-webhook` | Receive KYC webhooks | Sub-merchant webhook |
| `GET` | `/admin/sub-merchants/wire/lookup-by-email` | Lookup SM on WIRE by email | WIRE: Get SM by Email |
| `GET` | `/admin/sub-merchants/wire/lookup-by-id/{subMerchantId}` | Lookup SM on WIRE by ID | WIRE: Get SM by ID |
| `GET` | `/admin/sub-merchants/wire/lookup-by-key/{subMerchantKey}` | Lookup SM on WIRE by key | WIRE: Get SM by Key |
| `POST` | `/admin/sub-merchants/{id}/wire/kyc-profile-url` | Get KYC profile URL from WIRE | WIRE: Get KYC Profile URL |
| `POST` | `/admin/sub-merchants/wire/insta-collect-webhook` | Configure InstaCollect webhook | WIRE: InstaCollect Webhook Config |
| `POST` | `/admin/sub-merchants/wire/payout-webhook` | Configure payout webhook | WIRE: Payout Webhook Config |

---

## 22. Settlement Retrieval & InstaCollect Disbursement Webhooks (2026-10-22)

This section records the contracts supplied by Easebuzz on 2026-10-22 and what the code now does
with them. Anything not confirmed there is marked as such rather than invented.

### 22.1 Settlement / payout reconciliation retrieval

- **Endpoint**: `POST https://dashboard.easebuzz.in/settlements/v1/retrieve`
- **Content-Type**: `application/json`
- **Hash**: `sha512(merchant_key|start_date|end_date|salt)`; dates `DD-MM-YYYY`, `end_date >= start_date`
- **Auth alternative**: a `token` (1-hour validity, Token Authentication API) may replace `hash`
- **Pagination**: 20 rows per page by default, `page_size` maximum 500, and the `next` value must be
  echoed back in `page` until `next` is null
- **Sub-merchant filter**: `submerchant_id`, used when the sub-merchant's payout UTR is required
- **Response**: `{ next, previous, count, data: [ { payout_id, paid_out, payout_amount,
  payout_actual_date, bank_transaction_id, peb_transactions: [...], peb_refunds: [...] } ], status }`

`payout_amount` is the bank credit for the whole payout. It is a different level from a
`peb_transactions[]` row and must never be summed with, or substituted for, a transaction amount.

KhanaBook behaviour (`EasebuzzApiClient.retrieveSettlements`):
- sends `page_size: 500`, follows `next` through `page`, and returns one merged `data` array plus
  `pagesFetched` / `truncated`. A truncated period is logged at ERROR and never reported as
  reconciled.
- `EasebuzzReconciliationService.flattenPayouts` lifts `peb_transactions[]` out of each payout before
  matching, so bill matching still happens at transaction level. `peb_settlement_amount` is used as
  the restaurant's share when `payout_amount` is absent from the transaction row.

### 22.2 InstaCollect disbursement webhooks

Events: `REFUND_INITIATED`, `REFUND_STATUS_UPDATE`, `SETTLEMENT_INITIATED`, `SETTLEMENT_STATUS_UPDATE`.
The body is `{ event, data: {...} }`; the digest arrives in `data.Authorization` (not `hash`).

| Event family | Hash sequence |
|---|---|
| Refund disbursement | `sha512(key\|refund\|unique_transaction_reference\|amount\|status\|salt)` |
| Settlement disbursement | `sha512(key\|settlement\|unique_transaction_reference\|amount\|status\|salt)` |

`EasebuzzWebhookService.verifyPayoutHash` now verifies this sequence (reading `hash` first, then
`Authorization`) before the older `key|payout_id|salt` and Transfer V2 sequences.

**Open, deliberately fail-closed**: `SETTLEMENT_INITIATED` is documented with a `null`
`unique_transaction_reference`. The hash interpolates that value, so a null-UTR event cannot be
reproduced locally; such events are rejected with a warning instead of being accepted unverified.
Easebuzz must confirm how a null-UTR initiation is signed.

**Deduplication** should key on `data.id` (the disbursement id). No local table is keyed on it yet —
`handlePayoutWebhook` logs the disbursement id and warns when no payout row matches, rather than
acknowledging silently.

### 22.3 Not confirmed by this reference

| Item | Status |
|---|---|
| Transaction Status API endpoint/headers/hash/schema | Still unconfirmed. Code keeps `POST /transaction/v2.1/retrieve` with a `/transaction/v2/retrieve` fallback on 404/405 |
| Refund + refund-status content type | **Conflict**: this reference shows `application/json`; Easebuzz's PHP kit posts both form-urlencoded, which is what the code sends (see `EasebuzzRefundStatusContractTest`, `EasebuzzRequestContractTest`). Resolve with Easebuzz before switching |
| InstaCollect ack code / retry policy / delivery headers | Not established. Do not reuse the payment-gateway webhook retry assumptions |
| KYC webhook key selection | Documented as `key\|submerchant_id\|salt`; the "which key" question is still open |
