# Backup and Restore Runbook

**Verified:** 2026-11-17 — a real restore drill was executed against the newest production dump.
**Owner:** whoever deploys. Read before any migration.

---

## 1. What exists today (measured)

| Item | Detail |
| :-- | :-- |
| Schedule | nightly **21:00** via root crontab → `ops/daily_backup.sh` |
| Database dump | `backups/daily/kbook_saas_<UTC>.sql.gz` — ~1.1 MB, plain SQL, gzipped |
| CDN assets | `backups/daily/cdn_images_<UTC>.tar.gz` — ~32 MB |
| Verification | the script checks the dump: byte size, gzip validity, **55 tables**, critical tables present |
| Retention | prunes old files each run |
| Log | `backups/cron.log`, plus `backups/STATUS` |
| Location | **`/var/www/kbook.iadv.cloud/backups/`** — the same disk as the database |
| Off-site | **none.** No restic, no rclone, no S3, no rsync. |
| Encryption | **none** (plain `.sql.gz` containing customer PII) |

Last recorded run:

```
[2026-11-16 21:00:07] DB backup OK: .../daily/kbook_saas_20261116T210001Z.sql.gz (1.1M)
[2026-11-16 21:00:08] DB backup verified: 1067915B gz / 18348011B raw, 55 tables, critical tables present
[2026-11-16 21:00:11] CDN backup OK: .../cdn_images_20261116T210001Z.tar.gz (32M)
[2026-11-16 21:00:11] Retention: removed 2 old file(s)
```

---

## 2. Restore drill — executed, passing

A backup that has never been restored is a file, not a recovery plan. On 2026-11-17 the newest dump was
restored into a throwaway `postgres:16-alpine`:

```bash
docker run -d --name kbook-restore-drill \
  -e POSTGRES_PASSWORD=… -e POSTGRES_USER=drill -e POSTGRES_DB=kbook_drill \
  -p 55443:5432 postgres:16-alpine
gunzip -c backups/daily/kbook_saas_20261116T210001Z.sql.gz \
  | psql -h 127.0.0.1 -p 55443 -U drill -d kbook_drill -q
```

Result:

| Check | Value |
| :-- | :-- |
| SQL errors | **0** |
| tables in `public` | **55** |
| `users` | **165** |
| `restaurantprofiles` | **161** |
| `bills` | **2,179** |
| `flyway_schema_history` head | **121** |

Conclusion: the nightly dump is **restorable and complete**. The gap is not integrity — it is
**location**.

---

## 3. The actual risk

```
     ┌─────────────── this VPS ───────────────┐
     │  postgres ────► backups/daily/*.sql.gz │   ← same disk, same host, same root
     └────────────────────────────────────────┘
                       ✗ nothing leaves the box
```

Failure modes that destroy both copies at once: disk failure, corrupted filesystem, accidental
`rm -rf`, ransomware, provider account loss, a bad `docker volume prune`. Retention is short, so a
defect that silently corrupts data for a week can also rotate out the last good copy.

**This is the single highest-impact infrastructure gap after environment separation.**

---

## 4. Off-site backup — tooling ready, awaiting a bucket

`restic 0.16.4` is installed and `ops/offsite_backup.sh` is written and executable. It is **inert by
design**: with no repository configured it prints why and exits `78`, so it can never report a backup
that did not happen.

```bash
$ ./ops/offsite_backup.sh
[2026-11-17T10:30:21Z] not configured: /etc/kbook/restic.env is missing — see the header of this script
exit=78
```

To activate it, only two things are needed: an S3-compatible bucket and a passphrase. Full steps are in
the script header; in short:

1. Install `restic`, create an encrypted repository on object storage (S3/B2/Wasabi/rclone target).
2. Nightly, after the existing dump: `restic backup backups/daily/`, with `--keep-daily 14
   --keep-weekly 8 --keep-monthly 12`.
3. Keep the encryption password **out of git** and out of the server's `.env` (use a password manager;
   losing it means losing every backup).
4. Monthly, restore from the **off-site** copy — not the local one — into a scratch database.
5. Alert if the nightly job does not report success.

Until that exists, the manual mitigation is: copy `backups/daily/*.sql.gz` off the host after any
production change that touches data, and keep it encrypted at rest (the dump contains customer PII).

---

## 5. Restore procedures

### 5.1 Full restore into a scratch database (safe, routine)

Use this for rehearsal, forensics, or to test a migration. **Never** point the app at the result.

```bash
docker run -d --name pg-restore -e POSTGRES_PASSWORD=… -p 55443:5432 postgres:16-alpine
gunzip -c <dump>.sql.gz | psql -h 127.0.0.1 -p 55443 -U postgres -d postgres -q
```

### 5.2 Point-in-time recovery of a single table

```bash
gunzip -c <dump>.sql.gz > /tmp/restore.sql
docker cp /tmp/restore.sql <container>:/tmp/
docker exec -it <container> psql -U kbookuser -d kbook_scratch -f /tmp/restore.sql
# then compare / COPY the specific rows across — never TRUNCATE production to "re-sync"
```

### 5.3 Disaster recovery (production database lost)

1. Declare an incident (`docs/runbooks/INCIDENT_RESPONSE.md`).
2. Stop the application: `docker compose -p kbookiadvcloud stop server` — stop writes before restoring.
3. Recreate the DB volume and container.
4. Restore the newest verified dump (§5.1 against the real database name).
5. Verify counts against the drill table above (users/profiles/bills near-identical, drift explained by
   time since dump).
6. Start the server; confirm `/api/v1/actuator/health` and a real login.
7. Record actual data loss window = time since the dump.

**Never restore over a live database that is still serving writes.** You will silently lose everything
written during the restore.

---

## 6. Rules

* Take a fresh dump immediately **before** any production migration or data correction.
* Verify a dump by restoring it, not by looking at its size.
* Never delete the most recent two dumps, whatever the retention script says.
* Backups contain PII: never copy them into the repository, a ticket, or a chat.
* An untested restore is an assumption, not a capability.
