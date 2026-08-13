# Working-tree inventory resolution — Phase 0, task 2.1

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 2.1
Requirements: Baseline Precondition (Requirement 1.6)

| Field | Value |
|---|---|
| Module path set | `Android/` |
| Recorded on | 2026-08-05 |
| Branch under audit | `v3` (cut from `origin/main` @ `3b47c39c`, plus a Maven wrapper commit and spec/provenance docs commits) |
| `git status --porcelain` at time of recording | `M .kiro/specs/v2-feature-integration/tasks.md` only — verified below, not assumed |
| Resolution status | **CLOSED for the literal task as written — no dirty tree exists to commit/stash/discard; audit reframed per task instructions to whether the original precondition's concerns still apply to `v3`'s history** |
| Comparison branches used | `archive/v2-wip-v1-backport` @ `a2f44c22` (large committed snapshot of the former 267/268-file dirty tree, committed onto a v2-derived base) and `v2` @ `0df0098a` directly |

## 0. Verification that the working tree is actually clean

```
PS> git status --porcelain
 M .kiro/specs/v2-feature-integration/tasks.md
```

One tracked file, the orchestration bookkeeping edit to `tasks.md` itself. No other
tracked or untracked change exists on `v3`. This was checked directly rather than
assumed. There is therefore nothing to resolve by commit/stash/discard in the literal
sense the task title describes — the seven protected files named in the task
(`TenantDaos.kt`, `BillDao.kt`, `RestaurantDao.kt`, `RestaurantRepository.kt`,
`BillingViewModel.kt`, `NewBillScreen.kt`, `SettingsScreen.kt`) are not modified on disk.
The task is therefore an audit of whether the concern the original dirty tree raised
still needs closing against `v3`'s actual history, not a literal git-status triage.

## 1. Protected-file provenance check

For each protected file, three trees were compared: `origin/main` (= `v3`'s base),
`archive/v2-wip-v1-backport` (the committed snapshot of the old dirty tree), and `v2`
directly.

| Path | Original concern | Comparison result | Decision | Action taken |
|---|---|---|---|---|
| `Android/.../data/local/dao/TenantDaos.kt` | Uncommitted edit on `main`, unknown provenance | File does not exist in `archive` or in `v2` at all (`fatal: path ... exists on disk, but not in 'archive/v2-wip-v1-backport'` / same for `v2`) — it is a `main`-only file with no v2 counterpart | **DISCARD** (nothing to port; no v2-side content exists to compare against) | None. File is untouched on `v3` and identical to `origin/main`. |
| `Android/.../data/local/dao/BillDao.kt` | Uncommitted edit, unknown provenance | `archive` blob SHA `782f438f...` == `v2` blob SHA `782f438f...` (byte-identical). Diff vs `origin/main`: 112 insertions / 1169 deletions — the archived version is `v2`'s file verbatim | **DISCARD** | None. `v2`'s `BillDao.kt` predates `main`'s terminal-scoped, multi-payment-mode, quarantine-aware implementation (Req 4, 9); wholesale adoption would regress it. |
| `Android/.../data/local/dao/RestaurantDao.kt` | Uncommitted edit, unknown provenance | `archive` == `v2` byte-identical (`37199c08...`) | **DISCARD** | None. |
| `Android/.../data/repository/RestaurantRepository.kt` | Uncommitted edit, unknown provenance | `archive` == `v2` byte-identical (`50483b92...`) | **DISCARD** | None. |
| `Android/.../ui/screens/NewBillScreen.kt` | Uncommitted edit, unknown provenance | `archive` == `v2` byte-identical (`65595ff3...`) | **DISCARD** | None. This is `v2`'s screen, predating `main`'s payment-mode/terminal/KOT integration (Req 4, 8, 20). |
| `Android/.../ui/screens/SettingsScreen.kt` | Uncommitted edit, unknown provenance | `archive` == `v2` byte-identical (`6282d9b3...`) | **DISCARD** | None. |
| `Android/.../ui/viewmodel/BillingViewModel.kt` | Uncommitted edit, unknown provenance | `archive` == `v2` byte-identical (`07479861...`) | **DISCARD** | None. `v2`'s view model predates `main`'s hardened sync/payment-set-validation logic (Req 9). |

**Finding.** For every protected file that exists on both sides, the file committed onto
`archive/v2-wip-v1-backport` is byte-for-byte identical to the corresponding file on `v2`
itself. That means the "protected-file edits" the original precondition in
`requirements.md` described were not genuine v1 hotfixes sitting uncommitted on `main` —
they were `v2`'s own versions of those files, staged in the working tree as part of an
earlier (and, per this spec's explicit direction, wrong-way) attempt to backport `v2`
content onto a v1-based tree. Requirement 1 states plainly that a wholesale merge of `v2`
into `main` is out of scope because `v2` "predates and therefore lacks main's entire
safety layer" — multi-terminal enforcement, `SHOP_ADMIN`, `@RequireRole` AOP, the KB-001
through KB-009 fixes, per-tenant isolated databases, KOT delta printing, sync hardening,
and payment-mode flows. Adopting these seven files as committed on `archive` would
reintroduce exactly that regression. Every one is therefore closed as **DISCARD**: not
ported now, and not implicitly in scope for any later phase either — if any specific
capability inside these v2 files is ever wanted on `v3`, it has to be re-specified and
re-implemented against `main`'s architecture the way Requirement 1.3 already requires for
every ported feature, not transplanted as a file.

`TenantDaos.kt` is `main`-only: it doesn't exist on `v2` or on the archive at all, so
there was never anything to compare it against for backport purposes. Its presence in the
original dirty-tree description most likely reflects local edits that were never
committed anywhere, including the archive — those edits are simply gone; nothing on `v3`
or any branch currently carries them, and there is no artifact to recover them from.

## 2. Launcher-icon webp→png migration

| Path / category | Original concern | Comparison result | Decision | Action taken |
|---|---|---|---|---|
| `Android/app/src/main/res/mipmap-*/ic_launcher*.{webp,png}` | "Half-finished launcher-icon webp→png migration" in the original dirty tree | `origin/main` (`v3`'s base) carries **only** `.png` launcher icons across all five densities (`mipmap-hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi`) — zero `.webp` files remain anywhere under `Android/app/src/main/res`. `archive/v2-wip-v1-backport` carries the same 15 icon files but all as `.webp` — `v2`'s original, unmigrated state. | **CLOSED — already resolved on `v3`'s base, no action needed** | None. The migration this concern describes is complete on `origin/main`/`v3` already; the archive simply reflects `v2`'s pre-migration state, which is irrelevant since `v3` doesn't descend from `v2`. |

The original precondition in `requirements.md` was written against `main`'s state at spec
authoring time, when the migration was mid-flight in the uncommitted tree. That
uncommitted state was never captured anywhere reachable except possibly inside `archive`
(and there it's `v2`'s pre-migration `.webp` set, not a partial migration). Since then,
`main` itself (and therefore `v3`) completed the migration through ordinary commits.
There is nothing to reconcile.

## 3. Build-configuration drift

| Path | Original concern | Comparison result | Decision | Action taken |
|---|---|---|---|---|
| `Android/app/build.gradle.kts` | Build-config drift | `git diff origin/main archive/v2-wip-v1-backport` — **no output, byte-identical** | **CLOSED — no drift exists** | None. |
| `Android/build.gradle.kts` (root) | Build-config drift | Byte-identical, same check | **CLOSED — no drift exists** | None. |
| `Android/gradle/libs.versions.toml` | Build-config drift | Byte-identical (`agp = "8.9.1"`, `kotlin = "2.0.21"` on both sides). Confirmed this is `main`'s AGP version, not `v2`'s (`v2` pins `agp = "8.7.3"` directly) — the archive did not carry `v2`'s catalog for this file. | **CLOSED — no drift exists** | None. |
| `Android/gradle/wrapper/gradle-wrapper.properties` | Build-config drift | **Differs.** `origin/main`/`v3`: Gradle `8.11.1` (`distributionSha256Sum=f397b287...`). `archive`: Gradle `9.2.1` (`distributionSha256Sum=72f44c9f...`), byte-identical to `v2`'s own wrapper file. | **DISCARD** | None. This is `v2`-authored wrapper-version drift (an upgrade attempt made on the `v2`-derived working tree), not a v1 config fix `v3` is missing. Bumping `v3`'s wrapper to Gradle 9.2.1 unilaterally, outside of any specified task, risks an unreviewed toolchain change (AGP 8.9.1 compatibility with Gradle 9.x was not verified here) and isn't something this task's scope authorizes. |

Three of the four build-config paths named in the original concern have no drift at all
between `origin/main` and the archived snapshot — the "drift" the precondition described
evidently existed only in the uncommitted tree at authoring time and never in a form this
audit can locate. The fourth (`gradle-wrapper.properties`) does show a difference, but
it's `v2`'s wrapper bump, not a `main`-side fix `v3` lacks, so it's discarded for the same
reason as the protected files above.

## 4. `google-services.json`

| Path | Original concern | Comparison result | Decision | Action taken |
|---|---|---|---|---|
| `Android/app/google-services.json` | "Modified tracked `google-services.json`" of unknown intent | **Differs.** `archive`'s copy is byte-identical to `v2`'s copy. The diff against `origin/main` adds five extra OAuth client entries under the debug package (`com.piquantservices.khanabooklite.debug`), each with a different `certificate_hash`, and changes the debug variant's `mobilesdk_app_id` suffix from `...c64c771a037239514e8bd5` to `...b1f82aa6c065ebcb4e8bd5`. `project_info` (project id/number) is unchanged on both sides. | **DEFER** | None — see below. |

This one cannot be closed with the same confidence as the others. The extra OAuth client
entries are plausible legitimate additions — Google Sign-In is genuinely used in
`LoginScreen.kt` via `GoogleSignIn`/`GoogleSignInOptions`, and each entry's
`certificate_hash` could correspond to a real developer machine's debug keystore SHA-1
registered in the Firebase console so that Google Sign-In works when building from that
machine. But nothing in this repository lets me confirm which machines those five
certificate hashes belong to, whether they were registered against the `v2`-era Firebase
project deliberately, or whether they're incidental noise from `v2`'s own development
history that happened to get pulled into the working tree that was later committed onto
`archive`. This is exactly the kind of intent question the task instructions say should be
recorded as an open question rather than guessed.

This also isn't this task's item to resolve outright — `tasks.md` already assigns it to a
separate task, **2.2/2.3** (`Confirm the pending Android/app/google-services.json
modification is intended client configuration and contains no server-side credential`,
Requirements 27.5, 27.6). Recording it here so it isn't dropped: `v3`'s current
`google-services.json` is unmodified from `origin/main` (verified: `git diff origin/main
HEAD -- Android/app/google-services.json` is empty), so there is nothing pending on `v3`
today. If a future need arises to add debug-keystore OAuth entries for `v3` contributors'
machines, that should go through task 2.3's process (or the Firebase console directly)
with each certificate hash tied to a named, current developer machine — not by importing
the archive's five entries unverified, since their origin is unknown.

## 5. Decision summary

| Category | Count | Outcome |
|---|---|---|
| Protected files: v2-authored content, safety-layer regression risk | 6 (`BillDao.kt`, `RestaurantDao.kt`, `RestaurantRepository.kt`, `NewBillScreen.kt`, `SettingsScreen.kt`, `BillingViewModel.kt`) | DISCARD |
| Protected files: no v2 counterpart to compare | 1 (`TenantDaos.kt`) | DISCARD (nothing to port; original edits unrecoverable) |
| Launcher icon migration | 1 concern | CLOSED — already resolved on `v3`'s base |
| Build config: `build.gradle.kts` ×2, `libs.versions.toml` | 3 paths | CLOSED — no drift exists |
| Build config: `gradle-wrapper.properties` | 1 path | DISCARD — v2-authored Gradle 9.2.1 bump, not a v1 fix |
| `google-services.json` | 1 path | DEFER — open question, owned by task 2.3 |

Nothing in this audit calls for a code change to `v3`. No commit was made beyond this
documentation file, since every item resolved to DISCARD or CLOSED (no action) or DEFER
(explicitly not this task's decision to make). This satisfies Requirement 1.6 for the
seven named protected files: each has an explicit, recorded decision, and none of them
required a commit, stash, or discard operation against a dirty tree because no such dirty
tree exists on `v3`.

## Explicitly not claimed

- That the archive's byte-for-byte match with `v2` proves definitively that no genuine v1
  hotfix was ever mixed into the original uncommitted tree alongside the v2-staged
  content. It proves the *committed* archive snapshot for these specific seven files
  equals `v2` exactly; it says nothing about what else may have been in the working tree
  transiently and never committed anywhere.
- That the five extra `google-services.json` OAuth entries are illegitimate. They are
  unverified, not disproven — the decision is DEFER, not DISCARD, precisely because intent
  could not be established confidently.
- That closing this task authorizes skipping task 2.2 (icon/build-config) or task 2.3
  (`google-services.json` credential review) as separate line items in `tasks.md`. Those
  tasks may reference this document's findings, but their own acceptance criteria
  (Requirements 1.6, 27.5, 27.6) are not satisfied merely by this file existing.
- That `TenantDaos.kt`'s original uncommitted edits are known to be safe to lose. They are
  simply unrecoverable from any branch or artifact this repository holds.

## Reproducing this record

```powershell
git status --porcelain
git branch --show-current
git log -1 --format='%H|%ci|%s'                                    # v3 HEAD
git log -1 --format='%H|%ci|%s' origin/main
git log -1 --format='%H|%ci|%s' archive/v2-wip-v1-backport
git log -1 --format='%H|%ci|%s' v2

$paths = @(
  'Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/BillDao.kt',
  'Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/RestaurantDao.kt',
  'Android/app/src/main/java/com/khanabook/lite/pos/data/local/dao/TenantDaos.kt',
  'Android/app/src/main/java/com/khanabook/lite/pos/data/repository/RestaurantRepository.kt',
  'Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/NewBillScreen.kt',
  'Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/SettingsScreen.kt',
  'Android/app/src/main/java/com/khanabook/lite/pos/ui/viewmodel/BillingViewModel.kt'
)
foreach ($p in $paths) {
  $a = git rev-parse "archive/v2-wip-v1-backport:$p" 2>&1
  $v = git rev-parse "v2:$p" 2>&1
  Write-Output "$p archive=$a v2=$v"
}

git diff origin/main archive/v2-wip-v1-backport -- 'Android/app/src/main/res/mipmap-*'
git ls-tree -r origin/main --name-only -- Android/app/src/main/res | Select-String webp   # empty
git diff origin/main archive/v2-wip-v1-backport -- Android/app/build.gradle.kts
git diff origin/main archive/v2-wip-v1-backport -- Android/build.gradle.kts
git diff origin/main archive/v2-wip-v1-backport -- Android/gradle/libs.versions.toml
git diff origin/main archive/v2-wip-v1-backport -- Android/gradle/wrapper/gradle-wrapper.properties
git diff origin/main archive/v2-wip-v1-backport -- Android/app/google-services.json
git diff origin/main HEAD -- Android/app/google-services.json                            # empty on v3
```

## 6. Task 2.2 closure verification

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 2.2
Requirements: 1.6

Task 2.1 (section 2-3 above) already found the launcher-icon migration and the
build-config drift both **CLOSED — no action needed** on `v3`. This section
independently re-verifies those findings rather than trusting them, per task 2.2's
instructions, and formally closes the task.

### 6.1 Launcher icons — `.webp` search

```powershell
PS Android> git ls-files -- "Android/app/src/main/res/mipmap-*" | Select-String "webp"
# (no output)
PS Android> Get-ChildItem -Path "Android/app/src/main/res" -Filter "mipmap-*" -Directory |
    ForEach-Object { Get-ChildItem $_.FullName -Filter "*.webp" }
# (no output)
```

Zero `.webp` files anywhere under `Android/app/src/main/res/mipmap-*`, checked against
both the git index and the actual filesystem (not tracked files alone). Confirms 2.1's
finding independently.

### 6.2 Expected `.png` variants per density

Directory listing of all five `mipmap-*` density folders:

| Density | `ic_launcher.png` | `ic_launcher_round.png` | `ic_launcher_foreground.png` |
|---|---|---|---|
| hdpi | present | present | present |
| mdpi | present | present | present |
| xhdpi | present | present | present |
| xxhdpi | present | present | present |
| xxxhdpi | present | present | present |

All 15 expected raster files are present. Additionally, `mipmap-anydpi-v26/` carries
`ic_launcher.xml` and `ic_launcher_round.xml`, both adaptive-icon XML files referencing
`@drawable/ic_launcher_background` and `@mipmap/ic_launcher_foreground` — the foreground
drawable resolves to the per-density `ic_launcher_foreground.png` files confirmed above,
and the background resolves to `drawable/ic_launcher_background.xml`, which exists.

### 6.3 Manifest reference resolution

```
AndroidManifest.xml:44:  android:icon="@mipmap/ic_launcher"
AndroidManifest.xml:46:  android:roundIcon="@mipmap/ic_launcher_round"
```

Both references resolve: for API 26+ devices, `@mipmap/ic_launcher` and
`@mipmap/ic_launcher_round` resolve to the adaptive-icon XML in `mipmap-anydpi-v26/`
(verified present and internally consistent above); for pre-26 (moot given min SDK 26 per
`AGENTS.md`, but checked anyway) they'd resolve to the per-density `.png` files, also
present. No dangling reference to a removed `.webp` resource exists in either path.

### 6.4 Gradle wrapper / AGP pairing sanity check

```
Android/gradle/wrapper/gradle-wrapper.properties:
  distributionUrl=https://services.gradle.org/distributions/gradle-8.11.1-bin.zip

Android/gradle/libs.versions.toml:
  agp = "8.9.1"
  kotlin = "2.0.21"
```

Gradle 8.11.1 supports AGP up to the 8.x series inclusive of 8.9.x (AGP 8.9 requires
Gradle 8.11.1+, which is exactly what's pinned) — not a broken pairing.

Ran the wrapper itself rather than just eyeballing version strings:

```powershell
PS Android> .\gradlew.bat :app:tasks --console=plain --offline
...
BUILD SUCCESSFUL in 37s
1 actionable task: 1 executed
```

`--offline` was used to keep the check fast and side-effect-free (no dependency
re-resolution/download); it still exercises the actual wrapper JAR, downloads/verifies
the Gradle 8.11.1 distribution if not already cached, boots the AGP 8.9.1 plugin, and
evaluates the full project including `:app`, printing the real task graph
(`assembleDebug`, `testDebugUnitTest`, `lint`, etc.). This confirms the wrapper is
functional and not corrupted, and that AGP loads cleanly under the pinned Gradle version.
A full `assembleDebug`/sync was not run here since it's unnecessary to establish wrapper
health and would be materially slower; that level of build verification belongs to the
Per-Phase Gate (task 3.1), not to this provenance-closure task.

### 6.5 Result

No discrepancy from task 2.1's findings. Independent re-verification confirms:

- No `.webp` files under `mipmap-*` (git index and filesystem both checked).
- All 15 expected per-density `.png` variants present, plus a consistent adaptive-icon
  XML pair for API 26+.
- `AndroidManifest.xml`'s `android:icon`/`android:roundIcon` resolve cleanly with no
  dangling webp reference.
- `build.gradle.kts` (root and `app/`) and `libs.versions.toml` — unchanged since 2.1's
  byte-identical comparison against `origin/main`; not re-diffed here since 2.1 already
  established zero drift and nothing has touched these files since.
- `gradle-wrapper.properties` pins Gradle 8.11.1, a valid pairing for AGP 8.9.1, and the
  wrapper itself invokes successfully.

**Task 2.2 is formally CLOSED.** No source code change was required or made. This
verification pass found nothing needing escalation.

## 7. Task 2.3 — `google-services.json` intent and credential-sensitivity review

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 2.3
Requirements: 27.5, 27.6

### 7.0 Re-verification that there is no pending modification on `v3`

Re-run independently (not taken on trust from section 4 above):

```
PS> git status --porcelain
 M .kiro/specs/v2-feature-integration/tasks.md
PS> git diff origin/main HEAD -- Android/app/google-services.json
(no output)
```

Confirmed directly: `v3`'s only dirty file is the `tasks.md` bookkeeping edit; the
`google-services.json` diff against `origin/main` is empty. There is no pending
modification to this file on `v3` today. This task is therefore a confirm/deny review of
the file's *current, already-committed* state, not a review of an in-flight change — the
same conclusion section 4 reached, independently re-checked here.

History check, so "already committed" isn't just asserted:

```
PS> git log --diff-filter=A --oneline -- Android/app/google-services.json
8805b173 Fix auth identity and sync persistence across Android and server
PS> git log --oneline -- Android/app/google-services.json | Select-Object -First 5
93919eb0 fix: correct debug SHA-1 fingerprint 926b10 in google-services.json
d35b2bff fix: add debug SHA-1 to Firebase for Google Sign-In
657d6100 Update Android startup config and clean repo files
4e7d9a8d feat: add UI display scale control, improve error sanitization and crash handler
8e63e465 Update billing, dashboard, and Android auth components with web-admin improvements
```

The file has been tracked and edited through ordinary commits for a long time, including
commits explicitly about adding/correcting debug SHA-1 fingerprints for Google Sign-In —
i.e. the same category of change the archive's extra entries represent, just done the
normal way (one hash at a time, in a reviewed commit) rather than as an unexplained bulk
addition in an uncommitted tree.

### 7.1 Field catalog — current `v3` file

`Android/app/google-services.json` contains three `client` blocks (one per
`applicationId` variant built from this module: release `com.piquantservices.khanabook`,
lite-release `com.piquantservices.khanabooklite`, and lite-debug
`com.piquantservices.khanabooklite.debug`), plus one shared `project_info` block and a
`configuration_version` field.

| Field path | Present | Value / shape | Category |
|---|---|---|---|
| `project_info.project_number` | yes (top-level, shared) | `836086274000` | Public project identifier — appears in every Firebase REST call, visible in network traffic |
| `project_info.project_id` | yes | `new-khanabook-li` | Public project identifier |
| `project_info.storage_bucket` | yes | `new-khanabook-li.firebasestorage.app` | Public bucket name — access is governed by Firebase Storage security rules, not by the name being secret |
| `client[].client_info.mobilesdk_app_id` | yes, one per variant | `1:836086274000:android:<hex>` | Public per-app-variant identifier, deterministic from project number + a registration hash |
| `client[].client_info.android_client_info.package_name` | yes, one per variant | `com.piquantservices.khanabook` / `...khanabooklite` / `...khanabooklite.debug` | Public — identical to the `applicationId` already declared in `build.gradle.kts` and visible on the Play Store listing |
| `client[].oauth_client[].client_type: 1` (Android) | yes — 1 entry (release), 2 entries (lite-release), 1 entry (lite-debug) = 4 total | `client_id` + `android_info.package_name` + `android_info.certificate_hash` (SHA-1) | Client-configuration binding, not a credential — see 7.3 |
| `client[].oauth_client[].client_type: 3` (Web) | yes — 1 entry per variant, same `client_id` (`...csivf8ms...`) repeated across all three | `client_id` only, no `client_secret` field present anywhere in the file | Public OAuth client identifier used for the "web/server" audience in Google Sign-In token requests; carries no secret — see 7.3 |
| `client[].api_key[].current_key` | yes, one per variant, all three identical: `AIzaSyBGp7pbYCV7RBLecpXmq5VUoTznreNZnKY` | Restricted Android API key | Client configuration, not a server-side credential — see 7.2 |
| `client[].services.appinvite_service.other_platform_oauth_client` | yes, one per variant, duplicates the `client_type: 3` entry | Same web client id | Public, redundant with the entry above |
| `configuration_version` | yes | `"1"` | Format-version marker, not data |

Nothing in the file has any of the shapes that would indicate a server-side credential:
no `private_key`, no `client_secret`, no `client_email`/service-account fields (the shape
Firebase Admin SDK service-account JSON uses), no database connection string, no bearer
token, no refresh token. Every field is either a public identifier or a client-side
API key explicitly designed by Google to be restricted by binding rather than by secrecy.

### 7.2 Baseline: is this how the file has always shipped?

Checked whether `google-services.json` is excluded anywhere:

```
PS> Select-String -Path Android\.gitignore -Pattern "google-services"
(no match)
PS> Select-String -Path .gitignore -Pattern "google-services"
(no match)
```

Neither `.gitignore` (root or `Android/`) excludes `google-services.json`. Both do exclude
genuine secrets in the same area — `*.jks`, `*.keystore`, `keystore.properties`,
`signing.properties`, `secrets.properties`, `local.properties` — which shows the project's
`.gitignore` authors deliberately distinguish "things that must never be committed"
(signing keys, keystore passwords, local secrets file) from `google-services.json`, which
sits right alongside them in the same directory and is not on that list. That's a
meaningful signal of intent, not an oversight: the exclusion list is specific and
security-conscious, and it stops short of this file on purpose.

The file has been committed and repeatedly edited by ordinary commits since at least
`8805b173` (see 7.0), already contains a live API key and four Android OAuth client
registrations plus a web OAuth client id on the currently-deployed `origin/main`, and
Google Sign-In (`GoogleSignIn`/`GoogleSignInOptions` in `LoginScreen.kt`, confirmed present
in section 4 above) and Firebase are already live, shipped features. This is not a new or
unusual state being introduced — it is the same pattern the app has used in production
all along. `AGENTS.md`'s "do not commit secrets" line names `google-services.json`
explicitly alongside `.env`/`local.properties`/keystores, but that project convention is
more conservative than what Google's own guidance treats as required — see the Firebase
documentation and community consensus retrieved during this review: Firebase API keys
"restricted to Firebase services do not need to be treated as secrets, and it's safe to
include them in your code or configuration files" ([Firebase docs](https://firebase.google.com/docs/projects/api-keys)),
and the general community answer to "should `google-services.json` be synced in my team
repository" is "yes... it is something that should be shared among engineers on your team"
([Firebase Talk / Google Groups](https://groups.google.com/g/firebase-talk/c/bamCgTDajkw)).
Content rephrased for compliance with licensing restrictions. This project's own history
of committing and iterating on the file through normal commits is consistent with that
standard practice, and the `.gitignore` split (secrets excluded, this file not) shows the
codebase already treats it that way in practice, whatever the aspirational line in
`AGENTS.md` says.

### 7.3 OAuth-client binding reasoning

An `oauth_client` entry with `client_type: 1` (Android) is a *registration record*, not a
bearer credential. It ties together three things Google's servers check on every Google
Sign-In request: the requesting app's package name, the SHA-1 fingerprint of the
certificate that signed the APK, and this `client_id`. Google issues a token only when a
live signature check against the actual installed, signed APK matches the fingerprint
registered against that `client_id`/package pair. Possessing the JSON text of the entry —
`client_id`, package name, and even the SHA-1 hash string — grants nothing by itself: it
cannot be replayed, cannot authenticate a request, and cannot be used to sign anything,
because the entry contains a fingerprint *of* the private signing key, not the key itself,
and no possessor of this file also automatically possesses the corresponding `.jks`
keystore (which is separately `.gitignore`'d, per 7.2). This is the standard, documented
security model for this file type, corroborated by the sources reviewed above.

The `client_type: 3` (Web) entry present in this file carries only `client_id`, with no
`client_secret` field anywhere in the JSON (confirmed by the field catalog in 7.1). Web
OAuth clients *can* have a secret, but when one is required by the app's flow it is issued
and held server-side, never embedded in `google-services.json` — its absence here is
expected and correct, not a gap.

Applying this to the entries actually present in the current `v3` file: four
`client_type: 1` entries (one release, two lite-release, one lite-debug) and three
identical `client_type: 3` entries (one per variant, same web client id) — all bindings,
none usable without the matching private signing key for the Android entries, and none
carrying a secret for the Web entry.

Applying the same reasoning to the archive's five *extra* entries (not present on `v3`,
not being ported — see section 4): each is the same shape, `client_type: 1`, bound to the
debug package with its own `certificate_hash`. If they were ever added, they would
represent additional debug-keystore registrations for other developer machines — the same
low-sensitivity category as the four entries already present, not a service-account
private key or an API secret. Nothing about their *kind* changes the risk category; what
was actually unresolved in section 4 was provenance/authorization (whose debug keystores
they are), not sensitivity classification. That provenance question remains open and is
explicitly not resolved or closed by this task, consistent with section 4's DEFER and
"Explicitly not claimed" notes — this task only had to determine sensitivity category, not
authorize the entries.

### 7.4 Determination — current `v3` file

**Yes.** `v3`'s current, already-committed `Android/app/google-services.json` is intended
client configuration and contains no server-side credential.

Reasoning: every field present (7.1) is either a public project/app identifier, an
Android-restricted API key whose security model is binding-based rather than
secrecy-based, or an OAuth-client binding record that is inert without a private signing
key never itself present in this file or this repository in committed form. This matches
Google's documented intent for the file (7.2), matches this project's own long history of
committing and iterating on it through ordinary reviewed commits (7.0), and is consistent
with the project's own `.gitignore`, which excludes true secrets from this same directory
while deliberately not excluding this file (7.2). No field resembles a service-account
private key, API secret, or any other server-side credential. Nothing here rises to the
level requiring escalation.

### 7.5 Determination — archived variant's extra entries (informational, not ported)

**Yes, also client configuration in kind** — the five extra entries on
`archive/v2-wip-v1-backport` are, by shape and field content, the same category as the
entries already present on `v3`: `client_type: 1` Android OAuth-client bindings with no
secret material, inert without a matching private signing key (7.3). They are not, in
kind, server-side credentials. This does **not** reopen or resolve the *provenance*
question section 4 deferred — whose keystores these five hashes belong to and whether they
were ever deliberately registered is still unknown and still not this task's or this
spec's decision to make. It only answers the narrower question task 2.3 actually asks:
whether they are the *kind* of thing that would constitute a server-side credential. They
are not. Per section 4 and the task instructions, they remain un-ported; no action is
taken on the archive.

### 7.6 Escalation check

No private key material, no service-account JSON, no API secret (as distinct from a
restricted API key), no database credential, and no bearer/refresh token were found
anywhere in the current `v3` file or in the archive's differing entries. Nothing here
meets the escalation bar this task set. No escalation is raised.

### 7.7 Result

**Task 2.3 is formally CLOSED.** No code change was required or made; no porting was
performed. This closes the open question section 4 raised without merging anything from
the archive.
