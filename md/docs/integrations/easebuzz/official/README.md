# Easebuzz Official Documents — Vendored

Every file in this folder was exported from Easebuzz's own documentation portal
(`https://docs.easebuzz.in`) on **2026-10-22** and is committed here so the
integration's contracts are versioned with the code that consumes them.

Nothing in here is KhanaBook's interpretation. For the interpreted view — what
we need, what we call, what is broken — read
[`../OFFICIAL_API_CATALOGUE.md`](../OFFICIAL_API_CATALOGUE.md).

## Contents

| Path | What it is |
|---|---|
| [`openapi/`](./openapi) | Raw OpenAPI exports, 17 files. The authority for paths, fields and examples. |
| [`articles/`](./articles) | Raw guide pages: overviews, webhooks, lifecycle and business rules. |
| [`articles/REQUEST-SCHEMAS.txt`](./articles/REQUEST-SCHEMAS.txt) | Flattened request-field dump across the payload types. |
| [`manifest.json`](./manifest.json) | Machine-readable operation list: path, base host, hash sequence, required fields, idempotency key, enablement. |

`manifest.json` is the artifact to read programmatically during implementation.
It was compiled against the files in `openapi/`, and its `sources` array names
them.

## The portal hosts two separate products

| Product | Stoplight project | Branch | API base | Auth |
|---|---|---|---|---|
| Payment Gateway | `cHJqOjEyODU1Nw` (`prj:128517`) | `PG_Phase_4` | `dashboard.easebuzz.in` | SHA-512 `hash` in the body |
| Neobanking / WIRE | `cHJqOjE1NDA0Mw` (`prj:154043`) | `v1.0.25` | `wire.easebuzz.in` | SHA-512 in an `Authorization` header + `WIRE-API-KEY` |

Files prefixed `neobank-` in `articles/`, and `Wire-Stoplight`, `Sub-Merchant`,
`Beneficiary-Accounts`, `Contacts`, `InstaCollect` in `openapi/`, belong to the
second project. Do not mix the two auth schemes.

### Checkout host vs dashboard host

The Payment Gateway is served from **two** hosts. This is not cosmetic:

- `dashboard.easebuzz.in` / `testdashboard.easebuzz.in` — onboarding, refunds,
  reports, payment links, status.
- `pay.easebuzz.in` / `testpay.easebuzz.in` — `payment/initiateLink`,
  `payment/v2/auth/capture/`, `payment/v1/auth/reversal/`.

The same checkout paths on the dashboard host answer nginx **405**. In
`manifest.json` these operations carry `"base": "checkout"`.

## OpenAPI export → file map

Payment Gateway:

| Stoplight node | File |
|---|---|
| `ai079zykm502x` | `Submerchant-APIs.yaml` |
| `5db93e29fad89` | `Post-Transaction-Split.yaml` |
| `643f40bc5eae3` | `Ondemand-settlement-APIs.yaml` |
| `yl0zfrnpk63sb` | `Settlement-APIs.yaml` |
| `oudxtp83258l9` | `Refund-APIs.yaml` |
| `ws7va0ivtpb08` | `Transaction-APIs.json.spec` |
| `viyex0lwoda66` | `Initiate-Transaction-APIs.yaml` |
| `viyex0lwoda67` | `Auth-and-Capture.yaml` |
| `j7zy0xpyl26ts` | `Easy-Collect-APIs.yaml` |
| `3c0hhp2sehid2` | `Smart-Pay.yaml` |
| `fatyf4r58zdj0` | `Token-Authentication-API.yaml` |
| `4xdf15v7z2j1z` | `Native-OTP.yaml` |

WIRE / neobanking:

| Stoplight node | File |
|---|---|
| `a3d38f4e88cc8` | `Wire-Stoplight.yaml` |
| `5zi631w3bkjct` | `Sub-Merchant.yaml` |
| `559e897b47b9f` | `Beneficiary-Accounts.yaml` |
| `6a2346d5c896f` | `Contacts.yaml` |
| `5n3xzrk1j39v4` | `InstaCollect.yaml` |

## Article index

Payment Gateway guides:

| File | Read it for |
|---|---|
| `integration-types.md` | Hosted vs seamless integration shapes |
| `transaction-flow.md` | End-to-end payment sequence |
| `submerchants-overview.md` | Sub-merchant model, what the master controls |
| `easycollect-overview.md` | EasyCollect link service |
| `smartpay-overview.md` | SmartPay link service |
| `settlement-overview.md`, `settlement-process.md` | Settlement cadence and payout reporting |
| `refund-overview.md`, `refund-process.md` | Refund lifecycle and refund states |
| `webhooks-overview.md` | Delivery contract, retries, envelope |
| `webhook-transaction.md`, `webhook-refund.md`, `webhook-payout.md`, `webhook-kyc-approval.md` | Per-event payload and hash |
| `seamless-integration-merchant-hosted.md` | Seamless card flow |
| `easycollect-submerchant-link.md`, `easycollect-split-payments-link.md` | EasyCollect variants and their different hash sequences |
| `discount-coupons.md`, `soundbox.md`, `pos.md`, `mobile-app-web-view.md`, `team-members.md` | Adjacent products, not used by KhanaBook |
| `testing-credentials.md` | UAT test data |
| `go-live-steps.md`, `faq-s.md` | Go-live checklist, FAQ |

WIRE / neobanking guides (disabled on this account, kept for reference):

| File | Read it for |
|---|---|
| `neobank-submerchants-overview.md`, `neobank-submerchant-codes.md` | WIRE onboarding and code tables |
| `neobank-transfers-overview.md`, `neobank-transfers-detail.md` | Payout transfers |
| `neobank-webhooks-overview.md`, `neobank-kyc-status-webhook.md`, `neobank-payout-webhook.md` | WIRE webhooks |

## Caveats when trusting these files

- **Fields in `x-examples` are not schema.** `kyc_details`,
  `business_details` and `submerchant_deduction_percentage` on create appear
  only in examples, never in the JSON Schema `properties`. Treat them as
  unverified until Easebuzz confirms them in writing.
- **Node IDs are branch-specific; slugs are stable.** If the docs move, re-pull
  and diff before trusting a hash sequence.
- **No product page is complete.** Easebuzz's documents contradict themselves in
  places (refund status naming, cancel paths). Where they do, the catalogue
  records the conflict rather than picking a winner.
- **UAT behaviour differs from the documents.** Confirmed differences are in
  [`manifest.json`](./manifest.json) → `uatVerification` and in
  [`../OFFICIAL_API_CATALOGUE.md`](../OFFICIAL_API_CATALOGUE.md) §7.

## How this was captured, and how to refresh

The portal is a client-rendered Stoplight app behind Cloudflare; plain HTTP
returns an empty shell. The content comes from Stoplight's API, which only
answers from inside the page origin:

```
https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/table-of-contents?branch=PG_Phase_4
https://stoplight.io/api/v1/projects/cHJqOjEyODU1Nw/nodes/<nodeId>?branch=PG_Phase_4
```

OpenAPI files come from the export button's backend:

```
https://stoplight.io/api/v1/projects/easebuzz/payment-gateway/nodes/apis/<file>?fromExportButton=true&snapshotType=http_service&deref=optimizedBundle
```

Steps:

1. Open `https://docs.easebuzz.in/docs/payment-gateway` in Chrome and wait 6–8 s
   for hydration.
2. In DevTools, `fetch()` the TOC URL, then each node URL. Article nodes return
   markdown in `data`; operation nodes return a JSON **string** that needs one
   `JSON.parse`.
3. For each `http_service` node, read `.uri` and fetch the export URL.
4. Decode the DevTools JSON wrappers into real `.yaml` / `.md` with
   `easebuzz-mcp/scripts/decode-specs.mjs` and `split-articles.mjs` (source:
   `C:\Users\nandh\Desktop\easebuzz-mcp`).

No credentials were used — public documentation only.
