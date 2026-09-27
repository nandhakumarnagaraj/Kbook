package com.khanabook.lite.pos.data.repository
import com.khanabook.lite.pos.feature.menu.data.MenuRepository

import androidx.work.WorkManager
import android.content.Context
import android.content.SharedPreferences
import com.khanabook.lite.pos.feature.menu.data.ItemVariantEntity
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * P1: the acting user's authorization revision must be stamped onto every locally
 * created/edited menu row so the server can run Decision-A-strict revalidation.
 *
 * SessionManager is built as a REAL instance on a mocked Context: its primitive
 * getter getRestaurantId(): Long coexists with the restaurantId: StateFlow<Long>
 * property getter of the same name, which MockK's relaxed name-hinter cannot
 * disambiguate (it answers the Long call with the StateFlow → ClassCastException).
 */
class MenuRepositoryTest {

    private lateinit var menuDao: MenuDao
    private lateinit var sessionManager: SessionManager
    private lateinit var workManager: WorkManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var repository: MenuRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        menuDao = mockk(relaxed = true)
        sessionManager = realSessionManager()
        workManager = mockk(relaxed = true)
        permissionManager = mockk(relaxed = true)
        repository = MenuRepository(menuDao, sessionManager, workManager, permissionManager)
    }

    /** A real SessionManager backed by mocked SharedPreferences (no Android runtime). */
    private fun realSessionManager(): SessionManager {
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } returns null
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        return SessionManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun item(id: Long = 1L, price: String = "250", available: Boolean = true) = MenuItemEntity(
        id = id,
        categoryId = 10L,
        name = "Biryani",
        basePrice = price,
        isAvailable = available,
        restaurantId = 55L
    )

    @Test
    fun `updateItem stamps current permission revision`() = runTest {
        every { permissionManager.currentRevision() } returns 42L
        val saved = slot<MenuItemEntity>()
        coEvery { menuDao.updateItem(capture(saved)) } just Runs

        repository.updateItem(item(price = "300"))

        assertEquals(42L, saved.captured.permissionRevisionAtCreation)
        assertEquals(false, saved.captured.isSynced)
    }

    // NOTE: toggleItemAvailability() delegates to updateItem() for the actual write,
    // so the revision-stamping guarantee above transitively covers the toggle path.

    @Test
    fun `updateItemPhotoMetadata updates dao locally without queuing sync`() = runTest {
        coEvery { menuDao.updateImageMetadataLocally(any(), any(), any(), any()) } just Runs

        repository.updateItemPhotoMetadata(1L, "https://cdn.example.com/photo.jpg", 3)

        io.mockk.coVerify(exactly = 1) {
            menuDao.updateImageMetadataLocally(1L, 0L, "https://cdn.example.com/photo.jpg", 3)
        }
        io.mockk.verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>())
        }
    }

    // The class of bug worth locking down is not "the validator throws" but "a caller
    // that had no business touching a price blows up anyway". These assert the caller
    // survives a stored price it never wrote, which is what silently blocked stock
    // movements behind an invalid price.

    @Test
    fun `updateStock survives an out-of-band stored price`() = runTest {
        coEvery { menuDao.getItemById(1L, 0L) } returns item(price = "0.50")
        val saved = slot<MenuItemEntity>()
        coEvery { menuDao.updateItem(capture(saved)) } just Runs

        repository.updateStock(1L, "-3")

        coVerify(exactly = 1) { menuDao.updateItem(any()) }
        assertEquals("0.50", saved.captured.basePrice)
    }

    @Test
    fun `updateVariantStock survives an out-of-band stored variant price`() = runTest {
        coEvery { menuDao.getVariantById(7L, 0L) } returns ItemVariantEntity(
            id = 7L,
            menuItemId = 1L,
            variantName = "Small",
            price = "0.50"
        )
        val saved = slot<ItemVariantEntity>()
        coEvery { menuDao.updateVariant(capture(saved)) } just Runs

        repository.updateVariantStock(7L, "-3")

        coVerify(exactly = 1) { menuDao.updateVariant(any()) }
        assertEquals("0.50", saved.captured.price)
    }

    // Counterweight: skipping normalization for untouched fields must not become skipping
    // validation for the field the caller actually changed.
    @Test(expected = IllegalArgumentException::class)
    fun `updateItem still rejects an invalid price when basePrice is the changed field`() = runTest {
        coEvery { menuDao.getItemById(1L, 0L) } returns item(price = "250")
        coEvery { menuDao.updateItem(any()) } just Runs

        repository.updateItem(item(price = "0.50"))
    }

    // --- Explicit variant mode -------------------------------------------------
    // has_variants must be derived from the variant table, never inferred from a blank or
    // zero base price. See docs/design/MENU_ITEM_MODEL_INDIA_FIT_GAP.md section 6.

    private fun variant(itemId: Long = 1L, id: Long = 7L) = ItemVariantEntity(
        id = id,
        menuItemId = itemId,
        variantName = "Small",
        price = "150"
    )

    @Test
    fun `insertVariant promotes the parent to a variant container`() = runTest {
        coEvery { menuDao.insertVariant(any()) } returns 7L
        coEvery { menuDao.countLiveVariants(1L, 0L) } returns 1
        coEvery { menuDao.getItemById(1L, 0L) } returns item(price = "150")
        coEvery { menuDao.updateItemHasVariantsFlag(any(), any(), any(), any()) } just Runs

        repository.insertVariant(variant())

        coVerify(exactly = 1) { menuDao.updateItemHasVariantsFlag(1L, true, any(), 0L) }
    }

    @Test
    fun `deleting the last variant demotes the parent back to a simple item`() = runTest {
        coEvery { menuDao.markVariantDeleted(any(), any(), any()) } just Runs
        coEvery { menuDao.countLiveVariants(1L, 0L) } returns 0
        coEvery { menuDao.getItemById(1L, 0L) } returns item().copy(hasVariants = true)
        coEvery { menuDao.updateItemHasVariantsFlag(any(), any(), any(), any()) } just Runs

        repository.deleteVariant(variant())

        coVerify(exactly = 1) { menuDao.updateItemHasVariantsFlag(1L, false, any(), 0L) }
    }

    @Test
    fun `an unchanged flag is not rewritten`() = runTest {
        coEvery { menuDao.insertVariant(any()) } returns 7L
        coEvery { menuDao.countLiveVariants(1L, 0L) } returns 1
        coEvery { menuDao.getItemById(1L, 0L) } returns item().copy(hasVariants = true)

        repository.insertVariant(variant())

        coVerify(exactly = 0) { menuDao.updateItemHasVariantsFlag(any(), any(), any(), any()) }
        // The variant itself must still be persisted.
        coVerify(exactly = 1) { menuDao.insertVariant(any()) }
    }

    // The flag is derived data, so recomputing it must never roll back an unrelated field
    // the user just edited. This is why the repository writes it with a narrow targeted
    // UPDATE rather than a whole-row updateItem().
    @Test
    fun `refreshing the flag never rewrites the whole item row`() = runTest {
        coEvery { menuDao.insertVariant(any()) } returns 7L
        coEvery { menuDao.countLiveVariants(1L, 0L) } returns 1
        coEvery { menuDao.getItemById(1L, 0L) } returns item(price = "999")
        coEvery { menuDao.updateItemHasVariantsFlag(any(), any(), any(), any()) } just Runs

        repository.insertVariant(variant())

        coVerify(exactly = 0) { menuDao.updateItem(any()) }
    }
}