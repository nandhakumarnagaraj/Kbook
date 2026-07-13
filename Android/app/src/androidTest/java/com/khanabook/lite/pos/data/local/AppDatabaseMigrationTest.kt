package com.khanabook.lite.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate52To58_validatesFinalSchema() {
        helper.createDatabase(TEST_DB, 52).close()
        helper.runMigrationsAndValidate(TEST_DB, 58, true, *MIGRATIONS_TO_58).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate53To58_validatesFinalSchema() {
        helper.createDatabase(TEST_DB, 53).close()
        helper.runMigrationsAndValidate(TEST_DB, 58, true, *MIGRATIONS_TO_58).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate54To58_validatesFinalSchema() {
        helper.createDatabase(TEST_DB, 54).close()
        helper.runMigrationsAndValidate(TEST_DB, 58, true, *MIGRATIONS_TO_58).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate57To58_preservesTransactionalRowsAndBackfillsOwnership() {
        val db = helper.createDatabase(TEST_DB, 57)
        insertVersion57TransactionalRows(db)
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            58,
            true,
            AppDatabase.MIGRATION_57_58,
        )

        migrated.query("SELECT COUNT(*) FROM bills").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query(
            """
            SELECT terminal_id, created_terminal_id, created_device_id, current_owner_terminal_id,
                public_token, version, lock_status
            FROM bills
            WHERE public_token = 'token-completed'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("A", cursor.getString(0))
            assertEquals("A", cursor.getString(1))
            assertEquals("device-oppo", cursor.getString(2))
            assertEquals(null, cursor.getString(3))
            assertEquals("token-completed", cursor.getString(4))
            assertEquals(0L, cursor.getLong(5))
            assertEquals("unlocked", cursor.getString(6))
        }
        migrated.query(
            """
            SELECT terminal_id, current_owner_terminal_id
            FROM bills
            WHERE public_token = 'token-draft'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("A", cursor.getString(0))
            assertEquals("A", cursor.getString(1))
        }
        migrated.query(
            """
            SELECT COUNT(*)
            FROM bill_items
            WHERE bill_id = 1 AND item_name = 'Idli'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            """
            SELECT terminal_id, bill_public_token, sync_status
            FROM bill_payments
            WHERE bill_id = 1
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("A", cursor.getString(0))
            assertEquals("token-completed", cursor.getString(1))
            assertEquals("pending", cursor.getString(2))
        }
        migrated.query(
            """
            SELECT origin_device_id, event_token, bill_public_token, event_version
            FROM kot_events
            WHERE public_token = 'token-completed'
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("device-oppo", cursor.getString(0))
            assertEquals("token-completed:1", cursor.getString(1))
            assertEquals("token-completed", cursor.getString(2))
            assertEquals(0L, cursor.getLong(3))
        }
        migrated.query(
            """
            SELECT terminal_id, device_id, bill_public_token, print_event_token
            FROM kitchen_print_queue
            WHERE bill_id = 1
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("A", cursor.getString(0))
            assertEquals("device-oppo", cursor.getString(1))
            assertEquals("token-completed", cursor.getString(2))
            assertNotNull(cursor.getString(3))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun fresh58_validatesFinalSchema() {
        helper.createDatabase(TEST_DB, 58).close()
        helper.runMigrationsAndValidate(TEST_DB, 58, true).close()
    }

    private fun insertVersion57TransactionalRows(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO bills (
                id, restaurant_id, device_id, daily_order_id, daily_order_display, lifetime_order_id,
                subtotal, total_amount, payment_mode, payment_status, order_status, created_at,
                updated_at, public_token, terminal_series, financial_year, invoice_series,
                invoice_sequence, invoice_number, sync_status
            ) VALUES
                (1, 100, 'device-oppo', 1, '1', 1, '100.0', '100.0', 'cash', 'paid',
                 'completed', 1000, 1000, 'token-completed', 'A', '2026-2027', 'A', 1,
                 'A-2026-000001', 'pending'),
                (2, 100, 'device-oppo', 2, '2', 2, '50.0', '50.0', 'cash', 'pending',
                 'draft', 1001, 1001, 'token-draft', 'A', NULL, NULL, NULL, NULL, 'pending')
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_items (
                id, bill_id, item_name, price, quantity, item_total, restaurant_id, device_id,
                is_synced, updated_at
            ) VALUES (1, 1, 'Idli', '50.0', 2, '100.0', 100, 'device-oppo', 0, 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_payments (
                id, bill_id, payment_mode, amount, restaurant_id, device_id, is_synced, updated_at
            ) VALUES (1, 1, 'cash', '100.0', 100, 'device-oppo', 0, 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO kot_events (
                public_token, kot_revision, event_type, item_snapshot_json, originating_device_id,
                is_printed, created_at
            ) VALUES ('token-completed', '1', 'CREATED', '[]', 'device-oppo', 0, 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO kitchen_print_queue (
                id, bill_id, restaurant_id, printer_mac, attempts, dispatch_status,
                public_token, kot_revision, created_at, updated_at
            ) VALUES (1, 1, 100, 'AA:BB', 0, 'PENDING', 'token-completed', '1', 1000, 1000)
            """.trimIndent()
        )
    }

    private companion object {
        const val TEST_DB = "migration-test"
        val MIGRATIONS_TO_58 = arrayOf(
            AppDatabase.MIGRATION_52_53,
            AppDatabase.MIGRATION_53_54,
            AppDatabase.MIGRATION_54_55,
            AppDatabase.MIGRATION_55_56,
            AppDatabase.MIGRATION_56_57,
            AppDatabase.MIGRATION_57_58,
        )
    }
}
