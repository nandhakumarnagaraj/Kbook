package com.khanabook.lite.pos.feature.billing.domain

import com.khanabook.lite.pos.core.util.AppConstants

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
/**
 * Manages the generation and resetting of Daily and Lifetime Order IDs.
 */
object OrderIdManager {
    private const val INDIA_TIMEZONE = AppConstants.DEFAULT_TIMEZONE
    
    /**
     * Returns the formatted daily order ID (e.g., "01", "10", or "A1-01").
     */
    fun getDailyOrderDisplay(date: String, counter: Long, terminalSeries: String? = null): String {
        val displayCounter = counter.toString().padStart(2, '0')
        return if (terminalSeries != null && terminalSeries.isNotBlank()) {
            "$terminalSeries-$displayCounter"
        } else {
            displayCounter
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
