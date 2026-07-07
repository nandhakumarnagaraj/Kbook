-- Deduplicate gateway-owned bill payments.
-- payments.gateway_txn_id is already UNIQUE (V7). bill_payments carries the same
-- gateway_txn_id for gateway-verified payments but had no uniqueness guard, so a
-- WorkManager retry after a dropped push response could insert a duplicate row.
-- A partial unique index enforces one active row per non-blank gateway_txn_id while
-- still allowing many manual (NULL) payments. Paired with an app-layer idempotency skip
-- in GenericSyncService so a retried push is ignored rather than failing the batch.

-- Safety net: collapse any pre-existing duplicates before the index is created,
-- keeping the earliest row (lowest id) and soft-deleting the rest. Without this,
-- the CREATE UNIQUE INDEX statement would fail on existing duplicates.
UPDATE bill_payments bp
SET is_deleted = TRUE
WHERE bp.id IN (
    SELECT other.id
    FROM bill_payments other
    WHERE other.gateway_txn_id IS NOT NULL
      AND other.gateway_txn_id <> ''
      AND other.is_deleted = FALSE
      AND other.gateway_txn_id = bp.gateway_txn_id
      AND other.restaurant_id = bp.restaurant_id
      AND other.id > bp.id
);

-- Enforce one *active* row per non-blank gateway_txn_id. Scoping the predicate to
-- is_deleted = FALSE keeps it consistent with the soft-delete dedup above and
-- lets a legitimately re-created payment reuse a txn id whose prior row was
-- soft-deleted.
CREATE UNIQUE INDEX IF NOT EXISTS uq_bill_payments_gateway_txn
    ON bill_payments (gateway_txn_id)
    WHERE gateway_txn_id IS NOT NULL AND gateway_txn_id <> '' AND is_deleted = FALSE;
