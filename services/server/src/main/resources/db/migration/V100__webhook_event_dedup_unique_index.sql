-- V100: Webhook delivery dedup (race-proof idempotency)
--
-- The application previously guarded duplicate webhook deliveries with a
-- check-then-insert (existsByTxnIdAndStatus followed by save). Two concurrent
-- deliveries of the same Easebuzz webhook (their documented retry behaviour)
-- could both pass the check and double-process side effects.
--
-- A unique index on (txn_id, status) makes the database the final arbiter:
-- the losing transaction fails with a constraint violation before its side
-- effects, and the service layer treats that as "already processed".
--
-- Step 1: remove any duplicates already present (keep the earliest delivery).
DELETE FROM easebuzz_webhook_events a
USING easebuzz_webhook_events b
WHERE a.txn_id = b.txn_id
  AND a.status = b.status
  AND a.id > b.id;

-- Step 2: enforce uniqueness going forward.
CREATE UNIQUE INDEX IF NOT EXISTS ux_easebuzz_webhook_txn_status
    ON easebuzz_webhook_events (txn_id, status);
