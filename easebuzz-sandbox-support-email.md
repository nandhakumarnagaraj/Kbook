To: tech@easebuzz.in
Cc: gopal@indiaadvocacy.com
Subject: Request to Enable Features on Sandbox Account (Key: ADNX3KYX5)

Dear Easebuzz Team,

We have received our sandbox credentials (Key: ADNX3KYX5, Salt: Z4UFP4939) and successfully logged into the dashboard. We are developing a POS platform that requires sub-merchant management with split payments.

**Current Status:**
We can access the dashboard, but the following features are shown as "Inactive" on our sandbox account:

- Transactions
- Refund Requests
- Settlements
- Webhook History
- Easy Collect / Smart Pay
- Product Settings

**What We Need Enabled:**
To complete our integration testing, we request access to the following APIs:

1. **Split Label Create** (`/split/v1/create`)
2. **Post-Transaction Split** (`/post-split/v1/create/`)
3. **Refund & Refund Status** (`/transaction/v2/refund`, `/transaction/v2/refund_status`)
4. **Cancel Transaction** (`/transaction/v1/cancel`)
5. **Resend OTP** (`/submerchant/v1/resend_otp`)
6. **Sub-Merchant Management** (`/merchant/v1/submerchant/create/`)

**Note on KYC:**
We understand that **KYC Access Key** (`/submerchant/v1/generate_kyc_access_key`) is a live-only feature and we will test it in production. Our merchant KYC has been submitted and is under review.

**Our Callback URLs (Sandbox):**
- Return URL: `https://paripinnate-unfreighted-tynisha.ngrok-free.dev/api/v2/payments/easebuzz/return`
- Webhook URL: `https://paripinnate-unfreighted-tynisha.ngrok-free.dev/api/v2/payments/easebuzz/webhook`

Please let us know if any additional information or configuration is needed from our side. We appreciate your support.

Regards,
Gopal Krishna
India Advocacy
gopal@indiaadvocacy.com
