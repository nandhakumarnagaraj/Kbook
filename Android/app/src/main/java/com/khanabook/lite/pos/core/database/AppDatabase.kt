package com.khanabook.lite.pos.core.database
import com.khanabook.lite.pos.feature.staff.data.*
import com.khanabook.lite.pos.feature.printing.data.*
import com.khanabook.lite.pos.feature.sync.data.*
import com.khanabook.lite.pos.feature.billing.data.*
import com.khanabook.lite.pos.feature.auth.data.*

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khanabook.lite.pos.feature.notifications.data.NotificationDao
import com.khanabook.lite.pos.feature.notifications.data.NotificationEntity
import com.khanabook.lite.pos.feature.notifications.data.*
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillPaymentEntity
import com.khanabook.lite.pos.feature.billing.data.TerminalDailyCounterEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.UserEntity
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity

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
                        KotEventEntity::class,
                        TerminalDailyCounterEntity::class,
                        NotificationEntity::class,
                        StaffPermissionEntity::class,
                        PermissionCacheEntity::class
                ],
        version = 79,
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
    abstract fun kotEventDao(): KotEventDao
    abstract fun notificationDao(): NotificationDao
    abstract fun permissionCacheDao(): PermissionCacheDao

	    companion object {
	        const val DATABASE_NAME = "khanabook_lite_db"

        /**
         * The complete, ordered migration chain. Both Room builders
         * (DatabaseProvider and DatabaseModule) MUST reference this array —
         * never register individual migrations again, so the lists can never
         * drift apart (the drift hazard that lost 19→20…25→26 in the
         * Sep 2026 restructure).
         */
        // Property getter (not a val initializer): the individual migrations are
        // declared below, and a val initializer would snapshot them as null during
        // companion-object construction.
        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(
            MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
            MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
            MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
            MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33,
            MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37,
            MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41,
            MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45,
            MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49,
            MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53,
            MIGRATION_53_54, MIGRATION_54_55, MIGRATION_55_56, MIGRATION_56_57,
            MIGRATION_57_58, MIGRATION_58_59, MIGRATION_59_60, MIGRATION_60_61,
            MIGRATION_61_62, MIGRATION_62_63, MIGRATION_63_64, MIGRATION_64_65,
            MIGRATION_65_66, MIGRATION_66_67, MIGRATION_67_68, MIGRATION_68_69,
            MIGRATION_69_70, MIGRATION_70_71, MIGRATION_71_72, MIGRATION_72_73,
            MIGRATION_73_74, MIGRATION_74_75, MIGRATION_75_76, MIGRATION_76_77,
            MIGRATION_77_78, MIGRATION_78_79
        )

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
                // easebuzz_enabled is guarded: some v39-era devices received it
                // out-of-band (background sync ALTER) and a duplicate ALTER here
                // aborted the whole chain (SQLiteException: duplicate column).
                if (!db.hasColumn("restaurant_profile", "easebuzz_enabled")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `easebuzz_enabled` INTEGER NOT NULL DEFAULT 0")
                }
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
                // The old ALTER-only implementation could never produce the v19
                // schema: a plain ADD COLUMN cannot clear a NOT NULL constraint,
                // and Room's post-migration validation rejects NOT NULL drift on
                // country/currency. Rebuild the table to guarantee the exact v19
                // shape (country/currency nullable with 'India'/'INR' defaults).
                if (!db.hasColumn("restaurant_profile", "country")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `country` TEXT DEFAULT 'India'")
                }
                if (!db.hasColumn("restaurant_profile", "currency")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `currency` TEXT DEFAULT 'INR'")
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `restaurant_profile_new` (
                        `id` INTEGER NOT NULL,
                        `shop_name` TEXT,
                        `shop_address` TEXT,
                        `whatsapp_number` TEXT,
                        `email` TEXT,
                        `logo_path` TEXT,
                        `fssai_number` TEXT,
                        `email_invoice_consent` INTEGER NOT NULL DEFAULT 0,
                        `country` TEXT DEFAULT 'India',
                        `gst_enabled` INTEGER NOT NULL DEFAULT 0,
                        `gstin` TEXT,
                        `is_tax_inclusive` INTEGER NOT NULL DEFAULT 0,
                        `gst_percentage` REAL NOT NULL DEFAULT 0.0,
                        `custom_tax_name` TEXT,
                        `custom_tax_number` TEXT,
                        `custom_tax_percentage` REAL NOT NULL DEFAULT 0.0,
                        `currency` TEXT DEFAULT 'INR',
                        `upi_enabled` INTEGER NOT NULL DEFAULT 0,
                        `upi_qr_path` TEXT,
                        `upi_handle` TEXT,
                        `upi_mobile` TEXT,
                        `cash_enabled` INTEGER NOT NULL DEFAULT 1,
                        `pos_enabled` INTEGER NOT NULL DEFAULT 0,
                        `zomato_enabled` INTEGER NOT NULL DEFAULT 0,
                        `swiggy_enabled` INTEGER NOT NULL DEFAULT 0,
                        `own_website_enabled` INTEGER NOT NULL DEFAULT 0,
                        `printer_enabled` INTEGER NOT NULL DEFAULT 0,
                        `printer_name` TEXT,
                        `printer_mac` TEXT,
                        `paper_size` TEXT NOT NULL DEFAULT '58mm',
                        `auto_print_on_success` INTEGER NOT NULL DEFAULT 0,
                        `include_logo_in_print` INTEGER NOT NULL DEFAULT 1,
                        `print_customer_whatsapp` INTEGER NOT NULL DEFAULT 1,
                        `daily_order_counter` INTEGER NOT NULL DEFAULT 0,
                        `lifetime_order_counter` INTEGER NOT NULL DEFAULT 0,
                        `last_reset_date` TEXT,
                        `session_timeout_minutes` INTEGER NOT NULL DEFAULT 30,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO `restaurant_profile_new` SELECT * FROM `restaurant_profile`")
                db.execSQL("DROP TABLE `restaurant_profile`")
                db.execSQL("ALTER TABLE `restaurant_profile_new` RENAME TO `restaurant_profile`")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema 19.json and 20.json are byte-identical apart from the
                // version bump (verified by diff) — this step existed only as a
                // release marker and was lost in the restructure.
                android.util.Log.i("AppDatabase", "MIGRATION_19_20: no-op (schemas identical)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v21 also adds restaurant_profile.timezone (nullable, default
                // 'Asia/Kolkata') — plain ADD COLUMN, no rebuild needed.
                if (!db.hasColumn("restaurant_profile", "timezone")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `timezone` TEXT DEFAULT 'Asia/Kolkata'")
                }
                // v21 changes column affinities that ALTER TABLE cannot modify:
                //   users/categories: created_at TEXT -> INTEGER NOT NULL
                //   menu_items:       base_price REAL -> TEXT, current_stock/
                //                     low_stock_threshold REAL -> TEXT, adds barcode
                // Table rebuilds are required. created_at ISO strings cast to 0 —
                // acceptable: local timestamps are display hints, refreshed by sync.

                // 1. menu_items first (it holds the FK on categories).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `menu_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `base_price` TEXT NOT NULL,
                        `food_type` TEXT NOT NULL DEFAULT 'veg',
                        `description` TEXT,
                        `is_available` INTEGER NOT NULL DEFAULT 1,
                        `current_stock` TEXT NOT NULL DEFAULT '0.0',
                        `low_stock_threshold` TEXT NOT NULL DEFAULT '10.0',
                        `created_at` INTEGER NOT NULL,
                        `barcode` TEXT DEFAULT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `menu_items_new` (`id`, `category_id`, `name`, `base_price`, `food_type`, " +
                        "`description`, `is_available`, `current_stock`, `low_stock_threshold`, `created_at`, " +
                        "`restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted`) " +
                        "SELECT `id`, `category_id`, `name`, `base_price`, `food_type`, `description`, " +
                        "`is_available`, `current_stock`, `low_stock_threshold`, CAST(`created_at` AS INTEGER), " +
                        "`restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted` FROM `menu_items`"
                )
                db.execSQL("DROP TABLE `menu_items`")
                db.execSQL("ALTER TABLE `menu_items_new` RENAME TO `menu_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_items_category_id` ON `menu_items` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_menu_items_is_available` ON `menu_items` (`is_available`)")

                // 2. categories.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `categories_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `is_veg` INTEGER NOT NULL,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `categories_new` (`id`, `name`, `is_veg`, `sort_order`, `is_active`, " +
                        "`created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted`) " +
                        "SELECT `id`, `name`, `is_veg`, `sort_order`, `is_active`, CAST(`created_at` AS INTEGER), " +
                        "`restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted` FROM `categories`"
                )
                db.execSQL("DROP TABLE `categories`")
                db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")

                // 3. users.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `password_hash` TEXT,
                        `whatsapp_number` TEXT,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `users_new` (`id`, `name`, `email`, `password_hash`, `whatsapp_number`, " +
                        "`is_active`, `created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, " +
                        "`is_deleted`) SELECT `id`, `name`, `email`, `password_hash`, `whatsapp_number`, " +
                        "`is_active`, CAST(`created_at` AS INTEGER), `restaurant_id`, `device_id`, `is_synced`, " +
                        "`updated_at`, `is_deleted` FROM `users`"
                )
                db.execSQL("DROP TABLE `users`")
                db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")

                // 4. stock_logs: delta REAL->TEXT, created_at TEXT->INTEGER.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_logs_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menu_item_id` INTEGER NOT NULL,
                        `variant_id` INTEGER,
                        `delta` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`menu_item_id`) REFERENCES `menu_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `stock_logs_new` (`id`, `menu_item_id`, `variant_id`, `delta`, `reason`, " +
                        "`created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted`) " +
                        "SELECT `id`, `menu_item_id`, `variant_id`, CAST(`delta` AS TEXT), `reason`, " +
                        "CAST(`created_at` AS INTEGER), `restaurant_id`, `device_id`, `is_synced`, " +
                        "`updated_at`, `is_deleted` FROM `stock_logs`"
                )
                db.execSQL("DROP TABLE `stock_logs`")
                db.execSQL("ALTER TABLE `stock_logs_new` RENAME TO `stock_logs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_logs_menu_item_id` ON `stock_logs` (`menu_item_id`)")

                // 5. item_variants: price/current_stock/low_stock_threshold REAL->TEXT.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `item_variants_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menu_item_id` INTEGER NOT NULL,
                        `variant_name` TEXT NOT NULL,
                        `price` TEXT NOT NULL,
                        `is_available` INTEGER NOT NULL DEFAULT 1,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `current_stock` TEXT NOT NULL DEFAULT '0.0',
                        `low_stock_threshold` TEXT NOT NULL DEFAULT '10.0',
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`menu_item_id`) REFERENCES `menu_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `item_variants_new` (`id`, `menu_item_id`, `variant_name`, `price`, " +
                        "`is_available`, `sort_order`, `current_stock`, `low_stock_threshold`, `restaurant_id`, " +
                        "`device_id`, `is_synced`, `updated_at`, `is_deleted`) " +
                        "SELECT `id`, `menu_item_id`, `variant_name`, CAST(`price` AS TEXT), `is_available`, " +
                        "`sort_order`, CAST(`current_stock` AS TEXT), CAST(`low_stock_threshold` AS TEXT), " +
                        "`restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted` FROM `item_variants`"
                )
                db.execSQL("DROP TABLE `item_variants`")
                db.execSQL("ALTER TABLE `item_variants_new` RENAME TO `item_variants`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_variants_menu_item_id` ON `item_variants` (`menu_item_id`)")

                // 6. bills: all money columns REAL->TEXT, created_at/paid_at TEXT->INTEGER.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bills_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `daily_order_id` INTEGER NOT NULL,
                        `daily_order_display` TEXT NOT NULL,
                        `lifetime_order_id` INTEGER NOT NULL,
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
                        `part_amount_1` TEXT NOT NULL DEFAULT '0.0',
                        `part_amount_2` TEXT NOT NULL DEFAULT '0.0',
                        `payment_status` TEXT NOT NULL,
                        `order_status` TEXT NOT NULL,
                        `created_by` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `paid_at` INTEGER,
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`created_by`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `bills_new` (`id`, `restaurant_id`, `device_id`, `daily_order_id`, " +
                        "`daily_order_display`, `lifetime_order_id`, `order_type`, `customer_name`, " +
                        "`customer_whatsapp`, `subtotal`, `gst_percentage`, `cgst_amount`, `sgst_amount`, " +
                        "`custom_tax_amount`, `total_amount`, `payment_mode`, `part_amount_1`, `part_amount_2`, " +
                        "`payment_status`, `order_status`, `created_by`, `created_at`, `paid_at`, `is_synced`, " +
                        "`updated_at`, `is_deleted`) " +
                        "SELECT `id`, `restaurant_id`, `device_id`, `daily_order_id`, `daily_order_display`, " +
                        "`lifetime_order_id`, `order_type`, `customer_name`, `customer_whatsapp`, " +
                        "CAST(`subtotal` AS TEXT), CAST(`gst_percentage` AS TEXT), CAST(`cgst_amount` AS TEXT), " +
                        "CAST(`sgst_amount` AS TEXT), CAST(`custom_tax_amount` AS TEXT), CAST(`total_amount` AS TEXT), " +
                        "`payment_mode`, CAST(`part_amount_1` AS TEXT), CAST(`part_amount_2` AS TEXT), " +
                        "`payment_status`, `order_status`, `created_by`, CAST(`created_at` AS INTEGER), " +
                        "CAST(`paid_at` AS INTEGER), `is_synced`, `updated_at`, `is_deleted` FROM `bills`"
                )
                db.execSQL("DROP TABLE `bills`")
                db.execSQL("ALTER TABLE `bills_new` RENAME TO `bills`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_created_by` ON `bills` (`created_by`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_order_status` ON `bills` (`order_status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_created_at` ON `bills` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_daily_order_id` ON `bills` (`daily_order_id`)")

                // 7. bill_items: price/item_total REAL->TEXT.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bill_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bill_id` INTEGER NOT NULL,
                        `menu_item_id` INTEGER,
                        `item_name` TEXT NOT NULL,
                        `variant_id` INTEGER,
                        `variant_name` TEXT,
                        `price` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `item_total` TEXT NOT NULL,
                        `special_instruction` TEXT,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`menu_item_id`) REFERENCES `menu_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`variant_id`) REFERENCES `item_variants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `bill_items_new` (`id`, `bill_id`, `menu_item_id`, `item_name`, `variant_id`, " +
                        "`variant_name`, `price`, `quantity`, `item_total`, `special_instruction`, `restaurant_id`, " +
                        "`device_id`, `is_synced`, `updated_at`, `is_deleted`) " +
                        "SELECT `id`, `bill_id`, `menu_item_id`, `item_name`, `variant_id`, `variant_name`, " +
                        "CAST(`price` AS TEXT), `quantity`, CAST(`item_total` AS TEXT), `special_instruction`, " +
                        "`restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted` FROM `bill_items`"
                )
                db.execSQL("DROP TABLE `bill_items`")
                db.execSQL("ALTER TABLE `bill_items_new` RENAME TO `bill_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_bill_id` ON `bill_items` (`bill_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_menu_item_id` ON `bill_items` (`menu_item_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_variant_id` ON `bill_items` (`variant_id`)")

                // 8. bill_payments: amount REAL->TEXT.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bill_payments_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bill_id` INTEGER NOT NULL,
                        `payment_mode` TEXT NOT NULL,
                        `amount` TEXT NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `bill_payments_new` (`id`, `bill_id`, `payment_mode`, `amount`, `restaurant_id`, " +
                        "`device_id`, `is_synced`, `updated_at`, `is_deleted`) " +
                        "SELECT `id`, `bill_id`, `payment_mode`, CAST(`amount` AS TEXT), `restaurant_id`, " +
                        "`device_id`, `is_synced`, `updated_at`, `is_deleted` FROM `bill_payments`"
                )
                db.execSQL("DROP TABLE `bill_payments`")
                db.execSQL("ALTER TABLE `bill_payments_new` RENAME TO `bill_payments`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_payments_bill_id` ON `bill_payments` (`bill_id`)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 22.json and 23.json are identical apart from the version bump.
                android.util.Log.i("AppDatabase", "MIGRATION_22_23: no-op (schemas identical)")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v22 adds both role AND pin_hash. The original implementation only
                // added role — a schema validation failure on every device that ran
                // it (role was committed, pin_hash never was, leaving those devices
                // stuck at v22 with a mismatched identity hash). MIGRATION_24_25
                // tolerates both worlds via hasColumn guards.
                if (!db.hasColumn("users", "role")) {
                    db.execSQL("ALTER TABLE `users` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'owner'")
                }
                if (!db.hasColumn("users", "pin_hash")) {
                    db.execSQL("ALTER TABLE `users` ADD COLUMN `pin_hash` TEXT")
                }
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `show_branding` INTEGER NOT NULL DEFAULT 1")
                // v24 also adds bills.last_reset_date (NOT NULL DEFAULT '').
                if (!db.hasColumn("bills", "last_reset_date")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `last_reset_date` TEXT NOT NULL DEFAULT ''")
                }
                // v24 also renames the categories unique index to be
                // restaurant-scoped (multi-tenant support). The original
                // implementation never touched the index — validation gap.
                db.execSQL("DROP INDEX IF EXISTS `index_categories_name`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_restaurant_id_name` " +
                        "ON `categories` (`restaurant_id`, `name`)"
                )
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v25 removes password_hash from users (device no longer stores
                // passwords; auth moved to server-side tokens). DROP COLUMN is not
                // available on older SQLite levels Room supports, so rebuild.
                // Tolerates devices stuck mid-flight from the historically broken
                // MIGRATION_21_22 (role present without pin_hash).
                val roleSelect = if (db.hasColumn("users", "role")) "`role`" else "'owner' AS `role`"
                val pinSelect = if (db.hasColumn("users", "pin_hash")) "`pin_hash`" else "NULL AS `pin_hash`"
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `whatsapp_number` TEXT,
                        `role` TEXT NOT NULL DEFAULT 'owner',
                        `pin_hash` TEXT,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `users_new` (`id`, `name`, `email`, `whatsapp_number`, `role`, `pin_hash`, " +
                        "`is_active`, `created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, " +
                        "`is_deleted`) SELECT `id`, `name`, `email`, `whatsapp_number`, $roleSelect, $pinSelect, " +
                        "`is_active`, `created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, " +
                        "`is_deleted` FROM `users`"
                )
                db.execSQL("DROP TABLE `users`")
                db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("restaurant_profile", "mask_customer_phone")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `mask_customer_phone` INTEGER NOT NULL DEFAULT 1")
                }
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

                // 3. (Marketplace orders removed - this space reserved for future use)

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

        val MIGRATION_61_62 = object : Migration(61, 62) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Normalize legacy blank/deleted identities before enforcing the
                    // Room-declared unique index. SQLite permits multiple NULL values,
                    // so historical rows remain compatible without allowing blank
                    // identities to collide.
                    db.execSQL(
                        "UPDATE bill_payments SET operation_id = NULL " +
                            "WHERE operation_id IS NOT NULL AND (operation_id = '' OR is_deleted = 1)"
                    )
                    //
                    // Pre-migration duplicate resolution:
                    //   At version 61, operation_id was added with DEFAULT NULL (via MIGRATION_57_58),
                    //   so most devices have no data. However, a direct SQLite write or edge case
                    //   could have produced duplicates. For each duplicate group, compare the
                    //   immutable semantic fields:
                    //     - restaurant_id, bill_id, bill_public_token, operation_id
                    //     - amount, payment_mode, terminal_id
                    //     - gateway_txn_id, gateway_status, verified_by
                    //   If all semantic fields match, it is an exact duplicate and later rows
                    //   are safely soft-deleted. If ANY field differs, the migration refuses
                    //   with a clear diagnostic so no financial data is silently altered.
                    val dupCheckSql = """
                        SELECT operation_id, restaurant_id, COUNT(*), MIN(id)
                        FROM bill_payments
                        WHERE operation_id IS NOT NULL AND operation_id != '' AND is_deleted = 0
                        GROUP BY restaurant_id, operation_id
                        HAVING COUNT(*) > 1
                    """.trimIndent()
                    db.query(dupCheckSql).use { cursor ->
                        var exactDuplicateGroups = 0
                        while (cursor.moveToNext()) {
                            val opId = cursor.getString(0)
                            val restId = cursor.getLong(1)
                            val keepId = cursor.getLong(3)

                            // Load all rows in this duplicate group to compare semantic fields.
                            val groupSql = """
                                SELECT id, bill_id, COALESCE(bill_public_token, ''),
                                       amount, payment_mode, COALESCE(terminal_id, ''),
                                       COALESCE(gateway_txn_id, ''), COALESCE(gateway_status, ''), verified_by
                                FROM bill_payments
                                WHERE operation_id = ? AND restaurant_id = ?
                                  AND is_deleted = 0
                                ORDER BY id
                            """.trimIndent()
                            val groupRows = mutableListOf<Array<String?>>()
                            db.query(groupSql, arrayOf(opId, restId)).use { groupCursor ->
                                while (groupCursor.moveToNext()) {
                                    groupRows.add(Array(9) { i -> groupCursor.getString(i) })
                                }
                            }

                            // Compare all rows to the kept row (MIN id).
                            val keep = groupRows.firstOrNull { it[0] == keepId.toString() }
                                ?: continue // safety: keep row gone, skip
                            val semanticFields = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
                            var allExact = true
                            for (row in groupRows) {
                                if (row[0] == keepId.toString()) continue
                                for (field in semanticFields) {
                                    val keepVal = keep[field] ?: ""
                                    val rowVal = row[field] ?: ""
                                    if (keepVal != rowVal) {
                                        android.util.Log.e("AppDatabase",
                                            "MIGRATION_61_62: CONFLICTING duplicate (restaurant=$restId opId=$opId): " +
                                            "id=${keepId} $keepVal vs id=${row[0]} $rowVal on field index $field")
                                        allExact = false
                                        break
                                    }
                                }
                                if (!allExact) break
                            }

                            if (allExact) {
                                // Exact duplicates: soft-delete later rows.
                                db.execSQL(
                                    """
                                    UPDATE bill_payments SET is_deleted = 1, operation_id = NULL
                                    WHERE operation_id = ? AND restaurant_id = ?
                                      AND is_deleted = 0 AND id != ?
                                    """.trimIndent(),
                                    arrayOf(opId, restId, keepId)
                                )
                                exactDuplicateGroups++
                            } else {
                                // Conflicting duplicates: abort migration.
                                val errMsg = "MIGRATION_61_62 FAILED: Conflicting payment rows share " +
                                    "operation_id='$opId' in restaurant $restId. " +
                                    "Cannot migrate with conflicting financial data. " +
                                    "Manually reconcile bill_payments rows and re-run migration."
                                android.util.Log.e("AppDatabase", errMsg)
                                throw IllegalStateException(errMsg)
                            }
                        }
                        if (exactDuplicateGroups > 0) {
                            android.util.Log.w("AppDatabase",
                                "MIGRATION_61_62: soft-deleted $exactDuplicateGroups exact duplicate operation_id group(s)")
                        }
                    }

                    // The entity declares this as a regular unique index. Historical
                    // blank/deleted identities were normalized to NULL above.
                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS `idx_bill_payments_restaurant_operation`
                        ON `bill_payments` (`restaurant_id`, `operation_id`)
                        """.trimIndent()
                    )
                    android.util.Log.i("AppDatabase", "MIGRATION_61_62 complete: partial unique index on (restaurant_id, operation_id) for bill_payments")
                }
            }

        val MIGRATION_62_63 = object : Migration(62, 63) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("printer_profiles", "connection_type")) {
                    db.execSQL(
                        "ALTER TABLE `printer_profiles` ADD COLUMN `connection_type` TEXT NOT NULL DEFAULT 'BLUETOOTH'"
                    )
                }
                if (!db.hasColumn("printer_profiles", "host")) {
                    db.execSQL("ALTER TABLE `printer_profiles` ADD COLUMN `host` TEXT DEFAULT NULL")
                }
                if (!db.hasColumn("printer_profiles", "port")) {
                    db.execSQL("ALTER TABLE `printer_profiles` ADD COLUMN `port` INTEGER NOT NULL DEFAULT 9100")
                }
            }
        }

        val MIGRATION_64_65 = object : Migration(64, 65) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("restaurant_profile", "fssai_expiry_date")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `fssai_expiry_date` TEXT")
                }
            }
        }

        val MIGRATION_65_66 = object : Migration(65, 66) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `staff_permissions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `restaurant_id` INTEGER NOT NULL,
                        `user_id` INTEGER NOT NULL,
                        `permission_key` TEXT NOT NULL,
                        `granted` INTEGER NOT NULL DEFAULT 1,
                        `granted_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_permissions_restaurant_id_user_id` ON `staff_permissions` (`restaurant_id`, `user_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_permissions_restaurant_id_user_id_permission_key` ON `staff_permissions` (`restaurant_id`, `user_id`, `permission_key`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `permission_requests` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `restaurant_id` INTEGER NOT NULL,
                        `user_id` INTEGER NOT NULL,
                        `permission_key` TEXT NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'PENDING',
                        `reason` TEXT,
                        `requested_at` INTEGER NOT NULL,
                        `resolved_at` INTEGER,
                        `rejection_reason` TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_permission_requests_restaurant_id_user_id_status` ON `permission_requests` (`restaurant_id`, `user_id`, `status`)")
            }
        }

        val MIGRATION_66_67 = object : Migration(66, 67) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // easebuzz_enabled was already added in MIGRATION_39_40, so guard with hasColumn
                if (!db.hasColumn("restaurant_profile", "easebuzz_enabled")) {
                    db.execSQL("ALTER TABLE restaurant_profile ADD COLUMN easebuzz_enabled INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val MIGRATION_69_70 = object : Migration(69, 70) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // P1: stamp the acting user's permission revision on locally-created
                // menu edits so the server can run Decision-A-strict revalidation.
                if (!db.hasColumn("menu_items", "permission_revision_at_creation")) {
                    db.execSQL(
                        "ALTER TABLE `menu_items` ADD COLUMN `permission_revision_at_creation` INTEGER DEFAULT NULL"
                    )
                }
                // Persist granted permissions + revision so they survive process death offline.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `permission_cache` (
                        `user_id` INTEGER PRIMARY KEY NOT NULL,
                        `granted_csv` TEXT NOT NULL,
                        `permission_revision` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                android.util.Log.i("AppDatabase", "MIGRATION_69_70 complete: menu permission_revision + permission_cache")
            }
        }

        val MIGRATION_70_71 = object : Migration(70, 71) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Phase 1+2: field-level merge — add changed_fields column to track which
                // fields the device actually modified, so the server can merge only
                // those fields instead of whole-record LWW.
                if (!db.hasColumn("menu_items", "changed_fields")) {
                    db.execSQL(
                        "ALTER TABLE `menu_items` ADD COLUMN `changed_fields` TEXT DEFAULT NULL"
                    )
                }
                if (!db.hasColumn("restaurant_profile", "changed_fields")) {
                    db.execSQL(
                        "ALTER TABLE `restaurant_profile` ADD COLUMN `changed_fields` TEXT DEFAULT NULL"
                    )
                }
                android.util.Log.i("AppDatabase", "MIGRATION_70_71 complete: menu + profile changed_fields")
            }
        }
        val MIGRATION_72_73 = object : Migration(72, 73) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("menu_items", "image_url")) {
                    db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `image_url` TEXT")
                }
                if (!db.hasColumn("menu_items", "image_version")) {
                    db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `image_version` INTEGER NOT NULL DEFAULT 0")
                }
                android.util.Log.i("AppDatabase", "MIGRATION_72_73 complete: added image_url and image_version to menu_items")
            }
        }

        val MIGRATION_73_74 = object : Migration(73, 74) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `permission_requests`")
                android.util.Log.i("AppDatabase", "MIGRATION_73_74 complete: dropped unused permission_requests table")
            }
        }

        val MIGRATION_74_75 = object : Migration(74, 75) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("bills", "status_version")) {
                    db.execSQL("ALTER TABLE `bills` ADD COLUMN `status_version` INTEGER NOT NULL DEFAULT 0")
                }
                android.util.Log.i("AppDatabase", "MIGRATION_74_75 complete: added status_version to bills")
            }
        }

        val MIGRATION_75_76 = object : Migration(75, 76) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("restaurant_profile", "collect_customer_number")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `collect_customer_number` INTEGER NOT NULL DEFAULT 1")
                }
                android.util.Log.i("AppDatabase", "MIGRATION_75_76 complete: added collect_customer_number to restaurant_profile")
            }
        }

        val MIGRATION_76_77 = object : Migration(76, 77) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("menu_items", "has_variants")) {
                    db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `has_variants` INTEGER NOT NULL DEFAULT 0")
                }
                // Backfill from reality rather than trusting the default. An item is a
                // variant container if any of its variants survive. A stale FALSE on a real
                // container is recoverable; a stale TRUE on a simple item would render a
                // meaningless "from Rs." price, so only ever promote, never demote.
                db.execSQL(
                    """
                    UPDATE `menu_items`
                    SET `has_variants` = 1
                    WHERE EXISTS (
                        SELECT 1 FROM `item_variants` v
                        WHERE v.`menu_item_id` = `menu_items`.`id`
                          AND v.`is_deleted` = 0
                    )
                    """.trimIndent()
                )
                android.util.Log.i("AppDatabase", "MIGRATION_76_77 complete: added has_variants to menu_items")
            }
        }

        val MIGRATION_77_78 = object : Migration(77, 78) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Inventory feature removed 2026-09-29: the stock_logs ledger and its
                // sync path are gone. The ledger was headless (no Android surface ever
                // read it), so historical rows are dropped rather than preserved.
                // Menu-item/variant stock columns on menu tables are NOT removed —
                // they are menu configuration, still synced and still displayed.
                db.execSQL("DROP TABLE IF EXISTS `stock_logs`")
                android.util.Log.i("AppDatabase", "MIGRATION_77_78 complete: dropped stock_logs")
            }
        }

        val MIGRATION_78_79 = object : Migration(78, 79) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // End-state repair (Crashlytics issue 9a3ca081, OnePlus CPH2423,
                // app 1.0.33): real devices that MIGRATED to 78 carry legacy
                // defects the fresh-install schema never has — explicit
                // "DEFAULT NULL" from 27_28/32_33 ALTERs, a role default of
                // 'owner' where the spec says 'OWNER', a pin_hash column no
                // migration ever drops, and bills indices from dropped entity
                // definitions. SQLite cannot alter defaults or drop constraints
                // in place, so both tables are rebuilt to the exact current
                // entity spec. Data is fully preserved; per-column hasColumn
                // guards tolerate devices whose guarded historical ALTERs
                // silently failed.

                // Stale indices: old migrations created indices that later
                // entity changes removed; no migration ever dropped them and
                // schema validation requires an exact index set match.
                db.execSQL("DROP INDEX IF EXISTS `index_terminal_daily_counter_restaurant_date`")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_read`")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_created_at`")

                // ── users ───────────────────────────────────────────────
                fun uCol(name: String, fallback: String): String =
                    if (db.hasColumn("users", name)) "`$name`" else "$fallback AS `$name`"
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `login_id` TEXT,
                        `google_email` TEXT,
                        `auth_provider` TEXT NOT NULL DEFAULT 'PHONE',
                        `phone_number` TEXT,
                        `whatsapp_number` TEXT,
                        `role` TEXT NOT NULL DEFAULT 'OWNER',
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_at` INTEGER NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `token_invalidated_at` INTEGER,
                        `server_id` INTEGER,
                        `server_updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                val userCols = listOf(
                    "`id`", "`name`", "`email`",
                    uCol("login_id", "NULL"),
                    uCol("google_email", "NULL"),
                    uCol("auth_provider", "'PHONE'"),
                    uCol("phone_number", "NULL"),
                    "`whatsapp_number`",
                    uCol("role", "'OWNER'"),
                    uCol("is_active", "1"),
                    uCol("created_at", "0"),
                    uCol("restaurant_id", "0"),
                    uCol("device_id", "''"),
                    uCol("is_synced", "0"),
                    uCol("updated_at", "0"),
                    uCol("is_deleted", "0"),
                    uCol("token_invalidated_at", "NULL"),
                    uCol("server_id", "NULL"),
                    uCol("server_updated_at", "0")
                )
                db.execSQL(
                    "INSERT INTO `users_new` (`id`, `name`, `email`, `login_id`, `google_email`, " +
                        "`auth_provider`, `phone_number`, `whatsapp_number`, `role`, `is_active`, " +
                        "`created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, " +
                        "`is_deleted`, `token_invalidated_at`, `server_id`, `server_updated_at`) " +
                        "SELECT " + userCols.joinToString(", ") + " FROM `users`"
                )
                db.execSQL("DROP TABLE `users`")
                db.execSQL("ALTER TABLE `users_new` RENAME TO `users`")

                // ── bills ───────────────────────────────────────────────
                fun bCol(name: String, fallback: String): String =
                    if (db.hasColumn("bills", name)) "`$name`" else "$fallback AS `$name`"
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bills_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `terminal_id` TEXT DEFAULT NULL,
                        `created_terminal_id` TEXT DEFAULT NULL,
                        `created_device_id` TEXT DEFAULT NULL,
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
                        `status_version` INTEGER NOT NULL DEFAULT 0,
                        `created_by` INTEGER,
                        `created_by_user_id` INTEGER DEFAULT NULL,
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
                        `current_owner_terminal_id` TEXT DEFAULT NULL,
                        `version` INTEGER NOT NULL DEFAULT 0,
                        `lock_status` TEXT NOT NULL DEFAULT 'unlocked',
                        `operation_id` TEXT DEFAULT NULL,
                        `record_origin` TEXT NOT NULL DEFAULT 'local_created',
                        `record_scope` TEXT NOT NULL DEFAULT 'terminal_operational',
                        `payment_attempt_status` TEXT NOT NULL DEFAULT 'none',
                        `payment_attempt_started_at` INTEGER DEFAULT NULL,
                        FOREIGN KEY(`created_by`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                val billCols = listOf(
                    "`id`", "`restaurant_id`", "`device_id`",
                    bCol("terminal_id", "NULL"),
                    bCol("created_terminal_id", "NULL"),
                    bCol("created_device_id", "NULL"),
                    bCol("daily_order_id", "0"),
                    bCol("daily_order_display", "''"),
                    bCol("lifetime_order_id", "NULL"),
                    bCol("order_type", "'order'"),
                    bCol("customer_name", "NULL"),
                    bCol("customer_whatsapp", "NULL"),
                    bCol("subtotal", "'0.0'"),
                    bCol("gst_percentage", "'0.0'"),
                    bCol("cgst_amount", "'0.0'"),
                    bCol("sgst_amount", "'0.0'"),
                    bCol("custom_tax_amount", "'0.0'"),
                    bCol("total_amount", "'0.0'"),
                    bCol("payment_mode", "'CASH'"),
                    bCol("source_channel", "''"),
                    bCol("part_amount_1", "'0.0'"),
                    bCol("part_amount_2", "'0.0'"),
                    bCol("payment_status", "'pending'"),
                    bCol("order_status", "'pending'"),
                    bCol("status_version", "0"),
                    bCol("created_by", "NULL"),
                    bCol("created_by_user_id", "NULL"),
                    bCol("created_at", "0"),
                    bCol("paid_at", "NULL"),
                    bCol("last_reset_date", "''"),
                    bCol("is_synced", "0"),
                    bCol("updated_at", "0"),
                    bCol("is_deleted", "0"),
                    bCol("server_id", "NULL"),
                    bCol("server_updated_at", "0"),
                    bCol("cancel_reason", "''"),
                    bCol("public_token", "NULL"),
                    bCol("owner_user_id", "NULL"),
                    bCol("owner_restaurant_id", "NULL"),
                    bCol("sync_status", "'pending'"),
                    bCol("sync_failure_reason", "NULL"),
                    bCol("sync_failed_at", "NULL"),
                    bCol("terminal_series", "NULL"),
                    bCol("financial_year", "NULL"),
                    bCol("invoice_series", "NULL"),
                    bCol("invoice_sequence", "NULL"),
                    bCol("invoice_number", "NULL"),
                    bCol("refund_amount", "NULL"),
                    bCol("current_owner_terminal_id", "NULL"),
                    bCol("version", "0"),
                    bCol("lock_status", "'unlocked'"),
                    bCol("operation_id", "NULL"),
                    bCol("record_origin", "'local_created'"),
                    bCol("record_scope", "'terminal_operational'"),
                    bCol("payment_attempt_status", "'none'"),
                    bCol("payment_attempt_started_at", "NULL")
                )
                db.execSQL(
                    "INSERT INTO `bills_new` (`id`, `restaurant_id`, `device_id`, `terminal_id`, " +
                        "`created_terminal_id`, `created_device_id`, `daily_order_id`, " +
                        "`daily_order_display`, `lifetime_order_id`, `order_type`, `customer_name`, " +
                        "`customer_whatsapp`, `subtotal`, `gst_percentage`, `cgst_amount`, `sgst_amount`, " +
                        "`custom_tax_amount`, `total_amount`, `payment_mode`, `source_channel`, " +
                        "`part_amount_1`, `part_amount_2`, `payment_status`, `order_status`, " +
                        "`status_version`, `created_by`, `created_by_user_id`, `created_at`, `paid_at`, " +
                        "`last_reset_date`, `is_synced`, `updated_at`, `is_deleted`, `server_id`, " +
                        "`server_updated_at`, `cancel_reason`, `public_token`, `owner_user_id`, " +
                        "`owner_restaurant_id`, `sync_status`, `sync_failure_reason`, `sync_failed_at`, " +
                        "`terminal_series`, `financial_year`, `invoice_series`, `invoice_sequence`, " +
                        "`invoice_number`, `refund_amount`, `current_owner_terminal_id`, `version`, " +
                        "`lock_status`, `operation_id`, `record_origin`, `record_scope`, " +
                        "`payment_attempt_status`, `payment_attempt_started_at`) " +
                        "SELECT " + billCols.joinToString(", ") + " FROM `bills`"
                )
                db.execSQL("DROP TABLE `bills`")
                db.execSQL("ALTER TABLE `bills_new` RENAME TO `bills`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_created_by` ON `bills` (`created_by`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_order_status` ON `bills` (`order_status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_created_at` ON `bills` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_daily_order_id` ON `bills` (`daily_order_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bills_restaurant_public_token` ON `bills` (`restaurant_id`, `public_token`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_restaurant_id_terminal_id_created_at` ON `bills` (`restaurant_id`, `terminal_id`, `created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_restaurant_id_financial_year_invoice_series_invoice_sequence` ON `bills` (`restaurant_id`, `financial_year`, `invoice_series`, `invoice_sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_record_origin` ON `bills` (`record_origin`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_record_scope` ON `bills` (`record_scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_terminal_origin_scope` ON `bills` (`created_terminal_id`, `record_origin`, `record_scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_payment_attempt_status` ON `bills` (`payment_attempt_status`)")
                // ── bill_items ─────────────────────────────────────────
                fun iCol(name: String, fallback: String): String =
                    if (db.hasColumn("bill_items", name)) "`$name`" else "$fallback AS `$name`"
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bill_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bill_id` INTEGER NOT NULL,
                        `menu_item_id` INTEGER,
                        `item_name` TEXT NOT NULL,
                        `variant_id` INTEGER,
                        `variant_name` TEXT,
                        `price` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `item_total` TEXT NOT NULL,
                        `special_instruction` TEXT,
                        `sent_to_kot` INTEGER NOT NULL DEFAULT 0,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `server_id` INTEGER,
                        `server_bill_id` INTEGER,
                        `server_menu_item_id` INTEGER,
                        `server_variant_id` INTEGER,
                        `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`menu_item_id`) REFERENCES `menu_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`variant_id`) REFERENCES `item_variants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `bill_items_new` (`id`, `bill_id`, `menu_item_id`, `item_name`, `variant_id`, " +
                        "`variant_name`, `price`, `quantity`, `item_total`, `special_instruction`, `sent_to_kot`, " +
                        "`restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted`, `server_id`, " +
                        "`server_bill_id`, `server_menu_item_id`, `server_variant_id`, `server_updated_at`) " +
                        "SELECT `id`, `bill_id`, `menu_item_id`, `item_name`, `variant_id`, `variant_name`, " +
                        "`price`, `quantity`, `item_total`, `special_instruction`, " +
                        iCol("sent_to_kot", "0") + ", `restaurant_id`, `device_id`, `is_synced`, `updated_at`, " +
                        "`is_deleted`, " + iCol("server_id", "NULL") + ", " + iCol("server_bill_id", "NULL") + ", " +
                        iCol("server_menu_item_id", "NULL") + ", " + iCol("server_variant_id", "NULL") + ", " +
                        iCol("server_updated_at", "0") + " FROM `bill_items`"
                )
                db.execSQL("DROP TABLE `bill_items`")
                db.execSQL("ALTER TABLE `bill_items_new` RENAME TO `bill_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_bill_id` ON `bill_items` (`bill_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_menu_item_id` ON `bill_items` (`menu_item_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_items_variant_id` ON `bill_items` (`variant_id`)")

                // ── bill_payments ─────────────────────────────────────────
                // v18 stored `amount` as REAL; SQLite cannot ALTER COLUMN TYPE,
                // so every device that migrated from ≤v18 still has REAL affinity
                // at v78. Additionally server_bill_id, terminal_id,
                // bill_public_token, operation_id, sync_status, created_at,
                // gateway_txn_id, gateway_status, verified_by and version were
                // never back-filled for early-start migration chains. Rebuild to
                // exact 79.json DDL so column affinity and presence both match.
                fun bpCol(name: String, fallback: String): String =
                    if (db.hasColumn("bill_payments", name)) "`$name`" else "$fallback AS `$name`"
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bill_payments_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bill_id` INTEGER NOT NULL,
                        `payment_mode` TEXT NOT NULL,
                        `amount` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `terminal_id` TEXT DEFAULT NULL,
                        `bill_public_token` TEXT DEFAULT NULL,
                        `operation_id` TEXT DEFAULT NULL,
                        `sync_status` TEXT NOT NULL DEFAULT 'pending',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `server_id` INTEGER,
                        `server_bill_id` INTEGER,
                        `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                        `gateway_txn_id` TEXT,
                        `gateway_status` TEXT,
                        `verified_by` TEXT NOT NULL DEFAULT 'manual',
                        `version` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                val bpCols = listOf(
                    "`id`", "`bill_id`", "`payment_mode`", "`amount`",
                    bpCol("created_at", "0"), "`restaurant_id`", "`device_id`",
                    bpCol("terminal_id", "NULL"),
                    bpCol("bill_public_token", "NULL"),
                    bpCol("operation_id", "NULL"),
                    bpCol("sync_status", "'pending'"),
                    "`is_synced`", "`updated_at`", "`is_deleted`",
                    bpCol("server_id", "NULL"),
                    bpCol("server_bill_id", "NULL"),
                    bpCol("server_updated_at", "0"),
                    bpCol("gateway_txn_id", "NULL"),
                    bpCol("gateway_status", "NULL"),
                    bpCol("verified_by", "'manual'"),
                    bpCol("version", "0")
                )
                db.execSQL(
                    "INSERT INTO `bill_payments_new` (`id`, `bill_id`, `payment_mode`, `amount`, " +
                        "`created_at`, `restaurant_id`, `device_id`, `terminal_id`, " +
                        "`bill_public_token`, `operation_id`, `sync_status`, `is_synced`, " +
                        "`updated_at`, `is_deleted`, `server_id`, `server_bill_id`, " +
                        "`server_updated_at`, `gateway_txn_id`, `gateway_status`, " +
                        "`verified_by`, `version`) " +
                        "SELECT " + bpCols.joinToString(", ") + " FROM `bill_payments`"
                )
                db.execSQL("DROP TABLE `bill_payments`")
                db.execSQL("ALTER TABLE `bill_payments_new` RENAME TO `bill_payments`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_payments_bill_id` ON `bill_payments` (`bill_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `idx_bill_payments_restaurant_operation` ON `bill_payments` (`restaurant_id`, `operation_id`)")

                // ── restaurant_profile ────────────────────────────────────
                // v18 lacks collect_customer_number, easebuzz_enabled,
                // kitchen_printer_*, order_payment_flow_mode, timezone,
                // show_branding, mask_customer_phone, server_id,
                // server_updated_at, changed_fields and others. The migration
                // chain that should have added them is unreliable for early-start
                // devices. Rebuild to exact 79.json DDL so every column is present.
                fun rpCol(name: String, fallback: String): String =
                    if (db.hasColumn("restaurant_profile", name)) "`$name`" else "$fallback AS `$name`"
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `restaurant_profile_new` (
                        `id` INTEGER NOT NULL,
                        `shop_name` TEXT,
                        `shop_address` TEXT,
                        `whatsapp_number` TEXT,
                        `email` TEXT,
                        `logo_path` TEXT,
                        `logo_url` TEXT,
                        `logo_version` INTEGER NOT NULL DEFAULT 0,
                        `fssai_number` TEXT,
                        `fssai_expiry_date` TEXT,
                        `email_invoice_consent` INTEGER NOT NULL DEFAULT 0,
                        `country` TEXT DEFAULT 'India',
                        `gst_enabled` INTEGER NOT NULL DEFAULT 0,
                        `gstin` TEXT,
                        `is_tax_inclusive` INTEGER NOT NULL DEFAULT 0,
                        `gst_percentage` REAL NOT NULL DEFAULT 0.0,
                        `custom_tax_name` TEXT,
                        `custom_tax_number` TEXT,
                        `custom_tax_percentage` REAL NOT NULL DEFAULT 0.0,
                        `currency` TEXT DEFAULT 'INR',
                        `upi_enabled` INTEGER NOT NULL DEFAULT 0,
                        `upi_qr_path` TEXT,
                        `upi_qr_url` TEXT,
                        `upi_qr_version` INTEGER NOT NULL DEFAULT 0,
                        `upi_handle` TEXT,
                        `upi_mobile` TEXT,
                        `cash_enabled` INTEGER NOT NULL DEFAULT 1,
                        `pos_enabled` INTEGER NOT NULL DEFAULT 0,
                        `easebuzz_enabled` INTEGER NOT NULL DEFAULT 0,
                        `printer_enabled` INTEGER NOT NULL DEFAULT 0,
                        `printer_name` TEXT,
                        `printer_mac` TEXT,
                        `paper_size` TEXT NOT NULL DEFAULT '58mm',
                        `auto_print_on_success` INTEGER NOT NULL DEFAULT 0,
                        `include_logo_in_print` INTEGER NOT NULL DEFAULT 1,
                        `print_customer_whatsapp` INTEGER NOT NULL DEFAULT 1,
                        `kitchen_printer_enabled` INTEGER NOT NULL DEFAULT 0,
                        `kitchen_printer_name` TEXT,
                        `kitchen_printer_mac` TEXT,
                        `kitchen_printer_paper_size` TEXT NOT NULL DEFAULT '58mm',
                        `daily_order_counter` INTEGER NOT NULL DEFAULT 0,
                        `lifetime_order_counter` INTEGER NOT NULL DEFAULT 0,
                        `last_reset_date` TEXT,
                        `session_timeout_minutes` INTEGER NOT NULL DEFAULT 30,
                        `order_payment_flow_mode` TEXT NOT NULL DEFAULT 'pay_before_food',
                        `collect_customer_number` INTEGER NOT NULL DEFAULT 1,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `timezone` TEXT DEFAULT 'Asia/Kolkata',
                        `review_url` TEXT,
                        `invoice_footer` TEXT,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `show_branding` INTEGER NOT NULL DEFAULT 1,
                        `mask_customer_phone` INTEGER NOT NULL DEFAULT 1,
                        `server_id` INTEGER,
                        `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                        `changed_fields` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                val rpCols = listOf(
                    "`id`",
                    rpCol("shop_name", "NULL"),
                    rpCol("shop_address", "NULL"),
                    rpCol("whatsapp_number", "NULL"),
                    rpCol("email", "NULL"),
                    rpCol("logo_path", "NULL"),
                    rpCol("logo_url", "NULL"),
                    rpCol("logo_version", "0"),
                    rpCol("fssai_number", "NULL"),
                    rpCol("fssai_expiry_date", "NULL"),
                    rpCol("email_invoice_consent", "0"),
                    rpCol("country", "'India'"),
                    rpCol("gst_enabled", "0"),
                    rpCol("gstin", "NULL"),
                    rpCol("is_tax_inclusive", "0"),
                    rpCol("gst_percentage", "0.0"),
                    rpCol("custom_tax_name", "NULL"),
                    rpCol("custom_tax_number", "NULL"),
                    rpCol("custom_tax_percentage", "0.0"),
                    rpCol("currency", "'INR'"),
                    rpCol("upi_enabled", "0"),
                    rpCol("upi_qr_path", "NULL"),
                    rpCol("upi_qr_url", "NULL"),
                    rpCol("upi_qr_version", "0"),
                    rpCol("upi_handle", "NULL"),
                    rpCol("upi_mobile", "NULL"),
                    rpCol("cash_enabled", "1"),
                    rpCol("pos_enabled", "0"),
                    rpCol("easebuzz_enabled", "0"),
                    rpCol("printer_enabled", "0"),
                    rpCol("printer_name", "NULL"),
                    rpCol("printer_mac", "NULL"),
                    rpCol("paper_size", "'58mm'"),
                    rpCol("auto_print_on_success", "0"),
                    rpCol("include_logo_in_print", "1"),
                    rpCol("print_customer_whatsapp", "1"),
                    rpCol("kitchen_printer_enabled", "0"),
                    rpCol("kitchen_printer_name", "NULL"),
                    rpCol("kitchen_printer_mac", "NULL"),
                    rpCol("kitchen_printer_paper_size", "'58mm'"),
                    rpCol("daily_order_counter", "0"),
                    rpCol("lifetime_order_counter", "0"),
                    rpCol("last_reset_date", "NULL"),
                    rpCol("session_timeout_minutes", "30"),
                    rpCol("order_payment_flow_mode", "'pay_before_food'"),
                    rpCol("collect_customer_number", "1"),
                    rpCol("restaurant_id", "0"),
                    rpCol("device_id", "''"),
                    rpCol("is_synced", "0"),
                    rpCol("updated_at", "0"),
                    rpCol("timezone", "'Asia/Kolkata'"),
                    rpCol("review_url", "NULL"),
                    rpCol("invoice_footer", "NULL"),
                    rpCol("is_deleted", "0"),
                    rpCol("show_branding", "1"),
                    rpCol("mask_customer_phone", "1"),
                    rpCol("server_id", "NULL"),
                    rpCol("server_updated_at", "0"),
                    rpCol("changed_fields", "NULL")
                )
                db.execSQL(
                    "INSERT INTO `restaurant_profile_new` (`id`, `shop_name`, `shop_address`, " +
                        "`whatsapp_number`, `email`, `logo_path`, `logo_url`, `logo_version`, " +
                        "`fssai_number`, `fssai_expiry_date`, `email_invoice_consent`, `country`, " +
                        "`gst_enabled`, `gstin`, `is_tax_inclusive`, `gst_percentage`, " +
                        "`custom_tax_name`, `custom_tax_number`, `custom_tax_percentage`, " +
                        "`currency`, `upi_enabled`, `upi_qr_path`, `upi_qr_url`, " +
                        "`upi_qr_version`, `upi_handle`, `upi_mobile`, `cash_enabled`, " +
                        "`pos_enabled`, `easebuzz_enabled`, `printer_enabled`, `printer_name`, " +
                        "`printer_mac`, `paper_size`, `auto_print_on_success`, " +
                        "`include_logo_in_print`, `print_customer_whatsapp`, " +
                        "`kitchen_printer_enabled`, `kitchen_printer_name`, " +
                        "`kitchen_printer_mac`, `kitchen_printer_paper_size`, " +
                        "`daily_order_counter`, `lifetime_order_counter`, `last_reset_date`, " +
                        "`session_timeout_minutes`, `order_payment_flow_mode`, " +
                        "`collect_customer_number`, `restaurant_id`, `device_id`, " +
                        "`is_synced`, `updated_at`, `timezone`, `review_url`, `invoice_footer`, " +
                        "`is_deleted`, `show_branding`, `mask_customer_phone`, `server_id`, " +
                        "`server_updated_at`, `changed_fields`) " +
                        "SELECT " + rpCols.joinToString(", ") + " FROM `restaurant_profile`"
                )
                db.execSQL("DROP TABLE `restaurant_profile`")
                db.execSQL("ALTER TABLE `restaurant_profile_new` RENAME TO `restaurant_profile`")

                android.util.Log.i("AppDatabase", "MIGRATION_78_79 complete: users+bills+bill_items+bill_payments+restaurant_profile rebuilt to current spec")
            }
        }

        val MIGRATION_71_72 = object : Migration(71, 72) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Role model collapse (server V94): legacy staff roles become
                // SHOP_STAFF on-device too, matching what the next master sync
                // pushes down. Access during the pre-sync window is still safe —
                // SessionManager treats these as staff roles defensively.
                db.execSQL(
                    "UPDATE `users` SET `role` = 'SHOP_STAFF' WHERE `role` IN ('SHOP_ADMIN', 'WAITER', 'CASHIER', 'MANAGER', 'OPERATIONS')"
                )
                android.util.Log.i("AppDatabase", "MIGRATION_71_72 complete: legacy staff roles collapsed to SHOP_STAFF")
            }
        }

        val MIGRATION_68_69 = object : Migration(68, 69) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop marketplace columns removed from RestaurantProfileEntity in commit eea83ce8
                if (db.hasColumn("restaurant_profile", "zomato_enabled")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` DROP COLUMN `zomato_enabled`")
                }
                if (db.hasColumn("restaurant_profile", "swiggy_enabled")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` DROP COLUMN `swiggy_enabled`")
                }
                if (db.hasColumn("restaurant_profile", "own_website_enabled")) {
                    db.execSQL("ALTER TABLE `restaurant_profile` DROP COLUMN `own_website_enabled`")
                }
            }
        }

        val MIGRATION_67_68 = object : Migration(67, 68) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix stock_logs delta/reason nullability mismatch (delta String?/reason String? with defaults).
                // Older DBs have NOT NULL with no default; new entity expects notNull=false.
                // Recreate table with correct schema to avoid IllegalStateException on upgrade.
                val hasStockLogs = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stock_logs'").use { it.count > 0 }
                if (!hasStockLogs) return
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `stock_logs_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `menu_item_id` INTEGER NOT NULL,
                        `variant_id` INTEGER,
                        `delta` TEXT DEFAULT '0',
                        `reason` TEXT DEFAULT '',
                        `created_at` INTEGER NOT NULL,
                        `restaurant_id` INTEGER NOT NULL DEFAULT 0,
                        `device_id` TEXT NOT NULL DEFAULT '',
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0,
                        `is_deleted` INTEGER NOT NULL DEFAULT 0,
                        `server_id` INTEGER DEFAULT NULL,
                        `server_menu_item_id` INTEGER DEFAULT NULL,
                        `server_variant_id` INTEGER DEFAULT NULL,
                        `server_updated_at` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`menu_item_id`) REFERENCES `menu_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `stock_logs_new` (
                        `id`, `menu_item_id`, `variant_id`, `delta`, `reason`, `created_at`,
                        `restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted`,
                        `server_id`, `server_menu_item_id`, `server_variant_id`, `server_updated_at`
                    )
                    SELECT
                        `id`, `menu_item_id`, `variant_id`,
                        COALESCE(`delta`, '0'), COALESCE(`reason`, ''),
                        `created_at`, `restaurant_id`, `device_id`, `is_synced`, `updated_at`, `is_deleted`,
                        `server_id`, `server_menu_item_id`, `server_variant_id`, `server_updated_at`
                    FROM `stock_logs`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `stock_logs`")
                db.execSQL("ALTER TABLE `stock_logs_new` RENAME TO `stock_logs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_logs_menu_item_id` ON `stock_logs` (`menu_item_id`)")
            }
        }

        val MIGRATION_63_64 = object : Migration(63, 64) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `server_id` INTEGER NOT NULL,
                        `notification_type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT,
                        `reference_id` TEXT,
                        `reference_type` TEXT,
                        `amount` TEXT,
                        `is_read` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notifications_created_at` ON `notifications` (`created_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notifications_read` ON `notifications` (`is_read`)"
                )
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