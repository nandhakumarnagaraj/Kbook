# Easebuzz Live-Mode Test Plan

## Prerequisites
- Live Easebuzz merchant credentials (key + salt), not sandbox
- Live `pay.easebuzz.in` and `dashboard.easebuzz.in` URLs configured in `application-prod.properties`
- Live callback/notify URLs configured on Easebuzz dashboard (valid HTTPS endpoints)
- Server running with `--spring.profiles.active=prod` or `live`

---

## 1. KYC Access Key Generation

**API**: `POST /submerchant/v1/generate_kyc_access_key`
**Hash**: `merchant_key|sub_merchant_id|name|email|phone|salt`

### Steps
1. Create a test sub-merchant in the admin UI (save it to DB without submitting to Easebuzz)
2. Click **KYC 🔑** button on the sub-merchant detail panel
3. Observe: should receive an access key URL from Easebuzz
4. Open the KYC URL in a browser — should show Easebuzz KYC document upload portal
5. Upload test documents (PAN, Aadhaar, bank proof, etc.)

### Expected Results
- [ ] API returns `status: 1` with `access_key` and `redirect_url`
- [ ] KYC portal loads correctly and accepts documents
- [ ] Sub-merchant KYC status updates via webhook or status check

---

## 2. Sub-Merchant Creation

**API**: `POST /merchant/v1/submerchant/create/`
**Hash**: `key|submerchant_email|submerchant_phone|salt` (per Easebuzz docs)

### Steps
1. In admin UI, create a new sub-merchant with all required fields:
   - Business name, email, phone
   - Bank account details (account number, IFSC, bank name, branch, name on bank)
   - Business type (SOLE_PROPRIETOR / PARTNERSHIP / PRIVATE_LIMITED)
   - PAN, GST (optional)
   - Business address
2. Click **Submit 🚀** button

### Expected Results
- [ ] API returns HTTP 200 with success response
- [ ] Easebuzz returns `submerchant_id` in response
- [ ] Sub-merchant ID is saved to local DB
- [ ] Sub-merchant appears in Easebuzz dashboard under Sub-Aggregators

### Edge Cases
- [ ] Duplicate email/phone — should error gracefully
- [ ] Invalid PAN/GST format
- [ ] Missing required fields (bank account, IFSC)

---

## 3. Sub-Merchant Update

**API**: `POST /merchant/v1/submerchant/create/` (same endpoint, with `sub_merchant_id`)
**Hash**: `key|submerchant_email|submerchant_phone|salt`

### Steps
1. Select existing sub-merchant in admin UI
2. Modify fields (e.g., change bank account, business name, address)
3. Click **Sync 🔄** button

### Expected Results
- [ ] API returns success
- [ ] Changes reflected in Easebuzz dashboard
- [ ] Local DB synced with response

---

## 4. Split Label Creation

**API**: `POST /split/v1/create`
**Hash**: `key|name_on_bank|bank_name|branch_name|ifsc_code|account_number|label|salt`

### Steps
1. After sub-merchant is created and has `sub_merchant_id`
2. Click **Split 🏷️** button in admin UI
3. System auto-generates label as `sm_{sub_merchant_id}`

### Expected Results
- [ ] API returns success with split label ID
- [ ] Label saved to local DB (`EasebuzzSubMerchant.splitLabel`)
- [ ] Label visible in Easebuzz dashboard under Split configuration

---

## 5. Post-Transaction Split

**API**: `POST /post-split/v1/create/`
**Hash**: `key|merchant_request_id|easebuzz_id|salt`

### Steps
1. Initiate a payment transaction with a known `easebuzz_id`
2. After payment completes, call post-split API via admin UI
3. Retrieve split status with `POST /post-split/v1/retrieve/`

### Expected Results
- [ ] Post-split create returns success
- [ ] Split amounts are correctly distributed
- [ ] Split retrieve shows correct status
- [ ] Max 3 retries per transaction enforced

---

## 6. Transaction Status & Refund

**API**: `POST /transaction/v2.1/retrieve` (status), `POST /transaction/v2/refund` (refund)

### Steps
1. Make a test payment (use a real card/UPI)
2. Check transaction status via admin UI
3. Initiate a refund
4. Check refund status via `POST /transaction/v2/refund_status`

### Expected Results
- [ ] Transaction status returns correct data (amount, status, dates)
- [ ] Refund initiated successfully
- [ ] Refund status shows correct state (processed/pending)

---

## 7. Webhook Verification

**API**: Easebuzz pushes to `/api/v2/payments/easebuzz/webhook`

### Steps
1. Make a test payment
2. Wait for Easebuzz to POST to webhook URL
3. Verify hash signature using `MessageDigest.isEqual()`

### Expected Results
- [ ] Webhook receives payment update
- [ ] Hash verification passes (constant-time comparison)
- [ ] Bill status updated to "paid" in DB
- [ ] Duplicate webhook calls are idempotent (skip if already paid)

---

## 8. Sandbox-Exclusive Tests (if sandbox features are enabled)

If Easebuzz Ops enables these on sandbox:
- [ ] Step 2 (sub-merchant create) on sandbox
- [ ] Step 4 (split label) on sandbox
- [ ] Step 5 (post-split) on sandbox
- [ ] Step 6 (refund) on sandbox

---

## Rollback / Cleanup

- Sub-merchants created in live mode will be real — ensure test accounts are clearly labelled
- Use small amounts (₹1–₹10) for test transactions
- Refund all test payments before concluding

## Go-Live Checklist

- [ ] All 22 Easebuzz API endpoints tested in live mode
- [ ] Payment webhook verified end-to-end
- [ ] Sub-merchant webhook verified
- [ ] Split settlement verified
- [ ] Refund + cancel tested
- [ ] Payout + settlement tested
- [ ] KYC flow works for sub-merchants
- [ ] Android POS app payment flow tested with live credentials
- [ ] Apache proxy updated with `/api/v2/` route
- [ ] Health checks updated to `/api/v2/`
- [ ] JWT secret and DB password overridden via `.env` on server
