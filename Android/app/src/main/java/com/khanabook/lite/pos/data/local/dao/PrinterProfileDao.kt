package com.khanabook.lite.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterProfileDao {
    @Query("SELECT * FROM printer_profiles ORDER BY CASE role WHEN 'KITCHEN' THEN 0 ELSE 1 END, id ASC")
    fun getAllFlow(): Flow<List<PrinterProfileEntity>>

    @Query("SELECT * FROM printer_profiles ORDER BY CASE role WHEN 'KITCHEN' THEN 0 ELSE 1 END, id ASC")
    suspend fun getAll(): List<PrinterProfileEntity>

    @Query("SELECT * FROM printer_profiles WHERE role = :role LIMIT 1")
    suspend fun getByRole(role: String): PrinterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PrinterProfileEntity)

    @Query("DELETE FROM printer_profiles WHERE role = :role")
    suspend fun deleteByRole(role: String)
}
