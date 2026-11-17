# ACCOUNT DEPLOYMENT AND ROLLBACK

**Scope:** Phase 9 — how the account changes reach production without an outage, and how each step is
undone.
**Status:** Plan. Nothing here has been deployed.
**Date:** 2026-11-17

---

## 1. Current deployment state (verified today)

| Fact | Evidence |
| :-- | :-- |
| Production schema is at **V121** | `flyway_schema_history` tail |
| Production V110 = `add sub merchant restaurant format` | same |
| The working tree had a **second V110**, uncommitted | `git status` → `??` |
| A jar built from that tree **cannot boot** | `FlywayException: Found more than one migration with version 110` |
| That collision has been fixed: renamed to **V122** | `ops/check-migrations.sh` now passes |
| **V122 cannot be applied to production data** | 20 `device_id` groups hold 45 live shops (25 surplus); a unique index would be refused. Since 2026-11-17 the migration warns instead of failing, and `ops/verify-schema-invariants.sh` reports the missing index at every deploy |
| Three source files are modified but uncommitted | `feature/auth`, `restaurants/data` |

---

## 2. Blocking issue #1 — V122 will fail at boot

`V122__prevent_duplicate_device_restaurants.sql` is a bare:

```sql
CREATE UNIQUE INDEX uq_restaurantprofiles_live_device
    ON restaurantprofiles (device_id)
    WHERE is_deleted = false AND (is_suspended = false OR is_suspended IS NULL);
```

**Do not deploy this migration to production.** It will raise `could not create unique index … Key
(device_id)=(…) is duplicated`, Flyway runs migrations inside the application's bootstrap, and the
result is a **failed startup**, i.e. a full API outage — not a skipped migration.

Three safe options, in preference order:

1. **Drop the constraint; keep the signup guard.** `rejectSecondShopOnSameInstall` already prevents the
   behaviour. Also correct the product rule (16 real businesses legitimately share a device).
2. **Make it conditional, copying V107's pattern**: only create the index when no duplicate group
   exists, otherwise `RAISE WARNING` and continue. A migration must never be able to stop the API.
3. **Clean the data first** (§ `ACCOUNT_REMEDIATION_PLAN.md` R2.3), then apply the index. Slowest, and
   still loses the 16 legitimate cases.

---

## 3. Deployment ordering (when the identity work is approved)

The sequence is chosen so that at no point does a running client depend on something not yet deployed.

| Step | What | Reversible? |
| :-- | :-- | :-- |
| D1 | Migration only: add `user_identities` (+ `verified_at`) and backfill from existing rows. No code reads it yet. | yes — drop table |
| D2 | Code: read-through resolver that prefers `user_identities` and falls back to today's chains, behind a flag. | yes — flip flag |
| D3 | Code: Google path uses the resolver; returns `LINK_REQUIRED` instead of creating when ambiguous. | yes — flip flag |
| D4 | Code: unified duplicate contract (`ACCOUNT_EXISTS`, `LINK_REQUIRED`). Android `BackendErrorParser` updated in the **same** release. | yes — flag |
| D5 | Write-through: creation paths also populate `user_identities`. Legacy columns remain the projection. | yes |
| D6 | Contract: stop writing the legacy chain (only after 3 releases of D5 with zero fallback hits). | EOL, not rollback |

Rule: **no DDL in the same step as the code that depends on it**, and **no step that requires every
client to update at once**.

---

## 4. Backward compatibility

| Constraint | Why |
| :-- | :-- |
| JWT claim shape unchanged (`sub`, `restaurantId`, `role`, `deviceId`) | every live Android install would otherwise be forced to re-authenticate |
| `/api/v1` paths unchanged | older APKs pin them |
| Status codes for existing conditions unchanged | clients branch on 400/401/403 today |
| `users` columns retained as a projection | sync payloads (`UserSyncDto`) still carry them |
| Sync endpoint contracts unchanged | POS workers push whole rows |

Known **intended** breaking change: duplicate registration moves 400 → 409 (FR-9). Must ship with the
client change, in the same release window.

---

## 5. Pre-deployment checklist

1. `ops/check-migrations.sh` passes. *(now a CI gate)*
2. `mvn clean test` green — **`clean` matters**: a renamed migration stays in `target/classes`
   otherwise, and reproduced the duplicate-version failure during this work.
3. Migrations applied to a **restored production copy**, timed, with duplicate-key failures checked.
4. Rollback script written and exercised on that copy.
5. Backup taken in the change window, restore drilled.
6. Monitoring watched: signup 5xx rate, `409` rate, login 5xx, errorId volume.
7. On-call informed; deploy window outside peak billing hours.
8. OpenQodex review run and its findings resolved or explicitly accepted (`AGENTS.md`).

---

## 6. Rollback plan

| Failure | Immediate action | Data impact |
| :-- | :-- | :-- |
| Application won't start (migration error) | redeploy previous image; **do not** repair the migration on a live DB | none — Flyway rolled its transaction back |
| Post-deploy: login 5xx spike | flip resolver flag off; redeploy | none |
| Post-deploy: duplicate tenants still created | flag off; the old path creates, which is the known behaviour, no worse | none |
| Post-deploy: cross-tenant access suspected | revoke all sessions (`/business/sessions/revoke-all` per tenant, or rotate `JWT_SECRET` to invalidate every token) | sessions only |
| Data correction gone wrong | restore the affected tables from the change-window dump | bounded to those tables |

`JWT_SECRET` rotation is the nuclear option: it invalidates every token instantly at the cost of forcing
all users to log in again. Acceptable in a security incident; not for a normal rollback.

---

## 7. Post-deployment verification

1. New signup → one user, one tenant, one profile, one trial subscription.
2. Repeat signup → `ACCOUNT_EXISTS`.
3. Existing owner login → same `restaurantId` as before the deploy (recorded pre-deploy).
4. Staff login → permissions intact.
5. Master-sync pull for a sample tenant → counts unchanged.
6. Spot-check an affected device group → survivor intact, retired tenant hidden, bills preserved.

---

## 8. What must not happen

* Deploying `V122` as currently written (boot failure).
* Editing an already-applied migration (checksum drift; the `ops/flyway_repair_*` scripts exist because
  this has happened before).
* Deleting or merging production rows without written approval.
* A deploy from a dirty working tree.
