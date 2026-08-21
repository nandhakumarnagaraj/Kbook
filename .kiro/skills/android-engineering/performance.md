# Performance — APK Size, Recomposition & Startup

## When to Trigger

- Optimizing app startup time (cold/warm start)
- Reducing APK size for low-end devices (common in restaurant POS)
- Fixing recomposition issues in Compose (jank, dropped frames)
- Profiling memory usage or detecting leaks
- Optimizing Room queries or large dataset handling
- Building smooth scrolling lists (menu grid, order history)
- Targeting KhanaBook's low-end Android tablets/phones

## Stack Context

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose (Material 3) |
| DI | Hilt (compile-time) |
| Images | Coil (Compose integration) |
| Async | Coroutines + Flow |
| DB | Room |
| Build | Gradle (AGP 8+), R8 |

---

## Step-by-Step Workflow

### 1. App Startup Optimization

```kotlin
// Use App Startup library to control initialization order
class KhanaBookInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // Initialize only critical-path items here
        // Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

// Defer non-critical initialization
@HiltAndroidApp
class KhanaBookApp : Application() {

    @Inject lateinit var workManager: WorkManager

    override fun onCreate() {
        super.onCreate()
        // Only critical init on main thread
        // Defer everything else
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    initDeferredComponents()
                }
            }
        )
    }

    private fun initDeferredComponents() {
        // Schedule sync, analytics, etc. after first frame
        SyncWorker.enqueuePeriodicSync(workManager)
    }
}
```

### 2. Splash Screen with Baseline Profiles

```kotlin
// Baseline Profiles — generate for critical paths
@ExperimentalBaselineProfilesApi
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = rule.collect(
        packageName = "com.khanabook.pos"
    ) {
        // Cold start
        pressHome()
        startActivityAndWait()

        // Critical user journeys
        device.findObject(By.text("Orders")).click()
        device.waitForIdle()

        device.findObject(By.text("Menu")).click()
        device.waitForIdle()

        // Scroll menu
        device.findObject(By.scrollable(true)).scroll(Direction.DOWN, 1f)
    }
}
```

```groovy
// build.gradle.kts
android {
    buildTypes {
        release {
            // Enable baseline profiles
            baselineProfile {
                automaticGenerationDuringBuild = true
            }
        }
    }
}

dependencies {
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    baselineProfile(project(":baselineprofile"))
}
```

### 3. Recomposition Optimization

```kotlin
// ❌ BAD: Unstable lambda causes recomposition of entire list
@Composable
fun MenuList(items: List<MenuItem>, viewModel: OrderViewModel) {
    LazyColumn {
        items(items) { item ->
            MenuItemCard(
                item = item,
                onAdd = { viewModel.addItem(item) } // New lambda every recomposition!
            )
        }
    }
}

// ✅ GOOD: Stable key + remembered callback
@Composable
fun MenuList(
    items: List<MenuItem>,
    onAddItem: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(items = items, key = { it.id }) { item ->
            MenuItemCard(
                item = item,
                onAdd = { onAddItem(item) }
            )
        }
    }
}

// ✅ GOOD: Use @Immutable for data classes in composition
@Immutable
data class MenuItemUi(
    val id: String,
    val name: String,
    val price: String, // Pre-formatted
    val imageUrl: String?,
    val isVeg: Boolean
)

// ✅ GOOD: derivedStateOf for computed values
@Composable
fun OrderSummary(items: List<OrderItem>) {
    val totalItems by remember(items) {
        derivedStateOf { items.sumOf { it.quantity } }
    }
    val totalPrice by remember(items) {
        derivedStateOf { items.sumOf { it.price * it.quantity } }
    }

    Text("$totalItems items • ₹${totalPrice / 100}")
}
```

### 4. Lazy Layout Performance

```kotlin
@Composable
fun MenuGrid(
    categories: List<CategoryWithItems>,
    onItemTap: (MenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier,
        // Pre-fetch items for smoother scrolling
        contentPadding = PaddingValues(8.dp),
        // Avoid measuring all items
        state = rememberLazyGridState()
    ) {
        categories.forEach { category ->
            item(
                key = "header_${category.id}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header"
            ) {
                CategoryHeader(name = category.name)
            }
            items(
                items = category.items,
                key = { "item_${it.id}" },
                contentType = { "menu_item" } // Same type = reuse ViewHolder
            ) { menuItem ->
                MenuItemCard(item = menuItem, onTap = { onItemTap(menuItem) })
            }
        }
    }
}
```

### 5. Image Loading Optimization

```kotlin
@Composable
fun MenuItemImage(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .size(Size(240, 240)) // Don't decode full-res
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = null,
        modifier = modifier
            .size(120.dp)
            .clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.placeholder_food),
        error = painterResource(R.drawable.placeholder_food)
    )
}

// Configure Coil globally for KhanaBook
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.2) // 20% of app memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .respectCacheHeaders(false) // Cache aggressively for offline
            .build()
    }
}
```

### 6. APK Size Reduction

```kotlin
// build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Split APKs by ABI
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // Remove unused resources
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin"
            )
        }
    }

    // Use WebP for images
    aaptOptions {
        // Convert PNGs to WebP during build
        // Do this manually: right-click > Convert to WebP in Android Studio
    }
}

// Use Android App Bundle for Play Store
// ./gradlew bundleRelease
```

### 7. Room Query Performance

```kotlin
// ❌ BAD: Loading entire order history at once
@Query("SELECT * FROM orders ORDER BY created_at DESC")
fun getAllOrders(): Flow<List<OrderEntity>>

// ✅ GOOD: Paginated loading
@Query("SELECT * FROM orders ORDER BY created_at DESC")
fun getOrdersPaged(): PagingSource<Int, OrderEntity>

// Usage with Paging 3
@HiltViewModel
class OrderHistoryViewModel @Inject constructor(
    private val orderDao: OrderDao
) : ViewModel() {

    val orders: Flow<PagingData<Order>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 5,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { orderDao.getOrdersPaged() }
    ).flow
        .map { pagingData -> pagingData.map { it.toDomain() } }
        .cachedIn(viewModelScope)
}

// In Compose
@Composable
fun OrderHistoryScreen(viewModel: OrderHistoryViewModel = hiltViewModel()) {
    val orders = viewModel.orders.collectAsLazyPagingItems()

    LazyColumn {
        items(
            count = orders.itemCount,
            key = orders.itemKey { it.id }
        ) { index ->
            orders[index]?.let { order ->
                OrderHistoryCard(order = order)
            }
        }

        // Loading indicator
        if (orders.loadState.append is LoadState.Loading) {
            item { CircularProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        }
    }
}
```

### 8. Memory Optimization

```kotlin
// Avoid holding large bitmaps — use Coil's memory cache
// Avoid retaining Activity/Fragment references in singletons

// Use lifecycle-aware collection to prevent leaks
@Composable
fun OrderScreen(viewModel: OrderViewModel = hiltViewModel()) {
    // This automatically stops collection when lifecycle is below STARTED
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // ...
}

// Cancel ongoing work when ViewModel is cleared
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncUseCase: SyncUseCase
) : ViewModel() {

    private var syncJob: Job? = null

    fun startSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            syncUseCase.syncAll()
        }
    }

    // viewModelScope auto-cancels on ViewModel.onCleared()
}
```

---

## Anti-Patterns to Avoid

| Anti-Pattern | Correct Approach |
|-------------|-----------------|
| Loading all data in `onCreate` | Defer to lifecycle events, load lazily |
| No baseline profiles | Generate for critical user journeys |
| Universal APK for all ABIs | Use App Bundle or ABI splits |
| Full-res image decode | Specify target size in image requests |
| No `contentType` in lazy lists | Set contentType for item recycling |
| `items(list)` without `key` | Always provide stable `key` |
| Allocating objects in composition | Use `remember { }` |
| Unbounded Room queries | Use `Paging 3` for large datasets |
| Using `collectAsState()` | Use `collectAsStateWithLifecycle()` |
| Heavy init in `Application.onCreate()` | Defer non-critical init to after first frame |
| No R8/ProGuard in release | Always enable minification + shrinking |

---

## Verification Checklist

- [ ] Cold start time < 1 second (measure with `adb shell am start -W`)
- [ ] Baseline profiles generated for critical paths
- [ ] R8 + resource shrinking enabled in release
- [ ] ABI splits or App Bundle configured
- [ ] Images use Coil with size constraints + disk cache
- [ ] LazyColumn/Grid items have stable `key` and `contentType`
- [ ] `@Immutable`/`@Stable` on UI data classes
- [ ] `derivedStateOf` for computed values in composition
- [ ] Paging 3 for lists > 50 items
- [ ] No `StrictMode` violations in debug builds
- [ ] Layout Inspector shows no unnecessary recompositions
- [ ] APK size < 15 MB (check with APK Analyzer)
- [ ] No memory leaks (verify with LeakCanary in debug)
- [ ] `collectAsStateWithLifecycle()` used everywhere

---

## Profiling Commands

```bash
# Measure cold start time
adb shell am start -W com.khanabook.pos/.MainActivity

# Record method trace
adb shell am start -n com.khanabook.pos/.MainActivity --start-profiler /data/local/tmp/trace.trace

# Check APK size breakdown
./gradlew :app:assembleRelease
# Then use Android Studio > Build > Analyze APK

# Monitor memory
adb shell dumpsys meminfo com.khanabook.pos

# Check for recomposition (enable in Compose layout inspector)
# Android Studio > Layout Inspector > Show Recomposition Counts
```
