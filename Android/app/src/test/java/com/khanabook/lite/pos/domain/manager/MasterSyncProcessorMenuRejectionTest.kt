package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.local.dao.BillDao
import com.khanabook.lite.pos.data.local.dao.CategoryDao
import com.khanabook.lite.pos.data.local.dao.InventoryDao
import com.khanabook.lite.pos.data.local.dao.MenuDao
import com.khanabook.lite.pos.data.local.dao.PrinterProfileDao
import com.khanabook.lite.pos.data.local.dao.RestaurantDao
import com.khanabook.lite.pos.data.local.dao.UserDao
import com.khanabook.lite.pos.data.local.entity.MenuItemEntity
import com.khanabook.lite.pos.data.local.entity.SyncQuarantineEntity
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.PushSyncResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P2.6: a menu row the server rejects on permission grounds must (1) stop re-pushing
 * every cycle and (2) surface to the user. Product choice: quarantine (non-destructive).
 */
class MasterSyncProcessorMenuRejectionTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var billDao: BillDao
    private lateinit var menuDao: MenuDao
    private lateinit var sessionManager: SessionManager

    private val RESTAURANT = 55L

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        billDao = mockk(relaxed = true)
        menuDao = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = mockk<DatabaseProvider>(relaxed = true),
            billDao = billDao,
            restaurantDao = mockk<RestaurantDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            menuDao = menuDao,
            inventoryDao = mockk<InventoryDao>(relaxed = true),
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = sessionManager,
            permissionManager = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ── classification ────────────────────────────────────────────────────────

    @Test
    fun `permission reasons are classified as permanent`() {
        assertTrue(processor.isPermissionRejection("Not permitted to change the price of this item (PERMISSION_NOT_GRANTED)"))
        assertTrue(processor.isPermissionRejection("Not permitted to change item availability (REVOKED_AFTER_CREATION)"))
        assertTrue(processor.isPermissionRejection("Cannot authorize menu change: unknown user"))
    }

    @Test
    fun `transient and blank reasons are NOT permanent`() {
        assertFalse(processor.isPermissionRejection("Incoming record is older than the server record"))
        assertFalse(processor.isPermissionRejection(""))
        assertFalse(processor.isPermissionRejection(null))
    }

    // ── quarantine handler ──────────────────────────────────────────────────────

    @Test
    fun `quarantineRejectedMenuItems records reason and stops re-push`() = runTest {
        val item = MenuItemEntity(id = 7L, categoryId = 1L, name = "Biryani", basePrice = "300", restaurantId = RESTAURANT)
        coEvery { menuDao.getItemById(7L, RESTAURANT) } returns item
        val quarantineSlot = slot<SyncQuarantineEntity>()
        coEvery { billDao.upsertSyncQuarantineRecord(capture(quarantineSlot)) } just Runs
        val syncedSlot = slot<List<Long>>()
        coEvery { menuDao.markMenuItemsAsSynced(capture(syncedSlot), RESTAURANT) } just Runs

        val count = processor.quarantineRejectedMenuItems(
            mapOf(7L to "Not permitted to change the price of this item (PERMISSION_NOT_GRANTED)"),
            RESTAURANT
        )

        assertEquals(1, count)
        // (1) Visible signal: a quarantine record with the item name + server reason.
        assertEquals("menu_item", quarantineSlot.captured.childEntityType)
        assertEquals(7L, quarantineSlot.captured.childLocalId)
        assertEquals("Biryani", quarantineSlot.captured.childDisplayName)
        assertTrue(quarantineSlot.captured.syncFailureReason!!.contains("Not permitted"))
        // (2) Re-push stopped: the row is marked synced so getUnsyncedMenuItems drops it.
        assertEquals(listOf(7L), syncedSlot.captured)
    }

    // ── end-to-end via pushBatches ───────────────────────────────────────────────

    @Test
    fun `permission-rejected batch does not throw and invokes quarantine callback`() = runTest {
        // A push that permanently rejects the only row on permission grounds.
        val push: suspend (List<Long>) -> PushSyncResponse = {
            PushSyncResponse(
                successfulLocalIds = emptyList(),
                failedLocalIds = listOf(7L),
                failedReasons = mapOf(7L to "Not permitted to change the price of this item (PERMISSION_NOT_GRANTED)")
            )
        }
        var callbackReceived: Map<Long, String>? = null

        // Must NOT throw SyncConflictException even though every row "failed":
        // permanent rejections are peeled off before the throw decision.
        val result = processor.pushBatches(
            label = "menu items",
            records = listOf(7L),
            localId = { it },
            transform = { it },
            push = push,
            markSynced = { },
            onPermanentlyRejected = { rejected -> callbackReceived = rejected }
        )

        assertTrue(result.isEmpty())
        assertEquals(mapOf(7L to "Not permitted to change the price of this item (PERMISSION_NOT_GRANTED)"), callbackReceived)
    }

    @Test
    fun `LWW-older rejection still flows through conflict path (not quarantined as permanent)`() = runTest {
        val push: suspend (List<Long>) -> PushSyncResponse = {
            PushSyncResponse(
                successfulLocalIds = emptyList(),
                failedLocalIds = listOf(8L),
                failedReasons = mapOf(8L to "Incoming record is older than the server record")
            )
        }
        var callbackReceived: Map<Long, String>? = null
        var threw = false
        try {
            processor.pushBatches(
                label = "menu items",
                records = listOf(8L),
                localId = { it },
                transform = { it },
                push = push,
                markSynced = { },
                onPermanentlyRejected = { rejected -> callbackReceived = rejected }
            )
        } catch (e: Exception) {
            threw = true
        }
        // Not permanent → callback not invoked; the all-failed batch throws as before.
        assertTrue(threw)
        assertEquals(null, callbackReceived)
    }
}
