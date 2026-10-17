# KhanaBook — Problem Statement & Open Issues

> Two things in one file:
> **Part 1** — the product problem KhanaBook exists to solve.
> **Part 2** — the problems that are still open in the product and the code today.
>
> Verified against `main` / HEAD `afa5cc86` on 2026-10-17. Each open item names the
> evidence and the next action. Items marked **NEEDS RUNTIME** cannot be settled by
> reading code. Companion: `FEATURE.md`, `../meta/KNOWN_ISSUES.md`,
> `../ARCHITECTURE.md`, `../planning/ROOT_CAUSE_LOG.md`.

---

## Part 1 — The product problem

### Who it is for

Small and mid-sized Indian restaurants (single outlet to a handful of counters) that
bill at the table or counter and need GST-compliant invoices, fast service, and a way
for the owner to see the whole business without standing at the till.

### The problems the product addresses

1. **Connectivity is not guaranteed.** A till that stops working when the internet
   drops loses orders and money. KhanaBook is offline-first: every write lands in the
   encrypted local Room DB first, then syncs. Billing never waits on the network.
2. **Multiple counters must not see each other's open tabs.** A cashier should only
   see their own terminal's bills; the owner should see everything. Enforced in the
   Android DAO (`created_terminal_id`) and, separately, restaurant-wide in web-admin.
3. **GST and reporting are manual.** Bills, GST summaries, day summaries, item sales,
   and daily cash reconciliation are generated from the same bill data, with CSV
   export for the accountant.
4. **Digital payments are fragmented.** Easebuzz sub-merchant onboarding lets the
   restaurant receive settlements directly, with order-specific UPI QR / hosted
   checkout, server-side verification, refunds, and reconciliation.
5. **Owners lack remote visibility.** The web console gives restaurant-wide orders,
   menu, staff, terminals, inventory, reports, and daily closing without touching the
   POS device.
6. **Staff access is hard to reason about.** Roles are pinned (`OWNER`, `SHOP_STAFF`,
   `KBOOK_ADMIN`) so a waiter cannot silently gain price-edit or payout rights, and
   the server is the authority for every check.

### The problem it deliberately does not solve

Marketplace ordering (Zomato/Swiggy), storefront ordering, kitchen display screens,
and Android raw-material inventory are out of scope by product decision
(`docs/meta/AGENTS.md`). The owner console and server do carry a separate
raw-material inventory ledger; that is not a terminal feature.

---

## Part 2 — Open problems

### P1 — High

#### 1. Category reorder/rename/delete is lost after restart on affected tenants

- **Symptom:** drag-reorder applies in-session, then reverts after app restart.
  Category push returns HTTP 409 "modified by another device", and conflict recovery
  pulls with `lastSyncTimestamp = 0`, rewriting local categories to server order.
- **Root cause:** every category edit marks the row unsynced and bumps `updatedAt`;
  the server's optimistic-concurrency version rejects the push. Recovery silently
  discards the local change instead of surfacing a conflict.
- **Evidence:** `KNOWN_ISSUES.md` (verified on-device, 1.0.30);
  `CategoryRepository`, `SyncManager.recoverFromSyncConflict`, `GenericSyncService`.
- **Status:** Open. Pre-existing sync behaviour, not introduced by the category UI.
- **Next:** decide the conflict contract (last-writer-wins with version bump vs
  user-visible conflict), then fix server + client together; add a retry test.

#### 2. One device cannot bill in a second restaurant

- **Symptom:** sign out of restaurant A, sign in to restaurant B on the same install →
  billing blocked by `TerminalPendingApprovalException` (HTTP 202).
- **Root cause:** `TerminalController.activate()` trusts a device only per restaurant.
  Restaurant B already has a terminal, so the "first device" auto-activate path is
  skipped and the device is queued for approval that nobody performs. A fresh device
  works because it hits the first-device branch.
- **Evidence:** `KNOWN_ISSUES.md` (production logs, `TERMINAL_PENDING_REQUEST`);
  `TerminalController` Case 1/2/4 branches; `SyncManager.ensureTerminalActivated`.
- **Status:** Open. Server-side design gap; no Android change required.
- **Next:** add the cross-restaurant trusted-device branch for OWNER, audit it, keep
  the issued token scoped to the current restaurant.

#### 3. Five server feature families have no client at all

- **Orphans:** `/business/tax/**` (GST return/CSV), `/business/customers/**`
  (insights, consent, export, erasure), `/business/financing/**`,
  `/business/commerce/**`, `/business/onboarding/**`.
- **Impact:** owners pay for capabilities they cannot reach; for tax and customer-data
  erasure this is also a compliance gap.
- **Evidence:** controllers exist; `grep` across `apps/web-admin/src` and Android main for
  those path families returns 0 call sites (re-verified 2026-10-17).
- **Status:** Open (product decision, not a bug).
- **Next:** per family — wire, feature-flag, or delete. Cheapest first: GST return on
  the Reports page, since `/analytics/gst-ledger` already backs the on-screen view.

#### 4. Platform admin console is thinner than the platform API

- **Symptom:** 12 `/admin/*` controller families exist; web exposes
  dashboard/businesses/feature-flags only. Commission, payment routing, and admin
  reports have no page. There is no read-only tenant inspection or bounded support
  action, and no "enter tenant" primitive for a KBOOK_ADMIN.
- **Mitigated since the audit:** the platform dashboard now renders settlements,
  sub-merchants, and transactions tabs (verified in
  `platform-dashboard-page.component.ts`). Metrics, webhook health, commission, and
  payment routing still have no page or service wrapper. The tenant-inspection gap
  remains.
- **Evidence:** `app.routes.ts` admin routes; `AdminCommissionController`,
  `PaymentRoutingController`, `AdminReportsController`; sidebar nav.
- **Status:** Open. Gated on the support-policy decision.
- **Next:** choose policy (read-only inspection with audit / support session / no
  access), then reuse tenant-scoped services with an explicit tenant parameter —
  never widen `/business/**` to KBOOK_ADMIN.

### P2 — Medium

#### 5. Payment-mode edit write path has latent defects

- `BillRepository.updatePaymentStatus` writes `payment_status` **without bumping
  `status_version`**, unlike the other mutators, so `BillSyncService.protectBillState`
  cannot distinguish it from a stale push.
- It currently has **no callers** (only the `TenantDaos` pass-through), so nothing
  breaks today — but wiring it up would reintroduce status reversion.
- `bill_payments` is only synced when exactly one active payment exists; bills with
  0 or ≥2 records can diverge from `bills.payment_mode`, which the server uses for
  payment matching.
- `partAmount1`/`partAmount2` are unconditionally reset to `"0.0"` even for non-part
  modes; dead DAO method `BillDao.updatePaymentMode` still exists.
- **Evidence:** `BillRepository.kt:491-503`; `KNOWN_ISSUES.md` (re-verified
  2026-10-17 — still no `statusVersion` bump, still no callers).
- **Status:** Open, latent.
- **Next:** bring the method to parity or delete it; add a test pinning the version
  bump for every bill mutator.

#### 6. Admin push notifications are unvalidated and unaudited

- `POST /admin/notifications/send` takes `restaurantId` from the body, never checks
  the restaurant exists, has no rate limit, and writes no `SecurityAuditEvent`.
- **Evidence:** `AdminNotificationController` (re-verified 2026-10-17).
- **Status:** Open.
- **Next:** validate the tenant, audit every send, consider message-type allowlisting.

#### 7. Easebuzz onboarding is phone-only

- Android has the full sub-merchant flow (onboard, resubmit, KYC key, OTP). Web has
  only `GET/PUT /restaurants/payment-config/easebuzz`.
- **Evidence:** `KhanaBookApi.kt:295-317` vs `business-api.service.ts:436-440`.
- **Status:** Open — accepted gap until decided.
- **Next:** document phone-only onboarding, or build a web wizard over the existing
  endpoints.

#### 8. Dead sync path: `/sync/stocklog` and `stock_logs`

- The Android stock ledger was removed; the server controller and entity mapping
  remain, and `MasterSyncController` still injects `StockLogService` into master pull.
- **Evidence:** `StockLogController`, `StockLog` (`@Table("stock_logs")`);
  Android has no call sites (only a migration safety probe).
- **Status:** Open (legacy tolerance for old APKs).
- **Next:** remove in a release with a deprecation note, or exclude from master pull
  and mark legacy.

#### 9. Documentation contradicts the running system

| Doc | Claim | Reality |
|---|---|---|
| `docs/engineering/ARCHITECTURE.md` | DB version 77; "inventory removed 2026-09-29" | Room v82; web+server inventory is live |
| `docs/design/DESIGN_SYSTEM.md` | role is `SHOP_ADMIN` and "backend still issues it" | roles are OWNER / SHOP_STAFF / KBOOK_ADMIN |
| `docs/meta/AGENTS.md` | "do not build payment links" | Android wires `createPaymentLink(ForBill)` into billing |
| `docs/engineering/ARCHITECTURE.md` | module map omits favourites | favourites shipped 2026-10-15 |
| `graphify-out/GRAPH_REPORT.md` | (was) built from `0d6a9eda`, 2026-10-05 | **now refreshed** to `afa5cc86`, 13,355 nodes |
| `docs/engineering/ARCHITECTURE.md` | "last reviewed 2026-09-28" | 40 commits since 2026-10-14 alone, including sync fixes |

- **Status:** Open for ARCHITECTURE/DESIGN_SYSTEM/AGENTS; the graph report is current again
  (refreshed during this cross-check).
- **Next:** one documentation-truth pass, then a grep gate for `SHOP_ADMIN` and
  "inventory removed".

#### 10. Test reliability and coverage gaps

- Last measured (2026-10-14, **not re-run**): server suite 712 tests, 2 order-dependent
  401 failures on terminal-token sync tests (both pass in isolation); web Karma 121
  tests, 16 failures in date-range/order-history/daily-closing specs whose sources
  were unchanged; `ngc --noEmit` and Maven compile passed.
- Coverage improved since the audit: `BillRepositoryTest`, `BillingLogicTest`, and
  several `MasterSyncProcessor*Test` files now exist. `BillingViewModel` still has no
  dedicated test, so the largest money-path class is verified by hand.
- Two mocking stacks coexist (MockK and Mockito).
- **Status:** Open.
- **Next:** fix test isolation (not the tests' assertions), pick one mocking stack,
  add the first `BillRepository` KOT-version tests.

#### 11. Two-writer status conflict is untested

- A web owner status/payment-mode edit and a device cancel in the same sync window
  are guarded by `statusVersion`, but no runtime conflict test proves both actors are
  told the outcome rather than silently reverted.
- **Evidence:** `BillSyncService.protectBillState`, `GenericSyncService` failed-push
  reporting, `MasterSyncProcessor` now carries `statusVersion`.
- **Status:** NEEDS RUNTIME.
- **Next:** run the two-writer scenario on staging before changing the sync contract.

#### 12. Graphify CLI is broken as installed (graph itself is current)

- The installed CLI uses a uv-managed Python 3.11 without `_ssl`, so
  `http.client.HTTPSConnection` is missing and every command crashes.
- The graph was refreshed on 2026-10-17 using a temporary Python 3.12 venv; the
  committed `graphify-out/` now matches `afa5cc86` (13,355 nodes). The venv is not
  part of the repo, so a fresh clone still cannot run the mandated update command.
- **Status:** Open (tooling only).
- **Next:** reinstall the tool against Python 3.12, or retire it and rely on source.

### P3 — Low

13. **`ReportsViewModel.cancelOrder` has no client permission gate.** Harmless today
    because `billing.void` is in the staff set and the server re-checks on push; it is
    an asymmetry that could bite if the staff set changes.
14. **Authorization style drift.** URL rules, `@RequireRole`, and inline string checks
    coexist; `GET /restaurants/payment-config/easebuzz` is readable by any
    authenticated role in the tenant while the PUT is OWNER-only.
15. **Vendors are create/list only** — no edit/delete.
16. **`docs/engineering/ARCHITECTURE.md` describes inventory as removed** while the deduction
    pipeline runs on every finalized bill (the highest-risk version of item 9).

---

## Part 3 — Closed / verified, not problems

- **Recipe payload** sent a lazy proxy instead of ingredient name — fixed with
  `RecipeDtos.RecipeLineResponse` + read-only transaction; covered by a test.
- **Refund precision** rejected valid 2-decimal amounts — fixed with
  `stripTrailingZeros()`; bounds/zero/negative still rejected.
- **Manual refund validation** was UI-only — now enforced in
  `BusinessReadService` (eligibility, > 0, ≤ total, ≤ 2 dp).
- **Terminal ownership guard** is present in every bill mutator
  (`getOperationalBillById`), closing the stale KNOWN_ISSUES claim (re-verified
  2026-10-13).
- **`NotificationTestController`** is gated `@Profile("!prod")` + KBOOK_ADMIN; the
  earlier "no profile guard" finding was withdrawn.
- **Secrets** (`secrets.properties`, release keystore) are untracked; key rotation
  status is still **NEEDS RUNTIME**.

## Part 4 — Decisions needed

| # | Decision | Blocks |
|---|---|---|
| D1 | Inventory wording: confirm the authoritative statement and fix every doc | Item 9 |
| D2 | Platform-admin support policy: read-only inspection / support session / no tenant access | Item 4 |
| D3 | Orphan features: wire, flag, or delete | Item 3 |
| D4 | Conflict contract for category writes | Item 1 |
| D5 | Easebuzz onboarding: phone-only or web wizard | Item 7 |

## Sources

- `FEATURE_AUDIT_REPORT.txt` (2026-10-14) — role/feature matrix, F1–F12.
- `FEATURE_CROSS_CHECK_AND_SOLUTIONS.txt` (2026-10-14) — recipe/PO/refund fixes.
- `docs/meta/KNOWN_ISSUES.md` — category 409, payment-mode defects, terminal 202.
- `docs/engineering/ARCHITECTURE.md` — blast-radius map and review findings.
- `docs/planning/ROOT_CAUSE_LOG.md`, `docs/planning/cancel-status-revert-root-cause.md`.
- Code re-verified 2026-10-17: `AdminNotificationController`,
  `StockLogController`, `ReportsViewModel`, `BillRepository`, `app.routes.ts`,
  `business-api.service.ts`, `KhanaBookApi.kt`, `AppDatabase.kt`.
