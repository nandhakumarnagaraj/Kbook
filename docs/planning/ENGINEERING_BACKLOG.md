# Engineering Backlog

**Purpose:** one authoritative ticket list. Every item is a defect, a risk, or a debt — not a wish list.
**Date:** 2026-11-17
**Source:** account-lifecycle audit (Phases 1–10) + VPS infrastructure audit.

Priority: **P0** outage/security now · **P1** this week · **P2** this month · **P3** planned · **P4** opportunistic.

Every item needs an owner before it is scheduled. Nothing here is authorised to be implemented by an AI
agent without the approval noted in its row.

---

## P0 — active production/security exposure

| ID | Problem | Impact | Acceptance criteria | Status |
| :-- | :-- | :-- | :-- | :-- |
| **KB-001** | Google sign-in cannot resolve a phone-registered owner, so it provisions a **second tenant** for that person | Duplicate businesses, orphaned trials, unreconcilable identities. 34 production users have `email = NULL` | Google login resolves to the existing account or returns `LINK_REQUIRED`; never silently creates a parallel tenant | **Blocked** on D-1, D-3 |
| **KB-002** | `V122` created a unique index unconditionally → would fail at bootstrap on existing duplicate data, i.e. **failed startup** | Full API outage on next deploy | Migration warns and continues when duplicates exist; index appears once data is clean | ✅ **Fixed 2026-11-17**, both branches verified |

---

## P1 — do this week

| ID | Problem | Impact | Acceptance criteria | Status |
| :-- | :-- | :-- | :-- | :-- |
| **KB-010** | `email` uniqueness is byte-wise but lookups are case-insensitive → **HTTP 500** on login | Users locked out by their own typing | One row per `lower(email)`; login never 500s on ambiguity | Blocked on D-4 |
| **KB-011** | Backups exist and restore correctly, but **never leave the VPS** | Disk loss or ransomware destroys data + backups together | Encrypted off-site copy, monthly restore from off-site proven | 🟡 **Tooling ready 2026-11-17** — `restic` installed, `ops/offsite_backup.sh` written and refusing to run unconfigured. **Blocked on a bucket + passphrase.** |
| **KB-012** | **No monitoring or alerting** of any kind | Outages discovered by customers | 5 minimum alerts live (`docs/runbooks/MONITORING.md` §6) | Open — no alert channel exists |
| **KB-013** | Credentials committed to git history per `SECURITY_ROTATION_REQUIRED.md` (2026-08-19) — **rotation still pending** 3 months later | DB/repo compromise path | All listed secrets rotated; doc marked done with date | Owner action |
| **KB-014** | **Zero release tags**; deploys are built from a working tree | No known-good version to roll back to | Every production deploy is a tagged, built image | ✅ **Done 2026-11-17** — `v2026.11.17-1` tags the running commit |
| **KB-015** | `apache2` was **enabled but failed** on every boot; nginx owns 80/443; Apache vhosts still on disk | A future `systemctl start apache2` contends for the ports | Apache disabled and its vhosts removed | ✅ **Done 2026-11-17** — disabled, vhosts moved to `/etc/apache2/sites-enabled.offline-20261117/` (packages kept for easy revert) |
| **KB-016** | ufw allows **3389**, driven by a live `xrdp` — *not* idle as first reported | Remote-desktop brute force | Protected against brute force; port still usable | ✅ **Done 2026-11-17, differently** — port **kept** (it is in use); `fail2ban` now bans repeated RDP auth failures |
| **KB-017** | ~~SSH root + password login~~ — **this was my error**: `sshd -T` shows `permitrootlogin without-password`, `passwordauthentication no`, `MaxAuthTries 3` | — | — | ✅ **Already satisfied** before this session (`99-kbook-hardening.conf`) |
| **KB-018** | `/api/v1/actuator/prometheus` returned **200 anonymously** | Leaks internals; not covered by the "public sites" policy | 403 to anonymous, scrape still works | ✅ **Done 2026-11-17** — 403 from off-host and from a container; 200 on the control endpoint |
| **KB-019** | Development happens **on the production host**; `deploy-production.sh` builds there | Untested code becomes live code (this is how KB-002 nearly shipped) | Staging exists; production only ever runs built artifacts | Plan: `ENVIRONMENT_STRATEGY.md` §5 |

---

## P2 — this month

| ID | Problem | Impact | Acceptance criteria | Status |
| :-- | :-- | :-- | :-- | :-- |
| **KB-020** | `findByAnyIdentifier` can match >1 row; filter swallows the exception and continues **unauthenticated** | Confusing 401s, possible auth bypass paths | Non-unique resolution fails loudly with 401 + WARN log | Open |
| **KB-021** | `/auth/check-user` is an existence oracle; the client comment claiming otherwise is **false** | Enumeration; misleading future developers | Endpoint removed, or documented as accepted and comment corrected | Decision D-9 |
| **KB-022** | Provider switch / identity change writes **no audit event** | No forensic trail for account takeover | Every identity mutation emits an audit record | Open |
| **KB-023** | **25 surplus live shops** across 20 `device_id`s (17 with bills; 14 groups are browser ids, not hardware) | Duplicate businesses; blocks any device constraint | Owner-confirmed reconciliation, soft-deleted, bills preserved | `ACCOUNT_REMEDIATION_PLAN.md` §4 |
| **KB-024** | **24 users** hold a phone number in the `email` column | Ambiguous identity, blocks a clean unique index | Documented, or migrated with a safe plan | Decision D-4 |
| **KB-025** | **77 of 161 live tenants have never billed** | Retention/onboarding failure at ~48% | Cohort triaged; cause identified | Open |
| **KB-026** | Auth 429 returns empty body and no `Retry-After`; limit is 5/min/IP across all `/auth/**` | Legitimate retries look like outages | JSON error body + `Retry-After`; sane budget | Open |
| **KB-027** | `/cdn/**` assets enumerable by predictable path, anonymous | Competitor can enumerate another shop's menu images | Signed URLs or random object names | Policy decision |
| **KB-028** | Two orphaned `github-mcp-server` containers left running by earlier AI sessions | Wasted resources, unclear provenance | Removed; agent containers scoped and cleaned up | ✅ **Done 2026-11-17** |
| **KB-029** | Backups are **unencrypted** plain SQL containing customer PII | PII exposure if a backup leaks | Encrypted at rest | Resolved for off-site (restic encrypts) — local dumps still plain, with KB-011 |
| **KB-047** | **22 `CREATE TABLE`, 83 `CREATE INDEX` and 8 `ADD COLUMN` statements lack `IF NOT EXISTS`** — safe under Flyway, but they produce `relation/column already exists` the moment one is applied by hand or after a history mishap | Schema work becomes error-prone and alarming instead of deterministic | **Resolved** — enforced from V123 by `ops/check-migrations.sh`; V1–V122 are checksum-frozen and not edited | `docs/engineering/MIGRATION_SAFETY.md` §3 |
| **KB-048** | **`spring.flyway.baseline-on-migrate=true`** on a populated database whose `flyway_schema_history` is lost makes Flyway replay every version against existing tables | A restore mistake turns into an `already exists` storm across the whole schema | **Resolved** — set to `false` in `application.properties`, with the explicit `flyway baseline` command in a comment | `docs/engineering/MIGRATION_SAFETY.md` §3 |

---

## P3 — planned

| ID | Problem | Acceptance criteria | Status |
| :-- | :-- | :-- | :-- |
| **KB-040** | No HTTP-level integration tests for auth | Signup/login/Google covered through the API against a real DB | Open |
| **KB-041** | No release history / changelog discipline | Every deploy recorded with SHA, tag, migrations, verification | Open |
| **KB-042** | Documentation drifts from reality (the deployment runbook documented Apache for weeks) | Docs corrected during the change that invalidates them | **One fixed 2026-11-17** |
| **KB-043** | `.backup-srcafe-20261113-175029/` (production PII CSVs) still sits in the repo tree | Moved outside the repo and destroyed per retention policy | Gitignored, not deleted |
| **KB-044** | Backup/config `.bak` files inside `sites-enabled/` get loaded by nginx `include *` | Backups live outside served config dirs | **Fixed 2026-11-17** (`/etc/nginx/backups-offline/`) |
| **KB-045** | No brute-force protection on 22/3389 | fail2ban installed with `sshd` + `xrdp-sesman` jails | ✅ **Done 2026-11-17** (`/etc/fail2ban/jail.local`) |
| **KB-046** | No release history recorded | Tagged releases logged with what shipped | ✅ **Started 2026-11-17** (`docs/releases/RELEASE_HISTORY.md`) |

---

## P4 — opportunistic

| ID | Problem |
| :-- | :-- |
| **KB-050** | Three products (KhanaBook, khanabook.in, dukaanlocker) share one host — one bad deploy affects all |
| **KB-051** | Legacy `khanabook.service` still enabled and active alongside Compose |
| **KB-052** | No dependency/vulnerability scanning (Trivy/Dependabot) in CI |
| **KB-053** | No load testing for the sync endpoint under POS burst |

---

## Explicitly out of scope

Recorded so they are not mistaken for requirements:

* Kubernetes / blue-green / canary — unjustified at this scale.
* Multi-region failover.
* Replacing the identity schema with a full person/credential/membership model (Option B) before D-1
  and D-2 are answered.
