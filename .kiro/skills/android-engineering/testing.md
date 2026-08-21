# Testing — JUnit5, MockK & Compose Tests

## When to Trigger

- Writing unit tests for ViewModels, Repositories, or UseCases
- Writing UI tests for Compose screens
- Setting up test infrastructure (fakes, test doubles, test rules)
- Verifying business logic for orders, billing, or menu operations
- Running integration tests with Room (in-memory DB)
- Creating test fixtures for KhanaBook domain objects

## Stack Context

| Layer | Technology |
|-------|-----------|
| Unit Tests | JUnit 5 + MockK |
| Coroutine Tests | kotlinx-coroutines-test (Turbine) |
| Compose Tests | compose-ui-test-junit4 |
| DB Tests | Room testing (in-memory) |
| DI in Tests | Hilt Testing |
| Assertions | Truth / AssertK |

---

## Step-by-Step Workflow

### 1. ViewModel Unit Test

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class OrderViewModelTest {

    private val orderRepository: OrderRepository = mockk()
    private val menuRepository: MenuRepository = mockk()

    private lateinit var viewModel: OrderViewModel

    @BeforeEach
    fun setup() {
        // Default stubs
        every { orderRepository.observeActiveOrder() } returns flowOf(testOrder())
        every { menuRepository.observeMenuItems() } returns flowOf(testMenuItems())
    }

    @Test
    fun `initial state loads active order`() = runTest {
        viewModel = OrderViewModel(orderRepository, menuRepository)

        viewModel.uiState.test {
            val loading = awaitItem()
            assertThat(loading).isInstanceOf(OrderUiState.Loading::class.java)

            val success = awaitItem()
            assertThat(success).isInstanceOf(OrderUiState.Success::class.java)
            assertThat((success as OrderUiState.Success).items).hasSize(2)
        }
    }

    @Test
    fun `addItem increases quantity if item exists`() = runTest {
        coEvery { orderRepository.addItemToOrder(any(), any()) } returns Result.success(Unit)
        viewModel = OrderViewModel(orderRepository, menuRepository)

        viewModel.uiState.test {
            skipItems(2) // Loading + initial Success
        }

        viewModel.addItem(testMenuItem())

        coVerify { orderRepository.addItemToOrder(any(), testMenuItem().id) }
    }

    @Test
    fun `error state on repository failure`() = runTest {
        every { orderRepository.observeActiveOrder() } returns flow {
            throw IOException("No cached data")
        }

        viewModel = OrderViewModel(orderRepository, menuRepository)

        viewModel.uiState.test {
            skipItems(1) // Loading
            val error = awaitItem()
            assertThat(error).isInstanceOf(OrderUiState.Error::class.java)
            assertThat((error as OrderUiState.Error).message).contains("No cached data")
        }
    }
}
```

### 2. Main Dispatcher Extension (JUnit 5)

```kotlin
@ExperimentalCoroutinesApi
class MainDispatcherExtension(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
```

### 3. Repository Test with Fakes

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class MenuRepositoryTest {

    private val fakeDao = FakeMenuItemDao()
    private val fakeApi: MenuApiService = mockk()
    private val fakeNetworkMonitor = FakeNetworkMonitor()

    private lateinit var repository: MenuRepository

    @BeforeEach
    fun setup() {
        repository = MenuRepository(
            menuItemDao = fakeDao,
            menuApi = fakeApi,
            networkMonitor = fakeNetworkMonitor,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `observeMenuItems returns local data when offline`() = runTest {
        fakeNetworkMonitor.setConnected(false)
        fakeDao.insertAll(testMenuEntities())

        repository.observeMenuItems().test {
            val items = awaitItem()
            assertThat(items).hasSize(3)
            assertThat(items.first().name).isEqualTo("Butter Chicken")
        }
    }

    @Test
    fun `observeMenuItems refreshes from server when online`() = runTest {
        fakeNetworkMonitor.setConnected(true)
        coEvery { fakeApi.getMenuItems() } returns listOf(serverMenuItem())

        repository.observeMenuItems().test {
            val items = awaitItem()
            assertThat(items).isNotEmpty()
            coVerify { fakeApi.getMenuItems() }
        }
    }

    @Test
    fun `addMenuItem marks as PENDING_UPLOAD when offline`() = runTest {
        fakeNetworkMonitor.setConnected(false)

        val result = repository.addMenuItem(testMenuItem())

        assertThat(result.isSuccess).isTrue()
        val saved = fakeDao.getById(testMenuItem().id)
        assertThat(saved?.syncStatus).isEqualTo(SyncStatus.PENDING_UPLOAD)
    }
}
```

### 4. Fake DAO for Testing

```kotlin
class FakeMenuItemDao : MenuItemDao {

    private val items = mutableListOf<MenuItemEntity>()
    private val flow = MutableStateFlow<List<MenuItemEntity>>(emptyList())

    override fun observeAvailableItems(): Flow<List<MenuItemEntity>> = flow

    override suspend fun getPendingSyncItems(): List<MenuItemEntity> =
        items.filter { it.syncStatus != SyncStatus.SYNCED }

    override suspend fun upsert(item: MenuItemEntity) {
        items.removeAll { it.id == item.id }
        items.add(item)
        flow.value = items.toList()
    }

    override suspend fun upsertAll(items: List<MenuItemEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun getById(id: String): MenuItemEntity? =
        items.find { it.id == id }

    override suspend fun updateSyncStatus(id: String, status: SyncStatus) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] = items[index].copy(syncStatus = status)
            flow.value = items.toList()
        }
    }

    // Test helpers
    suspend fun insertAll(entities: List<MenuItemEntity>) = upsertAll(entities)
    fun clear() { items.clear(); flow.value = emptyList() }
}
```

### 5. Compose UI Test

```kotlin
class OrderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays order items when state is Success`() {
        val uiState = OrderUiState.Success(
            orderId = "order-1",
            tableNumber = 5,
            items = listOf(
                OrderItem(id = "1", name = "Butter Naan", quantity = 2, price = 4000L),
                OrderItem(id = "2", name = "Dal Tadka", quantity = 1, price = 18000L)
            ),
            total = BigDecimal("260.00")
        )

        composeTestRule.setContent {
            KhanaBookTheme {
                OrderScreen(
                    uiState = uiState,
                    onAddItem = {},
                    onRemoveItem = {},
                    onCheckout = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Butter Naan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dal Tadka").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹260.00").assertIsDisplayed()
    }

    @Test
    fun `shows loading indicator when state is Loading`() {
        composeTestRule.setContent {
            KhanaBookTheme {
                OrderScreen(
                    uiState = OrderUiState.Loading,
                    onAddItem = {},
                    onRemoveItem = {},
                    onCheckout = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `checkout button triggers callback`() {
        var checkoutClicked = false
        val uiState = OrderUiState.Success(
            orderId = "order-1",
            tableNumber = 5,
            items = listOf(testOrderItem()),
            total = BigDecimal("100.00")
        )

        composeTestRule.setContent {
            KhanaBookTheme {
                OrderScreen(
                    uiState = uiState,
                    onAddItem = {},
                    onRemoveItem = {},
                    onCheckout = { checkoutClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Pay ₹100.00").performClick()
        assertThat(checkoutClicked).isTrue()
    }
}
```

### 6. Room Database Test

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class MenuItemDaoTest {

    private lateinit var database: KhanaBookDatabase
    private lateinit var dao: MenuItemDao

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KhanaBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.menuItemDao()
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert inserts new item`() = runTest {
        val item = testMenuItemEntity()
        dao.upsert(item)

        val result = dao.getById(item.id)
        assertThat(result).isEqualTo(item)
    }

    @Test
    fun `observeAvailableItems excludes deleted items`() = runTest {
        dao.upsert(testMenuItemEntity(id = "1", syncStatus = SyncStatus.SYNCED))
        dao.upsert(testMenuItemEntity(id = "2", syncStatus = SyncStatus.PENDING_DELETE))

        dao.observeAvailableItems().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items.first().id).isEqualTo("1")
        }
    }
}
```

### 7. Test Fixtures

```kotlin
// TestFixtures.kt — shared across all tests
object TestFixtures {
    fun testMenuItem(
        id: String = "item-1",
        name: String = "Butter Chicken",
        price: Long = 35000L, // ₹350.00
        categoryId: String = "cat-1",
        isVeg: Boolean = false
    ) = MenuItem(id, name, price, categoryId, isVeg)

    fun testOrder(
        id: String = "order-1",
        tableNumber: Int = 5,
        items: List<OrderItem> = listOf(testOrderItem())
    ) = Order(id, tableNumber, items)

    fun testOrderItem(
        id: String = "oi-1",
        name: String = "Butter Naan",
        quantity: Int = 2,
        price: Long = 4000L
    ) = OrderItem(id, name, quantity, price)

    fun testMenuItemEntity(
        id: String = "item-1",
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ) = MenuItemEntity(
        id = id,
        name = "Butter Chicken",
        price = 35000L,
        categoryId = "cat-1",
        isVeg = false,
        syncStatus = syncStatus,
        updatedAt = 1000L
    )
}
```

---

## Anti-Patterns to Avoid

| Anti-Pattern | Correct Approach |
|-------------|-----------------|
| Mocking everything (over-mocking) | Use fakes for data sources, mocks for boundaries |
| Testing implementation details | Test observable behavior (outputs, state changes) |
| Skipping coroutine test setup | Use `MainDispatcherExtension` + `runTest` |
| No test for error/edge cases | Always test Loading, Success, Error, and empty states |
| Flaky tests with `delay()` | Use `advanceUntilIdle()` or Turbine's `awaitItem()` |
| Shared mutable state between tests | Fresh setup in `@BeforeEach`, no shared vars |
| Testing private methods | Test through public API |
| Not testing Room migrations | Use `MigrationTestHelper` for every migration |

---

## Verification Checklist

- [ ] ViewModels tested with `Turbine` for Flow assertions
- [ ] `MainDispatcherExtension` applied to all coroutine tests
- [ ] Fakes exist for all DAO interfaces
- [ ] Compose tests use stateless screen composable directly
- [ ] All UiState variants tested (Loading, Success, Error, Empty)
- [ ] Edge cases: empty lists, network failures, null fields
- [ ] Room tests use in-memory database
- [ ] Test fixtures shared via `TestFixtures` object
- [ ] No `Thread.sleep()` or `delay()` in tests
- [ ] Tests run in < 5 seconds each
- [ ] Migration tests exist for each database version bump
- [ ] CI runs both `testDebugUnitTest` and `connectedDebugAndroidTest`
