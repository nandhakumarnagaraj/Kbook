package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: RestaurantProfileEntity)

    @Query("SELECT * FROM restaurant_profile WHERE restaurant_id = :restaurantId LIMIT 1")
    suspend fun getProfile(restaurantId: Long): RestaurantProfileEntity?

    // No-arg fallback: each isolated per-restaurant DB holds exactly one profile row,
    // whose `id` is now the restaurant's server id (not the legacy `1`). Select it
    // unconditionally instead of `WHERE id = 1`, which no longer matches.
    @Query("SELECT * FROM restaurant_profile LIMIT 1")
    suspend fun getProfile(): RestaurantProfileEntity?

    @Query("SELECT * FROM restaurant_profile WHERE restaurant_id = :restaurantId LIMIT 1")
    fun getProfileFlow(restaurantId: Long): Flow<RestaurantProfileEntity?>

    @Query("SELECT * FROM restaurant_profile LIMIT 1")
    fun getProfileFlow(): Flow<RestaurantProfileEntity?>

    @Query(
        "UPDATE restaurant_profile SET daily_order_counter = :counter, last_reset_date = :date, is_synced = 0, updated_at = :updatedAt WHERE restaurant_id = :restaurantId"
    )
    suspend fun resetDailyCounter(restaurantId: Long, counter: Long, date: String, updatedAt: Long)

    @Query(
        "UPDATE restaurant_profile SET daily_order_counter = daily_order_counter + 1, lifetime_order_counter = lifetime_order_counter + 1, is_synced = 0, updated_at = :updatedAt WHERE restaurant_id = :restaurantId"
    )
    suspend fun incrementOrderCounters(restaurantId: Long, updatedAt: Long)

    @Transaction
    suspend fun incrementAndGetCounters(restaurantId: Long): Pair<Long, Long> {
        val profile = getProfile(restaurantId) ?: throw Exception("Profile not found")
        
        val zoneId = java.time.ZoneId.of("Asia/Kolkata")
        val today = java.time.LocalDate.now(zoneId).toString()
        val isNewDay = profile.lastResetDate != today
        val now = System.currentTimeMillis()

        if (isNewDay) {
            // Reset to 1 on a new day
            val nextDailyCounter = 1L
            resetDailyCounter(restaurantId, nextDailyCounter, today, now)
            incrementLifetimeCounterOnly(restaurantId, now)
            
            val updated = getProfile(restaurantId) ?: throw Exception("Profile lost during update")
            return Pair(nextDailyCounter, updated.lifetimeOrderCounter)
        } else {
            incrementOrderCounters(restaurantId, now)
            val updated = getProfile(restaurantId) ?: throw Exception("Profile lost during update")
            return Pair(updated.dailyOrderCounter, updated.lifetimeOrderCounter)
        }
    }

    @Query("UPDATE restaurant_profile SET lifetime_order_counter = lifetime_order_counter + 1, is_synced = 0, updated_at = :updatedAt WHERE restaurant_id = :restaurantId")
    suspend fun incrementLifetimeCounterOnly(restaurantId: Long, updatedAt: Long)

    @Transaction
    suspend fun updateCounters(restaurantId: Long, daily: Long, lifetime: Long) {
        val current = getProfile(restaurantId) ?: return
        val zoneId = java.time.ZoneId.of("Asia/Kolkata")
        val today = java.time.LocalDate.now(zoneId).toString()
        saveProfile(current.copy(
            dailyOrderCounter = daily,
            lifetimeOrderCounter = lifetime,
            lastResetDate = today,
            isSynced = true,
            updatedAt = System.currentTimeMillis()
        ))
    }

    @Query(
        "UPDATE restaurant_profile SET lifetime_order_counter = :counter, is_synced = 0, updated_at = :updatedAt WHERE restaurant_id = :restaurantId"
    )
    suspend fun updateLifetimeCounter(restaurantId: Long, counter: Long, updatedAt: Long)

    @Query("UPDATE restaurant_profile SET logo_path = :path, is_synced = 0, updated_at = :updatedAt WHERE restaurant_id = :restaurantId")
    suspend fun updateLogoPath(restaurantId: Long, path: String?, updatedAt: Long)

    @Query(
        "UPDATE restaurant_profile SET logo_url = :url, logo_version = :version, logo_path = NULL, is_synced = :isSynced, updated_at = :updatedAt WHERE restaurant_id = :restaurantId"
    )
    suspend fun updateLogoUrl(restaurantId: Long, url: String?, version: Int, isSynced: Boolean, updatedAt: Long)

    @Query("SELECT * FROM restaurant_profile WHERE is_synced = 0")
    suspend fun getUnsyncedRestaurantProfiles(): List<RestaurantProfileEntity>

    @Query("UPDATE restaurant_profile SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markRestaurantProfilesAsSynced(ids: List<Long>)

    @Query("UPDATE restaurant_profile SET server_id = :serverId WHERE id = :localId")
    suspend fun updateServerIdByLocalId(localId: Long, serverId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedRestaurantProfiles(items: List<RestaurantProfileEntity>)
}
