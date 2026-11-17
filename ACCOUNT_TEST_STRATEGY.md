# ACCOUNT TEST STRATEGY

**Scope:** Phase 7 — how the identity work is proven, layer by layer.
**Status:** Mixed. The items marked `[IMPLEMENTED]` exist and were executed on 2026-11-17; the rest are
specified for approval.
**Date:** 2026-11-17

---

## 1. Principle

Identity bugs are not logic bugs, they are **state** bugs: the wrong row, the missing row, two rows, or
a row that should have been rejected. In-memory mocks cannot have state that disagrees with the
database, so a mock-based test can pass while production returns 500. That is exactly how RC-1 hid
(`googleLogin_ownerAccount_isAllowed` stubs a `login_id` the real schema would never hold).

Therefore: **every identity guarantee must be proven against real PostgreSQL.** Mocks may cover
branching, never uniqueness.

---

## 2. Layers and current coverage

| Layer | Tool | Exists today | Runs in CI |
| :-- | :-- | :-- | :-- |
| Unit (branching) | JUnit + Mockito | 137 test classes; `AuthServiceImplTest` covers signup/login/surface rules | ✅ `ci.yml` |
| **DB integrity** | JUnit + Testcontainers + Flyway | **`[IMPLEMENTED]` `PhoneIdentityIntegrityTest`** — 5 tests | ✅ (`ci.yml` → `mvn test`) |
| Concurrency | JUnit threads + real DB | **`[IMPLEMENTED]`** inside the above (8 parallel inserts) | ✅ |
| Integration (HTTP) | `@SpringBootTest` + Testcontainers | ✗ none for auth | — |
| Client contract | Android `CheckUserContractTest` pattern | partial | ✅ android-ci |
| Security | targeted probes | ✗ | — |
| E2E | manual / Playwright for web-admin | ✗ for auth | — |

---

## 3. What the new database test proves

`services/server/src/test/java/com/khanabook/saas/identity/PhoneIdentityIntegrityTest.java`
applies all 104 migrations to a throwaway `postgres:16-alpine`, then:

| Test | Guarantee | Why it matters |
| :-- | :-- | :-- |
| `secondAccountWithSamePhone_isRejectedByTheDatabase` | second insert fails | the safety net is the index, not the pre-check |
| `phoneFormatVariants_collideOnTheDigitsIndex` | `+91…` and `0…` collide | V105's generated column is what stops format duplicates |
| `concurrentSignupsWithSamePhone_leaveExactlyOneRow` | 8 threads → 1 row, 7 rejections | kills the check-then-insert race |
| `softDeletedAccount_canReleaseItsNumber` | number reusable after release | guards the non-destructive delete rule |
| `caseVariantEmails_areRejected` | **`@Disabled`** | pins RC-2 as intent; enables on decision D-4 |

Executed result: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 1`.

**Operational note:** after renaming any migration, `mvn clean` is required. Maven's resource copy does
not delete removed files, so `target/classes/db/migration` can retain a superseded migration and
reproduce the duplicate-version failure the source no longer has. This was observed during this work.

---

## 4. Required tests before the identity fix can ship

### 4.1 Unit — identity normalisation
- phone: bare 10-digit, `+91`, leading `0`, spaced, `null`, `""`, `"   "`.
- email: mixed case, surrounding whitespace, `null`, `""`.
- assertion: normalisation is idempotent (`normalize(normalize(x)) == normalize(x)`).

### 4.2 Unit — resolver decisions
- verified phone + no email → resolves to the phone account.
- unknown identifier → creates, exactly once.
- identifier owned by a *different* tenant → `LINK_REQUIRED`, never adoption.
- two accounts claimable → deterministic, explicit outcome (no `IncorrectResultSizeDataAccessException`).

### 4.3 Integration — API
- `POST /auth/signup` happy path → 200, one tenant.
- duplicate → 409 `ACCOUNT_EXISTS` (after the §4 contract unification).
- soft-deleted number → allowed.
- `POST /auth/google` for an existing phone account → **original tenant** or `LINK_REQUIRED`.
- `POST /auth/google` twice, consecutively → one account.
- transaction rollback: force the user insert to fail, assert no profile and no subscription remain.

### 4.4 Concurrency
- 10 parallel signups, same phone → 1 row. *(covered)*
- 10 parallel signups, same email → 1 row.
- 2 parallel Google logins, same token → 1 account.
- parallel signup while a staff creation takes the same number → one wins, no orphans.

### 4.5 Regression
- existing owner logs in with the same credentials → same `restaurantId` as before.
- staff retains permissions after the change.
- master-sync pull returns unchanged counts for a sample tenant.
- bills, subscriptions, terminals untouched (row-count assertions per tenant).

### 4.6 Security
- owner A cannot read owner B's bills/staff/menu by id or by tenant in the body → 403/404, never 200.
- OTP: wrong number invalidates the challenge; 6th attempt rejected; expired OTP rejected.
- refresh-token reuse after rotation rejected.
- `/auth/check-user` behaves identically for known and unknown numbers except for the boolean.

### 4.7 E2E
- Android: signup → login → sync.
- Web admin: login → dashboard → staff list.
One path each; identity is bulk-tested below E2E.

---

## 5. Test data (fixtures)

A deterministic fixture set must exist for every layer:

| Fixture | Purpose |
| :-- | :-- |
| phone-only owner, no email | the RC-1 case |
| Google-only owner | the split case |
| owner with both providers (post-fix) | the linked case |
| staff member | role enforcement |
| soft-deleted user holding a number | release rule |
| second tenant with bills | cross-tenant isolation |

No production data in tests. The `PhoneIdentityIntegrityTest` uses a fictional number.

---

## 6. Gates

| Gate | Where | Blocks |
| :-- | :-- | :-- |
| `ops/check-migrations.sh` | `guards.yml` | duplicate/malformed migration versions |
| `mvn test` (incl. Testcontainers) | `ci.yml` | identity regressions |
| Android unit tests | `android-ci.yml` | client contract drift |
| Web-admin specs | `web-admin.yml` | console regressions |
| OpenQodex review | pre-push hook + `AGENTS.md` | unreviewed change |

---

## 7. Definition of done for a test

A test counts only if:
1. It fails when the guarantee is removed (verified by temporarily breaking it, once, during the PR).
2. It asserts behaviour, not implementation.
3. It states *why* the guarantee matters in a comment — a future engineer must know what breaks if
   the assertion is relaxed.
4. It runs in CI, not only locally.
