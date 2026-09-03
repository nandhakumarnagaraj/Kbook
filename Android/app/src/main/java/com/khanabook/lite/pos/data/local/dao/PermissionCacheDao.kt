package com.khanabook.lite.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khanabook.lite.pos.data.local.entity.PermissionCacheEntity

@Dao
interface PermissionCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PermissionCacheEntity)

    @Query("SELECT * FROM permission_cache WHERE user_id = :userId LIMIT 1")
    suspend fun getForUser(userId: Long): PermissionCacheEntity?

    /** Fallback for cold start before the active user id is known. */
    @Query("SELECT * FROM permission_cache ORDER BY updated_at DESC LIMIT 1")
    suspend fun getMostRecent(): PermissionCacheEntity?

    @Query("DELETE FROM permission_cache")
    suspend fun clear()
}
