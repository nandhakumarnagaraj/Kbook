package com.khanabook.lite.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khanabook.lite.pos.core.database.AppDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sweeps EVERY historical starting schema version and migrates it to the
 * current version with the full registered chain, validating the end schema.
 *
 * Motivation: a real device (OnePlus CPH2423, app 1.0.33) crash-looped with
 * "Migration didn't properly handle: bills" — the chain RAN but the end
 * schema was wrong, meaning at least one migration in 26→73 produces a table
 * that does not match its schema JSON. This sweep pinpoints every such step
 * in a single run: for each startable version V it creates a database at V
 * from the schema asset, runs the chain to the newest registered migration,
 * and validates the end schema against that version's committed JSON.
 *
 * Versions without a committed schema JSON (46, 49, 50, 55, 56) cannot be
 * created here; their SQL paths are still covered because every sweep that
 * starts below them (e.g. 45 → current, 54 → current) executes those steps.
 *
 * The end version is DERIVED from `ALL_MIGRATIONS`, never written down: this
 * used to end at 79, so 79→80 (is_favourite) and 80→81
 * (menu_item_category_repairs) were never migrated or validated on a device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MigrationChainSweepTest {

    /**
     * Required for the run to survive at all, not for the migrations. The app's
     * FirebaseMessagingService is created by FCM while the instrumentation is running, and under
     * the test application a Hilt-injected component can only be built once a component exists -
     * without this rule the process died 1.5s into the sweep with "The component was not
     * created. Check that you have added the HiltAndroidRule", so nothing was ever validated on
     * a device. The rule does not touch the databases, the chain or the end-schema validation.
     */
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun everyStartingVersionMigratesCleanlyToCurrentVersion() {
        val startable = intArrayOf(
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 47, 48, 51, 52, 53, 54, 57, 58,
            59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75,
            76, 77, 78, 79, 80
        )
        val latestVersion = AppDatabase.ALL_MIGRATIONS.maxOf { it.endVersion }

        val failures = StringBuilder()
        for (v in startable) {
            val dbName = "sweep-$v"
            try {
                helper.createDatabase(dbName, v).close()
                val db = helper.runMigrationsAndValidate(
                    dbName, latestVersion, true,
                    *AppDatabase.ALL_MIGRATIONS
                )
                db.close()
            } catch (t: Throwable) {
                failures
                    .append("v").append(v)
                    .append(": ").append(t.javaClass.simpleName).append(" — ")
                    .append(t.message ?: "no message")
                    .append("\n\n")
            }
        }

        // The instrumentation truncates long failure messages and logcat drops
        // lines under pressure, so the full report is written to the app's
        // external files dir and pulled with:
        //   adb exec-out run-as <pkg> cat <path> > sweep.txt
        // (logcat chunks are kept as a fallback.)
        val full = if (failures.isEmpty()) "ALL CLEAN" else failures.toString()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val out = java.io.File(ctx.filesDir, "migration-sweep.txt")
        out.writeText(full)
        android.util.Log.i("MIGRATION_SWEEP", "REPORT_PATH: " + out.absolutePath)
        var i = 0
        var chunk = 0
        while (i < full.length) {
            android.util.Log.i(
                "MIGRATION_SWEEP",
                "CHUNK_" + chunk + ": " + full.substring(i, minOf(i + 3500, full.length))
            )
            i += 3500
            chunk++
        }

        assertTrue(
            "Migration chain failures from " +
                failures.split("\n\n").count { it.startsWith("v") } +
                " starting versions — full diffs in logcat tag MIGRATION_SWEEP",
            failures.isEmpty()
        )
    }
}
