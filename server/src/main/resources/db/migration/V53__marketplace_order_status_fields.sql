-- Phase 6: marketplace order status tracking (Requirement 19).
--
-- Ports the restaurant-side order-status lifecycle columns from v2 V25 so the
-- merchant can accept / reject / mark-ready / complete orders that arrive via the
-- Swiggy and Zomato webhooks. The marketplace_orders + marketplace_order_items
-- tables themselves already exist (created by V21, which also seeded the
-- restaurant-scoped credential columns); this migration only adds the mutable
-- status fields and the index the merchant-action queries rely on.
--
-- Idempotency: V21 was applied on v3 long before this runs (it ships V49-V52),
-- so every column/index is added with IF NOT EXISTS / guarded creation. Nothing
-- is dropped or renamed (Requirement 2.8): the existing
-- uk_marketplace_order_platform_order_id UNIQUE (platform, platform_order_id)
-- from V21 already provides external-order idempotency for the webhook upsert, so
-- the external-id uniqueness required by spec task 14.1 is preserved.

ALTER TABLE marketplace_orders
    ADD COLUMN IF NOT EXISTS order_status      VARCHAR(50) NOT NULL DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS accepted_at        BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_at        BIGINT,
    ADD COLUMN IF NOT EXISTS rejected_reason    TEXT,
    ADD COLUMN IF NOT EXISTS ready_at           BIGINT,
    ADD COLUMN IF NOT EXISTS completed_at       BIGINT;

-- Merchant-action queries filter by restaurant + status; V21 only indexed
-- (restaurant_id, created_at) and (platform, platform_order_id). Add the status
-- index to avoid a sequential scan over a tenant's full order history.
CREATE INDEX IF NOT EXISTS idx_marketplace_orders_status
    ON marketplace_orders (restaurant_id, order_status, created_at DESC);
