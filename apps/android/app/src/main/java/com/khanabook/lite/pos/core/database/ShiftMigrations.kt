package com.khanabook.lite.pos.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for the business-day / shift work (schema 83 onward).
 *
 * Kept in their own file so `AppDatabase.kt` — already 2 300+ lines — does not keep
 * growing. The chain is still declared exactly once, in `AppDatabase.ALL_MIGRATIONS`;
 * both Room builders read only that array, and `DatabaseMigrationListConsistencyTest`
 * asserts there is no gap.
 */
object ShiftMigrations {

    /**
     * Adds the business-day configuration to `restaurant_profile` and the local
     * cashier-shift table.
     *
     * Every column has a default that preserves the previous behaviour:
     * `business_day_cutoff_time = '00:00'` (calendar day) and
     * `cutoff_effective_from = NULL` (never active), so an upgrade alone changes nothing.
     *
     * The DDL here must match `CashierShiftEntity` **column for column and index for
     * index** — Room validates the post-migration schema and reports any drift as
     * "Migration didn't properly handle". In particular there is deliberately no partial
     * `WHERE status = 'ACTIVE'` index: Room cannot express one, and an index that exists
     * in the database but not on the entity fails that validation. "At most one ACTIVE
     * shift per terminal" is therefore enforced by `CashierShiftDao.openShift`.
     *
     * ALTERs are guarded: an out-of-band column aborted the v39→40 chain once
     * (see MIGRATION_39_40), and a duplicate-column exception kills the whole upgrade.
     */
    val MIGRATION_83_84 = object : Migration(83, 84) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!db.hasColumn("restaurant_profile", "opening_time")) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `opening_time` TEXT NOT NULL DEFAULT '08:00'")
            }
            if (!db.hasColumn("restaurant_profile", "closing_time")) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `closing_time` TEXT NOT NULL DEFAULT '23:00'")
            }
            if (!db.hasColumn("restaurant_profile", "business_day_cutoff_time")) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `business_day_cutoff_time` TEXT NOT NULL DEFAULT '00:00'")
            }
            if (!db.hasColumn("restaurant_profile", "cutoff_effective_from")) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `cutoff_effective_from` INTEGER")
            }
            if (!db.hasColumn("restaurant_profile", "email_daily_closing_report")) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `email_daily_closing_report` INTEGER NOT NULL DEFAULT 0")
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_cashier_shifts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                    `device_id` TEXT NOT NULL DEFAULT '',
                    `terminal_id` TEXT NOT NULL,
                    `terminal_series` TEXT,
                    `cashier_user_id` INTEGER,
                    `cashier_name` TEXT,
                    `business_date` TEXT NOT NULL,
                    `opened_at` INTEGER NOT NULL,
                    `closed_at` INTEGER,
                    `opening_float` TEXT NOT NULL DEFAULT '0.00',
                    `declared_cash` TEXT,
                    `expected_cash` TEXT,
                    `cash_variance` TEXT,
                    `total_revenue` TEXT,
                    `total_orders` INTEGER,
                    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                    `close_source` TEXT,
                    `snapshot_json` TEXT,
                    `public_token` TEXT NOT NULL,
                    `server_id` INTEGER,
                    `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                    `is_synced` INTEGER NOT NULL DEFAULT 0,
                    `updated_at` INTEGER NOT NULL DEFAULT 0,
                    `is_deleted` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            // Index names follow Room's generated convention exactly.
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_local_cashier_shifts_public_token` " +
                    "ON `local_cashier_shifts` (`public_token`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_local_cashier_shifts_restaurant_id_terminal_id_status` " +
                    "ON `local_cashier_shifts` (`restaurant_id`, `terminal_id`, `status`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_local_cashier_shifts_restaurant_id_business_date` " +
                    "ON `local_cashier_shifts` (`restaurant_id`, `business_date`)"
            )

            android.util.Log.i(
                "ShiftMigrations",
                "MIGRATION_83_84 complete: business-day config + local_cashier_shifts"
            )
        }
    }

    private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean {
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }
}
