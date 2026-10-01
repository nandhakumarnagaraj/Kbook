package com.khanabook.lite.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khanabook.lite.pos.core.database.AppDatabase
import org.junit.Assert.assertTrue
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
 * from the schema asset, runs the chain to 78, and validates.
 *
 * Versions without a committed schema JSON (46, 49, 50, 55, 56) cannot be
 * created here; their SQL paths are still covered because every sweep that
 * starts below them (e.g. 45 → 78, 54 → 78) executes those steps.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainSweepTest {

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
            76, 77, 78
        )

        val failures = StringBuilder()
        for (v in startable) {
            val dbName = "sweep-$v"
            try {
                helper.createDatabase(dbName, v).close()
                val db = helper.runMigrationsAndValidate(
                    dbName, 79, true,
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
