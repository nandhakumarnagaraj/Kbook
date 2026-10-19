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
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.domain.MasterSyncProcessor
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
import org.junit.Before
import org.junit.Test

/**
 * A master-sync pull rebuilds bill items from the server, which never carries
 * sentToKot (device-local). Without preserving the local flag, every pull reset
 * already-sent items to "not sent", so the next print re-sent them to the kitchen.
 */
class MasterSyncProcessorKotFlagTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var billDao: BillDao
    private lateinit var db: AppDatabase

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
        every { prefs.getLong("restaurant_id", any()) } returns RESTAURANT_ID
        every { prefs.getString(any(), any()) } returns null

        val databaseProvider = mockk<DatabaseProvider>(relaxed = true)
        db = mockk(relaxed = true)
        every { databaseProvider.getDatabase() } returns db
        val txnBlock = slot<suspend () -> Any?>()
        coEvery { db.withTransaction(capture(txnBlock)) } coAnswers { txnBlock.captured.invoke() }

        billDao = mockk(relaxed = true)
        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = billDao,
            restaurantDao = mockk<RestaurantDao>(relaxed = true),
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            menuDao = mockk<MenuDao>(relaxed = true),
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = SessionManager(context),
            permissionManager = mockk<PermissionManager>(relaxed = true)
        )
        coEvery { billDao.getAllBillServerIds(RESTAURANT_ID) } returns
            listOf(ServerIdMapping(id = LOCAL_BILL_ID, serverId = SERVER_BILL_ID))
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
        unmockkStatic(Log::class)
    }

    @Test
    fun `pull keeps sentToKot true for an item already sent to the kitchen`() = runTest {
        coEvery { billDao.getBillItemsByServerIds(listOf(SERVER_ITEM_ID), RESTAURANT_ID) } returns
            listOf(billItem(serverId = SERVER_ITEM_ID, sentToKot = true))
        val upserted = slot<List<BillItemEntity>>()

        processor.insertMasterData(
            MasterSyncResponse(billItems = listOf(remoteItem(SERVER_ITEM_ID)))
        )

        coVerify { billDao.upsertSyncedBillItems(capture(upserted)) }
        assertEquals(true, upserted.captured.single().sentToKot)
    }

    @Test
    fun `pull defaults sentToKot to false for a new remote item`() = runTest {
        coEvery { billDao.getBillItemsByServerIds(listOf(SERVER_ITEM_ID), RESTAURANT_ID) } returns emptyList()
        val upserted = slot<List<BillItemEntity>>()

        processor.insertMasterData(
            MasterSyncResponse(billItems = listOf(remoteItem(SERVER_ITEM_ID)))
        )

        coVerify { billDao.upsertSyncedBillItems(capture(upserted)) }
        assertEquals(false, upserted.captured.single().sentToKot)
    }

    private fun remoteItem(serverId: Long) = BillItemEntity(
        billId = 0L,
        menuItemId = null,
        itemName = "Paneer",
        price = "100.00",
        quantity = 1,
        itemTotal = "100.00",
        restaurantId = RESTAURANT_ID,
        deviceId = "dev-A",
        serverId = serverId,
        serverBillId = SERVER_BILL_ID
    )

    private fun billItem(serverId: Long, sentToKot: Boolean) = remoteItem(serverId).copy(sentToKot = sentToKot)

    private companion object {
        const val RESTAURANT_ID = 55L
        const val LOCAL_BILL_ID = 1L
        const val SERVER_BILL_ID = 500L
        const val SERVER_ITEM_ID = 900L
    }
}
