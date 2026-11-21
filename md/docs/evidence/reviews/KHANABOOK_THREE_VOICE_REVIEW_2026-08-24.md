# KhanaBook Three-Voice Review — Functional / UI / Product Owner

Date: 2026-08-24 · Scope: whole repo (`main`) · Voices: server code correctness, UI/UX (web + Android), business.

---

## VOICE 1 — FUNCTIONAL (server correctness, money & security)

### Critical
1. **QR order price tampering** — `PublicOrderController.java:164-168`: variant is never validated against the selected menu item or restaurant. A customer can pair any restaurant's cheap variant with another restaurant's item → arbitrary paid price. Fix: `findByRestaurantIdAndId` + assert `menuItemId == item.id`.
2. **Unauthenticated-scope refund** — `PaymentController.java:123-131`: `/payments/easebuzz/refund/{billId}` has no tenant check and no amount bounds (defaults ₹0, allows over-refund, unpaid bills). Any authenticated tenant can refund any bill. Fix: route via `RefundService.initiatePartialRefund` with ownership + refundable math.
3. **Partial-refund idempotency bug** — `EasebuzzPaymentService.java:323-330`: deterministic `merchant_refund_id` per bill means the 2nd partial refund reuses the gateway-idempotent ID → no money moves but local state marks refunded. Fix: per-refund ordinal (`REF_{billId}_{seq}`), stable across retries only.

### High
4. **Sync fallback runs in aborted transaction** — `GenericSyncService.java:740-798`: Postgres aborts the tx on the first constraint violation; per-record fallback saves inside the same tx → rollback yet `successfulLocalIds` already returned → client marks unsaved records synced (silent loss). Fix: `REQUIRES_NEW` fallback.
5. **localId in both success and failed lists** — `GenericSyncService.java:577,708 vs 794`: same record reported success AND failure → undefined client behavior. Fix: remove from success on fallback failure.
6. **Fail-open double-charge window** — `EasebuzzPaymentService.java:649-675`: `pollOldTxnStatus` returns `"unknown"` on API error and callers treat unknown as safe-to-clear → fresh txnid while old may be pending (the exact scenario the ERA comment warns about). Fix: fail closed on unknown.
7. **Terminal challenge lockout bypassable** — `TerminalManagementService.java:179-184`: after 3 wrong guesses the code is nulled; null+null rows hit the legacy branch returning OK without matching anything → brute-force cap void. Fix: explicit LOCKED status.
8. **Refund race / over-refund** — `RefundService.java:53`: plain `findById` (no lock) → two concurrent partial refunds jointly exceed total. Fix: `findByIdForUpdate`.

### Medium
9. **Cross-tenant bill enumeration** — `PaymentController.java:111-136`: status/verify/refund-status endpoints accept any `billId`, no tenant scoping → leak amounts/txnid of other restaurants + trigger side effects. Fix: `findByIdAndRestaurantId`.
10. **Webhook amount not verified** — `EasebuzzWebhookService.java:81-83`: `settledAmount` taken verbatim; partial capture silently accepted as full payment. Fix: compare against `bill.getTotalAmount()`.
11. **Split fired pre-commit + lock held across HTTP** — `EasebuzzWebhookService.java:102` / `EasebuzzPaymentService.java:184`: async split can read rolled-back data; bill row locked across gateway round-trip. Fix: `afterCommit` publish, release lock before external call.
12. **QR rate-limit spoofable + counter resets on restart** — `PublicOrderController.java:297-303`: trusts first `X-Forwarded-For` hop; in-memory counter collides with persisted `(QR_ORDER, localId)` after restart. Fix: trusted-proxy XFF count; seed counter from `MAX(local_id)`.

### Done well
- Sync payment idempotency: dual-key dedup (`gateway_txn_id` + `operation_id`) with full semantic comparison.
- Terminal credential rotation: strict `credVer` equality + audit logging on every rejection path.
- Webhook crypto: reversed-hash sequence, constant-time compare, dev-only bypass gated on profiles, replay guard.

---

## VOICE 2 — UI (Angular web-admin + Android Compose)

### Web
1. **HIGH** — `transaction-monitor`, `settlement-reports`, `sub-merchants` pages don't exist (only services/models) — admin dashboard promises unbacked by routes. Build or de-scope.
2. **MEDIUM** — `styles.css:124` touch target token = 40px, violates the documented ≥44px rule. Bump token.
3. **MEDIUM** — `tabular-nums` only on `.stat-card`; financial table columns use proportional digits. Apply to `.data-table` numeric cells.
4. **LOW** — Marketplace route commented out ("hidden v1") but 390-line page still compiled; hardcoded hex (`#ccc`, `#F97316`) violates token rules. Gate/delete.
5. **LOW** — SHOP_ADMIN issued but nearly all routes OWNER-only (only terminals admits SHOP_ADMIN) → confusing limited-access experience. Reconcile landing.
6. **LOW** — Dashboard uses inline `catchError` strings instead of shared `ApiStateComponent` retry pattern used elsewhere.

### Android
7. **HIGH** — `AppLockViewModel.kt:141`: PIN verify has NO failed-attempt counter/lockout — SessionManager lockout helpers exist but are never called → 4-digit PIN brute-forceable offline. Wire exponential backoff.
8. **HIGH** — `SettingsScreen.kt:204-266`: "notifications" settings row navigates to a `section` with no `when` branch → blank screen dead-end.
9. **MEDIUM** — NotificationsScreen fully built + routed (`AppNavGraph.kt:316`) but nothing ever navigates to it → unreachable notification center. Add bell icon/badge.
10. **MEDIUM** — Inventory entry point commented out (`SettingsHomeSection.kt:112`) while the entire feature (screen/VM/repo/REST) ships compiled — dead weight or accidental hide.
11. **LOW** — CartStep phone-validation errors are text-only, no semantics for TalkBack. Add `error`/liveRegion semantics.

### Done well
- Responsive split-view is real: `calculateWindowSizeClass` + width tiers consumed across ~23 screens.
- Marketplace page handles save-failure gracefully (safe message, spinner-disabled button, retry-capable load errors).
- InventoryScreen is a model citizen: loading/error-retry/empty states, toast failures that preserve list, content descriptions.

---

## VOICE 3 — PRODUCT OWNER (business lens)

### Revenue & trust risks
1. **Refund hole = direct money leak** (Functional #2/#3/#8): cross-tenant refunds + broken partial-refund idempotency + over-refund race. For a payments-platform SaaS these are launch blockers before onboarding real sub-merchants.
2. **QR tampering undermines the "tamper-proof" pitch** (Functional #1): the Phase-2 QR story markets server-resolved prices; the variant gap breaks exactly that promise and could undercharge at scale.
3. **KYC funnel dependency**: sub-merchant activation depends on Easebuzz KYC turnaround (docs note 4-5 day pending). No owner-facing SLA/status nudges beyond push messages — churn risk during onboarding window.

### Product completeness vs promises
4. **Hidden features are half-launched**: inventory + analytics built but UI hidden (settings row commented out); notifications screen unreachable. Either ship behind flags or strip — hidden-but-shipped code adds support surface without value.
5. **Admin console gap**: platform admins have controllers (transaction-monitor, settlements, commissions) but no web pages — KBOOK_ADMIN workflows live only in APIs, contradicting PRODUCT.md's "single dashboard" success definition.
6. **Deferred items are correctly parked** (PLAN.md): storefront, multi-branch, complex roles, server-side KOT events — fine to defer, but the marketplace config write endpoint missing (spec'd, not built) silently breaks the Swiggy/Zomato setup story for owners.

### Operational readiness
7. **Deployment automation incomplete** (PLAN.md §9): test-gating, JAR retention, automated rollback missing; `public_token_reconciliation.sql` still pending manual run on prod — must close before scaling restaurants.
8. **Verification gaps** (PLAN.md §4): concurrency tests, sourceChannel round-trip, refund round-trip, two-offline-device invoice test — none landed; each maps directly to a money-integrity risk above (#4/#5 especially).
9. **Support surface**: no visible customer-data/export tooling wired into web (CustomerDataService exists server-side) — owners will ask "where do I get my GST report?" next; analytics UI hiding delays the obvious answer.

### Prioritized business sequencing
1. Fix money-integrity criticals (refunds, QR pricing, sync data-loss) → then
2. Close deployment automation + verification gaps → then
3. Ship notifications entry point + inventory/analytics UI (already built) → then
4. Build KBOOK_ADMIN console pages → then
5. Marketplace config endpoint + SHOP_ADMIN role parity.

---

*Full evidence chains in voice sections above; file paths verified against repo HEAD.*
