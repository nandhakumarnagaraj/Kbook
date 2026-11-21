# KhanaBook Engineering Plan

Status: Active — multi-device billing shipped (5-terminal enforcement live). Current focus is launch hardening, not net-new feature building. This plan was rewritten on 2026-07-17 from actual repo state; the prior PLAN.md (stale, predating the multi-device merge) is retained as PLAN.archive.20260717.md.

## 1. Product Scope

KhanaBook is an offline-first POS for Indian QSRs and small dine-in restaurants.

Supported:
- GST billing with manual UPI, cash, and POS payments.
- Pay-before-food and pay-after-food workflows.
- Customer receipt and kitchen KOT printing (delta printing via `sentToKot`).
- Menu, inventory, reports, synchronization, public invoices.
- Multi-device: up to **5 active Android terminals** per restaurant, server-enforced via `TerminalManagementService.MAX_ACTIVE_TERMINALS = 5` (pessimistic lock on restaurant profile).

Deferred:
- Storefront ordering, Easebuzz gateway verification, gateway refunds, complex staff roles, multi-branch management, shared kitchen print hub, server-side KOT event table.

## 2. What Has Already Shipped (verified from repo, 2026-07-17)

Git HEAD `fe7e9e71`. Do NOT re-plan these as future work — they are merged:

| Area | Status | Evidence |
|---|---|---|
| 5-terminal enforcement | SHIPPED | `TerminalManagementService.java:31,119`; migrations V26/V27/V28/V30 |
| Terminal lifecycle + device requests | SHIPPED | `V30__terminal_lifecycle_and_device_requests.sql`; recovery path fixed `fe7e9e71` |
| SHOP_ADMIN role + terminal mgmt authz | SHIPPED | `900d0ffa`, `54b840cd` |
| sourceChannel round-trip | SHIPPED (field exists) | `Bill.java:59`, `V25__add_bill_source_channel.sql`, Android `MIGRATION_51_52` |
| Refunds server-side + audit | SHIPPED (field exists) | `Bill.java:101`, `V13/V14/V29`, Android `MIGRATION_53_54`/`54_55` |
| publicToken bill identity | SHIPPED | `V19__add_bill_public_token.sql`, Android present pre-first-sync |
| Security hardening KB-001..009 | SHIPPED | commits `f68da0cb`..`b03bbcb4`, `00b3b8c3` |
| Invoice/daily counter atomicity | SHIPPED | `5daae7c2`, `03fc1953` (seed from existing bills) |

## 3. CRITICAL: Launch Blockers (must fix before relying on multi-device)

### 3.1 Room schema version mismatch (CRASH RISK) — FIXED 2026-07-17
- `AppDatabase.kt:28` declared `version = 60`, but schemas dir exported `61.json` (added `payment_attempt_status` + `payment_attempt_started_at` to `bills`) with no `MIGRATION_60_61`, and `BillEntity` lacked those fields → broken/unsafe state.
- **Fix:** bumped `version = 61`, added `MIGRATION_60_61` (guarded ALTER + index, idempotent), added the two fields + index to `BillEntity`, registered the migration in `DatabaseModule.kt` and `DatabaseProvider.kt`. `compileDebugKotlin` green.

### 3.2 public_token uniqueness not guaranteed on dirty prod DB — SCRIPTED 2026-07-17
- `V26` only creates `ux_bills_public_token` when no duplicates exist; a prod DB that held duplicates may still rely on the non-unique `idx_bills_public_token`.
- **Action:** added `ops/sql/public_token_reconciliation.sql` — detection query, idempotent reassign of colliding tokens, guarded creation of `ux_bills_public_token`, and verification queries. Manual-run after backup. Added `BillPublicTokenTest` (entity `@PrePersist` guarantee) so new bills never persist a null/duplicate token.

### 3.3 Invoice-number format constraints unchecked — FIXED 2026-07-17
- Allocation used `displayInvoiceSeries(series) + %02d sequence` (e.g. `A42`), not the plan's `26A1-000042` form, and had no GST 16-char bound.
- **Fix:** `BillServiceImpl.buildInvoiceNumber()` now uses 6-digit zero-pad and guards against exceeding 16 chars (silent truncation + audit hook reserved). Added 2 unit tests (`BillServiceImplTest`) — format + overflow. All pass.

### 3.4 Token replay on terminal re-activation — RE-EXAMINED: NOT A BUG
- `TerminalRequestFilter` (lines 91-116) rejects ANY token whose `credVer` ≠ DB `credentialVersion` on every request; rotation (`recoverTerminal`) bumps `credentialVersion`; `/activate` re-issues the token carrying the current `credVer`. A stolen valid token is only usable until rotation (by design). No code change needed.

## 4. Verification Gaps (both review voices flagged)

| Gap | Voice A (Claude) | Voice B (Codex) | Action |
|---|---|---|---|
| Concurrency tests | cooldown path, approve-vs-recovery race, /activate+approve combo missing; H2 may mask deadlock as pass | priority stack wrong (waits should be ordered) | Add `TerminalManagementPostgresConcurrencyTest`; assert cooldown returns null <5min |
| sourceChannel round-trip | no Zomato/Swiggy survive push+pull contract test | — | Add push→pull contract test per marketplace source |
| Refund round-trip | admin-refund→Android-pull→report integration test missing (plan 3.2 required) | — | Add integration test; assert Android report reflects server refund |
| Offline invoice alloc | — | understated in plan; local-state-dependent allocation needs offline two-device test | Two-offline-device unique-order/invoice test on real DB |
| Deployment automation | test-gating, git-commit exposure, JAR retention, rollback partial | migration/rollback not operationalized | Complete Section 9 pipeline |

## 5. Android Allocation Logic (as implemented — keep)

- First online activation assigns a permanent terminal series from server.
- Reserve next order/invoice sequence; save bill+items+payments in one Room transaction.
- Drafts get order number, no invoice number; invoice allocated only on successful settlement.
- Never renumber settled/printed invoice.
- Reinstalled device gets new series unless encrypted DB + device identity restored.
- Pre-provision next financial-year series before rollover.

## 6. Multi-Device KOT Safety (status)

- Delta printing via `sentToKot` works; retained.
- Server-side KOT event table (`KotEventEntity` exists on Android only) has **no server migration** — cross-device KOT ownership/audit is NOT yet server-backed. Deferred; do not claim it works cross-device until the server table + DTO land.
- Originating device auto-prints its events; pulled events never auto-print elsewhere (by design, V1).

## 7. Logout Verification (keep as-is, test-only)

Unsynced completed bill blocks logout; failed immediate sync preserves DB; force logout cannot bypass unsynced terminal bills; login restores state; hard delete needs explicit confirm. Change exception fallback only if on-device test demonstrates a bypass.

## 8. Sync and Scale (post-correctness)

- Cursor-based pagination on master pull (seam `mergeMasterSyncPages` exists).
- Follow-up run when data changes during active `KEEP` sync.
- Android/server contract tests for every replicated field (start with sourceChannel, refund, terminalSeries).
- Monitor quarantine count, conflict reasons, payload size, sync duration.

## 9. Deployment Automation

Target pipeline (partially present: backup script + health endpoint):
```
mvn test -> package JAR -> PostgreSQL backup -> record Git commit -> restart -> health/OpenAPI smoke -> retain rollback + previous JAR
```
Missing: test gating (remove `-DskipTests` in prod after stable), `GIT_COMMIT` exposure (actuator/info already wired `dbd99b0f`), previous-JAR retention, automated rollback, Git pull/rebuild. Never run Flyway on prod without verified backup + rollback plan.

## 10. Execution Sequence (remaining)

```text
Fix 3.1 Room crash
-> close 3.2/3.3/3.4 launch blockers
-> fill Section 4 verification gaps (concurrency, round-trips, offline two-device)
-> complete Section 9 deployment automation
-> (deferred) server-side KOT events, pagination, storefront
```

Multi-device billing is live but treat the above blockers as release gates before broad rollout.

## 11. Sync 409 Conflict Loop — ROOT CAUSED & FIXED

**Symptom:** Restaurant `2247597590390850619`, terminal `10` — `GenericSyncService` returns 409; Android re-pushes the same 11 bills every cycle (infinite loop). Server log: `ux_bills_restaurant_invoice_series_active` unique violation.

**Root cause:** `GenericSyncService.saveAll` (server `sync/service/GenericSyncService.java:584`) is all-or-nothing. On `DataIntegrityViolationException` from the invoice-series unique index, the old catch rethrew, so `successfulLocalIds` was never populated. Android `pushBatches` (`MasterSyncProcessor.kt:76`) only marks synced the ids in `successfulLocalIds`; the colliding bills stay `isSynced=0` and re-queue every cycle. The `quarantineFailedSyncRecords` path never fired (needs `SyncConflictException` + `failedLocalIds`, which the bare throw lacked). Separately, `BillingViewModel.allocateInvoiceIdentity:75` computed the next sequence from a query filtered by `terminal_series`+`financial_year` rather than the exact `invoice_series`, so server-pulled bills under the same series were not always counted → new collisions after a pull.

**Fix (two layers):**
- Server: `saveAll` catch now falls back to per-record save; colliding rows go to `failedLocalIds` (client quarantines them) while the rest commit → loop breaks.
- Android: `getMaxInvoiceSequence` now filters by the exact `invoice_series` (`BillDao.kt:490`, `TenantDaos.kt:467`, `BillRepository.kt:318`, `BillingViewModel.kt:75`) so local allocation never reuses an already-allocated sequence under the same series.

**Verification:** server `compile` + `test-compile` green; Android `compileDebugKotlin` green. `MultiDeviceInvoiceSyncIntegrationTest` updated (`duplicateInvoiceSequenceIsIsolatedNotFatal`) — needs Docker to run in CI.

**Still pending:** `ops/sql/public_token_reconciliation.sql` (3.2) must be run on prod after a verified backup.
