# KhanaBook Production Readiness & Reliability Audit

> **Date:** 2026-08-17
> **Scope:** Android POS (`Android/`), Spring Boot backend (`server/`), Angular web-admin (`web-admin/`), ops/deploy/CI
> **Method:** Static analysis of the full repository, trace-through of sync/payment/auth flows, incident log review (`docs/ROOT_CAUSE_LOG.md`), deploy/CI inspection. **No production code was modified.**
> **Evidence convention:** every finding cites `file:line`. **C** = confirmed by code reading, **S** = suspected, requires production log/metric evidence.

---

## 1. Executive Summary

**Why the production application generates many errors:** The errors are **not random and not isolated bugs — they are systemic**, and they concentrate in four subsystems: (1) the bidirectional sync engine, (2) the Easebuzz payment/webhook path, (3) the multi-device bill lifecycle, and (4) authentication under load. The application is architecturally ambitious (offline-first multi-device POS with server-side reconciliation) and the codebase carries a large amount of hardening already — but several **defeated safety mechanisms** create recurring, user-visible failures:

1. **The sync conflict "fallback" can fail open.** `GenericSyncService.saveAll` collision handling (server/src/main/java/com/khanabook/saas/sync/service/GenericSyncService.java:693-761) intends to break the infinite-409 push loop by falling back to per-record saves — but the whole push method runs in ONE transaction (`@Transactional`, :96). When `saveAll` hits a unique-constraint violation, **PostgreSQL marks the transaction aborted (25P02)**: every subsequent statement on that connection — including the per-record fallback saves AND the "idempotent recovery" query (GenericSyncService.java:735-748) — fails with `current transaction is aborted, commands ignored until end of transaction block`, and the eventual commit rolls back. The fallback therefore persists **nothing** (even the non-colliding records), the push returns 500, and the device retries the same batch every sync cycle. The code comment's claim that this "breaks the loop" is incorrect — it converts an infinite 409 loop into an infinite 500 loop. **This is the single most likely source of recurring `/sync/*` errors.** (C by PostgreSQL transaction semantics + code path; empirical proof = grep prod logs for `current transaction is aborted` or `25P02`.)
2. **Client pushes can clobber server/gateway-owned bill state.** `preserveServerOwnedState` (GenericSyncService.java:1053-1063) protects only user role/active and profile suspension. It does **not** protect `gatewayTxnId`, `gatewayStatus`, `settledAmount`, `paidAt` on `Bill` (Bill.java:116-126), and there is **no `@DynamicUpdate`** anywhere in the server codebase — every push rewrites all columns, so a NULL from a stale device overwrites the webhook-set gateway fields. The Android push DTO carries these fields (BillSyncDto: SyncRequestDtos.kt:93-94,44), and the code itself notes the v2-era guard `preserveGatewayOwnedBillState()` was dropped as dead code (GenericSyncService.java:509-511). (C)
3. **Push notifications are duplicated for cancellations (2×), not 4×.** Re-verification (2026-08-17) found `newBills.add`/`newPayments.add` each run ONCE (GenericSyncService.java:660,662) and the notification loops run once each (bills :768-786, cancelled :787-804, payments :808-829) — so new bills/payments produce exactly one FCM push. However, `cancelledBills.add(incomingBill)` fires TWICE with the same condition (GenericSyncService.java:507 and :528) → every order-cancellation push sends 2 "Order Cancelled" notifications. Additionally the payment-notification loop does a per-payment `billRepository.findById` (N+1, :811). (C — corrected from an earlier claim of 4× duplication.)
4. **The payment webhook retry engine is dead code.** `WebhookRetryService.enqueue` has zero callers (WebhookRetryService.java:41; WebhookRetryConfig.java:24-73). If a webhook processing step fails, the gateway is told `200` (EasebuzzWebhookService.java:47,103) and **nobody retries** — a paid bill can stay `pending`, or a refund can be silently dropped. (C)
5. **Money-path edge cases in the Easebuzz integration:** `createOrder` unconditionally clears the previous txnid (EasebuzzPaymentService.java:61-68) — if the customer already paid the previous txnid and the webhook is delayed, they can be charged twice (documented risk, line 57); refund webhooks look up the bill **by the txnid that createOrder just nulled** (EasebuzzWebhookService.java:165) → refund recorded at gateway but never applied to the bill. (C/S)
6. **Optimistic locking is disabled for sync** (GenericSyncService.java:535 sets the client's version to the server version). Conflict resolution is last-writer-wins by **client wall-clock timestamps** — clock skew between devices decides data loss. (C)
7. **Observability cannot support root-cause analysis:** prod logs are JSON but `restaurantId`/`userId` MDC keys are never populated (logback-spring.xml:6-8; RequestIdFilter sets only requestId), two conflicting structured-logging configs are active (logback Logstash vs `logging.structured.format.console=ecs`, application-prod.properties:10-11), there are **zero custom Micrometer metrics**, and no alerting configuration exists anywhere in the repo. An engineer at 3 AM cannot find "which restaurant, which device, which bill" for a burst of errors. (C)
8. **The web admin silently breaks:** ~55 API methods are no-op stubs returning `of(null)`/`of([])` (admin-api.service.ts:63-108, business-api.service.ts:205-213), network/5xx errors only reach `console.error` (jwt.interceptor.ts:42-44,58-60), and several pages load full collections without pagination (businesses, menu, staff, terminals, marketplace-orders) while daily-closing silently caps at 500 orders (daily-closing-page.component.ts:218-219). (C)
9. **Deployment has no safety net:** the live `deploy-production.sh` has none of the test→backup→rollback gates that the legacy `deploy.sh` enforces; the compose stack has no resource limits, no log rotation, and no scheduled backup mechanism (backup script exists, no cron). A bad Flyway migration = broken boot with no automated rollback. (C)
10. **Security/reliability gaps:** HTTP rate limiting covers only `/auth/**` and `/sync/**` (WebMvcConfig.java:36-40) — `create-order`, public invoice, and all webhooks are unthrottled; Swiggy/Zomato webhooks are accepted **unsigned** when no secret is configured ("onboarding mode", MarketplaceWebhookController.java:88-99); `.env` files with real secrets sit in the working tree.

**Is it isolated bugs or systemic?** Systemic. The same root patterns recur across features: client-timestamp-based conflict resolution, all-or-nothing batch writes without safe per-record isolation, gateway state not treated as server-owned, fire-and-forget notification/async side effects, and missing observability to detect any of it.

**Top production risks:** (1) bills stuck unsynced / infinite retry loops; (2) double-charge / missed refund on the money path; (3) silent clobbering of gateway state by sync; (4) duplicate notifications degrading trust; (5) unrecoverable production DB if a migration breaks (no auto-backup before deploy).

---

## 2. Architecture Map (Phase 1)

```
Android POS (offline-first)          Web Admin (Angular 18)            Marketplace (Zomato/Swiggy)
┌─────────────────────────────┐      ┌──────────────────────┐         ┌──────────────────────┐
│ Compose UI / ViewModels     │      │ signals + services   │         │ HMAC webhooks         │
│ CartManager / BillingViewModel│    │ jwt.interceptor      │         │ (optional secrets)    │
│ PaymentStateManager         │      └──────────┬───────────┘         └──────────┬───────────┘
│ PrintRouter / Bluetooth     │                 │ HTTPS/JWT                    │
│ KitchenPrintQueue           │                 ▼                             │
│ Room(SQLCipher) v66         │         ┌─────────────────┐                    │
│  bills/items/payments/menu  │◄───────►│ Apache :443     │◄───────────────────┘
│ SyncManager / MasterSync-   │         │ /api/v1 → :8081 │
│  Processor / WorkManager    │         └────────┬────────┘
│ MasterSyncWorker (15 min)   │                  │
└─────────────────────────────┘                  ▼
                                   ┌───────────────────────────┐
                                   │ Spring Boot 3.5.12 (prod) │  ← JwtRequestFilter, TerminalRequestFilter,
                                   │ controllers/service/sync  │     RateLimitingInterceptor (/auth,/sync only)
                                   ├───────────────────────────┤
                                   │ PostgreSQL 16 (compose)   │  ← Flyway V1..V74 (59 migrations, gaps)
                                   │ Easebuzz (txn/refund/payout/sub-merchant webhooks + wire API)
                                   │ Meta WhatsApp (OTP via API, 30s timeout)
                                   │ FCM push (PushNotificationService)
                                   │ CDN filesystem + cwebp
                                   └───────────────────────────┘
```

### Component responsibilities, dependencies, failure points

| Component | Responsibility | Key deps | Failure points |
|---|---|---|---|
| `SyncManager` (Android) | Orchestrate push+pull cycles, debounce 5s, NonCancellable cycles, conflict recovery + quarantine | Room, API, WorkManager | Push 5xx → no immediate retry; next window 15 min (periodic WorkManager) |
| `MasterSyncProcessor` (Android) | Batched push (50/batch), 409 bisection, per-bill conflict isolation, quarantine | Room DAOs, API | No HTTP retry/backoff on 5xx/429; one bad record can still abort a batch when not isolating |
| `GenericSyncService` (server) | Per-entity push validation, idempotent upsert, terminal ownership, saveAll fallback | PostgreSQL, JPA | Batch-save fallback broken in aborted transaction (S); client-time LWW; notification duplication (C) |
| `MasterSyncController` /pull | Paged master pull, terminal scoping | services, repos | **Read-write GET** (auto-enable side effects, :260-304); offset pagination races; 30s tx timeout |
| `EasebuzzPaymentService` | createOrder / verify / refund | Easebuzz API (10s conn / 30s read), ChargebackPrevention | txnid clearing (C); refund idempotency key is timestamp (C); dead reinitiateExistingOrder (C) |
| `EasebuzzWebhookService` | Payment/refund/payout/sub-merchant webhooks | Bill, FSSAI, payout repos, PostSplit async, FCM | Hash-mismatch → 200; no retry (C); refund lookup by txnid that may be nulled (C); payment idempotency = non-atomic status check (S) |
| `WebhookRetryService` | Retry engine w/ 6-attempt backoff | webhook_retry_jobs (V64) | **Orphaned — never enqueued** (C) |
| Auth (JwtRequestFilter, AuthServiceImpl, OTP) | JWT/refresh/OTP/PIN | Meta WhatsApp (OTP, 30s timeout), PG | Per-request user lookup (S perf); device binding warn-only (C); OTP is external-dep |
| Web-admin | Dashboard, orders, staff, menu, business settings | backend API | No refresh flow; stubs; full-collection loads; 5xx invisible (C) |
| Ops | Compose deploy, backups | VPS, Apache | No auto-backup, no limits, no rotation (C) |

---

## 3. Feature Inventory & Failure Analysis (Phase 2)

### F1 — Offline billing & settlement (core POS)
- **Entry:** NewBillScreen → BillingViewModel; **APIs:** `/sync/bills|items|payments/push|pull`, `/sync/master/pull`; **DB:** bills, bill_items, bill_payments; **local:** Room; **auth:** JWT + X-Terminal-Token.
- **Happy path:** draft persisted locally → payment captured → bill marked paid locally → push → pull → reconciliation.
- **Failure analysis (key rows):**

| Scenario | What happens today | What should happen | Risk | Handling |
|---|---|---|---|---|
| Push returns 5xx (e.g. aborted-tx batch) | Full sync fails; WorkManager retries in ≤15 min; bills stay `is_synced=0` | Retry with backoff; per-record isolation server-side | Bills invisible to other devices / web-admin for hours; logout blocked | P0 fix (see §15) + Android short retry |
| Push 409 after failed recovery | Bisection isolates record → quarantine table (`sync_quarantine_records`) | Quarantine + admin tooling | Data stuck in quarantine, no UI | Manual SQL reconciliation exists (`ops/sql/public_token_reconciliation.sql`); needs product UI |
| Two devices settle same bill | LWW by client clock; loser's push rejected if older | Terminal ownership already enforced; last-writer must be explicit | Silent overwrite of payments | Preserve gateway/paid state; compare payment sets (exists) |
| Device clock skew | Older device's `updatedAt` wins or loses arbitrarily | Server-issued versions/clock | Data loss on the money path | Use server timestamps for conflict arbitration |
| App killed mid-payment (Easebuzz) | txnid/URL captured in `PaymentReady`; status re-checked on restore (BillingViewModel payment-recovery path) | Same | Low — well handled | Keep |
| Logout with unsynced bills | Blocked (LogoutViewModel.kt:116), hard-logout path guarded | Same | Low | Keep; add force-logout audit event |

### F2 — Multi-device sync (bills/menu/staff/profiles)
- Confirmed strong: payload caps (200 push / 500 per service / 5MB Tomcat), field validation (`SyncPayloadValidator`), terminal ownership, cross-tenant guards, idempotent publicToken/operation-id/gateway-txn upserts, Android 50-page cap, per-page streaming pull, checkpoint-after-complete.
- **Weak spots:** client-clock LWW (C); optimistic lock disabled for sync (C); no retry/backoff for 5xx (C); pull is read-write (C); cancelled-bill notification double-add (C); per-payment N+1 in notification loop (C).

### F3 — Payments (Easebuzz)
Covered in §1 and §5. Money-path scenarios:
- **Webhook delayed + customer retries → double charge** (createOrder clears old txnid, EasebuzzPaymentService.java:61-68). Mitigation is only "old link expires in 15 min". A paid txnid1 webhook then marks the bill paid; txnid2's webhook is skipped (already paid) → overcharge with no auto-refund.
- **Refund webhook misses the bill** when `gateway_txn_id` was nulled by a sync push or createOrder → refund never lands on the bill (EasebuzzWebhookService.java:165).
- **verify/status "No gateway transaction found"** for a webhook-paid bill after a device push nulled the txnid.

### F4 — Authentication (OTP/JWT/refresh/PIN)
- OTP: 6-digit, bcrypt at rest, 10-min TTL, 60s cooldown, 5-verify limit, one-time-use, per-phone + per-IP limits, WhatsApp Meta 30s timeout with OTP deletion on delivery failure. Solid.
- JWT 15 min + rotating refresh (SHA-256 hashed) + revocation (DB+cache) + blocklist purge. Solid.
- PIN login with per-restaurant throttling + constant-time dummy hash. Solid.
- **Gaps:** no web-admin refresh flow (15-min logout cycles for admins); device-binding warn-only; per-request `findByAnyIdentifier` DB lookup on every authenticated request (S perf at scale).

### F5 — Web admin operations
- Order pagination is server-side (good). Everything else: full-collection client-side slicing; 500-order cap in daily closing; 30s polling with 100-order cap on active orders; stubs returning empty; errors invisible on 5xx/network.

### F6 — Printing (KOT/thermal)
- Well-engineered: two-phase locking, attempt caps, queue with UNASSIGNED fallback, KOT device-ownership guard (PrintRouter.kt:138-154), kitchen queue retry with `incrementAttempts`. Residual: KOT duplicate-print guard relies on `publicToken + kotRevision` — hardware-sensitive, per README needs real-device validation.

### F7 — Marketplace (Zomato/Swiggy)
- Unsigned webhook acceptance in onboarding mode (MarketplaceWebhookController.java:88-99); signature checked against **all** restaurant secrets, not the target restaurant's (:104-118). Verified events are deduped by `(platform, platform_order_id)` unique index. (C)

### F8 — Menu extraction (OCR)
- Async `@Async` worker with tenant/MDC propagation; polled job status. MenuExtractionWorker catches broad exceptions. Medium risk (file storage + ML Kit) but lower blast radius.

---

## 4. Production Error Analysis (Phase 3)

**Available evidence:** No raw prod logs in repo (container stdout, AGENTS.md:286-289). Incident log `docs/ROOT_CAUSE_LOG.md` (3 incidents), `docs/PLAN.md`, `docs/billing-sync-fix-verification.md`. The `errorId` mechanism exists (GlobalExceptionHandler.java:179-185) and is greppable per AGENTS.md.

### Incident history (documented)
1. **Completed bills reverting to draft after logout/login** — root cause: fire-and-forget `triggerImmediateSync()` racing logout; fixed by blocking logout on unsynced terminal bills. **Class of problem: fire-and-forget sync + local state reuse.**
2. **Pay-before/after flow instability** — draft/completion sync not triggered immediately; fixed by sync-after-every-terminal-event rule. **Class: state changes not durable before navigation.**
3. **Multi-device invoice collision + sync quarantine** — two devices allocated same invoice numbers offline; V22 uniqueness conditional; containment = one invoice device per restaurant; fix = terminal invoice series + publicToken canonical identity. **Class: human-readable counter used as identity + all-or-nothing batch save that left clients in 409 loops.**

### Error categories (from code analysis)
1. **Programming bugs:** cancelled-notification double-add (C); dead `reinitiateExistingOrder` (C); orphaned retry engine (C); healthcheck path mismatch (C).
2. **State-management:** client-clock LWW (C); gateway fields not server-owned (C); stale `refundAmount` on device until pull (C, mitigated by pull discipline).
3. **Database:** aborted-transaction fallback (C by PG semantics — prod log grep for `25P02`/`current transaction is aborted` still recommended); migration gaps V46/49/50/55/56 (Android schemas) & V46→V73/74 renumber (server) raise migration risk (C).
4. **Network:** no Android retry/backoff on 5xx (C); sync relies on 15-min periodic fallback (C).
5. **External-service:** Easebuzz API 10s/30s timeouts (C) — OK; WhatsApp 30s (C) — OK; **no circuit breaker / retry for either** (C).
6. **Observability:** MDC keys not populated (C); no metrics (C); no alerting (C).
7. **Expected errors treated as exceptions:** `IllegalArgumentException` → 400 with raw message (GlobalExceptionHandler.java:39-50) — some messages leak internal details (e.g., "Push payload exceeds maximum size", entity names). Low risk but inconsistent.
8. **Unclassified:** every non-mapped exception → 500 + errorId; **no error-id correlation on the Android side** — the user sees a generic toast; device-side context is not captured alongside errorId (S: no log shipping from devices).

**Per-error severity and blast radius** are captured in the Phase 13 matrix.

---

## 5. State Machine & Business Logic Audit (Phase 4)

### Bill lifecycle
`draft(pending)` → `completed(paid)` | `cancelled(failed)`; server webhook path: `pending → paid` directly.

| Transition | Idempotent? | Atomic? | Retry-safe? | Concurrency-safe? | Verdict |
|---|---|---|---|---|---|
| createDraft | Yes (operation id + existing-pending check, BillingViewModel.kt:446-460) | Yes (Room tx) | Yes | Partial — two devices can create two drafts for same order (no server-side draft dedupe beyond publicToken) | OK |
| finalize/settle (add payments, mark paid) | Yes — operation_id + gateway_txn dedupe on server; conditional DAO updates locally | Yes | Yes | **LWW by client clock on bill row** (C); payment set validated by `PaymentSetValidator` + server `isExactPaymentMatch` | At risk (P0) |
| Webhook paid | `paid` status check (EasebuzzWebhookService.java:63-66) — **not atomic** (S: two concurrent webhooks both pass) | Yes (per-request tx) | No retry (200 ack) | Duplicate gateway events possible (low impact) | At risk |
| Cancel/refund | Refund `merchantRefundId` = `REF{billId}_{timestamp}` (C — retry double-submits); refund webhook idempotent on `refunded` status | Yes | No | Refund lookup by txnid can miss after txnid nulled (C) | At risk (P0 money) |
| Sync push update | Yes (publicToken/updatedAt) | Yes per-batch | Batch fallback broken (S) | LWW | At risk (P0) |

### Systemic patterns
- **Non-idempotent:** refund initiation (timestamp key), cancellation notifications (double-add), Easebuzz createOrder (fresh txnid per attempt — by design but enables double-charge).
- **Non-atomic:** webhook paid-check-then-write; saveAll fallback within an aborted transaction; notification side effects outside the data transaction (acceptable for notifications, not for money).
- **Concurrency-unsafe:** any two writers on the same bill row (no `@Version` enforcement in sync; server blindly adopts client `updatedAt`).

---

## 6. API Reliability Audit (Phase 5)

| API | Validation | Timeout/Retry | Idempotency | Notes |
|---|---|---|---|---|
| `POST /sync/*/push` | `SyncPushGuard` 200 + `SyncPayloadValidator` per record + 500 cap + 5MB | None client-side (C); tx timeout 30s default | publicToken/opId/txnId (C) | **Batch-fallback fails in aborted tx** (C by semantics); cancelled-notification double-add (C) |
| `GET /sync/master/pull` | size clamp 1-500 (C), page unbounded (S: negative page → 400 only) | tx timeout 30s (C) | n/a | **GET with write side effects** (C); offset-pagination drift (S, low); admin impersonation logged (C) |
| `POST /payments/easebuzz/create-order` | bill paid-check, fraud scoring (C) | Easebuzz 10s/30s (C), no retry | **Not idempotent** (new txnid each call; double-charge window) (C) | Unthrottled at HTTP layer (C) |
| `POST /payments/easebuzz/webhook` | SHA-512 hash constant-time (C) | 200 on mismatch → gateway stops retrying (C) | status-based skip (C, non-atomic S) | **No retry engine wired** (C) |
| `POST /webhooks/{swiggy,zomato}` | HMAC when secrets configured; **unsigned accepted in onboarding mode** (C) | none | `(platform, order_id)` unique (C) | Cross-tenant secret check (C) |
| `GET /public/invoice/...` | constant-time token compare, 404 indistinguishability (C) | none | n/a | No rate limit at HTTP layer (C) |
| `GET /business/*` (web-admin) | role-gated | none | — | Full-collection responses; 500-order cap client-side (C) |

**Cross-cutting:** no API-level timeouts for slow DB paths beyond 30s tx; no response-size caps beyond Tomcat; no retry on 429/5xx anywhere; **rate limiting only on `/auth/**` + `/sync/**`** (WebMvcConfig.java:36-40).

---

## 7. Database Audit (Phase 6)

- **Schema:** 59 Flyway migrations (V1–V74 with deleted V46 renumbered to V73/74). `out-of-order=false`, `baseline-on-migrate`, `ddl-auto=validate`. Migration gaps and the V46 delete/renumber make historical reconstruction hard (C).
- **Uniques (good):** bills `(restaurant_id, device_id, local_id)`, `public_token`, `(restaurant_id, terminal_series, ...)` active-invoice series; payments `gateway_txn_id` global unique + `(restaurant_id, operation_id)` partial; users login/email/whatsapp CI-unique; webhook inbox `(provider_identity, provider_event_id)`; marketplace `(platform, platform_order_id)`.
- **Missing/weak:**
  - No DB-level guard preventing a client push from nulling `gateway_txn_id` (would need server-owned-state handling, not a constraint).
  - `updated_at` is client wall-clock — no server-authoritative `updated_at` column (S: skew corrupts LWW arbitration).
  - `webhook_retry_jobs` table (V64) has no producers (C).
  - No FK on `bill_payments.bill_id`? (not verified; FKs exist per V3 tenant-scoped FKs).
- **Query/transaction risks:**
  - **Aborted-transaction fallback (S)** — see §4-1.
  - Per-request `findByAnyIdentifier` OR-query (S perf, indexed columns but OR-plan uncertain).
  - Master pull = 9+ queries in one 30s read-only tx (bills + items + payments + terminal updates + stock) — at 500-row pages OK, but items/payments for pulled bill IDs can spike on first sync (C).
  - No `@QueryHints` timeout on pull queries beyond tx timeout (C).
- **Crash-analysis:**
  - **Crash halfway through push:** whole batch rolls back (single `@Transactional`); device retries; safe except for the aborted-tx fallback issue.
  - **Request repeated:** idempotency by publicToken/opId/txnId — covered.
  - **Two devices simultaneously:** LWW by client clock — data loss risk on the losing side for bill-level fields; child ownership guards prevent cross-terminal writes (C).

---

## 8. Performance Audit (Phase 7)

### Android
- Billing path: Room ops on IO dispatchers; Compose collectAsStateWithLifecycle — no obvious main-thread DB. (C)
- **WAL not enabled** for Room+SQLCipher (C) — single-writer serialization; POS writes are bursty during billing; medium.
- Sync: per-page persistence prevents OOM (C); 50-page cap = 25k records/cycle (C).
- Bluetooth: two-phase locks, attempt caps — OK. **Foreground service** for connected device (C).
- No excessive polling found in Android (WorkManager 15-min periodic + event-triggered).

### Backend
- Per-request JWT DB lookup (S); per-push notification N+1: `billRepository.findById` per payment notification (GenericSyncService.java:816-818) — up to 200 queries in a payment push (C). 
- Master pull: `findUpdatedForTerminal` + `findByRestaurantIdAndServerBillIdIn` — bounded.
- No metrics to quantify; **no load-test evidence anywhere in repo** (S).

### Infrastructure
- Tomcat 200 threads / 10k connections (C); Hikari 20/5 with 60s leak detection (C); JVM 75% MaxRAMPercentage of **unbounded container** (C) — a memory spike threatens the whole VPS.
- No `mem_limit`/`cpus`/`pids_limit`; no log rotation; single instance, no autoscaling; PostgreSQL 16 no tuning/limits visible (C).
- **Behavior as traffic grows:** per-request user lookup + OR-query degrades; sync pulls amplify with tenant count; notifications double-fire; rate limiter bucket cache capped at 1000 IPs (RateLimitingInterceptor) → beyond that, no per-IP limiting (S).

---

## 9. Reliability & Failure Recovery (Phase 8)

**What happens on failure at each point (critical features):**

| Failure | Sync (bills) | Payment | Auth |
|---|---|---|---|
| Network down | Offline-first; retries on connectivity (WorkManager NetworkType.CONNECTED) | Local capture; Easebuzz requires online | No login; refresh rotates |
| Server crash mid-push | `@Transactional` rollback; retry next cycle | txnid persisted on bill; app re-verifies | Blocklist survives in DB |
| DB crash | 500 + errorId; retry later | Webhook 500? No — 200 + event dropped unless it throws | 500 |
| External (Easebuzz/WhatsApp) outage | n/a | create-order 502 (GlobalExceptionHandler maps EasebuzzApiException→502); **webhook processed in-line — a DB/Easebuzz hiccup inside handler = 500 → but hash-mismatch/processed paths return 200 with no retry** | OTP send fails → OTP deleted, user retries |
| Duplicate request | idempotent upserts (C) | txnid double-charge window (C) | OTP one-time (C) |
| Partial success (multi-record push) | per-record failedLocalIds + quarantine (C) | part payments by opId (C) | n/a |

**Recovery mechanisms present:** exponential backoff in WorkManager sync (30s base), conflict-recovery pull + quarantine, blocklist purge jobs, OTP cleanup, token-blocklist hourly purge. **Missing:** retry/backoff for sync 5xx (Android), webhook retry (dead engine), dead-letter visibility for quarantine (no UI), reconciliation jobs (only manual SQL), circuit breaker for Easebuzz, no idempotency key for refunds.

**Where retries are safe vs dangerous:** retries are safe for reads and for idempotent writes (publicToken/opId/txnId push paths). Retries are **dangerous** for `create-order` (fresh txnid → double-charge) and refund initiation (timestamp key). Do not add blind retries to either.

---

## 10. Error Handling Audit (Phase 9)

**Current state:** single `@ControllerAdvice` (GlobalExceptionHandler.java) maps well-known exceptions; everything else → 500 + 8-char errorId + stack trace at ERROR. Android `UserMessageSanitizer` + `BackendErrorParser` give user-safe messages. Web-admin is the weak link (console-only for 5xx/network).

**Design the handlers should follow:**

```
User Input Error  (400, field map, no logging noise)
    ↓
Validation        (422 BusinessRuleException, safe message)
    ↓
Business Rule     (409/403/422 with audit trail)
    ↓
Application Error (500 + errorId + stack trace + requestId MDC + restaurantId/userId MDC)
    ↓
Infrastructure    (503/504, no stack dump to client, alert)
    ↓
External Dependency (502 Easebuzz / 504 timeout, retry-safe wrapper)
```

**Concrete defects:**
- `IllegalArgumentException` handler returns the raw message (GlobalExceptionHandler.java:42-49) — can leak internals (S).
- No 503/504 distinction; timeouts surface as generic 500 (S).
- Swallowed exceptions with real consequences: `MarketplaceWebhookController.java:206` (tax error → silent zero), `EasebuzzPaymentService.java:132-134` (sub-merchant lookup failure → parent fallback masks config errors), `EasebuzzWebhookService.java:263` (refund amount parse → null).
- Notifications failures are warn-only (acceptable) but the duplication bug amplifies cost (C).

---

## 11. Observability Audit (Phase 10)

**What exists:** requestId filter + MDC (RequestIdFilter.java:29); timing logs per request; errorId; actuator health/prometheus/info (prod exposure health,prometheus,info); JSON logs (Logstash encoder).

**What's broken/missing:**
- MDC `restaurantId`/`userId` never set (C) — cannot filter logs by tenant.
- Conflicting structured configs (C).
- Zero custom metrics (no counters for sync failures, 409s, webhook drops, OTP failures) (C).
- No alerting config; no dashboards; no error-rate tracking (C).
- Android: no crash reporting configured (no Crashlytics/Sentry reference found), no device-side log shipping (S).
- Health check path mismatch (C).
- **"Can an engineer diagnose at 3 AM?"** — Mostly no: they'd find `Unhandled exception [errorId=xxxx]` but not which restaurant/device/bill, and the client that triggered it is unreachable.

---

## 12. Security & Reliability Intersection (Phase 11)

| Issue | Status | Reliability impact |
|---|---|---|
| Rate limits only on /auth + /sync | C | create-order/invoice/webhooks abusable → resource exhaustion |
| Unsigned marketplace webhooks (onboarding mode) | C | Fake order injection; also real-order loss if secret mismatch |
| `.env`/`.env.v2` with secrets in working tree | C | Credential leak risk; guards.yml only protects new commits |
| Device binding warn-only | C | Stolen JWT usable from any device (requires valid JWT first) |
| `X-Forwarded-For` trusted for IP-based limits (first hop) | C | Rate-limit bypass via spoofed header behind Apache unless proxy strips it (S) |
| Admin IP allowlist | C | OK; cached |
| Invoice endpoint token 404-indistinguishability + constant-time | C | Good |
| HTML escaping in invoice + https allowlist for links | C (esc yes; scheme allowlist S) | XSS |
| CSP `script-src 'unsafe-inline'` | C | Client-side risk on web-admin |

---

## 13. Testing Gap Analysis (Phase 12)

**What exists:** server ~50 test classes (sync idempotency, terminal isolation, strict mode, payment/refund, Easebuzz webhook, migration smoke, concurrency tests via Testcontainers, run in CI). Android 35 unit-test files (billing logic, sync conflict isolation, print, session). Web-admin: property tests only (4 spec files), CI runs build only.

**Gap matrix (by production risk):**

| Risk | Covered? | Gap |
|---|---|---|
| Batch-fallback after constraint violation (aborted tx) | ❌ | No test asserts per-record fallback works inside same tx — needs Testcontainers test reproducing the 409→fallback path |
| Gateway state preserved against client push | ❌ | No test for "webhook paid → client push → gatewayTxnId retained" |
| Notification duplication | ❌ | No assertion on notification count |
| createOrder double-charge window | ❌ | No test simulating delayed webhook + second createOrder |
| Refund webhook after txnid cleared | ❌ | No test |
| Webhook retry engine | ❌ | No test for enqueue path (dead code) |
| Rate limiting coverage gaps | ❌ | No test for create-order/invoice throttling |
| Android 5xx retry/backoff | ❌ | No test |
| Migration rollback safety | Partial | Migration smoke exists; no "broken migration" drill |
| Web-admin error surfacing | ❌ | No tests for interceptor behavior on 5xx/network |
| Android crash reporting / log shipping | ❌ | None |

---

## 14. Production Risk Matrix (Phase 13)

| # | Issue | Feature | Root cause | Sev | Prob | Impact | Blast radius | Current handling | Recommended fix | Pri |
|---|---|---|---|---|---|---|---|---|---|---|
| R1 | Batch-save fallback fails inside aborted PG transaction → repeated 500s on /sync/push | Sync | Per-record fallback + idempotency query run in same tx as failed saveAll; PG 25P02 aborts tx | **P0** | Certain | Bills stuck unsynced, infinite sync loop, user-visible errors | All restaurants pushing colliding bills | 500 + errorId; Android retries later | Per-record REQUIRES_NEW (or savepoints) so the fallback commits; add integration test | **P0** |
| R2 | Client push nulls `gateway_txn_id`/`gateway_status` after webhook paid | Sync × Payments | `preserveServerOwnedState` omits gateway fields; no @DynamicUpdate | **P0** | High | Refund/status breakage on paid bills; money-path confusion | All Easebuzz-paid bills edited on device | None | Preserve gateway fields server-side (like refundAmount) | **P0** |
| R3 | Duplicate FCM cancellation notifications (2×); N+1 lookup per payment | Sync/notifications | `cancelledBills.add` runs twice (:507,:528) | **P1** | Certain | Duplicate "Order Cancelled" pushes, latency in payment loop | Restaurants cancelling bills | None | Dedupe cancelledBills; batch-fetch bills for payment loop; add notification-count test | **P1** |
| R4 | Webhook retry engine dead; failures acknowledged 200 | Payments | `enqueue` never called | **P1** | Med | Paid/refunded states missed silently | All gateway events | 200 ack, no retry | Wire enqueue on processing failure; 4xx/5xx for unprocessable | **P1** |
| R5 | createOrder clears previous txnid → double-charge window | Payments | Design with only 15-min link expiry | **P1** | Med | Customer charged twice | Easebuzz billers | None | Track attempt state; query gateway status for previous txnid before re-init; refund auto-reconcile | **P1** |
| R6 | Refund webhook lookup by nulled txnid | Payments | R2 + createOrder clearing | **P1** | Med | Refund recorded at gateway, bill still paid | Refunded bills | None | Lookup by udf1 billId; store all txnids per bill | **P1** |
| R7 | Observability: no tenant/device MDC, no metrics, no alerts | Ops | Config/logging gaps | **P1** | Certain | Cannot diagnose prod incidents | All | errorId only | Populate MDC; add counters; alerting | **P1** |
| R8 | Deploy without backup/rollback gates | Ops | deploy-production.sh minimal | **P1** | Med | Migration breaks boot, no rollback | Whole system | Manual backup script | Add backup-before-deploy + rollback to live script | **P1** |
| R9 | No resource limits/log rotation in compose | Infra | Compose config | **P2** | Med | OOM/disk exhaustion | Whole system | restart: unless-stopped | mem/cpu limits, logging rotation | **P2** |
| R10 | Web-admin full-collection loads + stubs + invisible 5xx | Web-admin | API design + dead stubs | **P2** | High | Broken admin views, missed errors | Platform admins | console.error | Server-side pagination; remove stubs; interceptor toast | **P2** |
| R11 | No Android retry/backoff on 5xx sync | Sync | Client design | **P2** | High | 15-min freshness delays | All devices | WorkManager periodic | Exponential backoff within cycle | **P2** |
| R12 | Unsigned marketplace webhooks | Security | Onboarding mode | **P2** | Low | Fake orders | Restaurants w/o secrets | Accept-all | Require secret or HMAC at all times | **P2** |
| R13 | Rate limiting gaps (create-order, invoice, webhooks) | Security | Interceptor scope | **P2** | Low | Abuse/resource exhaustion | Public endpoints | None | Extend interceptor or per-endpoint limits | **P2** |
| R14 | Refund idempotency key = timestamp | Payments | Design | **P2** | Low | Double refund on retry | Refund API | None | UUID idempotency key + DB unique | **P2** |
| R15 | `IllegalArgumentException` leaks raw message | Error handling | Handler design | **P3** | Med | Info leak | All | raw 400 | Sanitize; keep field errors | **P3** |
| R16 | Web-admin no refresh flow | Auth UX | Design | **P3** | Med | 15-min logouts | Admins | kick to login | Refresh rotation in web-admin | **P3** |
| R17 | Healthcheck path mismatch v1 vs v2 | Ops | Config drift | **P3** | Certain | False health state | Ops | — | Align compose + Dockerfile | **P3** |
| R18 | Pull GET with write side-effects | Sync | Auto-enable design | **P3** | Low | Surprise mutations on GET | Tenants | warn logs | Move to activation endpoint | **P3** |

---

## 15. Feature Risk Matrix (Phase 14)

| Feature | Reliability | Performance | Data | Security | Scalability | Main failure mode |
|---|---|---|---|---|---|---|
| Offline billing/settlement | Critical | Med | Critical | Low | Med | LWW clobber / stuck unsynced |
| Multi-device sync | Critical | Med | High | Med | Med | Batch 500s, quarantine backlog |
| Easebuzz payments | Critical | Low | **Critical** | High | Low | Double-charge / missed refund |
| Auth (OTP/JWT/PIN) | High | Med | Low | High | Med | External OTP dependency, per-req DB lookup |
| Marketplace | Med | Low | Med | High | Low | Unsigned webhooks |
| Web-admin | High | **Critical** | Med | Med | **Critical** | Full loads, silent errors, 500 cap |
| Printing | High | Low | Low | Low | Low | KOT duplicate/missed prints |
| Menu/OCR | Med | Med | Med | Med | Low | Job failures silent |
| Infra/deploy | High | High | **Critical** | Med | Critical | No rollback, no limits |

---

## 16. Root Cause Analysis (Phase 15)

**Top systemic root causes (recurring across ≥2 features):**
1. **Client-authoritative conflict resolution.** Everywhere the client's `updatedAt`/`version` decides truth (sync LWW, refund keys, txnid lifecycle) — combined with no server-authoritative timestamp, this is the root of R1/R2/R5/R6/R14.
2. **Gateway/external state treated as ordinary synced data.** Easebuzz state lives on the `bills` row that any device push can overwrite (R2/R6), and webhook failures are acknowledged without durable retry (R4).
3. **All-or-nothing batch writes with inadequate per-record isolation.** saveAll fallback (R1), duplicate quarantine semantics from incident #3.
4. **Side effects outside transactions, unguarded and duplicated.** Notifications (R3), auto-enable writes in GET (R18), async post-split.
5. **Observability absent at the exact layer where failures occur** (tenant/device/bill context, metrics, alerts) — making every incident slow to diagnose.
6. **Deployment hardening regressed in the compose migration** (backup/rollback gates lost; limits/rotation never added).
7. **Web-admin API surface outran its implementation** (~55 stubs) — silent emptiness instead of errors.

---

## 17. Improvement Recommendations (Phase 16)

### Immediate (0–2 days) — P0/P1 code
1. **Server: preserve gateway-owned bill fields on sync.** Extend `preserveServerOwnedState` (GenericSyncService.java:1123) to restore `gatewayTxnId`, `gatewayStatus`, `settledAmount`, `paidAt` from the existing row, mirroring the refundAmount handling (:523-530). *Why:* stops the refund/status breakage. *Verify:* integration test "webhook paid → device push → gateway fields retained".
2. **Server: fix saveAll fallback.** Replace in-transaction per-record fallback with `REQUIRES_NEW` per-record saves (or flush-per-record with `@Transactional(propagation=REQUIRES_NEW)`), so one collision cannot abort the batch. *Verify:* Testcontainers test with two colliding records + non-colliding records → non-colliding succeed, colliding land in failedLocalIds.
3. **Server: dedupe notifications.** Remove the double append (659-668) and the second loop (841-904). *Verify:* assert pushNotificationService called exactly once per bill/payment.
4. **Server: wire the webhook retry engine.** Call `WebhookRetryService.enqueue` on any processing exception in `EasebuzzWebhookService` handlers; return non-200 for processing errors so the gateway may also retry (or rely on the engine + a reconciliation job). *Verify:* unit test that a thrown exception enqueues.
5. **Android: short retry with backoff** for sync 5xx/429 (2,4,8,16s, cap ~5) before giving up to WorkManager. *Verify:* mock retrofit 500s, assert ≤5 retries and eventual hand-off.

### Short-term (1–2 weeks)
6. **Observability:** populate MDC `restaurantId`/`userId` (JwtRequestFilter/TenantContext); remove the dead ECS property; add Micrometer counters (sync_errors, push_409, webhook_dropped, notification_sent, create_order, refund_requested) + a simple alert rule set; align compose/Dockerfile healthcheck paths.
7. **Money-path hardening:** refund idempotency key (UUID) + unique DB constraint; store all txnids per bill (new table or JSON column) so refund/status lookups never miss; before re-creating an order, poll gateway status of the previous txnid when the old link is <15 min old (only when unverifiable, fall back to current behavior).
8. **Deploy gates:** add mandatory `ops/backup_postgres.sh` before `up -d server` and a post-deploy health+smoke check to `deploy-production.sh`; add cron for daily backups (repo-owned systemd timer).
9. **Compose hardening:** `mem_limit`/`cpus` for server+postgres, log rotation (`logging: options: max-size/max-file`).
10. **Web-admin:** remove or implement the ~55 stubs; server-side pagination for businesses/menu/staff/terminals/marketplace-orders; raise daily-closing beyond 500 or paginate; surface 5xx/network errors via ToastService in the interceptor.

### Medium-term (1–2 months)
11. **Server-authoritative conflict arbitration:** add a server `updated_at` that the server always stamps on accept (it already stamps `serverUpdatedAt`); use `(serverUpdatedAt, updatedAt)` for LWW arbitration instead of client clock alone. Phase out accepting client timestamps as truth.
12. **Payment event sourcing:** move gateway state to dedicated `bill_payment_attempts`/events (txnid lifecycle, statuses) instead of single-row overwrite — eliminates R5/R6 class permanently.
13. **Quarantine UI + reconciliation job:** periodic job that re-attempts quarantined sync records; admin view.
14. **Refresh-token flow for web-admin** (reuse existing server refresh API).
15. **Load test:** gatling/k6 scenario for sync push (200-record bills), master pull first-sync (25k records), concurrent webhooks; establish latency budgets.

### Long-term
16. Split `notifications` and `post-split` side effects onto a durable queue (Postgres-backed outbox pattern) instead of in-request `@Async` — only if metrics show loss.
17. Re-evaluate the "one push endpoint per entity" design toward a single ordered sync protocol with explicit server-conflict response codes if scale demands.

---

## 18. Performance Optimization Plan (Phase 17)

| Current bottleneck | Evidence | Root cause | Optimization | Expected impact | Measurement |
|---|---|---|---|---|---|
| Per-request user DB lookup | JwtRequestFilter.java:95 `findByAnyIdentifier` | No cache | 5-min Caffeine cache keyed by username with invalidation on role/tokenInvalidated changes | 1 query/req → ~0 | Metrics: filter avg latency; `EXPLAIN` on OR-query |
| Payment-push notification N+1 | GenericSyncService.java:816-818 per-payment `findById` | Loop design | Batch-load bills for the push once | 200 → 1 query | Query log / Hibernate stats (dev) |
| First-sync pull size | MasterSyncController pages; items/payments by bill IDs | Design | Keyset pagination `(updated_at, id)`; stream items per bill page | Smoother 25k-record pulls | Load test p99 |
| Duplicate cancellation FCM | R3 | Bug | Fix R3 | 2× → 1× cancellation pushes | FCM send counter |
| No WAL on Room | Android (grep: no journal_mode) | Config | Enable WAL pragma after backup-safe migration | Less UI blocking under burst writes | Instrumented test |

**Do NOT add:** caching of master pull data, microservices, message queues — none are justified by evidence yet.

---

## 19. Production Hardening Checklist (Phase 18)

- [ ] Error handling: sanitize `IllegalArgumentException` 400s; distinguish 503/504; keep errorId
- [ ] Validation: payload caps verified for ALL push endpoints incl. arrays nested in body
- [ ] Transactions: per-record REQUIRES_NEW fallback; verify aborted-tx behavior
- [ ] Idempotency: refund keys, create-order attempt tracking, webhook event dedupe
- [ ] Concurrency: server-authoritative timestamps; `@Version` honored on non-sync writes
- [ ] Retry: Android backoff ≤5; webhook engine wired; NO blind retries on create-order/refund
- [ ] Timeout: 30s tx timeout on pull (exists); add query hints; Easebuzz 10/30s (exists)
- [ ] Logging: MDC restaurantId/userId; remove ECS duplicate; log quarantine events
- [ ] Monitoring: Micrometer counters + gauges; errorId rate alerting
- [ ] Alerting: webhook drop rate, sync 409/500 rate, DB connections, disk, backup freshness
- [ ] Database: pre-deploy backup (mandatory), daily cron backup, restore drill, retention
- [ ] API: rate limits on create-order/invoice/webhooks; server-side pagination on admin lists
- [ ] Security: require marketplace webhook secrets; remove `.env` from working tree; CSP report-only trial
- [ ] Performance: load test before multi-device rollout
- [ ] Testing: gaps in §12; add failure-path integration tests
- [ ] Deployment: rollback procedure (JAR + schema), healthcheck alignment, `--check` on flyway
- [ ] Backup/DR: test restore monthly; document RPO/RTO

---

## 20. Verification Strategy

- **R1/R2 (server sync):** Testcontainers integration tests; then watch prod `errorId` rate for `/sync/*` before/after; confirm zero "current transaction is aborted" strings.
- **R3 (notifications):** unit test asserting one cancellation push per record (no double-add); assert exactly one push per new bill/payment; batch-fetch assert in payment loop.
- **R4 (webhook retry):** simulate processing failure in sandbox; assert `webhook_retry_jobs` rows appear and backoff timeline.
- **R5/R6 (payments):** sandbox Easebuzz: pay txnid1, wait for webhook, call createOrder again → assert no new txnid for paid bill; assert refund webhook applies with a cleared txnid.
- **R7 (observability):** deploy, `docker compose logs` shows `restaurantId`/`userId` in JSON; Prometheus has new counters; alert fires on injected error.
- **R8 (deploy):** run `deploy-production.sh` on staging; verify backup file created and health returns UP; simulate broken migration and verify rollback path.
- **Regression:** `mvn test`, `gradlew testDebugUnitTest`, `ng build` + `tsc --noEmit` (add to CI), plus manual two-device flow per `docs/PLAN.md` gates.

---

## 21. Final Answer

> **Why does this production application have so many errors?**
> Because its most safety-critical paths — sync conflict resolution, payment state, and webhook reliability — each contain a defeated safety mechanism: the batch-save fallback runs inside a transaction PostgreSQL has already aborted; gateway-owned bill fields are not protected from client overwrites; webhook failures are acknowledged without a retry path; and cancellation notifications are double-fired. These are systemic design flaws, not random bugs.
>
> **Which features cause them?** Sync (recurring 500s, stuck bills, quarantines), Payments/Easebuzz (double-charge window, missed refunds, dead retry engine), Notifications (duplicates), and Web-admin (silent failures from stubs and unpaginated loads).
>
> **What should be fixed first?** P0: preserve gateway state on sync; fix the batch fallback; dedupe notifications. P1: wire the webhook retry engine; close the double-charge/refund windows; add tenant/device observability; restore deploy backup/rollback gates. Then the medium-term architectural work: server-authoritative conflict arbitration and payment event sourcing.