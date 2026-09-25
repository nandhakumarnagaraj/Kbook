# Easebuzz hosted-checkout test cases — KhanaBook go-live mapping

Source: `C:\Users\nandh\Desktop\integration_test_cases_sample.xlsx` (Easebuzz-provided integration matrix; 6 sheets, ~45 unique scenarios, ~5,900 rows of repeated surcharge-slab iterations).

Purpose: map every scenario to KhanaBook's implementation, tag ownership (KhanaBook vs Easebuzz hosted checkout), record code evidence, and list what still needs production verification.

## KhanaBook payment model (summary)

- **Online link / hosted checkout**: Easy Collect `/payment/initiateLink`, `sub_merchant_id`, `udf1=billId`, `udf2=restaurantId`. `show_payment_mode=CC,DC,NB,UPI,WALLET` (EMI/QR excluded by product choice). See `EasebuzzPaymentService.createPaymentLinkForBill` (line 549).
- **In-store dynamic UPI QR**: POS billing screen, `payment_mode=UPI` + `upi_qr=true` request on the same link API (`EasebuzzApiClient` lines 119–122); order-specific amount + merchant txn for automated reconciliation.
- **Result handling**: webhooks (PAYMENT/REFUND/PAYOUT/SUB_MERCHANT) with hash + amount + bill checks, idempotent; durable retry/dead-letter (`WebhookRetryService`, `webhook_retry_jobs`); failure/cancel surfaced by re-query (`/transaction/v2.1/retrieve`).
- **Refunds**: Refund API v2 `/transaction/v2/refund`; status lookup `/refund/v1/retrieve` (JSON, hash `key|easebuzz_id|salt`).
- **Settlements/reconciliation**: on-demand + scheduled 6 AM IST reconciliation against Easebuzz settlements; amount mismatches, orphans and missing webhooks are flagged (`EasebuzzReconciliationService`).

## Scenario mapping

Legend — Owner: **EB** = Easebuzz hosted checkout behavior, **KB** = KhanaBook-owned. Status: `covered` (implemented), `verify-live` (needs production evidence), `open` (gap / needs decision).

### DEBIT CARD & CREDIT CARD (identical intent)

| TC | Scenario | Owner | KhanaBook status |
|----|----------|-------|------------------|
| 01 | Mode visible on checkout | EB | covered (show_payment_mode DC/CC) — verify-live |
| 02–04 | Visa / Master / RuPay success, failure, cancel | EB | covered — success/failure/cancel handled via webhook + txn query; verify-live |
| 05 | Surcharge OFF → payable == amount | EB | covered — KhanaBook sends 1:1 amount |
| 06 | Surcharge ON → amount + platform charge | EB | **open** — KhanaBook does not model surcharge; if ON, collected > bill amount and reconciliation flags mismatch |
| 07 | Success webhook received by merchant | KB | covered — hash+amount+bill checks, durable retry; tests: `EasebuzzWebhookTest`, `EasebuzzIntegrationTest`; verify-live |
| 08–10 | Surcharge slabs ≤1000 / ≤2000 / >2000 | EB | **open** — same surcharge gap as TC_06 |

### UPI

| TC | Scenario | Owner | KhanaBook status |
|----|----------|-------|------------------|
| 01 | UPI option visible | EB | covered (show_payment_mode UPI); verify-live |
| 02–03 | Collect success / failure | EB | covered — webhook + txn query; verify-live |
| 04–05 | QR success / failure | KB+EB | covered — dynamic order-specific UPI QR on POS (UPI QR is an **option check on the bill**, not a confirmed payment); cashier confirm + webhook/recon reconcile; verify-live |
| 06–07 | Surcharge OFF/ON | EB | **open** — same surcharge gap |
| 08 | Success webhook received | KB | covered — verify-live |

### EMI

| TC | Scenario | Owner | KhanaBook status |
|----|----------|-------|------------------|
| 01–05 | Mode visible, surcharge ON/OFF, min-amount error, ≥3000 success | EB | **out of scope — deliberate product decision.** Restaurant bills are low-ticket (EMI triggers only ≥ ₹3,000) with no installment demand, and EMI adds gateway merchant fees + riskier settlement. `show_payment_mode=CC,DC,NB,UPI,WALLET` stays as-is. |

### NETBANKING

| TC | Scenario | Owner | KhanaBook status |
|----|----------|-------|------------------|
| S_01 | Mode visible | EB | covered (show_payment_mode NB); verify-live |
| S_02–03 | Surcharge OFF/ON | EB | **open** — same surcharge gap |
| S_04–05 | Retail / corporate netbanking success | EB | covered (NB enabled in link flow); corporate banks on gateway; verify-live |

### WALLET

| TC | Scenario | Owner | KhanaBook status |
|----|----------|-------|------------------|
| TC_01 | Mode visible | EB | covered (show_payment_mode WALLET); verify-live |
| TC_02–03 | Surcharge OFF/ON | EB | **open** — same surcharge gap |
| TC_04 | Wallet success | EB | covered; verify-live |

## Go-live open items

1. **Surcharge OFF (verify, no code change)** — KhanaBook does not pass fees to the customer; surcharge is **not implemented** by design. Confirm the **production Easebuzz dashboard has surcharge OFF**; if ON, every card/NB/UPI/EMI/wallet payment above slab over-collects and `EasebuzzReconciliationService` flags amount mismatch (lines 137–138). Add as a dashboard check on go-live.
2. **Production verification of `/refund/v1/retrieve`** — contract confirmed by ERA + official Java kit (`merchant_refund_id`, hash `key|easebuzz_id|salt`; JSON body supported, form-urlencoded primary). Final acceptance still to be exercised against production (see `EASEBUZZ_REFUND_STATUS_CONTRACT_2026-09-23.md`).
3. **Live webhook delivery** — set payment/refund webhook URL in the production Easebuzz dashboard; confirm receipt on a real txn (TC_07/TC_08 across modes).
4. **Per-mode live evidence** — run the matrix's TC_01–04 (Visa/Master/RuPay, UPI collect+QR, NB retail/corporate, wallet) against the production sub-merchant and record results in the PASS/FAIL column once available.
5. **EMI** — **resolved: stay excluded** (deliberate product decision; restaurant bills are low-ticket, no installment demand).

## Operational coverage (core-essential vs this matrix)

- Merchant onboarding (Online Payments Setup, Settlement KYC, agreement): **outside matrix** — Easebuzz dashboard process, already done (see Agreement architecture below).
- Money in (paid-by-customer link/QR + webhooks): matrix TC_01–04, TC_07/08 — implemented, needs live evidence.
- Money back (refunds + durable delayed refund): `/refund/v1/retrieve` + `RefundService` — implemented, prod-verify pending.
- Settlement/recon (daily 6 AM IST, mismatch/orphan/missing-webhook alerts): implemented — surcharge OFF check protects it.
- Reporting + merchant control: Easebuzz dashboard (master merchant) + Kbook admin — outside matrix.

## Agreement architecture (verified 2026-09-23)

There are **two distinct agreements**; do not conflate them.

1. **KhanaBook Merchant Agreement (platform layer)** — KhanaBook's own terms of service with the restaurant owner, digitally acknowledged at registration. KhanaBook-owned; independent of Easebuzz.
2. **Easebuzz agreements (gateway layer)** — owned/executed by Easebuzz, not KhanaBook:
   - **Services Agreement** (Easebuzz ↔ KhanaBook as master/CLIENT API user): executed at onboarding. Easebuzz sign-up flow: KYC upload → business-rep call → *"Agreement is signed and pricing is transparently shared over your registered email ID"* (easebuzz.in/pricing, "Process of sign-up").
   - **Sub-merchant e-agreement** (KhanaBook master ↔ each restaurant): handled via Easebuzz's sub-merchant onboarding (Create/Express Onboarding + KYC access-key; KYCID+MID delivered by webhook). Digital, dashboard/API-driven.
   - Easebuzz Terms of Use (easebuzz.in/terms/) is the Services Agreement template; its e-consent clause states electronic delivery satisfies the legal in-writing requirement. Settlement is via Escrow account, max T+3.

Decision: restaurants' payment agreements/KYC belong to Easebuzz's flow; KhanaBook does not duplicate them. KhanaBook keeps only its own Merchant Agreement.

## External sources — verified via web research (2026-09-23)

Reference set for gateway claims in this doc (ERA Easebuzz provided docs; independent web verification where noted):

- Create Sub-Merchant API: docs.easebuzz.in `af94640eeae86`
- Update Sub-Merchant API: docs.easebuzz.in `94318e0f55958`
- Generate Sub-Merchant KYC Access Key API: docs.easebuzz.in `2b48a38b084b9`
- Express Onboarding API (registration + KYCID/MID webhook, PAN/GSTIN verify): docs.easebuzz.in `/express-onboarding-api/` (independent webfind)
- Webhooks: docs.easebuzz.in `587zy3v064so6` (ERA's earlier link `rt8pognkld0wb` is only the "Payment Methods" page — when pointed at it, use `587zy3v064so6`)
- Refund V2 `/transaction/v2/refund`: docs.easebuzz.in `c2ac48618b3bd`
- Refund Status `/refund/v1/retrieve`: docs.easebuzz.in `de78eba8de53c` (params `easebuzz_id`; hash `key|easebuzz_id|salt` — matches `EasebuzzRefundStatusContractTest`)
- Payout API / settlement report: payout docs + hash `merchant_key|start_date|end_date` (independent webfind)
- Payment Links product (150+ modes, webhook per txn, PCI-DSS L1): payment-links use-case page (independent webfind)
- ePOS (payment link + QR, UPI QR collection): ePOS product page (independent webfind)
- Slices = Easebuzz's split-payments product; single-charge split alternative if ever needed (independent webfind)
- Surcharge: set at **master-aggregator** level in the Easebuzz console (ERA-confirmed); supports verify-OFF go-live item #1
- RBI: Easebuzz authorised Payment Aggregator — RBI Cert. of Auth. No. 252/2025 (independent webfind)
- GitHub official libs confirm hash sequences (link/reverse/webhook and refund-status)

Note: `docs.easebuzz.in` is JS-rendered and bot-blocked; all field-level schemas above still require a human pasting the page body (webhooks, `/refund/v1/retrieve`) or production evidence to be locked end-to-end.

## Evidence inventory (existing tests)

- `EasebuzzIntegrationTest`, `EasebuzzWebhookTest`, `EasebuzzReconciliationServiceTest`, `EasebuzzNewFeaturesTest`, `RefundServiceTest`, `EasebuzzRefundStatusContractTest`.
- Slice docs: `SAAS_SLICE_15_REFUND_ATTEMPT_LEDGER.md`, `SAAS_SLICE_16_DURABLE_DELAYED_REFUND.md`, `SAAS_SLICE_17_DURABLE_POST_SPLIT.md`, `EASEBUZZ_REFUND_STATUS_CONTRACT_2026-09-23.md`.