# ACCOUNT IMPLEMENTATION STATUS

**Scope:** Honest status of the account-lifecycle programme against the original 10-phase brief.
**Date:** 2026-11-17

This file exists so that nobody mistakes a documented plan for delivered code. It is updated whenever
something moves.

---

## 1. Phase status

| Phase | Name | Status | Deliverable |
| :-- | :-- | :-- | :-- |
| 1 | Complete system discovery | ✅ **Done** | `ACCOUNT_SYSTEM_ARCHITECTURE_AUDIT.md` |
| 2 | Trace every creation path | ✅ **Done** | `ACCOUNT_CREATION_FLOW_ANALYSIS.md` |
| 3 | Root cause analysis | ✅ **Done** | `ACCOUNT_DUPLICATION_ROOT_CAUSE.md` |
| 4 | Multi-tenant identity architecture | ✅ **Done** | `ACCOUNT_IDENTITY_ARCHITECTURE.md` (options, decisions pending) |
| 5 | Solution design | 🟡 **Partial** | Options A/B written in the Phase 4 doc + `docs/adr/0002`. **Blocked on D-1…D-9.** |
| 6 | Duplicate data investigation | ✅ **Done** (investigation) | `ACCOUNT_DUPLICATE_DATA_REPORT.md` · remediation is `ACCOUNT_REMEDIATION_PLAN.md` (not executed) |
| 7 | SDLC implementation | 🟡 **Gates only** | Requirements + test strategy approved-for-review; **no identity code written** |
| 8 | Code quality | ⏸ **Not started** | — |
| 9 | Deployment strategy | ✅ **Done** (plan) | `ACCOUNT_DEPLOYMENT_AND_ROLLBACK.md` |
| 10 | Documentation | ✅ **Done** | all 12 files present |

Legend: ✅ done · 🟡 partial/blocked · ⏸ not started

---

## 2. What was actually implemented (code / config)

Deliberately small, because R-1 says the identity fix is not approved.

| Change | File | Why it was safe to do |
| :-- | :-- | :-- |
| Migration version guard | `ops/check-migrations.sh` (new) | read-only analysis of filenames |
| CI job for the guard | `.github/workflows/guards.yml` | adds a check, changes nothing |
| **Fixed an unbootable build** | `V110__…` → `V122__…` (rename, content unchanged, `f934fc98…`) | the file was untracked; never applied anywhere |
| DB-level identity tests | `services/server/.../identity/PhoneIdentityIntegrityTest.java` (new) | test only |
| PII out of the working tree | `.gitignore` (+`.backup-*/`, `*.csv`, `.audit-env/`) | prevents accidental commit; deletes nothing |
| ADR for identity direction | `docs/adr/0002-account-identity-resolution.md` (new) | documentation, status `proposed` |
| pgAdmin made public | `/etc/nginx/sites-enabled/kbook.iadv.cloud` + `ops/nginx-kbook.iadv.cloud.conf` | explicit owner instruction; backup retained |
| **`V122` made non-fatal** | `V122__prevent_duplicate_device_restaurants.sql` | a migration must never be able to stop the API; guard mirrors V107 |

### 2b. Infrastructure hardening (2026-11-17, second pass)

| Change | Detail | Reversible by |
| :-- | :-- | :-- |
| First release tag | `v2026.11.17-1` → `0c1b3ce6` (schema v121) | `git tag -d` |
| `apache2` disabled | was `enabled` + `failed`; vhosts moved to `/etc/apache2/sites-enabled.offline-20261117/` | `mv` back + `systemctl enable` |
| Prometheus closed | exact-match nginx location, loopback only; 403 verified off-host | revert the two `location` blocks |
| nginx backup relocated | out of `sites-enabled/` (it was loaded as a live server block) into `/etc/nginx/backups-offline/` | `mv` |
| `fail2ban` installed | `sshd` + custom `xrdp-sesman` jail (no upstream filter exists for xrdp) | `systemctl disable --now fail2ban` |
| `restic` installed | `ops/offsite_backup.sh` written; **inert** until a bucket exists | `rm` the script |
| Orphan containers removed | two `github-mcp-server` instances from earlier agent sessions | n/a |

**Not implemented, on purpose:** no change to `AuthServiceImpl`, no schema change applied to
production, no production row touched, no credentials rotated (owner action), no off-site bucket created
(needs an external account).

---

## 3. Verification evidence (all run, not assumed)

| Check | Result |
| :-- | :-- |
| `ops/check-migrations.sh` before the fix | ❌ failed — `duplicate migration version 110` (proves the guard works) |
| `ops/check-migrations.sh` after the fix | ✅ pass — 104 files, highest V122, next free V123 |
| Application boot with real migrations | ✅ `Successfully applied 104 migrations … version v122`, `Started KhanaBookSaaSApplication` |
| `mvn -Dtest=PhoneIdentityIntegrityTest test` | ✅ `Tests run: 5, Failures: 0, Errors: 0, Skipped: 1` — **re-run after the V122 rewrite**, schema reached `v122` with the index created |
| `guards.yml` YAML validity | ✅ 4 jobs parse |
| `.gitignore` effect | ✅ `git check-ignore` matches the PII dump |
| pgAdmin public | ✅ `403 → 302 → login 200` |
| API tenant isolation | ✅ verified in code (staff/menu/billing filters, `TenantContext` from signed JWT) |
| CDN enumeration | ⚠️ confirmed public and enumerable (SEC-11), policy decision pending |
| V122 branch 1 — clean data | ✅ index created, app `Started` |
| V122 branch 2 — duplicates present | ✅ `WARNING`, exit 0, index skipped, rows untouched |
| V122 branch 3 — after cleanup, re-run | ✅ index created; second run idempotent, 0 errors |
| Restore drill from nightly dump | ✅ 55 tables, 165 users, 161 profiles, 2,179 bills, 0 SQL errors |
| Prometheus now internal | ✅ 403 from public source and from a container; health 200 as control |
| `fail2ban` jails | ✅ `sshd, xrdp-sesman` both active |
| `offsite_backup.sh` unconfigured | ✅ exits 78, never claims a false success |
| Tag correctness | ✅ `v2026.11.17-1^{commit}` = `0c1b3ce6` = HEAD |

---

## 4. Findings by severity

| Severity | Open | Resolved |
| :-- | :-- | :-- |
| Critical | RC-1 (duplicate tenant via Google) | **RC-3 (unbootable build) — fixed** |
| High | RC-2, RC-5, RC-7, SEC-1, SEC-2, SEC-3, SEC-8, SEC-12 | SEC-7 (PII in tree) — mitigated |
| Medium | RC-4, RC-6, RC-9, RC-10, RC-11, SEC-4, SEC-5, SEC-9 | — |
| Low | RC-8, SEC-6, SEC-10, SEC-11 | — |

New production findings surfaced during Phase 6: **20 device groups / 45 shops / 25 surplus** (17 with
bills; 14 of the groups are browser ids rather than hardware), and **V122 cannot apply to that data**
(`ACCOUNT_DUPLICATE_DATA_REPORT.md` §3, snapshot 2026-11-17 10:47 UTC).

---

## 5. Blocked on the owner

| Blocker | Needs |
| :-- | :-- |
| Phase 5 solution design | D-1, D-2, D-3 |
| RC-2 fix (email uniqueness) | D-4 |
| Device rule itself (is one shop per device even correct?) | D-7 |
| Any production remediation | written approval (R-1) + per-group owner confirmation |
| `/auth/check-user` | D-9 |
| Off-site backups (KB-011) | a bucket + a passphrase only the owner holds |
| Credential rotation (KB-013) | owner action — secrets from August still live |

---

## 6. Recommended next actions, in order

1. **Confirm D-8** — resolved provisionally by engineering (index created only when the data allows);
   the owner may still prefer dropping the constraint or cleaning the data first.
2. **Answer D-4** — unblocks a small, isolated, testable fix that removes a live 500.
3. **Answer D-1 and D-3** — the only Critical finding cannot move without them.
4. **Rotate the August credentials** (KB-013) — the one thing no agent can do for you.
5. **Provide a bucket + passphrase** so off-site backups can actually run (KB-011).
6. Run `npx -y openqodex@0.16.0 review` before the next push (`AGENTS.md`).

---

## 7. Change-set inventory (uncommitted)

```
M  .github/workflows/guards.yml
M  .gitignore
M  docs/runbooks/DEPLOYMENT.md                                   ← Apache → nginx correction
M  ops/nginx-kbook.iadv.cloud.conf
M  services/server/.../auth/controller/AuthController.java        ← pre-existing, not mine
M  services/server/.../auth/service/AuthServiceImpl.java          ← pre-existing, not mine
M  services/server/.../restaurants/data/RestaurantProfileRepository.java ← pre-existing, not mine
?? ACCOUNT_*.md (12)
?? docs/adr/0002-account-identity-resolution.md
?? docs/engineering/AI_AGENT_PRODUCTION_POLICY.md
?? docs/engineering/ENVIRONMENT_STRATEGY.md
?? docs/engineering/MIGRATION_SAFETY.md
?? docs/planning/ENGINEERING_BACKLOG.md
?? docs/releases/RELEASE_HISTORY.md
?? docs/runbooks/BACKUP_AND_RESTORE.md
?? docs/runbooks/INCIDENT_RESPONSE.md
?? docs/runbooks/MONITORING.md
?? ops/check-migrations.sh          ← now also reports destructive DDL
?? ops/offsite_backup.sh
?? ops/verify-schema-invariants.sh
?? services/server/.../db/migration/V122__prevent_duplicate_device_restaurants.sql
?? services/server/.../identity/PhoneIdentityIntegrityTest.java
```

Measured 2026-11-17: **31 entries — 7 modified, 24 untracked**. Of the 7 modifications, **3 pre-date
this work** (the three Java sources under `auth/` and `restaurants/data/`) and were not touched; the
other 4 are this work. `.backup-srcafe-*` is untracked and deliberately ignored, not modified. Nothing
is committed; the `v2026.11.17-1` tag is local-only until the first push.

The three pre-existing source modifications were already in the tree when this audit began and were
**not** changed by it.
