# Android_Baseline_Commit — provenance record

Spec: `.kiro/specs/v2-feature-integration` — Phase 0, task 1.1
Requirements: Baseline Precondition criteria 1, 2, 3 (Requirement 1 / Req 1.1–1.3)

| Field | Value |
|---|---|
| Module path set | `Android/` |
| Recorded on | 2026-07-28 (repo HEAD date; see HEAD below) |
| Recorded from | Local repository evidence only — Play Console was not reachable from the recording environment |
| Resolution status | **UNRESOLVED — narrowed to a 6-commit candidate window** |
| Provenance kind | **Undetermined.** Neither criterion 2 (resolved commit SHA) nor criterion 3 (artifact-derived snapshot) can be satisfied without the Play artifact |
| Blocks | Task 2.4 (per-module diff against Baseline_Candidate) and therefore Baseline_Tag |

Nothing in this record is inferred from the deployed artifact. Every row below is
reproducible from this repository with the command shown.

---

## 1. Facts established locally

| Fact | Value | Evidence |
|---|---|---|
| Repo HEAD at recording | `0927ff11480a453f823e4430373fdbd2ce597f61` (2026-07-28 10:39:07 +0000) `fix(android): guard terminal daily counter against duplicate daily_order_id` | `git log -1` |
| Branch | `main` | `git rev-parse --abbrev-ref HEAD` |
| Remote | `https://github.com/nandhakumarnagaraj/Kbook.git` | `git remote -v` |
| Release tags in repo | **none** — the repository carries zero tags, so no release-tag record exists for any Android build | `git tag --list` (empty) |
| Committed `versionCode` default | `20` | `Android/app/build.gradle.kts:44` |
| Committed `versionName` default | `1.0.11` | `Android/app/build.gradle.kts:45` |
| Commit that set 20 / 1.0.11 | `11dc2399a55f3ea6c4d7a03b5dd060b456a2281d` (2026-06-05 16:18:46 +0530) `chore: bump version to 20 (1.0.11) for v1 release` — changed `19`/`1.0.10` → `20`/`1.0.11`, single file | `git log -S'"20"' -- Android/app/build.gradle.kts` |
| Later change to the version default | **none** — `11dc2399` is the only commit that alters `RELEASE_VERSION_CODE` / `RELEASE_VERSION_NAME` defaults after they were introduced, so the default stays `20`/`1.0.11` for every commit from `11dc2399` to HEAD | `git log -S'RELEASE_VERSION_CODE' -- Android/app/build.gradle.kts` |
| `applicationId` (release) | `com.piquantservices.khanabooklite` | `Android/app/build.gradle.kts:86` |
| Room `AppDatabase` version at HEAD | `62` | `Android/app/src/main/java/.../data/local/AppDatabase.kt:28` |
| Commit that set Room version 62 | `ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2` (2026-07-24 01:29:32 +0530) `feat: harden billing sync and terminal management`; no later commit changes it | `git log -S'version = 62' -- .../AppDatabase.kt` |
| Corroborating in-repo statements of the shipped version | `docs/KHANABOOK_FULL_TECHNICAL_SPEC.txt:995` "Current Version: 1.0.11 (versionCode 20)"; `docs/VERSION_ANALYSIS.txt:19`; `docs/billing-sync-fix-verification.md:7` | grep |
| Release CI | **none.** `.github/workflows/{ci,gated-tests,web-admin}.yml` and `Android/.github/workflows/android-tests.yml` run tests only — no `assembleRelease`, no `bundleRelease`, no Play upload step. There are therefore **no CI build records** to correlate an artifact to a commit | grep for `assembleRelease\|bundleRelease\|upload` |

### 1.1 The version is not pinned in the repo — it is overridable per machine

`Android/app/build.gradle.kts` resolves the version through `configValue(...)`, which
prefers, in order: `local.properties` → Gradle property → environment variable →
hardcoded default. The committed default is `20`/`1.0.11`, but any machine can build a
different version code without a repository change.

**This machine currently overrides it.** `Android/local.properties` (git-ignored) sets:

```
RELEASE_VERSION_CODE=21
RELEASE_VERSION_NAME=1.0.12
BACKEND_URL=https://kbook.iadv.cloud/
```

Confirmed by the most recent local build output,
`Android/app/build/outputs/apk/debug/output-metadata.json`, which reports
`versionCode: 21`, `versionName: "1.0.12"`.

Consequences for provenance:

- A release build produced on this machine **today** would carry versionCode 21, not 20.
  The production versionCode-20 artifact was therefore built either before this override
  was set, or on a different machine, or with the override temporarily set to 20.
- Because the version is not derived from git, **versionCode 20 does not identify a
  commit.** It only identifies "some tree whose effective version resolved to 20".
- The APK/AAB embeds no commit identifier. There is no `git-commit-id` equivalent on the
  Android side (unlike the server, which exposes `git.commit.id` via actuator — see task 1.2).

## 2. Candidate commit window

Two independent constraints narrow the window. Both are stated with their assumption.

**Constraint A (repo-verified): `versionCode` default = 20.**
Holds for every commit from `11dc2399` to HEAD → 61 commits touch `Android/` in that range.

**Constraint B (assumed, not artifact-verified): production Android is at Room DB version 62.**
This is asserted in `requirements.md` ("shipping Android 1.0.11 (versionCode 20), Room DB
version 62") but that assertion was derived from inspecting `main`, not from reading a
production device or the deployed artifact. If it holds, the window shrinks to
`ad0d2623..HEAD`, because Room 62 first exists at `ad0d2623`.

Applying both, the candidate set is 6 commits with 6 distinct `Android/` trees:

| Commit | Date | `git rev-parse <c>:Android` (tree SHA) | Subject |
|---|---|---|---|
| `ad0d2623050c6cef61ffcbd87042f8a60bb7d6b2` | 2026-07-24 01:29 +0530 | `9f524b3c576789ba8883c6eebe828f4487bda48f` | feat: harden billing sync and terminal management |
| `c8b95a9078b470b6493927d1107bc9bd455a811a` | 2026-07-25 12:26 +0530 | `fcff92d0bf543d13a5276e77f5f203bd5f71be93` | fix(android): enforce restaurant-scoped payment operation uniqueness |
| `523bfd8d45940ca106b6a51c5341fbdf14cf1ad7` | 2026-07-25 12:54 +0530 | `d7e1b3d1ad9501c1c56e4c51d80c42674015fe4a` | fix(android): prevent stale sync acknowledgements from hiding bill changes |
| `cf7eab9f823e354fefdaa00c930213ccc440d462` | 2026-07-25 13:40 +0530 | `6f67110a07a7cefb25dc691a0c8a1a4fbce1220a` | fix(android): reject bill completion without valid payments |
| `970eb15c1b3c5c6266532c4e6ef8c64d2423c410` | 2026-07-25 19:22 +0530 | `8618f185d7faea99b7561b3c2ae9081df2109801` | fix(android): responsive layout for tablets and remove shop name from UPI QR |
| `0927ff11480a453f823e4430373fdbd2ce597f61` | 2026-07-28 10:39 +0000 | `890f981b169f5bf7554f65cddc94fa5e5c0ed913` | fix(android): guard terminal daily counter against duplicate daily_order_id (HEAD) |

`e36e2294` and `e0198218` are omitted: their `Android/` tree is byte-identical to
`970eb15c` (`8618f185…`), so they are indistinguishable from it for provenance purposes.

If Constraint B is dropped, the window widens to all 61 `Android/`-touching commits in
`11dc2399..HEAD`.

**Seventh hypothesis that cannot be excluded:** the artifact was built from a dirty
working tree, in which case no commit in the window is content-equivalent and criterion 3
(artifact-derived snapshot) applies. The working tree is dirty **now** — 42 modified/deleted
tracked files plus 20 untracked paths under `Android/` — so this is not a hypothetical
failure mode for this repository.

## 3. Signing evidence collected locally

Two release keystores exist on this machine, both git-ignored (`Android/.gitignore:17 *.jks`)
and neither tracked (`git ls-files -- '*.jks'` is empty):

| File | Size | Modified | Used by current config? |
|---|---|---|---|
| `Android/app/khanabook-release-key.jks` | 2798 B | 2026-05-02 01:28 | **Yes** — `local.properties` sets `SIGNING_STORE_FILE=khanabook-release-key.jks`, resolved relative to the `app` module |
| `Android/release-key.jks` | 2828 B | 2026-05-01 17:25 | No |

Because `hasReleaseSigning` is true on this machine, the debug variant is signed with the
release key, so the release certificate identity is recoverable from the local debug APK
without touching any password:

```
apksigner verify --print-certs Android/app/build/outputs/apk/debug/app-debug.apk
```

```
V2 Signer: certificate DN: CN=Nandhakumar Nagaraj, OU=Development, O=India Advocacy,
           L=Chennai, ST=Tamil Nadu, C=IN
V2 Signer: certificate SHA-256 digest: 6c308ad9f3ea4768582201ded4d5771cf1f5b82bf9b071aa940a654a29fdd2bb
V2 Signer: certificate SHA-1   digest: 926b1008437b87d31dcf092cd1c236e9dda95200
```

This is the certificate held in `Android/app/khanabook-release-key.jks`. It is a
**candidate** upload certificate: nothing local proves it is the certificate Play accepted
for the versionCode-20 upload, and the presence of a second, unused keystore makes that
worth checking rather than assuming. No keystore password was read or recorded.

## 4. Items that cannot be resolved locally — operator confirmation required

Each item lists the exact step. Until every item is closed, `Android_Baseline_Commit`
stays UNRESOLVED and the Android module cannot be marked reconciled under criterion 9.

**A. Confirm the versionCode actually distributed to merchants.**
Play Console → app `com.piquantservices.khanabooklite` → Release → Production → track
history. Record: the version code on the production track, its rollout percentage, and
whether a staged rollout means more than one version code is live simultaneously.
*Why it matters:* the whole plan assumes a single live version code of 20. A partial
rollout would mean two Deployed_Module_Representations for one module.

**B. Confirm the upload certificate fingerprint.**
Play Console → Release → Setup → App integrity → App signing. Compare the **upload key**
SHA-256 against `6c308ad9…fdd2bb` above.
*If they differ:* `Android/app/khanabook-release-key.jks` did not sign the live artifact,
and the keystore that did must be located before any future release can be published.

**C. Download the deployed artifact and read its true version.**
Play Console → Release → App bundle explorer → select version code 20 → Downloads →
"Signed, universal APK". Then:
```
aapt2 dump badging <universal.apk> | findstr package
apksigner verify --print-certs <universal.apk>
```
Record `versionCode`, `versionName`, `compileSdkVersion`, `targetSdkVersion`, and the
signer chain.

**D. Determine whether the artifact resolves to a commit (criterion 2) or requires a
snapshot (criterion 3).**
For each of the 6 candidates in §2, build with the version pinned to the production values
and compare against the downloaded artifact:
```
cd Android
git worktree add ../baseline-probe-<short-sha> <commit>
./gradlew.bat :app:assembleRelease -PRELEASE_VERSION_CODE=20 -PRELEASE_VERSION_NAME=1.0.11
```
Compare the resulting APK to the Play universal APK on: `resources.arsc` entries,
`AndroidManifest.xml` (permissions, activities, receivers, services), the Room schema
identity hash embedded in the compiled `AppDatabase`, and per-class DEX presence.
R8/ProGuard (`isMinifyEnabled = true`, `isShrinkResources = true`) makes byte-for-byte DEX
equality unreachable, so the comparison is structural, not bitwise.
- Exactly one candidate matches structurally → record its SHA as `Android_Baseline_Commit`,
  provenance kind = **resolved-commit**.
- No candidate matches → the artifact came from an unrecorded tree. Materialise the
  artifact-derived snapshot required by criterion 4 (decompile the universal APK to a
  comparable tree of `Android/` paths), record provenance kind = **artifact-derived**, and
  hand the unreproducible paths to task 1.4 as residual provenance risk under criterion 5.
- More than one candidate matches → the differing commits are behaviourally invisible in
  the artifact; record the earliest as the representation and list the rest as
  indistinguishable.

**E. Recover the build record for the versionCode-20 upload.**
Play Console shows the upload timestamp and uploading account. Correlate that timestamp
against §2's commit dates to eliminate candidates that did not exist yet. Also check
whether the build machine still holds `Android/app/build/outputs/bundle/release/` from that
date, and whether its `output-metadata.json` survives — that file records the exact
version code emitted.
*Constraint already known:* the upload cannot have been produced by CI, because no release
workflow exists (§1). It was a local build, so the only build record is on whatever machine
produced it.

**F. Confirm the production Room database version on a merchant device.**
Needed to validate or discard Constraint B in §2. Read `PRAGMA user_version` (or the
`room_master_table` identity hash) from a device running the live build, or read the Room
version the decompiled artifact declares once C is done.

## 5. Explicitly not claimed

- That `0927ff11` (HEAD) is the deployed Android commit. It is one of six candidates.
- That any candidate is content-equivalent to the deployed artifact. No artifact was compared.
- That the deployed artifact was built from a committed tree at all.
- That `6c308ad9…fdd2bb` is the certificate Play holds for this app.
- That versionCode 20 is the only version code currently live.
- That production Android runs Room DB version 62. That is an inherited assertion, not a
  measurement.

## 6. Reproducing this record

```powershell
git rev-parse --abbrev-ref HEAD
git log -1 --format='%H|%ci|%s'
git tag --list                                   # empty: no release tags
git log --format='%h|%ci|%s' -S'RELEASE_VERSION_CODE' -- Android/app/build.gradle.kts
git log --format='%h|%ci|%s' -S'"20"'             -- Android/app/build.gradle.kts
git log --format='%h|%ci|%s' -S'version = 62'     -- Android/app/src/main/java/com/khanabook/lite/pos/data/local/AppDatabase.kt
git log --format='%h|%ci|%s' ad0d2623^..HEAD      -- Android/
foreach ($c in @('ad0d2623','c8b95a90','523bfd8d','cf7eab9f','970eb15c','0927ff11')) {
  "$c $(git rev-parse $c) $(git rev-parse "$c`:Android")"
}
git ls-files -- '*.jks'                          # empty: keystores untracked
git rev-list --count 11dc2399..HEAD -- Android/  # 60, +1 for 11dc2399 itself = 61
$s = git status --porcelain=v1 -- Android/
($s | Where-Object { $_ -notmatch '^\?\?' }).Count   # 42 tracked-changed
($s | Where-Object { $_ -match  '^\?\?' }).Count     # 20 untracked
```
