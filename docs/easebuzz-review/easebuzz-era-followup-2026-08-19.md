To: pgsupport@easebuzz.in
Cc: gopal@indiaadvocacy.com
Subject: KhanaBook — ERA Technical Follow-Up: 12 Questions for Production Go-Live (Sub-Merchant Marketplace)

Dear Easebuzz ERA Team,

This is a follow-up to our previous integration review request for KhanaBook (Parent Merchant: India Advocacy, Key: ADNX3KYX5).

We have completed implementation of all 22 Easebuzz APIs (sub-merchant, payment, refund, split, settlement, payout, and WIRE). Before going live, we need clarification on 12 technical/contract questions that are not covered in the public documentation.

---

## Context

- **Model:** KhanaBook is the parent merchant. Each restaurant tenant = one Easebuzz sub-merchant.
- **Use case:** Multi-terminal Android POS app. Customer pays at the counter via Easebuzz SDK. Payment is initiated by our backend with `sub_merchant_id`. Post-transaction split routes funds to the restaurant and KhanaBook commission.
- **Current status:** All APIs implemented and tested against sandbox. Ready for production review pending these answers.

---

## Questions

### 1. Sub-Merchant Architecture (Merchant of Record)

In our marketplace model (parent merchant + one sub-merchant per restaurant):
- Who is the **merchant of record** for the customer payment — the parent (KhanaBook) or the sub-merchant (restaurant)?
- Is passing `sub_merchant_id` in the payment initiate call the correct and only way to route a payment to a sub-merchant?
- Is the 1:1 mapping (one sub-merchant ID per restaurant) the recommended pattern, or should we use a different structure?

### 2. Transaction Status — Complete State Machine

- What is the **full list of transaction statuses** a payment can have? (We see `success`, `failure`, `pending` in docs — are there others like `reversed`, `expired`, `cancelled`, `initiated`?)
- Can a status move **backwards**? Specifically: can a `success` transaction ever become `failure`, `reversed`, or `pending` later?
- After what duration does an unpaid/abandoned `txnid` expire? (We assume 15 minutes from ERA guidance — please confirm.)

### 3. Webhook Delivery Contract

This is critical for our reliability design:
- **Retry behavior:** If our endpoint returns HTTP 4xx or 5xx or times out, does Easebuzz retry? How many times? At what intervals?
- **Response semantics:** Must we return HTTP 200 for "received"? Does returning non-200 trigger a retry?
- **Duplicate delivery:** Can the same webhook event be delivered more than once? (We already handle idempotently, just confirming.)
- **Out-of-order:** Can webhooks arrive out of order? (e.g., refund webhook before payment webhook for the same txnid)
- **Replay:** Is there a dashboard option or API to replay a missed webhook?
- **Timeout:** How long does Easebuzz wait for our endpoint to respond before considering it failed?

### 4. Webhook Idempotency Identifier

- Is there a **unique event ID** in every webhook payload that we should use as the idempotency key?
- Or should we use `txnid` + `status` for payment webhooks and `refund_id` for refund webhooks? (This is what we currently do.)

### 5. Cancel Transaction API

Our code calls `POST /transaction/v1/cancel` (params: key, txnid, amount, hash). However, we received earlier guidance that "no cancel API exists."
- **Does this API work?** Can we cancel an initiated-but-unpaid transaction?
- Does it work for sub-merchant payments (with `sub_merchant_id`)?
- What status does the transaction move to after cancellation?
- If cancel is not supported, what is the recommended way to handle an abandoned payment besides waiting for the 15-minute txnid expiry?

### 6. Refund — Sub-Merchant Specifics

- Who can initiate a refund for a sub-merchant payment — the parent merchant (using parent key/salt) or the sub-merchant?
- What is the **refund status lifecycle**? (e.g., `initiated` → `processing` → `completed`/`failed` — what are the actual statuses?)
- Is there a separate webhook event for "refund completed" vs "refund initiated"?
- Does the refund webhook include the bank ARN (Acquirer Reference Number)?
- Is the 180-day refund window applicable to sub-merchant transactions?
- Is `merchant_refund_id` truly idempotent — if we send the same `merchant_refund_id` twice for the same `easebuzz_id`, does it return the existing refund without creating a duplicate?

### 7. Chargebacks & Disputes

- Is there a **webhook or API** for chargeback notifications? Or is it communicated only via email/dashboard?
- What is the evidence submission deadline?
- How is the chargeback amount deducted — from the next settlement, or via a separate debit?
- For sub-merchant payments, who receives the chargeback notification — parent or sub-merchant?

### 8. Settlement Cycle & Reports

- What is the **settlement cycle for sub-merchants** in our marketplace model? Is it T+1, T+2, or configurable?
- Does the settlement go directly to the sub-merchant's bank account (as registered during onboarding), or does it go through the parent's pool first?
- What fields are available in the settlement report API response? (Specifically: UTR, MDR, GST deduction, refund adjustments, chargeback adjustments?)
- Is there a recommended reconciliation process or API for matching our internal records against Easebuzz settlements?

### 9. Rate Limits & Concurrency

Our POS app runs on **multiple Android terminals per restaurant** (up to 5 devices), each generating independent payments:
- Is there a **per-sub-merchant rate limit** on payment initiation?
- Is there a **per-parent-merchant rate limit** across all sub-merchants?
- Are there per-API rate limits (e.g., max calls/minute to `/transaction/v2.1/retrieve`)?
- Any issues with 5 terminals creating payments simultaneously under the same sub-merchant?

### 10. Error Catalogue — Retryable vs Permanent

- Which API error codes/responses are **safe to retry** (e.g., timeout, 5xx)?
- Which errors are **permanent** and should not be retried (e.g., invalid txnid, duplicate txnid)?
- What is the exact error response when a duplicate `txnid` is submitted to `/payment/initiateLink`?
- What HTTP status codes does Easebuzz return for different error classes?

### 11. Debited-But-Failed Handling

When a customer's bank debits them but the transaction status shows `failure` or `pending`:
- What is the **reversal timeline**? (How long before funds return to the customer?)
- Is there a webhook or status change we receive when the reversal happens?
- Should we poll `/transaction/v2.1/retrieve` periodically for such transactions, or just wait for a webhook?
- From our side, should we show the bill as "Payment pending — will be auto-reversed" or should we attempt a refund API call?

### 12. Double-Payment Prevention

ERA previously confirmed: "Multiple txnids for the same bill CAN both succeed" — this is our biggest risk.
- Is there **any API-level mechanism** to lock a sub-merchant payment to one active attempt at a time?
- If we poll `/transaction/v2.1/retrieve` for the old txnid and it shows `pending`, should we wait or can we safely create a new txnid?
- If two payments succeed for the same bill (double-charge), what is the official resolution process? Do we refund one ourselves, or is there an Easebuzz mechanism?

---

## Summary of What We Need Enabled for Production

(Reconfirming from our previous email — please confirm these are active on our account):

1. Parent-submerchant marketplace model ✅ enabled?
2. Post-transaction split feature ✅ enabled?
3. Webhook delivery for: payment, refund, sub-merchant status, payout ✅ enabled?
4. Cancel transaction API ✅ enabled?
5. On-demand settlement ✅ enabled?
6. Production callback URLs (will provide once sandbox testing complete)

---

We can share our complete integration review document (architecture diagrams, sequence flows, all API payloads) if that helps your team review faster. Please let us know the preferred format.

Thank you,

Gopal Krishna
India Advocacy
gopal@indiaadvocacy.com
