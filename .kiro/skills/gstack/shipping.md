# Garry Tan's Shipping Workflow

## Trigger Conditions
- User says "ship", "deploy", "release", "land it", "push to prod"
- User asks to review code before merging
- User wants a structured plan-to-deploy pipeline
- Feature branch is ready for integration

## Philosophy
Ship fast, ship safe. One-command workflows that enforce quality gates.
No half-measures — every ship goes through: Plan → Code → Review → QA → Land.

---

## Commands & Workflows

### 1. `plan-review` — Validate the plan before coding
**When:** Before writing any code for a new feature/fix.

```
Steps:
1. Read the spec/issue/ticket
2. Identify affected modules (android/, server/, admin/)
3. List files to create/modify
4. Identify risks (breaking changes, migrations, API contracts)
5. Write a 5-line summary of approach
6. Get sign-off (self or peer)
```

**KhanaBook Example:**
```markdown
Feature: Add UPI payment split for sub-merchants
Modules: server/payments, android/checkout
Files: PaymentSplitService.kt, V80__add_split_config.sql
Risks: Easebuzz API contract change, existing merchant data migration
Approach: Add split_config table, extend PaymentService, update Android checkout flow
```

### 2. `code-review` — Five-axis review before merge
**When:** PR is ready, all tests pass locally.

```
Steps:
1. Run lint + format check (ktlint, checkstyle)
2. Run full test suite (./gradlew test, mvn test)
3. Self-review against 5 axes: Correctness, Readability, Architecture, Security, Performance
4. Check for hardcoded values, missing error handling, N+1 queries
5. Verify API contracts haven't broken
6. Add PR description with context
```

### 3. `ship` — Build release artifacts
**When:** Code review approved, ready to build.

```
Steps:
1. Bump version in build.gradle / pom.xml
2. Generate changelog from commits since last tag
3. Build release artifacts:
   - Android: ./gradlew bundleRelease
   - Server: mvn package -DskipTests
4. Sign AAB with upload keystore
5. Upload ProGuard mapping to Firebase Crashlytics
6. Tag commit: git tag -a v{version} -m "Release v{version}"
```

### 4. `qa-test` — Structured QA pass
**When:** Artifacts are built, before deploying.

```
Steps:
1. Install on test device / deploy to staging
2. Run smoke tests (login, create bill, sync, payment)
3. Test offline scenarios (airplane mode → create bill → reconnect)
4. Test edge cases (zero items, max items, special characters)
5. Verify analytics events fire correctly
6. Check crash-free rate on Firebase
7. Performance: cold start < 2s, bill creation < 500ms
```

**KhanaBook Smoke Test Checklist:**
- [ ] Login with phone OTP
- [ ] Create new bill with 5+ items
- [ ] Apply discount and tax
- [ ] Process UPI payment
- [ ] Sync bill to server
- [ ] Print KOT
- [ ] View daily report

### 5. `investigate` — Debug production issues
**When:** Crash spike, user report, or anomaly detected.

```
Steps:
1. Check Firebase Crashlytics for stack traces
2. Check server logs: kubectl logs -f deployment/khanabook-api
3. Identify affected version and user segment
4. Reproduce locally with same data conditions
5. Write failing test that captures the bug
6. Fix and verify test passes
7. Fast-track through ship pipeline
```

### 6. `land-and-deploy` — Final deployment
**When:** QA passed, ready for production.

```
Steps:
1. Merge PR to main (squash merge preferred)
2. CI/CD pipeline triggers automatically
3. Server: Rolling deploy to production cluster
4. Android: Upload AAB to Play Console
5. Staged rollout: 1% → monitor 24h → 10% → monitor 48h → 100%
6. Monitor crash-free rate (target: >99.5%)
7. If crash rate spikes: halt rollout, investigate
8. Post-deploy: update CHANGELOG.md, close tickets
```

---

## Anti-patterns
- ❌ Shipping without running tests locally
- ❌ Skipping plan-review for "small" changes (small changes cause big bugs)
- ❌ Force-pushing to main
- ❌ Deploying on Fridays without monitoring coverage
- ❌ Skipping staged rollout for "safe" changes
- ❌ Not reading crash reports for 48h post-deploy

## Verification Checklist
- [ ] Plan reviewed and risks identified
- [ ] All tests pass (unit + integration)
- [ ] Code review completed (5-axis)
- [ ] QA smoke tests pass on physical device
- [ ] Version bumped and tagged
- [ ] Staged rollout configured
- [ ] Monitoring dashboards checked 24h post-deploy
