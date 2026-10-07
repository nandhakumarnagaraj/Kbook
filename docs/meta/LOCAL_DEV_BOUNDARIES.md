# Local Development & Testing Boundaries

Scope: what may run on the **developer machine** (this checkout) versus the VPS, so that
development and testing can never touch production data or infrastructure.

This document exists because of one concrete hole: a debug build currently talks to
**production** with no warning (see §1). It complements
[STAGING_SETUP_RUNBOOK.md](../qa/STAGING_SETUP_RUNBOOK.md), which defines the isolated
staging stack. Where the two overlap, the runbook's isolation rules win.

For the engineering-process side of the same question — scope, architecture invariants, R&D,
competitor analysis, feature-design checklists and the evidence standard — see
[SOLO_ENGINEERING_BOUNDARIES.md](SOLO_ENGINEERING_BOUNDARIES.md).

---

## 1. Why this is needed (verified findings)

| # | Finding | Evidence |
|---|---|---|
| 1 | The Android `BACKEND_URL` fallback **is the production URL**, so an unconfigured build targets prod. | `Android/app/build.gradle.kts:38` — `configValue("BACKEND_URL", "https://kbook.iadv.cloud/")` |
| 2 | The HTTPS + host-must-be-`kbook.iadv.cloud` guard is **release-only**, inside `gradle.taskGraph.whenReady`. Debug builds bypass it entirely. | `Android/app/build.gradle.kts:203-218` |
| 3 | The per-machine value file has **every** `BACKEND_URL` line commented out, so finding 1 currently applies to local builds. | `Android/local.properties:8,12,16` (all `#`) |
| 4 | `Android/secrets.properties` carries `BACKEND_URL`, but **no Gradle build file reads that file** — editing it has no effect on the app. | grep for `secrets` in `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` → no matches |
| 5 | This machine **cannot run the local/staging stacks**: docker is not installed. | `command -v docker` → not found |
| 6 | Consequence: a debug build tested here writes real bills/menu rows into `kbook_saas` and drives the real 5-terminal limit and real permission revisions. | observed: `Master pull complete: pages=1, totalRecords=1507`, real terminal id 66, real bill repairs |

Effective `configValue` precedence (`app/build.gradle.kts:16-20`):
`local.properties` → gradle property (`-P`) → environment variable → **hard-coded default**.

---

## 2. Boundary tiers

### B0 — Never from this machine (hard stop)
- Any write to production Postgres (`kbook_saas`, prod volume `pgdata`), including
  "just checking" SQL, migrations, or Flyway repair.
- `ops/deploy-production.sh`, `ops/backup_postgres.sh`, `ops/restore_postgres.sh`,
  `ops/flyway_repair_v82_checksum.sh`, `ops/flyway_reconcile_checksums.sh`,
  `ops/migrate_kyc_documents.sh`, `ops/reset-staging.sh`, `ops/deploy-all.sh`.
  These are VPS-side operations. Run them on the VPS, after a backup, deliberately.
- Touching the production CDN dirs, private-document dirs, Apache prod vhost, or
  production Easebuzz keys (see runbook §16 for the exact forbidden identifiers).
- Committing or copying `.env`, `Android/secrets.properties`, keystores,
  `google-services.json`.
- Production credentials in a local shell profile, so an agent or a stray script
  inherits them.

### B1 — Allowed on this machine (no external side effects)
- Android unit tests: `cd Android && ./gradlew testDebugUnitTest`
- Android lint / assemble Debug with an explicitly non-production `BACKEND_URL`.
- Server unit tests that run on in-memory H2 (`BaseIntegrationTest` →
  `jdbc:h2:mem:khanabook_test;MODE=PostgreSQL`).
- Reading code, docs, migrations, schema files; static analysis; writing documentation.

### B2 — Blocked on this machine by missing tooling (state it, don't fake it)
- `ops/docker-compose*.yml` — **docker is not installed here**.
- `connectedAndroidTest` — no emulator/device attached.
- Server tests requiring Testcontainers/Postgres
  (e.g. `TerminalManagementPostgresConcurrencyTest`) — same docker gap.

Rule: when a check falls in B2, report it as **not executed**. Never present it as passing.

### B3 — The only permitted integration surface: remote staging
- Target: the VPS staging stack (`127.0.0.1:8091` on the VPS, DB `kbook_staging`,
  volume `pgdata-staging`), per `docs/qa/STAGING_SETUP_RUNBOOK.md`.
- Any debug build used to test sync, terminals, or permissions must point there,
  not at production.

---

## 3. How to point a debug build at non-production

Edit **`Android/local.properties`** (gitignored — correct place for a per-machine value).
`Android/secrets.properties` is *not* read by the build, so putting it there does nothing.

```properties
# local dev backend (LAN server) — uncomment one, never leave prod here by accident
# BACKEND_URL=http://10.139.49.168:8081/
# BACKEND_URL=http://10.0.2.2:8081/      # emulator -> host loopback
# BACKEND_URL=https://staging.kbook.iadv.cloud/
```

Leave it **unset only if you intend production** — remember the default is prod (§1).

---

## 4. Enforcement gaps to close

| ID | Gap | Suggested guard | Strength |
|---|---|---|---|
| BD-01 | Debug build may point at prod silently | Fail (or loudly warn) when `BuildConfig.DEBUG` and the host is `kbook.iadv.cloud`, unless `-PALLOW_PROD_DEBUG=true` | highest value |
| BD-02 | Dev machine can reach prod at all | Point the default at staging/local, so prod must be chosen deliberately | strong |
| BD-03 | Sync/pull changes cannot be device-verified locally (no emulator) | Require a staging device run before releasing any change to sync, pull, or quarantine logic | process |
| BD-04 | Agents/shell commands inherit whatever is in the environment | Never export prod credentials in a local shell; keep prod access to the VPS | process |

BD-01 is one build-script check and is the single change that would have prevented the
current situation.

---

## 5. Rules for AI coding agents on this machine

1. Read-only by default. Do not modify files until the change is explicitly requested.
2. Never run anything in B0. Never `docker compose` against the production project.
3. Never stage, commit, or push changes the user did not make; never `git add -A`.
4. When a check is in B2, say so plainly instead of implying it passed.
5. Report test/lint exit statuses honestly, including failures, and give file:line evidence
   for claims about behaviour.
6. Treat production data seen in logs (bill ids, terminal ids, restaurant ids) as
   confidential; do not paste it into external prompts beyond what the task needs.

---

## 6. Quick checklist before any test run

- [ ] Which backend does this build point at? (`Android/local.properties`)
- [ ] Is that non-production? If not, stop — move to B3.
- [ ] Does this test write data? If yes, it must target staging, never prod.
- [ ] Am I about to run anything from the B0 script list? If yes, run it on the VPS instead.
- [ ] Any check I cannot run here (B2)? Log it as not executed.
