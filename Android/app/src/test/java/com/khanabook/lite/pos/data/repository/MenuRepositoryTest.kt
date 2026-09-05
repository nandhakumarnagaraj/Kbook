package com.khanabook.lite.pos.data.repository

import androidx.work.WorkManager
import android.content.Context
import android.content.SharedPreferences
import com.khanabook.lite.pos.data.local.dao.MenuDao
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.domain.manager.PermissionManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import io.mockk.Runs
import io.mockk.coEvery
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
}
