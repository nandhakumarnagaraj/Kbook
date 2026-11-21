# Production remediation handoff — kbook.iadv.cloud

**Audience:** the AI CLI or engineer executing this on the server.
**Repo:** `https://github.com/nandhakumarnagaraj/Kbook.git` @ `main`
**Host:** `srv1315142` — `/var/www/kbook.iadv.cloud`
**Written:** 2026-09-27, after commit `ae091f24`

---

## AIM

Get the running service onto commit `ae091f24` and leave the Flyway ledger
honest, without losing data and without a second window where terminals sync
against a half-applied schema.

Concretely, when this is done:

1. `ops/daily_backup.sh` verifies real backups on the 21:00 UTC cron run.
2. V82's missing index exists and its ledger row carries a real checksum.
3. `ops/flyway_reconcile_checksums.sh` prints `RECONCILED` and exits 0.
4. The deployed jar identifies its own commit, provably.
5. A fresh verified backup exists, taken *after* the repair.

---

## PURPOSE

Why these steps, and what goes wrong if they are skipped or reordered.

**The backup gate was rejecting every good backup.** Commit `91ebae6e` set
`MIN_RAW_BYTES=1000000`, a number never measured against this database. Valid
dumps land in a 599K–641K band, so the gate rejected 100% of them and then
`rm -f`'d the result. A genuine 640KB backup was destroyed by its own
verification step. The constants came from `pg_database_size` (14MB) — physical
size including bloat, not the ~640KB logical dump. Absolute byte floors are the
defect, so the gate now asserts content instead: 52 `CREATE TABLE` statements
versus 0 for an empty dump. That check cannot be mistuned against database size.

**Two backups had been silently empty.** Two 20-byte gzip files were valid empty
dumps. `gzip -t` passed, so any decompressibility-only check called them healthy,
and restoring either would have produced a 0-row database.

**V82 is half-applied and structurally undetectable.** The row was hand-inserted
with `checksum=NULL`, `installed_by='manual'`, `execution_time=0`. The column
`last_seen_at` exists; the index `idx_restaurant_terminal_last_seen` that V82's
own script creates does not. Flyway skips checksum validation for NULL rows
permanently, `success=t` means it will never re-apply, and `ddl-auto=validate`
only checks entity-mapped columns, never indexes. So "Successfully validated 88
migrations" in fact means 87. Detection existed all along — the reconcile script
flagged V82 as a warning nobody was reading — and `91ebae6e` promoted it to a
hard failure.

**The running jar cannot be identified.** `git.properties` in the live jar
contains only a header comment. The Dockerfile copied just `pom.xml` and `src`,
so `.git` was absent, and `failOnNoGitDirectory=false` made the plugin fail
silently. A wall-clock build time is not an identity. `91ebae6e` makes the
Dockerfile require a `GIT_COMMIT_ID` and hard-fail without a valid 40-char sha,
so deploying closes this for the first time.

**The host is unstable.** 12 unclean power losses in 38 days, none explained by
OOM, kernel panic, Xorg, or the deploy. This is why a verified backup is a
precondition and not a nicety: every fix here can be wiped by the next event.

---

## PLANNED

Execute in order. Each step states its expected output and when to stop.

### Step 0 — Preconditions

```bash
cd /var/www/kbook.iadv.cloud
git log --oneline -1
```

Requires a verified backup to already exist. Confirm it before continuing:

```bash
ls -lS backups/daily/*.sql.gz | head -3
NEW=$(ls -t backups/daily/*.sql.gz | head -1)
gzip -t "$NEW" && echo "gzip OK"
zcat "$NEW" | grep -c "CREATE TABLE"     # must be 52
```

**Stop if** `CREATE TABLE` is not 52, or `gzip -t` fails. Do not proceed on a
backup that has not been proven.

### Step 1 — Take the committed fixes

The server previously carried an *uncommitted* edit to `ops/daily_backup.sh`
setting the floors to `500000/20000`. That is superseded by the committed
content-based gate. Discard it, then pull:

```bash
git checkout -- ops/daily_backup.sh     # discard the uncommitted local edit
git pull --rebase origin main
git log --oneline -1                   # expect ae091f24
grep -nE "MIN_RAW_BYTES=|MIN_CREATE_TABLES=|FAILED" ops/daily_backup.sh
```

**Expect** `ae091f24`, `MIN_RAW_BYTES:-2048`, `MIN_CREATE_TABLES:-40`, and a
`.FAILED` rename in `reject()`.

> Note: an earlier version of this handoff claimed cron runs the *committed*
> file and that the 21:00 UTC run would therefore fail. That was wrong — cron
> runs the file on disk. The real risk is the reverse: `83dc3bce` still has the
> broken 1MB floor in git, so any `checkout`, `stash`, or `pull` reverts the fix.
> That is why Step 1 comes first.

### Step 2 — Prove the new gate accepts a real backup

```bash
bash ops/daily_backup.sh
```

**Expect** `DB backup verified: <n>B gz / <n>B raw, 52 tables, critical tables present`.

**Stop if** it prints `ERROR` and leaves a `.FAILED` file. That file is preserved
deliberately — read `backups/cron.log` and report the reason rather than deleting
it.

### Step 3 — Repair V82

```bash
bash ops/flyway_repair_v82_checksum.sh          # dry run, writes nothing
```

**Expect** the self-check to reproduce checksums Flyway itself recorded, then:

```
  V1   : OK (-2047847407)
  V26  : OK (-56105825)
  V106 : OK (366750563)
  restaurant_terminal: MISSING index idx_restaurant_terminal_last_seen   <-- the defect
  computed : V82 checksum = -47410940
  DRY RUN   : nothing was written
```

**Stop if** the self-check reports a `MISMATCH`. The script will refuse to write;
that means its checksum implementation disagrees with Flyway, and a wrong value
would corrupt the ledger permanently. Report it; do not work around it.

Then apply:

```bash
bash ops/flyway_repair_v82_checksum.sh --yes
```

**Expect** `idx_restaurant_terminal_last_seen confirmed`, then
`verified : V82 checksum now -47410940`.

### Step 4 — Reconcile

```bash
bash ops/flyway_reconcile_checksums.sh; echo "exit=$?"
```

**Expect** every row `OK` and `RECONCILED: all applied migrations match their local scripts.`

**Stop if** anything reports `MISMATCH`, `NOCHECKSUM`, or `FAILED`. Deployment is
gated on this and the gate is correct to block.

### Step 5 — Record the test baseline

```bash
./mvnw -q test 2>&1 | tail -40
```

**Expect 6 pre-existing failures**, unrelated to this work:

- 3 × Easebuzz FSSAI renewal webhook
- 3 × `SourceChannelSyncContractTest` `NoSuchElement`

Recording these now is what makes a *new* failure visible. Do not treat them as
regressions; do not silently ignore them either.

### Step 6 — Deploy

```bash
bash ops/deploy-production.sh
```

Closes the build-identity finding: the new Dockerfile writes `git.properties`
after packaging and refuses to ship without a valid 40-char sha.

**Expect** `Building server at main@<sha>`, then the reconcile gate passing, then
`Backend healthy.`

Note the script deploys the API only. If an API contract changed, follow with
`bash ops/deploy-web.sh` or use `ops/deploy-all.sh`.

### Step 7 — Verify the unattended run (after 21:00 UTC)

```bash
ls -lt backups/daily/*.sql.gz | head -2
tail -5 backups/cron.log
```

**Expect** a new `kbook_saas_20260927T2100*.sql.gz` of roughly 130KB, a
`DB backup verified` line naming 52 tables, and the archive count increasing.
This is the first truly unattended backup and the real test of the fix.

---

## HARD RULES

- **Never hand-apply a migration.** It is the direct cause of H3. Use
  `ops/flyway_repair_v82_checksum.sh`, which replays the real script and records
  a checksum only once the schema provably matches.
- **Never use `flyway:repair`.** It rewrites the checksum of every row that
  differs from its script, which would paper over genuine drift elsewhere and
  destroy the evidence that anything else is wrong. The repair is a targeted
  single-row `UPDATE` on version 82.
- **Verify a backup before writing to the database.** Steps 3 and 4 are writes.
- **Do not delete a rejected archive.** It is renamed `.FAILED` so evidence
  survives and nothing can be restored from it by accident.
- **Do not deploy before Step 4 prints `RECONCILED`.** The gate at
  `ops/deploy-production.sh` enforces this and will abort the deploy; that is
  the intended behaviour, not a bug to work around.
- **Do not prune the 11:28 UTC pre-V106 backup.** It is the only artifact that
  can revert the V106 schema change.
- **Record the baseline (Step 5) before deploying**, so a real regression is
  distinguishable from the 6 known failures.

---

## OPEN RISKS — NOT FIXED BY THIS RUNBOOK

- **Host power loss, every 2–5 days, cause external to the guest.** Off-box
  action, worth doing today: ask the provider for maintenance windows and
  host-error/power events for 2026-09-27 12:01 and 2026-09-26 07:04 UTC, and why
  the node reboots uncleanly. Nothing else matters if the host is unstable.
- **2 deprecated Android clients (v21) actively syncing.** They predate
  `has_variants` and are the population affected by the defect that `f33f32a5`
  fixes. Expect them to keep syncing after deploy; that is correct and safe.
- **No container memory ceiling, no swap** (`HostConfig.Memory=0`). A leak has
  no cgroup limit and would take Postgres down with it.
- **4 git stashes** including an autostash, implying an interrupted rebase.
  None of it is deployed. Review before discarding.
- **3 orphaned Postgres volumes** from earlier project renames. Prune only
  after another verified backup.
- **`flyway_schema_history.version` is varchar**, so `max(version)` returns `99`
  while `max(version::int)` is `106`. Any monitoring built on the former is
  silently wrong.
- **Log retention gap.** The ~30-minute window between V106 (11:31 UTC) and the
  null-guard image (11:56 UTC) is unrecoverable; old container logs were lost
  across a reboot. Worth asking the restaurant active in that window whether any
  menu save failed.
- **No alerting on backup failure.** A rejected backup went unnoticed for 15
  hours. This is the cheapest high-value fix remaining.
