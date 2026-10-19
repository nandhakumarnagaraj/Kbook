package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.core.database.DatabaseProvider
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.sync.data.MasterSyncResponse
import com.khanabook.lite.pos.feature.sync.data.PushSyncResponse
import com.khanabook.lite.pos.feature.auth.data.UserDao
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.menu.data.CategoryDao
import com.khanabook.lite.pos.feature.menu.data.MenuDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.staff.domain.PermissionManager
import com.khanabook.lite.pos.feature.sync.data.SyncQuarantineEntity
import com.khanabook.lite.pos.feature.sync.domain.MasterSyncProcessor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MasterSyncProcessorProfileStaleTest {

    private lateinit var processor: MasterSyncProcessor
    private lateinit var billDao: BillDao
    private lateinit var restaurantDao: RestaurantDao
    private lateinit var api: KhanaBookApi

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        billDao = mockk(relaxed = true)
        restaurantDao = mockk(relaxed = true)
        api = mockk(relaxed = true)
        processor = MasterSyncProcessor(
            api = api,
            databaseProvider = mockk<DatabaseProvider>(relaxed = true),
            billDao = billDao,
            restaurantDao = restaurantDao,
            userDao = mockk<UserDao>(relaxed = true),
            categoryDao = mockk<CategoryDao>(relaxed = true),
            menuDao = mockk<MenuDao>(relaxed = true),
            printerProfileDao = mockk<PrinterProfileDao>(relaxed = true),
            sessionManager = mockk<SessionManager>(relaxed = true),
            permissionManager = mockk<PermissionManager>(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `stale_push_conflict is classified as stale profile rejection`() {
        assertTrue(processor.isStaleProfileRejection("stale_push_conflict: claim is older than server"))
        assertTrue(processor.isStaleProfileRejection("STALE_PUSH_CONFLICT"))
    }

    @Test
    fun `other permanent rejections are not classified as stale`() {
        assertFalse(processor.isStaleProfileRejection("Permission not granted for restaurant profile"))
        assertFalse(processor.isStaleProfileRejection(null))
    }

    @Test
    fun `stale profile quarantine stores plain-language reason, not raw token`() = runTest {
        val saved = slot<SyncQuarantineEntity>()

        val count = processor.quarantineRejectedProfiles(
            mapOf(7L to "stale_push_conflict: claim is older than server"),
            restaurantId = 55L
        )

        assertEquals(1, count)
        coVerify { billDao.upsertSyncQuarantineRecord(capture(saved)) }
        assertEquals(
            "Another device updated your shop settings. Pull to refresh.",
            saved.captured.syncFailureReason
        )
        assertEquals("restaurant_profile", saved.captured.childEntityType)
    }

    @Test
    fun `stale profile is refreshed once and re-pushed, not quarantined`() = runTest {
        val profile = RestaurantProfileEntity(id = 7L, restaurantId = 55L, serverUpdatedAt = 100L)
        coEvery { restaurantDao.getUnsyncedRestaurantProfiles() } returns listOf(profile)
        coEvery { restaurantDao.getProfile(any<Long>()) } returns profile
        coEvery { api.pushRestaurantProfiles(any()) } returnsMany listOf(
            PushSyncResponse(
                successfulLocalIds = emptyList(),
                failedLocalIds = listOf(7L),
                failedReasons = mapOf(7L to "stale_push_conflict: claim is older than server")
            ),
            PushSyncResponse(successfulLocalIds = listOf(7L), failedLocalIds = emptyList())
        )
        coEvery { api.pullMasterSync(any(), any(), any(), any(), any(), any()) } returns
            MasterSyncResponse(profiles = listOf(RestaurantProfileEntity(id = 7L, restaurantId = 55L, serverUpdatedAt = 900L)))

        processor.pushAllForTest(55L)

        coVerify(exactly = 2) { api.pushRestaurantProfiles(any()) }
        coVerify(exactly = 1) { restaurantDao.refreshProfileServerRevision(7L, 900L) }
        coVerify(exactly = 0) { billDao.upsertSyncQuarantineRecord(any()) }
    }

    @Test
    fun `second stale rejection of the same profile is quarantined after one retry`() = runTest {
        val profile = RestaurantProfileEntity(id = 7L, restaurantId = 55L, serverUpdatedAt = 100L)
        val saved = slot<SyncQuarantineEntity>()
        coEvery { restaurantDao.getUnsyncedRestaurantProfiles() } returns listOf(profile)
        coEvery { restaurantDao.getProfile(any<Long>()) } returns profile
        coEvery { api.pushRestaurantProfiles(any()) } returns PushSyncResponse(
            successfulLocalIds = emptyList(),
            failedLocalIds = listOf(7L),
            failedReasons = mapOf(7L to "stale_push_conflict: claim is older than server")
        )
        coEvery { api.pullMasterSync(any(), any(), any(), any(), any(), any()) } returns
            MasterSyncResponse(profiles = listOf(RestaurantProfileEntity(id = 7L, restaurantId = 55L, serverUpdatedAt = 900L)))

        processor.pushAllForTest(55L)

        coVerify(exactly = 2) { api.pushRestaurantProfiles(any()) }
        coVerify(exactly = 1) { restaurantDao.refreshProfileServerRevision(7L, 900L) }
        coVerify { billDao.upsertSyncQuarantineRecord(capture(saved)) }
        assertEquals(
            "Another device updated your shop settings. Pull to refresh.",
            saved.captured.syncFailureReason
        )
    }

    @Test
    fun `non-stale profile quarantine keeps original reason`() = runTest {
        val saved = slot<SyncQuarantineEntity>()

        processor.quarantineRejectedProfiles(
            mapOf(7L to "Permission not granted for restaurant profile"),
            restaurantId = 55L
        )

        coVerify { billDao.upsertSyncQuarantineRecord(capture(saved)) }
        assertEquals("Permission not granted for restaurant profile", saved.captured.syncFailureReason)
    }
}
