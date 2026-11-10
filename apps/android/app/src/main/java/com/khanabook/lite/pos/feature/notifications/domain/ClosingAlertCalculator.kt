package com.khanabook.lite.pos.feature.notifications.domain

import com.khanabook.lite.pos.core.util.BusinessDate
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Encapsulates the live state of approaching closing time for the restaurant shift.
 */
data class ClosingAlertInfo(
    val isClosingSoon: Boolean,
    val isPastClosing: Boolean,
    val minutesUntilClosing: Int,
    val minutesPastClosing: Int,
    val closingTimeFormatted: String,
    val alertText: String,
    val tooltipText: String
)

/**
 * Offline-first calculator that evaluates whether a counter terminal is within
 * the pre-closing alert window or cutoff grace buffer.
 *
 * Designed to handle both standard all-day shifts (e.g. 11:00 AM - 11:00 PM)
 * and overnight late-night shifts (e.g. 6:00 PM - next-day 4:00 AM).
 */
object ClosingAlertCalculator {

    /**
     * Alert window:
     * - Within 30 minutes before closing -> isClosingSoon = true
     * - Exactly at closing time -> isClosingSoon = true
     * - Within 30 minutes past closing (grace buffer) -> isPastClosing = true
     * Returns null when outside this alert window.
     */
    fun calculate(
        closingTime: String?,
        nowMillis: Long = System.currentTimeMillis(),
        timezone: String? = null
    ): ClosingAlertInfo? {
        if (closingTime.isNullOrBlank()) return null
        val parts = closingTime.trim().split(":")
        if (parts.size < 2) return null
        val closeHour = parts[0].toIntOrNull() ?: return null
        val closeMinute = parts[1].toIntOrNull() ?: return null
        if (closeHour !in 0..23 || closeMinute !in 0..59) return null

        val zone = BusinessDate.zoneOf(timezone)
        val zonedNow = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val nowMinutes = zonedNow.hour * 60 + zonedNow.minute
        val closingMinutes = closeHour * 60 + closeMinute

        var diff = closingMinutes - nowMinutes
        if (diff < 0) diff += 1440 // overnight wrap

        val formattedClose = String.format(Locale.US, "%02d:%02d", closeHour, closeMinute)

        // 1 to 30 minutes before closing
        if (diff in 1..30) {
            return ClosingAlertInfo(
                isClosingSoon = true,
                isPastClosing = false,
                minutesUntilClosing = diff,
                minutesPastClosing = 0,
                closingTimeFormatted = formattedClose,
                alertText = "Closing in ${diff}m ($formattedClose)",
                tooltipText = "Store closing time is $formattedClose. Settle open tables and finalize active bills."
            )
        }

        // Exactly at closing time
        if (diff == 0) {
            return ClosingAlertInfo(
                isClosingSoon = true,
                isPastClosing = false,
                minutesUntilClosing = 0,
                minutesPastClosing = 0,
                closingTimeFormatted = formattedClose,
                alertText = "Closing time reached ($formattedClose)",
                tooltipText = "Store closing time reached. Complete table settlements and cash drawer reconciliation."
            )
        }

        // Within 30 minutes past closing (cutoff grace window)
        val pastMinutes = 1440 - diff
        if (pastMinutes in 1..30) {
            return ClosingAlertInfo(
                isClosingSoon = false,
                isPastClosing = true,
                minutesUntilClosing = 0,
                minutesPastClosing = pastMinutes,
                closingTimeFormatted = formattedClose,
                alertText = "Shift closing in progress (${pastMinutes}m past $formattedClose)",
                tooltipText = "Store is past closing time. Settle remaining orders before daily cutoff."
            )
        }

        return null
    }

    /**
     * Calculates the millisecond delay from nowMillis to the next scheduled alert milestone:
     * - 30 minutes before closing
     * - 15 minutes before closing
     * - At closing time
     * Returns null if closingTime is unparseable.
     */
    fun getNextMilestoneDelayMillis(
        closingTime: String?,
        timezone: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (closingTime.isNullOrBlank()) return null
        val parts = closingTime.trim().split(":")
        if (parts.size < 2) return null
        val closeHour = parts[0].toIntOrNull() ?: return null
        val closeMinute = parts[1].toIntOrNull() ?: return null
        if (closeHour !in 0..23 || closeMinute !in 0..59) return null

        val zone = BusinessDate.zoneOf(timezone)
        val zonedNow = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val nowMinutes = zonedNow.hour * 60 + zonedNow.minute
        val closingMinutes = closeHour * 60 + closeMinute

        // Target milestone minute offsets relative to midnight:
        val milestones = listOf(
            (closingMinutes - 30 + 1440) % 1440,
            (closingMinutes - 15 + 1440) % 1440,
            closingMinutes
        )

        // Find the milestone with the smallest positive minute difference:
        var minDiffMinutes = Int.MAX_VALUE
        for (m in milestones) {
            var diff = m - nowMinutes
            if (diff <= 0) diff += 1440 // next occurrence (tomorrow)
            if (diff < minDiffMinutes) {
                minDiffMinutes = diff
            }
        }

        val elapsedInCurrentMinuteMs = zonedNow.second * 1000L + zonedNow.nano / 1_000_000L
        val delayMs = (minDiffMinutes * 60 * 1000L) - elapsedInCurrentMinuteMs
        return delayMs.coerceAtLeast(10_000L) // Minimum 10 seconds safety margin
    }
}
