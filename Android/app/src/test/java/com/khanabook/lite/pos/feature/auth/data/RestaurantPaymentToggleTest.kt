package com.khanabook.lite.pos.feature.auth.data

import androidx.work.WorkManager
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.IOException

/**
 * Regression intent: the server is authoritative for the Easebuzz toggle — a failed
 * updateEasebuzzConfig call must never persist the switch locally.
 *
 * @Ignore for the same reason as BillRepositoryTest: SessionManager is a final class
 * with an init{} block that touches Android SharedPreferences, so it cannot be
 * constructed/mocked in pure JUnit (MockK fails before the first stub). Port to an
 * instrumented test (or after extracting a SessionManager interface) — do not delete.
 */
@Ignore("Requires Robolectric or instrumented test — SessionManager cannot be mocked in pure JUnit")
class RestaurantPaymentToggleTest {

    @Test
    fun `server failure does not save an unconfirmed off switch locally`() = runTest {
        val dao = mockk<RestaurantDao>(relaxed = true)
        val session = mockk<SessionManager>(relaxed = true)
        val api = mockk<KhanaBookApi>(relaxed = true)
        val repository = RestaurantRepository(dao, session, mockk<WorkManager>(relaxed = true), api)
        val current = RestaurantProfileEntity(restaurantId = 42L, easebuzzEnabled = true)
        every { session.getRestaurantId() } returns 42L
        coEvery { dao.getProfile(42L) } returns current
        coEvery { api.updateEasebuzzConfig(mapOf("easebuzzEnabled" to false)) } throws IOException("offline")

        var failed = false
        try {
            repository.savePaymentProfile(current.copy(easebuzzEnabled = false))
        } catch (_: IOException) {
            failed = true
        }
        assertTrue(failed)

        coVerify(exactly = 1) { api.updateEasebuzzConfig(mapOf("easebuzzEnabled" to false)) }
        coVerify(exactly = 0) { dao.saveProfile(any()) }
    }
}
