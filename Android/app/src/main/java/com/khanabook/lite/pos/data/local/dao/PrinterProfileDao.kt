package com.khanabook.lite.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterProfileDao {
    @Query("SELECT * FROM printer_profiles WHERE restaurant_id = :restaurantId ORDER BY CASE role WHEN 'KITCHEN' THEN 0 ELSE 1 END, id ASC")
    fun getAllFlow(restaurantId: Long): Flow<List<PrinterProfileEntity>>

    @Query("SELECT * FROM printer_profiles WHERE restaurant_id = :restaurantId ORDER BY CASE role WHEN 'KITCHEN' THEN 0 ELSE 1 END, id ASC")
    suspend fun getAll(restaurantId: Long): List<PrinterProfileEntity>

    @Query("SELECT * FROM printer_profiles WHERE role = :role AND restaurant_id = :restaurantId LIMIT 1")
    suspend fun getByRole(role: String, restaurantId: Long): PrinterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: PrinterProfileEntity)

    @Query("DELETE FROM printer_profiles WHERE role = :role AND restaurant_id = :restaurantId")
    suspend fun deleteByRole(role: String, restaurantId: Long)
}
