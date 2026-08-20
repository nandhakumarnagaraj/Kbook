# KhanaBook × Easebuzz ERA — Q&A Workbook

**Date:** 2026-08-17 · **Author:** KhanaBook engineering (audit + ERA consultation tracker)
**Status legend:**
- **[DOC]** — answered from official Easebuzz sources (docs.easebuzz.in, official SDK source code, easebuzz.in pages). Verified 2026-08-17.
- **[ERA]** — already confirmed via Easebuzz ERA and baked into `EasebuzzPaymentService.java` (see `ERA-CONFIRMED (2026-08-17)` comment at :53-59).
- **[ERA-NEEDED]** — cannot be confirmed from public material; must be asked via ERA (support.easebuzz.in / dashboard ERA chat / account manager).
- **[CODE]** — verified against the KhanaBook codebase on 2026-08-17 (read-only re-check).

## 0. Verified current codebase state (2026-08-17 re-check — corrections to earlier claims)

The re-check found the Easebuzz integration is **much more complete than the original audit snapshot**. Corrected facts:

1. **Sub-merchant architecture IS implemented.** `sub_merchant_id` is sent on every initiate when the restaurant's sub-merchant is ACTIVE (EasebuzzPaymentService.java:114-134). A full Express-Onboarding subsystem exists: `EasebuzzSubMerchant` + `EasebuzzSubMerchantWebhookEvent` entities, KYC access-key generation, OTP verify/resend, `submitToEasebuzz`, onboarding status webhooks (`handleSubMerchantWebhook`, hash = `key|submerchant_id|salt`), split labels, `settlements/v1/retrieve`, wire payouts. ERA Q&A #1/#7 answers are therefore partially satisfied by the implementation; the ERA questions that remain are contract-level confirmations, not new architecture.
2. **Webhook hash verification is correctly implemented** and matches the official SDK sequences exactly: payment reverse-hash `salt|status|udf10..udf1|email|firstname|productinfo|amount|txnid|key` (EasebuzzWebhookService.java:340-381), refund `key|easepayid|salt`, payout (2 variants), sub-merchant. All comparisons use timing-safe `MessageDigest.isEqual`. Test bypass (`skip_for_test`) is gated to dev/sandbox profiles only.
3. **Webhook retry engine is fully wired EXCEPT the feed.** `WebhookRetryService` runs every 30s (`@Scheduled`), implements attempts/backoff, `DEAD_LETTER` + ALERT log, and admin endpoints `/admin/webhooks/health`, `/dead-letter`, `/dead-letter/{id}/replay`. `WebhookRetryConfig` dispatches retries to the correct handler. **But `enqueue(...)` still has zero production callers** — nothing ever creates a job, and ALL webhook endpoints return HTTP 200 unconditionally (`ResponseEntity.ok(...)`, PaymentController.java:111-155), even on `hash_mismatch`. The gateway therefore never retries and the engine stays empty. (Audit R4 confirmed, refined.)
4. **NEW — a cancel API exists in the code but contradicts the ERA comment.** `POST /payments/easebuzz/cancel/{billId}` → `transaction/v1/cancel` (PaymentController.java:90-93, EasebuzzApiClient.java:207-214) is exposed, while the `ERA-CONFIRMED` comment at EasebuzzPaymentService.java:56 says "No cancel API exists". One of the two is wrong — must be tested + confirmed with ERA.
5. **NEW — the surl/furl return endpoint does not verify the reverse hash.** `GET /payments/easebuzz/return` (PaymentController.java:95-109) 302-redirects to `khanabook://payment/success` based purely on the client-supplied `status` param — it is spoofable. Low severity (money state is set only by webhook/`/verify`), but the redirect should still validate the reverse hash before showing success.
6. **SDK deviation — all 10 UDFs are sent in the POST body.** Official SDK computes the hash over udf8-10 but REMOVES them from the body before sending. `EasebuzzApiClient` sends all 10 as params (EasebuzzApiClient.java:95-104). Works in practice but deviates from the SDK; verify with ERA/sandbox that empty udf8-10 are acceptable (they carry empty strings).
7. **Confirmed intact:** timeouts 10s connect / 30s read (SimpleClientHttpRequestFactory, EasebuzzApiClient.java:29-32); status poll uses `transaction/v2.1/retrieve` (:147); payment webhook idempotency (already-paid guard + FSSAI guard, EasebuzzWebhookService.java:63-66,112-115); refund webhook idempotency for duplicate `refunded` (:208-211); refund lookup still by `findByGatewayTxnId` (R6 open); `merchant_refund_id` still `"REF"+billId+"_"+System.currentTimeMillis()` (R14 open); `easebuzzId` fallback to `txnid` on refund initiate (EasebuzzPaymentService.java:280-283 — must be resolved via webhook event or retrieve); `reinitiateExistingOrder` still dead code (:473-533); **no scheduled reconciliation job** (settlements retrieval is admin-manual only).

Sources: official SDK `paywitheasebuzz-php-lib` (utils.php, response.php, transaction.php), docs.easebuzz.in (payment gateway API, webhooks, refunds), easebuzz.in (express-onboarding, marketplace, pricing, grievance, terms), apis.io/OpenAPI mirror.

---

## 1. Sub-merchant architecture

| # | Question | Answer | Status |
|---|---|---|---|
| 1 | Recommended architecture for a SaaS/POS master merchant with restaurant sub-merchants? | Easebuzz explicitly supports platforms/marketplaces: "sub-merchant management for platforms and marketplaces" and Express Onboarding for partner/sub-merchant registration with "separate dashboards". No canonical public blueprint for the POS case. | [DOC] partial; **[ERA-NEEDED]** for the exact recommended split |
| 2 | Unique Easebuzz MID/sub-merchant ID per restaurant? | Express Onboarding issues each sub-merchant its own KYCID + MID, delivered via webhook. One MID per restaurant is the supported pattern. | [DOC] strong; **[ERA-NEEDED]** to confirm 1:1 mandatory |
| 3 | Exact relationship: master merchant ↔ sub-merchant ↔ MID ↔ transaction ID | Not documented publicly. | **[ERA-NEEDED]** |
| 4 | Which merchant is "merchant of record" for a customer payment? | Not documented publicly (for marketplace model the sub-merchant is typically merchant of record for the shopper; confirmation needed). | **[ERA-NEEDED]** |
| 5 | Which identifier to store permanently for reconciliation? | SDK confirms: `txnid` (initiate), `easebuzz_id`/`easepayid` (webhook + retrieve response), `refund_id` (refund), `merchant_refund_id` (your key). All four matter. KhanaBook already stores txnid, easebuzz_id (webhook events table), refund_id. | [DOC] + gap note below |
| 6 | Multiple terminals generating payments under one sub-merchant? | No rate-limit/concurrency doc. Each payment is independent (fresh txnid per attempt), so multiple terminals work at the protocol level. | [DOC] partial; **[ERA-NEEDED]** for limits |

## 2. Payment creation

| # | Question | Answer | Status |
|---|---|---|---|
| 7 | Create payment with master credentials + sub-merchant identifier? | Express Onboarding docs describe master-merchant driven onboarding; payment-initiation docs do not document a sub-merchant field. KhanaBook currently uses one merchant key/salt globally. | **[ERA-NEEDED]** (critical — affects architecture) |
| 8 | Mandatory fields for payment creation | `txnid`, `amount`, `firstname`, `email`, `phone`, `productinfo`, `surl`, `furl` (+ `key`, `hash`). `amount` must contain a decimal point and be ≥ 1.0. udf1–udf10 optional (udf8–10 hash-only, never sent in body). | [DOC] — SDK `_validateInitiatePaymentParams` |
| 9 | Immutable fields after creation | Not documented. Amount/txnid are effectively immutable after a paid webhook. | **[ERA-NEEDED]** |
| 10 | Recommended txnid strategy | Every attempt = a fresh, unique txnid. KhanaBook uses `KB` + 5-digit bill tail + 5-digit restaurant tail + 8-hex UUID = exactly 20 chars, matching ERA's 20-char max. SDK regex allows `[a-zA-Z0-9_|\-/]{1,40}`. | [DOC] + [ERA] ✓ aligned |
| 11 | txnid global vs per-sub-merchant uniqueness | ERA: "Each txnid can ONLY be used ONCE — never reuse after success, failure, or timeout." Scope (global vs sub-merchant) not specified. | [ERA] use-once; **[ERA-NEEDED]** scope |
| 12 | Same txnid sent twice | Implied rejected (use-once). Exact error code not documented. | **[ERA-NEEDED]** (expect "Duplicate txnid" error) |
| 13 | Idempotency mechanism for payment creation | No documented idempotency key for initiate. Guidance (easebuzz explainer): use idempotency keys, lock orders in "processing", query status API before retry. | [DOC] advisory; **[ERA-NEEDED]** formal |
| 14 | Timeout but transaction created — how to determine safely | Poll `/transaction/v2.1/retrieve` with the same txnid. ERA confirms: "No webhook for abandoned txnids — poll /transaction/v2.1/retrieve if needed." Do NOT create a new txnid before polling. | [ERA] ✓ (KhanaBook must implement poll-before-reinit) |
| 15 | Source of truth: API, webhook, status API, dashboard | Easebuzz's own developer guide: webhooks are "the final confirmation" / treat webhooks as source of truth; reconcile "pending" with status API + webhook finalisation. Never trust frontend/redirect alone. | [DOC] ✓ |
| 16 | API says `pending`, webhook says `success` — trust? | Trust the success webhook (final confirmation). Server-side webhook beats interim API state. | [DOC] ✓ |
| 17 | Second webhook for same transaction after success | Documented guidance mandates idempotent processing ("same event twice should not break you"). Duplicate delivery must be handled. | [DOC] ✓ (KhanaBook: duplicate-skip exists) |
| 18 | Can status move backwards (SUCCESS→FAILED/PENDING)? | Not documented. | **[ERA-NEEDED]** |
| 19 | Full status list + valid transitions | Public docs show `success`/`failure`/`pending` (plus `reversed` in industry terms). No official state machine published. | [DOC] partial; **[ERA-NEEDED]** |
| 20 | Official transaction-status API for delayed webhooks | Yes: `POST https://dashboard.easebuzz.in/transaction/v2/retrieve` (SDK) — ERA references `/transaction/v2.1/retrieve`. Params `key`,`txnid`; hash SHA-512(`key\|txnid\|salt`). | [DOC] ✓ |
| 21 | Complete webhook event list | Documented webhooks: **Transaction webhook**, **Refund webhook**, **Payout webhook**, plus Express-Onboarding status webhooks. | [DOC] ✓ |
| 22 | Unique event ID in every webhook | Not documented. | **[ERA-NEEDED]** |
| 23 | Field for webhook idempotency | Not documented (best candidates: `txnid`/`easebuzz_id` for payments, `refund_id` for refunds — KhanaBook uses these). | [DOC] partial; **[ERA-NEEDED]** |
| 24 | Can the same webhook be delivered multiple times? | Not guaranteed once; Easebuzz guidance says make processing idempotent for duplicate events. Assume yes. | [DOC] assume-dup; **[ERA-NEEDED]** confirm |
| 25 | Can webhooks arrive out of order? | Not documented. Assume possible (payment vs refund vs payout events are independent). | **[ERA-NEEDED]** |
| 26 | Webhook hours/days after transaction? | Refund webhooks can plausibly lag (bank processing). Not documented. | **[ERA-NEEDED]** |
| 27 | Retry delivery behavior | Not published. | **[ERA-NEEDED]** |
| 28 | Endpoint returns HTTP 500 | Not published; general practice = retry. Easebuzz guide: endpoints must return "valid responses (HTTP 200)". | [DOC] weak; **[ERA-NEEDED]** |
| 29 | Endpoint times out | Not published. | **[ERA-NEEDED]** |
| 30 | HTTP 200 but internal processing fails | No gateway-side reconciliation from ack; must be self-healed via status API or own retry queue. | [DOC] advisory; **[ERA-NEEDED]** |
| 31–33 | Retry count / interval / configurable | Not published. | **[ERA-NEEDED]** |
| 34 | Webhook replay from dashboard/API | Not documented. (KhanaBook's `WebhookRetryService` exists but has zero callers — audit R4.) | **[ERA-NEEDED]** |

## 5. Webhook security

| # | Question | Answer | Status |
|---|---|---|---|
| 35 | Verify webhook authenticity | Reverse hash: SHA-512(`salt\|status\|udf10\|udf9\|…\|udf1\|email\|firstname\|productinfo\|amount\|txnid\|key`), lowercase hex, compare with `hash` field using `hash_equals` (timing-safe). | [DOC] ✓ SDK `response.php`/`_getReverseHashKey` |
| 36 | Hash algorithm | SHA-512, lowercase hex. | [DOC] ✓ |
| 37 | Fields in signature | Exactly the order in #35 (empty fields still occupy `\|` positions). udf8–10 are part of the hash but never in the POST body — **this breaks naive signature implementations**. | [DOC] ✓ |
| 38 | Verify without another API call | Yes — pure hash computation, no callback API. | [DOC] ✓ |
| 39 | IP allowlist | Not documented publicly. | **[ERA-NEEDED]** |
| 40 | Signature + IP both? | Signature is mandatory and sufficient at protocol level; IP allowlist optional hardening. | [DOC] advisory |

## 6. Duplicate payments / double payment

| # | Question | Answer | Status |
|---|---|---|---|
| 41 | Paid but no response (timeout) — how to know | Poll `/transaction/v2.1/retrieve` with the txnid before anything else. | [ERA] ✓ |
| 42 | Query status before another attempt? | Yes — mandatory. ERA: "Never reuse [txnid] after success, failure, or timeout"; Easebuzz guide: "Reconcile 'pending' payments using a status API plus webhook finalisation". | [ERA] + [DOC] ✓ |
| 43 | Which API | `transaction/v2/retrieve` (SDK) / `transaction/v2.1/retrieve` (ERA reference). | [DOC]/[ERA] ✓ |
| 44 | Recommended timeout before retry | Not published. (KhanaBook SDK-parity timeouts: 10s connect / 30s read.) | **[ERA-NEEDED]** |
| 45 | Two active payment attempts for one order? | Possible and dangerous — ERA: "Multiple txnids for same bill CAN both succeed (double-charge risk)". No cancel API; unpaid txnids auto-expire in 15 min. | [ERA] ✓ — risk is REAL |
| 46 | Prevent double payment mechanism | No API-level order lock. Must be app-level: lock bill in processing state; poll before re-init; track all txnids per bill. | [DOC] advisory + [ERA] |
| 47 | Two txnids both paid for same bill — reconcile how | No automatic mechanism documented. | **[ERA-NEEDED]** |

## 7. Refunds

| # | Question | Answer | Status |
|---|---|---|---|
| 48 | Who initiates refund for sub-merchant | Not documented. | **[ERA-NEEDED]** |
| 49 | Refund API | `POST https://dashboard.easebuzz.in/refund/initiate`-family; SDK route `refund`. Params: `key`, `merchant_refund_id`, `easebuzz_id`, `refund_amount`; hash SHA-512(`key\|merchant_refund_id\|easebuzz_id\|refund_amount\|salt`). | [DOC] ✓ |
| 50 | Identifiers required | `easebuzz_id` (Easebuzz transaction ID — NOT `txnid`), `merchant_refund_id` (your idempotency key), `refund_amount`. KhanaBook currently falls back to passing `txnid` when `easebuzz_id` is unknown — **incorrect; must be resolved via webhook event or retrieve API**. | [DOC] ✓ + KhanaBook gap |
| 51 | Partial refunds | Public policy: refunds can be complete or partial. | [DOC] ✓ |
| 52 | Max refund amount | Not documented publicly (marketplace/common practice caps ≤ txn amount). | **[ERA-NEEDED]** |
| 53–54 | Max window / 180-day rule | Grievance policy: refunds not processed after **180 days** from transaction date. Confirm for sub-merchant contract. | [DOC] ✓ + **[ERA-NEEDED]** confirm |
| 55 | Refund idempotency | `merchant_refund_id` is the idempotency key — reusing the same value with the same `easebuzz_id` should be safe. KhanaBook generates `"REF" + billId + "_" + System.currentTimeMillis()` — **a retry generates a NEW key → double-refund risk. Must be a stored UUID reused on retry.** | [DOC] key exists + KhanaBook gap (audit R14) |
| 56 | Refund request timeout — safe determination | Re-poll with the SAME `merchant_refund_id` (idempotency key) — do not generate a new one. | [DOC] ✓ + gap |
| 57 | Same refund request twice | Same `merchant_refund_id` → idempotent (assumed); exact response behavior undocumented. | **[ERA-NEEDED]** |
| 58 | Unique refund ID returned | Yes — v2 response returns `refund_id` (also nested under `msg` in some responses). KhanaBook stores it (`bill.refundId`). | [DOC] ✓ |
| 59 | Relationship txnid / refund_id / reference ID / ARN | Not documented publicly. | **[ERA-NEEDED]** |
| 60 | Which identifier to store permanently | Both `txnid` (payment) and `refund_id` (refund); `merchant_refund_id` as your reconciliation key. | [DOC] ✓ |

## 8. Refund webhook

| # | Question | Answer | Status |
|---|---|---|---|
| 61 | Webhook when refund initiated | Refund webhook exists (`docs … refund-webhook`); payload fields observed in KhanaBook: `txnid`, `status`, `refund_id`, `refund_amount`, `easepayid`. Initiation vs completion events not formally split in public docs. | [DOC] partial |
| 62 | Webhook when refund completed | Not distinguished publicly. | **[ERA-NEEDED]** |
| 63 | Refund statuses | Not published. | **[ERA-NEEDED]** |
| 64–65 | Duplicate / out-of-order refund webhooks | Same as payments: assume possible, process idempotently (key on `refund_id`). | [DOC] advisory; **[ERA-NEEDED]** |
| 66 | Refund webhook with original txnid missing locally | KhanaBook looks up by `findByGatewayTxnId(txnid)` — breaks if `gateway_txn_id` was cleared/replaced by a later attempt (audit R6). Fix: keep full txnid history per bill; match on any stored txnid. | [DOC] + gap |
| 67 | Does refund webhook contain ARN | Not documented. | **[ERA-NEEDED]** |
| 68 | When is a refund truly REFUNDED | Policy distinguishes refund initiation vs bank processing/ARN. Exact terminal criteria undocumented. | **[ERA-NEEDED]** |

## 9. Settlement

| # | Question | Answer | Status |
|---|---|---|---|
| 69–70 | Settlement to each sub-merchant's bank account | Express Onboarding collects sub-merchant bank details → supports per-merchant settlement; confirm contract. | [DOC] partial; **[ERA-NEEDED]** |
| 71–72 | Settlement cycle / T+1 | Marketing states T+1 settlement for the gateway; **confirm for the contractual sub-merchant setup — do not assume**. | [DOC] marketing; **[ERA-NEEDED]** |
| 73–74 | Settlement reports / API | Yes — Payout API: params `merchant_key|start_date|end_date`, hash SHA-512(`merchant_key\|start_date\|end_date\|salt`). Returns settlement/payout details by date range. | [DOC] ✓ |
| 75 | Report fields (submerchant, MDR, GST, refund, chargeback, UTR…) | Exact columns not documented publicly. | **[ERA-NEEDED]** |
| 76 | Recommended reconciliation process | Not published; primitives available: `transaction_date` API (by date range, params `key|merchant_email|start_date|end_date`), payout API, per-txn `transaction/v2/retrieve`. | [DOC] primitives + **[ERA-NEEDED]** process |

## 10. Failed transactions

| # | Question | Answer | Status |
|---|---|---|---|
| 77 | Customer debited but status failed/pending | Terms: funds received in escrow for failed transactions are reversed per applicable timelines; status updates provided. Exact process for a POS must be confirmed. | [DOC] terms + **[ERA-NEEDED]** |
| 78 | Distinguish FAILED/PENDING/SUCCESS/DEBITED/REVERSED | No official classification published. | **[ERA-NEEDED]** |
| 79 | Reconciliation API for this | `transaction/v2/retrieve` per txn; `transaction_date` for ranges. | [DOC] ✓ |
| 80 | Wait before declaring permanently failed | 15-min txnid auto-expiry (ERA) is the abandonment threshold; reversal timelines unknown. | [ERA] partial + **[ERA-NEEDED]** |
| 81 | Who handles automatic reversal | Easebuzz/banks per terms; nothing for KhanaBook to call. | [DOC] ✓ |

## 11. Chargebacks / disputes

| # | Question | Answer | Status |
|---|---|---|---|
| 82–88 | Communication channel, webhook, info provided, deadline, evidence submission, settlement deduction, DB representation | Public material: merchants can be required to provide chargeback documentation within a short period (cited example: 3 calendar days in terms). **No public API/webhook contract.** | [DOC] policy only + **[ERA-NEEDED]** (all technical details) |

## 12. Sub-merchant onboarding

| # | Question | Answer | Status |
|---|---|---|---|
| 89–95 | Lifecycle statuses, webhook events, KYC-rejection handling, payment-mode toggles, status API | Express Onboarding: PAN/GSTIN verification, CPV via video, KYCID + MID delivered via webhook, real-time status webhooks at each stage incl. payment-mode activation. Exact status enum and query API not public. | [DOC] partial + **[ERA-NEEDED]** |

## 13. Multi-terminal POS

| # | Question | Answer | Status |
|---|---|---|---|
| 96–100 | Concurrent txn creation, per-sub-merchant rate limits, master-merchant limits, per-API limits, 5 terminals at once | Nothing published. Protocol-level: each txn independent (unique txnid). | **[ERA-NEEDED]** |

## 14. Production reliability

| # | Question | Answer | Status |
|---|---|---|---|
| 101 | Recommended timeouts | Official SDK uses **10s connect / 30s total** (CURLOPT_CONNECTTIMEOUT=10, CURLOPT_TIMEOUT=30). KhanaBook's Easebuzz client matches (10s/30s). | [DOC] ✓ |
| 102–104 | Retryable vs never-retry errors, retry strategy | Not published. Easebuzz guide: "Timeouts are normal. Your system should safely retry without double-charging." Use idempotency keys; query status before re-initiate. | [DOC] advisory + **[ERA-NEEDED]** error catalogue |
| 105 | Exponential backoff | Not specified. | **[ERA-NEEDED]** |
| 106–107 | Idempotent vs non-idempotent calls | `transaction/v2/retrieve`, `refund_status` (by easebuzz_id), `transaction_date`, `payout` = read-only/idempotent. `initiate` (new txnid per call — unique txnid makes retry create a NEW txn), `refund/initiate` (idempotent ONLY if `merchant_refund_id` reused). | [DOC] ✓ derived from API shape |
| 108 | Circuit-breaker strategy | Not documented; not needed per-request for POS volumes — app-level lock + poll is the mechanism. | [DOC] advisory |

## 15. Reconciliation

| # | Question | Answer | Status |
|---|---|---|---|
| 109–116 | Official reconciliation API/process, frequency, matching keys, orphan txn handling, amount mismatch | No official reconciliation endpoint beyond: per-txn `transaction/v2/retrieve`, date-range `transaction_date`, settlement `payout`. Matching key = `txnid` (yours) ↔ `easebuzz_id`; refunds = `merchant_refund_id` ↔ `refund_id`. Process for mismatches: **ERA-NEEDED**. | [DOC] primitives + **[ERA-NEEDED]** process |

---

## Top 15 questions — status after this research

| # | Question | Status |
|---|---|---|
| 1 | Recommended SaaS master/sub-merchant architecture | **[ERA-NEEDED]** |
| 2 | Authoritative payment status source | [DOC] — webhook is final; status API for reconcile |
| 3 | Complete status state machine | **[ERA-NEEDED]** |
| 4 | Webhook retry behavior for 2xx/4xx/5xx/timeout | **[ERA-NEEDED]** |
| 5 | Duplicate/out-of-order webhooks | [DOC] assume-dup, idempotency mandatory; confirm |
| 6 | Official webhook idempotency identifier | **[ERA-NEEDED]** |
| 7 | Timeout recovery without second payment | [ERA] poll `/transaction/v2.1/retrieve` first; never reuse txnid |
| 8 | Payment-creation idempotency | [DOC] no formal key; advisory pattern + txnid uniqueness |
| 9 | Sub-merchant refund flow + idempotency | [DOC] `merchant_refund_id` is the key; sub-merchant specifics ERA |
| 10 | Refund statuses + refund webhook events | **[ERA-NEEDED]** |
| 11 | Settlement/reconciliation API + strategy | [DOC] payout + transaction_date APIs; official process ERA |
| 12 | Debited-but-failed handling | [DOC] reversal per terms; exact process ERA |
| 13 | Rate/concurrency limits | **[ERA-NEEDED]** |
| 14 | Retry-safe vs never-retry operations | [DOC] derived (initiate = new txn; refund idempotent via key); catalogue ERA |
| 15 | DB-vs-gateway conflict resolution process | **[ERA-NEEDED]** |

---

## KhanaBook action map (what changes in our code regardless of ERA answers)

| Audit risk | Easebuzz contract fact | Current KhanaBook behavior (verified) | Required fix |
|---|---|---|---|
| R5 (double-charge) | ERA: multiple txnids for one bill **can both succeed**; no cancel API; abandoned txnids expire in 15 min | `createOrder` unconditionally clears old `gateway_txn_id` and creates a fresh one (EasebuzzPaymentService.java:61-68) | Before clearing/re-initiating: poll `/transaction/v2.1/retrieve` on the OLD txnid; success → return paid; pending → block/warn. Persist a `payment_attempts` history table (all txnids per bill). Refund/payment webhook matching must use the history. |
| R6 (refund webhook miss) | Refund webhook carries the original `txnid` | `billRepo.findByGatewayTxnId(txnid)` (EasebuzzWebhookService.java:165) fails when txnid was cleared/replaced | Match against txnid history; store `easebuzz_id` from payment webhook events (already saved) and never fall back to sending `txnid` as `easebuzz_id` in refund initiate (EasebuzzPaymentService.java:280-283) — resolve via event or `transaction/v2/retrieve` |
| R14 (refund idempotency) | `merchant_refund_id` is the official idempotency key | `"REF" + billId + "_" + System.currentTimeMillis()` (EasebuzzPaymentService.java:286) — new key per retry | Persist a UUID `merchant_refund_id` per refund attempt (unique DB constraint); reuse on retry; reconcile by `refund_id` |
| R4 (webhook retry) | Retry contract unpublished; must still self-heal | Engine fully wired (30s scheduler, DEAD_LETTER, admin replay) but `enqueue` has zero callers; all webhook endpoints return 200 even on `hash_mismatch` (PaymentController.java:111-155) | Call `enqueue` on processing failure; return non-2xx on hash mismatch so the gateway may retry; process idempotently (payments ✓, refunds ✓) |
| NEW | Cancel API exists in code: `transaction/v1/cancel` via `/payments/easebuzz/cancel/{billId}` (PaymentController.java:90-93) | ERA comment says "No cancel API exists" (EasebuzzPaymentService.java:56) — internal contradiction | Test in sandbox; if it works, replace the "clear and wait 15 min" strategy with poll + cancel; confirm with ERA |
| NEW | Return redirect must not be trusted | `GET /payments/easebuzz/return` redirects to `khanabook://payment/success` on client-supplied `status=success` without hash verification (PaymentController.java:95-109) | Verify reverse hash on the return params before redirecting (or drop the status-based branch) |
| Status trust | Webhook = final confirmation | Payment webhook marks paid + saves event; `/verify` polls `transaction/v2.1/retrieve` ✓ | Keep; add periodic sweep for bills stuck `pending` with a non-expired txnid |
| Reconciliation | txnid ↔ easebuzz_id; `transaction_date` + `settlements/v1/retrieve` APIs exist | `retrieveSettlements` exists but admin-manual only; no scheduled job | Add daily job: pull settlements + date-range transactions for each restaurant, match by txnid, alert on mismatches (debits without webhook, refunds without bill) |
| txnid format | 20-char max; use-once | `KB` + tails + 8-hex UUID = 20 chars ✓ | No change — already compliant |

---

## Remaining questions to send to ERA (concise list)

1. Recommended architecture: master merchant + one sub-merchant per restaurant? Merchant of record?
2. Payment creation on behalf of a sub-merchant — which credentials/fields? Is there a sub-merchant field in initiate?
3. Full transaction status enum + allowed transitions (can SUCCESS go back to PENDING/FAILED/REVERSED)?
4. Webhook delivery contract: retry count, intervals, backoff, response-code semantics (200/4xx/5xx/timeout), duplicate and out-of-order guarantees, event IDs, replay availability?
5. Official webhook idempotency identifier?
6. Refund limits for our sub-merchant model: max %, 180-day window applicability, partial refunds, refund status enum, "refund initiated" vs "completed/ARN" semantics, refund webhook events and fields (does it include ARN)?
7. Chargebacks: webhook or API? deadline, evidence submission, settlement deduction?
8. Settlement: cycle for sub-merchants (T+1?), per-merchant settlement, settlement report API fields (UTR, MDR, GST, refunds, chargebacks), reconciliation API/process?
9. Rate/concurrency limits per sub-merchant and master (esp. multiple POS terminals)?
10. Error catalogue: which errors are retryable, which are permanent; duplicate-txnid error code; recommended timeouts?
11. Debited-but-failed: exact reversal timeline and how we detect it via API?
12. Does a cancel API exist? Our code calls `transaction/v1/cancel` but ERA previously told us no cancel API exists — which is correct, and does canceling work for sub-merchant payments? If yes, we can replace the 15-min expiry wait with poll + cancel.
