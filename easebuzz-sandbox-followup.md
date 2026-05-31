To: pgsupport@easebuzz.in
Cc: gopal@indiaadvocacy.com
Subject: FOLLOW-UP: Sandbox sub-merchant/split features still blocked (Key: ADNX3KYX5)

Dear Easebuzz Team,

This is a follow-up to our previous email (sent 2 weeks ago). We're still completely blocked from testing the basic payment flow on the sandbox.

**Issue:** `POST https://testpay.easebuzz.in/payment/initiateLink` returns `"Invalid sub merchant id"` for every attempt.

**Features still inactive on sandbox dashboard:**
- Transactions, Refunds, Settlements, Webhooks, Easy Collect
- Sub-Merchant Management, Split APIs, Refund v2, Cancel, OTP APIs

**What we need enabled:**
1. Payment module activation (basic test transactions)
2. Sub-merchant create/update
3. Split label + post-transaction split
4. Refund v2 and cancel

**Callback URLs (same as before):**
- Return: `https://paripinnate-unfreighted-tynisha.ngrok-free.dev/api/v2/payments/easebuzz/return`
- Webhook: `https://paripinnate-unfreighted-tynisha.ngrok-free.dev/api/v2/payments/easebuzz/webhook`

Please let us know if there's anything else needed from our side.

Thank you,
Gopal Krishna
India Advocacy
gopal@indiaadvocacy.com
