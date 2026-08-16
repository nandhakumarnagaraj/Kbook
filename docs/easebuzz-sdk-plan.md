# Easebuzz Integration — ERA-Confirmed Details

## Payment SDK
- **Dependency:** `in.easebuzz:android-v2:1.0.2`
- **Activity:** `com.easebuzz.payment.kit.PWECheckoutActivity`
- **Extras:** `access_key` (from server), `pay_mode` ("test" or "production")
- **Result:** `getStringExtra("result")` → "payment_successfull" | "payment_failed" | "user_cancelled"

## Sub-Merchant Onboarding Flow (Confirmed by ERA)

### Step 1: Create Sub-Merchant
- **API:** `/merchant/v1/submerchant/create`
- **Easebuzz generates** the `sub_merchant_id` (we don't create it)
- **Mandatory fields:**
  - `sub_merchant_name`
  - `sub_merchant_email`
  - `sub_merchant_phone`
  - `sub_merchant_name_in_bank`
  - `sub_merchant_account_number`
  - `sub_merchant_bank_name`
  - `sub_merchant_branch_name`
  - `sub_merchant_ifsc_code`
  - `sub_merchant_password` + `sub_merchant_confirm_password`
- **Hash:** SHA-512 of `key|submerchant_email|submerchant_phone|salt`
- **Optional:** `kyc_details`, `business_details` (GSTIN, address), `submerchant_deduction_percentage`

### Step 2: OTP (Auto-sent)
- Easebuzz **auto-sends OTP** to sub-merchant phone/email after creation
- We call `/submerchant/v1/verify_otp` with the OTP user enters
- We call `/submerchant/v1/resend_otp` if user needs a new OTP

### Step 3: KYC (WebView — no separate SDK)
- Call `/submerchant/v1/generate_kyc_access_key` → returns `kyc_dashboard_url`
- Open this URL in a WebView inside our app
- Restaurant owner uploads documents there
- **No separate KYC SDK exists** — it's web-based

### Step 4: CPV (Easebuzz handles)
- After KYC submission, Easebuzz team manually verifies
- Restaurant owner gets email/SMS from Easebuzz if more docs needed
- Our app does NOT trigger CPV — Easebuzz does it
- Takes 1-3 business days

### Step 5: ACTIVE
- Webhook received: KYC approved → status = ACTIVE
- Sub-merchant can now accept payments
- Our server updates `EasebuzzSubMerchant.status = "ACTIVE"`
- Push notification sent to restaurant device

## Timeline
| Step | Time |
|------|------|
| Creation + OTP | Instant |
| KYC submission | User-dependent (5 min) |
| CPV review | 1-3 business days |
| ACTIVE | After CPV approval |

## What We Need in Android App (Onboarding Screen)
1. **Native form** → collect: name, email, phone, bank details, IFSC, password
2. **OTP screen** → enter OTP that Easebuzz auto-sent
3. **WebView** → open KYC dashboard URL for document upload
4. **Status screen** → show "Under Review" until webhook confirms ACTIVE

## Server Code Status
| Step | Server Code | Needs Update? |
|------|-------------|--------------|
| Create sub-merchant | SubMerchantService.create() | ⚠️ Needs to call Easebuzz API and store returned sub_merchant_id |
| OTP verify | EasebuzzApiClient.verifyOtp() | ✅ Ready |
| OTP resend | EasebuzzApiClient.resendOtp() | ✅ Ready |
| KYC access key | EasebuzzWireApiClient | ✅ Ready |
| Webhook | EasebuzzWebhookService | ✅ Ready |
