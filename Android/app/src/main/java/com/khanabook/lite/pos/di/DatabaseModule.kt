package com.khanabook.lite.pos.di

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.CategoryRepository
import com.khanabook.lite.pos.data.repository.InventoryRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.MenuRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager
import com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.util.KeystoreBackedPreferences
import com.khanabook.lite.pos.domain.util.LegacyEncryptedPrefsMigration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

private const val TAG = "DatabaseModule"
private const val SECURE_DB_PREFS = "secure_db_prefs"
private const val DB_KEY_PREF = "db_key"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private fun getSecureDbPrefs(context: Context): KeystoreBackedPreferences =
        KeystoreBackedPreferences(context, SECURE_DB_PREFS)

    internal fun getOrCreateDbPassphrase(context: Context): ByteArray {
        try {
            val sharedPrefs = getSecureDbPrefs(context)
            migrateLegacyDbKeyIfNeeded(context, sharedPrefs)

            var dbKey = sharedPrefs.getString(DB_KEY_PREF, null)
            if (dbKey == null) {
                val bytes = ByteArray(32)
                java.security.SecureRandom().nextBytes(bytes)
                dbKey = Base64.encodeToString(bytes, Base64.NO_WRAP)
                sharedPrefs.putString(DB_KEY_PREF, dbKey)
            }

            return Base64.decode(dbKey, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secure DB key storage.", e)
            throw e
        }
    }

    private fun migrateLegacyDbKeyIfNeeded(context: Context, securePrefs: KeystoreBackedPreferences) {
        if (securePrefs.contains(DB_KEY_PREF)) return
        val legacyPrefs = LegacyEncryptedPrefsMigration.open(context, SECURE_DB_PREFS) ?: return
        val legacyDbKey = legacyPrefs.getString(DB_KEY_PREF, null) ?: return
        securePrefs.putString(DB_KEY_PREF, legacyDbKey)
    }

    private fun shouldRecoverFromDbOpenFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message?.lowercase().orEmpty()
            if (
                "file is not a database" in message ||
                "sqlite_master" in message ||
                ("not an error" in message && "cipher" in message)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun quarantineDatabaseFiles(context: Context): List<String> {
        val suffix = ".corrupt-${System.currentTimeMillis()}"
        val paths = listOf(
            context.getDatabasePath(AppDatabase.DATABASE_NAME),
            context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal"),
            context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm"),
            context.getDatabasePath("${AppDatabase.DATABASE_NAME}-journal")
        )

        return paths.mapNotNull { source ->
            if (!source.exists()) return@mapNotNull null
            val target = java.io.File(source.absolutePath + suffix)
            if (source.renameTo(target)) target.absolutePath else null
        }
    }

    private fun buildDatabase(context: Context, passphrase: ByteArray): AppDatabase {
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
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
                AppDatabase.MIGRATION_56_57
            )
            .build()
    }

    @Provides
    fun provideDatabase(databaseProvider: DatabaseProvider): AppDatabase {
        return databaseProvider.getDatabase()
    }

    @Provides @Singleton fun provideUserDao(databaseProvider: DatabaseProvider): UserDao = TenantUserDao(databaseProvider)
    @Provides @Singleton fun provideRestaurantDao(databaseProvider: DatabaseProvider): RestaurantDao = TenantRestaurantDao(databaseProvider)
    @Provides @Singleton fun provideCategoryDao(databaseProvider: DatabaseProvider): CategoryDao = TenantCategoryDao(databaseProvider)
    @Provides @Singleton fun provideMenuDao(databaseProvider: DatabaseProvider): MenuDao = TenantMenuDao(databaseProvider)
    @Provides @Singleton fun providePrinterProfileDao(databaseProvider: DatabaseProvider): PrinterProfileDao = TenantPrinterProfileDao(databaseProvider)
    @Provides @Singleton fun provideKitchenPrintQueueDao(databaseProvider: DatabaseProvider): KitchenPrintQueueDao = TenantKitchenPrintQueueDao(databaseProvider)
    @Provides @Singleton fun provideBillDao(databaseProvider: DatabaseProvider): BillDao = TenantBillDao(databaseProvider)
    @Provides @Singleton fun provideInventoryDao(databaseProvider: DatabaseProvider): InventoryDao = TenantInventoryDao(databaseProvider)
    @Provides @Singleton fun provideKotEventDao(databaseProvider: DatabaseProvider): KotEventDao = TenantKotEventDao(databaseProvider)

    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager,
        api: KhanaBookApi
    ) = UserRepository(userDao, sessionManager, workManager, api)

    @Provides
    @Singleton
    fun provideRestaurantRepository(
        restaurantDao: RestaurantDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager,
        api: KhanaBookApi
    ) = RestaurantRepository(restaurantDao, sessionManager, workManager, api)

    @Provides
    @Singleton
    fun provideCategoryRepository(
        categoryDao: CategoryDao,
        menuDao: MenuDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager
    ) = CategoryRepository(categoryDao, menuDao, sessionManager, workManager)

    @Provides
    @Singleton
    fun provideMenuRepository(
        menuDao: MenuDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager
    ) = MenuRepository(menuDao, sessionManager, workManager)

    @Provides
    @Singleton
    fun providePrinterProfileRepository(
        printerProfileDao: PrinterProfileDao,
        sessionManager: SessionManager
    ) = PrinterProfileRepository(printerProfileDao, sessionManager)

    @Provides
    @Singleton
    fun provideKitchenPrintQueueRepository(
        kitchenPrintQueueDao: KitchenPrintQueueDao,
        sessionManager: SessionManager
    ) = KitchenPrintQueueRepository(kitchenPrintQueueDao, sessionManager)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager =
        androidx.work.WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideBillRepository(
        billDao: BillDao,
        restaurantDao: RestaurantDao,
        inventoryConsumptionManager: InventoryConsumptionManager,
        workManager: androidx.work.WorkManager,
        kitchenPrintQueueRepository: KitchenPrintQueueRepository,
        kotEventDao: KotEventDao,
        sessionManager: SessionManager
    ) = BillRepository(
        billDao,
        restaurantDao,
        inventoryConsumptionManager,
        workManager,
        kitchenPrintQueueRepository,
        kotEventDao,
        sessionManager
    )

    @Provides
    @Singleton
    fun provideInventoryRepository(
        inventoryDao: InventoryDao,
        menuDao: MenuDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager
    ) = InventoryRepository(inventoryDao, menuDao, sessionManager, workManager)

    @Provides
    @Singleton
    fun provideInventoryConsumptionManager(
        menuRepository: MenuRepository,
        inventoryRepository: InventoryRepository
    ) = InventoryConsumptionManager(menuRepository, inventoryRepository)


    @Provides
    @Singleton
    fun provideBluetoothPrinterManager(@ApplicationContext context: Context) =
        BluetoothPrinterManager(context)
}
