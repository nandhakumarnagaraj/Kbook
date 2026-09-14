package com.khanabook.lite.pos.feature.printing.data

import com.khanabook.lite.pos.feature.printing.data.PrinterProfileDao
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterProfileRepository(
    private val printerProfileDao: PrinterProfileDao,
    private val sessionManager: SessionManager
) {
    fun getProfilesFlow(): Flow<List<PrinterProfileEntity>> =
        sessionManager.restaurantId.flatMapLatest { restaurantId ->
            printerProfileDao.getAllFlow(restaurantId)
        }

    suspend fun getProfiles(): List<PrinterProfileEntity> =
        printerProfileDao.getAll(sessionManager.getRestaurantId())

    suspend fun getByRole(role: String): PrinterProfileEntity? =
        printerProfileDao.getByRole(role, sessionManager.getRestaurantId())

    suspend fun saveProfile(profile: PrinterProfileEntity) {
        printerProfileDao.upsert(
            profile.copy(
                restaurantId = sessionManager.getRestaurantId(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteByRole(role: String) {
        printerProfileDao.deleteByRole(role, sessionManager.getRestaurantId())
    }
}
