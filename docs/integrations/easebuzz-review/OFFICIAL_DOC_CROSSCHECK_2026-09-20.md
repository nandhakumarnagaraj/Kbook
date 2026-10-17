# Easebuzz official-document cross-check

Cross-check date: 2026-09-20

The official Easebuzz pages are Stoplight-rendered and load their detailed schemas client-side. The endpoint names, request forms, hash construction, webhook verification, and status handling below were checked against the linked official pages and the server implementation.

| Flow | Official documentation | KhanaBook implementation | Result |
|---|---|---|---|
| Hosted payment initiation | https://docs.easebuzz.in/docs/payment-gateway/viyex0lwoda66-initiate-transaction-ap-is | `EasebuzzApiClient.initiatePayment`: form POST to `/payment/initiateLink`; server-side hash; return/failure URLs; UDF fields | Static match; live payment pending |
| Easy Collect payment link | https://docs.easebuzz.in/docs/payment-gateway/05be3a890b572-easy-collect-api-create-link | `EasebuzzApiClient.createPaymentLink`: form POST to `/easy_collect`; merchant transaction and UDF fields | Static match; live link pending |
| Transaction status | https://docs.easebuzz.in/docs/payment-gateway/i849ghebwytej-payment-gateway | `getTransactionStatus`: dashboard POST to `/transaction/v2.1/retrieve`, hash `key|txnid|salt` | Read-only production request returned `200 Transaction not found` for a synthetic ID |
| Refund initiation | https://docs.easebuzz.in/docs/payment-gateway/25517a49bef7c-refund-api | `initiateRefund`: dashboard POST to `/transaction/v2/refund`, refund reference, Easebuzz payment ID, amount, and server hash | Static match; real refund pending |
| Refund status | https://docs.easebuzz.in/docs/payment-gateway/25517a49bef7c-refund-api | `getRefundStatus`: uses the payment `easepayid`/`easebuzz_id`, optional refund ID, and server hash | Static match; real refund pending |
| Payment webhook | https://docs.easebuzz.in/docs/payment-gateway/paw9n1qc3kuoz-transaction-webhook | `EasebuzzWebhookService`: reverse payment hash, bill resolution through `udf1`/transaction ID, idempotency, retry queue | Invalid signatures rejected; valid synthetic payment not yet run |
| Refund webhook | https://docs.easebuzz.in/docs/payment-gateway/y50x2qvuqc785 | `EasebuzzWebhookService`: verifies `sha512(key|easepayid|salt)`, handles queued/accepted/refunded, preserves cumulative refunds | Valid synthetic webhook `200`; invalid signature `401` |
| Sub-merchant KYC webhook | https://docs.easebuzz.in/docs/payment-gateway/ydk17g4xurhhp-submerchant-kyc-approval-webhook | Form and JSON payload handling, outer-field preservation, `key|submerchant_id|salt` verification, tenant lookup | Static tests pass; live KYC webhook pending |
| Payout webhook | https://docs.easebuzz.in/docs/payment-gateway/bwc1ej4g1d490-payout-webhook | Supports payout ID and transfer forms, status/UTR persistence, hash verification, retries | Static tests pass; live payout webhook pending |
| On-demand settlements | https://docs.easebuzz.in/docs/payment-gateway/643f40bc5eae3-on-demand-settlement-ap-is | Settlement initiation and date-range retrieval through dashboard base URL | Static implementation present; account enablement/live call pending |

## Evidence

- Focused server suite: 30 Easebuzz tests passed, 0 failures, 0 errors.
- Production readiness endpoint: `GET https://kbook.iadv.cloud/api/v1/actuator/health` returns `200` / `UP`.
- Production status API: synthetic transaction returns HTTP `200` with `Transaction not found`.
- Refund webhook: valid synthetic signature returns `200`; invalid signature returns `401`.
- Production Compose rejects missing Easebuzz URL variables and has no sandbox URL fallback.

## Remaining external gates

1. Rotate the merchant key, salt, and Wire API key that were previously exposed through the old helper file.
2. Configure and confirm the corresponding live Easebuzz webhook URLs in the merchant dashboard.
3. Execute one real ₹1 payment, payment webhook, status lookup, refund, refund webhook, and reconciliation cycle.

The repository and VPS are prepared for those checks; they must not be marked as complete from synthetic tests alone.
