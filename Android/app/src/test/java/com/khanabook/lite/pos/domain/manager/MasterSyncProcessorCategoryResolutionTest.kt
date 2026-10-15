package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.feature.sync.domain.MasterSyncProcessor
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.khanabook.lite.pos.core.database.AppDatabase
import com.khanabook.lite.pos.core.database.DatabaseProvider
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemCategoryRepairEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.data.MenuItemPullDto
import com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * A pulled dish's destination category only comes from the server through the
 * server-category → local-category map.
 *
 * The pull used to resolve it as `categoryIdMap[serverCategoryId] ?: serverCategoryId`, i.e. it
 * fell back to the raw SERVER number and then used it as a LOCAL category id. A category that
 * merely happened to carry that number locally — an unsynced category created on the till is the
 * obvious source — then received the dish. The row looked completely healthy afterwards: right
 * name, right price, wrong category, and the local-move mask the device pushes next would carry
 * the wrong category back to the server. Falling back to `categoryId` had the same shape, because
 * that field can hold a *foreign device's* local id.
 *
 * These tests pin the replacement rule: resolve through the map, otherwise keep the category the
 * local row already holds, otherwise skip the row. Never invent an id.
 */
class MasterSyncProcessorCategoryResolutionTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var menuDao: MenuDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var userDao: UserDao
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var db: AppDatabase

    private val RESTAURANT = 55L
    private val OTHER_DEVICE = "other-device"

    /** "Mains": the category the dish sits in today, known on this device as local id 10. */
    private val MAINS_SERVER_ID = 10L
    private val MAINS_LOCAL_ID = 10L

    /** "Beverages": the destination that has NOT reached this device yet. */
    private val BEVERAGES_SERVER_ID = 99L

    /**
     * A category created on this till and not pushed yet, so it has no server id. Its LOCAL id
     * collides with [BEVERAGES_SERVER_ID] — which is exactly what made the old fallback place the
     * dish in the wrong category instead of dropping it.
     */
    private val UNSYNCED_LOCAL_CATEGORY_ID = 99L

    private val ITEM_SERVER_ID = 100L

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0

        mockkStatic("androidx.room.RoomDatabaseKt")

        menuDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        val restaurantDao = mockk<RestaurantDao>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getLong("restaurant_id", any()) } returns RESTAURANT
        every { prefs.getString(any(), any()) } returns null
        val sessionManager = SessionManager(context)

        databaseProvider = mockk(relaxed = true)
        db = mockk(relaxed = true)
        every { databaseProvider.getDatabase() } returns db

        coEvery { userDao.getAllUsersOnce() } returns emptyList()

        // The only category on this device that carries a server id today.
        coEvery { categoryDao.getAllCategoryServerIds(RESTAURANT) } returns
            listOf(ServerIdMapping(id = MAINS_LOCAL_ID, serverId = MAINS_SERVER_ID))
        // ... plus the unsynced local category whose id collides with the server's Beverages id.
        coEvery { categoryDao.getAllCategoriesOnce(RESTAURANT) } returns listOf(
            CategoryEntity(
                id = MAINS_LOCAL_ID,
                name = "Mains",
                isVeg = true,
                restaurantId = RESTAURANT,
                serverId = MAINS_SERVER_ID
            ),
            CategoryEntity(
                id = UNSYNCED_LOCAL_CATEGORY_ID,
                name = "Counter Favourites",
                isVeg = true,
                restaurantId = RESTAURANT,
                serverId = null
            )
        )

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = restaurantDao,
            userDao = userDao,
            categoryDao = categoryDao,
            menuDao = menuDao,
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = sessionManager,
            permissionManager = mockk<PermissionManager>(relaxed = true)
        )

        val txnBlock = slot<suspend () -> Any?>()
        coEvery { db.withTransaction(capture(txnBlock)) } coAnswers { txnBlock.captured.invoke() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun localItem(categoryId: Long) = MenuItemEntity(
        id = ITEM_SERVER_ID,
        categoryId = categoryId,
        name = "Biryani",
        basePrice = "300",
        restaurantId = RESTAURANT,
        deviceId = OTHER_DEVICE,
        isSynced = true,
        serverId = ITEM_SERVER_ID,
        updatedAt = 1_000L
    )

    private fun masterData(
        destinationServerCategoryId: Long?,
        remoteCategoryId: Long?,
        name: String = "Biryani Large"
    ) = MasterSyncResponse(
        categories = listOf(
            CategoryEntity(
                id = MAINS_LOCAL_ID,
                name = "Mains",
                isVeg = true,
                restaurantId = RESTAURANT,
                serverId = MAINS_SERVER_ID
            )
        ),
        menuItems = listOf(
            MenuItemPullDto(
                serverId = ITEM_SERVER_ID,
                serverCategoryId = destinationServerCategoryId,
                categoryId = remoteCategoryId,
                name = name,
                basePrice = BigDecimal("320"),
                restaurantId = RESTAURANT,
                deviceId = OTHER_DEVICE,
                updatedAt = 2_000L,
                serverUpdatedAt = 2_000L
            )
        )
    )

    private suspend fun runAndCaptureUpsert(masterData: MasterSyncResponse): List<MenuItemEntity> {
        val captured = slot<List<MenuItemEntity>>()
        coEvery { menuDao.upsertSyncedMenuItems(capture(captured)) } returns Unit
        processor.insertMasterData(masterData)
        return captured.captured
    }

    @Test
    fun `dish moved to a category this device has not pulled keeps its current category`() = runTest {
        // The dish is already here, in Mains. The server says it now belongs to Beverages (server
        // id 99), but this device has never seen that category - id 99 locally is a different,
        // unsynced category of its own.
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))

        val items = runAndCaptureUpsert(
            masterData(destinationServerCategoryId = BEVERAGES_SERVER_ID, remoteCategoryId = BEVERAGES_SERVER_ID)
        )

        val item = items.single { it.id == ITEM_SERVER_ID }
        assertEquals(
            "the dish must stay in the category its row already holds",
            MAINS_LOCAL_ID,
            item.categoryId
        )
        assertEquals(
            "everything else about the row still syncs",
            "Biryani Large",
            item.name
        )
    }

    @Test
    fun `new dish is not placed in a local category that merely shares the server number`() = runTest {
        // Nothing local carries this dish, so there is no category to keep and none may be guessed
        // - even though local category 99 exists and would have matched the old fallback.
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns emptyList()

        val items = runAndCaptureUpsert(
            masterData(destinationServerCategoryId = BEVERAGES_SERVER_ID, remoteCategoryId = BEVERAGES_SERVER_ID)
        )

        assertTrue(
            "an unresolvable destination must skip the row, never invent a placement",
            items.none { it.id == ITEM_SERVER_ID }
        )
    }

    @Test
    fun `dish whose destination category is known lands there`() = runTest {
        // Beverages has been pulled since: server id 99 is local id 5.
        val beveragesLocalId = 5L
        coEvery { categoryDao.getAllCategoryServerIds(RESTAURANT) } returns listOf(
            ServerIdMapping(id = MAINS_LOCAL_ID, serverId = MAINS_SERVER_ID),
            ServerIdMapping(id = beveragesLocalId, serverId = BEVERAGES_SERVER_ID)
        )
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))

        val items = runAndCaptureUpsert(
            masterData(destinationServerCategoryId = BEVERAGES_SERVER_ID, remoteCategoryId = BEVERAGES_SERVER_ID)
        )

        assertEquals(
            "a real move still applies, through the server-to-local mapping",
            beveragesLocalId,
            items.single { it.id == ITEM_SERVER_ID }.categoryId
        )
    }

    @Test
    fun `pulled row without a server category id keeps the category its local row has`() = runTest {
        // A row written before the server carried serverCategoryId. Its categoryId is not usable
        // either (it can be a foreign device's local id), so the only safe answer is the one this
        // device can prove: what the row already holds.
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))

        val items = runAndCaptureUpsert(
            masterData(destinationServerCategoryId = null, remoteCategoryId = null)
        )

        val item = items.single { it.id == ITEM_SERVER_ID }
        assertEquals(MAINS_LOCAL_ID, item.categoryId)
        assertEquals("Biryani Large", item.name)
    }

    // ── parked moves (A5): durable park + repair ────────────────────────────────

    @Test
    fun `dish parked by a pull records where it was meant to go`() = runTest {
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))
        val parked = slot<List<MenuItemCategoryRepairEntity>>()
        coEvery { menuDao.upsertCategoryRepairs(capture(parked)) } returns Unit

        runAndCaptureUpsert(
            masterData(destinationServerCategoryId = BEVERAGES_SERVER_ID, remoteCategoryId = BEVERAGES_SERVER_ID)
        )

        val repair = parked.captured.single()
        assertEquals(ITEM_SERVER_ID, repair.itemLocalId)
        assertEquals(BEVERAGES_SERVER_ID, repair.destinationServerCategoryId)
        assertEquals(RESTAURANT, repair.restaurantId)
    }

    @Test
    fun `parked dish is moved once its destination category arrives`() = runTest {
        // Beverages has been pulled since: server id 99 is local id 5.
        val beveragesLocalId = 5L
        coEvery { categoryDao.getAllCategoryServerIds(RESTAURANT) } returns listOf(
            ServerIdMapping(id = MAINS_LOCAL_ID, serverId = MAINS_SERVER_ID),
            ServerIdMapping(id = beveragesLocalId, serverId = BEVERAGES_SERVER_ID)
        )
        coEvery { menuDao.getCategoryRepairs(RESTAURANT) } returns listOf(
            MenuItemCategoryRepairEntity(
                restaurantId = RESTAURANT,
                itemLocalId = ITEM_SERVER_ID,
                destinationServerCategoryId = BEVERAGES_SERVER_ID
            )
        )
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))
        coEvery { menuDao.repairItemCategory(any(), any(), any()) } returns 1

        processor.insertMasterData(MasterSyncResponse(categories = listOf(localCategory())))

        coVerify(exactly = 1) { menuDao.repairItemCategory(ITEM_SERVER_ID, beveragesLocalId, RESTAURANT) }
        coVerify(exactly = 1) { menuDao.deleteCategoryRepairs(RESTAURANT, listOf(ITEM_SERVER_ID)) }
    }

    @Test
    fun `parked dish is left alone while its destination is still unknown`() = runTest {
        coEvery { menuDao.getCategoryRepairs(RESTAURANT) } returns listOf(
            MenuItemCategoryRepairEntity(
                restaurantId = RESTAURANT,
                itemLocalId = ITEM_SERVER_ID,
                destinationServerCategoryId = BEVERAGES_SERVER_ID
            )
        )
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))

        processor.insertMasterData(MasterSyncResponse(categories = listOf(localCategory())))

        coVerify(exactly = 0) { menuDao.repairItemCategory(any(), any(), any()) }
        coVerify(exactly = 0) { menuDao.deleteCategoryRepairs(any(), any()) }
    }

    @Test
    fun `repair for a dish this device no longer has is dropped`() = runTest {
        coEvery { menuDao.getCategoryRepairs(RESTAURANT) } returns listOf(
            MenuItemCategoryRepairEntity(
                restaurantId = RESTAURANT,
                itemLocalId = 7_777L,
                destinationServerCategoryId = BEVERAGES_SERVER_ID
            )
        )
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns listOf(localItem(MAINS_LOCAL_ID))

        processor.insertMasterData(MasterSyncResponse(categories = listOf(localCategory())))

        coVerify(exactly = 0) { menuDao.repairItemCategory(any(), any(), any()) }
        coVerify(exactly = 1) { menuDao.deleteCategoryRepairs(RESTAURANT, listOf(7_777L)) }
    }

    private fun localCategory() = CategoryEntity(
        id = MAINS_LOCAL_ID,
        name = "Mains",
        isVeg = true,
        restaurantId = RESTAURANT,
        serverId = MAINS_SERVER_ID
    )
}
