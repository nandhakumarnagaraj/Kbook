# 10 — Performance Audit

> **Honest scope statement:** no load test, APM trace, `EXPLAIN (ANALYZE)` or frame-timing measurement was taken. Every item below is a **structural** finding — code that cannot scale, or that does avoidable work — not a measured bottleneck. Nothing here is a number, and nothing here should be presented to a customer as a speed claim.

## 1. Server-side findings

### AUD-006 (P2) — unbounded result sets [V]

- 46 controller methods return `List<…>`; 6 use `Pageable`.
- `AdminCommissionController:29` returns `subMerchantRepository.findAll()` — platform-wide, un-paged, including KYC/payout fields.

**Why it matters:** response time and heap grow with tenant count, not with what the user is looking at. A dashboard that works for 50 restaurants degrades silently at 5,000.

**Fix:** paginate tables that grow without bound (bills, orders, payments, refunds, activity logs, sub-merchants); cap the rest. Zero UI redesign needed if the response stays additive.

### AUD-021 (P2) — possible N+1 in aggregate views [C]

`business` and `platform` are aggregation modules that compose data from several leaf services per request. No `@EntityGraph`/`JOIN FETCH`/`@BatchSize` usage was found in the reviewed paths, and Hibernate defaults to lazy loading, so a dashboard that walks `bill → items → item` will issue one query per bill.

**This is the most likely performance defect in the product and the cheapest to confirm:** enable Hibernate SQL logging in dev and count statements for one dashboard load. If the count scales with row count, add `@EntityGraph` or a projection query.

### AUD-022 (P3) — broad exception handling can mask retries

Not a throughput issue, but a latency one: converting every exception to a generic message removes the distinction between "client error, do not retry" and "transient, safe to retry". The Android sync engine may therefore retry a permanently failing payload. Pair with AUD-014.

## 2. Database findings

- Tenant-leading composite indexes exist on the hot tables (`06` §2), so the obvious access paths are indexed.
- **Counter-risk:** with 59 index-creating migrations over 29 tables, write amplification on `bills`/`payments` is worth reviewing once real volume exists. Do not remove indexes speculatively.
- **Unverified:** index *usage* per query. Requires a live database and `EXPLAIN`.

## 3. Android findings

### AUD-023 (P2) — oversized state in the settings composable [V]

`ShopConfigView` holds ~25 `remember`ed values and one `remember(name, address, whatsapp, email, consent, reviewUrl, invoiceFooter, openingTime, closingTime, profile)` block with 10 keys. Every keystroke in any field re-evaluates that key list and re-runs the `isDirty` comparison.

For a 973-line composable with two independent OTP timers, this is a recomposition surface with no measured cost but obvious avoidable work. **Fix:** hoist dirty-state into a small state holder with `derivedStateOf`. No visual change.

### AUD-024 (P3) — startup cost of a 2,387-line Room database class [V]

`AppDatabase.kt` holds 18 entities and all migrations. Room's generated code is unaffected by file size, but the *build* and the mental/verification cost are not — and compile time is measured in `12`.

### Positive [V]

- Images go through Coil with explicit `memoryCacheKey`/`diskCacheKey` tied to `logoVersion`, so a changed logo invalidates correctly and an unchanged one is served from cache. That is a deliberate, correct choice.
- `imePadding`/`windowInsetsPadding` are used rather than resize hacks — avoids a relayout storm on keyboard open.

## 4. Web Admin findings

- `business-dashboard` (3,901 lines) and `menu` (2,398 lines) load and derive everything in one component, so a change to any tab re-runs the whole change-detection path. Angular 18 signals help only where they are used.
- **Recommendation:** move derivation into services returning signals; measure the dashboard's initial render before and after. Do not claim an improvement without that measurement.

## 5. Sync path (highest-uncertainty area)

The master pull returns "master data + their own grantedPermissions" in one response. As a shop's menu, inventory and staff grow, that payload grows with no documented pagination or delta mechanism.

**Verification needed before any optimisation:**
1. Capture one real `/sync/master/pull` response size for a realistic tenant (200 menu items, 50 staff, 500 stock items).
2. Check whether the client applies it incrementally or wholesale on every launch.
3. Only then decide between delta sync, compression, or nothing.

This is listed as **[U]** and is the single most valuable measurement to take next, because it affects cashier-perceived launch time on a low-end device.

## 6. Priority

| Priority | Item |
| --- | --- |
| P2 | Confirm or refute N+1 on dashboard/aggregate views (AUD-021) — measurement first |
| P2 | Paginate unbounded endpoints (AUD-006) |
| P2 | Measure `/sync/master/pull` payload before optimising |
| P2 | `derivedStateOf` for settings dirty-state (AUD-023) |
| P3 | Split god-components, then measure (AUD-009) |
| P3 | Review index write cost at volume |
