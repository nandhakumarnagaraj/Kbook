# KhanaBook SaaS — slice 15: refund-attempt ledger

Refund initiation now creates a durable `easebuzz_refund_attempts` record containing the bill and restaurant, merchant transaction ID, authenticated Easebuzz payment ID, merchant refund ID, gateway refund ID, amount, reason, timestamps, and lifecycle state. An accepted gateway request remains `INITIATED`; it no longer increases the bill's completed `refundAmount` or marks the bill refunded.

Pending attempts reserve their amount when checking whether another partial refund is allowed. A completion callback must match the bill's recorded payment, refund ID, and exact attempt amount before the attempt becomes `COMPLETED` and the bill's completed total changes. Duplicate completion does not count twice. Mismatched amounts and unconfirmed failure callbacks remain reserved with `refund_review_required` for reconciliation.

This slice intentionally does not release a reservation from a failure callback because the documented refund callback hash covers the Easebuzz payment ID but does not bind the status, refund ID, or amount. A subsequent slice must reconcile attempts through the refund-status API and provide an operator-visible recovery path. The gateway request also still occurs inside the database transaction; stable request IDs make retry safer, but durable recovery remains required.

Verification: `RefundServiceTest`, `EasebuzzWebhookTest`, and `EasebuzzIntegrationTest` cover initiated versus completed accounting, pending-amount reservation, over-refund prevention, exact attempt matching, duplicate completion, and fail-closed mismatch handling.
