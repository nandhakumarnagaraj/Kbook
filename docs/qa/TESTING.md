# KhanaBook — Testing & CI

> How to run, trust, and extend the test suites. Verified against `main` / HEAD
> `afa5cc86` on 2026-10-17.
>
> Companion: `../meta/AGENTS.md` (build/test commands), `../engineering/SYNC_CONTRACT.md`
> (sync invariants worth testing), `../product/PROBLEM.md` item 10 (open test debt).

## 1. Suites and commands

| Suite | Workdir | Command | Count |
|---|---|---|---|
| Android unit (JVM) | `apps/android/` | `./gradlew.bat testDebugUnitTest` | 60 files |
| Android unit (all variants) | `apps/android/` | `./gradlew.bat test` | — |
| Android instrumented | `apps/android/` | `./gradlew.bat connectedAndroidTest` | 28 files (needs device/emulator + Hilt runner) |
| Android lint | `apps/android/` | `./gradlew.bat lint` | — |
| Server | `services/server/` | `mvn test` | 115 files |
| Server, single class | `services/server/` | `mvn test -Dtest=ClassName` | — |
| Server build | `services/server/` | `mvn clean package` | `-DskipTests` to skip |
| Web admin | `apps/web-admin/` | `npm test` (`ng test`) | 33 spec files |
| Web admin build | `apps/web-admin/` | `npm run build` | prod bundle |
| Web admin typecheck | `apps/web-admin/` | `tsc --noEmit` | — |

Unit tests need **no Docker**. Only the gated integration suite does (§3.4).

## 2. CI workflows (`.github/workflows/`)

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | push to `main`, every PR | **server** (`./mvnw -B test`, with exclusions — see §3.1), **android** (`./gradlew testDebugUnitTest`, writes a stub `google-services.json`), **web-admin** (`npm ci` → Karma headless → `npm run check:motion`) |
| `android-ci.yml` | push | `android-unit-tests`, `android-lint` |
| `web-admin.yml` | push | `npm ci`, Karma headless, `check:motion`, `npm run build` |
| `gated-tests.yml` | see file | `testcontainers-gate` — `mvn test -Dtest=MultiDeviceInvoiceSyncIntegrationTest,PostgresMigrationSmokeTest` |
| `guards.yml` | push/PR to `main`, `v1`, `v3` | `guard-v2-artifacts` (fails on `DbCheck`, `QuickDbCheck`, `dev-debug`, `dev-refresh`, new `*storefront*` sources), `guard-credential-files` (fails on newly tracked credential filenames), `guard-web-admin-states` |

`ci.yml` is the merge gate: a failing suite blocks the push before production.

## 3. Current known state (read before trusting a green run)

### 3.1 Server tests are run with two classes excluded

`ci.yml` runs:

```
./mvnw -B test -Dtest='!EasebuzzWebhookTest,!EasebuzzWebhookServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
```

The inline TODO says these fail on `main` (Easebuzz webhook notification argument
drift) and that they are excluded **so CI starts green**, with an instruction to remove
them as each is fixed and never to add new exclusions. The comment also mentions six
classes historically excluded; the four bill-push 409 classes were un-excluded on
2026-09-29. Treat the comment as drift and the flag as the truth.

### 3.2 Order-dependent server failures

Last measured 2026-10-14 (**not re-run**): 712 tests, 2 failures, 29 skipped. Both
failures were 401s on the sync terminal-token path:

- `MasterSyncTerminalIsolationTest.terminalA_activeBill_notReturnedToB`
- `TerminalAccessControlGapTest.kotOnlyTerminal_stillPushesBills`

Both pass in isolation; a HEAD-only control run was 704 tests / 0 failures. This is
**test isolation**, not product behaviour — fix the shared state, do not delete the
assertions.

### 3.3 Web-admin Karma failures

Last measured 2026-10-14 (**not re-run**): 121 tests, 16 failures, all in
date-range / order-history / daily-closing specs whose component and spec files were
byte-identical to `HEAD`. Pre-existing and unrelated to recent changes — but CI treats
them as failures, so verify current state before assuming green.

### 3.4 Coverage gaps

- No dedicated `BillingViewModel` test — the largest money-path class is verified by
  hand.
- Sync coverage exists and is worth extending:
  `MasterSyncProcessor*Test`, `BillRepositoryTest`, `BillRepositoryActiveOrdersTest`,
  `MenuPushAuthorizationIntegrationTest`, `MultiDeviceInvoiceSyncIntegrationTest`.

## 4. Conventions

- **One mocking stack.** Both MockK and Mockito are currently on the Android test
  classpath; team preference is MockK. Do not add new Mockito usage.
- **Money path first.** Any change to billing, sync, or payment logic should add or
  extend a test — these are the areas with the least coverage relative to risk.
- **Migration changes** need an instrumented migration test
  (`docs/android/MIGRATION_TESTING_PLAYBOOK.md`) plus the gated Postgres smoke suite.
- **Sync changes** need a round-trip test: push → ack mapping → pull → conflict.
- **Do not weaken assertions to make CI green.** Two known-failing classes are excluded
  today; that exclusion list is a debt register, not a policy.
- Instrumented tests are not run in CI (no emulator job) — run them locally before
  shipping device-affecting changes.

## 5. Guards worth knowing

| Guard | Blocks |
|---|---|
| `guard-v2-artifacts` | resurrected v2 debris (debug DB checkers, `dev-*` endpoints, new storefront sources) |
| `guard-credential-files` | newly tracked credential files (`.env`, keystores, `google-services.json`) |
| `guard-web-admin-states` | web pages missing required UI states |
| `check:motion` | animation durations outside the 150–300 ms band (reads every stylesheet/template, not just mounted pages) |

## 6. Quick verification recipes

```bash
# Full local gate (what CI will run)
cd services/server        && ./mvnw -B test
cd ../Android    && ./gradlew.bat testDebugUnitTest
cd ../web-admin  && npm ci && npm test -- --watch=false --browsers=ChromeHeadless && npm run check:motion

# A single area
./gradlew.bat testDebugUnitTest --tests "*MasterSyncProcessor*"
cd services/server && ./mvnw test -Dtest='*Sync*Test'
```

When reporting results, state **pass/fail counts and what was excluded** — a green
filtered run is not a green full run.
