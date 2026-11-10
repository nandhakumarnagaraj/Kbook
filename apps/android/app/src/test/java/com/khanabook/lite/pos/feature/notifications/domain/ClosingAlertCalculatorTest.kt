package com.khanabook.lite.pos.feature.notifications.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ClosingAlertCalculatorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val testDate = LocalDate.of(2026, 11, 10)

    private fun timeMillis(hour: Int, minute: Int, second: Int = 0): Long {
        return ZonedDateTime.of(testDate, LocalTime.of(hour, minute, second), zone).toInstant().toEpochMilli()
    }

    @Test
    fun `overnight shift - 15 minutes before 04_00 AM closing triggers warning`() {
        // Shift is 6:00 PM to 4:00 AM
        val closingTime = "04:00"
        val now = timeMillis(3, 45) // 03:45 AM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNotNull(alert)
        assertTrue(alert!!.isClosingSoon)
        assertFalse(alert.isPastClosing)
        assertEquals(15, alert.minutesUntilClosing)
        assertEquals("04:00", alert.closingTimeFormatted)
        assertEquals("Closing in 15m (04:00)", alert.alertText)
    }

    @Test
    fun `overnight shift - exactly 30 minutes before 04_00 AM closing triggers warning`() {
        val closingTime = "04:00"
        val now = timeMillis(3, 30) // 03:30 AM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNotNull(alert)
        assertTrue(alert!!.isClosingSoon)
        assertEquals(30, alert.minutesUntilClosing)
        assertEquals("Closing in 30m (04:00)", alert.alertText)
    }

    @Test
    fun `overnight shift - exactly at 04_00 AM closing triggers closing time reached`() {
        val closingTime = "04:00"
        val now = timeMillis(4, 0) // 04:00 AM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNotNull(alert)
        assertTrue(alert!!.isClosingSoon)
        assertEquals(0, alert.minutesUntilClosing)
        assertEquals("Closing time reached (04:00)", alert.alertText)
    }

    @Test
    fun `overnight shift - 15 minutes past 04_00 AM closing is in grace cutoff window`() {
        val closingTime = "04:00"
        val now = timeMillis(4, 15) // 04:15 AM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNotNull(alert)
        assertFalse(alert!!.isClosingSoon)
        assertTrue(alert.isPastClosing)
        assertEquals(15, alert.minutesPastClosing)
        assertEquals("Shift closing in progress (15m past 04:00)", alert.alertText)
    }

    @Test
    fun `overnight shift - daytime hours return null`() {
        val closingTime = "04:00"
        val now = timeMillis(18, 0) // 6:00 PM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNull(alert)
    }

    @Test
    fun `overnight shift - 45 minutes past closing returns null (past grace buffer)`() {
        val closingTime = "04:00"
        val now = timeMillis(4, 45) // 04:45 AM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNull(alert)
    }

    @Test
    fun `standard daytime shift - 20 minutes before 23_00 closing triggers alert`() {
        val closingTime = "23:00"
        val now = timeMillis(22, 40) // 10:40 PM

        val alert = ClosingAlertCalculator.calculate(closingTime, now, "Asia/Kolkata")

        assertNotNull(alert)
        assertTrue(alert!!.isClosingSoon)
        assertEquals(20, alert.minutesUntilClosing)
        assertEquals("Closing in 20m (23:00)", alert.alertText)
    }

    @Test
    fun `next milestone delay - at 03_00 AM targets 03_30 AM milestone`() {
        val closingTime = "04:00"
        val now = timeMillis(3, 0, 0) // 03:00:00 AM

        val delayMs = ClosingAlertCalculator.getNextMilestoneDelayMillis(closingTime, "Asia/Kolkata", now)

        assertNotNull(delayMs)
        // 30 minutes = 1,800,000 ms
        assertEquals(30 * 60 * 1000L, delayMs!!)
    }

    @Test
    fun `next milestone delay - at 03_35 AM targets 03_45 AM milestone`() {
        val closingTime = "04:00"
        val now = timeMillis(3, 35, 0) // 03:35:00 AM

        val delayMs = ClosingAlertCalculator.getNextMilestoneDelayMillis(closingTime, "Asia/Kolkata", now)

        assertNotNull(delayMs)
        // 10 minutes = 600,000 ms
        assertEquals(10 * 60 * 1000L, delayMs!!)
    }

    @Test
    fun `next milestone delay - at 03_50 AM targets 04_00 AM milestone`() {
        val closingTime = "04:00"
        val now = timeMillis(3, 50, 0) // 03:50:00 AM

        val delayMs = ClosingAlertCalculator.getNextMilestoneDelayMillis(closingTime, "Asia/Kolkata", now)

        assertNotNull(delayMs)
        // 10 minutes = 600,000 ms
        assertEquals(10 * 60 * 1000L, delayMs!!)
    }
}
