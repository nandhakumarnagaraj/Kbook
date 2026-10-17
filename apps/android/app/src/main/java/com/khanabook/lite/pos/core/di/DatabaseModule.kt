package com.khanabook.lite.pos.core.di
import com.khanabook.lite.pos.feature.staff.data.*
import com.khanabook.lite.pos.feature.printing.data.*
import com.khanabook.lite.pos.core.database.TenantBillDao
import com.khanabook.lite.pos.core.database.TenantCategoryDao
import com.khanabook.lite.pos.core.database.TenantKitchenPrintQueueDao
import com.khanabook.lite.pos.core.database.TenantKotEventDao
import com.khanabook.lite.pos.core.database.TenantMenuDao
import com.khanabook.lite.pos.core.database.TenantNotificationDao
import com.khanabook.lite.pos.core.database.TenantPrinterProfileDao
import com.khanabook.lite.pos.core.database.TenantRestaurantDao
import com.khanabook.lite.pos.core.database.TenantUserDao
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueDao
import com.khanabook.lite.pos.feature.printing.data.KotEventDao

import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.billing.data.BillDao

import com.khanabook.lite.pos.feature.billing.data.*
import com.khanabook.lite.pos.feature.auth.data.*

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.core.database.AppDatabase
import com.khanabook.lite.pos.core.database.DatabaseProvider
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.notifications.data.NotificationDao
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueRepository
import com.khanabook.lite.pos.feature.notifications.data.NotificationRepository
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileRepository
import com.khanabook.lite.pos.feature.printing.domain.BluetoothPrinterManager
import com.khanabook.lite.pos.feature.printing.domain.KitchenPrintQueueManager
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.auth.data.UserRepository
import com.khanabook.lite.pos.feature.auth.domain.KeystoreBackedPreferences
import com.khanabook.lite.pos.feature.auth.domain.LegacyEncryptedPrefsMigration
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.menu.data.CategoryRepository
import com.khanabook.lite.pos.feature.menu.data.MenuRepository
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
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

    internal fun getOrCreateDbPassphrase(
        context: Context,
        allowKeyRegeneration: Boolean = true
    ): ByteArray {
        try {
            val sharedPrefs = getSecureDbPrefs(context)
            migrateLegacyDbKeyIfNeeded(context, sharedPrefs)

            var dbKey = sharedPrefs.getString(DB_KEY_PREF, null, strict = true)
            if (dbKey == null && sharedPrefs.contains(DB_KEY_PREF)) {
                // Ciphertext exists but the Keystore could not decrypt it (device
                // reset, OEM keystore wipe). Regenerating the key here would leave
                // every existing database permanently unreadable
                // ("file is not a database"). Fail loudly instead — callers that
                // pass allowKeyRegeneration = false also have a quarantine-and-
                // rebuild recovery path; minting a key silently has none.
                throw IllegalStateException(
                    "SQLCipher passphrase exists but could not be decrypted " +
                        "(Keystore key lost or invalidated)"
                )
            }
            if (dbKey == null) {
                if (!allowKeyRegeneration) {
                    throw IllegalStateException(
                        "No SQLCipher passphrase stored and key regeneration is disabled"
                    )
                }
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

    // NOTE: the previously duplicated buildDatabase() here (second Room builder
    // with its own copy of the migration list) and the unused
    // shouldRecoverFromDbOpenFailure()/quarantineDatabaseFiles() helpers were
    // dead code — recovery now lives in DatabaseProvider.warmUpDatabase() and
    // the migration list lives in AppDatabase.ALL_MIGRATIONS.

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
    @Provides @Singleton fun provideKotEventDao(databaseProvider: DatabaseProvider): KotEventDao = TenantKotEventDao(databaseProvider)
    @Provides @Singleton fun provideNotificationDao(databaseProvider: DatabaseProvider): NotificationDao = TenantNotificationDao(databaseProvider)

    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager,
        api: KhanaBookApi,
        databaseProvider: DatabaseProvider,
        restaurantDao: RestaurantDao,
        notificationRepository: NotificationRepository
    ) = UserRepository(userDao, sessionManager, workManager, api, databaseProvider, restaurantDao, notificationRepository)

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
        workManager: androidx.work.WorkManager,
        permissionManager: com.khanabook.lite.pos.feature.staff.domain.PermissionManager
    ) = CategoryRepository(categoryDao, menuDao, sessionManager, workManager, permissionManager)

    @Provides
    @Singleton
    fun provideMenuRepository(
        menuDao: MenuDao,
        sessionManager: SessionManager,
        workManager: androidx.work.WorkManager,
        permissionManager: com.khanabook.lite.pos.feature.staff.domain.PermissionManager
    ) = MenuRepository(menuDao, sessionManager, workManager, permissionManager)

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
        workManager: androidx.work.WorkManager,
        kitchenPrintQueueRepository: KitchenPrintQueueRepository,
        kotEventDao: KotEventDao,
        sessionManager: SessionManager,
        // Provider (not the concrete type) to break the KitchenPrintQueueManager <-> BillRepository
        // dependency cycle; the queue manager takes BillRepository directly.
        kitchenPrintQueueManager: javax.inject.Provider<KitchenPrintQueueManager>
    ) = BillRepository(
        billDao,
        restaurantDao,
        workManager,
        kitchenPrintQueueRepository,
        kotEventDao,
        sessionManager,
        kitchenPrintQueueManager
    )


    @Provides
    @Singleton
    fun provideBluetoothPrinterManager(@ApplicationContext context: Context) =
        BluetoothPrinterManager(context)
}