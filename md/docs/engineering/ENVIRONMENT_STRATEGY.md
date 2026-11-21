# Environment Strategy

**Purpose:** separate development from production, so that "it works" and "it is live" stop being the
same statement.
**Date:** 2026-11-17
**Status:** Assessment complete. Staging does **not** exist yet; §5 is the approved-for-review plan.

---

## 1. Current state (measured on the live host, 2026-11-17)

This answers the infrastructure assessment directly. Every row is evidence, not assumption.

| # | Question | Actual answer | Evidence |
| :-- | :-- | :-- | :-- |
| 1 | VPS operating system | **Ubuntu 24.04.3 LTS**, kernel `6.8.0-149-generic` | `/etc/os-release`, `uname -r` |
| 2 | How applications run | **Docker Compose + nginx** for KhanaBook. Also on the host: a native `postgresql@16-main`, `pgadmin4.service`, and a legacy `khanabook.service` (Node/Nuxt, port 3000 path, `/var/www/khanabook.in`) still **enabled and active**. | `docker ps`, `systemctl list-units` |
| 3 | Source control | **GitHub**, `https://github.com/nandhakumarnagaraj/Kbook.git`, branch `main`. **Zero release tags.** | `git remote -v`, `git tag` |
| 4 | Production database | **PostgreSQL 16.15** in `kbookiadvcloud-postgres-1` (`postgres:16-alpine`). A **second, unrelated** Postgres 16 listens on host `127.0.0.1:5432` for other products. `dukaanlocker-postgres` is a third. | `select version()`, `ss -ltnp` |
| 5 | Staging environment | **None.** `ops/docker-compose.staging.yml` exists but is not deployed; there is no staging directory, database or host. | `ls /var/www` |
| 6 | Backup strategy | **Automated, local-only.** Nightly 21:00 cron → `ops/daily_backup.sh`: DB dump + CDN tarball, **verified** (55 tables, critical tables present), retention prunes old files. **No off-site copy, no `restic`, no encryption.** | `crontab -l`, `backups/cron.log` |
| 7 | Development workflow | **Mixed — the worst mode.** The repo root on the VPS *is* the deploy root; `ops/deploy-production.sh` builds the image **on the production host** from the working tree. | `ops/deploy-production.sh`, runbook |

### 1.1 Host facts that matter

| Fact | Value | Implication |
| :-- | :-- | :-- |
| CPU/RAM | 31 GiB RAM, 7.4 GiB used | headroom for a staging stack |
| Disk | 387 GiB, 86 GiB used (23%) | not a constraint |
| Public ports (ufw) | 22, 80, 443, **3389** | `3389` is RDP on a Linux host — almost certainly unnecessary |
| SSH | `PermitRootLogin yes`; `PasswordAuthentication` not disabled | root + password login reachable |
| Operator account | `ubuntu` (uid 1000) exists and is unused by this work | the basis for a proper deploy user |
| Agent privilege | work is performed as **root** | violates least privilege |
| Monitoring | **none** — no Sentry, Netdata, Uptime Kuma, Grafana, Prometheus agent | outages are discovered by customers |
| Products on this host | KhanaBook (`kbook`), `khanabook.in` (Node), `dukaanlocker`, `bhai` | shared blast radius; one bad deploy affects three products |

---

## 2. Why the current model is the core risk

```
laptop ──ssh──► PRODUCTION VPS
                  │  git pull / edit in place
                  │  mvn / npm build IN the deploy dir
                  │  docker compose build server      ← artifact created here
                  └─ docker compose up -d             ← users affected here
```

There is no point at which a change is exercised anywhere except in front of customers. Concretely,
this is how a duplicate `V110` reached a live artifact, where it would have failed **flyway at boot**
and taken the API down.

The existing `docs/runbooks/DEPLOYMENT.md` describes this flow accurately, including an out-of-date
detail: it documents **Apache** as the public entry. Apache is `failed`; **nginx** serves 80/443. That
runbook has been corrected as part of this change.

---

## 3. Target model

| Environment | Where | Database | Purpose |
| :-- | :-- | :-- | :-- |
| **Local** | Developer laptop | Docker Postgres | code, unit tests, fast iteration |
| **Staging** | separate VPS (preferred) or an isolated Compose project on this host | **its own** Postgres, restored from a production dump with PII masked | integration, migration rehearsal, release candidate validation |
| **Production** | this VPS | `kbookiadvcloud-postgres-1` | customers |

Rules:
1. **Staging never shares production credentials, database, or `.env`.**
2. **Production runs a built, versioned artifact** (`server:<git-sha>`), never a working tree.
3. **Migrations are rehearsed on a restored production copy** before they touch production.
4. **Rollback is always "redeploy the previous tag"**, not "revert by hand".

Recommended intermediate step, if a second VPS is not available this week: a staging Compose project
(`-p kbook_staging`) on this host with ports `8082` and `5544`, a restored DB, and nginx
`staging.kbook.iadv.cloud`. It is not isolation in the security sense (same kernel, same disk) but it
does catch migration and configuration failures before customers see them.

---

## 4. Configuration separation

| Concern | Production | Staging | Local |
| :-- | :-- | :-- | :-- |
| env file | `/var/www/kbook.iadv.cloud/.env` (never committed) | `/var/www/kbook-staging/.env` | `.env.local` |
| profile | `SPRING_PROFILES_ACTIVE=prod` | `staging` | `local` |
| DB | `kbookiadvcloud-postgres-1` | own container | own container |
| JWT secret | production value | **different** value (tokens must not cross) | dev value |
| Third-party keys | live | sandbox/test | stubs |
| OTP delivery | real SMS | logged, never sent | logged |

Never reuse a JWT secret across environments: a staging token would otherwise be accepted by
production.

---

## 5. Migration path (smallest first)

| Step | Action | Risk | Approval |
| :-- | :-- | :-- | :-- |
| E1 | Correct the deployment runbook (Apache → nginx). **Done.** | none | — |
| E2 | Create a non-root deploy user; stop using root for routine work | low | needed |
| E3 | Tag the current commit as the first rollback point (`v2026.11.17-1`) | none | needed |
| E4 | Build images **tagged by commit SHA**, keep the previous tag running until verified | low | needed |
| E5 | Stand up staging (Compose project on this host) with a masked production dump | medium | needed |
| E6 | Off-site backup copy (§ `BACKUP_AND_RESTORE.md`) | none | needed |
| E7 | Monitoring + alerts (§ `MONITORING.md`) | none | needed |
| E8 | Separate staging VPS | medium | later |

E3 and E4 are the highest value per unit of effort: without a tag there is **no known-good version to
return to**, and `git log` is not a rollback plan.

---

## 6. Definition of done

* A change can be exercised end-to-end without touching production.
* Every production deploy has a commit SHA, a tag, and a tested rollback.
* Production holds no development working tree.
* No shared secrets between environments.
* The previous release is always available to return to.
