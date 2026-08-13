# Post-All-Phases Checklist

Final execution order after every task in `.kiro/specs/v2-feature-integration/tasks.md` is checked off.
This document defines the close-out that remains over and above the 161 tracked tasks. These steps are
the owner-vetted endgame from the KhanaBook integration (v1 core + v2 integrations → v3).

> Goal state: **`main` stays as the pure v1 core branch, `v3` is the integrated product, `v2` is deleted
> from GitHub.** Each environment keeps its own Postgres database and server deployment.

---

## 0. Definition of "all phases complete"

Before starting close-out, verify every task in `tasks.md` is `[x]`, including:

- [ ] Per-Phase Gate passed on the final phase (task 5/Phase list): `mvn test`,
      `./gradlew.bat testDebugUnitTest`, `./gradlew.bat assembleDebug`, `npm run build`
- [ ] Room migration tests for every version transition (62→63+)
- [ ] No protected v1 class deleted, no `/api/v2` route, no V2_Design_System artifact,
      no newly tracked credential
- [ ] Five smoke checks pass on the final phase
- [ ] Task 23.4 (StringBuilder renderer removal) done **only after** the observation period on 21.4

---

## 1. Completeness cross-check: v1 vs v2 vs v3

Prove no v2 behaviour was dropped and no v1-only feature was regressed.

- [ ] Re-diff every v2 feature module path (`server/`, `Android/`, `web-admin/`) against v3;
      confirm each ported feature from Requirements 17–20, 24, 25 exists in v3
- [ ] Confirm every v1-only feature (added to `main` after v2 forked) is present in v3 and was
      NOT overwritten by a v2 version — consult the conflict register (Req 29.3)
- [ ] Record zero unresolved port items; log any residual in `docs/baseline-provenance/residual-provenance-risk.md`
- [ ] Confirm the deferred set (WIRE, refresh-token rotation, 17 fintech pages) has no schema, endpoint,
      controller, or client in v3 (tasks 23.1–23.3 evidence)

## 2. Evidence pack per Requirement 29

Assemble the per-phase record before the branch cleanup (these live in the repo, not the DB):

- [ ] Preservation_Test_Suite result set for every phase (task 22.7)
- [ ] Endpoint register: path, method, required role, target consumer per added endpoint
- [ ] Conflict register: file + resolution for every file both branches touched after Merge_Base
- [ ] Flyway version range and Room version range per phase

## 3. v2 branch deletion (owner rule — LAST git action)

Order matters: delete remote only after v3 is fully proven, never while v2 still receives deploys.

- [ ] Freeze `v2`: stop v2 server deploys, confirm v2 stack safe to stand down (see §5)
- [ ] Confirm `origin/v2` is fully represented in `origin/v3` (no unharvested commits:
      `git log --oneline origin/v2 ^origin/v3` must be empty or resolved)
- [ ] Archive locally if desired: `git branch -m v2 archive/v2-final` (keep local reference if needed)
- [ ] Delete remote branch: `git push origin --delete v2`
- [ ] Verify GitHub branch list = `main` + `v3` only
- [ ] Update `.kiro/specs/v2-feature-integration/` note + this doc: record deletion SHA/date

## 4. Branch and repo hygiene

- [ ] `v3` set as the default/production branch if replacing `main`; otherwise confirm `main` is
      the deploy source and v3 feeds it by merge after approval (owner decision)
- [ ] Tag final integrated state: annotated `Baseline_Tag` / `v3-final` etc. per task 3.2 convention
- [ ] Update AGENTS.md deployment notes to the v3 reality (branch names, stacks, health URLs)

## 5. Production separation — three live stacks

Each environment keeps its own DB + server (owner rule).

- [ ] v1 stack (`main`): server + Postgres untouched, `/api/v1/actuator/health` UP
- [ ] v2 stack (`v2`): still running during harvest; after §3 freeze, stand down server container,
      retain its DB as read-only archive (or snapshot before shutdown)
- [ ] v3 stack: fresh Postgres + server deploy; DB migrated only through the shipped Flyway chain
      (V1–V54 by design; V49–V52 landed with the FSSAI/notifications port, V53 marketplace, V54 onboarding), additive-only, orphan V6/V7/V8 tables left in place as documented in V48
- [ ] Confirm no cross-stack sharing of DB credentials/config; `.env` per stack
- [ ] Health verified on all live stacks post-deploy

## 6. Post-deployment smoke + rollout (task 22.2–22.5 tail)

On the live v3 stack:

- [ ] Post-deployment smoke checklist (task 22.6): bill creation, sync push, sync pull, KOT print,
      terminal registration
- [ ] Replay legacy sync requests → confirm schema conformance against production (22.2)
- [ ] Android release to internal test track; Room 62→63 upgrade test on physical devices (22.3)
- [ ] Pilot: one restaurant enabled by OVERRIDE with `default_enabled` still false; watch its
      webhook backlog drain (22.4)
- [ ] Expand by override; monitor sync quarantine, payment reconciliation, notification delivery,
      inbox NEEDS_REVIEW counts (22.5)

## 7. Rollback playbooks (documented, not just coded)

- [ ] Per-phase rollback sequence recorded: kill-switch flag → previous server image → docroot backup
      (Req 28.11 + task 22.6)
- [ ] Database_Backup_Gate dumps (ops/backup_postgres.sh) archived as disaster-recovery artifacts (Req 28.9)
- [ ] Flyway rollback truth recorded: JAR rollback alone is NOT enough if a migration ran — restore DB

## 8. Repository docs refresh

- [ ] Update README/AGENTS.md: branch policy (main/v3, v2 deleted), deploy flow per stack,
      flag admin surface location, security notes
- [ ] Mark `.kiro/specs/v2-feature-integration/` status done with date + evidence links
- [ ] Leave a root-level close-out summary of what shipped and what is deferred

---

## Ownership checklist (who does what)

- [ ] **Owner (you):** VPS access for section 5/6, GitHub branch deletion approval (§3), Play Console
      track release, production actuator access (unblocks tasks 1.2/2.4/3.2/3.3 much earlier than this
      list)
- [ ] **Agent:** code, tests, evidence docs, migration authoring, cross-check diffs, this checklist

## Gate before §3 deletion

The ONLY hard gate blocking the v2 branch deletion is: **every v2 commit's behaviour is present and
proven in v3, and the v2 stack has been stood down.** Everything else on this list can be done before
or after; §3 is intentionally last.