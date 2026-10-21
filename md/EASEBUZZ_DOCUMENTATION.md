# Easebuzz API Documentation - KhanaBook Implementation

*Generated from codebase analysis of `services/server/` — reflects actual implementation as of 2026-10-21.*

---

## 📖 Overview

KhanaBook integrates with Easebuzz (RBI-licensed payment aggregator) for:
- Customer payments (UPI, cards, netbanking)
- Sub-merchant onboarding and management
- Refund processing
- Payouts and settlements
- FSSAI compliance renewal payments
- Daily reconciliation

All monetary operations use **SHA-512 hash verification** with sequences defined by Easebuzz.

---

## 🔐 Hash Verification Sequences

Every webhook and API call includes a `hash` field computed as `sha512(<sequence>)`. The hash ensures request integrity and authenticity.

### Payment Webhook (Reverse Sequence)
```
sha512(salt|status|udf10|udf9|udf8|udf7|udf6|udf5|udf4|udf3|udf2|udf1|email|firstname|productinfo|amount|txnid|key)
```
- **18 fields** in reverse order from what Easebuzz sends
- All UDFs (udf1-udf10), email, firstname, productinfo, amount, txnid included
- Verified in `EasebuzzWebhookService.verifyWebhookHash()`

### Refund Webhook
```
sha512(key|easepayid|salt)
```
- 3-field sequence: merchant key | easepayid (or easebuzz_id) | salt
- Verified in `EasebuzzWebhookService.verifyRefundWebhookHash()`

### Settlement Payout Webhook
```
sha512(key|payout_id|salt)
```
- Scenario 1: Basic payout hash
- Verified in `EasebuzzWebhookService.verifyPayoutHash()`

### Transfer (Payout V2) Webhook
```
sha512(key|beneficiary_account_number|ifsc|beneficiary_upi_handle|unique_request_number|amount|unique_transaction_reference|status|salt)
```
- Scenario 2: Full V2 payout hash with 8 fields
- Verified in `EasebuzzWebhookService.verifyPayoutHash()`

### Sub-merchant Webhook
```
sha512(key|submerchant_id|salt)
```
- 3-field sequence for KYC/status webhooks
- Verified in `EasebuzzWebhookService.verifySubMerchantWebhookHash()`

---

## 🌐 API Endpoints

### 1. Create Sub-Merchant
| Field | Description |
|---|---|
| **Endpoint** | `POST /merchant/v1/submerchant/create/` |
| **Hash** | `sha512(key|email|phone|salt)` |
| **Key Fields** | sub_merchant_name, email, phone, account_number, ifsc, bank_name, business_type, PAN, GST, FSSAI number, business_address, legal entity name, MCC code |
| **Implementation** | `EasebuzzApiClient.createSubMerchant()` |
| **Notes** | MCC code mapped from restaurant format (5812/5814/5813/5811). FSSAI mandatory for food merchants. |

### 2. Generate KYC Access Key
| Field | Description |
|---|---|
| **Endpoint** | `POST /submerchant/v1/generate_kyc_access_key` |
| **Hash** | `sha512(merchant_key|sub_merchant_id|name|email|phone|salt)` |
| **Purpose** | Generate one-time access key for KYC document retrieval |
| **Implementation** | `EasebuzzApiClient.generateKycAccessKey()` |

### 3. Verify OTP
| Field | Description |
|---|---|
| **Endpoint** | `POST /submerchant/v1/verify_otp` |
| **Hash** | `sha512(merchant_key|sub_merchant_id|otp|salt)` |
| **Purpose** | Validate OTP for KYC process |
| **Implementation** | `EasebuzzApiClient.verifyOtp()` |

### 4. Resend OTP
| Field | Description |
|---|---|
| **Endpoint** | `POST /submerchant/v1/resend_otp` |
| **Hash** | `sha512(merchant_key|sub_merchant_id|salt)` |
| **Purpose** | Resend KYC OTP to registered phone/email |
| **Implementation** | `EasebuzzApiClient.resendOtp()` |

### 5. Payment Initiate (Create Payment Link)
| Field | Description |
|---|---|
| **Endpoint** | `POST /payment/initiateLink` |
| **Hash** | `sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5|udf6|udf7|udf8|udf9|udf10|salt)` |
| **Params** | key, txnid, amount, productinfo, firstname, email, phone, surl, furl, notify_url, hash, udf1-udf10 |
| **Implementation** | `EasebuzzApiClient.initiatePayment()` |
| **Notes** | All 10 UDF fields must be sent as params since they're in the hash sequence. UPI QR mode supported. |

### 6. Get Transaction Status
| Field | Description |
|---|---|
| **Endpoint** | `GET /transaction/v2.1/retrieve` on `dashboardBaseUrl` |
| **Hash** | `sha512(key|txnid|salt)` |
| **Params** | key, txnid, hash |
| **Implementation** | `EasebuzzApiClient.getTransactionStatus()` |
| **Notes** | Uses `dashboardBaseUrl` (default `dashboard.easebuzz.in`), **not** `paymentBaseUrl`. Read-only lookup, safe to repeat. |

### 7. Initiate Refund (API v2)
| Field | Description |
|---|---|
| **Endpoint** | `POST /transaction/v2/refund` on `dashboardBaseUrl` |
| **Hash** | `sha512(key|merchant_refund_id|easebuzz_id|refund_amount|salt)` |
| **Body Params** | key, merchant_refund_id, easebuzz_id, refund_amount, hash |
| **Implementation** | `EasebuzzApiClient.initiateRefund()` |
| **Notes** | `merchant_refund_id` format: `REF_{billId}_{timestamp}`. Idempotent at gateway via this field. |

### 8. Get Refund Status
| Field | Description |
|---|---|
| **Endpoint** | `GET /refund/v1/retrieve` on `dashboardBaseUrl` |
| **Hash** | `sha512(key|easebuzz_id|salt)` |
| **Params** | key, easebuzz_id, merchant_refund_id (optional), hash |
| **Implementation** | `EasebuzzApiClient.getRefundStatus()` |
| **Critical** | **Keys on easebuzz_id, NOT txnid** per ERA 2026-09-09. JSON body also accepted. |

### 9. Create Payment Link (Easy Collect)
| Field | Description |
|---|---|
| **Endpoint** | `POST /easy_collect` on `paymentBaseUrl` |
| **Hash** | `sha512(key|merchant_txn|name|email|phone|amount|udf1|udf2|udf3|udf4|udf5|message|salt)` |
| **Params** | key, merchant_txn, name, email, phone, amount, message, udf1-udf5, hash |
| **Implementation** | `EasebuzzApiClient.createPaymentLink()` |
| **Notes** | Used for "Send Payment Link" flow. UDF1 typically set to billId for webhook reconciliation. |

### 10. Create Split Label
| Field | Description |
|---|---|
| **Endpoint** | `POST /split/v1/create` on `dashboardBaseUrl` |
| **Hash** | `sha512(key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt)` |
| **Body Params** | key, hash, name_on_bank, bank_name, branch_name, ifsc_code, account_number, label, payout_percentage |
| **Implementation** | `EasebuzzApiClient.createSplitLabel()` |

### 11. Post-Split Create/Retrieve
| Field | Description |
|---|---|
| **Create** | `POST /post-split/v1/create/` | Hash: `sha512(key|merchant_request_id|easebuzz_id|salt)` |
| **Retrieve** | `POST /post-split/v1/retrieve/` | Hash: `sha512(key|merchant_request_id|salt)` |
| **Implementation** | `EasebuzzApiClient.updateTransactionSplit()` / `retrieveTransactionSplit()` |

### 12. Initiate Payout (Transfer)
| Field | Description |
|---|---|
| **Endpoint** | `POST /payout/v2/transfer` on `dashboardBaseUrl` |
| **Hash** | `sha512(key|merchant_request_id|amount|salt)` |
| **Body** | key, merchant_request_id, amount, hash, + beneficiary details (account, ifsc, upi_handle) |
| **Implementation** | `EasebuzzApiClient.initiatePayout()` |

### 13. On-Demand Settlement
| Field | Description |
|---|---|
| **Endpoint** | `POST /settlement/v1/on_demand` on `dashboardBaseUrl` |
| **Hash** | `sha512(key|merchant_request_id|amount|salt)` |
| **Implementation** | `EasebuzzApiClient.initiateOnDemandSettlement()` |

### 14. Cancel Transaction
| Field | Description |
|---|---|
| **Endpoint** | `POST /transaction/v1/cancel` |
| **Status** | **DEPRECATED** — Returns error: "Cancel API not supported. Transactions auto-expire in 15 min." |
| **Implementation** | `EasebuzzApiClient.cancelTransaction()` (marked `@Deprecated`) |
| **Notes** | ERA confirmed: no cancel API. Unpaid txnids auto-expire in 15 minutes. |

### 15. Create FSSAI Renewal Order
| Field | Description |
|---|---|
| **Endpoint** | Internal: `EasebuzzPaymentService.createFssaiRenewalOrder()` → `initiatePayment()` |
| **Hash** | Same as payment initiate (full 18-field sequence) |
| **Amount** | ₹1000 per year of FSSAI license |
| **UDF1** | Set to `"fssai_renewal"` for webhook routing |
| **Implementation** | `EasebuzzPaymentService.createFssaiRenewalOrder()` |

---

## 📡 Webhook Endpoints (Server Receives)

| Event | URL | Hash Verification |
|---|---|---|
| **Payment Webhook** | `POST /payments/easebuzz/webhook` | `sha512(salt\|status\|udf10..udf1\|email\|firstname\|productinfo\|amount\|txnid\|key)` |
| **Refund Webhook** | `POST /payments/easebuzz/refund/webhook` | `sha512(key\|easepayid\|salt)` |
| **Sub-merchant Webhook** | `POST /payments/easebuzz/sub-merchant/webhook` | `sha512(key\|submerchant_id\|salt)` (or unhashed KYC approval in dev/sandbox) |
| **Payout Webhook** | `POST /payments/easebuzz/payout/webhook` | `sha512(key\|payout_id\|salt)` or `sha512(key\|beneficiary_account\|ifsc\|upi_handle\|request_number\|amount\|utr\|status\|salt)` |

**All webhooks** verify hash before processing. Failed hash → `hash_mismatch` response, no side effects.

---

## 🏗️ Khanabook Architecture Decisions

### Role-Based Access Control
| Role | Easebuzz Access |
|---|---|
| **OWNER** | Full: create links, refunds, FSSAI orders, sub-merchant management, payouts |
| **KBOOK_ADMIN** | Web-only access (same payment ops as OWNER) |
| **SHOP_STAFF** | **403 Forbidden** — Web login rejected with `surface: 'web'` |

### Android POS Role
- **Never initiates** Easebuzz API calls
- **Consumes only**: bill payment status, gateway status, payment links
- **Receives** updates via server webhook → sync → Room DB
- **Sub-merchant data** read-only via MasterSync for cashier sign-in

### Key Design Decisions

1. **Dashboard Base URL**: All status/refund/payout APIs use `dashboardBaseUrl` (`dashboard.easebuzz.in`), **not** `paymentBaseUrl` (`pay.easebuzz.in`). Confirmed intentional — likely different gateway network endpoints.

2. **Refund Status Keys on easebuzz_id**: ERA 2026-09-09 confirmed: refund status lookup uses `easebuzz_id` (not `txnid`). Code cites this follow-up explicitly.

3. **No Cancel API**: Deliberate — ERA confirmed cancel doesn't exist. Unpaid transactions auto-expire in 15 minutes. Code returns friendly error message.

4. **Undocumented MCC Field**: `sub_merchant_mcc_code` sent at sub-merchant creation via `mccCodeForFormat()`. Not in local doc reference but Easebuzz accepts it at creation time.

5. **Hash Order is Reverse**: Payment webhook hash is computed in reverse order from the API initiate hash. The initiate payment sends hash over `key|txnid|amount|...|salt`, but the webhook receives `sha512(salt|status|udf10|...|udf1|email|firstname|productinfo|amount|txnid|key)`. Both must match Easebuzz's expectations.

6. **FSSAI Renewal Integration**: Internal flow — creates FssaiRenewal record → initiates payment link with `udf1=fssai_renewal` → webhook routes to `handleFssaiRenewalSuccess()` → updates FssaiTracker status to `RENEWAL_PAID`.

7. **Composite Key Isolation**: All SQL queries enforce `restaurant_id = :restaurantId`. Never trust client-provided tenant IDs; extract from verified JWT claims.

8. **Monotonic status_version**: Bill status changes increment `status_version` to prevent stale sync packet overwrites.

---

## ⚠️ Known Discrepancies (Code vs Local Doc Reference)

| # | Discrepancy | Code Behavior | ERA Confirmation |
|---|---|---|---|
| 1 | Refund API v2 hash sequence | `key\|merchant_refund_id\|easebuzz_id\|refund_amount\|salt` (5 fields) | Code comment cites this as official; local doc says `key\|txnid\|amount\|salt` (3 fields) |
| 2 | Refund status endpoint + hash | `POST /refund/v1/retrieve` + `key\|easebuzz_id\|salt` | Local doc: `POST /transaction/v2/refund_status` + `key\|txnid\|refund_id\|salt` |
| 3 | Host for status/refund APIs | Uses `dashboardBaseUrl` | Local doc references `pay.easebuzz.in` for payment/status/refund APIs |
| 4 | Sub-merchant MCC field | Sends `sub_merchant_mcc_code` at creation | Not in official doc reference; ERA said `business_details.sub_merchant_business_nature` carries entity type (not MCC) |
| 5 | Cancel transaction | Returns error; auto-expire 15 min | Docs document cancel API; code deliberately skips it (ERA confirmed doesn't exist) |

**Recommendation**: Verify discrepancies #1-3 against live Easebuzz sandbox before producing production fixes. Discrepancies #4-5 are architectural choices confirmed via ERA follow-ups.

---

## 🛠️ Implementation References

| File | Purpose |
|---|---|
| `EasebuzzWebhookService.java` | Hash verification for all 4 webhook types (payment, refund, sub-merchant, payout) |
| `EasebuzzApiClient.java` | All 15+ API calls with hash generation (`generateHash()`) |
| `EasebuzzPaymentService.java` | Khanabook-specific flows: payment links, refunds, FSSAI orders, status verification |
| `EasebuzzProperties.java` | Config: merchantKey, salt, payment/dashboard/base URLs, return/notify URLs |
| `EasebuzzWebhookServiceTest.java` | 30+ unit tests verifying hash sequences and business logic |
| `BillSyncService.java` | Sync contracts (`/sync/push`, `/sync/pull`) with tenant isolation |
| `V94__collapse_staff_roles.sql` | DB migration: collapsed all roles to OWNER/SHOP_STAFF/KBOOK_ADMIN |

---

## 📦 Khanabook-Specific Flows

### Payment Link Flow
```
Web Admin → createPaymentLinkForBill(billId) → 
easebuzzApi.initiatePayment(data) → 
Easebuzz returns access_token + payment_url → 
Store merchant_txn on bill → bill.paymentStatus = "link_sent" → 
Android UI shows "Pay Now" → Customer pays → 
Easebuzz webhook → handlePaymentWebhook() → bill.paymentStatus = "paid"
```

### Refund Flow
```
Web Admin → initiateRefund(billId, amount) → 
easebuzzApi.initiateRefund(merchantRefundId, easebuzzId, amount) → 
Easebuzz returns refund_id → 
Store refund_id on bill → bill.gatewayStatus = "refund_initiated" → 
Web Admin checks refund status → 
Easebuzz webhook → handleRefundWebhook() → bill.paymentStatus = "refunded"/"partially_refunded"
```

### FSSAI Renewal Flow
```
Web Admin → createFssaiRenewalOrder(years, fssaiNumber, restaurantId) → 
Generates txnid → creates payment link with udf1="fssai_renewal" → 
Customer pays → webhook → handleFssaiRenewalSuccess() → 
Updates FssaiRenewal.status = "SUCCESS" + FssaiTracker.status = "RENEWAL_PAID"
```

### Sub-Merchant Onboarding Flow
```
Web Admin → createSubMerchant(details) → 
Easebuzz returns sub_merchant_id → 
Generate KYC access key → 
Verify OTP → 
Complete KYC → 
Sub-merchant ACTIVE → 
Other restaurants can use sub-merchant for payments
```

---