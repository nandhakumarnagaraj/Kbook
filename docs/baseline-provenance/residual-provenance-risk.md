# Residual_Provenance_Risk — compiled-output paths a snapshot cannot reproduce

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 1.4
Requirements: Baseline Precondition criterion 5 (Requirement 1.5)

| Field | Value |
|---|---|
| Module path sets covered | `Android/`, `server/`, `web-admin/` |
| Recorded on | 2026-08-05 |
| Depends on | Task 1.1 (`android-baseline-commit.md`), Task 1.2 (`server-baseline-commit.md`, currently UNRESOLVED), Task 1.3 (`web-admin-baseline-commit.md`) |
| Purpose | Enumerate, per module, every path/category an artifact-derived snapshot fundamentally cannot reproduce byte-for-byte from compiled output, so criterion 9 ("reconciled") is never claimed for these paths |
| Scope note | This task records risk categories and, where an artifact already exists, ties them to concrete evidence. It does not itself resolve any UNRESOLVED baseline (that remains task 1.2's job) |

**Source-file caveat.** `docs/baseline-provenance/android-baseline-commit.md` and
`docs/baseline-provenance/web-admin-baseline-commit.md` are not present in this
working tree (branch `v3`); they exist only in commit `a2f44c22` on
`archive/v2-wip-v1-backport`. The facts, commit SHAs, and evidence cited below for
Android and Web_Admin are reproduced from that commit via `git show`, since it is
the only recorded output of tasks 1.1 and 1.3. If those docs are expected to live on
this branch, they should be restored from `a2f44c22` as a separate action — this
task does not do that, since it is scoped to residual-risk documentation only.

Per Requirement 1.5: "WHERE an artifact-derived snapshot cannot reproduce a source
path because the artifact contains only compiled or bundled output, Phase 0 SHALL
record that path as unverifiable and SHALL list it as a residual provenance risk
rather than treating it as equivalent." Nothing in this document upgrades a listed
path to "reconciled." A later phase that wants to treat one of these paths as
equivalent must present new evidence, not rely on this record.

---

## 1. Android (`Android/`)

Provenance kind for Android is currently **undetermined** (task 1.1): no Play
artifact was reachable, so neither a resolved commit nor a materialised
artifact-derived snapshot exists yet. The categories below apply once such a
snapshot (decompiled/structurally-compared APK) is produced, and they explain why
task 1.1 §4.D specifies *structural*, not *bitwise*, comparison.

| # | Path / category | Why the artifact cannot reproduce it | Mitigated by | Status |
|---|---|---|---|---|
| A1 | Compiled DEX bytecode (`classes*.dex`) under R8 (`isMinifyEnabled = true`) | R8 renames classes, methods, and fields per `proguard-rules.pro` and inlines/removes dead code. The renamed symbol graph cannot be inverted to the original Kotlin source without the mapping file produced by that exact build. | The R8 `mapping.txt` for the production build, if retained. **Not currently available** — no build record exists for the versionCode-20 upload (task 1.1 §1, "no CI build records"). | Open |
| A2 | Shrunk/merged resources (`resources.arsc`, merged `res/`) under `isShrinkResources = true` | Resource shrinking removes entries R8 determines are unreachable and merges resource-set overlays from all modules/flavors into one binary table. The binary table's surviving entries are a subset and a re-encoding of the source `res/` tree, not a copy of it. | Structural comparison (which entries exist, resolved values) per task 1.1 §4.D. | Partially mitigated — structural only |
| A3 | Generated `BuildConfig.java` fields (`BACKEND_URL`, `GOOGLE_WEB_CLIENT_ID`) | These are `buildConfigField` values resolved through `configValue(...)`, which prefers `local.properties` → Gradle property → environment variable → hardcoded default (task 1.1 §1.1). The compiled value baked into the artifact reflects whatever the *build machine* had configured at build time, which is not recoverable from any committed source file. | None available. The committed default (`kbook.iadv.cloud` per `expectedProductionHost`) is a plausible but unverified value. | Open |
| A4 | Generated `R.java` / resource IDs | Resource IDs are assigned by the AAPT2 compiler run and can shift between builds depending on resource set composition, even for semantically identical source. An ID observed in a decompiled artifact does not map back to a specific commit's `res/` ordering. | None. Not load-bearing for provenance (IDs aren't compared; resolved values are). | Open, low impact |
| A5 | Merged/compiled `AndroidManifest.xml` | The manifest merger combines the app manifest with every library's manifest, resolves placeholders (e.g. `applicationId`), and serializes the result as binary XML. The binary manifest in the artifact is not the source manifest — it is a merge product. | Structural comparison (permissions, activities, receivers, services) per task 1.1 §4.D. | Partially mitigated — structural only |
| A6 | ProGuard/R8 mapping file (`mapping.txt`) itself | This file is a build output, not a source artifact, and it is what would resolve A1. If it was not preserved from the original build (which task 1.1 confirms: no release CI, no recorded build directory), it cannot be regenerated after the fact — a later rebuild produces a *different* mapping because R8's naming is non-deterministic across invocations without a saved mapping to feed back in via `-applymapping`. | None. | Open, permanently unrecoverable if not already saved |
| A7 | Room schema identity hash embedded in compiled `AppDatabase` | The identity hash is a derived digest of the schema Room actually compiled, not source text. It is useful evidence *for* a match (task 1.1 §4.D uses it as a comparison point) but the hash alone cannot be reversed to confirm entity-by-entity source equivalence — only that the compiled shape matches. | Serves as corroborating evidence, not a reproduction. | Not a gap, but not full equivalence either |
| A8 | Zip/APK container metadata (per-entry timestamps, v2/v3 signing block) | APK zip entries carry a build-time timestamp unrelated to source content, and the signature block is a function of the private key, not the source tree. Two builds from the identical commit produce different bytes here. | N/A — explicitly not meaningful for content comparison; excluded from the structural diff. | Not a gap — correctly out of scope |
| A9 | DEX/bytecode non-determinism from toolchain version drift | Even with an unmodified source tree and a retained mapping file, a different AGP/R8/Kotlin-compiler version than the one used for the original build can produce non-identical bytecode for identical source. Task 1.1 §4.D calls this out directly: "R8/ProGuard... makes byte-for-byte DEX equality unreachable, so the comparison is structural, not bitwise." | Pin toolchain versions via `gradle-wrapper.properties`/`libs.versions.toml` before attempting a comparison build (these are separately in flux per task 2.2). | Open, environmental |

**Net effect for Android:** even after task 1.1 resolves to a specific commit or a
materialised snapshot, A1, A3, and A6 remain permanently unverifiable unless a
retained mapping file or build log surfaces. The comparison in task 2.4 for
`Android/` must be read as "structurally consistent with," never "byte-identical
to," the deployed artifact.

---

## 2. Server (`server/`)

Task 1.2 is **UNRESOLVED** — no server artifact (JAR, image, or authenticated
`git.commit.id`) has been captured yet. The categories below therefore describe
what will remain unverifiable *once* an artifact is obtained; they do not
themselves close 1.2, and no server path may be marked "reconciled" until 1.2
resolves and this table is revisited against the actual captured artifact.

| # | Path / category | Why the artifact cannot reproduce it | Mitigated by | Status |
|---|---|---|---|---|
| S1 | `git.properties` / `build-info.properties` baked by `git-commit-id-maven-plugin` and the Spring Boot `build-info` goal | These files capture the git SHA, branch, and dirty-state flag, and the build timestamp, *of the machine that ran the build* — not of a clean checkout of the resolved commit. `server/Dockerfile` copies only `pom.xml` and `src/` into the build stage (no `.git`), and the plugin is configured `failOnNoGitDirectory=false`, so the file may be **absent entirely** from the compose-built image (`server-baseline-commit.md` "Build-metadata risk"). | Authenticated `/api/v1/actuator/info`, if the field is present. Task 1.2 exists specifically to capture this. | Open — blocked on 1.2 |
| S2 | Environment-baked runtime configuration (DB credentials, JWT secret, Firebase service-account, Easebuzz keys, SMTP credentials) | These are supplied via `.env` / mounted files / environment variables at container start, never committed, and by design cannot be recovered from either source or the compiled JAR. | N/A — this is intentionally not reproducible; it is a secret-handling boundary, not a provenance gap to close. | Not a gap — correctly out of scope; listed so no one later treats a running container's effective config as source-derivable |
| S3 | `MANIFEST.MF` build timestamps (`Created-By`, `Build-Jdk-Spec`, `Implementation-Version`) and Docker image layer timestamps | Regenerated on every build regardless of source identity; two builds from the same commit differ here. | N/A — excluded from content comparison. | Not a gap — correctly out of scope |
| S4 | `.class` file bytecode reproducibility across JDK vendor/minor versions | Unlike Android, the server has no obfuscation step (no ProGuard/R8 equivalent), so `javac` output for identical source and an identical JDK is close to deterministic. However, constant-pool ordering and embedded debug line-number tables can still differ across JDK distributions/minor versions even for byte-identical source, so bitwise JAR equality across environments should not be assumed. | Compare decompiled class structure / method signatures rather than raw bytes, matching the approach already used for Android. | Open, environmental — lower risk than Android since no renaming occurs |
| S5 | Flyway migration SQL (`server/src/main/resources/db/migration/*.sql`) | **Explicitly not a residual risk.** These are source files copied verbatim into the JAR's classpath resources; Flyway's own checksum mechanism (Requirement 2.13's reconciliation script) already verifies them against Flyway_History. Listed here only to state that this path is *not* subject to the compiled-output limitation the rest of this document describes. | Flyway checksum reconciliation (task 5.4). | Not a gap |

**Net effect for Server:** because the server has no bytecode-renaming build step,
its residual-risk surface is narrower than Android's and is dominated by S1 (the
one piece of provenance metadata the architecture relies on) and S2 (which is a
non-goal by design, not a defect). Task 1.2's authenticated-actuator approach is the
correct closure path for S1; nothing in this task substitutes for it.

---

## 3. Web_Admin (`web-admin/`)

Provenance kind for Web_Admin is **artifact-derived**, already established (task
1.3) against commit `ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2` plus a one-line
patch, with 29+ generated assets matched byte-for-byte by SHA-256 after rebuilding
from that reconstructed tree. Because a full rebuild-and-diff was already performed
(rather than only inspecting the artifact in isolation), Web_Admin's residual risk
is the narrowest of the three modules — but it is not zero.

| # | Path / category | Why the artifact cannot reproduce it | Mitigated by | Status |
|---|---|---|---|---|
| W1 | Minified/tree-shaken/hashed bundles (`main-*.js`, `chunk-*.js`, `polyfills-*.js`, `styles-*.css`) | Angular's production builder (`outputHashing: "all"`, default `optimization: true`) renames local variables/properties, dead-code-eliminates unused exports, and content-hashes filenames. None of that is invertible from the bundle alone. | The bundle was independently rebuilt from the reconstructed source tree and matched byte-for-byte, so equivalence here is *proven by reproduction*, not merely asserted. | Resolved for the recorded commit+patch combination — but only because a rebuild was done; the minified bytes alone would not have sufficed |
| W2 | Absence of source maps in the production build | `angular.json`'s `production` configuration does not set `sourceMap: true` (only the `development` configuration does), so no `.js.map` files are emitted for or served with the deployed bundle. Line/symbol-level mapping from the minified artifact back to `.ts` source is therefore unavailable from the artifact itself. | Full source rebuild-and-diff (§ above), which sidesteps the need for source maps entirely for this baseline. Would matter if a *future* production incident needed to deobfuscate a stack trace from the live bundle without rebuilding. | Open for any use case other than the already-completed baseline reconciliation |
| W3 | `environment.prod.ts` build-time file replacement | **Not a residual risk for this repository.** `fileReplacements` swaps in the committed `environment.prod.ts` at build time; both files are tracked source, so the substitution is fully reproducible from the resolved commit. Listed here only to state it is out of scope, since file-replacement mechanisms are a common source of "environment-baked" risk elsewhere. | Committed source; verified during the task 1.3 rebuild. | Not a gap |
| W4 | Non-bundled paths (unit-test sources, editor metadata, local caches, the deploy operator's shell history/session) | The production build only emits browser-servable output; anything excluded from the Angular build graph (tests, `.vscode`/`.idea` state, `node_modules` cache contents, whatever the deploying operator's shell session actually looked like) leaves no trace in the docroot. This is the category `web-admin-baseline-commit.md` itself already flags under "Residual provenance risk for task 1.4." | None possible by construction — these paths are never part of a deployed bundle. | Open, structurally unresolvable from any artifact |
| W5 | End-of-line normalization in static passthrough files (`index.html`, `privacy-policy.html`, `robots.txt`) | The task 1.3 comparison found these identical only after ignoring CRLF-vs-LF differences (Windows build machine vs. Linux deployment). This is a line-ending artifact of the build/deploy environment, not a content difference, but it means "byte-for-byte identical" does not literally hold for these files even though content equivalence does. | Line-ending-normalized comparison, already performed. | Resolved with a stated caveat |

**Net effect for Web_Admin:** W1 and W3 are closed because task 1.3 did the more
expensive thing (rebuild from source and diff outputs) rather than relying on
inspecting the minified artifact alone. W2 and W4 remain genuinely open — no amount
of rebuilding the *current* baseline recovers source maps or non-bundled paths for
a *different*, unknown future artifact, so any later re-baselining of Web_Admin
should not assume this task's mitigation carries forward automatically.

---

## 4. Cross-module summary

| Module | Residual risks fully open | Residual risks mitigated (by rebuild/comparison) | Residual risks explicitly not applicable |
|---|---|---|---|
| Android | A1 (mapping), A3 (baked config values), A6 (unrecoverable mapping), A9 (toolchain drift) | A2, A5 (structural comparison only — not bitwise) | A8 (container metadata) |
| Server | S1 (git metadata — blocked on task 1.2), S4 (bytecode drift, lower severity) | — (no artifact captured yet to mitigate against) | S2 (secrets, by design), S3 (build timestamps), S5 (Flyway SQL, verified by checksum) |
| Web_Admin | W2 (no source maps), W4 (non-bundled paths) | W1, W5 (both proven via full rebuild-and-diff) | W3 (file replacement, fully source-derived) |

## Explicitly not claimed

- That any path listed as "Open" above will ever become verifiable; some (A6, W4)
  are structurally unrecoverable regardless of future effort.
- That Server's residual-risk table is complete or final. It is provisional until
  task 1.2 produces an actual artifact; S1's severity in particular cannot be
  assessed until then.
- That a "Mitigated" or "Not a gap" entry means the underlying path is
  content-equivalent in the byte-for-byte sense — only that either a rebuild
  proved equivalence for the *specific* recorded baseline, or that the category
  was never a meaningful comparison target in the first place.
- That this document authorizes marking Android, Server, or Web_Admin as
  "reconciled" under Requirement 1.5's own baseline criterion 9. Criterion 9 is
  about whole-module path comparison (task 2.4); this document only prevents any
  of the paths above from being silently folded into that comparison as if they
  were equivalent.
- That the Android and Web_Admin facts cited here were re-verified against the
  current working tree. They are reproduced from the archived task 1.1/1.3 output
  (commit `a2f44c22` on `archive/v2-wip-v1-backport`) because that output is not
  present on this branch; if that branch's history is later rewritten or the docs
  are restored with different findings, this document should be revisited.

## Reproducing this record

```powershell
# Confirm the source docs for 1.1/1.3 are absent here but present in the archive:
git log --oneline --all -- "docs/baseline-provenance/*"
git show a2f44c22:docs/baseline-provenance/android-baseline-commit.md
git show a2f44c22:docs/baseline-provenance/web-admin-baseline-commit.md
git show a2f44c22:docs/baseline-provenance/server-baseline-commit.md

# Confirm the build-configuration facts cited above against the current tree:
Select-String -Path Android/app/build.gradle.kts -Pattern "isMinifyEnabled|isShrinkResources|buildConfigField"
Select-String -Path server/pom.xml -Pattern "git-commit-id|build-info"
Select-String -Path web-admin/angular.json -Pattern "sourceMap|outputHashing|fileReplacements"
```
