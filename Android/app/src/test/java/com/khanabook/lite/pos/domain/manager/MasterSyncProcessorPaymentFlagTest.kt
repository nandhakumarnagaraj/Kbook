package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.khanabook.lite.pos.core.database.AppDatabase
import com.khanabook.lite.pos.core.database.DatabaseProvider
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.domain.MasterSyncProcessor
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

/**
 * Bug: the POS lost the owner's "Easebuzz Online" switch on every master sync.
 *
 * The switch is owner-gated and confirmed server-side before it is stored
 * (RestaurantRepository.savePaymentProfile -> PUT payment-config/easebuzz), so the
 * server row is the source of truth and the device row is its mirror. The pull half
 * of master sync rebuilt the profile row through `RestaurantProfileEntity(...)`,
 * which never named `easebuzzEnabled`, and `insertSyncedRestaurantProfiles` is an
 * @Insert(REPLACE) - so the column fell back to its `0` default and the flag was
 * wiped on the next sync. The owner's symptom: "Send Payment Link" disappears from
 * billing (PaymentModeManager drops PAYMENT_LINK) and the payment settings toggle
 * flips itself off, while the server still reports the switch as on.
 */
class MasterSyncProcessorPaymentFlagTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var restaurantDao: RestaurantDao
    private lateinit var db: AppDatabase
    private lateinit var databaseProvider: DatabaseProvider

    private val RESTAURANT = 55L

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

        databaseProvider = mockk(relaxed = true)
        db = mockk(relaxed = true)
        every { databaseProvider.getDatabase() } returns db

        restaurantDao = mockk(relaxed = true)

        processor = MasterSyncProcessor(
            api = mockk<KhanaBookApi>(relaxed = true),
            databaseProvider = databaseProvider,
            billDao = mockk<BillDao>(relaxed = true),
            restaurantDao = restaurantDao,
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            menuDao = mockk<MenuDao>(relaxed = true),
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

    private fun localProfile(easebuzz: Boolean) = RestaurantProfileEntity(
        id = RESTAURANT,
        restaurantId = RESTAURANT,
        easebuzzEnabled = easebuzz
    )

    /** The server's profile row, exactly as the pull deserializes it. */
    private fun pulledProfile(easebuzz: Boolean) = RestaurantProfileEntity(
        id = RESTAURANT,
        restaurantId = RESTAURANT,
        shopName = "Test Restaurant",
        easebuzzEnabled = easebuzz
    )

    private suspend fun pullAndCaptureStoredProfile(
        localEasebuzz: Boolean,
        remoteEasebuzz: Boolean
    ): RestaurantProfileEntity {
        val local = localProfile(localEasebuzz)
        coEvery { restaurantDao.getProfile(RESTAURANT) } returns local
        coEvery { restaurantDao.getProfile() } returns local

        val stored = slot<List<RestaurantProfileEntity>>()
        coEvery { restaurantDao.insertSyncedRestaurantProfiles(capture(stored)) } returns Unit

        processor.insertMasterData(
            MasterSyncResponse(profiles = listOf(pulledProfile(remoteEasebuzz)))
        )

        return stored.captured.single()
    }

    @Test
    fun `a profile pulled from the server restores the owner's Easebuzz switch`() = runTest {
        // The server still has the switch on: the device row must carry it, otherwise
        // the till silently loses "Send Payment Link".
        assertTrue(pullAndCaptureStoredProfile(localEasebuzz = false, remoteEasebuzz = true).easebuzzEnabled)
    }

    @Test
    fun `a switch turned off on the server is turned off on the device`() = runTest {
        assertFalse(pullAndCaptureStoredProfile(localEasebuzz = true, remoteEasebuzz = false).easebuzzEnabled)
    }

    @Test
    fun `a switch left on by the server stays on`() = runTest {
        assertTrue(pullAndCaptureStoredProfile(localEasebuzz = true, remoteEasebuzz = true).easebuzzEnabled)
    }
}
