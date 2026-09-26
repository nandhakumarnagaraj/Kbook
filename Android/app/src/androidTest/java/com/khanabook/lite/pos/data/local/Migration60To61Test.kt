package com.khanabook.lite.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class Migration60To61Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration60To61_addsPaymentAttemptColumns() {
        val db60 = helper.createDatabase(TEST_DB_NAME, 60)
        db60.close()

        val db61 = helper.runMigrationsAndValidate(TEST_DB_NAME, 61, true, AppDatabase.MIGRATION_60_61)

        assertTrue(db61.hasColumn("bills", "payment_attempt_status"))
        assertTrue(db61.hasColumn("bills", "payment_attempt_started_at"))

        db61.query("SELECT payment_attempt_status FROM bills LIMIT 1").use { cursor ->
            assertEquals(1, cursor.columnCount)
        }

        db61.close()
    }

    @Test
    fun migration60To61_existingRowsGetDefaultStatus() {
        val db60 = helper.createDatabase(TEST_DB_NAME, 60)
        db60.execSQL(
            "INSERT INTO bills (id, restaurant_id, device_id, daily_order_id, daily_order_display, subtotal, total_amount, payment_mode, payment_status, order_status, created_at, updated_at, last_reset_date) " +
            "VALUES (1, 100, 'device-1', 1, 'INV-001', '100.0', '100.0', 'CASH', 'paid', 'completed', 1700000000, 1700000000, '2024-01-01')"
        )
        db60.close()

        val db61 = helper.runMigrationsAndValidate(TEST_DB_NAME, 61, true, AppDatabase.MIGRATION_60_61)

        db61.query("SELECT payment_attempt_status, payment_attempt_started_at FROM bills WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("none", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }

        db61.close()
    }

    private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        const val TEST_DB_NAME = "migration-test-db"
    }
}
