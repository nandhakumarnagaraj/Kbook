-- V113: a payment link needs its own clock.
--
-- bills.updated_at moves on every device edit, so it cannot answer "how long has this link been
-- open?" — a bill edited six minutes ago would look like a fresh link and get swept early, while
-- a stale one could hide behind a recent edit. The sweep needs a time that only moves when the
-- link is created.

ALTER TABLE bills ADD COLUMN IF NOT EXISTS gateway_link_created_at BIGINT;

-- Links created before this migration have no recorded creation time. updated_at is the best
-- available estimate and can only over-state the age, which is the safe direction: the sweep
-- will re-check sooner rather than never.
UPDATE bills
SET gateway_link_created_at = updated_at
WHERE gateway_status = 'link_created'
  AND gateway_link_created_at IS NULL;

-- Serves the sweep's only query: oldest unconfirmed links first.
CREATE INDEX IF NOT EXISTS idx_bills_gateway_stuck
    ON bills(gateway_status, gateway_link_created_at);
