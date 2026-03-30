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
                        BillEntity::class,
                        BillItemEntity::class,
                        BillPaymentEntity::class,
                        StockLogEntity::class
                ],
        version = 31,
        exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun categoryDao(): CategoryDao
    abstract fun menuDao(): MenuDao
    abstract fun billDao(): BillDao
    abstract fun inventoryDao(): InventoryDao

	    companion object {
	        const val DATABASE_NAME = "khanabook_lite_db"

            val MIGRATION_30_31 = object : Migration(30, 31) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `login_id` TEXT")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `google_email` TEXT")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE `users` ADD COLUMN `auth_provider` TEXT NOT NULL DEFAULT 'PHONE'")
                    } catch (_: Exception) {
                    }
                    try {
                        db.execSQL("ALTER TABLE `menu_items` ADD COLUMN `barcode` TEXT")
                    } catch (_: Exception) {
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
                    } catch (e: Exception) {
                        // Already exists or table missing
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
                    } catch (e: Exception) {
                        // Already exists or table missing
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
                } catch (e: Exception) {
                    
                }

                try {
                    db.execSQL("ALTER TABLE `item_variants` ADD COLUMN `current_stock` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `item_variants` ADD COLUMN `low_stock_threshold` REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    
                }
            }
        }
        
        
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                
                try {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `country` TEXT DEFAULT 'India'")
                } catch (e: Exception) {
                    db.execSQL("UPDATE `restaurant_profile` SET `country` = 'India' WHERE `country` IS NULL")
                }

                try {
                    db.execSQL("ALTER TABLE `restaurant_profile` ADD COLUMN `currency` TEXT DEFAULT 'INR'")
                } catch (e: Exception) {
                    db.execSQL("UPDATE `restaurant_profile` SET `currency` = 'INR' WHERE `currency` IS NULL")
                }
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `users` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'owner'")
                } catch (e: Exception) {
                    // Ignore if columns exist
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
