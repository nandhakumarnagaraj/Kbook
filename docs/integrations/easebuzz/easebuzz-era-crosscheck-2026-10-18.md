# Easebuzz ERA (AI assistant) — cross-check before CPV hardening release

Date: 2026-10-18
Status: prompts drafted, **not yet run**

## What this is

ERA is Easebuzz's **AI chatbot**, not a human at `pgsupport@`. Earlier integration
decisions (GST optional, Aadhaar portal-only, MCC locked at creation, Public Limited /
OPC supported) are currently recorded as *"ERA confirmed, 2026-10-17"* and are baked into
shipped code — `SubMerchantService.submitToEasebuzz` and
`EasebuzzApiClient.mccCodeForFormat`.

That is a weaker source than the docs imply. An AI assistant can answer confidently and be
wrong, and nothing about its wording is contractual. This file exists to re-run the open
assumptions against ERA and to record the answers verbatim.

## How to use

- Paste **one block at a time**. ERA handles a single focused topic far better than a wall
  of nested questions, and a short prompt is easier to audit.
- Record the answer **verbatim** in the log at the bottom, with the date.
- Ask ERA to cite the **doc URL** for each answer. An answer with no citation is a
  hypothesis, not a fact.

## How to read the answers

- **Agrees with 2026-10-17** → the earlier answer survives; still an AI source, so treat as
  "believed true, unconfirmed by a human".
- **Contradicts 2026-10-17** → the earlier answer was possibly hallucinated. Since MCC
  immutability and GST-optional are already in code, escalate to human support
  (`pgsupport@easebuzz.in`) and the assigned ERA contact before release. Do **not** silently
  follow the newer answer.
- **"I don't have that information"** → probably a real gap in the docs. Escalate.

The two release-blocking items are **1 (MCC)** and **2 (business-proof list)**.

---

## Prompt 1 — MCC per outlet format (BLOCKING)

```
We are a payment aggregator parent merchant (parent + one sub-merchant per restaurant
outlet) and we must pick a sub-merchant MCC at creation.

Answer precisely, and cite the documentation URL for each answer:
1. Which MCCs are enabled for sub-merchant creation on a restaurant account? Specifically,
   are 5812 (restaurants), 5814 (fast food / QSR), 5813 (bars / drinking places) and
   5811 (caterers) all accepted?
2. Which field of POST /submerchant/create carries the MCC, and does it take the plain
   4-digit code?
3. Does MCC 5813 require a liquor/excise licence during CPV, such that a restaurant with
   both a kitchen and a bar should be created under 5812 instead?
4. Once a sub-merchant is created, can its MCC be changed in any status (including
   PENDING_KYC with no payments taken)? If not, is there any assisted correction short of
   full recreation?
5. Can we read back the MCC that was sent, via the retrieve API, the dashboard, or the
   MERCHANT_KYC_APPROVAL webhook?
```

## Prompt 2 — Business-proof document types (BLOCKING)

```
For sub-merchant KYC, what is the authoritative list of accepted *business proof* document
types?

For context, we currently require, for a sole proprietorship: at least one business/address
proof, two proofs must be of different document types if two are given, and a rent/lease
agreement is not accepted as the only proof. We intend to enforce this list: Shop &
Establishment certificate, Import/Export certificate, trade licence, Udyam (URC)
certificate, utility bill (electricity/water/gas).

Answer and cite the documentation URL:
1. Is that list complete and correct for sub-merchant onboarding? Any type to add or remove?
2. Is a rent/lease agreement accepted when paired with a second accepted proof?
3. Is one proof sufficient, or are two distinct proofs mandatory, for a sole proprietorship?
4. For partnership, LLP, private limited and trust, are business proofs required at all, or
   only the entity documents?
5. When CPV fails on document grounds, is the rejection reason exposed in the
   MERCHANT_KYC_APPROVAL webhook as a code, or is it free text / email only?
```

## Prompt 3 — Webhook source IP ranges

```
We run an IP allowlist on our webhook endpoints and need the published source ranges for
production.

1. What are the source IPs or CIDRs for payment, refund, sub-merchant and payout webhooks
   in production? Cite the published list.
2. Are sandbox callback IPs the same or different?
3. Do these ranges change, and is there advance notice if they do?
```

## Prompt 4 — GST omitted from the create payload

```
GST is optional for our sub-merchants. When a merchant is not GST-registered, should the
POST /submerchant/create payload:
(a) omit the GSTIN field entirely, or
(b) send it as an empty string?
We currently omit it (the field is only set when a GSTIN exists). Confirm which the API
expects, and that omitting it will not fail payload validation at create time.
```

## Prompt 5 — Refund status values

```
Our refund webhook handler treats both status "processed" (event REFUND_PROCESSED) and the
legacy value "refunded" as "money returned".
Confirm: is "processed" the current production value for a completed refund, and will
"refunded" continue to be sent for older callbacks, or has it been retired?
Cite the documentation URL.
```

## Prompt 6 — KYC status signal

```
We treat the MERCHANT_KYC_APPROVAL webhook as the only approval signal, with no polling API
and no Aadhaar-specific webhook.

1. Is kyc_status a strict boolean?
2. Does a CPV rejection always arrive on MERCHANT_KYC_APPROVAL with kyc_status=false, or can
   a rejection be communicated by email only?
```

## Prompt 7 — FSSAI and bars

```
We require a 14-digit FSSAI licence number (not an acknowledgement) for every restaurant
outlet, including bars.
Confirm whether an outlet onboarded under MCC 5813 (bar / drinking place) needs any licence
beyond FSSAI, so we can block it before submission rather than fail CPV.
```

## Prompt 8 — Recreation of a wrong-MCC sub-merchant

```
We understand a sub-merchant's MCC cannot be changed, so the only fix is recreation.
Confirm:
1. A recreated sub-merchant carries over no KYC documents from the old one.
2. Settlements already made to the old sub-merchant stay attached to the old
   submerchant_id, so reconciliation is manual on our side.
3. There is no limit or fee on creating a replacement sub-merchant for the same outlet.
```

---

## Prompt 9 — KYC document ingest API (BLOCKING for docs accuracy)

```
Does Easebuzz provide an API that submits a sub-merchant's KYC documents directly, instead of
the merchant uploading them on the hosted KYC portal?

For context, our code calls this and we need to know whether it is current, legacy, or never
supported:
  POST {dashboard_base_url}/submerchant/v1/upload_kyc_documents
  Content-Type: multipart/form-data
  fields: submerchant_id, document_type, file, hash (SHA-256 of the file bytes)
  document_type values we send: ID_PROOF, BANK_PROOF, ADDRESS_PROOF, FSSAI_LICENSE

Answer and cite the documentation URL for each answer:
1. Is /submerchant/v1/upload_kyc_documents a supported endpoint? If yes, is it in the current
   published documentation, or is it a legacy operation no longer documented?
2. If it is not supported, what is the correct replacement for submitting documents by API?
   Give the exact endpoint, method, content type, and every field name.
3. Is `hash` required, and if so what is the exact algorithm and the string it is computed
   over (file bytes only, or a key|salt sequence)?
4. Which document_type values are accepted, and do they differ per entity type
   (sole proprietorship vs partnership vs LLP vs private limited vs trust)?
5. When documents are submitted by API rather than the portal, does the merchant still need
   to verify OTP and sign in to the hosted KYC portal, and does CPV still apply?
6. Does a document submitted by API retain the same evidentiary status as one uploaded on the
   portal, and does Easebuzz retain the file, or must the parent merchant keep its own copy
   for audit?
7. Does an API document upload change kyc_profile_status or trigger any webhook?
   Your marketing page for Express Onboarding states that merchants "submit these documents
   through the KYC API" - confirm whether that API is the endpoint above or a different one.
```

---

## Answer log — run 2026-10-18

Answers received from ERA (AI assistant). Treat as leads, not facts.

| # | Topic | Answer | Source given | Verdict |
|---|---|---|---|---|
| 1 | MCC per outlet format | All of 5812/5814/5813/5811 accepted; MCC goes in `sub_merchant_business_nature`; 5813 needs a liquor licence, prefer 5812 for kitchen+bar; immutable post-creation; readable in the approval webhook | KYC docs + sub-merchant APIs, but **"unknown"** on immutability | **Wrong on the field name and the webhook** (see below) |
| 2 | Business-proof list | Our five types are "complete and authoritative"; rent accepted when paired; **two distinct proofs mandatory**; no proofs for non-proprietorships; rejection reason is free text | KYC docs | **Wrong on completeness and on "two mandatory"** |
| 3 | Webhook source IPs | `13.233.108.64/28`, `15.206.144.0/24`; sandbox `13.233.108.80/28` | All **"unknown - needs human confirmation"**, one explicitly "example" | **Fabricated — do not use** |
| 4 | GST omitted from payload | Omit the GSTIN field entirely; empty strings may fail validation | Sub-merchant APIs | **Matches our code — usable** |
| 5 | Refund status values | `processed` is current; `refunded` is retired | **"unknown"** | Harmless either way (we accept both) |
| 6 | KYC status signal | `kyc_status` strict boolean; rejections always arrive on the webhook | KYC webhooks | Matches the published schema |
| 7 | FSSAI for bars | FSSAI mandatory; **no** additional licence for 5813 | KYC docs | **Contradicts answer 1** |
| 8 | Recreation rules | No document carryover; settlements stay on the old id; no fee | **"unknown"** on the fee | Matches the runbook |

Prompt 9 (KYC document ingest) was **run 2026-10-18 — inconclusive**; see the subsection below.
It was added after the code/doc contradiction at `EASEBUZZ_LIFECYCLE.md:7` and
`SubMerchantService:704,856` was found: we call
`POST /submerchant/v1/upload_kyc_documents`, while our own API reference
(`sub-merchant-apis.md`, 21 endpoints) and the published onboarding flow document only the
hosted-portal route.

### Second round — identity, bank, expiry, retries

| Question | Answer | Consequence |
|---|---|---|
| Is `data.id` the `submerchant_id`? | Yes, same value | The `:392-395` overwrite is harmless |
| Is the webhook email unique? | **No** — outlets may share it | `findByContactEmail` returning `Optional` throws on a duplicate → **fixed 2026-10-18** |
| Can `kyc_status=true` with `bank_status=false`? | Yes, and the merchant cannot take payments until both are true | Activation bug → **fixed 2026-10-18** |
| What happens at `kyc_expiry_date`? | Status reverts, no webhook, must poll | **Still open** — nothing stored, no poll job |
| Retry / replay behaviour | 2–3 attempts over ~1 hour, then lost permanently; no replay; a Get Sub-merchant Details API exists | Missed approvals are only recoverable by polling |

### Prompt 9 answer — run 2026-10-18 (KYC document ingest)

ERA's reply, distilled. Verdicts are checked against code, not accepted on trust.

| Question | ERA's answer | Verdict |
|---|---|---|
| Is `/submerchant/v1/upload_kyc_documents` supported? | "not documented in the current Easebuzz API references. It may be legacy or deprecated." | **Non-answer** — never states supported or unsupported |
| Correct replacement | `POST /submerchant/v1/generate_kyc_access_key` + hosted portal | Answers a different question; the portal route was never in doubt |
| Hash for the access key | SHA-512 of `merchant_key\|sub_merchant_id\|salt` | **Wrong.** Code hashes `key\|subMerchantId\|name\|email\|phone\|salt` (`EasebuzzApiClient:541-552`), matching `sub-merchant-apis.md` §3. Following ERA here would break the call |
| Accepted `document_type` values | "the portal accepts ID_PROOF, BANK_PROOF, ADDRESS_PROOF…" | **Category error** — `document_type` is an upload-API field; the portal has no such parameter |
| Evidentiary status / retention | "Documents uploaded via the portal have the same legal status as API submissions" | **Self-contradictory** — it denied an API two answers earlier |
| Express Onboarding "KYC API" | "likely refers to the KYC Access Key API… or the Submerchant Creation API" | Speculation, no citation, contradicts the vendor's own FAQ wording |
| OTP + portal sign-in still required | Yes | Consistent with `submitToEasebuzz` → `verifyOtp` → `kyc_access_key` |
| Webhooks | `MERCHANT_KYC_APPROVAL` unchanged | Consistent with the published payload |

**Conclusion:** the endpoint stays **unverified**. ERA could not confirm it exists and could
not confirm it is dead. Its one falsifiable claim (the hash sequence) is wrong, which does not
increase confidence in the rest.

**Consequence, unmitigated in code:** if the endpoint is dead, `SubMerchantService:293`
throws `kyc_upload_rejected` for every app-side KYC upload, and that path has no fallback to
the portal link. There is no test covering it, so a failure would surface first in front of a
restaurant owner. Next step is human confirmation from `pgsupport@easebuzz.in` (or a sandbox
call), not another ERA round.

#### Structured re-ask — verdicts (2026-10-18)

Second pass with forced `CONFIRMED / UNCONFIRMED / NOT SUPPORTED`. Every item that matters came
back unconfirmed or unsupported, which is itself the answer.

| Item | ERA verdict | Checked against |
|---|---|---|
| API document upload (1a–1c, 1e, 1f) | UNCONFIRMED / **NOT SUPPORTED** | Agrees with `sub-merchant-apis.md` (21 endpoints, no upload) and `FEATURE_DOCS.txt:244` ("legacy") |
| `sub_merchant_mcc_code` is a create field (2a) | UNCONFIRMED — absent from the schema | **Corroborated**: `sub-merchant-apis.md` contains no `mcc` at all, yet `EasebuzzApiClient:449` sends one |
| MCC readback and correction (2c, 2d) | UNCONFIRMED | The approval payload carries no MCC (see below) |
| `kyc_status=true` with `bank_status=false` (3a) | CONFIRMED | Already fixed 2026-10-18 |
| Get Sub-merchant Details API (3c) | **NOT SUPPORTED** | **Contradicted**: `GET /api/v1/merchants/{id}/` exists (`sub-merchant-apis.md` §18) and returns `kyc_status` + `virtual_account`. It does **not** return `bank_status` or MCC. `EasebuzzApiClient` never calls it |
| Resubmitting a REJECTED sub-merchant (4a) | UNCONFIRMED | Our `/resubmit` pushes an update; no documented resubmission contract |
| Behaviour at `kyc_expiry_date` (4b) | UNCONFIRMED | Our daily expiry job is a local assumption |
| Webhook source IPs (5a, 5b) | UNCONFIRMED | Allowlist stays disabled |
| Retry policy (5c) | CONFIRMED — 10 s timeout, no replay tool | Single AI citation; sandbox-test before relying on it |

**What three independent sources now agree on:** there is no documented API for KYC document
upload. Our call at `EasebuzzApiClient:740` targets an endpoint that appears in no vendor doc,
in no ERA answer, and not in our own 21-endpoint reference. The only consistent description is
`FEATURE_DOCS.txt:244`: a legacy operation.

That resolves the plan's branch: treat the API upload as **unsupported by contract**, make it
non-fatal with a portal fallback (Step 1), and restore it only if a human confirms it is live.

Note that ERA's one `NOT SUPPORTED` verdict that we can falsify is wrong — §18 of our own
reference documents a sub-merchant lookup — so even a negative verdict must be checked before
anything is deleted on its authority.

#### Settled by direct probe — 2026-10-18 (no credentials required)

Three unauthenticated requests per host, one file, fake ids. The documented endpoint is the
control; a route that cannot exist is the negative control.

| Host | `generate_kyc_access_key` (control) | `upload_kyc_documents` | `definitely_not_a_route` |
|---|---|---|---|
| `testdashboard.easebuzz.in` | **400** `{"status":false,"error_desc":"Please enter proper value for ['hash']"}` | **404** HTML `Not Found` | **404** HTML `Not Found` |
| `dashboard.easebuzz.in` | **400** same JSON | **404** HTML `Not Found` | **404** HTML `Not Found` |

`upload_kyc_documents` is byte-identical to a route that certainly does not exist, while a
route that does exist answers with application JSON even for a bad hash. **The endpoint is not
deployed on either host.**

Consequences:

- The app's KYC upload path has never worked. Before 2026-10-18 every attempt threw
  `kyc_upload_rejected`; after the fallback it returns `portal_required` — correct outcome,
  but reached through a guaranteed 404 and a wasted round trip.
- The docs line `EASEBUZZ_LIFECYCLE.md:7` ("no document-ingest API") is now **verified true**,
  and the comments at `SubMerchantService:704`, `:856` are correct rather than stale.
- ERA's "may be legacy or deprecated" was as close as it got; three rounds produced nothing a
  single unauthenticated request did not settle in seconds.

Residual possibility: the operation could exist on another host or path (WIRE, a versioned
prefix). Nothing in our reference or any ERA answer suggests one, and no credential was used,
so this rules out *these two paths on these two hosts*, not the existence of any upload API.

### Contradicted by the published documentation

Where an ERA answer disagrees with the Easebuzz docs, the docs won:

- **MCC field name.** ERA says the MCC goes in `business_details.sub_merchant_business_nature`.
  We already use that key for the **entity type** (`SOLE PROPRIETOR`, `PARTNERSHIP FIRM`, …).
  Our MCC is sent as `sub_merchant_mcc_code`, a key that appears in neither ERA's answer nor
  our local API reference — **still unverified**, and the one release-blocking unknown.
- **MCC in the webhook.** ERA says the approval payload carries `business_details`. The
  published payload does not: it has `id`, `virtual_account`, `kyc`, `commission_charges`,
  `insta_primary_vpa`, `kyc_status`, `bank_status`, `kyc_url`, `kyc_profile_status`,
  `kyc_expiry_date`, `email`, `phone`, `name`. No MCC anywhere.
- **"Two distinct proofs mandatory."** The docs say two shall be obtained **but** accept one
  proof routed to manual CPV: *"If only one document submitted as BusinessProof, CPV shall be
  carried out…"*. Our one-proof gate is doc-compatible; do **not** tighten it.
- **"Your list is complete."** The documented list has nine types; our five omitted sales/
  income tax returns, GST/CST/VAT certificate, Sales Tax / Service Tax / Professional Tax
  registration, and full ITR. Enforcing our five would have rejected valid merchants.
- **Source IPs.** Self-labelled example/unknown — keep the allowlist disabled.

Also note ERA earlier stated there is **no polling API**, then later pointed at a Get
Sub-merchant Details API. Treat its contract answers as leads requiring human confirmation.

## After the answers

- Fold confirmed answers into `../../integrations/easebuzz/EASEBUZZ.md` and
  `EASEBUZZ_LIFECYCLE.md`, **relabelled** as `ERA (AI assistant)` rather than
  "confirmed in writing", so the strength of the evidence is visible.
- If the business-proof list (2.1) is confirmed, implement the deferred allowlist half of
  Change 3 — the code today only rejects rent/lease-as-only-proof and duplicate types; it
  cannot reject an off-list document type.
- If MCC immutability (1.4) is contradicted, flag `mccCodeForFormat` and the recreation
  runbook in `EASEBUZZ_LIFECYCLE.md` §14 as unverified before release.
