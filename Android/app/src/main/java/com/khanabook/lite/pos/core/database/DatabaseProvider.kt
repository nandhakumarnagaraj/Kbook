package com.khanabook.lite.pos.core.database
import com.khanabook.lite.pos.feature.billing.data.*
import com.khanabook.lite.pos.feature.auth.data.*

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.core.di.DatabaseModule
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
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

    /**
     * Open or create the database for [restaurantId] and make it the active database
     * for all subsequent DAO calls. Unlike [getDatabase], this method does NOT read
     * the restaurant ID from [SessionManager] — it accepts it directly, so the caller
     * can switch databases *before* publishing the new restaurantId to StateFlow
     * collectors, eliminating the race window where UI sees restaurant B while the
     * old database (A) is still active.
     *
     * Call this first, then [SessionManager.saveRestaurantId], to make the switch atomic
     * from the app's point of view.
     */
    @Synchronized
    fun switchToDatabase(restaurantId: Long): AppDatabase {
        // Session state guard: warn when database is accessed outside a ready session.
        // This is a soft guard — initialization flows (e.g. loadPersistedUser) need DB
        // access before the session is formally READY. The warning helps detect
        // accidental post-logout access during development.
        if (!sessionManager.isSessionReady()) {
            Log.w(tag, "switchToDatabase called while session is ${sessionManager.sessionState.value}. " +
                "restaurant=$restaurantId. This may indicate stale DB access after logout.")
        }
        if (activeDatabase != null && activeRestaurantId == restaurantId) {
            return activeDatabase ?: throw IllegalStateException("Database not initialized. Call switchToDatabase() first.")
        }
        activeDatabase?.close()
        activeRestaurantId = restaurantId
        val dbName = if (restaurantId > 0) {
            "khanabook_lite_db_$restaurantId"
        } else {
            "khanabook_lite_db"
        }

        // Check if legacy data needs migration (idempotent — skips if target DB exists)
        if (restaurantId > 0) {
            migrateLegacyDataIfNecessary(restaurantId)
        }

        try {
            Log.i(
                tag,
                "Switching to database expectedRoomVersion=78 appVersion=${BuildConfig.VERSION_NAME} " +
                    "restaurant=${maskRestaurantId(restaurantId)} terminal=${sessionManager.getTerminalId() ?: "none"} " +
                    "device=${sessionManager.getDeviceId().takeLast(6)} db=$dbName"
            )
            activeDatabase = buildDatabaseWithName(context, dbName)
            databaseState.value = activeDatabase
            Log.i(tag, "Switched to database instance: $dbName")
        } catch (e: Exception) {
            Log.e(
                tag,
                "Database switch failed expectedRoomVersion=78 appVersion=${BuildConfig.VERSION_NAME} " +
                    "restaurant=${maskRestaurantId(restaurantId)} terminal=${sessionManager.getTerminalId() ?: "none"} " +
                    "device=${sessionManager.getDeviceId().takeLast(6)} db=$dbName",
                e
            )
            throw e
        }
        return activeDatabase ?: throw IllegalStateException("Database not initialized. Call switchToDatabase() first.")
    }

    /**
     * Returns true if a database file already exists for [restaurantId].
     * Safe to call before [switchToDatabase] — does not touch Room or file I/O
     * beyond the fast [android.content.Context.getDatabasePath] stat call.
     */
    fun isDatabaseFileExists(restaurantId: Long): Boolean {
        return if (restaurantId > 0L) {
            context.getDatabasePath("khanabook_lite_db_$restaurantId").exists()
        } else {
            context.getDatabasePath("khanabook_lite_db").exists()
        }
    }

    @Synchronized
    fun getDatabase(): AppDatabase {
        return switchToDatabase(sessionManager.getRestaurantId())
    }

    fun warmUpDatabase() {
        val database = getDatabase()
        try {
            database.openHelper.writableDatabase
            Log.i(tag, "Database warm-up completed expectedRoomVersion=78")
        } catch (e: Exception) {
            if (!isRecoverableDbOpenFailure(e)) {
                Log.e(tag, "Database warm-up failed expectedRoomVersion=78", e)
                throw e
            }
            // Recovery path: SQLCipher passphrase mismatch (Keystore reset) or a
            // corrupt file. Quarantine (rename, never delete) the unreadable files
            // and rebuild a fresh database. Unsynced local rows survive in the
            // .corrupt-* copies for manual recovery; master sync repopulates the rest.
            val dbName = currentDatabaseName()
            val restaurantId = activeRestaurantId
            Log.e(tag, "Database open failed with a recoverable cipher/corruption error; " +
                "quarantining db=$dbName and rebuilding", e)
            try {
                database.close()
            } catch (ignored: Exception) {
            }
            activeDatabase = null
            databaseState.value = null
            quarantineDatabaseFiles(dbName)
            val recovered = switchToDatabase(restaurantId)
            recovered.openHelper.writableDatabase
            Log.w(tag, "Database rebuilt after quarantine. Unsynced local data was preserved " +
                "in *.corrupt-* files; a full master re-sync is required. db=$dbName")
        }
    }

    private fun currentDatabaseName(): String =
        if (activeRestaurantId > 0) "khanabook_lite_db_$activeRestaurantId" else "khanabook_lite_db"

    private fun isRecoverableDbOpenFailure(error: Throwable): Boolean {
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

    private fun quarantineDatabaseFiles(dbName: String) {
        val suffix = ".corrupt-${System.currentTimeMillis()}"
        listOf(dbName, "$dbName-wal", "$dbName-shm", "$dbName-journal").forEach { name ->
            val file = context.getDatabasePath(name)
            if (file.exists()) {
                val target = java.io.File(file.absolutePath + suffix)
                if (file.renameTo(target)) {
                    Log.w(tag, "Quarantined ${file.name} -> ${target.name}")
                }
            }
        }
    }

    @Synchronized
    fun closeDatabase() {
        activeDatabase?.close()
        activeDatabase = null
        databaseState.value = null
        activeRestaurantId = -1L
        sessionManager.setSessionState(SessionManager.SessionState.INACTIVE)
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
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
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
                        newDb!!.categoryDao().upsertSyncedCategories(categories)
                    }

                    // 4. Migrate MenuItems & Variants
                    val menuItems = legacyDb!!.menuDao().getAllMenuItemsOnce(restaurantId)
                    if (menuItems.isNotEmpty()) {
                        newDb!!.menuDao().upsertSyncedMenuItems(menuItems)
                    }
                    val variants = legacyDb!!.menuDao().getAllVariantsOnce(restaurantId)
                    if (variants.isNotEmpty()) {
                        newDb!!.menuDao().upsertSyncedItemVariants(variants)
                    }

                    // 6. Migrate Bills, BillItems, BillPayments
                    val unsyncedBills = legacyDb!!.billDao().getUnsyncedBills(restaurantId)
                    if (unsyncedBills.isNotEmpty()) {
                        newDb!!.billDao().insertSyncedBills(unsyncedBills)
                    }
                    val unsyncedItems = legacyDb!!.billDao().getUnsyncedBillItems(restaurantId)
                    if (unsyncedItems.isNotEmpty()) {
                        newDb!!.billDao().upsertSyncedBillItems(unsyncedItems)
                    }
                    val unsyncedPayments = legacyDb!!.billDao().getUnsyncedBillPayments(restaurantId)
                    if (unsyncedPayments.isNotEmpty()) {
                        newDb!!.billDao().upsertSyncedBillPayments(unsyncedPayments)
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
