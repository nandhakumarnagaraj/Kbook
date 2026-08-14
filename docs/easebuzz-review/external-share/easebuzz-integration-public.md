# KhanaBook Easebuzz Integration Review

## Purpose

KhanaBook integrates Easebuzz using a parent-submerchant marketplace model. KhanaBook acts as the parent merchant. Each restaurant partner is onboarded as an Easebuzz sub-merchant after KYC verification.

This document is intentionally limited to the Easebuzz integration boundary. It does not describe KhanaBook source code, internal modules, database schema, infrastructure topology, or unrelated product features.

## Integration Model

- Parent merchant: KhanaBook.
- Sub-merchant: One restaurant partner under the parent merchant.
- Payment collection: Customer payments are initiated through KhanaBook and processed by Easebuzz.
- Settlement routing: Successful transactions are split between the restaurant sub-merchant and KhanaBook commission.
- KYC: Restaurant KYC is completed using Easebuzz-hosted KYC flows.
- Webhooks: Easebuzz sends status updates to KhanaBook for payment, refund, KYC, payout, and settlement events.

## High-Level Architecture

```mermaid
flowchart LR
    POS["KhanaBook POS / Business Console"] --> API["KhanaBook Backend\nIntegration Layer"]
    OPS["KhanaBook Operations Console"] --> API

    API --> EBPay["Easebuzz Payment Gateway"]
    API --> EBDash["Easebuzz Dashboard APIs"]
    API --> EBKyc["Easebuzz Hosted KYC"]
    EBPay --> WH["KhanaBook Webhook Endpoint"]
    EBDash --> WH
    WH --> API

    API --> Ledger["KhanaBook Payment Ledger\nTenant Isolated"]
```

## Onboarding and KYC

```mermaid
flowchart TD
    A["Restaurant partner applies for online payments"] --> B["KhanaBook validates business, contact, bank, and food-license details"]
    B --> C["KhanaBook creates or updates Easebuzz sub-merchant"]
    C --> D{"Sub-merchant created?"}
    D -->|No| E["Show correction required to operations or restaurant owner"]
    D -->|Yes| F["Generate Easebuzz KYC access link"]
    F --> G["Restaurant owner completes KYC on Easebuzz hosted portal"]
    G --> H["Easebuzz sends KYC status webhook"]
    H --> I{"KYC status"}
    I -->|Approved| J["Enable online payment routing for the restaurant"]
    I -->|Pending| K["Keep onboarding under review"]
    I -->|Rejected| L["Request corrected details and resubmission"]
```

## Payment and Split Settlement

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant POS as KhanaBook POS
    participant KB as KhanaBook Backend
    participant EB as Easebuzz
    participant SET as Easebuzz Settlement

    POS->>KB: Create payment request for restaurant order
    KB->>KB: Validate tenant and payment eligibility
    KB->>EB: Initiate payment with restaurant sub-merchant context
    EB-->>KB: Return payment access token or payment URL
    KB-->>POS: Return payment parameters
    Customer->>EB: Complete payment
    EB-->>KB: Send payment status webhook
    KB->>KB: Verify webhook hash and update payment status
    KB->>EB: Submit post-transaction split configuration
    EB-->>KB: Confirm split status
    SET-->>Customer: Customer receives payment/refund updates as applicable
    SET-->>KB: Settlement and payout status available for reconciliation
```

## Webhook Processing

```mermaid
flowchart TD
    A["Easebuzz webhook received"] --> B{"Webhook type"}
    B -->|Payment| C["Verify payment hash"]
    B -->|Refund| D["Verify refund hash"]
    B -->|KYC| E["Verify sub-merchant hash"]
    B -->|Payout or settlement| F["Verify payout or settlement hash"]
    C --> G{"Valid?"}
    D --> G
    E --> G
    F --> G
    G -->|No| H["Reject without state change"]
    G -->|Yes| I["Apply idempotency checks"]
    I --> J["Update payment, refund, KYC, or payout status"]
    J --> K["Record audit event for reconciliation"]
```

## Easebuzz APIs Used

| Area | Easebuzz capability |
|---|---|
| Sub-merchant onboarding | Create/update sub-merchant |
| KYC | Generate KYC access key, verify OTP, resend OTP |
| Payment | Initiate payment, retrieve transaction status |
| Split settlement | Create split label, create/retrieve post-transaction split |
| Refunds | Initiate refund, retrieve refund status |
| Settlement and payout | On-demand settlement, payout transfer, settlement retrieve |
| Webhooks | Payment, refund, sub-merchant/KYC, payout/settlement callbacks |

## Security Controls Shared for Review

- Easebuzz hashes are generated only on the server.
- Merchant salt is never exposed to mobile or web clients.
- All inbound Easebuzz webhooks are hash-verified before processing.
- Duplicate payment and refund webhook deliveries are handled idempotently.
- Restaurant actions are tenant-isolated so one restaurant cannot access another restaurant's payment state.
- Bank and KYC-sensitive information is not exposed in shared review material.

## External Review Checklist

- [ ] Confirm parent-submerchant marketplace model is enabled.
- [ ] Confirm sub-merchant create/update APIs are enabled.
- [ ] Confirm hosted KYC access-key flow is enabled.
- [ ] Confirm OTP verify/resend APIs are enabled if required for sub-merchant onboarding.
- [ ] Confirm split label and post-transaction split APIs are enabled.
- [ ] Confirm payment, refund, KYC, payout, and settlement webhooks are enabled.
- [ ] Confirm live callback URLs before production go-live.
- [ ] Run one low-value live payment test.
- [ ] Verify payment webhook and transaction status reconciliation.
- [ ] Verify split settlement and settlement report reconciliation.
- [ ] Verify refund and refund-status flow.

