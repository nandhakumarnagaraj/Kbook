# Release History

**Purpose:** the record of what is running in production, and what it can be rolled back to.
**Rule:** a production deploy without an entry here did not happen.

Until 2026-11-17 there were **no tags at all** — `git log` was the only history, and the running
container was built from a working tree. This file starts the record.

---

## v2026.11.17-1 — first tagged rollback point

| | |
| :-- | :-- |
| **Tag** | `v2026.11.17-1` (annotated) |
| **Commit** | `0c1b3ce6` — `feat(web-admin): modernize dashboard (control flow, native dialogs, perf)` |
| **Branch** | `main` |
| **Schema version** | `v121` |
| **Runtime** | `kbookiadvcloud-server-1` + `kbookiadvcloud-postgres-1` (Compose project `kbookiadvcloud`) |
| **Public entry** | nginx → `/var/www/kbook.iadv.cloud` |
| **Tagged** | 2026-11-17 |

**Why this commit:** it is what production is actually running, and it boots cleanly against all 104
migrations. It exists so there is something to return to — not because it is defect-free (KB-001 and
KB-010 are open against it).

**Verified at tag time**

| Check | Result |
| :-- | :-- |
| `/api/v1/actuator/health` | 200 |
| web admin `/` | 200 |
| pgAdmin `/pgadmin4/` | 302 → login (public by owner decision) |
| Containers | both `Up 2 days (healthy)` |
| Migration head | v121 |
| Restore drill from nightly dump | 55 tables, 165 users, 161 profiles, 2,179 bills, 0 errors |

**Rollback:** `docker compose -p kbookiadvcloud up -d` from this tag, or redeploy the image built from
`0c1b3ce6`. Schema is unchanged by the tag, so no down-migration is required.

---

## Infrastructure changes on the same date

Not a code release, but production-affecting and therefore recorded:

| Change | Detail |
| :-- | :-- |
| nginx | pgAdmin allowlist removed (owner policy: public sites, login is the gate) |
| nginx | backup files moved out of `sites-enabled/` → `/etc/nginx/backups-offline/` (they were being loaded as live server blocks) |
| nginx | `/api/v1/actuator/prometheus` restricted to loopback; 403 verified off-host |
| systemd | `apache2` disabled; vhosts relocated to `/etc/apache2/sites-enabled.offline-20261117/` |
| packages | `fail2ban` enabled (`sshd`, `xrdp-sesman` jails), `restic` installed |
| docker | two orphaned `github-mcp-server` containers removed |
| docs | deployment runbook corrected: Apache → nginx |
| migration | `V122` made non-fatal (guarded, V107-style) — **not yet applied to production** |

---

## Template for the next entry

```markdown
## vYYYY.MM.DD-N

| | |
| :-- | :-- |
| **Tag** | |
| **Commit** | |
| **Schema version** | |
| **Deployed by** | |
| **Migrations included** | |
| **Backup taken** | file + verify result |

**Changes shipped**
- …

**Verified after deploy**
- health · login · signup · one billing path · sync

**Rollback**
- previous tag: …
- schema: reversible? yes/no — how

**Known issues carried forward**
- KB-…
```
