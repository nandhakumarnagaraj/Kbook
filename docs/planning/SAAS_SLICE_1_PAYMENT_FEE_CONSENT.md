# KhanaBook SaaS analysis — slice 1

KhanaBook serves three parties: a restaurant runs the offline-first Android POS, KhanaBook operates the multi-tenant server and web admin, and a customer can pay a restaurant bill through Easebuzz. A bill may be created offline, but an Easebuzz link is an online server operation. Payment, refund, and settlement evidence therefore has to be anchored on the server, not inferred from the device's locally synced payment toggle.

## Finding and decision

The current restaurant payment addendum says a separate KhanaBook platform fee must be disclosed and accepted separately. The submerchant record also has an admin-configurable `commissionRate`, and `PostSplitService` applies that rate after a successful customer payment. There is no separate platform-fee consent record in this path. A restaurant could therefore receive a payment with a deduction that the current addendum did not authorize. The older Easebuzz business-model document describes a 2–5% commission; that is a product aspiration, not evidence that a particular owner accepted a rate.

Slice 1 covers **new payment-link creation for a restaurant with a positive configured platform commission**. The server rejects the request before calling Easebuzz and returns `PLATFORM_FEE_CONSENT_REQUIRED`. A zero-commission restaurant can still create a link when its current owner agreement and Easebuzz submerchant are active. The Android billing flow extracts the server's error from the HTTP response and shows the reason to the operator instead of a generic HTTP 400 message.

Path: Android bill → `BillingViewModel.prepareAndCreatePaymentLink` → `PaymentController` → `EasebuzzPaymentService.createPaymentLink` → Easebuzz only if the prerequisites pass. An integration test asserts that a positive commission produces the rejection and that the mocked gateway is never called.

## Verification

- Server: `EasebuzzIntegrationTest` — 17 passed, including the positive-commission rejection and zero-commission success path.
- Android: `:app:compileDebugKotlin :app:testDebugUnitTest --offline` passed.
- No live Easebuzz call or production deployment was performed.

## Remaining product risks, outside slice 1

- Existing payment links can still complete after a commission is changed; `PostSplitService` currently reads the rate at split time. Consent and rate should be recorded per transaction before an authorized commission is charged.
- The payment-settings screen can show a locally enabled Easebuzz toggle before the server considers the merchant payment-ready. The server remains the enforcement point.
- The proposed gateway rates require Easebuzz's final approval. The drawn-signature flow also needs legal review before it is represented as a prescribed Indian electronic-signature method.

Stop after slice 1. The next slice should be selected only after this behavior and the platform-fee policy are reviewed.
