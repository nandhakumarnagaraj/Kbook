package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.local.dao.PrinterProfileDao
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.domain.manager.SessionManager
import kotlinx.coroutines.flow.Flow

class PrinterProfileRepository(
    private val printerProfileDao: PrinterProfileDao,
    private val sessionManager: SessionManager
) {
    fun getProfilesFlow(): Flow<List<PrinterProfileEntity>> =
        printerProfileDao.getAllFlow(sessionManager.getRestaurantId())

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
