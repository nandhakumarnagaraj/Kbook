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
                        StockLogEntity::class
                ],
        version = 39,
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

	    companion object {
	        const val DATABASE_NAME = "khanabook_lite_db"

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
    }
}
