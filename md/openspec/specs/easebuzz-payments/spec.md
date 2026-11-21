# Specification: Easebuzz Payment Gateway & Settlements

## Purpose
Governs restaurant sub-merchant onboarding, KYC document verification, dynamic UPI QR generation at till counters, webhook processing with idempotency, and refund ledgers.

## Requirements

### Requirement: Sub-Merchant Onboarding & KYC
Each restaurant tenant SHALL operate as an isolated sub-merchant account on Easebuzz with dedicated KYC lifecycle states.

#### Scenario: Sub-merchant KYC submission
- **WHEN** an owner submits PAN, GSTIN, and bank account credentials via Web Admin
- **THEN** the system registers the sub-merchant entity (`easebuzz_sub_merchant`) and tracks `kyc_status` (Pending $\rightarrow$ Verified / Rejected).

---

### Requirement: Dynamic Countertop UPI QR
The POS terminal SHALL be capable of generating a dynamic UPI QR code bound to the exact bill amount and unique transaction ID directly on the till screen.

#### Scenario: Customer selects Dynamic UPI payment
- **WHEN** the cashier chooses Dynamic UPI mode for bill B1 with amount A
- **THEN** the backend requests a payment intent from Easebuzz, displays the QR on the POS screen, and polls/awaits webhook confirmation before finalizing settlement.

---

### Requirement: Webhook Idempotency & Reconciliation
All Easebuzz webhook callbacks (`POST /api/v1/payments/easebuzz/webhook`) SHALL be strictly idempotent and validated against merchant cryptographic hash signatures.

#### Scenario: Duplicate webhook delivery
- **WHEN** a payment success webhook is received more than once for transaction T
- **THEN** the system verifies the SHA-512 signature, logs the event to `easebuzz_webhook_events`, detects that transaction T is already settled, and returns HTTP 200 without double-crediting.

---

### Requirement: Stuck Payment Sweeper
Transactions remaining in pending state past their TTL window SHALL be automatically polled and resolved by background sweepers.

#### Scenario: Network drop during customer UPI authorization
- **WHEN** a payment remains in `PENDING` past the allowed settlement window
- **THEN** `StuckPaymentSweepService` queries Easebuzz order status API directly, either transitioning the bill to `COMPLETED` if funds were captured, or releasing the lock to allow retry.
