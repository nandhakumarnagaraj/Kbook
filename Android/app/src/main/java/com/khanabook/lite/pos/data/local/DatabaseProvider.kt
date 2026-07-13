package com.khanabook.lite.pos.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.di.DatabaseModule
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager
) {
    private val tag = "DatabaseProvider"
    private var activeDatabase: AppDatabase? = null
    private var activeRestaurantId: Long = -1L

    private val databaseState = MutableStateFlow<AppDatabase?>(null)

    val activeDatabaseFlow: Flow<AppDatabase> = databaseState
        .filterNotNull()
        .distinctUntilChanged()

    @Synchronized
    fun getDatabase(): AppDatabase {
        val restaurantId = sessionManager.getRestaurantId()
        if (activeDatabase == null || activeRestaurantId != restaurantId) {
            activeDatabase?.close()
            activeRestaurantId = restaurantId
            val dbName = if (restaurantId > 0) {
                "khanabook_lite_db_$restaurantId"
            } else {
                "khanabook_lite_db"
            }

            // Check if legacy data needs migration
            if (restaurantId > 0) {
                migrateLegacyDataIfNecessary(restaurantId)
            }

            try {
                Log.i(
                    tag,
                    "Opening database expectedRoomVersion=58 appVersion=${BuildConfig.VERSION_NAME} " +
                        "restaurant=${maskRestaurantId(restaurantId)} terminal=${sessionManager.getTerminalId() ?: "none"} " +
                        "device=${sessionManager.getDeviceId().takeLast(6)} db=$dbName"
                )
                activeDatabase = buildDatabaseWithName(context, dbName)
                databaseState.value = activeDatabase
                Log.i(tag, "Opened database instance: $dbName")
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Database open failed expectedRoomVersion=58 appVersion=${BuildConfig.VERSION_NAME} " +
                        "restaurant=${maskRestaurantId(restaurantId)} terminal=${sessionManager.getTerminalId() ?: "none"} " +
                        "device=${sessionManager.getDeviceId().takeLast(6)} db=$dbName",
                    e
                )
                throw e
            }
        }
        return activeDatabase!!
    }

    fun warmUpDatabase() {
        try {
            val database = getDatabase()
            database.openHelper.writableDatabase
            Log.i(tag, "Database warm-up completed expectedRoomVersion=58")
        } catch (e: Exception) {
            Log.e(tag, "Database warm-up failed expectedRoomVersion=58", e)
            throw e
        }
    }

    @Synchronized
    fun closeDatabase() {
        activeDatabase?.close()
        activeDatabase = null
        databaseState.value = null
        activeRestaurantId = -1L
        Log.i(tag, "Closed active database instance")
    }

    private fun buildDatabaseWithName(context: Context, dbName: String): AppDatabase {
        val passphrase = DatabaseModule.getOrCreateDbPassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .openHelperFactory(factory)
            .addMigrations(
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
                AppDatabase.MIGRATION_37_38,
                AppDatabase.MIGRATION_38_39,
                AppDatabase.MIGRATION_39_40,
                AppDatabase.MIGRATION_40_41,
                AppDatabase.MIGRATION_41_42,
                AppDatabase.MIGRATION_42_43,
                AppDatabase.MIGRATION_43_44,
                AppDatabase.MIGRATION_44_45,
                AppDatabase.MIGRATION_45_46,
                AppDatabase.MIGRATION_46_47,
                AppDatabase.MIGRATION_47_48,
                AppDatabase.MIGRATION_48_49,
                AppDatabase.MIGRATION_49_50,
                AppDatabase.MIGRATION_50_51,
                AppDatabase.MIGRATION_51_52,
                AppDatabase.MIGRATION_52_53,
                AppDatabase.MIGRATION_53_54,
                AppDatabase.MIGRATION_54_55,
                AppDatabase.MIGRATION_55_56,
                AppDatabase.MIGRATION_56_57,
                AppDatabase.MIGRATION_57_58
            )
            .build()
    }

    private fun maskRestaurantId(restaurantId: Long): String {
        if (restaurantId <= 0L) return "none"
        val value = restaurantId.toString()
        return if (value.length <= 4) "****" else "****${value.takeLast(4)}"
    }

    private fun migrateLegacyDataIfNecessary(restaurantId: Long) {
        val legacyDbFile = context.getDatabasePath("khanabook_lite_db")
        if (!legacyDbFile.exists()) return

        val newDbFile = context.getDatabasePath("khanabook_lite_db_$restaurantId")
        if (newDbFile.exists()) return

        Log.i(tag, "Found legacy database and no restaurant database for $restaurantId. Starting data migration.")

        var legacyDb: AppDatabase? = null
        var newDb: AppDatabase? = null
        try {
            legacyDb = buildDatabaseWithName(context, "khanabook_lite_db")
            newDb = buildDatabaseWithName(context, "khanabook_lite_db_$restaurantId")

            newDb.runInTransaction {
                runBlocking {
                    // 1. Migrate Users
                    val users = legacyDb!!.userDao().getAllUsersOnce().filter { it.restaurantId == restaurantId }
                    if (users.isNotEmpty()) {
                        newDb!!.userDao().insertSyncedUsers(users)
                    }

                    // 2. Migrate RestaurantProfile.
                    // Old single-DB installs stored the profile with id=1 and restaurant_id=0
                    // (the entity defaults), so neither a restaurant_id filter nor
                    // getProfile(restaurantId) finds it. Look it up by restaurant_id first,
                    // then fall back to the legacy id=1 row, and rewrite both keys to the
                    // restaurant's server id so the per-restaurant DB queries can find it.
                    val legacyProfile = legacyDb!!.restaurantDao().getProfile(restaurantId)
                        ?: legacyDb!!.restaurantDao().getProfile()
                    if (legacyProfile != null) {
                        newDb!!.restaurantDao().saveProfile(
                            legacyProfile.copy(id = restaurantId, restaurantId = restaurantId)
                        )
                    }
                    // Preserve any unsynced profile rows (including legacy restaurant_id=0)
                    // so pending counter/setting changes still get pushed after migration.
                    val unsyncedProfiles = legacyDb!!.restaurantDao().getUnsyncedRestaurantProfiles()
                        .filter { it.restaurantId == restaurantId || it.restaurantId == 0L }
                    if (unsyncedProfiles.isNotEmpty()) {
                        newDb!!.restaurantDao().insertSyncedRestaurantProfiles(
                            unsyncedProfiles.map { it.copy(id = restaurantId, restaurantId = restaurantId) }
                        )
                    }

                    // 3. Migrate Categories
                    val categories = legacyDb!!.categoryDao().getAllCategoriesOnce(restaurantId)
                    if (categories.isNotEmpty()) {
                        newDb!!.categoryDao().insertSyncedCategories(categories)
                    }

                    // 4. Migrate MenuItems & Variants
                    val menuItems = legacyDb!!.menuDao().getAllMenuItemsOnce(restaurantId)
                    if (menuItems.isNotEmpty()) {
                        newDb!!.menuDao().insertSyncedMenuItems(menuItems)
                    }
                    val variants = legacyDb!!.menuDao().getAllVariantsOnce(restaurantId)
                    if (variants.isNotEmpty()) {
                        newDb!!.menuDao().insertSyncedItemVariants(variants)
                    }

                    // 5. Migrate StockLogs
                    val unsyncedStock = legacyDb!!.inventoryDao().getUnsyncedStockLogs(restaurantId)
                    if (unsyncedStock.isNotEmpty()) {
                        newDb!!.inventoryDao().insertSyncedStockLogs(unsyncedStock)
                    }

                    // 6. Migrate Bills, BillItems, BillPayments
                    val unsyncedBills = legacyDb!!.billDao().getUnsyncedBills(restaurantId)
                    if (unsyncedBills.isNotEmpty()) {
                        newDb!!.billDao().insertSyncedBills(unsyncedBills)
                    }
                    val unsyncedItems = legacyDb!!.billDao().getUnsyncedBillItems(restaurantId)
                    if (unsyncedItems.isNotEmpty()) {
                        newDb!!.billDao().insertSyncedBillItems(unsyncedItems)
                    }
                    val unsyncedPayments = legacyDb!!.billDao().getUnsyncedBillPayments(restaurantId)
                    if (unsyncedPayments.isNotEmpty()) {
                        newDb!!.billDao().insertSyncedBillPayments(unsyncedPayments)
                    }

                    // Migrate Printer Profiles
                    val printerProfiles = legacyDb!!.printerProfileDao().getAll(restaurantId)
                    if (printerProfiles.isNotEmpty()) {
                        printerProfiles.forEach { newDb!!.printerProfileDao().upsert(it) }
                    }
                }
            }
            Log.i(tag, "Data migration completed successfully for restaurant $restaurantId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to migrate legacy data to restaurant-specific database", e)
        } finally {
            legacyDb?.close()
            newDb?.close()
        }
    }
}
