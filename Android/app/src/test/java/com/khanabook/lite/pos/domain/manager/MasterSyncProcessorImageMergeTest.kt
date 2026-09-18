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
import com.khanabook.lite.pos.feature.inventory.data.InventoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.data.MenuItemPullDto
import com.khanabook.lite.pos.feature.sync.domain.MenuItemImageInfo
import com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Bug fix: a menu item's server-owned photo (image_url / image_version) must survive a
 * master-sync pull. The Room full @Update previously nulled image_url on every pull because
 * the pull DTO omitted the image fields. insertMasterData now reconciles by imageVersion:
 * higher version wins; on a tie, the existing local url is kept.
 */
class MasterSyncProcessorImageMergeTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var menuDao: MenuDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var restaurantDao: RestaurantDao
    private lateinit var userDao: UserDao
    private lateinit var sessionManager: SessionManager
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var db: AppDatabase

    private val RESTAURANT = 55L
    private val CATEGORY_SERVER_ID = 10L
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

        // Make androidx.room.withTransaction { } execute its block inline.
        mockkStatic("androidx.room.RoomDatabaseKt")

        menuDao = mockk(relaxed = true)
        categoryDao = mockk(relaxed = true)
        restaurantDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getLong("restaurant_id", any()) } returns RESTAURANT
        every { prefs.getString(any(), any()) } returns null
        sessionManager = SessionManager(context)

        databaseProvider = mockk(relaxed = true)
        db = mockk(relaxed = true)

        every { databaseProvider.getDatabase() } returns db

        // Seed a category so the menu item is not rejected for an unknown category.
        coEvery { categoryDao.getAllCategoryServerIds(RESTAURANT) } returns
            listOf(ServerIdMapping(id = CATEGORY_SERVER_ID, serverId = CATEGORY_SERVER_ID))
        coEvery { userDao.getAllUsersOnce() } returns emptyList()

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = restaurantDao,
            userDao = userDao,
            categoryDao = categoryDao,
            menuDao = menuDao,
            inventoryDao = mockk<InventoryDao>(relaxed = true),
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = sessionManager,
            permissionManager = mockk(relaxed = true)
        )

        // withTransaction { block() } -> just run the block.
        val txnBlock = slot<suspend () -> Any?>()
        coEvery { db.withTransaction(capture(txnBlock)) } coAnswers { txnBlock.captured.invoke() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun masterDataWith(remoteUrl: String?, remoteVersion: Int?): MasterSyncResponse =
        MasterSyncResponse(
            categories = listOf(
                CategoryEntity(
                    id = CATEGORY_SERVER_ID,
                    name = "Mains",
                    isVeg = true,
                    restaurantId = RESTAURANT,
                    serverId = CATEGORY_SERVER_ID
                )
            ),
            menuItems = listOf(
                MenuItemPullDto(
                    serverId = ITEM_SERVER_ID,
                    serverCategoryId = CATEGORY_SERVER_ID,
                    name = "Biryani",
                    basePrice = BigDecimal("300"),
                    restaurantId = RESTAURANT,
                    deviceId = "other-device",
                    imageUrl = remoteUrl,
                    imageVersion = remoteVersion
                )
            )
        )

    private suspend fun runAndCaptureResolvedItem(masterData: MasterSyncResponse): MenuItemEntity {
        val captured = slot<List<MenuItemEntity>>()
        coEvery { menuDao.upsertSyncedMenuItems(capture(captured)) } returns Unit
        processor.insertMasterData(masterData)
        return captured.captured.single { it.id == ITEM_SERVER_ID }
    }

    @Test
    fun `remote imageVersion lower than local keeps local imageUrl`() = runTest {
        coEvery { menuDao.getAllMenuItemImageInfo(RESTAURANT) } returns
            listOf(MenuItemImageInfo(id = ITEM_SERVER_ID, imageUrl = "local.jpg", imageVersion = 5))

        val item = runAndCaptureResolvedItem(masterDataWith(remoteUrl = "remote.jpg", remoteVersion = 2))

        assertEquals("local.jpg", item.imageUrl)
        assertEquals(5, item.imageVersion)
    }

    @Test
    fun `remote imageVersion higher than local takes remote imageUrl`() = runTest {
        coEvery { menuDao.getAllMenuItemImageInfo(RESTAURANT) } returns
            listOf(MenuItemImageInfo(id = ITEM_SERVER_ID, imageUrl = "local.jpg", imageVersion = 2))

        val item = runAndCaptureResolvedItem(masterDataWith(remoteUrl = "remote.jpg", remoteVersion = 7))

        assertEquals("remote.jpg", item.imageUrl)
        assertEquals(7, item.imageVersion)
    }

    @Test
    fun `remote equal version with null url keeps local url`() = runTest {
        coEvery { menuDao.getAllMenuItemImageInfo(RESTAURANT) } returns
            listOf(MenuItemImageInfo(id = ITEM_SERVER_ID, imageUrl = "local.jpg", imageVersion = 3))

        // Real regression path: the pull DTO omits the image fields, so Gson yields
        // null (treated as version 0). 0 < local 3, so reconciliation keeps the local
        // url and the local (higher) version. This is exactly what nulled image_url before.
        val item = runAndCaptureResolvedItem(masterDataWith(remoteUrl = null, remoteVersion = null))

        assertEquals("local.jpg", item.imageUrl)
        assertEquals(3, item.imageVersion)
    }
}