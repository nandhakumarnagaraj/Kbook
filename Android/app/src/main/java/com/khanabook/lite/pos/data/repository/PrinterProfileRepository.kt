package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.local.dao.PrinterProfileDao
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import kotlinx.coroutines.flow.Flow

class PrinterProfileRepository(
    private val printerProfileDao: PrinterProfileDao
) {
    fun getProfilesFlow(): Flow<List<PrinterProfileEntity>> = printerProfileDao.getAllFlow()

    suspend fun getProfiles(): List<PrinterProfileEntity> = printerProfileDao.getAll()

    suspend fun getByRole(role: String): PrinterProfileEntity? = printerProfileDao.getByRole(role)

    suspend fun saveProfile(profile: PrinterProfileEntity) {
        printerProfileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteByRole(role: String) {
        printerProfileDao.deleteByRole(role)
    }
}
