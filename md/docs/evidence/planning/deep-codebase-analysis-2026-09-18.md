# Deep Codebase Analysis — KhanaBook (2026-09-18)

> **Scope:** Security, correctness, and robustness across Android (POS), Server (Spring Boot), Web Admin (Angular).
> **Method:** Static analysis of source, configs, migrations, and manifests — cross-verified against this session's earlier audits (printing, sync, save-flow). No code changed.
> **Context:** Android is ~95% feature-sliced, Server & Web Admin are still layered (see `VERTICAL_SLICES_BLUEPRINT.md` cross-check).

---

## 1. Executive Summary

**Overall verdict: this is a well-defended codebase.** The fundamentals are unusually strong for a POS SaaS: certificate pinning, Keystore-backed secrets, terminal-bound JWTs, tenant-scoped queries, constant-time token compares, idempotent webhooks, pessimistic locking on invoice sequences, and 75+ tested Room migrations.

The findings below are **residual risks at the edges** — none are architectural. Ranked by real-world impact:

| # | Severity | Finding | Platform | Fix size |
|---|---|---|---|---|
| F1 | 🔴 **Critical** | `Android/secrets.properties` (keystore passwords, Google client ID) is **tracked in a public repo** | Android | 15 min |
| F2 | 🟠 High | Certificate pin-set has **no backup pins and hard expiry** (2028-06-04) | Android | 30 min |
| F3 | 🟠 High | FCM retry uses `GlobalScope` + self-re-launching recursion (unbounded lifetime) | Android | 1 hr |
| F4 | 🟡 Medium | `runBlocking` on the print path (logo network fallback) can freeze printing | Android | 30 min |
| F5 | 🟡 Medium | Webhook idempotency = read-then-insert (TOCTOU under concurrent duplicates) | Server | 1 hr |
| F6 | 🟡 Medium | Public invoice endpoints are **unthrottled** (UUID space too big to brute-force, but free to hammer) | Server | 30 min |
| F7 | 🟡 Low-Med | Public invoice HTML lacks `Cache-Control: no-store` (PII in shared caches) | Server | 10 min |
| F8 | 🟡 Low | KYC-approval webhook: unhashed events accepted via cross-checks (dev bypass is profile-gated ✅) | Server | review |
| F9 | ⚪ Hygiene | 5 save-fix files + doc uncommitted; server/web-admin restructure 0%; graphify stale after restructure | All | — |

---

## 2. Findings — Detail

### F1 🔴 `secrets.properties` committed to a **public** repository

**Evidence:**
```
$ git check-ignore Android/secrets.properties  → NOT ignored
$ git ls-files Android/secrets.properties      → tracked
git log: 03017254 "Track runtime configuration files"
         c82d7bc6 "chore: repository cleanup — untrack secrets, remove stale files, harden gitignore"  ← irony: later re-tracked
```
File contains `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_PASSWORD` (currently placeholder-style values `YOU***`), `BACKEND_URL`, `GOOGLE_WEB_CLIENT_ID`.

**Why it matters:** the *pattern* is live — the moment real keystore credentials land in this file, they're one `git push` from public. Signing passwords + a public repo = anyone can build and sign a malicious KhanaBook APK that indistinguishably updates the real one.

**Fix (15 min):**
1. `git rm --cached Android/secrets.properties`
2. Add to `.gitignore` (the `.example` file stays tracked)
3. If real credentials were ever committed: **rotate the keystore password** and consider a play-app-signing migration; also purge from history (`git filter-repo`) since it's a public repo.

---

### F2 🟠 Certificate pinning is correct but fragile — single pin-set, dated expiry

**Evidence:** `network_security_config.xml` pins `kbook.iadv.cloud` with 3 pins, `expiration="2028-06-04"`, no `backup-lines`.

**Why it matters (two failure modes):**
- **Rollover:** if the server's cert/key rotates to a new intermediate not covered by these 3 SPKIs, every production app **hard-fails TLS** — total outage of POS→server connectivity until users update.
- **Expiry semantics:** after 2028-06-04 Android silently *stops enforcing* the pin-set (fails open to normal CA validation). Secure-but-quiet degradation.

**Fix:** add a **backup pin** (a spare key's SPKI stored offline — never deployed), and a calendar/CI reminder well before 2028 to rotate the pin-set alongside cert renewal. Consider pinning the intermediate rather than leaf to survive leaf renewal.

---

### F3 🟠 `GlobalScope` recursive FCM retry — unbounded coroutine lifetime

**Evidence:** `NotificationRepository.kt:252` — `GlobalScope.launch(Dispatchers.IO) { delay(5000); api.registerDeviceToken(...); ... }` re-launches itself on failure (fixed 5s, cap via prefs — but the chain never dies with the app component that started it). Also `:305` for background registration.

**Why it matters:** battery/network drain in background; no cancellation when token becomes stale or user logs out; antipattern that survives process-lifecycle reasoning. This was flagged in the earlier all-features audit; still unfixed.

**Fix:** inject an application-scoped `CoroutineScope` (Hilt module), use `WorkManager` for the retry with exponential backoff. ~1 hr including DI wiring.

---

### F4 🟡 `runBlocking(Dispatchers.IO)` inside the print path

**Evidence:** `InvoiceFormatter.kt:63` — logo network fallback blocks a thread if the local logo file isn't synced yet (first-run scenario). The comment correctly says printing "must never depend on the network," but the fallback *does* go to network — synchronously.

**Why it matters:** on a slow network, the first receipt after install can stall for the full HTTP timeout on the printing coroutine; combined with the kitchen queue this delays subsequent jobs. Low frequency, real cost.

**Fix:** drop the network fallback entirely (skip logo, print text-only — receipts remain valid), or pre-fetch the logo at profile-sync time only (which the comment says already happens). Simplest correct change: delete the Coil fallback block. ~30 min.

---

### F5 🟡 Webhook dedup is check-then-act, not atomic

**Evidence:** `EasebuzzWebhookService.handlePaymentWebhook` — `webhookEventRepo.existsByTxnIdAndStatus(txnid, status)` then later insert. Easebuzz retries webhooks; two concurrent deliveries can both pass `exists` and double-process.

**Why it matters:** mitigated downstream by the bill-level `paymentStatus == "paid"` guard (line 82) for payment success — but payout/KYC/failure paths don't all have an equivalent business-level guard. Worst case is duplicated side effects (notifications, payout records).

**Fix:** unique constraint on `(txn_id, status)` in `easebuzz_webhook_events` + `insert ... on conflict do nothing` (or catch `DataIntegrityViolationException` and treat as duplicate). ~1 hr with migration.

---

### F6 🟡 Public endpoints unthrottled (invoice, webhook)

**Evidence:** `SecurityConfig` permits `/public/**` unauthenticated; `RateLimitingInterceptor` exists but public invoice endpoints don't appear in a strict bucket. UUIDv4 tokens (122-bit) make guessing infeasible — this is **availability** exposure only: an attacker can hammer `/public/invoice/...` for free (DB hits on every miss).

**Fix:** rate-limit `/public/**` per-IP (the interceptor infrastructure is already there), and add `Cache-Control` (F7) so a CDN in front absorbs repeat hits.

---

### F7 🟡 Public invoice HTML — no cache headers

PII (customer name/phone on invoice) served with no `Cache-Control: no-store, private` — shared/proxy caches may retain invoice pages. One-line fix in `InvoiceController` (`ResponseEntity.ok().cacheControl(...)`).

---

### F8 🟡 KYC-approval webhook accepts unhashed events via cross-check

`verifyPayoutHash` path: for `MERCHANT_KYC_APPROVAL` with no hash, server accepts if subMerchant exists **or** a WIRE API lookup confirms the email. The dev bypass (`isDevOrSandboxProfile()`) is properly profile-gated ✅, but the WIRE-lookup fallback in prod is a trust-on-secondary-evidence decision — defensible, worth an explicit security review note + alerting when it fires.

---

## 3. What's Verified Strong (do not touch)

| Area | Evidence |
|---|---|
| **Transport security** | Cleartext disabled globally; system-CAs-only; pinning on API domain; no user-CA trust |
| **Token storage (Android)** | JWTs in `KeystoreBackedPreferences` (AES/GCM + Android Keystore), legacy prefs migration path |
| **Token storage (web-admin)** | `sessionStorage` (deliberate XSS-surface reduction, commented), JWT interceptor + role guard present |
| **Auth (server)** | JWT w/ device-binding cross-check (`X-Device-Id` mismatch → reject + audit), admin IP allowlist, token blocklist cache, terminal-token filter **after** JWT filter with tenant cross-check |
| **RBAC** | Clean role matrix in `SecurityConfig` (KBOOK_ADMIN/OWNER/SHOP_STAFF), `/admin/**`, `/business/**`, `/sync/**` all gated; dev signup endpoints `@Profile("dev")`-gated ✅ |
| **Tenant isolation** | Every bill query scoped by `restaurantId` (+ terminal scoping on pulls); `TenantContext` ThreadLocal; pessimistic lock on invoice sequence; DB unique indexes guarded by pre-flight conflict checks |
| **Payment webhooks** | Reverse-hash sequence per Easebuzz spec, **constant-time compare** (`MessageDigest.isEqual`), bill-level paid-guard, payout hash scenario split |
| **Offline sync authz** | `OfflineAuthDecider` — permission revision captured at creation, revocation-after-creation → REJECT, missing revision → QUARANTINE (conservative defaults, fully unit-testable pure function) |
| **Public invoice tokens** | UUIDv4 + constant-time compare + tenant+bill binding on lookup |
| **Swagger** | Disabled by default in prod (`SPRINGDOC_*:false`) |
| **DB** | 75+ Flyway migrations, index coverage on the sync hot path (`restaurant_id, server_updated_at`) |
| **Previously fixed this session** | Wi-Fi transport (chunked+watchdog+retry), status-revert sync chain, save single-flight — all tested green |

---

## 4. Cross-cutting Observations

1. **Error-handling consistency (server):** `JwtRequestFilter` logs validation failures at `debug` — good for ops noise, but pair with a security metric/counter so a spike in failures (credential stuffing) is *visible* in monitoring, not just logs.
2. **Web-admin:** small surface, decent guards. No findings beyond the usual sessionStorage-vs-CSP tradeoff (acceptable; ensure a strict CSP is set server-side for the admin bundle).
3. **Android test tree** still mirrors pre-slice packages — compiles fine, but after finishing the vertical-slice stragglers it won't mirror feature structure. Low priority, do during restructure.
4. **Process:** 5 save-fix files still uncommitted (from the previous task) — commit before any restructure work.

---

## 5. Recommended Order

| Priority | Action | Effort |
|---|---|---|
| **P0** | F1 — untrack `secrets.properties`, gitignore, verify no real creds ever committed | 15 min |
| **P0** | Commit the 5 pending save-fix files | 2 min |
| **P1** | F2 — backup pin + rollover calendar | 30 min |
| **P1** | F5 — webhook dedup unique constraint | 1 hr |
| **P2** | F3 — replace GlobalScope retry with WorkManager | 1 hr |
| **P2** | F6+F7 — public route rate-limit + `Cache-Control: no-store` | 40 min |
| **P3** | F4 — remove logo network fallback from print path | 30 min |
| **P3** | F8 — add alerting on WIRE-fallback acceptance path | 1 hr |

Every fix is additive and none touches the freeze-list features (sync engine, terminal ownership, KOT queue, payment idempotency, transports).
