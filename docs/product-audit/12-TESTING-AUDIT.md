# 12 — Testing Audit

## 1. Inventory [V]

| Surface | Test files | Main files | Notes |
| --- | --- | --- | --- |
| Server (Java) | 143 | 353 | JUnit + Spring Boot test; includes webhook, identity and business-rule suites |
| Android (Kotlin) | 97 across `src/test` + `src/androidTest` | 261 | the instrumented tier is 30 files per `android-ci.yml`'s own comment |
| Web Admin (TypeScript) | 46 spec files | 30 page components | suite reported green at 350/350 on 2026-11-13 (`NEXT_ACTIONS.md` item 7) |

A 143/353 server ratio and 46 specs over 30 pages are healthy for a project of this age. Test *quantity* is not the problem here.

## 2. What was actually executed during this audit [V]

| Check | Result |
| --- | --- |
| `./gradlew :app:compileDebugKotlin --offline` | **PASS** (after the AUD-001 repair; it failed before) |
| `./gradlew :app:testDebugUnitTest --offline` | **PASS**, Gradle exit status 0 (captured via `PIPESTATUS`, not the pipe's exit) |
| `./mvnw -B test` (server) | **not run** — outside this audit's time budget |
| `ng test` (web admin) | **not run** |
| `connectedDebugAndroidTest` | **not run** — no emulator/device |

So: the Android unit tier is green on this commit. Server and web suites are reported green by the project's own records but were not re-verified here.

## 3. The significant finding: a red build reached `main` [I]

`ci.yml` runs on **every push to `main` and every PR**, and its `android` job executes `./gradlew testDebugUnitTest` — which must compile all main sources. Commit `8a600882` contains a hard Kotlin syntax error, three unresolved references and a call to a non-existent composable. Therefore that job **could not have passed**. It reached `main` anyway.

Three possible explanations, and all three are actionable:

1. **The check was red and the merge/push ignored it** (no branch protection on `main`).
2. **Actions are disabled or not running** for the repo (fork, billing, permissions).
3. **The workflow failed to start** for a non-code reason (e.g. missing `google-services.json` handling — though `ci.yml` synthesises it).

The CI-run status was not retrievable from this environment, so the *cause* is inferred **[I]** while the *outcome* is certain **[V]**: a tree that does not compile was on `main` when this audit started.

**Recommendation, in order of cost:**
1. Enable branch protection on `main` requiring the `CI` workflow's three jobs.
2. If Actions cannot run, add a local pre-push hook running `./gradlew :app:compileDebugKotlin --offline` (~20s warm) — that single check would have caught AUD-001.
3. Keep the `--offline`-free, full test command in CI; the fast compile check is a local convenience, not a replacement.

## 4. Coverage gaps by risk (not by percentage)

| Risk area | Tests | Assessment |
| --- | --- | --- |
| Login identity / OTP mutation | `UserServiceEmailUpdateTest`, `BusinessWriteServiceProfileIdentityTest`, `EmailOtpViewModelTest` | **Good** — the highest-risk authorisation change is directly tested |
| Payments / webhooks | `EasebuzzWebhookTest`, `EasebuzzWebhookServiceTest` (explicitly re-included, not excluded, in `ci.yml`) | **Good**, with the caveat that no sandbox run occurred here |
| Migrations | `PostgresMigrationSmokeTest` run in `gated-tests.yml` via Testcontainers, plus Android migration tests | **Good on server**, **non-blocking on Android** (AUD-007) |
| Multi-device invoice sync | `MultiDeviceInvoiceSyncIntegrationTest` (gated job) | **Good** |
| Tenant isolation | no cross-tenant test found per module | **Gap** — the highest-severity architectural risk has no executable guard (AUD-003) |
| Settings/OTP UI logic | none (rules live inside a composable) | **Gap** (AUD-013) |
| Web-admin visual contracts | 10 `check:*` scripts + responsive/refresh specs | **Strong** |

## 5. AUD-007 (P2) — an entire test tier is decorative [V]

`android-ci.yml`'s `android-instrumented-tests` job runs `connectedDebugAndroidTest` with:

```yaml
continue-on-error: true
```

and the surrounding comment states the suite "has never been executed, so the first run is expected to be red… Once it is green, delete the continue-on-error line."

Implication: 30 instrumented test files — covering menu config, part payment, new bill, KDS and migrations — provide **no protection today**. Worse, their existence in the tree suggests coverage that does not exist (this is why `03-FEATURE-INVENTORY.md` marks card/split tender and quick bill as Unverified).

**Recommendation:** run the job once and triage the real result, then either fix or delete the failing tests. Do not leave a permanently-skipped suite in place; delete the `continue-on-error` line as the workflow itself instructs.

## 6. Duplicate gates [V]

| Workflow | What it runs | Overlap |
| --- | --- | --- |
| `ci.yml` | server tests, Android unit tests, Angular tests + `check:motion` | baseline gate |
| `android-ci.yml` | Android unit tests **again**, lint, instrumented (non-blocking) | duplicates `ci.yml`'s Android job |
| `web-admin.yml` | `npm ci`, `ng test`, `check:motion`, `npm run build` | duplicates `ci.yml`'s web-admin job, adds a production build |
| `gated-tests.yml` | Testcontainers migration + multi-device sync | unique |
| `guards.yml` | credential-file guard, web-admin state guards | unique |

**Recommendation:** one workflow per surface. Keep `ci.yml` as the required gate; reduce `android-ci.yml` to the instrumented job; keep the production build from `web-admin.yml` (it is the only place `ng build` runs).

## 6b. Execution results after the repairs (2026-11-13)

| Command | Result |
| --- | --- |
| `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --offline` | **PASS**, Gradle exit 0 |
| `./mvnw -o -DskipTests compile` | **PASS**, exit 0 (failed before the `UserServiceImpl` repair) |
| `./mvnw -o test -Dtest=…` (auth, notification, identity, sync, terminal classes) | **PASS** for every class re-run individually |
| `./mvnw -o test` (full suite, 955 tests) | **Not completed** — exceeds the 10-minute command cap. Before the fixes: 6 failures + 5 errors. The failures were traced to one P0 (`getSubscriptionSummary` inserting inside the pull's read-only transaction), one stale Google-lock test, two `phone_number` assertions, and stale 5-terminal expectations. |

**Final full-suite result (completed after the fixes):** `Tests run: 955, Failures: 2, Errors: 0, Skipped: 6`.

Both remaining failures are the same symptom and are **test-isolation defects, not product defects**:

- `ConcurrentBillPushTest.billPush_paymentStatus_lwwBehavior:252` — `Status expected:<200> but was:<401>`
- `TerminalAccessControlGapTest.kotOnlyTerminal_stillPushesBills:177` — `Status expected:<200> but was:<401>`

Run as `-Dtest='ConcurrentBillPushTest,TerminalAccessControlGapTest'` they **pass** (exit 0). They only fail in the full run, so a class executed earlier leaves shared state behind: the most likely candidates are a bumped `RestaurantTerminal.credentialVersion` (recovery/replacement increments it, which revokes previously minted tokens) or the token-revocation cache, on top of re-used restaurant ids across classes. Next step is a bisect, then `@DirtiesContext`/unique ids on the polluter — not a product change.

`TerminalManagementConcurrencyTest` was fixed in the same pass: `BaseIntegrationTest` uses `ddl-auto=create` and skips Flyway, so no plan rows exist; the class now creates its own 5-terminal `ENTERPRISE` fixture plan instead of relying on the default entitlement.

## 7. Recommendations, ordered

1. Make `main` unmergeable when `CI` is red (AUD-001 recurrence is the single biggest process risk).
2. Add cross-tenant tests per module — the guard for AUD-003.
3. Triage or delete the instrumented suite; remove `continue-on-error` (AUD-007).
4. De-duplicate workflows (AUD-019).
5. Move save/validation rules out of composables so they can be tested (AUD-013).
