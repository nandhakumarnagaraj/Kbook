-- V98: Persist KOT state server-side so kitchen-print history survives device
-- replacement and is available to future Kitchen Display System (KDS) terminals.
--
-- 1. bill_items.sent_to_kot: monotonic "this item was sent to the kitchen" marker.
--    Once true it never flips back; devices OR their local value on pull.
-- 2. kot_events: append-only ledger of every kitchen-facing revision, keyed by
--    (restaurant_id, public_token, kot_revision) for idempotent device uploads.
ALTER TABLE bill_items
    ADD COLUMN IF NOT EXISTS sent_to_kot BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS kot_events (
    id                    BIGSERIAL PRIMARY KEY,
    restaurant_id         BIGINT      NOT NULL,
    device_id             TEXT        NOT NULL,
    terminal_id           TEXT,
    terminal_series       TEXT,
    public_token          TEXT        NOT NULL,
    bill_public_token     TEXT,
    kot_revision          TEXT        NOT NULL,
    event_type            TEXT        NOT NULL,
    item_snapshot_json    TEXT,
    originating_device_id TEXT,
    origin_terminal_id    TEXT,
    origin_device_id      TEXT,
    event_token           TEXT,
    event_version         BIGINT      NOT NULL DEFAULT 0,
    is_printed            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            BIGINT      NOT NULL,
    updated_at            BIGINT      NOT NULL,
    CONSTRAINT ux_kot_events_tenant_token_revision UNIQUE (restaurant_id, public_token, kot_revision)
);

CREATE INDEX IF NOT EXISTS idx_kot_events_tenant_device_created
    ON kot_events (restaurant_id, device_id, created_at);

CREATE INDEX IF NOT EXISTS idx_kot_events_tenant_bill
    ON kot_events (restaurant_id, bill_public_token);