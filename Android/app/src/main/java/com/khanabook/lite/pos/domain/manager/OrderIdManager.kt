package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Manages the generation and resetting of Daily and Lifetime Order IDs.
 */
object OrderIdManager {
    
    /**
     * Returns the formatted daily order ID (e.g., "001").
     */
    fun getDailyOrderDisplay(date: String, counter: Long): String {
        return String.format("%03d", counter)
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
     * Calculates the next lifetime order ID.
     */
    fun getNextLifetimeId(profile: RestaurantProfileEntity): Long {
        return profile.lifetimeOrderCounter + 1L
    }

    /**
     * Returns today's date string in YYYY-MM-DD format.
     */
    fun getTodayString(timezone: String = "Asia/Kolkata"): String {
        return LocalDate.now(ZoneId.of(timezone)).toString()
    }
}
