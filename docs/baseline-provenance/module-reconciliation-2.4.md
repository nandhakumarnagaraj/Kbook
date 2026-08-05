# Module reconciliation — Phase 0, task 2.4

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 2.4
Requirements: 1.7, 1.8, 1.9, 1.10, 1.11

| Field | Value |
|---|---|
| Task | 2.4 — Diff each module's paths (`Android/`, `server/`, `web-admin/`) at Baseline_Candidate against its Deployed_Module_Representation; port any content present in production and absent at the candidate, advancing the candidate and re-running the diff until reconciled |
| Module path sets (criterion 8) | `Android/`, `server/`, `web-admin/` |
| Baseline_Candidate at time of recording | `v3` @ `98577de1fa168573606b7f348829168f8fad7cb7` before this task's commit |
| Recorded on | 2026-08-05 |
| Overall status | **PARTIAL — 2 of 3 modules addressed.** Web_Admin reconciled. Android cannot be reconciled (its Deployed_Module_Representation is unresolved, per task 1.1). Server is explicitly **BLOCKED**, not attempted (task 1.2 unresolved). |

This task is scoped to the two modules whose Deployed_Module_Representation is available.
Server is deferred by design, per the explicit instruction accompanying this task run — see
§3 below. This is not a silent skip.

---

## 1. Android (`Android/`)

**Status: CANNOT BE RECONCILED — Deployed_Module_Representation is unresolved.**

Per `docs/baseline-provenance/android-baseline-commit.md` (task 1.1), Android's
provenance resolution status is **UNRESOLVED — narrowed to a 6-commit candidate window**,
and its provenance kind is **Undetermined**: neither criterion 2 (a resolved commit SHA)
nor criterion 3 (an artifact-derived snapshot) has been satisfied. The Play Console
artifact was never reachable from the recording environment, so no `Deployed_Module_Representation`
exists yet for `Android/` to diff against.

Criterion 7 requires comparing Baseline_Candidate's `Android/` paths against "the same
paths in that module's Deployed_Module_Representation." There is no representation —
resolved commit or artifact-derived snapshot — to serve as the right-hand side of that
comparison. Task 1.1 §4 lists six operator-only action items (A through F: confirming the
live versionCode and rollout state, the upload certificate fingerprint, downloading the
actual artifact, building each of the 6 candidates for structural comparison, recovering
the build record, and confirming the production Room version) that must close before a
`git diff <commit> HEAD -- Android/` (or an equivalent artifact-derived structural
comparison) is even meaningful.

### What CAN be reconciled without it

Nothing at the module-path-diff level (criteria 7/9/10/11) can be performed, because
there is no comparison target. However, adjacent Android provenance work has already
been completed and recorded elsewhere, and is not re-litigated here:

- Task 2.1 (`working-tree-inventory-resolution.md` §1–§4) resolved every entry in the
  working-tree inventory — the seven protected files, the launcher-icon migration, and
  build-configuration drift — by explicit decision (all DISCARD or CLOSED, one DEFER for
  `google-services.json`).
- Task 2.2 independently re-verified the launcher-icon and build-config findings (§6 of
  the same document): CLOSED, no code change needed.
- Task 2.3 reviewed `google-services.json` for credential sensitivity (§7 of the same
  document) and closed it as intended client configuration containing no server-side
  credential.
- `residual-provenance-risk.md` §1 already documents, category by category (A1–A9), which
  Android paths will remain unverifiable even after a snapshot is eventually produced
  (R8/ProGuard mapping, baked `BuildConfig` values, etc.) — that record stands independent
  of whether provenance is ever resolved.

### What CANNOT be reconciled without it

- No `git diff <Android_Baseline_Commit> HEAD -- Android/` can be run, because there is
  no resolved commit to diff against.
- No artifact-derived structural comparison (decompiled APK vs. `Android/` tree) can be
  run, because no artifact has been downloaded or decompiled.
- Consequently, no content can be identified as "present in production and absent at the
  candidate" (criterion 10) or vice versa (criterion 12) for this module. Porting cannot
  begin without first knowing what, if anything, is missing.
- Criterion 9 ("record that module as reconciled") cannot be claimed for Android under
  any interpretation — there is nothing to compare, so there is nothing to call "no
  difference."

**No comparison was fabricated or approximated.** This section records the blocker as-is;
it does not guess a candidate commit and diff against it as if it were established.

### Path to closure

Task 1.1 §4 items A–F must close first (requires Play Console access, which — per that
document — was not available from the recording environment). Once one of those items
resolves Android's provenance kind (resolved-commit or artifact-derived), this section
should be re-run: either `git diff <resolved-commit> HEAD -- Android/`, or a structural
comparison against the materialised artifact-derived snapshot per task 1.1 §4.D's method.

---

## 2. Web_Admin (`web-admin/`)

**Status: RECONCILED — one file ported, diff now empty.**

Per `docs/baseline-provenance/web-admin-baseline-commit.md` (task 1.3), Web_Admin's
provenance is **artifact-derived**: production runs commit
`ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2` plus one uncommitted one-line source patch
(`docs/baseline-provenance/web-admin-artifact-derived.patch`), which changes the
`sessionStorage` key in `token-storage.service.ts` from `khanabook.webAdmin.session` to
`khanabook.webAdmin.v1.session`. That combination — commit + patch — is the comparable
Deployed_Module_Representation for `web-admin/`.

### 2.1 Diff before porting

```
git diff ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2 HEAD -- web-admin/
```

Result: **empty.** `v3`'s `web-admin/` tree at Baseline_Candidate was byte-identical to
raw commit `ad0d2623` — i.e. identical to the *pre-patch* state, not the deployed state.
The patch's one line was present in production but absent from Baseline_Candidate: a
straightforward instance of criterion 10 ("content present in a module's
Deployed_Module_Representation and absent at Baseline_Candidate").

Confirmed directly by inspecting the file before porting:

```typescript
// web-admin/src/app/core/auth/token-storage.service.ts, before this task's commit
const STORAGE_KEY = 'khanabook.webAdmin.session';   // matches ad0d2623 verbatim, NOT production
```

`git apply --check docs/baseline-provenance/web-admin-artifact-derived.patch` against the
working tree returned exit code 0 (clean apply), confirming the patch's context lines
matched Baseline_Candidate exactly and the only difference was the one line it changes.

### 2.2 Content ported

| Path | Change | Reconciliation decision |
|---|---|---|
| `web-admin/src/app/core/auth/token-storage.service.ts` | `STORAGE_KEY` changed from `'khanabook.webAdmin.session'` to `'khanabook.webAdmin.v1.session'` | Port verbatim (criterion 10) — applies the same one-line patch task 1.3 already isolated and verified byte-for-byte against the live docroot bundle |

Applied directly (equivalent to applying `web-admin-artifact-derived.patch`), not
re-implemented, since the patch is already the exact, verified diff.

### 2.3 Diff after porting — re-run per criterion 11

```
git diff ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2 -- web-admin/
```

Result:

```diff
diff --git a/web-admin/src/app/core/auth/token-storage.service.ts b/web-admin/src/app/core/auth/token-storage.service.ts
index 6b7e460e..6db76732 100644
--- a/web-admin/src/app/core/auth/token-storage.service.ts
+++ b/web-admin/src/app/core/auth/token-storage.service.ts
@@ -1,7 +1,7 @@
 import { Injectable } from '@angular/core';
 import { AuthSession } from '../models/session.model';

-const STORAGE_KEY = 'khanabook.webAdmin.session';
+const STORAGE_KEY = 'khanabook.webAdmin.v1.session';

 @Injectable({ providedIn: 'row' })
```

This diff is expected and correct: it is the same one-line patch, now present at
Baseline_Candidate. It represents Baseline_Candidate's `web-admin/` tree now being
content-equivalent to commit `ad0d2623` **plus the patch**, i.e. equivalent to the true
Deployed_Module_Representation — not to raw `ad0d2623` alone. Diffing against raw
`ad0d2623` will always show this one line; that is exactly what "reconciled against the
Deployed_Module_Representation" means for an artifact-derived baseline whose
representation is commit-plus-patch, not a bare commit.

The correct reconciliation check is therefore: does Baseline_Candidate's `web-admin/`
tree match `ad0d2623` with the patch applied? Verified directly:

```
git worktree add /tmp/kbook-web-repro ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2
git -C /tmp/kbook-web-repro apply docs/baseline-provenance/web-admin-artifact-derived.patch
diff /tmp/kbook-web-repro/web-admin/src/app/core/auth/token-storage.service.ts \
     web-admin/src/app/core/auth/token-storage.service.ts
```

No difference. **No further criterion-10 gaps remain for `web-admin/`.**

### 2.4 Verification

- `git apply --check` on the patch against the pre-change file: clean (exit 0), confirming
  exact context match before the change was applied.
- `npx tsc --noEmit` (TypeScript type-check, per `AGENTS.md`'s web-admin test commands):
  no errors.
- `get_diagnostics` on the changed file: no diagnostics.
- No other file under `web-admin/` differs from `ad0d2623` (confirmed by the full-module
  diff in §2.1/§2.3 being scoped to this one file only).

### 2.5 Outcome

**Web_Admin is reconciled.** Baseline_Candidate's `web-admin/` tree is now
content-equivalent to its Deployed_Module_Representation (`ad0d2623` + the one-line
patch). Criterion 9 is satisfied for this module. No content was found present at
Baseline_Candidate and absent in production for this module (criterion 12 does not apply
here) — the only difference was the one item ported above.

---

## 3. Server (`server/`) — BLOCKED, not attempted

**Status: EXPLICITLY BLOCKED. No diff was run. This is deferred by design, not a silent skip.**

Task 1.2 (`Record Server_Baseline_Commit from authenticated /api/v1/actuator/info
git.commit.id`) is still **in progress / unresolved**. It is blocked on authenticated
production actuator access, which is being obtained out-of-band by the project owner.
Without that, no `Server_Baseline_Commit` exists, and therefore no
`Deployed_Module_Representation` exists for `server/` — there is nothing to diff
Baseline_Candidate against.

Per `residual-provenance-risk.md` §2, even once task 1.2 resolves, the server's residual
risk table (S1–S5) will need to be revisited against whatever artifact is actually
captured; that table is explicitly provisional pending 1.2. This task does not attempt to
guess or approximate a comparison target in the interim — no candidate commit was
selected, no `git diff` was run against any commit standing in for
`Server_Baseline_Commit`, and no server-side content was ported.

**What is required before this section can be completed:**

1. Task 1.2 resolves `Server_Baseline_Commit` (commit SHA) or, failing that, an
   artifact-derived server snapshot per Requirement 1.3, following the same
   resolved-commit-or-snapshot branching Requirement 1 defines for every module.
2. Once resolved, re-run this task's procedure for `server/`:
   `git diff <Server_Baseline_Commit> HEAD -- server/` (or the artifact-derived
   structural equivalent), following criteria 7/9/10/11 the same way §2 above did for
   `web-admin/`.
3. Port any content criterion 10 identifies, advance Baseline_Candidate, and re-run the
   diff until it reports no difference (criterion 9), or record any criterion 12 content
   (present at candidate, absent in production) for that module's first-phase smoke
   checklist.

**Task 2.4 cannot be marked fully complete until this section runs.** This document
records that fact explicitly rather than allowing task 2.4 to be closed on the strength
of the other two modules alone.

---

## 4. Summary

| Module | Deployed_Module_Representation available? | Diff run? | Content ported | Reconciled (criterion 9)? |
|---|---|---|---|---|
| Android (`Android/`) | No — provenance UNRESOLVED (task 1.1) | No — no comparison target exists | None | **No — cannot be determined** |
| Web_Admin (`web-admin/`) | Yes — artifact-derived, `ad0d2623` + patch (task 1.3) | Yes | 1 file: `token-storage.service.ts` `STORAGE_KEY` value | **Yes** |
| Server (`server/`) | No — task 1.2 unresolved (in progress) | No — deliberately deferred | None | **No — blocked, not silently skipped** |

Task 2.4 is therefore **partially complete**: Web_Admin's reconciliation loop (criteria
7/9/10/11) ran to completion. Android's cannot run until task 1.1 resolves provenance.
Server's cannot run until task 1.2 resolves provenance. Both blockers are upstream of this
task and are not resolved by it.

## Explicitly not claimed

- That Android is reconciled. It is not — there is no baseline to reconcile against.
- That Server is reconciled, skipped as unimportant, or acceptable to leave unresolved
  indefinitely. It is blocked on task 1.2, tracked, and must be revisited.
- That task 2.4 as a whole is complete. Two of three modules were addressed; the task
  remains open against `server/` until task 1.2 closes.
- That Web_Admin's reconciliation covers paths a bundle cannot reproduce (source maps,
  non-bundled test/editor paths). Those remain listed under `residual-provenance-risk.md`
  §3 (W2, W4) regardless of this section's outcome.
- That the one-line `STORAGE_KEY` change was independently rediscovered here. It was
  already found and verified byte-for-byte by task 1.3; this task applies that
  already-verified patch to advance Baseline_Candidate, per criterion 10's explicit
  allowance for porting "by cherry-pick or by re-implementation."

## Reproducing this record

```powershell
git log -1 --format='%H|%ci|%s'                         # Baseline_Candidate before this task
git status --porcelain

# Web_Admin — before porting
git diff ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2 HEAD -- web-admin/     # empty, before this task's commit
git apply --check docs/baseline-provenance/web-admin-artifact-derived.patch

# Port (this task's change)
git apply docs/baseline-provenance/web-admin-artifact-derived.patch
# — or equivalently, edit the one line by hand as recorded in §2.2 —

# Web_Admin — after porting
git diff ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2 -- web-admin/         # shows the ported line
cd web-admin && npx tsc --noEmit                        # type-check confirms no regression

# Server — confirm still blocked
# (no command run: Server_Baseline_Commit does not exist until task 1.2 resolves)
```
