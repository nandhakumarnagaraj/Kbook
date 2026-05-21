To: pgsupport@easebuzz.in
Cc: gopal@indiaadvocacy.com
Subject: BLOCKED: Sandbox payment initiation failing with "Invalid sub merchant id" — need enablement for testing (Key: ADNX3KYX5)

Dear Easebuzz Team,

We are developing a POS platform that integrates Easebuzz for payment processing with sub-merchant management and split payouts. We have sandbox credentials (Key: ADNX3KYX5, Salt: Z4UFP4939) and can access the dashboard, but we are **completely blocked** from testing the basic payment flow.

## The Blocking Issue

The payment initiation API (`POST https://testpay.easebuzz.in/payment/initiateLink`) returns:

```json
{"status": 0, "error_desc": "Invalid sub merchant id.", "data": "Parameter validation failed"}
```

This happens on **every** payment attempt, even without sending a `sub_merchant_id` parameter. We have verified:
- ✅ SHA-512 hash computation is correct (confirmed via hash matching)
- ✅ API endpoint `transaction/v2.1/retrieve` works fine with the same credentials
- ✅ The credentials (key/salt) are valid

We cannot complete even a dummy/ test transaction through the sandbox because of this.

## Additional Features Blocked

The following features show as "Inactive" on our sandbox dashboard:
- Transactions
- Refund Requests
- Settlements
- Webhook History
- Easy Collect / Smart Pay
- Product Settings/API Access

We also need these enabled for testing:

1. **Sub-Merchant Management** (`/merchant/v1/submerchant/create/`) — create & update sub-merchants
2. **Split Label Create** (`/split/v1/create`) — bank-to-label linkage
3. **Post-Transaction Split** (`/post-split/v1/create/`, `/post-split/v1/retrieve/`) — per-transaction fund distribution
4. **Refund API v2** (`/transaction/v2/refund`, `/transaction/v2/refund_status`) — initiate & check refunds
5. **Cancel Transaction** (`/transaction/v1/cancel`) — cancel pending transactions
6. **OTP APIs** (`/submerchant/v1/verify_otp`, `/submerchant/v1/resend_otp`) — sub-merchant OTP verification

## What We Need

Could you please enable the sub-merchant/payment module on our sandbox account so we can:
1. Complete at least one test transaction end-to-end (initiate → pay → webhook → verify)
2. Test sub-merchant creation and KYC flow
3. Test split payouts

**Note on KYC:** We understand KYC Access Key (`/submerchant/v1/generate_kyc_access_key`) may be live-only — we will test that in production.

**Our Sandbox Callback URLs:**
- Return URL: `https://paripinnate-unfreighted-tynisha.ngrok-free.dev/api/v2/payments/easebuzz/return`
- Webhook URL: `https://paripinnate-unfreighted-tynisha.ngrok-free.dev/api/v2/payments/easebuzz/webhook`

Please let us know if any additional configuration or information is needed from our side. We're ready to proceed as soon as the sandbox is enabled.

Thank you,
Gopal Krishna
India Advocacy
gopal@indiaadvocacy.com
