# KhanaBook Easebuzz Sub-Merchant Business Model — v2

> **Scope**: End-to-end documentation of how KhanaBook (platform) uses Easebuzz sub-merchant APIs to onboard restaurant partners, collect payments, split funds, and manage commissions.

> **Official Easebuzz Docs**: https://docs.easebuzz.in/

---

## Official API Reference Links

| Feature | Official Doc URL |
|---------|------------------|
| Create Sub-Merchant | https://docs.easebuzz.in/docs/payment-gateway/af94640eeae86-create-sub-merchant-api |
| Update Sub-Merchant | https://docs.easebuzz.in/docs/payment-gateway/94318e0f55958-update-sub-merchant-api |
| Generate KYC Access Key | https://docs.easebuzz.in/docs/payment-gateway/2b48a38b084b9-generate-sub-merchant-kyc-access-key-api |
| Confirm / Verify OTP | https://docs.easebuzz.in/docs/payment-gateway/ifudkz7jpfadk-confirm-otp-verify-otp-api |
| Resend OTP | https://docs.easebuzz.in/docs/payment-gateway/3b9hki8thr6pn-resend-otp-api |
| Cancel Transaction | https://docs.easebuzz.in/docs/payment-gateway/vjykxhu9v65zf-cancel-transaction-api |
| Payout API v2 | https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2 |
| Split Label Create | https://docs.easebuzz.in/docs/payment-gateway/e383e3fb60ce3-create-api |
| Split Retrieve | https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api |
| On-Demand Settlement | https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is |
| Payment Initiate | https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api |
| Payment Status | https://docs.easebuzz.in/docs/payment-gateway/j6v1nmy74m325-status-api |
| Refund Status | https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api |
| Refund API v2 | https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2 |

---

## 1. Executive Summary

KhanaBook is a SaaS POS + online-ordering platform for restaurants. In v2, we integrate **Easebuzz** as the primary payment gateway using its **sub-merchant (marketplace)** capability.

**Why sub-merchants?**
- Each restaurant partner needs its own KYC-verified identity under KhanaBook's master merchant account.
- Enables **automated settlement splitting**: customer pays KhanaBook's Easebuzz account, funds are auto-split between the restaurant (sub-merchant) and KhanaBook (platform commission).
- Ensures regulatory compliance (KYC, bank account verification) per RBI guidelines.

**Key stakeholders:**
- **KbookAdmin**: Platform operator who onboards restaurants and manages the Easebuzz pipeline.
- **Restaurant Owner**: Uses the Android POS app; their payout account is the sub-merchant.
- **Customer**: Pays via Easebuzz (UPI, cards, netbanking) during checkout.
- **Easebuzz**: Gateway + settlement engine.

---

## 2. Business Problem & Opportunity

### Problem (v1)
- v1 used a single merchant account. All customer payments landed in one pool.
- Manual reconciliation: the operations team had to calculate commissions and transfer money to each restaurant via IMPS/NEFT.
- High operational overhead, delayed settlements, and no per-restaurant transaction traceability.

### Opportunity (v2)
- **Easebuzz Marketplace APIs** allow a master merchant (KhanaBook) to create child merchants (restaurants).
- Each child merchant gets:
  - A unique `submerchant_id` (e.g., `S360DILA`)
  - KYC-verified bank account for settlements
  - A split label (e.g., `sm_S360DILA`) used for per-transaction fund routing
- **Result**: Automatic T+1 (or T+2) settlements directly to each restaurant's bank account, minus platform commission.

---

## 3. Revenue Model

### 3.1 Commission Structure

| Layer | Who | What |
|-------|-----|------|
| **Customer** | Pays full bill amount | ₹1,000 |
| **Easebuzz** | Gateway fee (deducted by Easebuzz before payout) | ~1.8% – 2.2% (depends on method) |
| **KhanaBook Platform** | Platform commission (configured per sub-merchant) | e.g., 2% – 5% |
| **Restaurant** | Receives remainder after gateway fee + commission | ₹1,000 – (gateway fee + commission) |

### 3.2 Configurable Per-Merchant Rates

The `commissionRate` field on `EasebuzzSubMerchant` allows KbookAdmin to set individual commission percentages:

- A high-volume partner may negotiate **1.5%**.
- A standard partner pays **3.0%**.
- A premium partner with extra services may pay **5.0%**.

**Additional deduction fields** (stored in entity):
- `upiDeductionLtLimit`: Deduction % for UPI transactions below a threshold.
- `dcDeductionGtTwoThousand`: Deduction % for debit card transactions above ₹2,000.

These are currently stored for reporting/future auto-calculation but the actual split API uses a fixed amount per transaction.

### 3.3 Revenue Flow Example

```
Customer pays ₹1,000 via UPI
        │
        ▼
Easebuzz captures ₹1,000
        │
        ▼
Post-Transaction Split API called by KhanaBook backend:
  - Label "sm_S360DILA"  → ₹970.00  (restaurant net)
  - Label "kb_commission" → ₹30.00  (KhanaBook commission)
        │
        ▼
Easebuzz settles:
  - ₹970.00 → Restaurant's bank account (T+1)
  - ₹30.00  → KhanaBook's master bank account (T+1)
  - Gateway fee already deducted by Easebuzz from gross
```

> **Note**: In practice, the gateway fee is deducted by Easebuzz from the **total transaction amount** before split funds are released. KhanaBook's backend split configuration must account for this if the restaurant expects exact net amounts.

---

## 4. Sub-Merchant Lifecycle

### 4.1 State Machine

```
[DRAFT]
   │
   │ KbookAdmin clicks "Submit to Easebuzz"
   ▼
[PENDING_KYC] ──(assign ID manually)──► [PENDING_KYC]
   │
   │ KbookAdmin clicks "Generate KYC Access Key"
   │ Restaurant owner uploads docs via KYC portal
   │
   │ Webhook received: status = "True"
   ▼
[KYC_SUBMITTED] / [ACTIVE]
   │
   │ KbookAdmin clicks "Create Split Label"
   ▼
[ACTIVE] + splitLabel = "sm_xxx"
   │
   │ Restaurant starts accepting live payments
   ▼
[SUSPENDED] ──(reactivate)──► [ACTIVE]
   │
   ▼
[REJECTED]
```

### 4.2 Stage Definitions

| Status | Meaning |
|--------|---------|
| `DRAFT` | Local DB record only. Not yet created in Easebuzz. |
| `PENDING_KYC` | Created in Easebuzz (`submerchant_id` assigned). Waiting for KYC document upload. |
| `KYC_SUBMITTED` | KYC documents submitted by restaurant. Under Easebuzz review. |
| `ACTIVE` | KYC approved. Split label created. Ready to accept payments. |
| `SUSPENDED` | Temporarily disabled. No new payments, but historical data retained. |
| `REJECTED` | KYC rejected or manually rejected by admin. Requires re-onboarding. |
| `FAILED` | Easebuzz API creation failed (e.g., invalid IFSC, duplicate email). |

---

## 5. Fund Flow & Settlement Architecture

### 5.1 Two Types of Split

Easebuzz provides two distinct APIs. KhanaBook uses both:

#### A. Split Label Create (`/split/v1/create`)
- **One-time setup** per sub-merchant.
- Links the sub-merchant's bank account to a string label.
- Hash: `key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt`
- The label (e.g., `sm_S360DILA`) becomes the routing identifier for all future transactions.

#### B. Post-Transaction Split (`/post-split/v1/create/`)
- **Per-transaction** fund distribution.
- Called **after** the customer payment is successful.
- Hash: `key|merchant_request_id|easebuzz_id|salt`
- Configuration array defines how much goes to each label.
- **Max 3 update attempts** per transaction. After 3, the split is locked.

### 5.2 Settlement Timing

- Easebuzz typically settles on **T+1** (next business day).
- The split configuration determines which bank accounts receive what portion.
- If post-split is not called, the entire amount stays in the master merchant account.

### 5.3 Reconciliation

KhanaBook backend stores:
- `easebuzz_id` per bill/payment.
- `merchant_request_id` (generated by us, e.g., `KB_1712345678_123`).
- Split configuration snapshot.

This allows nightly reconciliation between:
- Easebuzz settlement reports
- KhanaBook's internal transaction ledger
- Commission report (`commission-report` page)

---

## 6. Commission Engine

### 6.1 How Commission is Applied

Currently, the commission is **not auto-deducted by Easebuzz**. KhanaBook's backend calculates it and includes it in the post-split configuration:

```java
// Pseudo-logic inside Post-Transaction Split creation
transactionAmount = 1000.00;
commissionRate = subMerchant.getCommissionRate(); // e.g., 3.0
commissionAmount = transactionAmount * commissionRate / 100; // 30.00
restaurantAmount = transactionAmount - commissionAmount; // 970.00

configuration = [
  { label: "sm_S360DILA", amount: "970.00" },
  { label: "kb_commission", amount: "30.00" }
];
```

> **Future enhancement**: Use Easebuzz's native commission deduction feature if enabled on the master account, which would simplify the split to a single restaurant label.

### 6.2 Commission Report

The `commission-report` page shows:
- Per-restaurant gross revenue.
- Commission rate applied.
- Net commission earned by KhanaBook.
- Period filters (daily, weekly, monthly).

### 6.3 Commission Config

The `commission-config` page lets KbookAdmin:
- View all sub-merchants and their current rates.
- Edit rates inline (stored in `EasebuzzSubMerchant.commissionRate`).
- Track `updatedAt` for audit purposes.

---

## 7. Roles & Responsibilities

### 7.1 KbookAdmin (Platform Operator)

**Permissions**: `KBOOK_ADMIN` role
**Access**: `/admin/*` routes

**Responsibilities**:
1. Create sub-merchant draft in local DB.
2. Submit to Easebuzz API (`submit-to-easebuzz`).
3. Generate KYC portal link (`kyc-access-key`) and share with restaurant owner.
4. Monitor KYC webhook events.
5. Approve/reject KYC or mark active manually.
6. Create split label (`split-label`).
7. Configure commission rates.
8. Handle disputes, refunds, and settlement reconciliation.

### 7.2 Restaurant Owner (Business User)

**Permissions**: `OWNER` role
**Access**: `/business/*` routes

**Responsibilities**:
1. Complete KYC via the portal link sent by KbookAdmin.
2. Verify bank account details are correct in Settings.
3. Accept payments via Android POS (Easebuzz SDK V2).
4. View business dashboard for today's revenue, pending orders, and setup status.
5. Cannot view other restaurants' data.

### 7.3 Customer

- Pays via Easebuzz checkout (Android POS or web).
- No awareness of sub-merchant architecture; sees KhanaBook branding.

---

## 8. Technical Architecture

### 8.1 Component Diagram

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  Android POS    │────▶│  KhanaBook API   │────▶│  Easebuzz Gateway   │
│  (Easebuzz SDK) │     │  (Spring Boot)   │     │  pay.easebuzz.in    │
└─────────────────┘     └──────────────────┘     └─────────────────────┘
        │                        │                         │
        │                        │                         │
        ▼                        ▼                         ▼
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│  Web Admin      │────▶│  PostgreSQL      │◄────│  Easebuzz Dashboard│
│  (Angular)      │     │  (sub_merchants) │     │  dashboard.easebuzz  │
└─────────────────┘     └──────────────────┘     └─────────────────────┘
```

### 8.2 Key Server Components

| File | Responsibility |
|------|----------------|
| `EasebuzzApiClient.java` | Raw HTTP calls to Easebuzz (7 methods). |
| `SubMerchantService.java` | Business logic for onboarding, KYC, split labels. |
| `EasebuzzPaymentService.java` | Initiate payments, check status, refunds. |
| `EasebuzzWebhookService.java` | Handles incoming webhooks from Easebuzz. |
| `AdminSubMerchantController.java` | 10 REST endpoints for web admin. |
| `BusinessAdminController.java` | Business-facing endpoints (profile, orders, dashboard). |

### 8.3 Android Components

| File | Responsibility |
|------|----------------|
| `EasebuzzSdkPaymentRepository.kt` | Native SDK V2 integration (`in.easebuzz:android-v2:1.0.6`). |
| `EasebuzzPaymentRepository.kt` | Fallback/server-side payment initiation. |
| `PaymentConfigSection.kt` | Owner-facing payment settings (redirects to web dashboard for onboarding). |
| `EasebuzzPaymentScreen.kt` | Checkout UI using SDK V2. |
| `EasebuzzKycScreen.kt` | KYC status display (if embedded). |

---

## 9. API Orchestration

### 9.1 Hash Sequence Reference

All Easebuzz APIs require SHA-512 hashes. The sequences below are verified against official Stoplight docs.

| API | Hash Sequence | Official Doc |
|-----|---------------|--------------|
| Initiate Payment | `key\|txnid\|amount\|productinfo\|firstname\|email\|udf1\|udf2\|udf3\|udf4\|udf5\|\|\|\|\|\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api) |
| Transaction Status V2.1 | `key\|txnid\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/j6v1nmy74m325-status-api) |
| Cancel Transaction | `key\|txnid\|amount\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/vjykxhu9v65zf-cancel-transaction-api) |
| Refund API v2 | `key\|txnid\|amount\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2) |
| Refund Status | `key\|txnid\|refund_id\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api) |
| Create Sub-Merchant | `key\|submerchant_email\|submerchant_phone\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/af94640eeae86-create-sub-merchant-api) |
| Update Sub-Merchant | `key\|submerchant_email\|submerchant_phone\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/94318e0f55958-update-sub-merchant-api) |
| KYC Access Key | `merchant_key\|sub_merchant_id\|name\|email\|phone\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/2b48a38b084b9-generate-sub-merchant-kyc-access-key-api) |
| Confirm / Verify OTP | `merchant_key\|sub_merchant_id\|otp\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/ifudkz7jpfadk-confirm-otp-verify-otp-api) |
| Resend OTP | `merchant_key\|sub_merchant_id\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/3b9hki8thr6pn-resend-otp-api) |
| Split Label Create | `key\|name_on_bank\|bank_name\|branch_name\|ifsc_code\|account_number\|label\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/e383e3fb60ce3-create-api) |
| Post-Split Create | `key\|merchant_request_id\|easebuzz_id\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2) |
| Post-Split Retrieve | `key\|merchant_request_id\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api) |
| Payout API v2 | `key\|merchant_request_id\|amount\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2) |
| On-Demand Settlement | `key\|merchant_request_id\|amount\|salt` | [Link](https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is) |

### 9.2 Backend-to-Easebuzz Flow

```
1. Android POS calls POST /api/v2/payments/easebuzz/create-order
2. Backend generates hash, calls Easebuzz Initiate API
   Docs: https://docs.easebuzz.in/docs/payment-gateway/q3zhxyn3v4xbv-initiate-api
3. Easebuzz returns payment URL / SDK params
4. Customer completes payment on Easebuzz checkout
5. Easebuzz hits POST /api/v2/payments/easebuzz/webhook
6. Backend verifies hash, updates bill status = PAID
7. Backend calls POST /post-split/v1/create/ (max 3 attempts)
   Docs: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2
8. Easebuzz confirms split configuration
   Docs: https://docs.easebuzz.in/docs/payment-gateway/d333db79c5c82-retrieve-api
9. T+1 settlement occurs to respective bank accounts
10. If refund needed → POST /transaction/v2/refund
    Docs: https://docs.easebuzz.in/docs/payment-gateway/c2ac48618b3bd-refund-api-v2
11. Poll refund status → POST /transaction/v2/refund_status
    Docs: https://docs.easebuzz.in/docs/payment-gateway/de78eba8de53c-refund-status-api
```

---

## 10. Webhook Strategy

### 10.1 Payment Webhook

**Endpoint**: `POST /api/v2/payments/easebuzz/webhook`

**Payload**: Standard Easebuzz transaction data (`txnid`, `easebuzz_id`, `status`, `amount`, `hash`, etc.)

**Actions**:
1. Verify `hash` before processing.
2. Update `BillPayment` status.
3. Trigger post-transaction split if status = `success`.
4. If split fails, queue for retry (max 3 attempts).

### 10.2 Sub-Merchant Webhook

**Endpoint**: `POST /api/v2/payments/easebuzz/submerchant-webhook`

**Payload**:
```json
{
  "status": "1",
  "data": {
    "submerchant_id": "S360DILA",
    "status": "True"
  }
}
```

**Status mapping**:
- `"True"` → `kycStatus = "ACTIVATED"`, `status = "ACTIVE"`
- `"False"` → `kycStatus = "REJECTED"`
- `"Pending"` → `kycStatus = "SUBMITTED"`, `status = "KYC_SUBMITTED"`

**Actions**:
1. Lookup `EasebuzzSubMerchant` by `submerchant_id`.
2. Update `kycStatus`, `kycActivatedAt`, and overall `status`.
3. Notify KbookAdmin via dashboard priority actions feed.

---

## 11. Android POS Integration

### 11.1 Payment Flow (SDK V2)

```kotlin
// Inside EasebuzzPaymentScreen
EasebuzzSDK.getInstance().open(
    context = activity,
    paymentParams = params, // Contains txnid, amount, hash, etc.
    listener = object : PaymentListener {
        override fun onSuccess(response: String) { ... }
        override fun onFailure(response: String) { ... }
    }
)
```

### 11.2 KYC Flow

Since KYC requires document upload (PAN, bank proof, ID proof), the Android app does **not** handle it directly. Instead:

1. KbookAdmin generates a KYC access key.
2. The KYC portal URL is shared with the restaurant owner (via WhatsApp/SMS/email).
3. The owner opens the URL in a browser, uploads documents.
4. Webhook updates status back to KhanaBook.

### 11.3 Configuration

The Android app reads `easebuzzEnabled` from `RestaurantProfileEntity`. If enabled:
- `PaymentMode.ONLINE` is offered in the New Bill screen.
- PaymentConfigSection shows "Easebuzz Payments" toggle (read-only; redirects to web dashboard for actual setup).

---

## 12. KYC & Compliance

### 12.1 Required Documents

| Document | Purpose | Storage |
|----------|---------|---------|
| PAN Card | Tax identity | `idProofUrl` (S3 / CDN URL) |
| Cancelled Cheque / Bank Statement | Bank account verification | `bankProofUrl` |
| Business PAN / GST | Business identity | `gst`, `pan` fields |

### 12.2 KYC Aging

The web admin now shows a **KYC Age** column:
- `Done` → Activated
- `2d 4h` → Time since KYC submission or draft creation
- Helps KbookAdmin prioritize follow-ups.

### 12.3 Data Retention

- `EasebuzzSubMerchantWebhookEvent` stores all incoming webhooks for audit.
- `EasebuzzWebhookEvent` stores payment webhooks.
- Retention policy: 90 days for raw payloads, indefinite for status transitions.

---

## 13. Error Handling & Retry Policy

### 13.1 Post-Split Retry

```
Attempt 1: Immediate (after payment webhook)
Attempt 2: +5 minutes (scheduled job)
Attempt 3: +15 minutes (scheduled job)
After 3 failures: Alert KbookAdmin. Manual intervention required.
```

### 13.2 Common Error Codes

| Code | Meaning | Action |
|------|---------|--------|
| `EBPTSURVE01` | Duplicate `merchant_request_id` | Use retrieve API to check existing config. |
| `EBPTSURVE06` | Max update attempts reached | Alert admin; transaction settled as-is. |
| `EBPTSUTVE04` | Transaction already settled | Cannot split after settlement window closes. |
| `EBPTSUTVE05` | Refunds exist against transaction | Block split if partial refund initiated. |

### 13.3 Idempotency

- `merchant_request_id` is generated as `KB_<timestamp>_<billId>`.
- Easebuzz treats duplicate IDs as no-ops (after successful creation), preventing double-splitting.

---

## 14. Sandbox vs Production

### 14.1 Environments

| Env | Dashboard | Payment URL | Config Key |
|-----|-----------|-------------|------------|
| Sandbox | `https://testdashboard.easebuzz.in` | `https://testpay.easebuzz.in` | `test` |
| Production | `https://dashboard.easebuzz.in` | `https://pay.easebuzz.in` | `prod` |

### 14.2 Switching

- Server: `application-dev.properties` vs `application-prod.properties`.
- Android: `easebuzz_env` column in `RestaurantProfileEntity` (default `test`).
- Web Admin: Always points to the backend API; backend handles Easebuzz env routing.

### 14.3 Testing Checklist (Sandbox)

1. Create sub-merchant → Verify `submerchant_id` returned.
2. Generate KYC access key → Verify URL opens.
3. Simulate KYC approval webhook → Verify status becomes `ACTIVE`.
4. Create split label → Verify `status: true`.
5. Initiate test payment (₹1.00) → Use test card/UPI.
6. Verify webhook received and bill marked PAID.
7. Call post-split create → Verify configuration accepted.
8. Call post-split retrieve → Verify amounts match.
9. Verify no `duplicate merchant_request_id` errors on retry.

---

## 15. Security Considerations

1. **Hash Verification**: Every Easebuzz callback is hash-verified before action. Never trust payload alone.
2. **Salt Storage**: `easebuzz.salt` is stored in server config (`application.properties`), never exposed to client.
3. **Webhook IPs**: Optionally whitelist Easebuzz webhook IPs at firewall/load-balancer level.
4. **KYC URLs**: KYC access keys are time-sensitive (generated per-request). Do not cache or log the full URL containing tokens.
5. **Bank Data**: Bank account numbers are masked in UI (show last 4 digits only) and encrypted at rest where possible.

---

## 16. Database Schema (Key Tables)

### `easebuzz_sub_merchant`

| Column | Type | Purpose |
|--------|------|---------|
| `id` | BIGINT | PK |
| `restaurant_id` | BIGINT | Links to restaurant profile |
| `sub_merchant_id` | VARCHAR | Easebuzz ID (e.g., `S360DILA`) |
| `status` | VARCHAR | `DRAFT`, `PENDING_KYC`, `ACTIVE`, etc. |
| `business_name` | VARCHAR | Legal business name |
| `pan` | VARCHAR | PAN number |
| `gst` | VARCHAR | GSTIN |
| `bank_account_no` | VARCHAR | Settlement account |
| `ifsc` | VARCHAR | IFSC code |
| `bank_name` | VARCHAR | Bank name |
| `branch_name` | VARCHAR | Branch name |
| `beneficiary_name` | VARCHAR | Name as per bank |
| `kyc_status` | VARCHAR | `PENDING`, `SUBMITTED`, `ACTIVATED`, `REJECTED` |
| `kyc_submitted_at` | BIGINT | Epoch ms |
| `kyc_activated_at` | BIGINT | Epoch ms |
| `commission_rate` | NUMERIC(5,2) | Platform % |
| `upi_deduction_lt_limit` | NUMERIC(5,2) | Custom UPI deduction % |
| `dc_deduction_gt_two_thousand` | NUMERIC(5,2) | Custom DC deduction % |
| `split_label` | VARCHAR | `sm_{sub_merchant_id}` |
| `id_proof_url` | TEXT | KYC doc URL |
| `bank_proof_url` | TEXT | Bank proof URL |
| `easebuzz_response` | JSONB | Raw API response for debugging |
| `created_at` | BIGINT | Epoch ms |
| `updated_at` | BIGINT | Epoch ms |

---

## 17. Glossary

| Term | Definition |
|------|------------|
| **Sub-merchant** | A child merchant under KhanaBook's master Easebuzz account. Each restaurant = 1 sub-merchant. |
| **Split Label** | A string alias (`sm_xxx`) linked to a bank account, used in post-transaction splits. |
| **Post-Transaction Split** | Dividing a single payment into multiple payouts **after** the transaction succeeds. |
| **KYC Access Key** | A temporary token that generates a URL for the restaurant owner to upload KYC docs. |
| **Merchant Request ID** | KhanaBook-generated unique ID for a split attempt (`KB_<ts>_<billId>`). |
| **Easebuzz ID** | The gateway transaction ID returned by Easebuzz after payment initiation. |
| **T+1 Settlement** | Funds credited to bank account on the next business day after transaction. |

---

## 18. POS Features & Workflows (Android App)

This section describes the full Easebuzz-related feature set available to the **Restaurant Owner** inside the KhanaBook Android POS app.

---

### 18.1 Sub-merchant Onboarding from POS

**Where**: `SettingsScreen.kt` → `PaymentConfigSection.kt`

**Flow**:
1. Owner taps **"Easebuzz Payments"** toggle.
2. Since onboarding requires document upload (PAN, cancelled cheque, bank proof), the app **redirects to the Web Admin Dashboard** via browser:
   ```kotlin
   val base = BuildConfig.WEB_ADMIN_URL.trimEnd('/')
   val url = "$base/payments/easebuzz"
   context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
   ```
3. The owner logs into the Web Admin (Business Console), navigates to **Settings → Payment Config**.
4. KbookAdmin (or the owner, depending on self-onboarding permissions) fills:
   - Business name, PAN, GSTIN
   - Bank account number, IFSC, bank name, branch name
   - Beneficiary name
   - Contact email & phone
5. KbookAdmin clicks **Submit to Easebuzz** from the admin panel.
6. Backend calls `POST /merchant/v1/submerchant/create`.
7. On success, `submerchant_id` (e.g., `S360DILA`) is stored in `EasebuzzSubMerchant.subMerchantId`.

**Owner visibility in POS**:
- A read-only chip shows **"Easebuzz: Pending KYC"** or **"Easebuzz: Active"** inside `PaymentConfigSection`.
- The toggle itself is **not editable** from POS — it reflects the backend status.

---

### 18.2 KYC Status Check

**Where**: `PaymentConfigSection.kt` / `SettingsScreen.kt`

**API**: `GET /api/v2/restaurants/payment-config/easebuzz/sub-merchant-status`

**Response fields** (shown to owner):
| Field | Meaning |
|-------|---------|
| `subMerchantStatus` | `NOT_STARTED`, `DRAFT`, `PENDING_KYC`, `KYC_SUBMITTED`, `ACTIVE`, `REJECTED` |
| `subMerchantId` | Easebuzz ID (e.g., `S360DILA`) |
| `kycPortalUrl` | Link to KYC document upload portal (if pending) |
| `kycSubmittedAt` | Timestamp of submission |
| `kycActivatedAt` | Timestamp of approval |

**UI Behavior**:
- `NOT_STARTED` → Show **"Setup Easebuzz"** button. Opens web dashboard.
- `PENDING_KYC` → Show **"Complete KYC"** button with KYC portal URL.
- `KYC_SUBMITTED` → Show **"Under Review"** badge + time since submission.
- `ACTIVE` → Show **"Active"** badge + split label (`sm_xxx`).
- `REJECTED` → Show **"KYC Rejected"** warning + **"Resubmit"** action.

---

### 18.3 KYC Resubmission

**Trigger**: KYC status = `REJECTED` (webhook received `status: "False"`).

**Flow**:
1. Owner sees red **"KYC Rejected"** banner in `PaymentConfigSection`.
2. Owner taps **"Resubmit KYC"**.
3. App calls `POST /api/v2/payments/easebuzz/regenerate-kyc` (or KbookAdmin regenerates from admin panel).
4. Backend calls Easebuzz **Generate KYC Access Key** API again:
   - Hash: `merchant_key|sub_merchant_id|name|email|phone|salt`
5. New KYC portal URL is returned.
6. Owner opens the URL, updates/corrects documents, and re-submits.
7. Webhook eventually updates status back to `SUBMITTED` → `ACTIVATED`.

**Notes**:
- There is **no limit** on how many times KYC can be resubmitted.
- However, each resubmission requires a **fresh access key** (old URLs expire).

---

### 18.4 Transactions — Payment Initiation

**Where**: `NewBillScreen.kt` → Payment mode selection → `EasebuzzPaymentScreen.kt`

**Flow**:
1. Staff creates a bill in POS (`NewBillScreen`).
2. At checkout, payment modes are shown:
   - Cash
   - UPI QR
   - POS Machine
   - **Easebuzz Online** (if `easebuzzEnabled == true`)
3. Staff selects **Easebuzz Online**.
4. App navigates to `EasebuzzPaymentScreen` with:
   - `restaurantId`, `billId`, `amount`
5. App calls backend: `POST /api/v2/payments/easebuzz/create-order`
   - Backend generates `txnid`, calculates hash, and calls Easebuzz initiate API.
6. Backend returns payment params (or a payment URL).
7. App opens Easebuzz SDK V2:
   ```kotlin
   EasebuzzSDK.getInstance().open(
       context = activity,
       paymentParams = params,
       listener = object : PaymentListener {
           override fun onSuccess(response: String) { ... }
           override fun onFailure(response: String) { ... }
       }
   )
   ```
8. Customer completes payment on their phone (UPI / card / netbanking).

---

### 18.5 Transactions — Status Tracking

**Where**: `OrdersScreen.kt` → Bill detail view

**API**: `GET /api/v2/payments/easebuzz/status/{billId}`

**States**:
| State | Meaning | UI Indicator |
|-------|---------|--------------|
| `INITIATED` | Order created, waiting for customer | Yellow spinner |
| `PENDING` | Customer is on Easebuzz checkout page | Yellow spinner |
| `SUCCESS` | Payment confirmed by Easebuzz | Green checkmark |
| `FAILED` | Payment failed / cancelled | Red cross |
| `REFUNDED` | Full refund processed | Blue "Refunded" label |
| `PARTIAL_REFUND` | Partial refund processed | Blue "Partial Refund" label |

**Polling Strategy**:
- App polls every **5 seconds** for 2 minutes after initiating payment.
- If webhook arrives first, backend updates DB and polling returns immediately.
- After 2 minutes, app shows **"Check Status"** button for manual refresh.

---

### 18.6 Transaction History

**Where**: `OrdersScreen.kt` → Filters → "Online Payments" tab

**Displayed columns**:
- Order code
- Customer name
- Amount
- Payment method (UPI / Card / Netbanking)
- Easebuzz Transaction ID (`easebuzzId`)
- Status chip
- Date/time

**Actions per row**:
- View receipt
- Initiate refund (if status = `SUCCESS` and within refund window)
- Re-send payment link (if status = `FAILED` or `INITIATED`)

---

### 18.7 Refunds — Raising

**Where**: `OrdersScreen.kt` → Long-press or action menu on a paid order

**Flow**:
1. Staff selects **"Raise Refund"**.
2. Dialog opens showing:
   - Original amount
   - Already refunded amount (if any)
   - Remaining refundable amount
3. Staff enters refund amount (can be partial) and reason:
   - "Customer request"
   - "Order cancelled"
   - "Item unavailable"
   - "Other"
4. App calls: `POST /api/v2/payments/easebuzz/refund/{billId}`
   ```json
   {
     "refundAmount": 250.00,
     "reason": "Customer request"
   }
   ```
5. Backend calls Easebuzz Refund API:
   - Hash: `key|txnid|amount|salt`
   - `txnid` is the original transaction ID.
6. Easebuzz returns `easebuzz_refund_id`.
7. Backend updates `BillPayment.refundStatus = "PENDING"`.

**Constraints**:
- Cannot refund more than original amount.
- Cannot refund if original transaction is older than Easebuzz's refund window (typically 180 days).
- If post-split was already created and settled, refund may fail — backend handles this gracefully.

---

### 18.8 Refunds — Tracking

**Where**: `OrdersScreen.kt` → Refund status column

**States**:
| State | Meaning |
|-------|---------|
| `NOT_REQUESTED` | No refund yet |
| `PENDING` | Refund request sent to Easebuzz, awaiting processing |
| `PROCESSED` | Easebuzz confirmed refund initiated |
| `FAILED` | Refund rejected by Easebuzz (e.g., after settlement window) |
| `COMPLETED` | Refund credited to customer's account (T+2 to T+5) |

**Polling**:
- App polls `GET /api/v2/payments/easebuzz/status/{billId}` every 30 seconds for 10 minutes after raising refund.
- Webhook from Easebuzz updates status asynchronously.

**Refund Receipt**:
- Once `COMPLETED`, app can generate a refund receipt PDF showing:
  - Original transaction ID
  - Refund ID (`easebuzzRefundId`)
  - Refund amount
  - Date/time
  - Reason

---

### 18.9 Notifications

The Android app receives push notifications (FCM) for Easebuzz-related events:

| Notification Type | Trigger | Content |
|-------------------|---------|---------|
| **Payment Success** | Webhook confirms `status = success` | "₹450 received from Rahul for Order #KB1234" |
| **Payment Failure** | Webhook confirms `status = failure` | "Payment failed for Order #KB1234. Retry or use alternate method." |
| **KYC Approved** | Sub-merchant webhook `status = "True"` | "Your Easebuzz account is now active. Start accepting online payments!" |
| **KYC Rejected** | Sub-merchant webhook `status = "False"` | "KYC rejected. Please resubmit documents. Tap to open portal." |
| **KYC Submitted** | Sub-merchant webhook `status = "Pending"` | "KYC documents submitted. Under review by Easebuzz team." |
| **Refund Initiated** | `POST /refund/{billId}` returns 200 | "Refund of ₹250 initiated for Order #KB1234." |
| **Refund Completed** | Webhook / polling confirms refund success | "Refund of ₹250 has been credited to the customer's account." |
| **Settlement Reminder** | Cron job at 9 AM daily | "Your yesterday's earnings of ₹3,240 will be settled to your bank account today." |

**Notification Deep Links**:
- Payment Success → `khanabook://orders/{orderId}`
- KYC events → `khanabook://settings/payment`
- Refund events → `khanabook://orders/{orderId}/refund`

---

### 18.10 Error Handling in POS

| Error | Cause | User-Facing Message |
|-------|-------|---------------------|
| `HASH_MISMATCH` | Server-side hash calculation error | "Payment setup failed. Please try again." |
| `SUBMERCHANT_INACTIVE` | Owner tries to accept payment before KYC approval | "Your Easebuzz account is not active yet. Complete KYC to accept online payments." |
| `REFUND_WINDOW_CLOSED` | Easebuzz no longer allows refund for this txn | "Refund not possible. Transaction settlement period has expired." |
| `SPLIT_ALREADY_SETTLED` | Post-split funds already disbursed | "Refund pending manual review. Please contact support." |
| `NETWORK_ERROR` | Device offline / API timeout | "No internet connection. Payment status will sync when online." |

---

## 19. Additional Easebuzz APIs (Documented for Future Use)

### 19.1 Confirm / Verify OTP API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/ifudkz7jpfadk-confirm-otp-verify-otp-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/verify_otp`
- **Hash**: `merchant_key|sub_merchant_id|otp|salt`
- **Purpose**: Verify the OTP sent to the sub-merchant's registered mobile/email during onboarding.
- **Current Status**: Not used in KhanaBook v2. KYC is handled via portal link instead of OTP.

### 19.2 Resend OTP API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/3b9hki8thr6pn-resend-otp-api
- **Endpoint**: `POST https://dashboard.easebuzz.in/submerchant/v1/resend_otp`
- **Hash**: `merchant_key|sub_merchant_id|salt`
- **Purpose**: Resend OTP to the sub-merchant's registered contact.
- **Current Status**: Not used in KhanaBook v2.

### 19.3 Cancel Transaction API

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/vjykxhu9v65zf-cancel-transaction-api
- **Endpoint**: `POST https://pay.easebuzz.in/transaction/v1/cancel`
- **Hash**: `key|txnid|amount|salt`
- **Purpose**: Cancel a pending transaction before it is captured/settled.
- **Current Status**: Not exposed to POS. Can be used in future for cancelling unconfirmed payments after timeout.

### 19.4 Payout API v2

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/xsrzv37a3ksv0-payout-api-v2
- **Endpoint**: `POST https://dashboard.easebuzz.in/payout/v2/transfer`
- **Hash**: `key|merchant_request_id|amount|salt`
- **Purpose**: Direct payout to a bank account or UPI ID. Can be used for instant commission payouts or emergency settlements.
- **Current Status**: Not used. Future enhancement for on-demand restaurant payouts.

### 19.5 On-Demand Settlement APIs

- **Official Doc**: https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is
- **Endpoint**: `POST https://dashboard.easebuzz.in/settlement/v1/on_demand`
- **Hash**: `key|merchant_request_id|amount|salt`
- **Purpose**: Request an immediate settlement of available balance instead of waiting for T+1 auto-settlement.
- **Current Status**: Not exposed. Can be added as a premium feature for high-volume partners.

---

## 20. Future Enhancements

1. **Auto KYC Status Polling**: If webhook is missed, poll `/submerchant/v1/status` periodically.
2. **Commission Auto-Deduct**: Use Easebuzz's native commission feature (if enabled on master account) instead of manual post-split calculation.
3. **Settlement Reconciliation Report**: Nightly cron that compares Easebuzz settlement CSV vs KhanaBook ledger.
4. **Multi-Gateway Support**: Abstract payment interface to support Razorpay Route or Cashfree Split in parallel.
5. **KYC Document Upload via App**: Allow owners to upload PAN/bank docs directly from Android, reducing friction.
6. **POS Offline Payment Queue**: Queue Easebuzz payments when offline; auto-sync when connectivity returns.
7. **Instant Settlement (T+0)**: If Easebuzz enables instant payouts, add toggle in `PaymentConfigSection`.
8. **Sub-merchant Self-Service Password Reset**: Allow KbookAdmin to trigger password reset from admin panel so owners can log into Easebuzz dashboard independently.

---

*Document version: v2.2 — aligned with `v2` branch*
*Last updated: 2026-05-17*
