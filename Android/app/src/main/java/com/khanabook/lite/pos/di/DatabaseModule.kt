package com.khanabook.lite.pos.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.inject.Singleton
import net.sqlcipher.database.SupportFactory

private const val TAG = "DatabaseModule"
private const val KEYSTORE_ALIAS = "KhanaBookDbKey"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

        private fun getOrCreateDbPassphrase(context: Context): ByteArray {
                try {
                    val mainKey = androidx.security.crypto.MasterKey.Builder(context)
                        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    val sharedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                        context,
                        "secure_db_prefs",
                        mainKey,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )

                    var dbKey = sharedPrefs.getString("db_key", null)
                    if (dbKey == null) {
                        val secureRandom = java.security.SecureRandom()
                        val bytes = ByteArray(32)
                        secureRandom.nextBytes(bytes)
                        dbKey = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        sharedPrefs.edit().putString("db_key", dbKey).apply()
                    }

                    return Base64.decode(dbKey, Base64.NO_WRAP)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize EncryptedSharedPreferences. Database cannot be opened safely.", e)
                    throw e
                }
        }

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
                val passphrase = getOrCreateDbPassphrase(context)
                val factory = SupportFactory(passphrase)

                val builder =
                        Room.databaseBuilder(
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
                                    AppDatabase.MIGRATION_30_31
                                )

                return builder.build()
        }

        @Provides fun provideUserDao(database: AppDatabase) = database.userDao()
        @Provides fun provideRestaurantDao(database: AppDatabase) = database.restaurantDao()
        @Provides fun provideCategoryDao(database: AppDatabase) = database.categoryDao()
        @Provides fun provideMenuDao(database: AppDatabase) = database.menuDao()
        @Provides fun provideBillDao(database: AppDatabase) = database.billDao()
        @Provides fun provideInventoryDao(database: AppDatabase) = database.inventoryDao()

        @Provides
        @Singleton
        fun provideUserRepository(
                userDao: UserDao,
                @ApplicationContext context: Context,
                sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
                workManager: androidx.work.WorkManager,
                api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi
        ) =
                UserRepository(
                        userDao,
                        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE),
                        sessionManager,
                        workManager,
                        api
                )

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
        fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager =
                androidx.work.WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideBillRepository(
                billDao: BillDao,
                restaurantDao: RestaurantDao,
                inventoryConsumptionManager:
                        com.khanabook.lite.pos.domain.manager.InventoryConsumptionManager,
                workManager: androidx.work.WorkManager
        ) = BillRepository(billDao, restaurantDao, inventoryConsumptionManager, workManager)

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
