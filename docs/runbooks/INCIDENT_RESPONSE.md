# Incident Response

**Scope:** production incidents on the KhanaBook VPS and platform.
**Principle:** restore service first, understand second, prevent third. Never debug on a live system
while customers are blocked.

---

## 1. Severity

| Sev | Definition | Response |
| :-- | :-- | :-- |
| **SEV-1** | All restaurants cannot bill; data loss or corruption; credential compromise; public data exposure | immediate, all hands, notify customers |
| **SEV-2** | A subset of tenants blocked; a major flow broken (signup, login, sync); payments failing | within 30 min |
| **SEV-3** | Degraded but usable; single tenant affected; non-critical feature down | same business day |
| **SEV-4** | Cosmetic, or a risk with no current impact | backlog |

---

## 2. Immediate steps (any severity)

1. **Declare it.** Write down start time and the symptom in one sentence. Announce in the team channel.
2. **Assess customer impact** — who cannot do what, right now.
3. **Freeze changes.** No deploys, no migrations, no "quick fixes" while diagnosing.
4. **Preserve evidence** *before* restarting anything:
   ```bash
   docker logs kbookiadvcloud-server-1 --since 30m > /tmp/incident-$(date -u +%FT%TZ).log
   docker logs kbookiadvcloud-postgres-1 --since 30m >> /tmp/incident-$(date -u +%FT%TZ).log
   docker ps -a > /tmp/incident-ps.txt
   journalctl -u nginx --since "30 min ago" >> /tmp/incident-nginx.log
   ```
   A restart destroys the only copy of the cause.
5. **Identify the last change.** If a deploy happened in the preceding hours, it is the prime suspect.
6. **Choose the cheapest safe recovery** (§3). Prefer rollback over repair.
7. **Verify recovery** with a real user path, not just `/health`.
8. **Record** using §5 within 48 hours.

---

## 3. Recovery options, cheapest first

| Situation | Action |
| :-- | :-- |
| Bad deploy | redeploy the previous image tag; confirm health + one real login |
| Migration failed at boot | redeploy previous image — **do not** patch the migration on the live DB |
| App healthy but data looks wrong | stop the server before any data action; take a dump; investigate on a copy |
| Database unavailable | follow `BACKUP_AND_RESTORE.md` §5.3 |
| Suspected credential compromise | rotate the affected secret; rotate `JWT_SECRET` to invalidate all sessions; review access logs |
| One tenant broken, others fine | do not restart the platform; scope the fix to that tenant |

---

## 4. Explicit prohibitions during an incident

* No `UPDATE`/`DELETE` on production without a fresh dump and a second pair of eyes.
* No editing an applied Flyway migration (checksum drift; `ops/flyway_repair_*` exists because this has
  happened).
* No `docker volume prune`, `docker system prune -a`, or disk "cleanup" to buy space — that can delete
  the database volume or the backups.
* No speculative restarts of nginx/postgres "to see if it helps".

---

## 5. Incident report template

Copy into `docs/incidents/YYYY-MM-DD-<slug>.md`.

```markdown
# Incident: <one-line title>

- **ID:** INC-YYYYMMDD-NN
- **Severity:** SEV-1 | 2 | 3 | 4
- **Status:** open | mitigated | resolved
- **Detected:** YYYY-MM-DD HH:MM UTC — by whom / what signal
- **Mitigated:** YYYY-MM-DD HH:MM UTC
- **Resolved:** YYYY-MM-DD HH:MM UTC
- **Duration of customer impact:** N minutes
- **Tenants affected:** N (names/ids held separately, not in this file)

## Impact
What customers could not do. Quantify: bills missed, logins failed, orders unsynced.

## Timeline (UTC)
| Time | Event |
|---|---|
| HH:MM | first symptom / alert |
| HH:MM | acknowledged by |
| HH:MM | evidence captured |
| HH:MM | hypothesis formed |
| HH:MM | action taken |
| HH:MM | recovery verified |

## Root cause
The mechanism, not the symptom. Evidence: log lines, query results, commit SHAs.

## Detection
How it was noticed. If a customer noticed first, say so — that is a monitoring gap, not a detail.

## What went well / what did not

## Corrective actions
| Action | Owner | Due | Ticket |
|---|---|---|---|

## Was production modified during resolution?
yes/no — and exactly what.
```

---

## 6. Post-incident rules

1. Blameless: the question is "what made this easy to do", not "who did it".
2. Every SEV-1/SEV-2 produces at least one **preventive** action with an owner and a date.
3. If detection was human, a monitoring action is mandatory.
4. The report is written within 48 hours, while memory is accurate.
5. Never edit a published report to look better; append a correction.
