# 01 — Project Structure Audit

**Scope:** folder organisation, naming, separation of concerns, build topology.
**Evidence:** `ls`, `find`, `git ls-files`, `wc -l` on `main` @ `a733025b`.

## 1. Current layout

```
/
├── apps/
│   ├── android/          Kotlin + Compose POS (Gradle, single :app module)
│   └── web-admin/        Angular 18 owner/admin console
├── services/
│   └── server/           Spring Boot 3.5.12 API (Maven)
├── docs/                 product, api, adr, runbooks, audit, qa, specs…
├── ANDROID_POS_DOCUMENTATION/   device-verified POS knowledge base [V]
├── ops/                  apache config, deployment assets
├── artifacts/            screenshots and device evidence
├── openspec/             change proposals / specs
├── mcp/, env/, secrets/, backups/, md/, graphify-out/
└── AGENTS.md, AI_ENGINEERING_RULES.md, CLAUDE.md, REQUIREMENT_IMPLEMENTATION_GAPS.md
```

The `apps/` + `services/` split is the right call and demonstrably current: commit `324fefe5` ("chore(repo): split the repo into apps/ and services/") moved everything, and all CI workflows reference the new paths (`working-directory: apps/android`, `services/server`) [V].

## 2. Findings

### AUD-008 (P3) — stale untracked duplicates at the repository root [V]

`Android/`, `server/` and `web-admin/` still exist at the root, dated 2026-10-17 (pre-split). They are **not tracked by git** (`git ls-files server` and `git ls-files web-admin` both return nothing), so they are local residue rather than repository content.

Why it matters:
- A `grep -r` across the repo silently searches a second, stale copy of the code, which produces contradictory results (this audit confined every search to `apps/` and `services/` for that reason).
- An IDE opened at the root may index both trees.

**Correction (post-audit check):** they are already ignored — `.gitignore:164-168` lists `/web-admin/`, `/server/` and `/Android/`. The original recommendation to "add them to .gitignore" was wrong. The only remaining action is deleting the local copies after confirming nothing unique lives there; this is a local action, not a code change.

### Module granularity [C]

- **Directory-per-feature, not layer-per-module.** Server packages are `feature/<domain>/{controller,service,repository,data,domain}`; Android is `feature/<domain>/{ui,viewmodel,data,domain}`. This is a genuine strength: a feature can be read, tested and deleted as a unit.
- **Android is a single Gradle module** (`:app`). There is no `:core`, `:feature:billing` module split. At 261 files / ~60k lines this is still tractable, but build times and the 2,387-line `AppDatabase.kt` show the cost.
- **Server has no Maven multi-module split** either: one jar, 49 controllers, 353 classes.

### Naming and conventions [V]

Consistent and enforceable:
- Server: `*Controller`, `*Service`/`*ServiceImpl`, `*Repository`, `*Entity`/`*Dto`.
- Android: `<Screen>Screen.kt`, `<Name>View.kt`, `<Name>ViewModel.kt`, `<Name>Repository.kt`, `<Name>Dao.kt`.
- Flyway: `V<n>__lower_snake_case.sql` — 103 files, no gaps in the `V1..V121` sequence apart from the discarded `V9` (see `06`).

**Inconsistency worth noting:** two UI dialects coexist on Android — `*Screen.kt` (nav destinations) and `*View.kt` (embedded views such as `ShopConfigView`, `ChangePasswordView`). It is followed consistently, but it is undocumented; a new contributor will guess wrong. Recommendation: one paragraph in `ANDROID_POS_DOCUMENTATION/01_APPLICATION_OVERVIEW.md` stating the rule.

### Separation of concerns [C]

Mostly clean, with three documented exceptions:

| Location | Lines | Concern |
| --- | --- | --- |
| `apps/web-admin/src/app/pages/business-dashboard/business-dashboard-page.component.ts` | 3,901 | data loading, derived metrics, layout and chart config in one component |
| `apps/web-admin/src/app/pages/menu/menu-page.component.ts` | 2,398 | ditto |
| `apps/android/.../core/database/AppDatabase.kt` | 2,387 | 18 entities, all DAOs, all migrations in one file |
| `apps/android/.../feature/sync/domain/MasterSyncProcessor.kt` | 2,054 | every master-data entity handled in one processor |
| `services/server/.../feature/sync/service/GenericSyncService.java` | 1,704 | generic push/pull for every syncable entity |

These are analysed further in `09-ARCHITECTURE-AUDIT.md` and `13-TECHNICAL-DEBT.md` (AUD-009).

### Build reproducibility [V]

Strong:
- `gradle.properties` explicitly forbids a committed `org.gradle.java.home` because "CI runs it on Linux, where a Windows JDK path makes Gradle abort" — a real bug already learned from.
- CI synthesises a stub `google-services.json` because the Firebase plugin hard-fails without it, with the two required application ids documented in `ci.yml`.
- Release build has `isMinifyEnabled = true` with `proguard-android-optimize.txt` (`apps/android/app/build.gradle.kts:123`).
- Server pins `spring-boot-starter-parent 3.5.12`, Java 17, and wraps Maven (`./mvnw -B test`).

Weaknesses:
- Two independent Android workflows (`ci.yml` and `android-ci.yml`) both trigger on `apps/android/**` for push and PR. That is duplicated CI cost and two places to drift; `ci.yml` already covers unit tests.
- No `gradle/libs.versions.toml` version catalogue in the Android tree — dependency versions are declared inline in `app/build.gradle.kts`.

## 3. Recommendations (no new functionality)

1. **AUD-008:** remove the root duplicate trees; ignore them at the root.
2. Delete the redundant `android-ci.yml` **or** reduce it to the instrumented job that `ci.yml` does not run — do not keep two full copies of the same gate.
3. Introduce `gradle/libs.versions.toml` and migrate Android dependency coordinates (mechanical, no behaviour change).
4. Document the `*Screen.kt` vs `*View.kt` convention and the `feature/<domain>/{ui,viewmodel,data,domain}` layout in the Android overview.
5. Leave module counts alone. Splitting `:app` into Gradle modules is a real improvement but a large, risky refactor with no user-visible benefit; sequence it after `14-IMPROVEMENT-ROADMAP.md` P0/P1 work.
