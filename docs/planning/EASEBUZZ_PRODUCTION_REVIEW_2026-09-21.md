# Easebuzz production review — 2026-09-21

**Updated 2026-09-23. Verdict: the code-level slices 1–15 are implemented, but the Easebuzz flow is not yet ready for unattended production money movement.** This review covers the repository and mocked gateway tests. It does not certify an Easebuzz account, live webhook delivery, settlement, or a real ₹1 payment/refund. No production server or merchant account was changed during this review.

## Read-only production configuration check

On 2026-09-21, SSH access to `kbook.iadv.cloud` succeeded. The VPS checkout was at `a165d697`, older than the local slice 8–14 commits. The root deployment `.env` contains non-empty `EASEBUZZ_MERCHANT_KEY` and `EASEBUZZ_SALT` entries; its payment and dashboard base URLs point to `pay.easebuzz.in` and `dashboard.easebuzz.in`. The public application health endpoint returned `UP`, and the server and PostgreSQL containers reported healthy. This confirms configuration presence and service health, **not** that the gateway credentials are accepted, the account has every required API enabled, or the running image contains the local fixes. Secret values were not read into the review. Separate sandbox credential placement and validity were not verified in this check. Do not deploy the current changes or run a live payment solely on this evidence.

## Implemented and checked

- Slices 1–3: zero Khanabook commission in post-split, tenant readiness reporting, and an owner-controlled Easebuzz switch enforced before new links.
- Slices 4–8: exact payment/refund amounts, explicit refund failures, consistent refund HTTP responses, and accurate immediate cancel/refund results.
- Slices 9–14: partial refund callback state; locale-independent bill-link amount; payment callback and status-response matching against the bill's transaction and exact amount; refund callback matching against the original Easebuzz payment ID; and removal of the merchant-transaction-ID fallback for refunds.
- Slice 15: a durable per-attempt refund ledger separates initiated and completed amounts, reserves pending amounts, matches completion to the recorded payment/refund/amount, and ignores duplicate completion.
- A combined focused server run passed 58 tests across payment links, transaction verification, payment/refund callbacks, refund service/controller, readiness, post-split commission, and merchant agreement storage. After slice 14, `EasebuzzIntegrationTest` passed again with 25 tests. These tests mock Easebuzz and cannot establish live gateway conformance.

Each completed slice has its own `SAAS_SLICE_*` note in this directory and an entry in `FEATURE_DOCS.txt`.

## Remaining production blockers

1. **Refund reconciliation is incomplete.** Slice 15 now records each attempt and keeps initiated money separate from completed refunds. Because the documented callback hash does not bind status, refund ID, or amount, failure callbacks remain reserved for review. A reconciler must confirm each attempt through Easebuzz's refund-status API, resolve requests whose response was lost before database commit, and provide operator-visible recovery before unattended refunds are safe.
2. **Delayed refund jobs are volatile.** `cancelAndAutoRefund` schedules a Java in-memory task and returns `refundScheduled=true`. A restart can lose the task after the order was cancelled. Persist a refund job and retry/reconcile it after restart, or remove delayed refund from the production workflow until that exists.
3. **Post-split dispatch is not durable.** The payment webhook invokes `createPostSplitAsync` before its transaction commits. A fast worker may read stale state; a process restart or exhausted in-memory retries may leave a paid bill unsplit. Dispatch from an after-commit durable outbox and reconcile against Easebuzz settlements.
4. **No live gateway acceptance evidence.** The official Easebuzz technical pages at the links below require JavaScript in the available reader, so the exact account-specific response schema and webhook examples could not be independently extracted. Before enabling a restaurant, confirm the merchant's enabled APIs and callback contract with Easebuzz, then test KYC/CPV, a controlled ₹1 payment, status refresh, refund request and callback, duplicate callbacks, split, and T+1 settlement with reconciliation evidence. Do not infer production approval from mocked tests or an ERA chatbot answer.

## Next slices and acceptance criteria

- **16 — Durable work and refund reconciliation:** confirm initiated refund attempts through the status API; persist delayed refunds and post-split jobs; dispatch after commit; retry with stable idempotency IDs; and expose failures for operator reconciliation. Prove restart/retry recovery.
- **17 — Gateway acceptance:** run account-specific sandbox tests and a controlled real-money pilot only after Easebuzz enables the required sub-merchant/KYC/CPV, payment, refund, and split capabilities. Capture gateway IDs, callback bodies, settlement reports, and database states without storing secrets in this repository.

Easebuzz references for account-specific verification: [Payment Gateway docs](https://docs.easebuzz.in/docs/payment-gateway/i849ghebwytej-payment-gateway), [Refund API](https://docs.easebuzz.in/docs/payment-gateway/25517a49bef7c-refund-api), [Refund webhook](https://docs.easebuzz.in/docs/payment-gateway/y50x2qvuqc785), [Express Onboarding overview](https://easebuzz.in/express-onboarding-api/), and [SaaS/ERP use case](https://easebuzz.in/use-cases/saas-and-erp/).
