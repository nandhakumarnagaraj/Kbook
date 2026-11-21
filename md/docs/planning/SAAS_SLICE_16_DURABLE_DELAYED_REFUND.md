# KhanaBook SaaS — slice 16: durable delayed refunds

Delayed cancellation refunds are now stored in the existing database-backed payment work queue instead of a process-local Java timer. The cancellation transaction records a `DELAYED_REFUND` job with its due time, serialized bill/restaurant/amount/reason payload, and stable `DELAYED_REFUND:{restaurantId}:{billId}` key. The API response includes the persisted work ID.

The scheduled queue processor executes due work after restart and applies the existing retry and dead-letter policy. A stable work key prevents the same cancellation intent from creating multiple delayed jobs. If a worker commits the refund attempt but stops before marking the work complete, the refund ledger's reserved amount prevents a second gateway refund; the replay is then treated as complete.

This slice makes delayed refund dispatch restart-safe. It does not confirm pending refund results through Easebuzz's refund-status API, and it does not replace the separate post-split async path. Those remain production blockers.

Verification: `RefundServiceTest`, `WebhookRetryServiceTest`, `WebhookRetryConfigTest`, and `EasebuzzIntegrationTest` passed 41 tests with zero failures. They cover persisted scheduling, due time and stable-key storage, duplicate-key reuse, executor registration, and delayed refund execution.
