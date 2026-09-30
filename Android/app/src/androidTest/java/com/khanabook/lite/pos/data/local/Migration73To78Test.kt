package com.khanabook.lite.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khanabook.lite.pos.core.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the real-world app-update path: a device running the Play Store build
 * (schema 73) updates to the current build (schema 78). Room runs every step of
 * the chain, so a missing or broken step here crashes the app on first launch
 * after update — exactly the class of failure that must never ship again.
 *
 * Chain under test (all live in AppDatabase, all registered in
 * DatabaseProvider/DatabaseModule):
 *   73 → 74  drop unused permission_requests table
 *   74 → 75  bills.status_version
 *   75 → 76  restaurant_profile.collect_customer_number
 *   76 → 77  menu_items.has_variants + backfill from surviving variants
 *   77 → 78  drop stock_logs (inventory feature removal)
 */
@RunWith(AndroidJUnit4::class)
class Migration73To78Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migration73To78_dropsInventoryLedgerAndPermissionRequests() {
        val db73 = helper.createDatabase(TEST_DB_NAME, 73)
        db73.close()

        val db78 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 78, true,
            AppDatabase.MIGRATION_73_74,
            AppDatabase.MIGRATION_74_75,
            AppDatabase.MIGRATION_75_76,
            AppDatabase.MIGRATION_76_77,
            AppDatabase.MIGRATION_77_78
        )

        assertFalse(
            "stock_logs must be dropped at 77→78",
            db78.tableExists("stock_logs")
        )
        assertFalse(
            "permission_requests must be dropped at 73→74",
            db78.tableExists("permission_requests")
        )
        assertTrue(db78.tableExists("bills"))
        assertTrue(db78.tableExists("menu_items"))

        db78.close()
    }

    @Test
    fun migration73To78_preservesBillsAndBackfillsNewDefaults() {
        val db73 = helper.createDatabase(TEST_DB_NAME, 73)
        // A bill created by the old build (only the columns v73 required).
        db73.execSQL(
            "INSERT INTO bills (id, restaurant_id, device_id, daily_order_id, daily_order_display, " +
                "subtotal, total_amount, payment_mode, payment_status, order_status, created_at, updated_at) " +
                "VALUES (1, 100, 'device-1', 1, 'INV-001', '100.0', '100.0', 'CASH', 'paid', 'completed', " +
                "1700000000, 1700000000)"
        )
        // A menu item WITH a surviving variant (should get has_variants=1) and one without (=0).
        db73.execSQL(
            "INSERT INTO menu_items (id, category_id, name, base_price, created_at) " +
                "VALUES (1, 1, 'Dosa', '50.0', 1700000000)"
        )
        db73.execSQL(
            "INSERT INTO menu_items (id, category_id, name, base_price, created_at) " +
                "VALUES (2, 1, 'Coffee', '20.0', 1700000000)"
        )
        db73.execSQL(
            "INSERT INTO item_variants (id, menu_item_id, variant_name, price) " +
                "VALUES (1, 1, 'Small', '40.0')"
        )
        db73.close()

        val db78 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 78, true,
            AppDatabase.MIGRATION_73_74,
            AppDatabase.MIGRATION_74_75,
            AppDatabase.MIGRATION_75_76,
            AppDatabase.MIGRATION_76_77,
            AppDatabase.MIGRATION_77_78
        )

        // Pre-update bill data survived, and 74→75 stamped the status_version default.
        db78.query(
            "SELECT total_amount, order_status, status_version FROM bills WHERE id = 1"
        ).use { cursor ->
            assertTrue("bill row must survive the full 73→78 chain", cursor.moveToFirst())
            assertEquals("100.0", cursor.getString(0))
            assertEquals("completed", cursor.getString(1))
            assertEquals(0, cursor.getLong(2))
        }

        // 76→77 backfill: promote only items whose variants actually survived.
        db78.query("SELECT id, has_variants FROM menu_items ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0)); assertEquals(1L, cursor.getLong(1))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0)); assertEquals(0L, cursor.getLong(1))
            assertFalse(cursor.moveToNext())
        }

        db78.close()
    }

    private fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean {
        query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$tableName'"
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0) > 0
        }
    }

    private companion object {
        const val TEST_DB_NAME = "migration-73-78-test-db"
    }
}
