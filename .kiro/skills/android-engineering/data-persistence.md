# Data Persistence — Room, Offline-First & Sync

## When to Trigger

- Creating or modifying Room entities, DAOs, or database migrations
- Implementing offline-first data access patterns
- Designing repository layer with local + remote sources
- Building sync logic between Room and backend API (Spring Boot)
- Handling conflict resolution for offline edits
- Working with KhanaBook's menu, orders, or billing data

## Stack Context

| Layer | Technology |
|-------|-----------|
| Local DB | Room (SQLite) |
| Remote API | Retrofit + OkHttp |
| DI | Hilt |
| Async | Coroutines + Flow |
| Serialization | Kotlinx Serialization / Moshi |

---

## Step-by-Step Workflow

### 1. Entity Design

```kotlin
@Entity(
    tableName = "menu_items",
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["sync_status"])
    ]
)
data class MenuItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "price")
    val price: Long, // Store as paisa (cents) to avoid floating point
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "is_veg")
    val isVeg: Boolean,
    @ColumnInfo(name = "is_available")
    val isAvailable: Boolean = true,
    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    // Sync metadata
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "server_version")
    val serverVersion: Long = 0
)

enum class SyncStatus {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DELETE,
    CONFLICT
}
```

### 2. DAO Design

```kotlin
@Dao
interface MenuItemDao {

    // Observe all available items grouped by category
    @Query("""
        SELECT * FROM menu_items 
        WHERE is_available = 1 AND sync_status != 'PENDING_DELETE'
        ORDER BY sort_order ASC
    """)
    fun observeAvailableItems(): Flow<List<MenuItemEntity>>

    // One-shot query for sync
    @Query("SELECT * FROM menu_items WHERE sync_status != 'SYNCED'")
    suspend fun getPendingSyncItems(): List<MenuItemEntity>

    @Upsert
    suspend fun upsert(item: MenuItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MenuItemEntity>)

    @Query("UPDATE menu_items SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Transaction
    suspend fun syncFromServer(serverItems: List<MenuItemEntity>) {
        // Only update items that haven't been locally modified
        serverItems.forEach { serverItem ->
            val local = getById(serverItem.id)
            if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                upsert(serverItem.copy(syncStatus = SyncStatus.SYNCED))
            } else if (local.serverVersion < serverItem.serverVersion) {
                // Server wins if version is newer and local has pending changes
                upsert(serverItem.copy(syncStatus = SyncStatus.CONFLICT))
            }
        }
    }

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: String): MenuItemEntity?
}
```

### 3. Database Setup with Migrations

```kotlin
@Database(
    entities = [
        MenuItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        CategoryEntity::class,
        TableEntity::class
    ],
    version = 3,
    exportSchema = true // Always export for migration testing
)
@TypeConverters(Converters::class)
abstract class KhanaBookDatabase : RoomDatabase() {
    abstract fun menuItemDao(): MenuItemDao
    abstract fun orderDao(): OrderDao
    abstract fun categoryDao(): CategoryDao
}

// Migration example
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX index_menu_items_sync_status ON menu_items(sync_status)")
    }
}

// Hilt module
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KhanaBookDatabase {
        return Room.databaseBuilder(
            context,
            KhanaBookDatabase::class.java,
            "khanabook.db"
        )
            .addMigrations(MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideMenuItemDao(db: KhanaBookDatabase): MenuItemDao = db.menuItemDao()
}
```

### 4. Repository Pattern (Offline-First)

```kotlin
class MenuRepository @Inject constructor(
    private val menuItemDao: MenuItemDao,
    private val menuApi: MenuApiService,
    private val networkMonitor: NetworkMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Single source of truth: always return local data.
     * Refresh from server in background when network is available.
     */
    fun observeMenuItems(): Flow<List<MenuItem>> {
        return menuItemDao.observeAvailableItems()
            .map { entities -> entities.map { it.toDomain() } }
            .onStart { refreshIfConnected() }
            .flowOn(ioDispatcher)
    }

    private suspend fun refreshIfConnected() {
        if (!networkMonitor.isConnected()) return
        try {
            val serverItems = menuApi.getMenuItems()
            menuItemDao.syncFromServer(serverItems.map { it.toEntity() })
        } catch (e: Exception) {
            // Silently fail — offline-first means local data is always valid
            Timber.w(e, "Menu refresh failed, using cached data")
        }
    }

    suspend fun addMenuItem(item: MenuItem): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val entity = item.toEntity().copy(syncStatus = SyncStatus.PENDING_UPLOAD)
            menuItemDao.upsert(entity)
            // Attempt immediate sync
            if (networkMonitor.isConnected()) {
                syncPendingItems()
            }
        }
    }

    suspend fun syncPendingItems() = withContext(ioDispatcher) {
        val pending = menuItemDao.getPendingSyncItems()
        pending.forEach { item ->
            try {
                when (item.syncStatus) {
                    SyncStatus.PENDING_UPLOAD -> {
                        menuApi.upsertMenuItem(item.toDto())
                        menuItemDao.updateSyncStatus(item.id, SyncStatus.SYNCED)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        menuApi.deleteMenuItem(item.id)
                        menuItemDao.delete(item.id)
                    }
                    else -> { /* skip */ }
                }
            } catch (e: Exception) {
                Timber.w(e, "Sync failed for item ${item.id}")
            }
        }
    }
}
```

### 5. Network Monitor

```kotlin
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService<ConnectivityManager>()!!

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        // Emit current state
        trySend(isConnected())
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun isConnected(): Boolean {
        val capabilities = connectivityManager
            .getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
```

### 6. Sync Worker (WorkManager)

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val menuRepository: MenuRepository,
    private val orderRepository: OrderRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            menuRepository.syncPendingItems()
            orderRepository.syncPendingOrders()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun enqueuePeriodicSync(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "khanabook_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

---

## Anti-Patterns to Avoid

| Anti-Pattern | Correct Approach |
|-------------|-----------------|
| Storing prices as `Double`/`Float` | Use `Long` (paisa/cents) or `BigDecimal` |
| Making network call before showing data | Show cached data immediately, refresh in background |
| No sync status tracking on entities | Add `syncStatus` + `updatedAt` columns |
| Auto-migrations without testing | Write explicit migrations + test with `MigrationTestHelper` |
| Blocking main thread with DB calls | All DAO functions are `suspend` or return `Flow` |
| Single large entity for everything | Normalize: separate entities with relations |
| Ignoring schema export | Set `exportSchema = true` for migration verification |
| Hardcoding database name | Use DI module constant for testability |

---

## Verification Checklist

- [ ] All monetary values stored as `Long` (paisa)
- [ ] Entities have `syncStatus` and `updatedAt` for offline-first
- [ ] DAOs return `Flow` for observable queries, `suspend` for one-shot
- [ ] Repository returns local data immediately (no network blocking)
- [ ] Background refresh triggered on `onStart` or connectivity change
- [ ] WorkManager handles periodic sync with exponential backoff
- [ ] Migrations are explicit and tested with `MigrationTestHelper`
- [ ] `exportSchema = true` in `@Database` annotation
- [ ] Indices exist on frequently queried columns
- [ ] TypeConverters handle enums and complex types
- [ ] Database provided as `@Singleton` via Hilt
- [ ] Network errors don't crash — graceful degradation to cached data
