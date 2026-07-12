package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
/**
 * Manages the generation and resetting of Daily and Lifetime Order IDs.
 */
object OrderIdManager {
    private const val INDIA_TIMEZONE = "Asia/Kolkata"
    
    /**
     * Returns the formatted daily order ID (e.g., "001" or "A1-0001").
     */
    fun getDailyOrderDisplay(date: String, counter: Long, terminalSeries: String? = null): String {
        return if (terminalSeries != null && terminalSeries.isNotBlank()) {
            "$terminalSeries-${String.format("%04d", counter)}"
        } else {
            String.format("%03d", counter)
        }
    }

    /**
     * Checks if the daily counter needs to be reset based on the current date.
     */
    fun isResetNeeded(profile: RestaurantProfileEntity, today: String): Boolean {
        return profile.lastResetDate != today
    }

    /**
     * Calculates the next daily counter value.
     */
    fun getNextDailyCounter(profile: RestaurantProfileEntity, today: String): Long {
        return if (profile.lastResetDate != today) 1L else profile.dailyOrderCounter + 1L
    }

    /**
     * Returns today's date string in YYYY-MM-DD format.
     */
    fun getTodayString(timezone: String = INDIA_TIMEZONE): String {
        return java.time.LocalDate.now(java.time.ZoneId.of(INDIA_TIMEZONE)).toString()
    }
}
