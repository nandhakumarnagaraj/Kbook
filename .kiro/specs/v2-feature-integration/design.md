# Design Document

## Overview

This design implements the one-way harvest of `v2` features onto `main` defined in `requirements.md`. It is organised around the three structural constraints requirements established — forward-only additive Flyway authoring at V48+, a forward-only Room chain from 62, and preservation of `main`'s safety layer — plus the two mechanisms the rollback model depends on: persisted per-restaurant `Feature_Flag` state and a durable `Webhook_Inbox`.

Every decision below is grounded in `main` as it exists at the current head. Where `main`'s code differs from `AGENTS.md`, the code wins and the difference is recorded.

### Corrections to project documentation discovered during design

| Claim | Reality |
|---|---|
| `AGENTS.md`: Android package `com.khanabook.pos` | Actual: `com.khanabook.lite.pos` |
| `AGENTS.md`: web-admin uses `toSignal()` at component level | Pages hand-roll `signal()` + explicit `.subscribe({next,error})`. `ApiStateComponent` exists but is unused |
| Expected `SecurityConfig` in `config/` | Actual: `server/.../saas/security/SecurityConfig.java` |
| Expected `User.Role` nested enum | Actual: top-level `com.khanabook.saas.entity.UserRole` (`OWNER`, `SHOP_ADMIN`, `KBOOK_ADMIN`) |
| `DatabaseProvider` logs `expectedRoomVersion=58` | Schema is at 62. Pre-existing cosmetic bug, fixed in Phase 2 |
| `RestaurantProfileEntity` has an Easebuzz enable flag | It does not. V9 dropped the server columns. Easebuzz enablement lives in `Feature_Flag` + `restaurant_payment_config` |

## The pre-existing schema finding

`main` deleted the Easebuzz *Java* classes (`71f00d96`, `8a75a021`) but never reverted the *schema*. Production PostgreSQL already carries, applied and recorded in `flyway_schema_history`:

| Object | From | Status on `main` |
|---|---|---|
| `bill_payments.gateway_txn_id`, `.gateway_status`, `.verified_by` | V6 | **Mapped** by `BillPayment` and `BillPaymentEntity` |
| `easebuzz_webhook_events` (UNIQUE `restaurant_id, txn_id`) | V6 | Orphan — no entity |
| `restaurant_payment_config` (merchant_key, encrypted_salt, environment, is_active) | V7 | Orphan — no entity |
| `payments` (gateway, gateway_txn_id UNIQUE, checkout_url, …) | V7 | Orphan — no entity |
| `payment_webhook_logs` (payload, signature_valid, processed) | V7 | Orphan — no entity |
| `storefront_customer_orders` | V8 | Orphan — no entity |
| `restaurantprofiles.easebuzz_*` credentials | V6, dropped V9 | Gone |
| CHECK `chk_bill_payment_mode` permitting `'easebuzz'`, `'part_cash_easebuzz'`, `'part_easebuzz_pos'` | V15 | **Live and enforced** |

Three consequences:

1. `spring.jpa.hibernate.ddl-auto=validate` tolerates unmapped tables and columns. The orphans are inert; Requirement 2.8 forbids dropping them.
2. The `'easebuzz'` payment mode is **already legal in production**. V16 documents the intended semantics: `payment_mode='easebuzz'` together with `verified_by='easebuzz'`. No migration is needed to permit the mode.
3. Android already carries dormant gateway plumbing: `BillPaymentEntity` has the three gateway fields (Room 39→40), `PaymentSetValidator.equivalent` already compares all three, and `BillingViewModel` has `_gatewayTxnId`/`_gatewayStatus`/`setGatewayResult()`/`clearGatewayResult()`. `buildPaymentEntities` hardcodes `verifiedBy = "manual"` and never reads them.

**D1 — reuse the existing gateway columns and the already-legal `'easebuzz'` mode instead of adding payment schema.** This removes the payment-schema tranche entirely. New Easebuzz *sub-merchant* and *settlement* schema is still required (V52); new *payment* schema is not.

**D2 — do not adopt `easebuzz_webhook_events` or `payment_webhook_logs` as `Webhook_Inbox`.** `easebuzz_webhook_events` is keyed `UNIQUE(restaurant_id, txn_id)`, not the `(provider_identity, provider_event_id)` pair Requirement 33.8 mandates, and retrofitting would require dropping a unique constraint — forbidden by Requirement 2.8. A purpose-built `webhook_inbox` is created at V48; both orphans are left untouched.

## Architecture

```
Android (Kotlin/Compose, Room 62→63, SQLCipher, per-tenant DB)
   │  HTTPS + JWT + X-Terminal-Token
   ▼
Apache ──/api/v1/──▶ 127.0.0.1:8081  Spring Boot 3.5.x ──▶ PostgreSQL (Flyway V1→V52)
   └──/──▶ static docroot (Angular 18)
```

No topology change. Context path `/api/v1` (Req 13.1), port 8081 (Req 13.4).

### New server components

```
  Provider ──▶ Webhook_Endpoint  (verify → persist → acknowledge → return)
                      │ writes only, never mutates business state
                      ▼
               webhook_inbox  (V48, leased claims)
                      │ claimed atomically per aggregate
                      ▼
               InboxWorker  (dedicated scheduler; sets TenantContext
                             explicitly from each row)
                      │ the ONLY path to business state
                      ▼
   EasebuzzPaymentService · MarketplaceOrderService · SubMerchantService
   · FssaiTrackerService · NotificationService
                      │ all gated by
                      ▼
               FeatureFlagService  (feature_flag + override + audit, V48)
```

## Components and Interfaces

### 1. Feature flags (Requirement 30)

#### Three-state model

A single `global_enabled` column cannot express a single-restaurant pilot: enabling it globally to let an override take effect would enable the feature for every restaurant that has no override. The model therefore separates the dominant kill switch from the rollout default.

```sql
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
CREATE INDEX idx_feature_flag_override_restaurant ON feature_flag_override (restaurant_id);

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
CREATE INDEX idx_feature_flag_audit_flag ON feature_flag_audit (flag_key, changed_at DESC);
```

**D3 — `kill_switched` defaults `TRUE` and `default_enabled` defaults `FALSE`.** Requirement 30.9 requires every flag disabled on first migration; defaulting both to their safe values satisfies it twice over and makes an accidentally-inserted row inert.

#### Resolution order

```
1. flag row absent for key                → DISABLED   (Req 30.11)
2. FeatureConfigGuard fails                → DISABLED   (Req 30.10) — dominates persisted state
3. kill_switched = true                    → DISABLED   (Req 30.8)  — dominates override
4. override row exists for restaurant      → override.enabled       (Req 30.6)
5. otherwise                               → default_enabled        (Req 30.7)
```

Requirement 30.7's "global state" maps to `default_enabled`; Requirement 30.8's "global state disabled" maps to `kill_switched = true`. The two are deliberately distinct columns rather than one.

Operational states this makes reachable:

| Intent | `kill_switched` | `default_enabled` | Overrides |
|---|---|---|---|
| Not yet deployed | `true` | `false` | — |
| Internal test | `false` | `false` | staff restaurant `true` |
| **Single-restaurant pilot** | `false` | `false` | pilot restaurant `true` |
| Staged rollout | `false` | `false` | N restaurants `true` |
| General availability | `false` | `true` | opt-outs `false` |
| **Emergency kill** | `true` | any | ignored |

The emergency-kill row is the first rollback step in Requirement 28.7 and requires exactly one `UPDATE`.

#### Service

```java
public enum FlagState { ENABLED, DISABLED }

public interface FeatureFlagService {
    FlagState resolve(String flagKey, Long restaurantId);
    boolean   isEnabled(String flagKey, Long restaurantId);
    Map<String,Boolean> resolveAllForRestaurant(Long restaurantId);

    void setKillSwitch(String flagKey, boolean killSwitched);   // KBOOK_ADMIN
    void setDefault(String flagKey, boolean defaultEnabled);    // KBOOK_ADMIN
    void setOverride(String flagKey, Long restaurantId, boolean enabled);
    void clearOverride(String flagKey, Long restaurantId);
}
```

Flag keys: `notifications`, `fssai_compliance`, `marketplace_orders`, `easebuzz_onboarding`, `easebuzz_payments`, `kyc_upload`, `submerchant_admin`, `restaurant_settings`. Requirement 30.2 forbids a flag for Requirement 22, so no infrastructure keys exist.

`FeatureConfigGuard` per flag is a predicate over resolved config: `easebuzz_payments` requires merchant key + salt; `notifications` requires a Firebase credential; `marketplace_orders` returns constant `true` because its keys are per-restaurant.

#### Caching (Req 30.12–30.15)

**D4 — Caffeine instance owned directly by `FeatureFlagService`, `expireAfterWrite` = propagation deadline.** Requirement 22.1's `CacheManager` arrives in Phase 3, after flags land in Phase 2; owning the instance keeps Phase 2 self-contained with no forward dependency. `TokenRevocationCache` is the existing precedent for an owned cache in this codebase.

```properties
khanabook.feature-flags.propagation-deadline-seconds=${FEATURE_FLAG_PROPAGATION_SECONDS:30}
```

Key is `flagKey + ':' + restaurantId`. Writes invalidate eagerly on the writing instance; the TTL bounds staleness elsewhere. Production runs one container, so eager invalidation is effectively immediate and the TTL is the correctness floor.

#### Flag_Admin_Surface (Req 30.20–30.22, Phase 2)

```
GET    /api/v1/admin/feature-flags
GET    /api/v1/admin/feature-flags/{key}/audit
PUT    /api/v1/admin/feature-flags/{key}/kill-switch      { killSwitched }
PUT    /api/v1/admin/feature-flags/{key}/default          { defaultEnabled }
PUT    /api/v1/admin/feature-flags/{key}/restaurants/{rid} { enabled }
DELETE /api/v1/admin/feature-flags/{key}/restaurants/{rid}
```

All `@RequireRole(UserRole.KBOOK_ADMIN)`. They sit under `/admin/**`, already restricted to `hasRole("KBOOK_ADMIN")` by `SecurityConfig`, so the aspect is defence-in-depth satisfying Requirement 5.5 without a `SecurityConfig` change.

#### Client contract (Req 30.23–30.27)

Feature state rides the existing pull response rather than adding a round trip:

```java
// MasterSyncResponseDTO — additive field (Req 14.2)
private Map<String, Boolean> enabledFeatures;
```

Legacy Gson clients ignore the unknown field. New clients cache it, and treat absence or expiry as all-disabled (30.25, 30.26). Requirement 30.27 holds structurally: flags gate only new entry points and no Base_Branch path consults them.

### 2. Webhook inbox (Requirement 33)

#### Schema (V48)

```sql
CREATE TABLE webhook_inbox (
    id                  BIGSERIAL     PRIMARY KEY,
    provider_identity   VARCHAR(32)   NOT NULL,
    provider_event_id   VARCHAR(191)  NOT NULL,
    event_class         VARCHAR(48)   NOT NULL,
    aggregate_key       VARCHAR(255)  NOT NULL,
    ordering_key        BIGINT        NOT NULL,
    ordering_source     VARCHAR(16)   NOT NULL,   -- SEQUENCE | EVENT_TIME | RECEIPT
    restaurant_id       BIGINT,                   -- NULL ⇒ UNRESOLVED (Req 33.26)
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
    claim_token         BIGINT,                   -- fencing token (D17)
    CONSTRAINT uq_webhook_inbox_provider_event
        UNIQUE (provider_identity, provider_event_id)     -- Req 33.7, 33.8
);

CREATE INDEX idx_webhook_inbox_claim
    ON webhook_inbox (state, aggregate_key, ordering_key)
    WHERE state = 'UNPROCESSED';
CREATE INDEX idx_webhook_inbox_restaurant ON webhook_inbox (restaurant_id);
CREATE INDEX idx_webhook_inbox_review ON webhook_inbox (state) WHERE state = 'NEEDS_REVIEW';
```

Uniqueness is on the **pair**, per Requirement 33.8's explicit prohibition on keying by event id alone.

#### Endpoint contract (Req 33.2–33.6, 33.29–33.30)

```java
@PostMapping("/payments/easebuzz/webhook")
public ResponseEntity<String> easebuzzWebhook(@RequestBody String rawBody,
                                              @RequestHeader Map<String,String> headers) {
    // 1. credential absent/invalid → 503, persist nothing        (33.19, 33.20)
    // 2. verify HMAC over rawBody                                (33.2)
    // 3. invalid → 401, persist nothing                          (33.3)
    // 4. derive identity/class/aggregate/ordering, persist row   (33.4)
    // 5. return the provider's expected success response         (33.5)
    //    business processing NEVER happens here                  (33.29, 33.30)
}
```

**D5 — the handler is structurally incapable of mutating business state.** It depends on `WebhookInboxService` only, never on a business service. This makes Requirement 33.29 verifiable by dependency inspection rather than review discipline, and a test asserts the constructor's dependency set.

`SecurityConfig` gains the webhook paths as `permitAll`, placed before the `/payments/**` matchers, because providers present no JWT — authentication is by HMAC, which is why Requirement 33.2 makes verification unconditional and first:

```java
.requestMatchers("/payments/easebuzz/webhook", "/payments/easebuzz/refund/webhook",
                 "/payments/easebuzz/payout/webhook", "/payments/easebuzz/sub-merchant/webhook",
                 "/marketplace/webhook/swiggy", "/marketplace/webhook/zomato").permitAll()
```

#### Descriptor abstraction

```java
public interface WebhookDescriptor {
    String       providerIdentity();
    boolean      verify(String rawBody, Map<String,String> headers);
    String       providerEventId(JsonNode payload);
    EventClass   eventClass(JsonNode payload);
    String       aggregateKey(JsonNode payload);      // Req 33.12
    OrderingKey  orderingKey(JsonNode payload);       // Req 33.10, 33.18
    Long         resolveRestaurantId(JsonNode payload);
}
record OrderingKey(long value, OrderingSource source) {}  // SEQUENCE | EVENT_TIME | RECEIPT
```

Aggregate formats, exactly per Requirement 33.12:

| Event class | `aggregate_key` |
|---|---|
| `MARKETPLACE_ORDER` | `{provider}:{restaurantId}:order:{externalOrderId}` |
| `PAYMENT` | `easebuzz:{restaurantId}:txn:{gatewayTxnId}` |
| `REFUND` | `easebuzz:{restaurantId}:txn:{refundedGatewayTxnId}` |
| `PAYOUT` | `easebuzz:payout:{payoutId}`, else `easebuzz:submerchant:{subMerchantId}` |
| `SUBMERCHANT_KYC` | `easebuzz:submerchant:{subMerchantId}` |
| `COMPLIANCE` | `{restaurantId}:licence:{licenceId}` |

Requirement 33.13 forbids the restaurant alone — every format carries a narrower discriminator. Requirement 33.14's unextractable case falls back to `unresolved:{provider}:{providerEventId}`, unique per record so it blocks nothing.

`OrderingKey` normalises all three sources onto one `BIGINT` so the claim query sorts in SQL, and records `ordering_source` so Requirement 33.18's receipt-derived ordering is observable rather than silent.

#### InboxWorker: atomic claims with leases (Req 33.31–33.37)

**D6 — atomic per-row claims via `FOR UPDATE SKIP LOCKED` plus a processing lease. No global advisory lock.** A single global lock would serialise every aggregate, contradicting Requirement 33.14, and would leave rows permanently stuck if the worker died mid-processing. Claims plus leases give per-aggregate concurrency, crash recovery, and retry backoff with no extra table.

Claim query — takes the **lowest `ordering_key`** row of one aggregate:

```sql
UPDATE webhook_inbox
   SET claimed_by = :workerId,
       claimed_at = :now,
       lease_expires_at = :now + :leaseMs,
       attempt_count = attempt_count + 1
 WHERE id = (
     SELECT id FROM webhook_inbox
      WHERE state = 'UNPROCESSED'
        AND aggregate_key = :aggregateKey
        AND next_attempt_at <= :now
        AND (lease_expires_at IS NULL OR lease_expires_at < :now)
      ORDER BY ordering_key ASC
      LIMIT 1
      FOR UPDATE SKIP LOCKED
 )
RETURNING *;
```

Four properties fall out of this one statement:

- **Atomic claim** — `FOR UPDATE SKIP LOCKED` means two workers never claim the same row (Req 33.37).
- **Crash recovery** — a worker that dies leaves `lease_expires_at` in the past; the predicate makes the row claimable again with no operator action.
- **Head-of-aggregate ordering** — `ORDER BY ordering_key ASC LIMIT 1` claims only the head. Requirement 33.15's ordering is therefore enforced by the claim, not by application sorting.
- **Failure halts only its own aggregate** — a failed head row gets `next_attempt_at` in the future, so no row in that aggregate is claimable until it retries, while other aggregates proceed untouched (Req 33.17, 33.42).

Backoff on failure, capped and jittered:

```java
long backoffMs = Math.min(MAX_BACKOFF_MS,
                          BASE_BACKOFF_MS * (1L << Math.min(attemptCount, 10)))
               + ThreadLocalRandom.current().nextLong(JITTER_MS);
```

Past the attempt limit the row moves to `NEEDS_REVIEW` and is surfaced in web-admin (Req 33.25); it is never deleted (Req 33.27).

#### What the lease actually guarantees

**A lease alone does not provide mutual exclusion, and the design must not claim it does.** If a healthy handler runs longer than `lease_expires_at`, a second worker legitimately reclaims the row and both execute concurrently. Any design that treats "lease held" as "sole executor" is wrong for exactly the case that matters — a slow but succeeding handler.

**D17 — the guarantee is _effect-once under at-least-once execution_, achieved by four mechanisms together rather than by the lease.**

**(a) Fencing token.** Each claim mints a monotonic token; every subsequent write by that claim asserts it still owns the row. A reclaimed row invalidates the earlier token, so the stale worker's terminal write fails and is discarded rather than overwriting the new one.

```sql
ALTER TABLE webhook_inbox ADD COLUMN claim_token BIGINT;   -- part of V48
```

```sql
-- every terminal write is fenced
UPDATE webhook_inbox
   SET state = 'PROCESSED', processed_at = :now, claimed_by = NULL,
       claim_token = NULL, lease_expires_at = NULL
 WHERE id = :id AND claim_token = :token;      -- 0 rows ⇒ we were fenced out
```

A zero-row result is logged as `INBOX_FENCED` and the work is abandoned, not retried, because the reclaiming worker owns it now.

**(b) Lease renewal.** A long-running handler extends its own lease on a heartbeat at one third of the lease duration, so a healthy slow handler is not reclaimed at all. Renewal is itself fenced:

```sql
UPDATE webhook_inbox SET lease_expires_at = :now + :leaseMs
 WHERE id = :id AND claim_token = :token;
```

If renewal returns zero rows the handler has already been fenced and cooperatively aborts at its next checkpoint.

**(c) Bounded handler timeout.** Every handler runs under a timeout strictly less than the lease duration, so a hung handler is terminated before its lease can lapse. This makes reclamation a genuine-crash signal rather than a routine slow-path event.

```properties
khanabook.webhook-inbox.lease-ms=${INBOX_LEASE_MS:60000}
khanabook.webhook-inbox.renew-interval-ms=${INBOX_RENEW_MS:20000}
khanabook.webhook-inbox.handler-timeout-ms=${INBOX_HANDLER_TIMEOUT_MS:45000}
```

The invariant `handler-timeout < lease` is asserted at startup; a violating configuration fails fast rather than silently permitting concurrent execution.

**(d) Idempotent database effects.** The decisive mechanism. Even with (a)–(c), concurrent execution must be *safe*, not merely unlikely. Every handler's effect is idempotent at the schema level:

| Handler | Idempotency mechanism |
|---|---|
| `PAYMENT` | `bill_payments` matched by `gateway_txn_id`; existing row updated, never duplicated |
| `REFUND` | refund sum recomputed inside the transaction against `gateway_txn_id` |
| `POST_SPLIT` | `uq_easebuzz_post_split_txn UNIQUE (gateway_txn_id)` |
| `PAYOUT` | `uq_easebuzz_payout_reference UNIQUE (payout_reference)` |
| `MARKETPLACE_ORDER` | unique external order id per provider + restaurant |
| `SUBMERCHANT_KYC` | `uq_easebuzz_sub_merchant_restaurant`; status transition is a monotonic set |
| `COMPLIANCE` | `(tracker_id, alert_window)` uniqueness |

So Requirement 33.37's "prevent concurrent workers from processing the same record simultaneously" is honoured as: (b) and (c) make it not happen in practice, (a) prevents a fenced worker from committing a terminal state change, and (d) makes the residual race harmless. Requirement 33.9 (repeat processing yields the same final state) is the property actually relied upon, and it is tested directly rather than assumed.

**D7 — the InboxWorker sets `TenantContext` explicitly from each inbox row.** `AsyncConfig`'s `TaskDecorator` copies `TenantContext` from the *submitting* thread, which works for `@Async` invoked from a request. A scheduled job has no inbound request and therefore no context to copy — the decorator would propagate nothing. Tenant identity must come from the row's `restaurant_id`:

```java
private void process(WebhookInbox row) {
    try {
        if (row.getRestaurantId() != null) {
            TenantContext.setCurrentTenant(row.getRestaurantId());
        }
        MDC.put("requestId", "inbox-" + row.getProviderIdentity() + "-" + row.getId());
        handlerFor(row.getEventClass()).apply(row);
        markProcessed(row);
    } catch (Exception e) {
        recordFailureWithBackoff(row, e);       // Req 33.24 — never fails the provider
    } finally {
        TenantContext.clear();                  // mirrors JwtRequestFilter's finally
        MDC.clear();
    }
}
```

`TenantContext.setCurrentRole` is deliberately **not** set. Inbox handlers call service-layer methods that require only a tenant; none passes through a `@RequireRole` boundary. A test asserts every handler completes with a null role, which prevents a later change from smuggling in an authorization bypass.

#### Scheduling

**D8 — dedicated `inboxTaskScheduler` and `inboxExecutor`.** `@EnableScheduling` is present but there is no `TaskScheduler` bean and no `spring.task.scheduling.pool.size`, so Spring provisions a single-threaded scheduler already shared by `TokenBlocklistCleanupService`, `PasswordResetOtpService`, and `WebAdminPasswordResetService`. Adding the worker there would serialise it behind token cleanup, breaking Requirements 33.14 and 33.36. The three existing jobs stay where they are.

#### Three-tier eligibility

**D9 — eligibility is evaluated at three distinct scopes. A single global flag check at the provider level would block pilots.**

Resolving `isEnabled(flag, null)` in the provider loop takes the no-override path and returns `default_enabled`. During a pilot that value is `false` by construction, so the provider would be skipped and the pilot restaurant's events would never drain — the same defect the three-state flag model exists to prevent. Eligibility is therefore split:

| Tier | Question | Evaluated with | Effect when false |
|---|---|---|---|
| **Global** | Can this provider be processed at all? | valid provider configuration AND `kill_switched = false` | provider skipped this cycle |
| **Per-record** | Is this row's restaurant enabled? | `resolve(flag, row.restaurantId)` | row left `UNPROCESSED`, aggregate not advanced |
| **Pre-mutation** | Is it still enabled right now? | `resolve(flag, row.restaurantId)` re-read | claim released, row left `UNPROCESSED` |

```java
@Scheduled(fixedDelayString = "${khanabook.webhook-inbox.poll-interval-ms:5000}",
           scheduler = "inboxTaskScheduler")
public void drain() {
    for (String provider : providers) {
        // Tier 1 — global gate only. Deliberately NOT a per-restaurant resolve.
        if (!flags.isProviderProcessable(flagFor(provider))) continue;   // Req 33.35
        for (String aggregate : claimableAggregates(provider)) {
            inboxExecutor.submit(() -> drainAggregate(aggregate));       // Req 33.14
        }
    }
}

// FeatureFlagService — global tier, independent of any restaurant
boolean isProviderProcessable(String flagKey) {
    FeatureFlag f = repo.find(flagKey).orElse(null);
    return f != null && !f.isKillSwitched() && configGuard(flagKey).isSatisfied();
}
```

Tier 2 runs inside `process(row)` before the handler is invoked. Tier 3 runs inside the handler's transaction immediately before the first business mutation, so a flag disabled mid-flight cannot land a half-applied effect:

```java
private void process(WebhookInbox row, ClaimToken token) {
    try {
        if (row.getRestaurantId() != null) {
            TenantContext.setCurrentTenant(row.getRestaurantId());
        }
        MDC.put("requestId", "inbox-" + row.getProviderIdentity() + "-" + row.getId());

        // Tier 2 — per-record, uses the row's own restaurant
        if (!flags.isEnabled(flagFor(row), row.getRestaurantId())) {
            releaseClaim(row, token);          // stays UNPROCESSED, no backoff penalty
            return;                            // Req 30.9, 33.34
        }
        handlerFor(row.getEventClass()).apply(row, token);   // Tier 3 inside
        markProcessed(row, token);
    } catch (Exception e) {
        recordFailureWithBackoff(row, token, e);   // Req 33.24
    } finally {
        TenantContext.clear();
        MDC.clear();
    }
}
```

Tier 2 releasing the claim without incrementing backoff matters: a restaurant that is simply not yet enabled must not accumulate attempts toward `NEEDS_REVIEW`. Its backlog drains untouched the moment its override flips (Req 33.34).

An `UNRESOLVED` row has no `restaurant_id`, so Tier 2 cannot resolve a flag for it. Those rows are never processed — they are retained for operator review per Requirement 33.26 and excluded from the claim query by `state = 'UNPROCESSED'`.

### 3. Easebuzz integration (Requirement 20)

D1 removes the payment-schema work, but the Android payment surface still needs deliberate expansion across five areas.

#### 3.1 Server schema (V52, Phase 7)

`easebuzz_sub_merchant`, `easebuzz_payout`, and `easebuzz_post_split` are genuinely new, authored fresh against V45 state per Requirement 2.3/2.5. v2 needed six migrations here (its V22, V23, V24, V26 corrective FK fix, V34, V35); this collapses them into one correct migration, which Requirement 2.5 mandates and 2.6 requires recorded.

```sql
CREATE TABLE easebuzz_sub_merchant (
    id                 BIGSERIAL    PRIMARY KEY,
    restaurant_id      BIGINT       NOT NULL,
    sub_merchant_key   VARCHAR(128),
    sub_merchant_id    VARCHAR(128),
    status             VARCHAR(32)  NOT NULL,  -- PENDING|SUBMITTED|ACTIVE|FAILED|REJECTED
    legal_entity_name  VARCHAR(255),
    fssai_number       VARCHAR(32),
    gstin              VARCHAR(20),
    address_line1      VARCHAR(255),
    address_city       VARCHAR(128),
    address_state      VARCHAR(128),
    address_pincode    VARCHAR(12),
    business_proof_url TEXT,
    kyc_document_url   TEXT,
    failure_reason     VARCHAR(500),
    submitted_at       BIGINT,
    activated_at       BIGINT,
    created_at         BIGINT       NOT NULL,
    updated_at         BIGINT       NOT NULL,
    CONSTRAINT uq_easebuzz_sub_merchant_restaurant UNIQUE (restaurant_id)
);

CREATE TABLE easebuzz_post_split (
    id                   BIGSERIAL     PRIMARY KEY,
    restaurant_id        BIGINT        NOT NULL,
    gateway_txn_id       VARCHAR(128)  NOT NULL,
    settled_amount       NUMERIC(12,2) NOT NULL,
    platform_share       NUMERIC(12,2) NOT NULL,
    sub_merchant_share   NUMERIC(12,2) NOT NULL,
    commission_bps       INT           NOT NULL,
    state                VARCHAR(24)   NOT NULL,  -- PENDING|COMPLETED|FAILED
    completed_at         BIGINT,
    created_at           BIGINT        NOT NULL,
    updated_at           BIGINT        NOT NULL,
    CONSTRAINT uq_easebuzz_post_split_txn UNIQUE (gateway_txn_id)   -- Req 20.9 idempotency
);

CREATE TABLE easebuzz_payout (
    id               BIGSERIAL     PRIMARY KEY,
    restaurant_id    BIGINT        NOT NULL,
    sub_merchant_id  BIGINT        REFERENCES easebuzz_sub_merchant(id),
    payout_reference VARCHAR(128),
    amount           NUMERIC(12,2) NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    reported_at      BIGINT        NOT NULL,
    created_at       BIGINT        NOT NULL,
    updated_at       BIGINT        NOT NULL,
    CONSTRAINT uq_easebuzz_payout_reference UNIQUE (payout_reference)
);
```

`uq_easebuzz_post_split_txn` is what makes Requirement 20.9 (`Post_Split` returns the existing split, creates no additional one) an invariant enforced by the database rather than by application check-then-act. Merchant credentials continue to live in the existing orphan `restaurant_payment_config`, which Phase 7 finally gives an entity — no new credential table.

#### 3.2 Payment mode enum and split components

`PaymentMode` gains three values (Requirement 14.6 — additive):

```kotlin
EASEBUZZ("easebuzz", "Easebuzz"),
PART_CASH_EASEBUZZ("part_cash_easebuzz", "Cash + Easebuzz"),
PART_EASEBUZZ_POS("part_easebuzz_pos", "Easebuzz + POS"),
```

All three already pass production's `chk_bill_payment_mode`. `PaymentModeManager` needs four edits, not one — the earlier design missed three:

```kotlin
fun getEnabledModes(profile, easebuzzEnabled: Boolean): List<PaymentMode> {
    // …existing profile-driven modes unchanged…
    if (easebuzzEnabled) modes.add(PaymentMode.EASEBUZZ)
    if (profile.cashEnabled && easebuzzEnabled) modes.add(PaymentMode.PART_CASH_EASEBUZZ)
    if (easebuzzEnabled && profile.posEnabled) modes.add(PaymentMode.PART_EASEBUZZ_POS)
    return modes
}

fun isPartPayment(mode) = /* …existing… */ ||
    mode == PaymentMode.PART_CASH_EASEBUZZ || mode == PaymentMode.PART_EASEBUZZ_POS

fun getPartLabels(mode) = when (mode) {
    /* …existing… */
    PaymentMode.PART_CASH_EASEBUZZ -> "Cash" to "Easebuzz"
    PaymentMode.PART_EASEBUZZ_POS  -> "Easebuzz" to "POS"
    else -> "" to ""
}

fun getPaymentComponents(mode, totalAmount, partAmount1, partAmount2) = when (mode) {
    /* …existing… */
    PaymentMode.PART_CASH_EASEBUZZ -> listOf(
        PaymentComponent(PaymentMode.CASH, partAmount1),
        PaymentComponent(PaymentMode.EASEBUZZ, partAmount2))
    PaymentMode.PART_EASEBUZZ_POS -> listOf(
        PaymentComponent(PaymentMode.EASEBUZZ, partAmount1),
        PaymentComponent(PaymentMode.POS, partAmount2))
    else -> listOf(PaymentComponent(mode, totalAmount))
}
```

**D10 — `easebuzzEnabled` is a new parameter, not a `RestaurantProfileEntity` field.** V9 dropped the profile credential columns and `RestaurantProfileEntity` has no Easebuzz flag. Enablement is the resolved `easebuzz_payments` feature flag, delivered via `MasterSyncResponseDTO.enabledFeatures`. Passing it as a parameter keeps `getEnabledModes` pure and avoids adding a synced profile column, which would require a Room migration and a sync DTO change for a value the server already sends.

Requirement 30.24 (hide, don't fail) is satisfied here: with the flag off, no Easebuzz mode enters `selectableModes`, so `NewBillScreen` needs no conditional.

#### 3.3 Validator and DAO

```kotlin
// PaymentSetValidator — one-line addition, not a replacement (Req 20.21)
private val supportedModes = setOf("cash", "upi", "pos", "easebuzz")
```

`assessForRecovery` shares `supportedModes`, so partial-payment recovery covers Easebuzz automatically. `BillingViewModel.recoverPartialDraftPayment`'s hardcoded `setOf(CASH, UPI, POS)` guard gains `EASEBUZZ`.

`BillDao.finalizeOnlineBillAtomically`'s mode-set derivation gains the two arms V15 already permits:

```kotlin
setOf("cash", "easebuzz") -> "part_cash_easebuzz"
setOf("easebuzz", "pos")  -> "part_easebuzz_pos"
```

#### 3.4 Where the dormant plumbing connects

`buildPaymentEntities` is the single disconnection point. It currently hardcodes `verifiedBy = "manual"` and ignores `_gatewayTxnId`/`_gatewayStatus`:

```kotlin
val isGateway = component.mode == PaymentMode.EASEBUZZ
BillPaymentEntity(
    /* …unchanged… */
    verifiedBy    = if (isGateway) "easebuzz" else "manual",
    gatewayTxnId  = if (isGateway) _gatewayTxnId.value else null,
    gatewayStatus = if (isGateway) _gatewayStatus.value else null
)
```

This matches V16's documented semantics exactly and has a consequence worth stating: the server's existing `GenericSyncService` idempotency branch (`findByRestaurantIdAndGatewayTxnId` → `isExactPaymentMatch` → acknowledge with `localToServerIdMap`) is currently **unreachable code**. Connecting `gatewayTxnId` makes it live, giving Easebuzz payment retry-safety across dropped sync responses with no server sync change.

`PaymentGatewayHelper`'s UPI-family predicate gains `EASEBUZZ` so `setPaymentMode`'s existing `clearGatewayResult()` behaviour is correct for the new modes.

**D11 — touch no other `PaymentMode` switch site.** The ~20 remaining sites (`OrdersScreen`, `ReportsScreen`, `SearchScreen`, `ReportExporter`, `ReportGenerator`, `getPayModeColor`, `ActiveOrderScreen`) either route through `fromDbValue` with a `CASH` fallback or carry `else` arms, so new enum values are safe. Only `getPayModeColor` gains an explicit arm, for legibility rather than correctness.

#### 3.5 SDK checkout and recovery

`NewBillScreen.PaymentStep` renders modes through a dropdown over `selectableModes`, so Easebuzz needs no new UI control. Two behavioural additions:

- When the selected mode includes an Easebuzz component, the terminal button launches the Easebuzz SDK activity instead of showing the UPI QR. `paymentFlowLocked` — which already exists to keep users inside the flow during online confirmation — covers the SDK round trip unchanged.
- `EasebuzzPaymentScreen` is a new screen built with `KhanaBookTheme.spacing`/`iconSize` and `MaterialTheme.typography` per Requirement 15.4/15.5.

Requirement 20.22 requires the bill id and payment operation id persisted **before** the SDK launches. `createDraftOnlineBill()` already does exactly this: it writes a `DRAFT`+`PENDING` bill with deterministic `operationId = "{restaurantId}:{terminalId}:{publicToken}:create_bill"` and stashes the id in `SavedStateHandle` under `pending_online_bill_id`. The Easebuzz path reuses it unchanged.

`EasebuzzPaymentRecoveryWorker` (Requirement 20.15) reconciles after process death by querying `getLatestPendingOnlineBill()` — which already exists and already filters to `payment_mode IN ('upi','part_cash_upi','part_upi_pos')`. **That filter must gain the three Easebuzz modes**, in both `BillDao.getLatestPendingOnlineBill` overloads, `getPendingOnlineBillsFlow`, `getRestorablePendingOnlineBillWithItems`, and `cancelStalePendingOnlineDrafts`. Missing any one of them would leave an interrupted Easebuzz payment unrecoverable — this is the least obvious and highest-risk edit in the phase.

#### 3.6 Settlement and post-split state

Settlement arrives by webhook, so it flows through the inbox: `PAYMENT` events update `bill_payments.gateway_status`; a settled event additionally enqueues `Post_Split`. `PostSplitService.apply(gatewayTxnId)` inserts into `easebuzz_post_split` and relies on `uq_easebuzz_post_split_txn` to make repeat application a no-op that returns the existing row (Requirement 20.9). Platform and sub-merchant shares are computed to sum exactly to `settled_amount` with the remainder assigned to the sub-merchant, satisfying Requirement 20.8 and property P4 without rounding drift.

`RefundService` caps refunds against the gateway-paid amount (Requirements 20.10, 20.11) by summing prior refunds for the `gateway_txn_id` inside the same transaction that inserts the new one.

### 4. Marketplace orders (Requirement 19)

**D12 — the Android marketplace screen is backed by a direct REST repository, not by bill-pull rows.** The earlier design was wrong to claim marketplace orders arrive through the bill pull. They do arrive there as `source_channel IN ('zomato','swiggy','own_website')` rows — but those are deliberately *read-only history*: `BillDao.getOperationalBillById` excludes them explicitly, and `BillRepository.isLocallyOwned` rejects them, so no accept/reject/mark-ready action can ever be performed on them. Requirement 19.2 requires exactly those actions.

```kotlin
class MarketplaceOrderRepository(
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager
) {
    suspend fun list(status: String?): Result<List<MarketplaceOrderDto>>
    suspend fun counts(): Result<MarketplaceOrderCounts>
    suspend fun accept(orderId: Long): Result<Unit>
    suspend fun reject(orderId: Long, reason: String): Result<Unit>
    suspend fun markReady(orderId: Long): Result<Unit>
    suspend fun complete(orderId: Long): Result<Unit>
}
```

**D13 — no Room table for marketplace orders.** They are server-authoritative, mutable by external parties, and every action requires connectivity. Caching them locally would create a stale-state surface with no offline benefit, and would add a Room migration for no gain. `MarketplaceOrdersScreen` renders an explicit offline state when `NetworkMonitor.status` is unavailable. This keeps the Room chain at 62→63 across all phases.

`MarketplaceOrdersScreen` is a separate entry point from `OrdersScreen`; Requirement 10.2 preserves the ActiveOrders screens untouched. Converting a marketplace order into a local bill is explicitly out of scope for this spec.

Server side, `MarketplaceOrderService` is driven by the inbox for webhook ingestion (Requirement 19.3–19.5) and directly by the controller for merchant actions. Requirement 19.1 preserves `main`'s existing `MarketplaceConfigController` behaviour and adds the order service alongside it.

### 5. Notifications (Requirement 17)

Server: `DeviceToken`, `NotificationEvent`, `NotificationTemplate` entities over V49. Token registration upserts on the token string so repeat registration yields exactly one active row (Requirement 17.3, property P11). Delivery failure with a permanent FCM error marks the token inactive and the originating business operation still succeeds (17.5); an absent or invalid Firebase credential resolves the `notifications` flag to disabled via `FeatureConfigGuard`, so the operation also succeeds (17.6, 17.14).

**D14 — notification dispatch runs on a dedicated `notificationExecutor`, never on the request thread and never on `inboxExecutor`.** A push attempt must not extend or fail a bill finalisation — the same constraint Requirement 32.7 places on email. It must also not consume webhook-processing capacity: Firebase calls are outbound network I/O with tail latencies measured in seconds, and sharing `inboxExecutor` would let a slow FCM endpoint starve payment and marketplace event draining. `notificationExecutor` and `emailExecutor` are separate bounded pools with caller-runs rejection, so saturation degrades notification delivery only.

Android: `NotificationEntity` is the single new tenant entity (Room 62→63), registered in the tenant database per Requirement 7.5 and reached through a new `TenantNotificationDao` following the established `dao get()` / `runFlow {}` split. `KhanaBookFirebaseMessagingService`, `NotificationHelper` (channel groups: orders, payments, system, promotions — Requirement 17.10), `NotificationActionReceiver`, `BootReceiver` with `RECEIVE_BOOT_COMPLETED` (17.9), and `OemHardeningHelper` behind a `NotificationReliabilityScreen`. Deep links route through the existing `NavController` graph (17.8).

### 6. FSSAI and GST compliance (Requirement 18)

`fssai_tracker` and `fssai_renewal` over V50, plus compliance expiry columns added to `restaurantprofiles` as nullable. `ComplianceAlertService` runs on the existing default scheduler — it is low-frequency and has no ordering requirement, so it does not need `inboxTaskScheduler`.

Alert deduplication (Requirement 18.3, property P14) is enforced by a unique constraint on `(tracker_id, alert_window)` rather than a query-then-insert, making repeated schedule runs idempotent by construction.

`Pay Now` is gated on the `easebuzz_payments` flag (Requirement 18.5, 18.6): Phase 5 ships the tracker and `Remind Me` only, and the action appears once Phase 8 enables collection. `GstFssaiLookupService` backs the three lookup endpoints.

### 7. KYC document upload (Requirement 24)

Content-type and size validated **before** any bytes are written (24.2, 24.3, property P24), reusing the existing `kbook.cdn.*` local-CDN configuration and its `max-upload-bytes` pattern rather than introducing a second storage mechanism. Read access is restricted to the owning restaurant and `KBOOK_ADMIN` (24.4) via `@RequireRole` plus a tenant check. Android uses the Android 13+ `PickVisualMedia` picker already present on `main` (24.5).

### 8. Invoice template migration (Requirement 31, Phase 9)

Both renderers coexist, selected by configuration, defaulting to the Base_Branch `StringBuilder` renderer (31.3, 31.4):

```properties
khanabook.invoice.renderer=${INVOICE_RENDERER:stringbuilder}   # stringbuilder | thymeleaf
```

A template exception falls back to the StringBuilder renderer and records the failure (31.5). The old renderer is removed only in Phase 11 after a recorded observation period (31.6). A differential test asserts field equality across a generated bill set (31.7, property P32).

### 9. Transactional email (Requirement 32)

`EmailNotificationService` over `spring-boot-starter-mail`. Only two templates ship: `onboarding-welcome` in Phase 7 and `refund-confirmation` in Phase 8, each in the phase owning its trigger (32.2, 32.3). `settlement-notification` and `chargeback-alert` are excluded because Requirement 25 defers their features (32.4). Absent SMTP credentials log and let the operation succeed (32.5); dispatch never blocks the triggering request thread (32.7).

### 10. Operational infrastructure (Requirement 22, Phase 3, non-flagged)

**D15 — the only cache registered is `restaurantProfileReadOnly`, 60-second TTL, web-admin reads only.** Requirement 22.8 bars caching anything whose staleness could alter a billing, payment, sync, or terminal decision. `RestaurantProfile` participates in `mergeCounterState` during sync, and a stale read there would corrupt invoice numbering — so sync, billing, and terminal paths are explicitly excluded.

MDC propagation (22.3) extends `AsyncConfig`'s existing `TaskDecorator` to copy the `requestId` key alongside `TenantContext`. This closes a real current gap: the console pattern already emits `[requestId=%X{requestId}]`, but `@Async` methods lose it today.

`logback-spring.xml` adds a JSON encoder; `application-prod.properties` already sets `logging.structured.format.console=ecs`. Requirement 22.5's guarantee is that `GlobalExceptionHandler`'s 8-character `errorId` stays greppable — it becomes a JSON field, and `ops/PRODUCTION_STACK.md` is updated in the same commit (22.9).

### 11. Web admin

New pages, all following the established hand-rolled pattern (`signal()` state, explicit `.subscribe({next,error})`, `.page-shell`/`.panel`/`.data-table`, `<app-empty-state>`, skeleton `<ng-template #loading>`). Methods are added to the existing `AdminApiService`/`BusinessApiService` — no new service classes, consistent with `environment.apiBaseUrl` being a module-level `const`.

| Phase | Route | Roles |
|---|---|---|
| 2 | `admin/feature-flags` | `KBOOK_ADMIN` |
| 2 | `admin/webhook-inbox` | `KBOOK_ADMIN` |
| 5 | `business/compliance` | `OWNER` |
| 6 | `business/marketplace-orders` | `OWNER` |
| 7 | `admin/sub-merchants` | `KBOOK_ADMIN` |
| 7 | `business/settings` | `OWNER` |

`styles.css` remains the styling source; `styles.scss` is excluded (Requirement 15.6).

## Data Models

### Flyway allocation

| Version | Phase | Contents | Subsumes v2 |
|---|---|---|---|
| V48 | 2 | `feature_flag`, `feature_flag_override`, `feature_flag_audit`, `webhook_inbox` | none (new) |
| V49 | 4 | `device_token`, `notification_event`, `notification_template` | v2 V36, V39 |
| V50 | 5 | `fssai_tracker`, `fssai_renewal`, nullable compliance expiry columns | v2 V28, V37, V38 |
| V51 | 6 | `marketplace_order`, `marketplace_order_item` | v2 V25, V27 |
| V52 | 7 | `easebuzz_sub_merchant`, `easebuzz_post_split`, `easebuzz_payout` | v2 V22, V23, V24, V26, V34, V35 |
| — | 8 | none — D1 | v2 payment migrations dropped entirely |
| — | 9 | none | — |

Excluded per Requirement 25.3/25.4: chargebacks (v2 V30), customer profiles (V31, V33), webhook retry jobs (V32 — superseded by `webhook_inbox`), refresh tokens (V29 — Requirement 23 defers).

Every migration is additive only: `CREATE TABLE`, nullable-or-defaulted `ADD COLUMN`, `CREATE INDEX`, constraints that hold over existing rows. No drop, rename, or type narrowing (Requirement 2.8).

### Room allocation

| Version | Phase | Contents |
|---|---|---|
| 63 | 4 | `notifications` table (`NotificationEntity`) |

**D16 — register every new Room migration in both `DatabaseProvider.buildDatabaseWithName` and `DatabaseModule.buildDatabase`.** The list is duplicated on `main`; omitting either causes a runtime failure on whichever path a device takes. A Phase 2 test asserts the two lists are identical, eliminating the bug class for all later phases.

Adding `NotificationEntity` follows the 11-step tenant-entity path: entity, `AppDatabase.entities` + version bump to 63, `MIGRATION_62_63` using `CREATE TABLE IF NOT EXISTS`, registration in both lists, `NotificationDao`, `abstract fun notificationDao()`, `TenantNotificationDao`, a `@Provides @Singleton` binding, `restaurantId` on the entity with every query filtered by it (Requirement 7.4/7.5), and the exported schema JSON committed in the same commit (28.13). `fallbackToDestructiveMigration` is absent today and stays absent (3.5).

## Error Handling

| Condition | Response | Requirement |
|---|---|---|
| Flag disabled — merchant/admin endpoint | 503 | 30.16 |
| Flag disabled — webhook endpoint | provider success + inbox row retained | 33.6, 33.18 |
| Verification credential absent/invalid | 503, persist nothing | 33.19, 33.20 |
| HMAC invalid | 401, persist nothing | 33.3 |
| Inbox processing failure | `UNPROCESSED` + backoff, provider never sees failure | 33.24 |
| Past attempt limit | `NEEDS_REVIEW`, surfaced in web-admin, never deleted | 33.25, 33.27 |
| Restaurant unresolvable | `UNRESOLVED`, retained | 33.26 |
| Worker crash mid-processing | lease expires, row reclaimable, no operator action | D6 |
| `BusinessRuleException` | 400 | 22.4 |
| `EntityNotFoundException` | 404 | 22.4 |
| `EasebuzzApiException` | 502, local record left retryable | 20.16, 20.17 |
| Refund exceeding gateway-paid amount | rejected | 20.10, 20.11 |
| Config absent at startup | server starts, feature disabled, name logged | 27.7 |
| Upload oversize / disallowed type | 400, no bytes written | 24.3 |
| Template render failure | fall back to StringBuilder, record | 31.5 |
| SMTP absent | log, operation succeeds | 32.5 |

## Requirement Traceability

Requirement 33 has 42 criteria; this maps each to its design mechanism, as the review required.

| Criteria | Mechanism |
|---|---|
| 33.1 | `WebhookDescriptor` registry defines the `Webhook_Endpoint` set; exempted from 30.16 by D5 |
| 33.2, 33.3 | `verify()` first in handler; 401 before any persist |
| 33.4 | `WebhookInboxService.persist()` writes payload + identity + ordering + receipt before processing |
| 33.5 | Per-descriptor provider success response |
| 33.6 | Flag state not consulted by the handler at all; persistence is unconditional post-verification |
| 33.7, 33.8 | `uq_webhook_inbox_provider_event UNIQUE (provider_identity, provider_event_id)` |
| 33.9 | Handlers idempotent; `uq_easebuzz_post_split_txn` and marketplace external-id uniqueness enforce it |
| 33.10, 33.11 | `OrderingKey` record; `ordering_source` column; event id never used for ordering |
| 33.12, 33.13, 33.14 | `aggregateKey()` table; no restaurant-only format; `unresolved:` fallback |
| 33.15 | `ORDER BY ordering_key ASC LIMIT 1` in the claim query |
| 33.16 | Aggregates claimed independently; `inboxExecutor` fan-out |
| 33.17 | `next_attempt_at` on the head row blocks only its own aggregate |
| 33.18 | `ordering_source = RECEIPT` recorded explicitly |
| 33.19, 33.20, 33.21 | Credential guard returns 503 before persist; no unverified write path exists |
| 33.22 | Per-descriptor retry-eliciting status override |
| 33.23 | Credential absence surfaced as a health signal |
| 33.24 | `recordFailureWithBackoff` retains `UNPROCESSED`, never propagates to provider |
| 33.25 | `NEEDS_REVIEW` + `admin/webhook-inbox` page |
| 33.26 | Nullable `restaurant_id`, `UNRESOLVED` state |
| 33.27 | No delete path for `UNPROCESSED`; retention applies to `PROCESSED` only |
| 33.28 | `restaurant_id` on every row; unresolved recorded not discarded |
| 33.29, 33.30 | D5 — handler depends on `WebhookInboxService` only; asserted by dependency test |
| 33.31 | `InboxWorker` on `inboxTaskScheduler`, independent of any request |
| 33.32, 33.33 | One `handlerFor()` path regardless of arrival-time flag state |
| 33.34 | Re-enabling drains backlog with no operator action |
| 33.35 | Flag check inside the provider loop |
| 33.36 | `poll-interval-ms` bounds persist-to-process delay |
| 33.37 | `FOR UPDATE SKIP LOCKED` + lease |
| 33.38–33.42 | Phase 2 and Phase 8 test suites |

### All other requirements

Every requirement gets an explicit row, including deferrals and exclusions, so none can silently vanish when tasks are generated.

| Req | Title | Design location | Disposition |
|---|---|---|---|
| 1 | One-Way Integration Direction | Phase 0 / Baseline Precondition; no `Source_Branch` merge parent anywhere in the plan | **Process constraint** — enforced by branch protocol and a CI check that `git log --merges` names no v2 commit |
| 2 | Forward-Only Additive Schema | Data Models → Flyway allocation | V48–V52, additive only |
| 3 | Room Migration Chain | Data Models → Room allocation; D16 | 62→63 only |
| 4 | Multi-Terminal Preservation | Testing Strategy → Phase 1 | No design change; existing tests are the gate |
| 5 | Role/Authorization Preservation | §1 Flag_Admin_Surface (`@RequireRole`); Phase 1 aspect-active test | `aop` retained, `SHOP_ADMIN` retained |
| 6 | Security Hardening Preservation | Phase 1 harness; §2 webhook paths added as `permitAll` **without** relaxing any existing matcher | KB-001..009 untouched |
| 7 | Per-Tenant Android DBs | §5 Notifications — `NotificationEntity` in tenant DB via `TenantNotificationDao` | Only new entity honours 7.5 |
| 8 | KOT and Printing Preservation | No component touches KOT | No design change |
| 9 | Sync Engine Hardening | §3.4 — Easebuzz reuses the *existing* `GenericSyncService` idempotency branch; no sync rewrite | Additive only |
| 10 | v1 Billing Behaviour | §3.2–3.5 — additive enum values, `else`-arm safety (D11) | `OrderPaymentFlowMode` untouched |
| 11 | v1 Web Admin Preservation | §11 — new routes added, none replaced | No page removed |
| 12 | CI/Ops/Test Assets | Testing Strategy → Preservation gate | `jqwik` + surefire include retained |
| 13 | Single API Namespace | Architecture — `/api/v1`, port 8081; every new endpoint path in §1/§2/§3 is `/api/v1`-relative | No `/api/v2` |
| 14 | Legacy Fleet Compatibility | §1 client contract (`enabledFeatures` additive field); §3.2 additive enum values; P18 | 14.7 holds: gateway modes only appear on rows a legacy client did not originate, because legacy clients never send `verified_by='easebuzz'` |
| 15 | UI Scope Constraint | §3.5 (`EasebuzzPaymentScreen`), §4 (`MarketplaceOrdersScreen`), §5 (`NotificationReliabilityScreen`), §11 (`styles.css`) | All new screens use `KhanaBookTheme` tokens; dark-only preserved |
| 16 | Exclusion of v2 Debris | Not designed in — enforced by CI greps for `DbCheck`, `QuickDbCheck`, `dev-debug`, `dev-refresh`, `Storefront` | **Process constraint** |
| 17 | Push Notifications | §5 | In scope, Phase 4 |
| 18 | FSSAI/GST Compliance | §6 | In scope, Phase 5 |
| 19 | Marketplace Orders | §4; D12, D13 | In scope, Phase 6 |
| 20 | Easebuzz Gateway | §3; D1, D10, D11, D17 | Onboarding Phase 7, payments Phase 8 |
| 21 | WIRE Platform | *No design section by intent* | **DEFERRED** — excluded from V52; follow-up spec |
| 22 | Operational Infrastructure | §10; D15 | Phase 3, non-flagged |
| 23 | Refresh Token Rotation | *No design section by intent* | **DEFERRED** — 30-day JWT retained; follow-up spec |
| 24 | KYC Document Upload | §7 | In scope, Phase 7 |
| 25 | Fintech Admin Tranche | §11 — only `admin/sub-merchants` and `business/settings` | 17 pages **DEFERRED**; their migrations excluded from V48–V52 |
| 26 | Storefront Exclusion | Open Risk 4 — table retained, feature excluded | **DROP** + CI grep on `Storefront` |
| 27 | Secrets and Configuration | §1 `FeatureConfigGuard`; property keys throughout; Open Risk 6 | 27.5 scopes out `google-services.json` |
| 28 | Phased Delivery and Rollback | Flyway/Room allocation tables; §1 emergency-kill row | Rollback step 1 = `kill_switched = true`, one `UPDATE`, no schema change |
| 29 | Regression Evidence | Testing Strategy → per-phase suites | 29.2 satisfied by Phase 1 harness |
| 30 | Feature Flags | §1; D3, D4, D9 | Phase 2 |
| 31 | Invoice Template | §8 | Phase 9, dual renderer |
| 32 | Transactional Email | §9; D14 | Templates split Phase 7 / Phase 8 |
| 33 | Webhook Inbox | §2; D5, D6, D7, D8, D9, D17 | Phase 2 infrastructure, handlers per phase |

Three requirements are satisfied by process rather than by code and therefore need explicit task coverage rather than a component: **Req 1** (no v2 merge parent), **Req 16** (debris exclusion greps), and **Req 26**'s CI guard.

## Testing Strategy

### Preservation gate (Requirements 12, 29)

All 23 existing server test classes and the 6 jqwik property suites must pass at the end of every phase. `jqwik` and the surefire `**/*Properties.java` include stay in `pom.xml` (Requirement 12.5) — v2 removed both and that removal is in `Excluded_Debris_Set`.

### Phase 1 — preservation harness

At least one test per preservation requirement 4–12 (Requirement 29.2). Highest-value additions, because they cover behaviour currently asserted only indirectly:

- Room 62→62 no-op with seeded unsynced bills/items/payments — baseline for 3.6
- Legacy sync replay asserting Base_Branch response schema conformance — 14.8, property P18
- `RequireRoleAspect` active in context — 5.7
- Migration-list identity between `DatabaseProvider` and `DatabaseModule` — D16

### Phase 2 — flags and inbox

Flag resolution truth table over all six operational states including pilot; kill-switch dominance (P29); config-forces-disabled (30.30); flag-off equivalence against Baseline_Tag (P26); webhook capture under any flag state (P30); composite key across providers (P33); verification fail-closed (P35); handler dependency-set assertion (33.29); **concurrent-claim test** asserting two workers never claim one row; **lease-expiry test** asserting a simulated crash leaves the row reclaimable; **backoff test** asserting a failed head row blocks its own aggregate and no other (P34).

### Phase 8 — Easebuzz payments

Split conservation (P4); refund cap (P5); inbox drain equivalence (P31); `post_split` repeat application returns the existing row; and a targeted regression asserting every pending-online-bill query in `BillDao` recognises the three Easebuzz modes — the §3.5 risk.

## Correctness Properties

The 37 properties P1–P37 are stated in `requirements.md`. This section records how the design makes each testable and which decision it rests on.

### Property 26: Flag-off equivalence

**Validates: Requirements 30.16, 30.28**

Flags gate only new entry points; no Base_Branch code path calls `FeatureFlagService`. With every flag disabled the Base_Branch surface is identical to Baseline_Tag. Provable by dependency inspection plus the Phase 2 equivalence test.

### Property 27: Additive-migration compatibility

**Validates: Requirements 2.7, 2.15**

Every V48+ migration is `CREATE TABLE` / nullable-or-defaulted `ADD COLUMN` / `CREATE INDEX` only. The previous image starts against the migrated schema because `ddl-auto=validate` tolerates unmapped objects — the property the orphan V6/V7 tables already demonstrate in production today.

### Property 29: Global kill-switch dominance

**Validates: Requirements 30.8, 30.29**

Resolution step 3 (`kill_switched`) precedes step 4 (override lookup), so no override can re-enable a kill-switched flag. Separating `kill_switched` from `default_enabled` is what makes this hold while still permitting a single-restaurant pilot.

### Property 33: Composite inbox key

**Validates: Requirements 33.7, 33.8, 33.40**

`uq_webhook_inbox_provider_event UNIQUE (provider_identity, provider_event_id)`. Two providers may share an event id; one provider redelivering an id yields exactly one row.

### Property 34: Aggregate ordering and isolation

**Validates: Requirements 33.15, 33.16, 33.17, 33.42**

The claim query's `ORDER BY ordering_key ASC LIMIT 1` enforces intra-aggregate order; `next_attempt_at` on a failed head row blocks only that aggregate; other aggregates are claimed and drained concurrently by `inboxExecutor`.

### Property 35: Verification fail-closed

**Validates: Requirements 33.19, 33.20, 33.21, 33.41**

Handler ordering is credential check → HMAC verify → persist. No persistence path exists before verification, so an unverified request yields neither an inbox row nor a provider success response.

### Property 36: Single processing path

**Validates: Requirements 33.29, 33.30, 33.32**

The `Webhook_Endpoint` handler depends on `WebhookInboxService` only, never on a business service, and `handlerFor()` is the sole route to business state regardless of arrival-time flag state. Asserted by a dependency test.

### Property 37: Aggregate derivation totality

**Validates: Requirements 33.12, 33.13, 33.14**

`aggregateKey()` falls back to `unresolved:{provider}:{eventId}` — non-null, self-unique, and never the restaurant alone.

### Properties requiring generated input

| Property | Surface |
|---|---|
| P1, P2 | Existing `TerminalManagementPostgresConcurrencyTest` — unchanged, must keep passing |
| P3 | `BillingViewModel.computeSummary` is already pure `(items, profile) → BillSummary`, directly generator-addressable |
| P4, P5 | `PostSplitService`, `RefundService` (Phase 8) |
| P6 | Existing `GenericSyncCrossTenantTest` |
| P9 | Seeded 62→63 migration with unsynced rows |
| P11 | Device-token upsert |
| P14 | `(tracker_id, alert_window)` uniqueness |
| P16 | `sentToKot` semantics, unchanged |
| P18 | Captured Base_Branch field sets vs Integration_Codebase |
| P28 | Flag toggle sequences over written rows |
| P31 | Depends on D5/D7 — one code path for arrived-enabled and drained-disabled |

### Deferred

P7 and P32 both require the dual-renderer arrangement from Requirement 31.3, which lands in Phase 9. Neither is testable earlier; both gate that phase.

### Not property-tested

Per requirements: live provider APIs (sandbox example tests), Docker/Apache/deploy wiring (smoke checklist), SMTP (mock sender), structured log format (single captured-line assertion).

## Open Design Risks

1. **Phase 2's flag cache precedes Phase 3's `CacheManager`.** Resolved by D4 — Phase 2 owns a Caffeine instance directly. Cost is the dependency arriving one phase earlier than Requirement 22.1 implies.
2. **Concurrent handler execution is possible, not prevented.** D17 makes it safe rather than impossible: renewal and the bounded handler timeout make it rare, the fencing token stops a stale worker committing, and idempotent schema effects make the residual race harmless. The guarantee is effect-once under at-least-once execution. The startup assertion `handler-timeout < lease` is what keeps that guarantee true; a misconfiguration that inverts it degrades the design to unfenced concurrency, so it fails fast instead.
3. **`easebuzz_webhook_events`, `payments`, `payment_webhook_logs` stay as orphans.** Cannot be dropped (Requirement 2.8), not adopted (D2). They will read as dead schema; the V48 comment records why.
4. **`storefront_customer_orders` likewise stays** — Requirement 26 drops the feature but not the table.
5. **The §3.5 pending-online-bill query set is the sharpest edge in the plan.** Five separate `BillDao` queries filter `payment_mode IN ('upi','part_cash_upi','part_upi_pos')`. Missing one leaves an interrupted Easebuzz payment unrecoverable, with no compile error. Covered by a dedicated Phase 8 regression test.
6. **`Android/app/google-services.json` is tracked and currently modified in the working tree.** Requirement 27.5 scopes it out, but Phase 0 must confirm the pending modification is intended client config before Baseline_Tag.
