package com.khanabook.lite.pos.ui.viewmodel

import com.khanabook.lite.pos.feature.menu.data.CategoryRepository
import com.khanabook.lite.pos.feature.menu.data.MenuRepository
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.menu.viewmodel.MenuViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class MenuViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        // JVM unit tests: android.util.Log is unmocked, and the ViewModel's catch
        // blocks call Log.e before setting error state. Mock it so the error path
        // actually runs to setError.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `parseDraftsFromText should extract items from Bismi Biriyani menu text`() {
        val menuText = """
            BISMI BIRIYANI
            STARTER
            Full Half
            Bismi Fried Chicken - 380 200
            Bismi Tandoori Chicken - 400 210
            Bismi Spl Mutton Seek Kebab - 350
            Bismi Fish Tikka - 320
            BIRIYANI
            Chicken Biriyani - 170
            Mutton Biriyani - 200
            Fish Biriyani - 230
            Prawn Biriyani - 260
            Bismi Spl Chicken Biriyani - 230
            Bismi Spl Mutton Biriyani - 270
            Bismi Spl Prawn Biriyani - 280
            Bismi Spl Fish Biriyani - 290
        """.trimIndent()

        val drafts = MenuViewModel.parseDraftsFromText(menuText)
        drafts.forEach { println("Parsed: ${it.name}, price: ${it.price}, category: ${it.categoryName}, variants: ${it.variants}") }

        // Verify Starters
        val friedChicken = drafts.find { it.name == "Bismi Fried Chicken" }
        assertTrue("Fried Chicken not found", friedChicken != null)
        assertEquals(380.0, friedChicken!!.price, 0.0)
        assertEquals("non-veg", friedChicken.foodType)
        assertEquals(2, friedChicken.variants.size)
        assertEquals("Full", friedChicken.variants[0].name)
        assertEquals(380.0, friedChicken.variants[0].price, 0.0)
        assertEquals("Half", friedChicken.variants[1].name)
        assertEquals(200.0, friedChicken.variants[1].price, 0.0)

        // Verify Biriyanis (category switch should have occurred)
        val chickenBiriyani = drafts.find { it.name == "Chicken Biriyani" }
        assertTrue("Chicken Biriyani not found", chickenBiriyani != null)
        assertEquals(170.0, chickenBiriyani!!.price, 0.0)
        assertEquals("Biriyani", chickenBiriyani.categoryName)

        val fishBiriyani = drafts.find { it.name == "Bismi Spl Fish Biriyani" }
        assertTrue("Fish Biriyani not found", fishBiriyani != null)
        assertEquals(290.0, fishBiriyani!!.price, 0.0)
    }

    @Test
    fun `parseDraftsFromText should handle empty text gracefully`() {
        val drafts = MenuViewModel.parseDraftsFromText("")
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun `parseDraftsFromText should skip lines without prices in local mode`() {
        val menuText = """
            Item One
            Item Two - 100
        """.trimIndent()
        val drafts = MenuViewModel.parseDraftsFromText(menuText)
        assertEquals(2, drafts.size)
        assertEquals("Item One", drafts[0].name)
        assertEquals(0.0, drafts[0].price, 0.0)
        assertEquals("Item Two", drafts[1].name)
        assertEquals(100.0, drafts[1].price, 0.0)
    }

    @Test
    fun `master data write is blocked with a role-bound dialog for staff`() {
        val categoryRepository = mockk<CategoryRepository>(relaxed = true)
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.canWriteMasterData() } returns false
        val viewModel = MenuViewModel(
            categoryRepository = categoryRepository,
            menuRepository = mockk<MenuRepository>(relaxed = true),
            databaseProvider = mockk(relaxed = true),
            permissionManager = mockk<PermissionManager>(relaxed = true),
            sessionManager = sessionManager,
            khanaBookApi = mockk(relaxed = true)
        )

        viewModel.addCategory("Biryani", true)
        viewModel.toggleItem(1L, false)

        val blocked = viewModel.blockedPermission.value
        assertNotNull("Staff master-data write should surface a blocked dialog", blocked)
        assertEquals("master_data_write", blocked!!.key)
        coVerify(exactly = 0) { categoryRepository.insertCategory(any()) }
    }

    @Test
    fun `owner can write master data`() {
        val categoryRepository = mockk<CategoryRepository>(relaxed = true)
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.canWriteMasterData() } returns true
        val viewModel = MenuViewModel(
            categoryRepository = categoryRepository,
            menuRepository = mockk<MenuRepository>(relaxed = true),
            databaseProvider = mockk(relaxed = true),
            permissionManager = mockk<PermissionManager>(relaxed = true),
            sessionManager = sessionManager,
            khanaBookApi = mockk(relaxed = true)
        )

        viewModel.addCategory("Biryani", true)

        assertNull(viewModel.blockedPermission.value)
        coVerify { categoryRepository.insertCategory(any()) }
    }

    private fun ownerViewModel(
        categoryRepository: CategoryRepository = mockk(relaxed = true),
        menuRepository: MenuRepository = mockk(relaxed = true)
    ): MenuViewModel {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.canWriteMasterData() } returns true
        return MenuViewModel(
            categoryRepository = categoryRepository,
            menuRepository = menuRepository,
            databaseProvider = mockk(relaxed = true),
            permissionManager = mockk<PermissionManager>(relaxed = true),
            sessionManager = sessionManager,
            khanaBookApi = mockk(relaxed = true)
        )
    }

    @Test
    fun `deleteItem emits success message on success`() {
        val menuRepository = mockk<MenuRepository>(relaxed = true)
        val viewModel = ownerViewModel(menuRepository = menuRepository)

        viewModel.deleteItem(mockk(relaxed = true))

        assertEquals("Item deleted", viewModel.ocrImportUiState.value.successMessage)
        assertNull(viewModel.ocrImportUiState.value.error)
    }

    @Test
    fun `deleteItem emits error message on failure`() {
        val menuRepository = mockk<MenuRepository>(relaxed = true)
        coEvery { menuRepository.deleteItem(any()) } throws RuntimeException("db down")
        val viewModel = ownerViewModel(menuRepository = menuRepository)

        viewModel.deleteItem(mockk(relaxed = true))

        assertNotNull(viewModel.ocrImportUiState.value.error)
        assertNull(viewModel.ocrImportUiState.value.successMessage)
    }

    @Test
    fun `availability toggle does not emit success but surfaces error on failure`() {
        val menuRepository = mockk<MenuRepository>(relaxed = true)
        coEvery { menuRepository.toggleItemAvailability(any(), any()) } throws RuntimeException("boom")
        val viewModel = ownerViewModel(menuRepository = menuRepository)

        viewModel.toggleItem(1L, false)

        assertNull("Availability toggle must not emit a success toast", viewModel.ocrImportUiState.value.successMessage)
        assertNotNull(viewModel.ocrImportUiState.value.error)
    }

    @Test
    fun `availability toggle success stays silent`() {
        val menuRepository = mockk<MenuRepository>(relaxed = true)
        val viewModel = ownerViewModel(menuRepository = menuRepository)

        viewModel.toggleItem(1L, true)

        assertNull(viewModel.ocrImportUiState.value.successMessage)
        assertNull(viewModel.ocrImportUiState.value.error)
    }

    @Test
    fun `category CRUD emits success messages`() {
        val categoryRepository = mockk<CategoryRepository>(relaxed = true)
        val viewModel = ownerViewModel(categoryRepository = categoryRepository)

        viewModel.deleteCategory(mockk(relaxed = true))
        assertEquals("Category deleted", viewModel.ocrImportUiState.value.successMessage)

        viewModel.updateCategory(mockk(relaxed = true))
        assertEquals("Category updated", viewModel.ocrImportUiState.value.successMessage)
    }

    @Test
    fun `clearCategoryItems reports cleared count`() {
        val menuRepository = mockk<MenuRepository>(relaxed = true)
        coEvery { menuRepository.getItemsByCategoryOnce(any()) } returns listOf(
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
        val viewModel = ownerViewModel(menuRepository = menuRepository)

        viewModel.clearCategoryItems(5L)

        // clearCategoryItems runs on Dispatchers.IO; await the resulting state.
        val message = awaitValue { viewModel.ocrImportUiState.value.successMessage }
        assertEquals("Cleared 2 items", message)
    }

    private fun <T> awaitValue(timeoutMs: Long = 2_000L, block: () -> T?): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var value = block()
        while (value == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L)
            value = block()
        }
        return value
    }
}
