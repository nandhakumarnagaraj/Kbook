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

    @Test
    @Throws(IOException::class)
    fun migrate59To60_validatesFinalSchema() {
        helper.createDatabase(TEST_DB, 59).close()
        helper.runMigrationsAndValidate(TEST_DB, 60, true, AppDatabase.MIGRATION_59_60).close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate59To60_classifiesRecordsCorrectly() {
        val db = helper.createDatabase(TEST_DB, 59)

        // 1. Local unsynced draft (own terminal) -> local_created / terminal_operational
        insertV59Bill(
            db, 1, isSynced = 0, orderStatus = "draft", paymentStatus = "pending",
            createdTerminalId = "A", publicToken = "tok-local-draft", invoiceNumber = null
        )
        // 2. Local completed & synced (own terminal) -> server_imported / restaurant_history
        //    (conservative; runtime reconcile re-labels this terminal's own bills later)
        insertV59Bill(
            db, 2, isSynced = 1, orderStatus = "completed", paymentStatus = "paid",
            createdTerminalId = "A", publicToken = "tok-local-completed", invoiceNumber = "A-2026-000002"
        )
        // 3. Other terminal completed & synced -> server_imported / restaurant_history
        insertV59Bill(
            db, 3, isSynced = 1, orderStatus = "completed", paymentStatus = "paid",
            createdTerminalId = "B", publicToken = "tok-other-completed", invoiceNumber = "B-2026-000003"
        )
        // 4. Other terminal active (draft) & synced -> server_imported / restaurant_history.
        //    Step 2 promotes it to local_created, but step 4 (synced + local_created) demotes it
        //    back to read-only history. Runtime reconcile never re-labels other terminals' bills.
        insertV59Bill(
            db, 4, isSynced = 1, orderStatus = "draft", paymentStatus = "pending",
            createdTerminalId = "B", publicToken = "tok-other-draft", invoiceNumber = null
        )
        // 5. Marketplace order -> marketplace_imported / restaurant_shared
        insertV59Bill(
            db, 5, isSynced = 1, orderStatus = "completed", paymentStatus = "paid",
            createdTerminalId = "A", publicToken = "tok-zomato", invoiceNumber = "A-2026-000005",
            sourceChannel = "zomato"
        )
        // 6. Synced but missing created_terminal_id -> server_imported / restaurant_history
        insertV59Bill(
            db, 6, isSynced = 1, orderStatus = "completed", paymentStatus = "paid",
            createdTerminalId = null, publicToken = "tok-no-term", invoiceNumber = "A-2026-000006"
        )
        // 8. Cancelled synced -> server_imported / restaurant_history
        insertV59Bill(
            db, 8, isSynced = 1, orderStatus = "cancelled", paymentStatus = "paid",
            createdTerminalId = "A", publicToken = "tok-cancelled", invoiceNumber = "A-2026-000008"
        )
        // 9. Refunded synced -> server_imported / restaurant_history
        insertV59Bill(
            db, 9, isSynced = 1, orderStatus = "completed", paymentStatus = "refunded",
            createdTerminalId = "A", publicToken = "tok-refunded", invoiceNumber = "A-2026-000009",
            refundAmount = "50.0"
        )
        // 10. Duplicate invoice numbers across terminal series -> both retained, both restaurant history
        insertV59Bill(
            db, 10, isSynced = 1, orderStatus = "completed", paymentStatus = "paid",
            createdTerminalId = "A", publicToken = "tok-dup-A", invoiceNumber = "A-2026-000010"
        )
        insertV59Bill(
            db, 11, isSynced = 1, orderStatus = "completed", paymentStatus = "paid",
            createdTerminalId = "B", publicToken = "tok-dup-B", invoiceNumber = "B-2026-000010"
        )
        // 11. Pending KOT (local unsynced draft) -> local_created / terminal_operational
        insertV59Bill(
            db, 12, isSynced = 0, orderStatus = "draft", paymentStatus = "pending",
            createdTerminalId = "A", publicToken = "tok-pending-kot", invoiceNumber = null
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 60, true, AppDatabase.MIGRATION_59_60)

        assertEquals("local_created", originFor(migrated, 1).first)
        assertEquals("terminal_operational", originFor(migrated, 1).second)

        assertEquals("server_imported", originFor(migrated, 2).first)
        assertEquals("restaurant_history", originFor(migrated, 2).second)

        assertEquals("server_imported", originFor(migrated, 3).first)
        assertEquals("restaurant_history", originFor(migrated, 3).second)

        assertEquals("server_imported", originFor(migrated, 4).first)
        assertEquals("restaurant_history", originFor(migrated, 4).second)

        assertEquals("marketplace_imported", originFor(migrated, 5).first)
        assertEquals("restaurant_shared", originFor(migrated, 5).second)

        assertEquals("server_imported", originFor(migrated, 6).first)
        assertEquals("restaurant_history", originFor(migrated, 6).second)

        assertEquals("server_imported", originFor(migrated, 8).first)
        assertEquals("restaurant_history", originFor(migrated, 8).second)

        assertEquals("server_imported", originFor(migrated, 9).first)
        assertEquals("restaurant_history", originFor(migrated, 9).second)

        assertEquals("server_imported", originFor(migrated, 10).first)
        assertEquals("server_imported", originFor(migrated, 11).first)
        assertEquals("A-2026-000010", invoiceFor(migrated, 10))
        assertEquals("B-2026-000010", invoiceFor(migrated, 11))

        assertEquals("local_created", originFor(migrated, 12).first)
        assertEquals("terminal_operational", originFor(migrated, 12).second)
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate59To60_multiRestaurantPreservesIsolation() {
        val db = helper.createDatabase(TEST_DB, 59)
        insertV59Bill(
            db, 100, restaurantId = 100, isSynced = 0, orderStatus = "draft",
            paymentStatus = "pending", createdTerminalId = "A",
            publicToken = "tok-r100-local", invoiceNumber = null
        )
        insertV59Bill(
            db, 200, restaurantId = 200, isSynced = 0, orderStatus = "draft",
            paymentStatus = "pending", createdTerminalId = "A",
            publicToken = "tok-r200-local", invoiceNumber = null
        )
        insertV59Bill(
            db, 201, restaurantId = 200, isSynced = 1, orderStatus = "completed",
            paymentStatus = "paid", createdTerminalId = "C",
            publicToken = "tok-r200-other", invoiceNumber = "C-2026-000001"
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 60, true, AppDatabase.MIGRATION_59_60)

        assertEquals("local_created", originFor(migrated, 100).first)
        assertEquals("local_created", originFor(migrated, 200).first)
        assertEquals("server_imported", originFor(migrated, 201).first)
        migrated.close()
    }

    private fun insertV59Bill(
        db: SupportSQLiteDatabase,
        id: Long,
        restaurantId: Long = 100,
        deviceId: String = "device-A",
        isSynced: Int = 1,
        orderStatus: String = "completed",
        paymentStatus: String = "paid",
        createdTerminalId: String? = "A",
        currentOwnerTerminalId: String? = createdTerminalId,
        terminalSeries: String? = createdTerminalId,
        publicToken: String? = null,
        invoiceNumber: String? = null,
        sourceChannel: String = "",
        refundAmount: String? = null,
        cancelReason: String = "",
        financialYear: String? = "2026-2027"
    ) {
        db.execSQL(
            """
            INSERT INTO bills (
                id, restaurant_id, device_id, daily_order_id, daily_order_display,
                subtotal, total_amount, payment_mode, payment_status, order_status, created_at,
                updated_at, is_synced, is_deleted, public_token, created_terminal_id,
                current_owner_terminal_id, terminal_series, financial_year, invoice_series,
                invoice_sequence, invoice_number, source_channel, refund_amount, cancel_reason
            ) VALUES (?,?,?,?,?, '100.0','100.0','cash',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                id, restaurantId, deviceId, id, id.toString(),
                paymentStatus, orderStatus, 0L, 0L, isSynced, 0,
                publicToken, createdTerminalId, currentOwnerTerminalId, terminalSeries,
                financialYear, "A", id, invoiceNumber, sourceChannel, refundAmount, cancelReason
            )
        )
    }

    private fun originFor(db: SupportSQLiteDatabase, id: Long): Pair<String, String> {
        db.query(
            "SELECT record_origin, record_scope FROM bills WHERE id = $id"
        ).use { cursor ->
            check(cursor.moveToFirst()) { "No bill with id=$id" }
            return cursor.getString(0) to cursor.getString(1)
        }
    }

    private fun invoiceFor(db: SupportSQLiteDatabase, id: Long): String? {
        db.query("SELECT invoice_number FROM bills WHERE id = $id").use { cursor ->
            check(cursor.moveToFirst()) { "No bill with id=$id" }
            return cursor.getString(0)
        }
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
