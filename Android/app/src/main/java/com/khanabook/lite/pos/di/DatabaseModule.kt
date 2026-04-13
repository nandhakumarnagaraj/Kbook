package com.khanabook.lite.pos.di

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.repository.*
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

        private fun getSecureDbPrefs(context: Context): android.content.SharedPreferences {
                val mainKey = androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()

                return androidx.security.crypto.EncryptedSharedPreferences.create(
                    context,
                    SECURE_DB_PREFS,
                    mainKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
        }

        private fun getOrCreateDbPassphrase(context: Context): ByteArray {
                try {
                    val sharedPrefs = getSecureDbPrefs(context)

                    var dbKey = sharedPrefs.getString(DB_KEY_PREF, null)
                    if (dbKey == null) {
                        val secureRandom = java.security.SecureRandom()
                        val bytes = ByteArray(32)
                        secureRandom.nextBytes(bytes)
                        dbKey = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        sharedPrefs.edit().putString(DB_KEY_PREF, dbKey).apply()
                    }

                    return Base64.decode(dbKey, Base64.NO_WRAP)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize EncryptedSharedPreferences. Database cannot be opened safely.", e)
                    throw e
                }
        }

        private fun clearDbPassphrase(context: Context) {
                runCatching {
                        getSecureDbPrefs(context).edit().remove(DB_KEY_PREF).apply()
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
                            "not an error" in message && "cipher" in message
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
                sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
                workManager: androidx.work.WorkManager,
                api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi
        ) = UserRepository(userDao, sessionManager, workManager, api)

        @Provides
        @Singleton
        fun provideRestaurantRepository(
                restaurantDao: RestaurantDao,
                sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
                workManager: androidx.work.WorkManager,
                api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi
        ) = RestaurantRepository(restaurantDao, sessionManager, workManager, api)

        @Provides
        @Singleton
        fun provideCategoryRepository(
                categoryDao: CategoryDao,
                menuDao: MenuDao,
                sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
                workManager: androidx.work.WorkManager
        ) = CategoryRepository(categoryDao, menuDao, sessionManager, workManager)

        @Provides
        @Singleton
        fun provideMenuRepository(
                menuDao: MenuDao,
                sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
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
                inventoryConsumptionManager:
                        com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager,
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
                sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
                workManager: androidx.work.WorkManager
        ) = InventoryRepository(inventoryDao, menuDao, sessionManager, workManager)

        @Provides
        @Singleton
        fun provideInventoryConsumptionManager(
                menuRepository: MenuRepository,
                inventoryRepository: InventoryRepository
        ) =
                com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager(
                        menuRepository,
                        inventoryRepository
                )

        @Provides
        @Singleton
        fun provideBluetoothPrinterManager(@ApplicationContext context: Context) =
                com.khanabook.lite.pos.domain.manager.BluetoothPrinterManager(context)
}
