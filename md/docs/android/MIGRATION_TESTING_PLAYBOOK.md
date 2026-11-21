# Migration Testing Playbook

## Context: v35 (1.0.35) Release — End-State Repair

### Issue
Crashlytics issue `9a3ca081c04bd86dbe4f4a49073a4970`: "Migration didn't properly
handle: bills(...)" on OnePlus CPH2423 (Android 14, app 1.0.33).

Real devices upgrading through 78 incremental migrations from v18 accumulate
defects that the fresh-install schema never has. `runMigrationsAndValidate`
compares the end-state `TableInfo` against the 79.json schema JSON — exact
match required on every column, index, FK, and default.

### Root Causes Identified

| Table            | Defect                                                                 |
|------------------|------------------------------------------------------------------------|
| `bill_payments`  | `amount` stuck as REAL affinity (should be TEXT); missing indices and columns |
| `restaurant_profile` | Missing `collect_customer_number` and ~15 other columns from early migrations |
| `bills`          | Stale indices from dropped entity definitions; orphaned ALTERs          |
| `users`          | Role default `'owner'` instead of `'OWNER'`; `pin_hash` never dropped    |
| `easebuzz_enabled` | MIGRATION_66_67 double-adds a column already created by MIGRATION_39_40  |

### Solution: MIGRATION_78_79 Rebuild

Rebuilt 5 tables wholesale to exact 79.json DDL — `users`, `bills`,
`bill_items`, `bill_payments`, `restaurant_profile`. Each follows the same
pattern:

```
CREATE TABLE _new (...)           -- exact 79.json columns + types + defaults
INSERT INTO _new (...)            -- per-column hasColumn guards for missing cols
  SELECT hasColumn("x") ? `x` : fallback FROM _old
DROP TABLE _old
ALTER TABLE _new RENAME TO _old
CREATE INDEX ...                  -- recreate all indices from 79.json
```

**Key detail missed in v1 (caught by sweep):**
`bill_payments` had table + columns + FKs correct but was missing both
indices (`index_bill_payments_bill_id` non-unique, `idx_bill_payments_restaurant_operation`
unique). Added the two `CREATE INDEX` statements after the rename.

---

## Testing Methodology

### MigrationChainSweepTest

`MigrationChainSweepTest.kt` sweeps **all 54 startable schema versions** (18–78,
excluding 46/49/50/55/56 which lack committed schema JSONs) in a single test
run. For each version V:

1. Create database from `schemas/79.json` asset at version V
2. Run full migration chain via `runMigrationsAndValidate`
3. Validate end-state TableInfo matches the 79.json schema JSON
4. Capture any failure with "Expected vs Found" TableInfo diff

**To run:**
```bash
# Build + install test APK
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Run (correct instrumentation — NOT the .test package name)
adb shell am instrument -w \
  -e class com.khanabook.lite.pos.data.local.MigrationChainSweepTest \
  com.piquantservices.khanabooklite.debug.test/com.khanabook.lite.pos.test.util.HiltTestRunner

# Pull the report
adb shell "run-as com.piquantservices.khanabooklite.debug cat \
  /data/data/com.piquantservices.khanabooklite.debug/files/migration-sweep.txt"
```

**Gotchas:**
- Network must be off during testing (FCM crash swamps the result)
- `adb uninstall` between runs to avoid stale state
- Instrumentation FQCN is `HiltTestRunner`, not `AndroidJUnitRunner`
- Test application ID differs from main: `com.piquantservices.khanabooklite.debug.test`
- Report written to `filesDir/migration-sweep.txt` (full output; logcat chunks are truncated)

---

## Future Prevention

### 1. CI Gate: Migration Sweep on Every PR
Add a GitHub Action that runs `MigrationChainSweepTest` before merge:
```yaml
# .github/workflows/migration-test.yml
- name: Run migration sweep
  run: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=MigrationChainSweepTest
```

### 2. Pre-Merge Schema Validation
Run `room.schemaLocation` validation against `runMigrationsAndValidate` as part
of `./gradlew check`. This catches index/column drift before it reaches a
release.

### 3. hasColumn Guard Convention
**Every** `ALTER TABLE ADD COLUMN` in any migration must be wrapped:
```kotlin
if (!db.hasColumn("table_name", "column_name")) {
    db.execSQL("ALTER TABLE table_name ADD COLUMN column_name TYPE ...")
}
```
This prevents crashes on devices whose prior ALTERs silently failed.

### 4. Index Audit Before Release
Before committing a schema bump, diff the old and new JSON schema files:
```bash
diff <(jq '.database.entities[].tableName' schemas/old.json) \
     <(jq '.database.entities[].tableName' schemas/new.json)
```
Verify every table's `indices` array matches between old→new for rebuilt tables.

### 5. ProGuard Gson Keep Rules (already committed)
The Login 400 fix (f583da8) added Gson type-adapter keep rules for API response
models. This prevents `VerifyOtpResponse` / `RefundBillRequest` deserialization
crashes in release builds with R8 minification.
