package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.khanabook.lite.pos.core.database.AppDatabase
import com.khanabook.lite.pos.core.database.DatabaseProvider
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.CategoryEntity
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.menu.data.MenuItemEntity
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.data.MenuItemPullDto
import com.khanabook.lite.pos.feature.sync.data.MenuItemSyncDto
import com.khanabook.lite.pos.feature.sync.domain.MasterSyncProcessor
import com.khanabook.lite.pos.feature.sync.domain.ServerIdMapping
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * WHY THIS TEST EXISTS
 * ====================
 * A dish carries TWO stars, and they are not the same field:
 *
 *   - `isFavourite` is the SHOP's pin. Owner-set in web admin, delivered on the pull, never
 *     asserted by a terminal (the server restores its own value on every push path).
 *   - `localFavourite` is THIS TILL's own shortcut, toggled at the counter. It is deliberately
 *     device-local, so it is absent from MenuItemSyncDto and the server has no column for it.
 *
 * The pull rebuilds every dish row from the wire (`MenuItemEntity(...)` built field by field,
 * then handed to `menuDao.upsertSyncedMenuItems`). `localFavourite` is never on the wire, so
 * nothing in that constructor call can supply it: without the carry-over,
 * MasterSyncProcessor read the constructor default and the next sync silently unstars whatever
 * a cashier had starred - the same failure class that a category rebuild used to cause for the
 * shop's pin (bb94f328). The star is also the sort key of every category list
 * (`is_favourite DESC, local_favourite DESC, name ASC`), so the dish visibly jumps back to
 * alphabetical order mid-shift.
 *
 * The DTO test at the bottom is the other half of the contract: the moment anyone adds
 * `localFavourite` to MenuItemSyncDto, every till starts pushing a column the server does not
 * have - which is how a device-local convenience turns into a rejected batch inside billing.
 */
class MasterSyncProcessorLocalFavouriteTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var menuDao: MenuDao
    private lateinit var db: AppDatabase

    private val RESTAURANT = 55L
    private val CATEGORY_SERVER_ID = 10L
    private val ITEM_ID = 77L

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

        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { prefs.getLong("restaurant_id", any()) } returns RESTAURANT
        every { prefs.getString(any(), any()) } returns null

        val databaseProvider = mockk<DatabaseProvider>(relaxed = true)
        db = mockk(relaxed = true)
        every { databaseProvider.getDatabase() } returns db

        menuDao = mockk(relaxed = true)
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        val userDao = mockk<UserDao>(relaxed = true)

        // Seed a category so the dish is not rejected for an unknown destination.
        coEvery { categoryDao.getAllCategoryServerIds(RESTAURANT) } returns
            listOf(ServerIdMapping(id = CATEGORY_SERVER_ID, serverId = CATEGORY_SERVER_ID))
        coEvery { userDao.getAllUsersOnce() } returns emptyList()

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = mockk<RestaurantDao>(relaxed = true),
            userDao = userDao,
            categoryDao = categoryDao,
            menuDao = menuDao,
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = SessionManager(context),
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

    /** What a pull of this restaurant's menu looks like on the wire. */
    private fun masterData() = MasterSyncResponse(
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
                localId = ITEM_ID,
                serverId = ITEM_ID,
                serverCategoryId = CATEGORY_SERVER_ID,
                name = "Biryani",
                basePrice = java.math.BigDecimal("300"),
                restaurantId = RESTAURANT,
                deviceId = "another-till",
                // The shop has pinned this dish; the counter's own star is separate and is
                // deliberately not on the wire at all.
                isFavourite = true
            )
        )
    )

    /** The row this device already holds, as the counters' own star would have left it. */
    private fun storedItem(localFavourite: Boolean) = MenuItemEntity(
        id = ITEM_ID,
        categoryId = CATEGORY_SERVER_ID,
        name = "Biryani",
        basePrice = "300",
        restaurantId = RESTAURANT,
        serverId = ITEM_ID,
        serverCategoryId = CATEGORY_SERVER_ID,
        localFavourite = localFavourite
    )

    private suspend fun runAndCaptureResolvedItem(existing: List<MenuItemEntity>): MenuItemEntity {
        coEvery { menuDao.getAllMenuItemsOnce(RESTAURANT) } returns existing
        val captured = slot<List<MenuItemEntity>>()
        coEvery { menuDao.upsertSyncedMenuItems(capture(captured)) } returns Unit
        processor.insertMasterData(masterData())
        return captured.captured.single { it.id == ITEM_ID }
    }

    @Test
    fun `a pull rebuild keeps this till's own star`() = runTest {
        val item = runAndCaptureResolvedItem(existing = listOf(storedItem(localFavourite = true)))

        assertTrue(
            "the till's own star must survive the rebuild, or the next sync unstars the dish",
            item.localFavourite
        )
        // ... and the shop's pin, which IS on the wire, still lands alongside it.
        assertTrue(item.isFavourite)
    }

    @Test
    fun `a dish this till never starred is not starred by the pull`() = runTest {
        // The other direction, so the fix cannot be "always carry true": an unstarred row must
        // stay unstarred, and a dish pulled for the first time must not inherit a star.
        val existing = runAndCaptureResolvedItem(existing = listOf(storedItem(localFavourite = false)))
        assertFalse(existing.localFavourite)

        val pulled = runAndCaptureResolvedItem(existing = emptyList())
        assertFalse(
            "a brand-new dish has no till star to inherit",
            pulled.localFavourite
        )
    }

    @Test
    fun `the shop pinning a dish does not star it for this till`() = runTest {
        // localFavourite is never derived from isFavourite: the shop pinning a dish is not the
        // counter starring it, and conflating them would make the till star impossible to clear.
        val item = runAndCaptureResolvedItem(existing = listOf(storedItem(localFavourite = false)))

        assertTrue(item.isFavourite)
        assertFalse(item.localFavourite)
    }

    @Test
    fun `the push DTO carries neither star`() {
        val dtoFields = MenuItemSyncDto::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()

        assertFalse(
            "localFavourite is device-local: pushing it would name a column the server has no " +
                "counterpart for, and the shop's pin is server-owned so a device asserting it " +
                "could only ever be ignored. Either way the round-trip changes nothing while " +
                "widening what this device claims to own.",
            dtoFields.contains("localFavourite") || dtoFields.contains("isFavourite")
        )
    }
}
