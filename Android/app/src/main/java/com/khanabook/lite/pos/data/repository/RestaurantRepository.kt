package com.khanabook.lite.pos.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.RestaurantDao
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.worker.MasterSyncWorker
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

class RestaurantRepository(
        private val restaurantDao: RestaurantDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager,
        private val api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi
) {
    suspend fun saveProfile(profile: RestaurantProfileEntity) {
        val restaurantId = sessionManager.getRestaurantId()
        val enriched =
                profile.copy(
                        id = restaurantId,
                        restaurantId = restaurantId,
                        deviceId = sessionManager.getDeviceId(),
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        restaurantDao.saveProfile(enriched)
        triggerBackgroundSync()
    }

    suspend fun getProfile(): RestaurantProfileEntity? {
        val restaurantId = sessionManager.getRestaurantId()
        return if (restaurantId > 0) restaurantDao.getProfile(restaurantId) else restaurantDao.getProfile()
    }

    fun getProfileFlow(): Flow<RestaurantProfileEntity?> {
        val restaurantId = sessionManager.getRestaurantId()
        return if (restaurantId > 0) restaurantDao.getProfileFlow(restaurantId) else restaurantDao.getProfileFlow()
    }

    suspend fun resetDailyCounter(counter: Long, date: String) {
        val restaurantId = sessionManager.getRestaurantId()
        restaurantDao.resetDailyCounter(restaurantId, counter, date, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun incrementOrderCounters() {
        val restaurantId = sessionManager.getRestaurantId()
        restaurantDao.incrementOrderCounters(restaurantId, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun incrementAndGetCounters(requireServer: Boolean = false): Pair<Long, Long> {
        val restaurantId = sessionManager.getRestaurantId()
        return try {
            val response = api.incrementCounters()
            restaurantDao.updateCounters(restaurantId, response.dailyCounter, response.lifetimeCounter)
            Pair(response.dailyCounter, response.lifetimeCounter)
        } catch (e: Exception) {
            if (requireServer) {
                throw IllegalStateException(
                    "Unable to reserve an online order number from the server. Check your connection and try again.",
                    e
                )
            }
            val counters = restaurantDao.incrementAndGetCounters(restaurantId)
            triggerBackgroundSync()
            counters
        }
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
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest =
                OneTimeWorkRequestBuilder<MasterSyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork(
                "MasterSyncWorker_OneTime",
                ExistingWorkPolicy.KEEP,
                syncWorkRequest
        )
    }
}
