package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.domain.util.AppConstants

import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.RestaurantDao
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.TerminalDailyCounterEntity
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.util.enqueueMasterSyncOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import okhttp3.MultipartBody

@OptIn(ExperimentalCoroutinesApi::class)
class RestaurantRepository(
        private val restaurantDao: RestaurantDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager,
        private val api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi
) {
    suspend fun saveProfile(profile: RestaurantProfileEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        val current = restaurantDao.getProfile(restaurantId) ?: restaurantDao.getProfile()
        val enriched =
                profile.copy(
                        id = restaurantId,
                        restaurantId = restaurantId,
                        deviceId = sessionManager.getDeviceId(),
                        dailyOrderCounter = maxOf(profile.dailyOrderCounter, current?.dailyOrderCounter ?: 0L),
                        lifetimeOrderCounter = profile.lifetimeOrderCounter,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        restaurantDao.saveProfile(enriched)
        triggerBackgroundSync()
    }

    suspend fun seedProfileIfMissing(profile: RestaurantProfileEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        if (restaurantDao.getProfile(restaurantId) != null || restaurantDao.getProfile() != null) return

        restaurantDao.saveProfile(
            profile.copy(
                id = restaurantId,
                restaurantId = restaurantId,
                deviceId = sessionManager.getDeviceId(),
                isSynced = true,
                updatedAt = 0L
            )
        )
    }

    suspend fun getProfile(): RestaurantProfileEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return if (restaurantId > 0) restaurantDao.getProfile(restaurantId) else restaurantDao.getProfile()
    }

    fun getProfileFlow(): Flow<RestaurantProfileEntity?> {
        return sessionManager.restaurantId.flatMapLatest { restaurantId ->
            if (restaurantId > 0) restaurantDao.getProfileFlow(restaurantId) else restaurantDao.getProfileFlow()
        }
    }

    suspend fun resetDailyCounter(counter: Long, date: String) {
        val restaurantId = sessionManager.getRestaurantId()
        restaurantDao.resetDailyCounter(restaurantId, counter, date, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun raiseDailyCounterAtLeast(dailyCounter: Long, date: String) {
        val restaurantId = sessionManager.getRestaurantId()
        restaurantDao.raiseDailyCounterAtLeast(
            restaurantId = restaurantId,
            dailyCounter = dailyCounter,
            date = date,
            updatedAt = System.currentTimeMillis()
        )
        triggerBackgroundSync()
    }

    suspend fun incrementOrderCounters() {
        val restaurantId = sessionManager.getRestaurantId()
        restaurantDao.incrementOrderCounters(restaurantId, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun incrementAndGetCounters(requireServer: Boolean = false): Pair<Long, Long> {
        val restaurantId = sessionManager.getRestaurantId()
        if (!requireServer) {
            val counters = restaurantDao.incrementAndGetCounters(restaurantId)
            triggerBackgroundSync()
            return counters
        }
        return try {
            val response = api.incrementCounters()
            restaurantDao.updateCounters(restaurantId, response.dailyCounter, response.lifetimeCounter)
            Pair(response.dailyCounter, response.lifetimeCounter)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Unable to reserve an online order number from the server. Check your connection and try again.",
                e
            )
        }
    }

    suspend fun incrementAndGetDailyCounter(): Long {
        val restaurantId = sessionManager.getRestaurantId()
        val dailyCounter = restaurantDao.incrementAndGetDailyCounter(restaurantId)
        triggerBackgroundSync()
        return dailyCounter
    }

    // ── Terminal Daily Counter (per-terminal isolation) ────────────────────────

    suspend fun incrementAndGetTerminalDailyCounter(terminalId: String): Long {
        val restaurantId = sessionManager.getRestaurantId()
        val dailyCounter = restaurantDao.incrementAndGetTerminalDailyCounter(restaurantId, terminalId)
        triggerBackgroundSync()
        return dailyCounter
    }

    suspend fun getTerminalDailyCounter(terminalId: String, date: String? = null): TerminalDailyCounterEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        val targetDate = date ?: java.time.LocalDate.now(java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)).toString()
        return restaurantDao.getTerminalDailyCounter(restaurantId, terminalId, targetDate)
    }

    suspend fun raiseTerminalDailyCounterAtLeast(terminalId: String, counter: Long, date: String? = null) {
        val restaurantId = sessionManager.getRestaurantId()
        val targetDate = date ?: java.time.LocalDate.now(java.time.ZoneId.of(AppConstants.DEFAULT_TIMEZONE)).toString()
        restaurantDao.raiseTerminalDailyCounterAtLeast(restaurantId, terminalId, targetDate, counter, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun updateLogoPath(path: String?) {
        val restaurantId = sessionManager.getRestaurantId()
        restaurantDao.updateLogoPath(restaurantId, path, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun uploadLogo(file: MultipartBody.Part): String {
        val restaurantId = sessionManager.getRestaurantId()
        val current = if (restaurantId > 0) restaurantDao.getProfile(restaurantId) else restaurantDao.getProfile()
        val response = api.uploadLogo(file)
        val version = response.logoVersion.takeIf { it > 0 }
            ?: ((current?.logoVersion ?: 0) + 1)
        restaurantDao.updateLogoUrl(
            restaurantId,
            response.logoUrl,
            version,
            current?.isSynced ?: true,
            System.currentTimeMillis()
        )
        if (current?.isSynced == false) triggerBackgroundSync()
        return response.logoUrl
    }

    private fun triggerBackgroundSync() {
        workManager.enqueueMasterSyncOnce()
    }
}
