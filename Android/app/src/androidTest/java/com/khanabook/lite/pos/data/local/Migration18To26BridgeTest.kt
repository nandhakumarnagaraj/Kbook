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
 * Guards the reconstructed legacy migration chain 18 → 26.
 *
 * The five bridge migrations (19→20, 20→21, 22→23, 24→25, 25→26) were lost in
 * the Sep 2026 restructure; a device sitting on any of those versions crashed
 * on launch with "A migration from X to Y was required but not found". The
 * chain also repairs three latent bugs in previously-shipped steps:
 *  - 18→19 could not clear NOT NULL on restaurant_profile.country/currency
 *  - 21→22 never added users.pin_hash (schema validation failure)
 *  - 23→24 never recreated the restaurant-scoped categories unique index
 *
 * MigrationTestHelper validates the post-migration schema against the schema
 * JSON assets, so every step here must land on the exact expected shape.
 */
@RunWith(AndroidJUnit4::class)
class Migration18To26BridgeTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun fullChain18To26_validatesAndPreservesData() {
        val db18 = helper.createDatabase(TEST_DB_NAME, 18)
        // v18 restaurant_profile: country/currency are NOT NULL in the actual
        // v18 schema (that is exactly the defect 18→19 must repair). The helper
        // creates the schema, so inserts use only the columns that exist there.
        db18.execSQL(
            "INSERT INTO restaurant_profile (id, shop_name) VALUES (1, 'Test Diner')"
        )
        db18.execSQL(
            "INSERT INTO users (id, name, email, is_active, created_at, restaurant_id) " +
                "VALUES (1, 'Owner', 'owner@test.com', 1, '2024-01-01 00:00:00', 7)"
        )
        db18.execSQL(
            "INSERT INTO categories (id, name, is_veg, created_at, restaurant_id) " +
                "VALUES (1, 'Starters', 1, '2024-01-01 00:00:00', 7)"
        )
        db18.execSQL(
            "INSERT INTO menu_items (id, category_id, name, base_price, created_at, restaurant_id) " +
                "VALUES (1, 1, 'Samosa', 40.0, '2024-01-01 00:00:00', 7)"
        )
        db18.close()

        val db26 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 26, true,
            *AppDatabase.ALL_MIGRATIONS
        )

        // Owner row survived every rebuild; password_hash is gone at v25.
        db26.query(
            "SELECT name, email, role FROM users WHERE id = 1"
        ).use { cursor ->
            assertTrue("user row must survive the 18→26 rebuilds", cursor.moveToFirst())
            assertEquals("Owner", cursor.getString(0))
            assertEquals("owner@test.com", cursor.getString(1))
            assertEquals("owner", cursor.getString(2))
        }
        assertFalse(
            "password_hash must be dropped at 24→25",
            columnExists(db26, "users", "password_hash")
        )
        assertTrue(
            "pin_hash must exist at v25",
            columnExists(db26, "users", "pin_hash")
        )

        // Menu item survived with affinity-corrected base_price (TEXT at v21).
        db26.query(
            "SELECT name, base_price, barcode FROM menu_items WHERE id = 1"
        ).use { cursor ->
            assertTrue("menu item must survive the 20→21 rebuild", cursor.moveToFirst())
            assertEquals("Samosa", cursor.getString(0))
            assertEquals("40.0", cursor.getString(1))
        }

        // Category survived; the unique index is restaurant-scoped at v24.
        db26.query("SELECT name FROM categories WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Starters", cursor.getString(0))
        }

        // Profile survived; v26 default applied for the new column.
        db26.query(
            "SELECT shop_name, mask_customer_phone FROM restaurant_profile WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Test Diner", cursor.getString(0))
            assertEquals(1, cursor.getLong(1))
        }

        db26.close()
    }

    @Test
    fun chain22To26_recoversDevicesStuckMidFlight() {
        // Devices that ran the historically broken 21→22 (role committed without
        // pin_hash) were stuck at v22. Recreate that exact shape by stopping the
        // helper build at 22, then walk 22→26.
        val db22 = helper.createDatabase(TEST_DB_NAME, 22)
        db22.execSQL(
            "INSERT INTO users (id, name, email, role, is_active, created_at, restaurant_id) " +
                "VALUES (1, 'Owner', 'owner@test.com', 'owner', 1, 1700000000, 7)"
        )
        db22.close()

        val db26 = helper.runMigrationsAndValidate(
            TEST_DB_NAME, 26, true,
            AppDatabase.MIGRATION_22_23,
            AppDatabase.MIGRATION_23_24,
            AppDatabase.MIGRATION_24_25,
            AppDatabase.MIGRATION_25_26
        )

        db26.query("SELECT role FROM users WHERE id = 1").use { cursor ->
            assertTrue("stuck v22 device must migrate cleanly", cursor.moveToFirst())
            assertEquals("owner", cursor.getString(0))
        }
        assertTrue(columnExists(db26, "users", "pin_hash"))

        db26.close()
    }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }

    private companion object {
        const val TEST_DB_NAME = "migration-18-26-test-db"
    }
}
