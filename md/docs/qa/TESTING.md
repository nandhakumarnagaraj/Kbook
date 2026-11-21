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
| Android screenshots (design system) | `apps/android/` | `./gradlew.bat :app:verifyRoborazziDebug` (record: `:app:recordRoborazziDebug`) | 6 goldens, host-side |
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
| `android-ci.yml` | push | `android-lint`, `android-instrumented-tests` (non-blocking), `android-screenshot-tests` (non-blocking) |
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
| `.githooks/pre-push` | blocks only when OpenQodex returns exit 1; otherwise it reports. See below. |

### Pre-push review gate

`core.hooksPath` is `.githooks`, so every `git push` runs OpenQodex's
`hook pre-push`. The hook resolves the binary from `PATH`, then
`$HOME/.openqodex/bin`, then the pinned `npx openqodex@0.16.0` release — a
hardcoded `/root/...` path silently skipped the gate on Windows checkouts.

A review is keyed to **the change OpenQodex saw**. A review taken while the
working tree holds uncommitted files covers those files too, so its change id can
never match a push that carries commits only, and the hook prints *"has not
reviewed this change"*. That is expected in this repository whenever other work
is uncommitted; the hook now says so instead of leaving a bare warning.

To get a receipt that actually matches a push:

```bash
git status --short            # commit or stash the unrelated work first
npx -y openqodex@0.16.0 review      # or: review --agent, when no reviewer is installed
# …fix the findings, finalize the receipt, then:
git push origin main
```

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

## 7. Design-system screenshot tests

`apps/android/app/src/test/java/com/khanabook/lite/pos/designsystem/DesignSystemScreenshotTest.kt`
renders the KhanaBook design-system components inside the real `KhanaBookLiteTheme` and
compares the pixels against committed PNGs in `apps/android/app/src/test/screenshots/`.

This is the only automated enforcement of `docs/meta/ANDROID_UI_RULES.md` and the Android
section of `docs/meta/AGENTS.md`: dark-only palette, gold accents, token spacing, `KhanaRadii`
shapes, responsive type tiers, semantic status colours.

| Action | Command |
|---|---|
| Verify (CI gate) | `./gradlew.bat :app:verifyRoborazziDebug` |
| Re-record after an intended UI change | `./gradlew.bat :app:recordRoborazziDebug` |
| Diff report on failure | `apps/android/app/build/reports/roborazzi/index.html` |

**Rules.** A failing verify means the pixels changed. Read the diff, then either fix the
regression or re-record **in the same commit as the code change**. Re-recording to silence a
diff you have not read destroys the gate.

**Caveats — do not over-trust a green run:**

1. Goldens are rendered by Robolectric on **SDK 34** (`app/src/test/resources/robolectric.properties`).
   Robolectric 4.15.x has no SDK 36 runtime, so Android 15/16-only behaviour is not covered.
2. **Poppins is not present.** `core/theme/Type.kt` resolves it through Google Fonts
   (downloadable), which Robolectric cannot fetch, so goldens render the platform fallback
   typeface. These tests lock layout, colour, spacing and shape — **not glyph metrics**.
   Font regressions still require a device or emulator.
3. Host-side rendering can differ slightly across operating systems. Goldens were recorded on
   Windows; if the Linux CI job disagrees on the first run, re-record there and commit — the
   gate is only meaningful once the recording platform matches CI.

