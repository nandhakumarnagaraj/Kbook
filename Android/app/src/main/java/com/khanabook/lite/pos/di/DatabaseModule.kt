package com.khanabook.lite.pos.di

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.CategoryDao
import com.khanabook.lite.pos.data.local.dao.InventoryDao
import com.khanabook.lite.pos.data.local.dao.KitchenPrintQueueDao
import com.khanabook.lite.pos.data.local.dao.MenuDao
import com.khanabook.lite.pos.data.local.dao.PrinterProfileDao
import com.khanabook.lite.pos.data.local.dao.RestaurantDao
import com.khanabook.lite.pos.data.local.dao.UserDao
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
import net.sqlcipher.database.SupportFactory

private const val TAG = "DatabaseModule"
private const val SECURE_DB_PREFS = "secure_db_prefs"
private const val DB_KEY_PREF = "db_key"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private fun getSecureDbPrefs(context: Context): KeystoreBackedPreferences =
        KeystoreBackedPreferences(context, SECURE_DB_PREFS)

    private fun getOrCreateDbPassphrase(context: Context): ByteArray {
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

    private fun clearDbPassphrase(context: Context) {
        runCatching {
            getSecureDbPrefs(context).remove(DB_KEY_PREF)
        }.onFailure {
            Log.e(TAG, "Failed to clear stored DB passphrase during recovery.", it)
        }
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

    private fun deleteDatabaseFiles(context: Context) {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        context.getDatabasePath("${AppDatabase.DATABASE_NAME}-wal").delete()
        context.getDatabasePath("${AppDatabase.DATABASE_NAME}-shm").delete()
        context.getDatabasePath("${AppDatabase.DATABASE_NAME}-journal").delete()
    }

    private fun buildDatabase(context: Context, passphrase: ByteArray): AppDatabase {
        val factory = SupportFactory(passphrase)

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
                AppDatabase.MIGRATION_35_36
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return try {
            val database = buildDatabase(context, getOrCreateDbPassphrase(context))
            database.openHelper.writableDatabase
            database
        } catch (e: Exception) {
            if (!shouldRecoverFromDbOpenFailure(e)) {
                throw e
            }

            Log.w(TAG, "Detected unusable encrypted database. Resetting local DB and passphrase.", e)
            runCatching { deleteDatabaseFiles(context) }
                .onFailure { Log.e(TAG, "Failed to delete local DB files during recovery.", it) }
            clearDbPassphrase(context)

            val recoveredDb = buildDatabase(context, getOrCreateDbPassphrase(context))
            recoveredDb.openHelper.writableDatabase
            recoveredDb
        }
    }

    @Provides fun provideUserDao(database: AppDatabase) = database.userDao()
    @Provides fun provideRestaurantDao(database: AppDatabase) = database.restaurantDao()
    @Provides fun provideCategoryDao(database: AppDatabase) = database.categoryDao()
    @Provides fun provideMenuDao(database: AppDatabase) = database.menuDao()
    @Provides fun providePrinterProfileDao(database: AppDatabase) = database.printerProfileDao()
    @Provides fun provideKitchenPrintQueueDao(database: AppDatabase) = database.kitchenPrintQueueDao()
    @Provides fun provideBillDao(database: AppDatabase) = database.billDao()
    @Provides fun provideInventoryDao(database: AppDatabase) = database.inventoryDao()

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
        printerProfileDao: PrinterProfileDao
    ) = PrinterProfileRepository(printerProfileDao)

    @Provides
    @Singleton
    fun provideKitchenPrintQueueRepository(
        kitchenPrintQueueDao: KitchenPrintQueueDao
    ) = KitchenPrintQueueRepository(kitchenPrintQueueDao)

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
        kitchenPrintQueueRepository: KitchenPrintQueueRepository
    ) = BillRepository(
        billDao,
        restaurantDao,
        inventoryConsumptionManager,
        workManager,
        kitchenPrintQueueRepository
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
