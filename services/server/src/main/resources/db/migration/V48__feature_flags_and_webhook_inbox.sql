-- Phase 2 integration foundation: per-restaurant feature flags and the durable
-- webhook inbox (Requirements 30 and 33).
--
-- Both mechanisms are the rollback model's foundation: a Feature_Flag is
-- disabled first on remediation (Req 28.7), and the Webhook_Inbox lets provider
-- events be captured while a flag is off and drained when it turns on (Req 33),
-- all without business processing ever happening inside the provider HTTP
-- handler (Req 33.29).
--
-- Every statement below is additive: CREATE TABLE / CREATE INDEX / INSERT only.
-- No column is dropped and no constraint is relaxed, so the previous server
-- image starts against the migrated schema (ddl-auto=validate tolerates the
-- unmapped objects, exactly as the orphan V6/V7/V8 tables already demonstrate in
-- production today - see notes at the end of this file).

-- ---------------------------------------------------------------------------
-- Feature flags (Requirement 30)
-- ---------------------------------------------------------------------------
-- Three-state model (design D3): the dominant kill switch is separate from the
-- rollout default so that a single-restaurant override can enable a feature for
-- one restaurant without switching it on for every restaurant that has no
-- override. Both safe-state columns default to disabled, so every row created
-- here is inert even if the application forgets to set a value (Req 30.9).

CREATE TABLE feature_flag (
    flag_key         VARCHAR(64) PRIMARY KEY,
    kill_switched    BOOLEAN     NOT NULL DEFAULT TRUE,   -- dominant OFF (Req 30.8)
    default_enabled  BOOLEAN     NOT NULL DEFAULT FALSE,  -- rollout default (Req 30.7)
    description      VARCHAR(255),
    created_at       BIGINT      NOT NULL,
    updated_at       BIGINT      NOT NULL
);

CREATE TABLE feature_flag_override (
    id             BIGSERIAL   PRIMARY KEY,
    flag_key       VARCHAR(64) NOT NULL REFERENCES feature_flag(flag_key),
    restaurant_id  BIGINT      NOT NULL,
    enabled        BOOLEAN     NOT NULL,
    created_at     BIGINT      NOT NULL,
    updated_at     BIGINT      NOT NULL,
    CONSTRAINT uq_feature_flag_override UNIQUE (flag_key, restaurant_id)
);
CREATE INDEX IF NOT EXISTS idx_feature_flag_override_restaurant ON feature_flag_override (restaurant_id);

CREATE TABLE feature_flag_audit (
    id              BIGSERIAL   PRIMARY KEY,
    flag_key        VARCHAR(64) NOT NULL,
    scope           VARCHAR(16) NOT NULL,   -- KILL_SWITCH | DEFAULT | OVERRIDE
    restaurant_id   BIGINT,                 -- NULL for KILL_SWITCH and DEFAULT
    previous_state  VARCHAR(16),            -- ENABLED | DISABLED | ABSENT
    new_state       VARCHAR(16) NOT NULL,
    actor_user_id   BIGINT,
    actor_username  VARCHAR(255),
    changed_at      BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_feature_flag_audit_flag ON feature_flag_audit (flag_key, changed_at DESC);

-- Seed one row per known flag (task 5.2). Every flag starts fully inert:
-- kill_switched=TRUE and default_enabled=FALSE mean the resolved state is
-- disabled for every restaurant until a KBOOK_ADMIN explicitly changes it.
-- Flag keys (design section 1, Req 30.1) cover the ported features of
-- Requirements 17-20, 24 and 25; no flag exists for Requirement 22 (Req 30.2).

INSERT INTO feature_flag (flag_key, kill_switched, default_enabled, description, created_at, updated_at) VALUES
    ('notifications',         TRUE, FALSE, 'FCM push notifications for orders, payments, compliance events (Req 17)',                (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('fssai_compliance',      TRUE, FALSE, 'FSSAI expiry tracking and renewal reminders (Req 18)',                                       (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('marketplace_orders',    TRUE, FALSE, 'Marketplace delivery order ingestion (Req 19)',                                              (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('easebuzz_onboarding',   TRUE, FALSE, 'Easebuzz sub-merchant onboarding (Req 20)',                                                  (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('easebuzz_payments',     TRUE, FALSE, 'Easebuzz payment collection via the EaseBuzz SDK flow (Req 20)',                              (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('kyc_upload',            TRUE, FALSE, 'KYC document upload during onboarding (Req 24)',                                              (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('submerchant_admin',     TRUE, FALSE, 'Sub-merchant administration for gateways (Req 25)',                                           (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT),
    ('restaurant_settings',   TRUE, FALSE, 'Extended restaurant settings surface (Req 26)',                                               (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT);

-- ---------------------------------------------------------------------------
-- Webhook inbox (Requirement 33)
-- ---------------------------------------------------------------------------
-- One row per provider event. Uniqueness is on the (provider_identity,
-- provider_event_id) PAIR, per Req 33.8's explicit prohibition on keying by the
-- provider event id alone. A row is inserted the moment a signed request passes
-- HMAC verification, in the provider HTTP handler; business processing is never
-- performed there, only by the Inbox_Worker once the owning flag is enabled.

CREATE TABLE webhook_inbox (
    id                  BIGSERIAL     PRIMARY KEY,
    provider_identity   VARCHAR(32)   NOT NULL,
    provider_event_id   VARCHAR(191)  NOT NULL,
    event_class         VARCHAR(48)   NOT NULL,
    aggregate_key       VARCHAR(255)  NOT NULL,
    ordering_key        BIGINT        NOT NULL,
    ordering_source     VARCHAR(16)   NOT NULL,   -- SEQUENCE | EVENT_TIME | RECEIPT
    restaurant_id       BIGINT,                   -- NULL => UNRESOLVED (Req 33.26)
    raw_payload         TEXT          NOT NULL,
    received_at         BIGINT        NOT NULL,
    state               VARCHAR(24)   NOT NULL DEFAULT 'UNPROCESSED',
                        -- UNPROCESSED | PROCESSED | NEEDS_REVIEW | UNRESOLVED
    processed_at        BIGINT,
    attempt_count       INT           NOT NULL DEFAULT 0,
    next_attempt_at     BIGINT        NOT NULL DEFAULT 0,
    last_failure_reason VARCHAR(500),
    claimed_by          VARCHAR(64),
    claimed_at          BIGINT,
    lease_expires_at    BIGINT,
    claim_token         BIGINT,                       -- fencing token (design D17)
    CONSTRAINT uq_webhook_inbox_provider_event
        UNIQUE (provider_identity, provider_event_id)     -- Req 33.7, 33.8
);

-- Partial index: claim query targets exactly the UNPROCESSED rows in aggregate
-- order (aggregate_key, ordering_key).
CREATE INDEX IF NOT EXISTS idx_webhook_inbox_claim
    ON webhook_inbox (state, aggregate_key, ordering_key)
    WHERE state = 'UNPROCESSED';
-- Resolve a row to its restaurant once the merchant is identified.
CREATE INDEX IF NOT EXISTS idx_webhook_inbox_restaurant ON webhook_inbox (restaurant_id);
-- Manual review queue for rows the worker could not process.
CREATE INDEX IF NOT EXISTS idx_webhook_inbox_review ON webhook_inbox (state) WHERE state = 'NEEDS_REVIEW';

-- ---------------------------------------------------------------------------
-- Orphaned V6/V7/V8 tables (design D2)
-- ---------------------------------------------------------------------------
-- v1 production already carries these unmapped tables - `easebuzz_webhook_events`,
-- `restaurant_payment_config`, `payments`, `payment_webhook_logs`, and
-- `storefront_customer_orders` - from the original Easebuzz prototype migrations
-- (V6-V8). Requirement 2.8 forbids dropping them, and Requirement 33.8 makes them
-- unusable as the inbox: `easebuzz_webhook_events` is keyed UNIQUE
-- (restaurant_id, txn_id), not the (provider_identity, provider_event_id) pair
-- the inbox mandates, and retrofitting it would require dropping a unique
-- constraint - also forbidden by Requirement 2.8. They are therefore left
-- untouched and read as dead schema by any future maintainer; the purpose-built
-- `webhook_inbox` created above is the one true inbox.