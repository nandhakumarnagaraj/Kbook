# 14 — Improvement Roadmap

**Guardrail:** every item stabilises, corrects or measures something that already exists. No new module, feature, integration or workflow appears in this roadmap. Items that would change product behaviour are marked **[PO]** and need product-owner sign-off before implementation.

Sequencing rule: **restore the gate → contain the leak → remove false assurance → pay down structural debt → measure.** Doing these out of order means working without a safety net.

---

## Phase 0 — Restore the ability to trust `main` (done / immediate)

| # | Action | Status |
| --- | --- | --- |
| 0.1 | Repair the four compile breaks from `8a600882` | **Done 2026-11-13** (`15` §0) |
| 0.2 | Confirm `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass | **Done**, both exit 0 |
| 0.3 | Require the `CI` workflow on `main` (branch protection) | **Open** — needs repo admin |
| 0.4 | Local pre-push compile check as a fallback if Actions cannot run | **Open** |

**Exit criteria:** a tree that does not compile can no longer reach `main`; the reason the previous one did is identified and written down in `12` §3.

---

## Phase 1 — Contain the P1 exposures (first working sprint)

| # | Action | AUD | Effort |
| --- | --- | --- | --- |
| 1.1 | Introduce an ORM-level tenant filter + explicit `KBOOK_ADMIN` bypass | AUD-003 | M |
| 1.2 | One cross-tenant test per module (authenticate as A, request B's id, expect 404/403) | AUD-003 | M |
| 1.3 | **[PO]** Decide the Quick Start password: remove the field, or send it to a real password-set path with `EncryptedSharedPreferences` | AUD-002 | S–M |
| 1.4 | Stop prompting for a password that is not used (whichever option 1.3 chooses) | AUD-002 | S |

**Exit criteria:** a deliberately removed `restaurantId` predicate in any one module fails a test instead of leaking data; no plaintext credential is written to disk; onboarding copy matches behaviour.

---

## Phase 2 — Remove false assurance (same sprint if capacity allows)

| # | Action | AUD | Effort |
| --- | --- | --- | --- |
| 2.1 | Run `connectedDebugAndroidTest` once, triage, then delete `continue-on-error` or delete the dead tests | AUD-007 | S–M |
| 2.2 | Make Room migration tests blocking | AUD-015 | S |
| 2.3 | Collapse duplicate workflows: `ci.yml` as the gate, `android-ci.yml` reduced to instrumented only | AUD-019 | S |
| 2.4 | Re-enable `nosniff`; fix the asset that motivated disabling it | AUD-005 | S |
| 2.5 | In-band protection for `/actuator/prometheus` (scrape token or separate management port) | AUD-004 | S |
| 2.6 | Dependency CVE sweep (`mvn dependency-check`, `npm audit`, Gradle equivalent) | AUD-027 | S |

**Exit criteria:** every workflow has a unique purpose; every test tier that exists actually runs; no security header is disabled without a recorded scoped exception.

---

## Phase 3 — Structural debt with measured payoff

| # | Action | AUD | Effort |
| --- | --- | --- | --- |
| 3.1 | Paginate/cap endpoints over unbounded tables | AUD-006 | M |
| 3.2 | Confirm or refute N+1 on dashboard/aggregate views with SQL logging | AUD-021 | S to measure, M to fix |
| 3.3 | Measure `/sync/master/pull` payload on a realistic tenant before optimising | — | S |
| 3.4 | Extract a shared OTP state holder + field; rewrite phone and email flows onto it | AUD-012/025 | M |
| 3.5 | `derivedStateOf` for settings dirty-state | AUD-023 | S |
| 3.6 | Split `AppDatabase.kt` along entity boundaries | AUD-015 | M |
| 3.7 | Log the throwable in broad catch blocks with the request id | AUD-014 | S |
| 3.8 | Resolve `completePasswordReset`: expose it (needed by Google/email owners) or delete it | AUD-011 **[PO]** | S |

**Exit criteria:** no endpoint returns an unbounded table; the dashboard's query count does not scale with row count; one OTP implementation, tested without Compose.

---

## Phase 4 — Maintainability, opportunistically

Only touch these when already editing the file for another reason — they carry risk without user-visible benefit.

| # | Action | AUD |
| --- | --- | --- |
| 4.1 | Extract dashboard data derivation into services, one tab per change | AUD-009 |
| 4.2 | Same for the menu page | AUD-009 |
| 4.3 | Key web-admin ticket cache per user or delete it | AUD-010 |
| 4.4 | Remove the stale root duplicate trees; ignore them at the root | AUD-008 |
| 4.5 | Register sync handlers instead of extending the central switch — only when a new entity is next added | AUD-020 |
| 4.6 | Adopt `gradle/libs.versions.toml` | — |
| 4.7 | Document `*Screen.kt` vs `*View.kt` and the feature-package layout | — |
| 4.8 | Fix OTP verified-state reset keying | AUD-026 |

---

## Explicitly deferred (and why)

| Option | Why not now |
| --- | --- |
| Gradle feature-module split | Large, risky, no user-visible benefit; Phase 1–3 first |
| Microservices | Would add operational surface without fixing any observed failure |
| Sync engine rewrite | It is idempotent and works; extend incrementally (4.5) |
| UI redesign | No finding in `11` requires one |
| New features from `POST_V2_FEATURE_SUGGESTIONS.md` | Out of scope by the audit's own constraint — the point is to stabilise what exists |

## How to know this roadmap worked

Track only these, quarterly:

1. Red `main` incidents: target 0 (currently ≥ 1 on 2026-11-13).
2. Cross-tenant tests: target ≥ 1 per module.
3. Test tiers actually executed in CI: target 3/3 (server, Android unit + instrumented, web-admin).
4. Unbounded list endpoints: target 0 over the named growing tables.
5. Plaintext credentials at rest: target 0.
