# KhanaBook SaaS — slice 17: durable post-split dispatch

A successful Easebuzz payment callback now stores a `POST_SPLIT` job in the database transaction that marks the bill paid and records the gateway event. The job contains the bill ID, Easebuzz payment ID, and merchant transaction ID and uses the stable key `POST_SPLIT:{billId}:{easebuzzId}`. It becomes visible to the scheduled processor only after the payment transaction commits.

The post-split worker now makes one gateway attempt per durable queue execution. Retry timing, attempt limits, and dead-letter handling are owned by the persisted queue, so no retry depends on an async thread or `Thread.sleep`. The merchant request ID remains deterministic. A replay after the bill has already recorded a completed split succeeds without calling Easebuzz again, while failed attempts stay visible for retry or operator recovery.

KhanaBook commission remains zero: the complete bill amount is assigned to the restaurant's active split label. Settlement reconciliation remains responsible for detecting a gateway/local mismatch.

Verification: `PostSplitNoCommissionTest`, `WebhookRetryConfigTest`, `EasebuzzWebhookTest`, and `EasebuzzIntegrationTest` passed. Coverage includes durable payment-callback enqueueing, executor payload parsing, full restaurant allocation, and idempotent replay after completion.
