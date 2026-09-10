# KhanaBook Domain Model

KhanaBook is an offline-first restaurant POS and billing platform with cloud synchronization and integrated digital payments.

## Payment & Onboarding

**Sub-Merchant**:
A restaurant business registered under KhanaBook's master aggregator account on Easebuzz to receive digital payments directly into their bank account.
_Avoid_: Vendor, seller account, client account

**Online Payments Setup**:
The initial 2-minute onboarding flow (Bank account, IFSC, PAN, and OTP) required by Easebuzz to generate a sub-merchant ID and start processing live customer payments.
_Avoid_: KYC (prior to registration), gateway registration, merchant verification

**Settlement KYC**:
Regulatory compliance documentation (address proof, CPV / contact point verification) required by RBI guidelines for daily payout settlement from the gateway into the merchant's bank account.
_Avoid_: Onboarding documents, gateway agreement, compliance checklist

**Merchant Agreement**:
The internal platform terms of service between the KhanaBook SaaS platform and the restaurant owner, digitally acknowledged during registration.
_Avoid_: Gateway agreement, Easebuzz contract, legal document

**Dynamic UPI QR**:
An order-specific UPI QR code generated on the POS billing screen containing the exact bill amount and transaction reference for instant automated reconciliation.
_Avoid_: Static QR, physical standee, paper QR

**Payment Gateway (Easebuzz)**:
The RBI-licensed payment aggregator handling customer checkout sessions across UPI, Credit/Debit cards, and NetBanking.
_Avoid_: Payment processor, bank portal
