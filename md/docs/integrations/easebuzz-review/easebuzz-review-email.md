To: pgsupport@easebuzz.in
Cc: gopal@indiaadvocacy.com
Subject: KhanaBook Easebuzz Marketplace Integration Review - Parent Submerchant, KYC, Split Settlement, Refunds

Dear Easebuzz Team,

We are preparing KhanaBook v2 for production review with Easebuzz. KhanaBook is a tenant-isolated restaurant POS and marketplace platform where KhanaBook acts as the parent merchant and each restaurant is onboarded as an Easebuzz sub-merchant.

We request your review and enablement for the complete integration surface listed below.

## Integration Model

- Parent merchant: KhanaBook / India Advocacy
- Child entity: One Easebuzz sub-merchant per restaurant tenant
- Payment model: Customer payment is initiated by KhanaBook backend and routed with `sub_merchant_id` when the restaurant is active
- Settlement model: Post-transaction split sends restaurant share to `sm_{sub_merchant_id}` and platform commission to KhanaBook
- Tenant isolation: All owner-facing POS/payment-config actions resolve the restaurant from JWT-backed `TenantContext`; admin-only actions are under `/api/v2/admin/*`

## Callback URLs

Please confirm or enable the following production URLs once the live domain is finalized:

- Payment return URL: `https://<production-domain>/api/v2/payments/easebuzz/return`
- Payment webhook URL: `https://<production-domain>/api/v2/payments/easebuzz/webhook`
- Refund webhook URL: `https://<production-domain>/api/v2/payments/easebuzz/refund/webhook`
- Sub-merchant webhook URL: `https://<production-domain>/api/v2/payments/easebuzz/sub-merchant/webhook`
- Payout webhook URL: `https://<production-domain>/api/v2/payments/easebuzz/payout/webhook`

## APIs Implemented

- Create and update sub-merchant: `/merchant/v1/submerchant/create/`
- Generate KYC access key: `/submerchant/v1/generate_kyc_access_key`
- Verify OTP: `/submerchant/v1/verify_otp`
- Resend OTP: `/submerchant/v1/resend_otp`
- Create split label: `/split/v1/create`
- Create post-transaction split: `/post-split/v1/create/`
- Retrieve post-transaction split: `/post-split/v1/retrieve/`
- Initiate payment: `/payment/initiateLink`
- Retrieve transaction status: `/transaction/v2.1/retrieve`
- Cancel transaction: `/transaction/v1/cancel`
- Initiate refund v2: `/transaction/v2/refund`
- Retrieve refund status: `/transaction/v2/refund_status`
- Initiate payout v2: `/payout/v2/transfer`
- On-demand settlement: `/settlement/v1/on_demand`
- Retrieve settlement report: `/settlements/v1/retrieve`

## Hash and Webhook Security

All outbound Easebuzz calls generate SHA-512 hashes server-side only. The salt is never exposed to Android or web clients.

Implemented webhook checks:

- Payment webhook reverse-hash verification
- Refund webhook hash verification
- Sub-merchant webhook hash verification using `key|submerchant_id|salt`
- Payout webhook hash verification for settlement and transfer payloads
- Constant-time comparison using `MessageDigest.isEqual`
- Idempotency guards for duplicate payment and refund webhooks

## Requested Enablement

Please enable or confirm the following for our merchant account:

- Parent-submerchant marketplace model
- Sub-merchant creation and update APIs
- Hosted KYC access-key flow
- OTP verify/resend APIs
- Split label creation
- Post-transaction split create/retrieve
- Refund v2 and refund status APIs
- Cancel transaction API
- Payout v2 and on-demand settlement APIs
- Webhook delivery for payment, refund, KYC/sub-merchant, and payout status

## Review Materials

We have prepared a full review document covering architecture, sequence diagrams, KYC/CPV/OTP, payment initiation, webhooks, post-split settlement, refunds, settlement tracking, readiness, and compliance controls. We can share the PDF-ready Markdown or exported PDF on request.

Please let us know if you need live test credentials, sample payloads, callback validation, IP allowlisting details, or any additional production checklist from our side.

Thank you,

Gopal Krishna  
India Advocacy  
gopal@indiaadvocacy.com

