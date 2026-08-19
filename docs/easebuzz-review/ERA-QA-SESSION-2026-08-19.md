# KhanaBook × Easebuzz ERA Q&A Session — 2026-08-19

**Date:** 2026-08-19
**Channel:** ERA (Easebuzz Rapid Assist) AI chatbot
**Merchant Key:** ADNX3KYX5 (India Advocacy / KhanaBook)

---

## Summary

All 14 questions answered. No blockers for production go-live from Easebuzz's side.
The remaining work is on our code to fix safety issues identified in earlier audits.

---

## Q1: Sub-Merchant Architecture (Merchant of Record)

**Answers:**
- **KhanaBook (parent) is the merchant of record** for the customer.
- Sub-merchant (restaurant) is the beneficiary of the payment.
- Passing `sub_merchant_id` in `/payment/initiateLink` is the correct way to route payments.
- 1:1 mapping (one sub-merchant per restaurant) is the recommended pattern.

---

## Q2: Transaction Status State Machine

**Full status list:**
| Status | Meaning |
|---|---|
| `preInitiated` | Customer clicked "Pay Now" (access key generated) |
| `initiated` | Customer on the card/payment/checkout page |
| `pending` | Customer on the bank page |
| `success` | Transaction successful |
| `failure` | Transaction failed |
| `userCancelled` | Customer explicitly cancelled |
| `dropped` | Customer left during bank processing |
| `bounced` | Customer left during card page |

**Key facts:**
- Status **CANNOT move backwards** — `success` stays `success` forever.
- Abandoned txnids expire after ~15 minutes → marked `dropped` or `bounced`.

---

## Q3: Webhook Delivery Contract

| Property | Value |
|---|---|
| Retry count | **5 attempts** (PG/InstaCollect) |
| Retry interval | Every **30 minutes** |
| Must return | **HTTP 200** to acknowledge |
| Non-200 triggers | Retry |
| Duplicate delivery | **Yes** — possible due to retries |
| Out-of-order | **Yes** — webhooks are async |
| Replay mechanism | Manual reactivation from dashboard only; no replay API |
| Timeout | **30 seconds** before considered failed |
| After 5 failures | Webhook is **blocked** — must manually reactivate in dashboard |

---

## Q4: Webhook Idempotency Identifier

| Webhook type | Idempotency key |
|---|---|
| Payment | `txnid` + `status` |
| Refund | `refund_id` |
| Sub-merchant | `submerchant_id` |

No unique event ID exists — composite keys are the pattern.

---

## Q5: Cancel Transaction API

- ❌ **Cancel API does NOT exist** — `/transaction/v1/cancel` is not officially supported.
- Abandoned transactions auto-expire after 15 minutes.
- Resolution: poll `/transaction/v2.1/retrieve` + client-side timeouts + webhook tracking.

---

## Q6: Refunds — Sub-Merchant Specifics

| Property | Answer |
|---|---|
| Who initiates | **Parent merchant (KhanaBook)** — sub-merchants cannot |
| Refund statuses | `Queued` → `Approved` → `Refunded` (or `Cancelled Refund` / `Hold Refund`) |
| Separate webhooks | Yes: `REFUND_INITIATED` and `REFUND_STATUS_UPDATE` |
| ARN in webhook | Yes — `arn_number` included when status = `refunded` |
| 180-day window | Applies to all transactions including sub-merchant |
| `merchant_refund_id` idempotent | **YES** — same ID returns existing refund, no duplicate created |

---

## Q7: Chargebacks & Disputes

| Property | Answer |
|---|---|
| Webhook | Yes — `CASE_STATUS_UPDATE` events (`OPEN`, `MERCHANT_DENIED`, etc.) |
| Also notified via | Email + DRS (Dispute Resolution System) dashboard |
| Evidence deadline | 7–10 working days (Level 1); 1–3 days (fraud) |
| Deduction method | From next settlement cycle (if accepted/lost) |
| Who gets notified | **Parent merchant (KhanaBook)** — sub-merchants configurable |

---

## Q8: Settlement Cycle & Reports

| Property | Answer |
|---|---|
| Cycle | **Configurable** — T+1 default; T+0/instant available (contact sales) |
| Settlement target | **Direct to sub-merchant's bank** — no pooling through parent |
| Report fields | `bank_transaction_id` (UTR), `peb_service_charge` (MDR), `peb_service_tax` (GST), `peb_refunds`, `payout_amount` |
| Reconciliation | `/settlements/v1/retrieve` with date range; match by `payout_id`, `txnid`, `amount` |
| Pagination | Max 500 records/page |

---

## Q9: Rate Limits & Concurrency

| Property | Answer |
|---|---|
| Per-sub-merchant limit | **None** |
| Per-parent-merchant limit | **None** |
| Per-API limit | **None rigid** (optimize calls, don't abuse) |
| 5 terminals simultaneously | **Fully supported** |

---

## Q10: Error Catalogue

| Error class | HTTP code | Retryable? |
|---|---|---|
| Server error | 5xx | ✅ Yes — safe to retry |
| Timeout | — | ✅ Yes — safe to retry |
| Validation error | 400 | ❌ No — permanent |
| Auth error | 401 | ❌ No — permanent |
| Duplicate txnid | 400 | ❌ No — permanent ("already in use") |
| Invalid txnid | 400 | ❌ No — permanent |

---

## Q11: Debited-But-Failed Handling

| Property | Answer |
|---|---|
| Reversal timeline | **5–7 business days** (UPI: 24–48 hours) |
| Webhook | Yes — status changes to `failure` or `auto refunded` |
| Should we poll? | **No** — rely on webhooks; poll only for debugging |
| Should we manually refund? | **No** — Easebuzz + bank handle auto-reversal |
| UX recommendation | Show "Payment Pending" → advise 5–7 days for bank reversal |

---

## Q12: Double-Payment Prevention

| Property | Answer |
|---|---|
| API-level lock | ❌ **None** — must be handled on our side |
| If old txnid is `pending` | **WAIT** — do NOT create a new txnid |
| Double-charge resolution | **Refund one yourself** via Refund API — no Easebuzz auto-mechanism |

---

## Q13: KYC, OTP Verification & CPV

| Property | Answer |
|---|---|
| Lifecycle | `CREATED` → `KYC_SUBMITTED` → `KYC_APPROVED` → `ACTIVE` (or `KYC_REJECTED`) |
| CPV process | **Automatic via OTP** — no video call (rare exceptions) |
| KYC rejection | Can resubmit; webhook `MERCHANT_KYC_APPROVAL` with `kyc_status: false` + reason |
| Webhook events | `MERCHANT_KYC_APPROVAL` (kyc_status true/false) |
| OTP stage | After generating KYC access key — authenticates sub-merchant before KYC |
| Post-KYC activation | **Automatic** — KYC approved = ACTIVE, no extra API call |
| KYC review time | **24–48 hours** (business days) |

---

## Q15: KYC Webhook Payload & CPV Details (R&D Follow-Up)

**MERCHANT_KYC_APPROVAL webhook sample payload (Approved):**
```json
{
  "event": "MERCHANT_KYC_APPROVAL",
  "data": {
    "id": 4900,
    "virtual_account": {
      "id": "vabb6eb90ec640689d4377d56b395d6e",
      "account_number": "1010000000xxxxxxxx",
      "balance": 0,
      "is_active": true,
      "bank_name": "ICICI Bank",
      "status": "active",
      "ifsc": "ICIC0000104"
    },
    "kyc_status": true,
    "kyc_profile_status": "Completed",
    "email": "easebuzztest@gmail.com",
    "phone": "9999999999",
    "name": "KYC check"
  }
}
```

**Rejected:** Same structure, `kyc_status: false`, `kyc_profile_status: "Rejected"`

| Property | Answer |
|---|---|
| CPV vs KYC | CPV is a separate step but part of the same flow — no separate webhook |
| CPV process | Video/photo of business premises + location (lat/long) |
| KYC documents | PAN, Aadhaar, bank proof, business registration, GST, video/photo for CPV |
| KYC status polling API | ❌ None — must rely on webhook only |
| Virtual account | Provided on KYC approval — store `account_number` + `ifsc` for InstaCollect/settlements |
| CPV docs reference | https://docs.easebuzz.in/docs/get-started/par15oupue6xy |
| KYC documents list | https://docs.easebuzz.in/docs/get-started/6v6i0r0x14zly-kyc-documents |
| Webhook handling docs | https://docs.easebuzz.in/docs/neobanking/q7ba7nsflsf78 |

---

## Q14: Anything Missed? (Final Check)

**ERA suggestions for future:**
- UPI Intent & QR payments (broader checkout options)
- Soundbar/Soundbox (instant payment audio alerts at counter)
- Recurring payments (Recurri) — for subscriptions
- Offer Engine — dynamic discounts

**Compliance before go-live:**
- All sub-merchants must complete KYC (RBI requirement)
- Sign Easebuzz merchant agreement
- PCI-DSS handled by Easebuzz (we don't touch card data)

**Go-live checklist (from Easebuzz):**
1. Complete test cases in sandbox
2. Replace test credentials with live keys
3. Configure production webhook URLs
4. Verify settlement accounts for sub-merchants

**Common pitfalls:**
- Return HTTP 200 from webhook endpoints (non-200 = retry/block)
- Double-check reverse hash logic
- Test payouts in sandbox before live
- Monitor sub-merchant transaction limits in dashboard

---

## Code Action Items (from this session)

| Priority | Fix | ERA Basis |
|---|---|---|
| P0 | Add `easebuzz` to `PaymentSetValidator.supportedModes` | Payments work but can't finalize locally |
| P0 | Poll old txnid before creating new; block if `pending` | Q12: wait if pending, no API lock |
| P1 | Persist `merchant_refund_id` + reuse on retry | Q6: idempotent confirmed |
| P1 | Return non-200 on webhook hash mismatch | Q3: non-200 triggers retry (5× every 30min) |
| P1 | Wire `enqueue()` in webhook handler on failure | Q3: no replay API; must self-heal |
| P1 | Handle `auto refunded` webhook status | Q11: new status for debited-but-failed |
| P2 | Remove/disable cancel transaction endpoint | Q5: API doesn't exist |
| P2 | Add daily reconciliation job | Q8: use `/settlements/v1/retrieve` |
| P2 | Verify reverse hash on `/payments/easebuzz/return` | Security — don't trust client-supplied status |
| P2 | Handle `dropped`, `bounced`, `userCancelled` statuses | Q2: full status list |
| Future | Integrate DRS webhook (`CASE_STATUS_UPDATE`) | Q7: chargeback notifications |
| Future | Soundbar/Soundbox integration | Q14: payment audio alerts |
