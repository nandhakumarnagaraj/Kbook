# KhanaBook Easebuzz Integration

*How KhanaBook uses Easebuzz payment services — architecture, flows, and role boundaries.*

---

## 🏢 Overall Architecture

KhanaBook uses Easebuzz as the **RBI-licensed payment aggregator** for digital collections from restaurants (sub-merchants) to their bank accounts. The integration follows an **offline-first, web-admin-exclusive** model:

| Aspect | Design Decision |
|---|---|
| **Primary Surface** | Web Admin dashboard only — all Easebuzz operations originate here |
| **Android POS** | Read-only consumer — displays status, never initiates API calls |
| **Role Model** | OWNER full access; SHOP_STAFF web access denied (403); KBOOK_ADMIN web access |
| **Data Tenant Isolation** | Every SQL query enforces `restaurant_id = :restaurantId`; JWT claims used, not client-provided |
| **Hash Verification** | SHA-512 on all inbound webhooks and outbound API calls — strict sequence compliance |
| **Sync Strategy** | Server webhook → bill state update → MasterSync → Android Room DB delta pull |

---

## 📋 Core Integration Flows

### 1. Payment Link Creation (Most Used Flow)

**When**: Waiter presents bill → Customer chooses "Pay via Link" → System generates UPI QR/dynamic QR

**Steps**:
1. **Web Admin** clicks "Send Payment Link" on bill screen
2. API: `createPaymentLinkForBill(billId, restaurantId)`
3. Validation checks:
   - Bill not already paid
   - No active link already sent (`link_sent` status)
   - Sub-merchant is ACTIVE on Easebuzz (Merchant Agreement requirement retired in `V116`)
   - Easebuzz Online enabled for this restaurant
4. Builds data map with:
   - `merchant_txn` (unique: `PL` + bill tail + UUID suffix)
   - `name`, `email`, `phone` (customer or sub-merchant contact)
   - `amount` (bill total, 2 decimal places)
   - `udf1` = `billId` (CRITICAL: webhook uses this to reconcile)
   - `show_payment_mode` = `"CC,DC,NB,UPI,WALLET"` (excludes QR per product requirement)
5. Calls `easebuzzApi.initiatePayment(data)` → gets `access_token` + `payment_url`
6. Stores `merchant_txn` on bill, sets `paymentStatus = "link_sent"`, `gatewayStatus = "link_created"`
7. **Android UI**: Shows "Pay Now" button linking to the payment URL
8. **Webhook**: When customer pays → `handlePaymentWebhook()` → bill `paymentStatus = "paid"`

**Key Detail**: `udf1=billId` is the reconciliation hook. The webhook extracts `udf1`, resolves the bill via `findByGatewayTxnId(udf1)`, and marks it paid.

---

### 2. Refund Flow

**When**: Customer returns item/meal; owner processes partial/full refund

**Steps**:
1. **Web Admin** clicks "Process Refund" on paid bill
2. API: `initiateRefund(billId, amount, reason)`
3. Validation:
   - Bill must have `gatewayTxnId` (original payment link)
   - Resolves `easebuzzId` from original payment webhook event (stored in `EasebuzzWebhookEvent`)
   - If no `easebuzzId` → error: "Original Easebuzz payment ID unavailable"
   - Generates `merchantRefundId` = `REF_{billId}_{cumulative_refund_amount}`
   - Calls `easebuzzApi.initiateRefund(merchantRefundId, easebuzzId, amount)`
4. Easebuzz returns `refund_id` + status
5. Stores `refundId` on bill, sets `gatewayStatus = "refund_initiated"`
6. **Subsequent calls**: `getRefundStatus(billId)` → queries `POST /refund/v1/retrieve`
   - **Critical**: Hash uses `key\|easebuzz_id\|salt` — keys on `easebuzz_id`, NOT `txnid`
   - ERA 2026-09-09 confirmed this contract
7. Refund states tracked:
   - `INITIATED` → `COMPLETED` (when amount matches)
   - `COMPLETED` → `partially_refunded` if multiple partial refunds
   - Fully refunded → `refunded` + `orderStatus = "cancelled"`

**Android UI**: Shows refund status alongside payment status; cashier can see if refund was processed.

---

### 3. FSSAI Renewal Order

**When**: Restaurant owner renews FSSAI license (1-5 years)

**Steps**:
1. **Web Admin** → `createFssaiRenewalOrder(years, fssaiNumber, restaurantId)`
2. Validation:
   - `years` between 1-5
   - `fssaiNumber` must be exactly 14 digits
   - Generates `amount` = `years × ₹1000`
   - Generates `txnid` = `KBF` + restaurant tail + 8-char UUID suffix
3. Creates `FssaiRenewal` record (status: `PENDING`, `easebuzzTxnId` = generated txnid)
4. Builds payment data:
   - `udf1` = `"fssai_renewal"` (critical for webhook routing)
   - `udf2` = `restaurantId`
   - `udf3` = `fssaiNumber`
   - `udf4` = `years.toString()`
   - `email`/`phone` from onboarded sub-merchant or defaults
5. Calls `easebuzzApi.initiatePayment(data)` → payment link generated
6. **Webhook routing**: `udf1=fssai_renewal` → `handleFssaiRenewalSuccess()`
7. On success:
   - `FssaiRenewal.status` = `"SUCCESS"`
   - `FssaiTracker.status` = `"RENEWAL_PAID"` + `lastUpdatedOn` = today
   - Push notification: "Your FSSAI renewal payment of ₹{amount} was received successfully"
   - Bill remains unpaid (separate from FSSAI compliance payment)

**Android UI**: Shows FSSAI compliance status in restaurant settings; owner can see renewal history.

---

### 4. Sub-Merchant Onboarding

**When**: New restaurant signs up on KhanaBook platform

**Steps**:
1. Collect business details: legal entity name, address, state, business type, PAN, GST, FSSAI number
2. Map restaurant format → MCC code (5812 dining, 5814 QSR, 5813 bar, 5811 caterer)
3. Call `EasebuzzApiClient.createSubMerchant()` → Easebuzz returns `sub_merchant_id`
4. Store `sub_merchant_id`, contact email/phone, business details in `EasebuzzSubMerchant` table
5. **KYC flow** (if needed):
   - `generateKycAccessKey(subMerchantId, name, email, phone)` → get one-time key
   - `verifyOtp(subMerchantId, otp)` → validate KYC documents
   - `resendOtp(subMerchantId)` → resend if needed
6. Sub-merchant becomes ACTIVE → other restaurants (under same master) can use it for payments

**Android**: Sub-merchant data consumed **read-only** via MasterSync for cashier sign-in only. No staff management UI on mobile.

---

### 5. Payout / Settlement

**When**: Restaurant wants to withdraw accumulated balance or transfer funds

**Two operations**:

**A. Initiate Payout (Transfer)**
```
API: initiatePayout(merchantRequestId, amount, beneficiaryDetails)
Hash: sha512(key|merchant_request_id|amount|salt)
Body: key, merchant_request_id, amount, hash, + beneficiary_account, ifsc, beneficiary_upi_handle
```
- Idempotent at gateway via `merchant_request_id`
- Used for one-time transfers to beneficiary bank account

**B. On-Demand Settlement**
```
API: initiateOnDemandSettlement(merchantRequestId, amount)
Hash: sha512(key|merchant_request_id|amount|salt)
```
- Also idempotent via `merchant_request_id`
- Runs daily at 6 AM IST as a reconciliation sweep (configurable cron: `0 30 6 * * *`)

**Android**: No direct interaction. Web admin controls payouts.

---

### 6. Payment Status Lookup

**When**: Owner or system wants to verify a bill's payment state

**API**: `getPaymentStatus(billId, refresh)`
- Calls `easebuzzApi.getTransactionStatus(gatewayTxnId)`
- Hash: `sha512(key|txnid|salt)`
- Response includes: `paymentStatus`, `gatewayStatus`, `txnid`, `amount`
- If `refresh=true` and bill not paid + has gatewayTxnId → refreshes from gateway
- Double-charge guard: if bill already `paid`, returns existing result without re-processing

**Android**: UI displays bill payment status (paid/pending/failed) from local Room DB, refreshed via sync.

---

## 🔐 Security & Access Control

### Role Boundaries

| Operation | OWNER | KBOOK_ADMIN | SHOP_STAFF |
|---|---|---|---|
| Create payment link | ✅ | ✅ | ❌ 403 |
| Initiate refund | ✅ | ✅ | ❌ 403 |
| Get refund status | ✅ | ✅ | ❌ 403 |
| Create FSSAI order | ✅ | ✅ | ❌ 403 |
| Manage sub-merchant | ✅ | ✅ | ❌ 403 (no UI) |
| View bill status | ✅ | ✅ | ✅ (read-only) |
| Web login with `surface: 'web'` | ✅ | ✅ | ❌ Rejected 403 `STAFF_WEB_ACCESS_DENIED` |

### Authentication Boundary
- Web login **must** send `{ surface: 'web' }`
- Backend rejects `SHOP_STAFF` with `403 Forbidden` (`STAFF_WEB_ACCESS_DENIED`)
- Android POS uses separate auth surface (not web)

### Tenant Isolation
- Every bill/query enforces `restaurant_id = :restaurantId`
- **Never** trust client-provided tenant IDs
- Identity extracted from verified JWT claims
- Android sync only pulls delta for restaurant owner's own establishment(s)

### Webhook Security
- All 4 webhook types verify hash **before** any state change
- Failed hash → `hash_mismatch` response, **no side effects**
- IP allowlist filter: only requests from Easebuzz IP ranges pass through
- Webhook URLs are registered at `/payments/easebuzz/webhook`, `/refund/webhook`, etc.

---

## 📊 Data Flow Summary

```
Web Admin (Owner)
    │
    ├───► createPaymentLinkForBill() ──► easebuzzApi.initiatePayment() ──► Easebuzz gateway
    │                                        │
    │                                        ▼
    │                               Payment link URL
    │                                        │
    │                                Customer pays on link
    │                                        │
    │                                ┌────────▼───────────┐
    │                                │ Easebuzz Webhook │◄─────┘
    │                                │ handlePaymentWebhook()
    │                                │ • Verify hash SHA-512
    │                                │ • Resolve bill via udf1=billId
    │                                │ • bill.paymentStatus = "paid"
    │                                └──────────────────────┘
    │
    ├───► initiateRefund() ──► easebuzzApi.initiateRefund() ──► Easebuzz gateway
    │                        │
    │                        ▼
    │                   Refund ID stored on bill
    │                        │
    │               getRefundStatus() ──► POST /refund/v1/retrieve
    │                        │
    └──► createFssaiRenewalOrder() ──► easebuzzApi.initiatePayment() ──► Easebuzz gateway
                               │
                               ▼
                       Webhook → handleFssaiRenewalSuccess()
                               │
                               ├──► FssaiRenewal.status = "SUCCESS"
                               ├──► FssaiTracker.status = "RENEWAL_PAID"
                               └──► Push notification sent

Android POS (Read-Only)
    │
    │   Pulls delta from MasterSync → Room DB
    │   Displays: bill.paymentStatus, gatewayStatus, payment link UI
    │   No API calls to Easebuzz endpoints
    │   Sub-merchant data: read-only via MasterSync (cashier sign-in only)
    │
    ▼
Server (Sync Layer)
    │
    │   Webhook → Bill state update → MasterSync → Room DB delta → Android UI
    │
    ▼
PostgreSQL (115+ Flyway migrations)
    │
    │   Tables: bills, bill_items, bill_payments, restaurantprofiles, users, etc.
    │   Composite key: (restaurant_id, device_id, local_id) on all synced entities
    │   Monotonic: status_version incremented on deliberate status changes
    │
    ▼
Easebuzz (External)
    │
    │   Payment gateway: UPI, cards, netbanking
    │   Sub-merchant management API
    │   Refund processing
    │   Payout/settlement
    │   Webhook callbacks
    │
    └──────────────────────────────────────────────────────────────────────
```

---

## ⚙️ Configuration Environment

| Property | Description | Default (Sandbox) |
|---|---|---|
| `easebuzz.merchant-key` | Merchant key from Easebuzz dashboard | `test_merchant_key` |
| `easebuzz.salt` | Salt for hash computation | `test_salt` |
| `easebuzz.payment-base-url` | Payment gateway host | `https://testpay.easebuzz.in` |
| `easebuzz.dashboard-base-url` | Dashboard/status/refund host | `https://testdashboard.easebuzz.in` |
| `easebuzz.return-url` | Return URL after payment | `Set via env var` |
| `easebuzz.notify-url` | Webhook notification URL | `Set via env var` |
| `easebuzz.webhook-url` | Explicit webhook endpoint URL | `Set via env var` |
| `easebuzz.wire-base-url` | Wire platform (sub-merchant KYC, etc.) | `https://testdashboard.easebuzz.in` |
| `easebuzz.reconciliation-enabled` | Daily reconciliation sweep | `false` |
| `easebuzz.kyc-expiry-check.cron` | KYC expiry daily check cron | `0 30 6 * * *` (6:30 AM IST) |
| `easebuzz.webhook.allowed-ips` | IP allowlist for webhooks | (empty - all accepted, logged) |

**Profiles**: `dev`, `sandbox`, `test`, `prod` — some behaviors differ (e.g., unhashed KYC approval accepted in dev/sandbox).

---

## 🛠️ Key Implementation Files

| File | Location | Purpose |
|---|---|---|
| `EasebuzzWebhookService.java` | `services/server/src/main/java/.../payments/service/` | Hash verification for all 4 webhook types |
| `EasebuzzApiClient.java` | Same path | All API calls + hash generation (`generateHash()`) |
| `EasebuzzPaymentService.java` | Same path | Khanabook-specific: payment links, refunds, FSSAI orders |
| `BillPaymentController.java` | `services/server/src/main/java/.../billing/controller/` | REST endpoints: `/payments/easebuzz/*` |
| `PaymentController.java` | Same path | Higher-level: `/payments/easebuzz/create-fssai-order`, status, refund |
| `EasebuzzWebhookServiceTest.java` | `services/server/src/test/.../service/` | 30+ unit tests for hash sequences + business logic |
| `WebhookIpAllowlistFilterTest.java` | Same path | IP allowlist filter for webhook security |
| `BaseSyncEntity.java` | Android core | Composite key: `(restaurant_id, device_id, local_id)` |
| `MasterSync.java` | Android sync | Delta sync from server to Room DB SQLCipher |
| `V94__collapse_staff_roles.sql` | `services/server/src/main/resources/db/migration/` | DB migration: collapsed to 3 roles |

---

## 📈 Observability

### Metrics Tracked
- Payment link creation success/failure rates
- Refund initiation success/failure rates
- Webhook delivery success/failure rates
- Hash verification failures (indicates Easebuzz side changes or replay attacks)
- Sub-merchant KYC completion rates
- FSSAI renewal order success rates

### Logs Structure
- All Easebuzz API calls logged with `log.info("... restaurantId={} txnid={} ...", ...)`
- Hash sequences logged at trace level in `EasebuzzApiClient.generateHash()`
- Webhook hash mismatches logged at `warn` level with `txnid` for debugging
- Duplicate webhook attempts logged at `info` level (replay guard triggered)

### Error Handling
- **Hash mismatch**: Returns `hash_mismatch`, no bill state change
- **Missing gatewayTxnId**: Returns `failure` + error code
- **Duplicate webhook**: Short-circuits before hash verification, returns `ok` + `"already processed"`
- **Transport failure**: Sweep retry logic (exponential backoff, max 3 attempts)
- **Idempotency**: `merchant_refund_id`, `merchant_txn`, `(txnid, status)` guard prevent double-processing

---