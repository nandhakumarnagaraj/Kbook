package com.khanabook.lite.pos.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV62NoOpMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun reopenAt62_identityOpen_preservesSeededUnsyncedRows() {
        val db = helper.createDatabase(TEST_DB, 62)
        seedUnsyncedRows(db)
        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 62, true).use { reopened ->
            assertSeededRowsSurvive(reopened)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate62To63_preservesSeededUnsyncedRows() {
        val db = helper.createDatabase(TEST_DB, 62)
        seedUnsyncedRows(db)
        db.close()

        helper.runMigrationsAndValidate(TEST_DB, 63, true, AppDatabase.MIGRATION_62_63).use { migrated ->
            assertSeededRowsSurvive(migrated)
        }
    }

    private fun seedUnsyncedRows(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO bills (
                id, restaurant_id, device_id, terminal_id, created_terminal_id, created_device_id,
                daily_order_id, daily_order_display, lifetime_order_id, subtotal, total_amount,
                payment_mode, source_channel, payment_status, order_status, created_at, paid_at,
                is_synced, updated_at, is_deleted, public_token, sync_status, terminal_series,
                financial_year, invoice_series, invoice_sequence, invoice_number,
                current_owner_terminal_id, version, lock_status, operation_id, record_origin,
                record_scope, payment_attempt_status
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                1L, 100L, "device-A", "A", "A", "device-A",
                1L, "1", null, "245.0", "245.0",
                "upi", "", "pending", "draft", 1000L, null,
                0, 1000L, 0, "tok-v62-unsynced-1", "pending", "A",
                "2026-2027", "A", null, null, "A",
                0L, "unlocked", "bill-op-1", "local_created", "terminal_operational", "none"
            )
        )
        db.execSQL(
            """
            INSERT INTO bills (
                id, restaurant_id, device_id, terminal_id, created_terminal_id, created_device_id,
                daily_order_id, daily_order_display, lifetime_order_id, subtotal, total_amount,
                payment_mode, source_channel, payment_status, order_status, created_at, paid_at,
                is_synced, updated_at, is_deleted, public_token, sync_status, terminal_series,
                financial_year, invoice_series, invoice_sequence, invoice_number,
                current_owner_terminal_id, version, lock_status, operation_id, record_origin,
                record_scope, payment_attempt_status
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                2L, 100L, "device-A", "A", "A", "device-A",
                2L, "2", 2L, "500.0", "550.0",
                "part_cash_upi", "", "paid", "completed", 2000L, 2500L,
                0, 2000L, 0, "tok-v62-unsynced-2", "pending", "A",
                "2026-2027", "A", 2L, "A-2026-000002", "A",
                3L, "unlocked", "bill-op-2", "local_created", "terminal_operational", "none"
            )
        )
        db.execSQL(
            """
            INSERT INTO bill_items (
                id, bill_id, item_name, price, quantity, item_total, sent_to_kot,
                restaurant_id, device_id, is_synced, updated_at
            ) VALUES (1, 1, 'Idli', '50.0', 2, '100.0', 0, 100, 'device-A', 0, 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_items (
                id, bill_id, item_name, price, quantity, item_total, sent_to_kot,
                restaurant_id, device_id, is_synced, updated_at
            ) VALUES (2, 1, 'Dosa', '80.0', 1, '80.0', 0, 100, 'device-A', 0, 1000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_items (
                id, bill_id, item_name, price, quantity, item_total, sent_to_kot,
                restaurant_id, device_id, is_synced, updated_at
            ) VALUES (3, 2, 'Paneer Biryani', '250.0', 2, '500.0', 1, 100, 'device-A', 0, 2000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_payments (
                id, bill_id, payment_mode, amount, created_at, restaurant_id, device_id,
                terminal_id, bill_public_token, operation_id, sync_status, is_synced,
                updated_at, is_deleted, verified_by, version
            ) VALUES (1, 1, 'upi', '245.0', 1000, 100, 'device-A', 'A', 'tok-v62-unsynced-1',
                'payment-op-1', 'pending', 0, 1000, 0, 'manual', 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_payments (
                id, bill_id, payment_mode, amount, created_at, restaurant_id, device_id,
                terminal_id, bill_public_token, operation_id, sync_status, is_synced,
                updated_at, is_deleted, verified_by, version
            ) VALUES (2, 2, 'cash', '300.0', 2000, 100, 'device-A', 'A', 'tok-v62-unsynced-2',
                'payment-op-2', 'pending', 0, 2000, 0, 'manual', 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO bill_payments (
                id, bill_id, payment_mode, amount, created_at, restaurant_id, device_id,
                terminal_id, bill_public_token, operation_id, sync_status, is_synced,
                updated_at, is_deleted, gateway_txn_id, gateway_status, verified_by, version
            ) VALUES (3, 2, 'upi', '250.0', 2100, 100, 'device-A', 'A', 'tok-v62-unsynced-2',
                'payment-op-3', 'pending', 0, 2100, 0, 'gtxn-123', 'success', 'gateway', 1)
            """.trimIndent()
        )
    }

    private fun assertSeededRowsSurvive(db: SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM bills").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM bill_items").use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM bill_payments").use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }

        db.query(
            """
            SELECT restaurant_id, device_id, terminal_id, created_terminal_id, daily_order_id,
                daily_order_display, lifetime_order_id, subtotal, total_amount, payment_mode,
                payment_status, order_status, created_at, paid_at, is_synced, updated_at,
                is_deleted, public_token, sync_status, terminal_series, financial_year,
                invoice_series, invoice_sequence, invoice_number, current_owner_terminal_id,
                version, lock_status, operation_id, record_origin, record_scope,
                payment_attempt_status
            FROM bills WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(100L, cursor.getLong(0))
            assertEquals("device-A", cursor.getString(1))
            assertEquals("A", cursor.getString(2))
            assertEquals("A", cursor.getString(3))
            assertEquals(1L, cursor.getLong(4))
            assertEquals("1", cursor.getString(5))
            assertEquals(null, cursor.getString(6))
            assertEquals("245.0", cursor.getString(7))
            assertEquals("245.0", cursor.getString(8))
            assertEquals("upi", cursor.getString(9))
            assertEquals("pending", cursor.getString(10))
            assertEquals("draft", cursor.getString(11))
            assertEquals(1000L, cursor.getLong(12))
            assertEquals(null, cursor.getString(13))
            assertEquals(0, cursor.getInt(14))
            assertEquals(1000L, cursor.getLong(15))
            assertEquals(0, cursor.getInt(16))
            assertEquals("tok-v62-unsynced-1", cursor.getString(17))
            assertEquals("pending", cursor.getString(18))
            assertEquals("A", cursor.getString(19))
            assertEquals("2026-2027", cursor.getString(20))
            assertEquals("A", cursor.getString(21))
            assertEquals(null, cursor.getString(22))
            assertEquals(null, cursor.getString(23))
            assertEquals("A", cursor.getString(24))
            assertEquals(0L, cursor.getLong(25))
            assertEquals("unlocked", cursor.getString(26))
            assertEquals("bill-op-1", cursor.getString(27))
            assertEquals("local_created", cursor.getString(28))
            assertEquals("terminal_operational", cursor.getString(29))
            assertEquals("none", cursor.getString(30))
        }

        db.query(
            """
            SELECT restaurant_id, daily_order_id, subtotal, total_amount, payment_mode,
                payment_status, order_status, created_at, paid_at, is_synced,
                public_token, sync_status, invoice_number, version, operation_id
            FROM bills WHERE id = 2
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(100L, cursor.getLong(0))
            assertEquals(2L, cursor.getLong(1))
            assertEquals("500.0", cursor.getString(2))
            assertEquals("550.0", cursor.getString(3))
            assertEquals("part_cash_upi", cursor.getString(4))
            assertEquals("paid", cursor.getString(5))
            assertEquals("completed", cursor.getString(6))
            assertEquals(2000L, cursor.getLong(7))
            assertEquals(2500L, cursor.getLong(8))
            assertEquals(0, cursor.getInt(9))
            assertEquals("tok-v62-unsynced-2", cursor.getString(10))
            assertEquals("pending", cursor.getString(11))
            assertEquals("A-2026-000002", cursor.getString(12))
            assertEquals(3L, cursor.getLong(13))
            assertEquals("bill-op-2", cursor.getString(14))
        }

        db.query(
            """
            SELECT id, bill_id, item_name, price, quantity, item_total, sent_to_kot,
                restaurant_id, device_id, is_synced, updated_at
            FROM bill_items ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals("Idli", cursor.getString(2))
            assertEquals("50.0", cursor.getString(3))
            assertEquals(2, cursor.getInt(4))
            assertEquals("100.0", cursor.getString(5))
            assertEquals(0, cursor.getInt(6))
            assertEquals(100L, cursor.getLong(7))
            assertEquals("device-A", cursor.getString(8))
            assertEquals(0, cursor.getInt(9))
            assertEquals(1000L, cursor.getLong(10))

            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals("Dosa", cursor.getString(2))
            assertEquals("80.0", cursor.getString(3))
            assertEquals(1, cursor.getInt(4))
            assertEquals("80.0", cursor.getString(5))
            assertEquals(100L, cursor.getLong(7))

            cursor.moveToNext()
            assertEquals(3L, cursor.getLong(0))
            assertEquals(2L, cursor.getLong(1))
            assertEquals("Paneer Biryani", cursor.getString(2))
            assertEquals("250.0", cursor.getString(3))
            assertEquals(2, cursor.getInt(4))
            assertEquals("500.0", cursor.getString(5))
            assertEquals(1, cursor.getInt(6))
            assertEquals(100L, cursor.getLong(7))
            assertEquals("device-A", cursor.getString(8))
            assertEquals(0, cursor.getInt(9))
            assertEquals(2000L, cursor.getLong(10))
        }

        db.query(
            """
            SELECT id, bill_id, payment_mode, amount, created_at, restaurant_id, device_id,
                terminal_id, bill_public_token, operation_id, sync_status, is_synced,
                updated_at, is_deleted, gateway_txn_id, gateway_status, verified_by, version
            FROM bill_payments ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals(1L, cursor.getLong(1))
            assertEquals("upi", cursor.getString(2))
            assertEquals("245.0", cursor.getString(3))
            assertEquals(1000L, cursor.getLong(4))
            assertEquals(100L, cursor.getLong(5))
            assertEquals("device-A", cursor.getString(6))
            assertEquals("A", cursor.getString(7))
            assertEquals("tok-v62-unsynced-1", cursor.getString(8))
            assertEquals("payment-op-1", cursor.getString(9))
            assertEquals("pending", cursor.getString(10))
            assertEquals(0, cursor.getInt(11))
            assertEquals(1000L, cursor.getLong(12))
            assertEquals(0, cursor.getInt(13))
            assertEquals(null, cursor.getString(14))
            assertEquals(null, cursor.getString(15))
            assertEquals("manual", cursor.getString(16))
            assertEquals(0L, cursor.getLong(17))

            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(0))
            assertEquals(2L, cursor.getLong(1))
            assertEquals("cash", cursor.getString(2))
            assertEquals("300.0", cursor.getString(3))
            assertEquals("payment-op-2", cursor.getString(9))
            assertEquals("pending", cursor.getString(10))
            assertEquals(0, cursor.getInt(11))

            cursor.moveToNext()
            assertEquals(3L, cursor.getLong(0))
            assertEquals(2L, cursor.getLong(1))
            assertEquals("upi", cursor.getString(2))
            assertEquals("250.0", cursor.getString(3))
            assertEquals(2100L, cursor.getLong(4))
            assertEquals("tok-v62-unsynced-2", cursor.getString(8))
            assertEquals("payment-op-3", cursor.getString(9))
            assertEquals("pending", cursor.getString(10))
            assertEquals(0, cursor.getInt(11))
            assertEquals("gtxn-123", cursor.getString(14))
            assertEquals("success", cursor.getString(15))
            assertEquals("gateway", cursor.getString(16))
            assertEquals(1L, cursor.getLong(17))
        }
    }

    private companion object {
        const val TEST_DB = "v62-migration-test"
    }
}
