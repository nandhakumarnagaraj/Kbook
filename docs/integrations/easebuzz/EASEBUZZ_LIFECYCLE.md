# Easebuzz lifecycle — from sub-merchant onboarding to settlement

How a restaurant goes from "no online payments" to "money in its bank account", and every
operation in between. Verified against code on 2026-10-17.

Model (confirmed by ERA, 2026-10-17): **KhanaBook is the parent merchant**; **each restaurant
is a sub-merchant**, settled to its own bank account. Easebuzz has no document-ingest API, so
KYC documents are uploaded by the merchant on Easebuzz's hosted portal, not stored by us.

```
DRAFT → PENDING_KYC → KYC_SUBMITTED / CPV_PENDING → ACTIVE
             └──────────── REJECTED (merchant resubmits) ────────┘
                          FAILED (submit error)
```

## 0. Objects and who owns what

| Object | Owner | Created by |
|---|---|---|
| Parent merchant (KhanaBook) | KhanaBook | Easebuzz dashboard, once |
| Sub-merchant row | KhanaBook DB | `SubMerchantService.create` |
| Sub-merchant at Easebuzz | Easebuzz | `submitToEasebuzz` → `/submerchant/create` |
| Split label `sm_<subMerchantId>` | Easebuzz | after KYC approval |
| Bill payment | KhanaBook | `PaymentController.createPaymentLinkForBill` |
| Settlement / payout | Easebuzz (wire) | automatic, or on-demand |

## 1. Draft — DRAFT

The owner opens Settings → Payments → Easebuzz onboarding, or an admin creates it at
`POST /admin/sub-merchants`. Fields captured: business name, **legal entity name** (must match
PAN/GST — this is what CPV compares), business type, PAN, GST (optional), FSSAI, address,
state, contact email/phone, bank account/IFSC/beneficiary, bank name/branch.

## 2. Submit — gates run server-side

`POST /restaurants/payment-config/easebuzz/onboard` (owner) or
`/admin/sub-merchants/{id}/submit-to-easebuzz` (admin, requires the two proof documents). `SubMerchantService.submitToEasebuzz` blocks a
submission that would fail CPV:

| Rule | Meaning |
|---|---|
| `LEGAL_ENTITY_NAME_REQUIRED` | registered name missing → CPV name mismatch |
| `FSSAI_REQUIRED` | 14-digit FSSAI licence missing |
| `MANDATORY_FIELDS_MISSING` | PAN, address, state, account, IFSC, bank name, branch, beneficiary, email, phone |
| `BUSINESS_PROOFS_REQUIRED` | proprietorship with no business/address proof (admin flow) |
| `BUSINESS_PROOF_TYPES_REQUIRED` | proof uploaded but type not set |
| `BUSINESS_PROOF_TYPES_NOT_DISTINCT` | both proofs of the same type |
| `BUSINESS_PROOF_TYPE_NOT_ACCEPTED` | rent/lease agreement as the only proof |

On success Easebuzz returns `submerchant_id`, row becomes **PENDING_KYC**.

## 3. OTP — proves the phone belongs to the merchant

Easebuzz sends an OTP to the registered phone. The owner enters it in the app
(`OtpVerificationStep` → `verifyOtp`); resend is rate-limited (`resend-otp`). OTP must be
verified before the KYC portal opens.

## 4. KYC upload — merchant self-service on Easebuzz's portal

`generate_kyc_access_key` returns a KYC URL; the app shows "Open Easebuzz KYC Portal" and the
merchant **signs in themselves** (KhanaBook cannot upload on their behalf).

| Entity | Mandatory documents | Owner |
|---|---|---|
| Sole proprietor | PAN, Aadhaar, bank proof, FSSAI, Udyam/MSME | Proprietor |
| Partnership | PAN (firm), partnership deed, bank proof, FSSAI, Udyam/MSME | Firm / partners |
| LLP | PAN (LLP), COI, bank proof, FSSAI | LLP / designated partners |
| Private limited | PAN (company), COI, bank proof, FSSAI | Company / directors |
| Trust / society | PAN (trust), registration deed, bank proof, FSSAI | Trust / signatory |

- **GST is optional** (ERA, in writing).
- Aadhaar may be masked or XML; passport / voter ID / driving licence accepted instead when
  the merchant has PAN + FSSAI + Udyam.
- For a sole proprietorship, documents in the owner's name count for the business.
- KhanaBook carries no storage or verification liability for portal-uploaded documents.

## 5. CPV — Easebuzz's verification

Status becomes **KYC_SUBMITTED** or **CPV_PENDING** (agentless CPV link shown in-app).
Outcome arrives only via the **`MERCHANT_KYC_APPROVAL`** webhook — there is no Aadhaar-specific
webhook and no polling API:

- approved → **ACTIVE**, but only when `kyc_status=true` **and** `bank_status=true`. Easebuzz
  reports bank verification separately; with the bank unverified the row stays pending and is
  not activated (hardened 2026-10-18).
- rejected → **REJECTED**, reason surfaced on the rejection screen
- still pending → stays **CPV_PENDING**

A rejection is fixed by editing the details and submitting through
`POST /restaurants/payment-config/easebuzz/resubmit` (a REJECTED row cannot go back through
`/onboard` — the server answers `ONBOARDING_ALREADY_SUBMITTED`).

## 6. Activation — split label

On approval a split label is created so the restaurant's share routes to its own bank account:
first `sm_<subMerchantId>`, then `sm_<subMerchantId>_v1`, `_v2`, … A bank-account change is
detected by comparing `splitLabelBankSnapshot` with the current account+IFSC and automatically
creates the next version — Easebuzz has no label-update API, so rotation is the only way.

## 7. Collect payments

| Path | Entry point |
|---|---|
| Bill payment (POS) | `POST /payments/easebuzz/create-link-for-bill` → hosted checkout |
| Ad-hoc link | `POST /payments/easebuzz/create-link` |
| FSSAI/renewal order | `POST /payments/easebuzz/create-fssai-order` |
| Dynamic UPI QR | POS screen, per-bill VPA |

Confirmation is webhook-first (`/payments/easebuzz/webhook`), with
`GET /payments/easebuzz/status/{billId}` and `POST /payments/easebuzz/verify/{billId}` as the
pull-based fallback. `/payments/easebuzz/return` handles the browser return.

## 8. Settlements

- **Automatic**: **T+1** (next business day) to the sub-merchant account, cut-off ~6 PM IST;
  the payout webhook fires after settlement. Each payment carries the split so the restaurant
  receives `total − commission − MDR − GST-on-MDR`.
- **On-demand**: `POST /admin/sub-merchants/settlements/on-demand`; retrieve with
  `GET /admin/sub-merchants/settlements/retrieve`.
- **Instant settlement**: `GET /business/settlements/estimate` (fee preview) then
  `POST /business/settlements/instant`.
- **Payouts** to a saved beneficiary: `POST /admin/sub-merchants/payout`. The payout row is
  written as `initiated`, moves to `pending` when Easebuzz accepts it (with `payout_id`) and
  `failed` with the error message when it does not; final state arrives on `/payout/webhook`.

## 9. Refunds

`POST /payments/easebuzz/refund/{billId}` (full or partial), status via
`GET /payments/easebuzz/refund-status/{billId}` and `/refund/webhook`. The customer sees the
money in **3–5 business days**; the callback carries status `processed` (event
`REFUND_PROCESSED`) — `refunded` is also accepted for older callbacks. Refunds are attempted
once and retried only for safe (non-mutating) gateway failures; a
`POST /payments/easebuzz/cancel/{billId}` covers cancelling an unpaid transaction.

## 10. Chargebacks / disputes

`GET /business/chargebacks/summary`, `GET /business/chargebacks/reason-codes`,
`POST /business/chargebacks` (raise), `POST /business/chargebacks/{id}/resolve`, and
`POST /business/chargebacks/score/{billId}` for a pre-payment risk score.

Easebuzz enables **chargebacks and instant settlement for the parent merchant only**;
sub-merchants inherit those settings. Our restaurant-scoped endpoints therefore work only
while KhanaBook's parent account has the feature enabled.

## 11. Status tracking and reconciliation

| Signal | Source |
|---|---|
| Onboarding / KYC state | `GET /restaurants/payment-config/easebuzz/sub-merchant-status`, `MERCHANT_KYC_APPROVAL` webhook |
| Payment success/failure | `/webhook`, `GET /status/{billId}`, `POST /verify/{billId}` |
| Refund state | `/refund/webhook`, `GET /refund-status/{billId}` |
| Payout/settlement | `/payout/webhook`, wire settlement retrieve |
| Reconciliation | `GET /admin/sub-merchants/reconciliation`, `/reconciliation/splits`, `EasebuzzReconciliationService` |

All webhooks are IP-allowlisted (`WebhookIpAllowlistFilter`) and signature-checked. Failed
deliveries go to `WebhookRetryService` (`PAYMENT`, `REFUND`, `POST_SPLIT`) and are retried with
backoff, so a dropped callback does not lose a payment.

## 12. Failure paths

| Failure | Behaviour |
|---|---|
| Gate rejects submit | 4xx with the rule code; nothing reaches Easebuzz |
| Easebuzz create fails | row → **FAILED**, retried after fixing credentials/data |
| KYC rejected | **REJECTED**; owner edits and submits via `/resubmit` |
| Webhook lost | retry queue; `/verify` re-checks the bill |
| Merchant offline mid-payment | bill stays unpaid; POS can re-verify once online |

## 13. Code map

| Concern | File |
|---|---|
| Onboarding + gates + status | `services/server/.../payments/service/SubMerchantService.java` |
| Easebuzz HTTP | `.../service/EasebuzzApiClient.java`, `EasebuzzWireApiClient.java` |
| Webhooks | `.../service/EasebuzzWebhookService.java`, `PaymentController` |
| Refunds | `.../service/RefundService.java` |
| Settlements | `InstantSettlementService.java`, `AdminSettlementController` |
| Reconciliation | `EasebuzzReconciliationService.java` |
| Android flow | `apps/android/.../payments/ui/EasebuzzOnboardingScreen.kt`, `viewmodel/EasebuzzOnboardingViewModel.kt` |

## 14. Open items

1. **MCC — implemented 2026-10-17.** Easebuzz locks the code at creation, so the onboarding
   form now requires an **outlet format** (dine-in / QSR / bar / caterer), the server stores
   it in `easebuzz_sub_merchant.restaurant_format`, refuses a submit without it, and maps it
   to 5812 / 5814 / 5813 / 5811 in `EasebuzzApiClient.mccCodeForFormat`. Sub-merchants created
   **before** this change are stuck on 5812 — see the recreation runbook below.
   Easebuzz does **not** expose the MCC on the sub-merchant retrieve API, and the
   `MERCHANT_KYC_APPROVAL` payload has no `business_details` block either, so
   `restaurant_format` on our own row is the only audit trail of what we sent.
2. `Public Limited` / OPC **are** supported (ERA, 2026-10-17) — no change needed.
3. `InstaCollect` targets subscription merchants, not a restaurant POS — out of scope.
4. Bank-account change: Easebuzz prefers the update API (`/submerchant/create/` with the new
   details); our automatic split-label rotation is optional but recommended for tracking, and
   is what we do today.
5. No in-app Aadhaar capture — by design, per ERA (portal-only is sufficient).
6. **KYC expiry — stored, not enforced.** The approval payload's `kyc_expiry_date` is now
   persisted (`easebuzz_sub_merchant.kyc_expiry_date`, V111) and returned by the sub-merchant
   status endpoint. Nothing acts on it yet: when it lapses the sub-merchant can no longer
   accept payments and Easebuzz sends no expiry webhook, so a scheduled poll of the
   Get Sub-merchant Details API is still needed to catch it.

### Recreating a sub-merchant for a wrong MCC (ERA, 2026-10-17)

Easebuzz will **not** change an MCC on request, in any status — including `PENDING_KYC` where no
payments have been taken yet. Recreation is the only remedy, and it is disruptive:

- a **new `submerchant_id`** is issued;
- KYC documents must be **re-uploaded** (nothing carries over) and the merchant redoes **OTP +
the KYC portal**;
- the old split label and any settlements stay attached to the **old** sub-merchant, so
  reconciliation needs manual adjustment;
- Easebuzz recommends recreating pending-KYC rows *before* approval, and planning manual
  reconciliation for active ones.

Local blocker to plan around: `SubMerchantService.delete` only allows `DRAFT` / `FAILED` rows,
and `easebuzz_sub_merchant` has `UNIQUE (restaurant_id)`. Recreating therefore needs an admin
"reset/archive" action (or a manual DB step) for `PENDING_KYC` / `ACTIVE` rows — it cannot be
done from the app today.
