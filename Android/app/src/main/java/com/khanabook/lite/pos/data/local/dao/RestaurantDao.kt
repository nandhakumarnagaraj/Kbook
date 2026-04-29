package com.khanabook.lite.pos.data.local.dao

import androidx.room.*
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: RestaurantProfileEntity)

    @Query("SELECT * FROM restaurant_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): RestaurantProfileEntity?

    @Query("SELECT * FROM restaurant_profile WHERE id = 1 LIMIT 1")
    fun getProfileFlow(): Flow<RestaurantProfileEntity?>

    @Query(
        "UPDATE restaurant_profile SET daily_order_counter = :counter, last_reset_date = :date, is_synced = 0, updated_at = :updatedAt WHERE id = 1"
    )
    suspend fun resetDailyCounter(counter: Long, date: String, updatedAt: Long)

    @Query(
        "UPDATE restaurant_profile SET daily_order_counter = daily_order_counter + 1, lifetime_order_counter = lifetime_order_counter + 1, is_synced = 0, updated_at = :updatedAt WHERE id = 1"
    )
    suspend fun incrementOrderCounters(updatedAt: Long)

    @Transaction
    suspend fun incrementAndGetCounters(): Pair<Long, Long> {
        val profile = getProfile() ?: throw Exception("Profile not found")
        
        val zoneId = java.time.ZoneId.of(profile.timezone ?: "Asia/Kolkata")
        val today = java.time.LocalDate.now(zoneId).toString()
        val isNewDay = profile.lastResetDate != today
        val now = System.currentTimeMillis()
        
        if (isNewDay) {
            // Reset to 1 on a new day
            val nextDailyCounter = 1L
            resetDailyCounter(nextDailyCounter, today, now)
            incrementLifetimeCounterOnly(now)
            
            val updated = getProfile() ?: throw Exception("Profile lost during update")
            return Pair(nextDailyCounter, updated.lifetimeOrderCounter)
        } else {
            incrementOrderCounters(now)
            val updated = getProfile() ?: throw Exception("Profile lost during update")
            return Pair(updated.dailyOrderCounter, updated.lifetimeOrderCounter)
        }
    }

    @Query("UPDATE restaurant_profile SET lifetime_order_counter = lifetime_order_counter + 1, is_synced = 0, updated_at = :updatedAt WHERE id = 1")
    suspend fun incrementLifetimeCounterOnly(updatedAt: Long)

    @Transaction
    suspend fun updateCounters(daily: Long, lifetime: Long) {
        val current = getProfile() ?: return
        val zoneId = java.time.ZoneId.of(current.timezone ?: "Asia/Kolkata")
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
        "UPDATE restaurant_profile SET lifetime_order_counter = :counter, is_synced = 0, updated_at = :updatedAt WHERE id = 1"
    )
    suspend fun updateLifetimeCounter(counter: Long, updatedAt: Long)

    @Query("UPDATE restaurant_profile SET upi_qr_path = :path, is_synced = 0, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateUpiQrPath(path: String?, updatedAt: Long)

    @Query("UPDATE restaurant_profile SET logo_path = :path, is_synced = 0, updated_at = :updatedAt WHERE id = 1")
    suspend fun updateLogoPath(path: String?, updatedAt: Long)

    @Query(
        "UPDATE restaurant_profile SET logo_url = :url, logo_version = :version, logo_path = NULL, is_synced = :isSynced, updated_at = :updatedAt WHERE id = 1"
    )
    suspend fun updateLogoUrl(url: String?, version: Int, isSynced: Boolean, updatedAt: Long)

    @Query(
        "UPDATE restaurant_profile SET upi_qr_url = :url, upi_qr_version = :version, upi_qr_path = NULL, is_synced = :isSynced, updated_at = :updatedAt WHERE id = 1"
    )
    suspend fun updateUpiQrUrl(url: String?, version: Int, isSynced: Boolean, updatedAt: Long)

    @Query("SELECT * FROM restaurant_profile WHERE is_synced = 0")
    suspend fun getUnsyncedRestaurantProfiles(): List<RestaurantProfileEntity>

    @Query("UPDATE restaurant_profile SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markRestaurantProfilesAsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncedRestaurantProfiles(items: List<RestaurantProfileEntity>)
}
