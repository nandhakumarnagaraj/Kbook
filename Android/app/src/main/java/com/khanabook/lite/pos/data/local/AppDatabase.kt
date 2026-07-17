package com.khanabook.lite.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*

@Database(
        entities =
                [
                        UserEntity::class,
                        RestaurantProfileEntity::class,
                        CategoryEntity::class,
                        MenuItemEntity::class,
                        ItemVariantEntity::class,
                        PrinterProfileEntity::class,
                        KitchenPrintQueueEntity::class,
                        BillEntity::class,
                        BillItemEntity::class,
                        BillPaymentEntity::class,
                        SyncQuarantineEntity::class,
                        StockLogEntity::class,
                        KotEventEntity::class,
                        TerminalDailyCounterEntity::class
                ],
        version = 61,
        exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun categoryDao(): CategoryDao
    abstract fun menuDao(): MenuDao
    abstract fun printerProfileDao(): PrinterProfileDao
    abstract fun kitchenPrintQueueDao(): KitchenPrintQueueDao
    abstract fun billDao(): BillDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun kotEventDao(): KotEventDao

	    companion object {
	        const val DATABASE_NAME = "khanabook_lite_db"

            val MIGRATION_52_53 = object : Migration(52, 53) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    if (!db.hasColumn("restaurant_profile", "order_payment_flow_mode")) {
                        db.execSQL(
                            "ALTER TABLE `restaurant_profile` ADD COLUMN `order_payment_flow_mode` TEXT NOT NULL DEFAULT 'pay_before_food'"
                        )
                    }
                }
            }

            val MIGRATION_51_52 = object : Migration(51, 52) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    if (!db.hasColumn("bills", "source_channel")) {
                        db.execSQL("ALTER TABLE `bills` ADD COLUMN `source_channel` TEXT NOT NULL DEFAULT ''")
                        db.execSQL(
                            """
                            UPDATE `bills`
                            SET `source_channel` = `payment_mode`
                            WHERE `source_channel` = ''
                              AND `payment_mode` IN ('zomato', 'swiggy', 'own_website')
                            """.trimIndent()
                        )
                    }
                }
            }

            val MIGRATION_50_51 = object : Migration(50, 51) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    if (!db.hasColumn("sync_quarantine_records", "child_snapshot_json")) {
                        db.execSQL("ALTER TABLE `sync_quarantine_records` ADD COLUMN `child_snapshot_json` TEXT")
                    }
                }
            }

            val MIGRATION_49_50 = object : Migration(49, 50) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sync_quarantine_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                            `parent_bill_id` INTEGER NOT NULL,
                            `parent_bill_display` TEXT,
                            `child_entity_type` TEXT NOT NULL,
                            `child_local_id` INTEGER NOT NULL,
                            `child_display_name` TEXT,
                            `child_summary` TEXT,
                            `child_snapshot_json` TEXT,
                            `sync_failure_reason` TEXT,
                            `quarantined_at` INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_sync_quarantine_records_restaurant_id` ON `sync_quarantine_records` (`restaurant_id`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_sync_quarantine_records_restaurant_id_parent_bill_id` ON `sync_quarantine_records` (`restaurant_id`, `parent_bill_id`)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_quarantine_records_restaurant_id_child_entity_type_child_local_id` ON `sync_quarantine_records` (`restaurant_id`, `child_entity_type`, `child_local_id`)"
                    )
                }
            }

            val MIGRATION_48_49 = object : Migration(48, 49) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `bill_items` ADD COLUMN `sent_to_kot` INTEGER NOT NULL DEFAULT 0")
                }
            }

            val MIGRATION_47_48 = object : Migration(47, 48) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `sync_status` TEXT NOT NULL DEFAULT 'pending'")
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `sync_failure_reason` TEXT")
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `sync_failed_at` INTEGER")
                    db.execSQL("UPDATE `bills` SET `sync_status` = CASE WHEN `is_synced` = 1 THEN 'synced' ELSE 'pending' END")
                }
            }

            val MIGRATION_46_47 = object : Migration(46, 47) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `printer_profiles` ADD COLUMN `restaurant_id` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("DROP INDEX IF EXISTS `index_printer_profiles_role`")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_printer_profiles_restaurant_id_role` ON `printer_profiles` (`restaurant_id`, `role`)")
                }
            }

            val MIGRATION_45_46 = object : Migration(45, 46) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `restaurant_id` INTEGER NOT NULL DEFAULT 0")
                    // Backfill from the owning bill's restaurant so existing queued jobs stay scoped.
                    db.execSQL(
                        """
                        UPDATE `kitchen_print_queue`
                        SET `restaurant_id` = (
                            SELECT `restaurant_id` FROM `bills` WHERE `bills`.`id` = `kitchen_print_queue`.`bill_id`
                        )
                        WHERE `bill_id` IN (SELECT `id` FROM `bills`)
                        """.trimIndent()
                    )
                }
            }

            val MIGRATION_44_45 = object : Migration(44, 45) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("UPDATE restaurant_profile SET id = restaurant_id WHERE id = 1 AND restaurant_id > 0")
                }
            }

            val MIGRATION_43_44 = object : Migration(43, 44) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `owner_user_id` INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `owner_restaurant_id` INTEGER DEFAULT NULL")
                }
            }

            val MIGRATION_41_42 = object : Migration(41, 42) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `logo_url` TEXT")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `logo_version` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `upi_qr_url` TEXT")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `upi_qr_version` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `invoice_footer` TEXT")
                }
            }

            val MIGRATION_42_43 = object : Migration(42, 43) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `public_token` TEXT")
                }
            }

            val MIGRATION_40_41 = object : Migration(40, 41) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        UPDATE `users`
                        SET `role` = 'OWNER'
                        WHERE `role` IS NULL OR `role` NOT IN ('OWNER', 'KBOOK_ADMIN')
                        """.trimIndent()
                    )
                }
            }

            val MIGRATION_39_40 = object : Migration(39, 40) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Gateway tracking on bill_payments
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `gateway_txn_id` TEXT")
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `gateway_status` TEXT")
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `verified_by` TEXT NOT NULL DEFAULT 'manual'")
                }
            }

            val MIGRATION_38_39 = object : Migration(38, 39) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        ALTER TABLE `kitchen_print_queue`
                        ADD COLUMN `dispatch_status` TEXT NOT NULL DEFAULT 'pending'
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        ALTER TABLE `kitchen_print_queue`
                        ADD COLUMN `last_attempt_at` INTEGER
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        UPDATE `kitchen_print_queue`
                        SET `dispatch_status` = 'pending'
                        WHERE `dispatch_status` IS NULL OR `dispatch_status` = ''
                        """.trimIndent()
                    )
                }
            }

            val MIGRATION_37_38 = object : Migration(37, 38) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Backfill restaurant_id=0 on bills/items/payments using the stored profile.
                    // These were created before restaurantId was set in session (e.g. first login,
                    // reinstall) and were stuck unsynced because the server rejected restaurantId=0.
                    val tables = arrayOf("bills", "bill_items", "bill_payments")
                    for (table in tables) {
                        db.execSQL("""
                            UPDATE `$table`
                            SET restaurant_id = (
                                SELECT restaurant_id FROM restaurant_profile
                                WHERE restaurant_id > 0 LIMIT 1
                            )
                            WHERE (restaurant_id = 0 OR restaurant_id IS NULL)
                            AND (SELECT COUNT(*) FROM restaurant_profile WHERE restaurant_id > 0) > 0
                        """.trimIndent())
                    }
                }
            }

            val MIGRATION_35_36 = object : Migration(35, 36) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `kitchen_print_queue` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `bill_id` INTEGER NOT NULL,
                            `printer_mac` TEXT NOT NULL,
                            `attempts` INTEGER NOT NULL,
                            `last_error` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_kitchen_print_queue_bill_id_printer_mac` ON `kitchen_print_queue` (`bill_id`, `printer_mac`)"
                    )
                }
            }

            val MIGRATION_36_37 = object : Migration(36, 37) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `kitchen_printer_enabled` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `kitchen_printer_name` TEXT")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `kitchen_printer_mac` TEXT")
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `kitchen_printer_paper_size` TEXT NOT NULL DEFAULT '58mm'")
                }
            }

            val MIGRATION_33_34 = object : Migration(33, 34) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        // Column already exists — safe to skip
                        android.util.Log.w("AppDatabase", "MIGRATION_33_34: created_at may already exist: ${e.message}")
                    }
                }
            }

            val MIGRATION_34_35 = object : Migration(34, 35) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `printer_profiles` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `role` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `mac_address` TEXT NOT NULL,
                            `enabled` INTEGER NOT NULL,
                            `auto_print` INTEGER NOT NULL,
                            `paper_size` TEXT NOT NULL,
                            `include_logo` INTEGER NOT NULL,
                            `copies` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_printer_profiles_role` ON `printer_profiles` (`role`)")
                }
            }

            val MIGRATION_32_33 = object : Migration(32, 33) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `phone_number` TEXT DEFAULT NULL")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_32_33: phone_number may already exist: ${e.message}")
                    }
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `token_invalidated_at` INTEGER DEFAULT NULL")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_32_33: token_invalidated_at may already exist: ${e.message}")
                    }
                }
            }

            val MIGRATION_31_32 = object : Migration(31, 32) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE `bills` ADD COLUMN `cancel_reason` TEXT NOT NULL DEFAULT ''")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_31_32: cancel_reason may already exist: ${e.message}")
                    }
                }
            }

            val MIGRATION_30_31 = object : Migration(30, 31) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `login_id` TEXT")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_30_31: login_id column may already exist: ${e.message}")
                    }
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `google_email` TEXT")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_30_31: google_email column may already exist: ${e.message}")
                    }
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `auth_provider` TEXT NOT NULL DEFAULT 'PHONE'")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_30_31: auth_provider column may already exist: ${e.message}")
                    }
                    try {
                        db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `barcode` TEXT")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w("AppDatabase", "MIGRATION_30_31: barcode column may already exist: ${e.message}")
                    }
                    db.execSQL("UPDATE `users` SET `login_id` = COALESCE(NULLIF(`login_id`, ''), `email`) WHERE `login_id` IS NULL OR `login_id` = ''")
                    db.execSQL("UPDATE `users` SET `auth_provider` = COALESCE(NULLIF(`auth_provider`, ''), 'PHONE')")
                }
            }

	        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `server_category_id` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `item_variants` ADD COLUMN `server_menu_item_id` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `bill_items` ADD COLUMN `server_menu_item_id` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `bill_items` ADD COLUMN `server_variant_id` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `stock_logs` ADD COLUMN `server_menu_item_id` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `stock_logs` ADD COLUMN `server_variant_id` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tables = arrayOf(
                    "users", "restaurant_profile", "categories", "menu_items",
                    "item_variants", "bills", "bill_items", "bill_payments", "stock_logs"
                )
                for (table in tables) {
                    try {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `server_updated_at` INTEGER NOT NULL DEFAULT 0")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w(
                            "AppDatabase",
                            "MIGRATION_28_29: failed to add server_updated_at on $table: ${e.message}"
                        )
                    }
                }
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val tables = arrayOf(
                    "users", "restaurant_profile", "categories", "menu_items",
                    "item_variants", "bills", "bill_items", "bill_payments", "stock_logs"
                )
                for (table in tables) {
                    try {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `server_id` INTEGER DEFAULT NULL")
                    } catch (e: android.database.sqlite.SQLiteException) {
                        android.util.Log.w(
                            "AppDatabase",
                            "MIGRATION_27_28: failed to add server_id on $table: ${e.message}"
                        )
                    }
                }
            }
        }

        
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                
                db.execSQL("DROP TABLE IF EXISTS `raw_materials`")
                db.execSQL("DROP TABLE IF EXISTS `material_batches`")
                db.execSQL("DROP TABLE IF EXISTS `recipe_ingredients`")
                db.execSQL("DROP TABLE IF EXISTS `raw_material_stock_logs`")

                
                try {
                    db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `current_stock` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `low_stock_threshold` REAL NOT NULL DEFAULT 0.0")
                } catch (e: android.database.sqlite.SQLiteException) {
                    android.util.Log.w(
                        "AppDatabase",
                        "MIGRATION_17_18: menu_items stock columns may already exist: ${e.message}"
                    )
                }

                try {
                    db.execSQL("ALTER TABLE `item_variants` ADD COLUMN `current_stock` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `item_variants` ADD COLUMN `low_stock_threshold` REAL NOT NULL DEFAULT 0.0")
                } catch (e: android.database.sqlite.SQLiteException) {
                    android.util.Log.w(
                        "AppDatabase",
                        "MIGRATION_17_18: item_variants stock columns may already exist: ${e.message}"
                    )
                }
            }
        }
        
        
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                
                try {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `country` TEXT DEFAULT 'India'")
                } catch (e: android.database.sqlite.SQLiteException) {
                    android.util.Log.w("AppDatabase", "MIGRATION_18_19: country may already exist: ${e.message}")
                    db.execSQL("UPDATE `restaurant_profile` SET `country` = 'India' WHERE `country` IS NULL")
                }

                try {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `currency` TEXT DEFAULT 'INR'")
                } catch (e: android.database.sqlite.SQLiteException) {
                    android.util.Log.w("AppDatabase", "MIGRATION_18_19: currency may already exist: ${e.message}")
                    db.execSQL("UPDATE `restaurant_profile` SET `currency` = 'INR' WHERE `currency` IS NULL")
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `users` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'owner'")
                } catch (e: android.database.sqlite.SQLiteException) {
                    android.util.Log.w("AppDatabase", "MIGRATION_21_22: role may already exist: ${e.message}")
                }
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `show_branding` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `review_url` TEXT")
            }
        }

        val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the temporary bills_new table with nullable lifetime_order_id and the
                // new identity columns. This migration must preserve unsynced bills because the
                // app is offline-first and an upgrade can happen while the device is offline.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bills_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `daily_order_id` INTEGER NOT NULL,
                        `daily_order_display` TEXT NOT NULL,
                        `lifetime_order_id` INTEGER,
                        `order_type` TEXT NOT NULL DEFAULT 'order',
                        `customer_name` TEXT,
                        `customer_whatsapp` TEXT,
                        `subtotal` TEXT NOT NULL,
                        `gst_percentage` TEXT NOT NULL DEFAULT '0.0',
                        `cgst_amount` TEXT NOT NULL DEFAULT '0.0',
                        `sgst_amount` TEXT NOT NULL DEFAULT '0.0',
                        `custom_tax_amount` TEXT NOT NULL DEFAULT '0.0',
                        `total_amount` TEXT NOT NULL,
                        `payment_mode` TEXT NOT NULL,
                        `source_channel` TEXT NOT NULL DEFAULT '',
                        `part_amount_1` TEXT NOT NULL DEFAULT '0.0',
                        `part_amount_2` TEXT NOT NULL DEFAULT '0.0',
                        `payment_status` TEXT NOT NULL,
                        `order_status` TEXT NOT NULL,
                        `created_by` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `paid_at` INTEGER,
                        `last_reset_date` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `server_id` INTEGER,
                        `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                        `cancel_reason` TEXT NOT NULL DEFAULT '',
                        `public_token` TEXT,
                        `owner_user_id` INTEGER DEFAULT NULL,
                        `owner_restaurant_id` INTEGER DEFAULT NULL,
                        `sync_status` TEXT NOT NULL DEFAULT 'pending',
                        `sync_failure_reason` TEXT,
                        `sync_failed_at` INTEGER,
                        `terminal_series` TEXT DEFAULT NULL,
                        `financial_year` TEXT DEFAULT NULL,
                        `invoice_series` TEXT DEFAULT NULL,
                        `invoice_sequence` INTEGER DEFAULT NULL,
                        `invoice_number` TEXT DEFAULT NULL,
                        `refund_amount` TEXT DEFAULT NULL,
                        FOREIGN KEY(`created_by`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())

                // 3. Copy existing data from bills to bills_new
                db.execSQL("""
                    INSERT INTO `bills_new` (
                        `id`, `restaurant_id`, `device_id`, `daily_order_id`, `daily_order_display`,
                        `lifetime_order_id`, `order_type`, `customer_name`, `customer_whatsapp`, `subtotal`,
                        `gst_percentage`, `cgst_amount`, `sgst_amount`, `custom_tax_amount`, `total_amount`,
                        `payment_mode`, `source_channel`, `part_amount_1`, `part_amount_2`, `payment_status`,
                        `order_status`, `created_by`, `created_at`, `paid_at`, `last_reset_date`,
                        `is_synced`, `updated_at`, `is_deleted`, `server_id`, `server_updated_at`,
                        `cancel_reason`, `public_token`, `owner_user_id`, `owner_restaurant_id`,
                        `sync_status`, `sync_failure_reason`, `sync_failed_at`, `refund_amount`
                    )
                    SELECT
                        `id`, `restaurant_id`, `device_id`, `daily_order_id`, `daily_order_display`,
                        `lifetime_order_id`, `order_type`, `customer_name`, `customer_whatsapp`, `subtotal`,
                        `gst_percentage`, `cgst_amount`, `sgst_amount`, `custom_tax_amount`, `total_amount`,
                        `payment_mode`, `source_channel`, `part_amount_1`, `part_amount_2`, `payment_status`,
                        `order_status`, `created_by`, `created_at`, `paid_at`, `last_reset_date`,
                        `is_synced`, `updated_at`, `is_deleted`, `server_id`, `server_updated_at`,
                        `cancel_reason`, `public_token`, `owner_user_id`, `owner_restaurant_id`,
                        `sync_status`, `sync_failure_reason`, `sync_failed_at`, `refund_amount`
                    FROM `bills`
                """.trimIndent())

                // 4. Drop the old bills table
                db.execSQL("DROP TABLE IF EXISTS `bills`")

                // 5. Rename bills_new to bills
                db.execSQL("ALTER TABLE `bills_new` RENAME TO `bills`")

                // Older local rows may not have a public token yet. Give them a canonical
                // identity so future pull reconciliation does not depend on lifetime_order_id.
                db.query("SELECT id FROM bills WHERE public_token IS NULL OR public_token = ''").use { cursor ->
                    val ids = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        ids += cursor.getLong(0)
                    }
                    ids.forEach { id ->
                        db.execSQL(
                            "UPDATE `bills` SET `public_token` = ? WHERE `id` = ?",
                            arrayOf(java.util.UUID.randomUUID().toString(), id)
                        )
                    }
                }

                // 6. Recreate indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_created_by` ON `bills` (`created_by`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_order_status` ON `bills` (`order_status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_created_at` ON `bills` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_daily_order_id` ON `bills` (`daily_order_id`)")
            }
        }

        val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("kitchen_print_queue", "public_token")) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `public_token` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kitchen_print_queue", "kot_revision")) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `kot_revision` TEXT DEFAULT NULL")
                }
            }
        }

        val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                android.util.Log.i("AppDatabase", "MIGRATION_57_58 start: terminal ownership backfill")
                if (!db.hasColumn("bills", "terminal_id")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `terminal_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bills", "created_terminal_id")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `created_terminal_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bills", "created_device_id")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `created_device_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bills", "current_owner_terminal_id")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `current_owner_terminal_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bills", "version")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("bills", "lock_status")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `lock_status` TEXT NOT NULL DEFAULT 'unlocked'")
                }
                if (!db.hasColumn("bills", "operation_id")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `operation_id` TEXT DEFAULT NULL")
                }

                db.execSQL(
                    """
                    UPDATE `bills`
                    SET `terminal_id` = COALESCE(NULLIF(`terminal_id`, ''), NULLIF(`terminal_series`, ''), 'LEGACY_UNRESOLVED'),
                        `created_terminal_id` = COALESCE(NULLIF(`created_terminal_id`, ''), NULLIF(`terminal_series`, ''), 'LEGACY_UNRESOLVED'),
                        `created_device_id` = COALESCE(NULLIF(`created_device_id`, ''), NULLIF(`device_id`, '')),
                        `current_owner_terminal_id` = CASE
                            WHEN `order_status` = 'draft'
                            THEN COALESCE(NULLIF(`current_owner_terminal_id`, ''), NULLIF(`terminal_series`, ''), 'LEGACY_UNRESOLVED')
                            ELSE `current_owner_terminal_id`
                        END,
                        `lock_status` = COALESCE(NULLIF(`lock_status`, ''), 'unlocked')
                    """.trimIndent()
                )

                if (!db.hasColumn("bill_payments", "terminal_id")) {
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `terminal_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bill_payments", "bill_public_token")) {
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `bill_public_token` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bill_payments", "operation_id")) {
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `operation_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("bill_payments", "sync_status")) {
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `sync_status` TEXT NOT NULL DEFAULT 'pending'")
                }
                if (!db.hasColumn("bill_payments", "version")) {
                    db.execSQL("ALTER TABLE `bill_payments` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    """
                    UPDATE `bill_payments`
                    SET `terminal_id` = (
                            SELECT COALESCE(NULLIF(`bills`.`terminal_id`, ''), NULLIF(`bills`.`terminal_series`, ''), 'LEGACY_UNRESOLVED')
                            FROM `bills`
                            WHERE `bills`.`id` = `bill_payments`.`bill_id`
                        ),
                        `bill_public_token` = (
                            SELECT `bills`.`public_token`
                            FROM `bills`
                            WHERE `bills`.`id` = `bill_payments`.`bill_id`
                        ),
                        `sync_status` = CASE WHEN `is_synced` = 1 THEN 'synced' ELSE 'pending' END
                    WHERE `bill_id` IN (SELECT `id` FROM `bills`)
                    """.trimIndent()
                )

                if (!db.hasColumn("kot_events", "origin_terminal_id")) {
                    db.execSQL("ALTER TABLE `kot_events` ADD COLUMN `origin_terminal_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kot_events", "origin_device_id")) {
                    db.execSQL("ALTER TABLE `kot_events` ADD COLUMN `origin_device_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kot_events", "event_token")) {
                    db.execSQL("ALTER TABLE `kot_events` ADD COLUMN `event_token` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kot_events", "bill_public_token")) {
                    db.execSQL("ALTER TABLE `kot_events` ADD COLUMN `bill_public_token` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kot_events", "event_version")) {
                    db.execSQL("ALTER TABLE `kot_events` ADD COLUMN `event_version` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    """
                    UPDATE `kot_events`
                    SET `origin_device_id` = COALESCE(NULLIF(`origin_device_id`, ''), NULLIF(`originating_device_id`, '')),
                        `bill_public_token` = COALESCE(NULLIF(`bill_public_token`, ''), `public_token`),
                        `event_token` = COALESCE(NULLIF(`event_token`, ''), `public_token` || ':' || `kot_revision`)
                    """.trimIndent()
                )

                if (!db.hasColumn("kitchen_print_queue", "terminal_id")) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `terminal_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kitchen_print_queue", "device_id")) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `device_id` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kitchen_print_queue", "bill_public_token")) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `bill_public_token` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("kitchen_print_queue", "print_event_token")) {
                    db.execSQL("ALTER TABLE `kitchen_print_queue` ADD COLUMN `print_event_token` TEXT DEFAULT NULL")
                }
                db.execSQL(
                    """
                    UPDATE `kitchen_print_queue`
                    SET `terminal_id` = (
                            SELECT COALESCE(NULLIF(`bills`.`terminal_id`, ''), NULLIF(`bills`.`terminal_series`, ''), 'LEGACY_UNRESOLVED')
                            FROM `bills`
                            WHERE `bills`.`id` = `kitchen_print_queue`.`bill_id`
                        ),
                        `device_id` = (
                            SELECT NULLIF(`bills`.`device_id`, '')
                            FROM `bills`
                            WHERE `bills`.`id` = `kitchen_print_queue`.`bill_id`
                        ),
                        `bill_public_token` = COALESCE(`bill_public_token`, `public_token`, (
                            SELECT `bills`.`public_token`
                            FROM `bills`
                            WHERE `bills`.`id` = `kitchen_print_queue`.`bill_id`
                        )),
                        `print_event_token` = COALESCE(NULLIF(`print_event_token`, ''), COALESCE(`public_token`, '') || ':' || COALESCE(`kot_revision`, '') || ':' || `id`)
                    WHERE `bill_id` IN (SELECT `id` FROM `bills`)
                    """.trimIndent()
                )

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bills_restaurant_public_token` ON `bills` (`restaurant_id`, `public_token`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_restaurant_id_terminal_id_created_at` ON `bills` (`restaurant_id`, `terminal_id`, `created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence` ON `bills` (`restaurant_id`, `financial_year`, `invoice_series`, `invoice_sequence`)")
android.util.Log.i("AppDatabase", "MIGRATION_57_58 complete")
            }
        }

        val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `terminal_daily_counter` (
                        `restaurant_id` INTEGER NOT NULL,
                        `terminal_id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `daily_order_counter` INTEGER NOT NULL DEFAULT 0,
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`restaurant_id`, `terminal_id`, `date`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_terminal_daily_counter_restaurant_date` ON `terminal_daily_counter` (`restaurant_id`, `date`)"
                )
            }
        }

        val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add record_origin and record_scope columns to bills table for terminal ownership isolation
                if (!db.hasColumn("bills", "record_origin")) {
                    db.execSQL(
                        "ALTER TABLE `bills` ADD COLUMN `record_origin` TEXT NOT NULL DEFAULT 'local_created'"
                    )
                }
                if (!db.hasColumn("bills", "record_scope")) {
                    db.execSQL(
                        "ALTER TABLE `bills` ADD COLUMN `record_scope` TEXT NOT NULL DEFAULT 'terminal_operational'"
                    )
                }

                // ── Backfill strategy ──────────────────────────────────────────────────
                // NOTE: We deliberately do NOT infer "this terminal" from restaurant_profile,
                // which has no terminal_id column. The authoritative terminal is only known at
                // runtime (SessionManager), so a one-time runtime reconciliation
                // (BillRepository.reconcileLocalBillScope) corrects local vs server classification
                // after the terminal identity is available. Here we apply a conservative,
                // data-preserving backfill:
                //   • Unsynced (is_synced=0)           → local_created / terminal_operational
                //   • In-progress drafts (created_terminal_id set) → local_created / terminal_operational
                //     (preserves UPI/payment-link drafts that were already pushed but not completed)
                //   • Marketplace channels             → marketplace_imported / restaurant_shared
                //   • All other synced history         → server_imported / restaurant_history
                // No row is deleted and no invoice/sequence is regenerated.

                // 1. Unsynced = locally created operational records (definitely this terminal).
                db.execSQL("""
                    UPDATE `bills`
                    SET `record_origin` = 'local_created',
                        `record_scope` = 'terminal_operational'
                    WHERE `is_synced` = 0 AND `is_deleted` = 0
                """.trimIndent())

                // 2. In-progress drafts remain operational so the user can complete them.
                //    (Covers both unsynced and already-pushed payment-link drafts.)
                db.execSQL("""
                    UPDATE `bills`
                    SET `record_origin` = 'local_created',
                        `record_scope` = 'terminal_operational'
                    WHERE `is_deleted` = 0
                      AND `order_status` = 'draft'
                      AND `payment_status` = 'pending'
                      AND `created_terminal_id` IS NOT NULL
                """.trimIndent())

                // 3. Marketplace orders are shared, regardless of sync state.
                db.execSQL("""
                    UPDATE `bills`
                    SET `record_origin` = 'marketplace_imported',
                        `record_scope` = 'restaurant_shared'
                    WHERE `is_deleted` = 0
                      AND `source_channel` IN ('zomato', 'swiggy', 'own_website')
                """.trimIndent())

                // 4. Remaining synced history (defaulted to local_created) is treated as
                //    read-only server history. The runtime reconciliation re-labels this
                //    terminal's own completed bills back to local_created / terminal_operational.
                db.execSQL("""
                    UPDATE `bills`
                    SET `record_origin` = 'server_imported',
                        `record_scope` = 'restaurant_history'
                    WHERE `is_deleted` = 0
                      AND `record_origin` = 'local_created'
                      AND `is_synced` = 1
                """.trimIndent())

                // Indexes for efficient querying
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_record_origin` ON `bills` (`record_origin`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_record_scope` ON `bills` (`record_scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_terminal_origin_scope` ON `bills` (`created_terminal_id`, `record_origin`, `record_scope`)")

                android.util.Log.i("AppDatabase", "MIGRATION_59_60 complete: record_origin + record_scope added")
            }
        }

        val MIGRATION_60_61 = object : Migration(60, 61) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add payment-attempt tracking columns to bills (used by payment deep-link flow).
                if (!db.hasColumn("bills", "payment_attempt_status")) {
                    db.execSQL(
                        "ALTER TABLE `bills` ADD COLUMN `payment_attempt_status` TEXT NOT NULL DEFAULT 'none'"
                    )
                }
                if (!db.hasColumn("bills", "payment_attempt_started_at")) {
                    db.execSQL(
                        "ALTER TABLE `bills` ADD COLUMN `payment_attempt_started_at` INTEGER DEFAULT NULL"
                    )
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_payment_attempt_status` ON `bills` (`payment_attempt_status`)")
                android.util.Log.i("AppDatabase", "MIGRATION_60_61 complete: payment_attempt_status + payment_attempt_started_at added")
            }
        }

        val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `kot_events` (
                        `public_token` TEXT NOT NULL,
                        `kot_revision` TEXT NOT NULL,
                        `event_type` TEXT NOT NULL,
                        `item_snapshot_json` TEXT NOT NULL,
                        `originating_device_id` TEXT NOT NULL,
                        `is_printed` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`public_token`, `kot_revision`)
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kot_events_public_token` ON `kot_events` (`public_token`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_kot_events_originating_device_id` ON `kot_events` (`originating_device_id`)")
            }
        }

        val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("bills", "refund_amount")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `refund_amount` TEXT DEFAULT NULL")
                }
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
}
