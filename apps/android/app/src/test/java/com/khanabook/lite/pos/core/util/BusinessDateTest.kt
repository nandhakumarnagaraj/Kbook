package com.khanabook.lite.pos.core.util

import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The boundary table from the design doc
 * (`docs/planning/BUSINESS_DAY_CUTOFF_AND_SHIFTS_PLAN_2026-11-06.md` §10).
 *
 * The Java twin (`BusinessDayTest`) runs the same cases, because a client and a server
 * that disagree by one day is exactly the failure `applyServerBusinessDate` exists to
 * prevent.
 */
class BusinessDateTest {

    private val ist = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ist).toInstant().toEpochMilli()

    private fun profile(
        timezone: String = "Asia/Kolkata",
        cutoff: String = "00:00",
        effectiveFrom: Long? = null,
        opening: String = "08:00",
        closing: String = "23:00"
    ) = RestaurantProfileEntity(
        timezone = timezone,
        businessDayCutoffTime = cutoff,
        cutoffEffectiveFrom = effectiveFrom,
        openingTime = opening,
        closingTime = closing
    )

    private val activatedAt4Am = at(2026, 11, 10, 0, 0)

    @Test
    fun `no cutoff keeps the calendar day`() {
        val p = profile()
        assertEquals("2026-11-06", BusinessDate.businessDate(at(2026, 11, 6, 1, 30), p))
        assertEquals("2026-11-06", BusinessDate.businessDate(at(2026, 11, 6, 4, 30), p))
    }

    @Test
    fun `explicit midnight cutoff is the legacy rule even when an effective instant is set`() {
        val p = profile(cutoff = "00:00", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-06", BusinessDate.businessDate(at(2026, 11, 6, 1, 30), p))
    }

    @Test
    fun `a bill created before activation keeps the calendar day`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 9, 1, 30), p))
    }

    @Test
    fun `bill before the cutoff belongs to the previous business day`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 3, 59), p))
    }

    @Test
    fun `the cutoff instant itself belongs to the new business day`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 4, 0), p))
    }

    @Test
    fun `just after the cutoff belongs to the new business day`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 4, 1), p))
    }

    @Test
    fun `late evening stays on the same business day`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 23, 0), p))
    }

    @Test
    fun `an unparseable cutoff falls back to the legacy rule`() {
        val p = profile(cutoff = "not-a-time", effectiveFrom = activatedAt4Am)
        assertEquals("2026-11-06", BusinessDate.businessDate(at(2026, 11, 6, 1, 30), p))
    }

    @Test
    fun `the restaurant timezone decides the calendar day`() {
        val p = profile(timezone = "America/New_York")
        // 2026-09-12T03:30Z == 2026-09-11 23:30 in New York.
        val instant = java.time.Instant.parse("2026-09-12T03:30:00Z").toEpochMilli()
        assertEquals("2026-09-11", BusinessDate.businessDate(instant, p))
    }

    @Test
    fun `an invalid timezone falls back to IST`() {
        val p = profile(timezone = "Not/AZone")
        // 2026-09-10T18:39Z == 2026-09-11 00:09 IST.
        assertEquals("2026-09-11", BusinessDate.businessDate(
            java.time.Instant.parse("2026-09-10T18:39:00Z").toEpochMilli(), p
        ))
    }

    @Test
    fun `the day before the effective date is the 28 hour one`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        val lastCalendarDay = LocalDate.of(2026, 11, 9)

        assertEquals(at(2026, 11, 9, 0, 0), BusinessDate.windowStart(lastCalendarDay, p))
        assertEquals(at(2026, 11, 10, 4, 0), BusinessDate.windowEnd(lastCalendarDay, p))
    }

    @Test
    fun `the effective date itself starts at the cutoff`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        val effectiveDate = LocalDate.of(2026, 11, 10)

        assertEquals(at(2026, 11, 10, 4, 0), BusinessDate.windowStart(effectiveDate, p))
        assertEquals(at(2026, 11, 11, 4, 0), BusinessDate.windowEnd(effectiveDate, p))
    }

    @Test
    fun `every instant in a window maps back to that business date`() {
        // The failure this guards: businessDate() and windowStart()/windowEnd() disagreeing
        // by one day, which double-counts four hours in reports and makes the server's
        // duplicate-order key look in a different day from the local guard.
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am)
        for (day in 7..13) {
            val date = LocalDate.of(2026, 11, day)
            val start = BusinessDate.windowStart(date, p)
            val end = BusinessDate.windowEnd(date, p)
            assertTrue("window for $date is empty", end > start)

            var instant = start
            while (instant < end) {
                assertEquals(
                    "$instant should belong to $date",
                    date.toString(),
                    BusinessDate.businessDate(instant, p)
                )
                instant += 15 * 60 * 1000L
            }
            // The last instant before the window belongs to the previous day.
            assertNotEquals(date.toString(), BusinessDate.businessDate(start - 1, p))
        }
    }

    @Test
    fun `only the date of the effective instant matters`() {
        val midnightActivation = profile(cutoff = "04:00", effectiveFrom = at(2026, 11, 10, 0, 0))
        val midDayActivation = profile(cutoff = "04:00", effectiveFrom = at(2026, 11, 10, 15, 0))
        val effectiveDate = LocalDate.of(2026, 11, 10)

        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 1, 0), midnightActivation))
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 1, 0), midDayActivation))
        assertEquals(
            BusinessDate.windowStart(effectiveDate, midnightActivation),
            BusinessDate.windowStart(effectiveDate, midDayActivation)
        )
    }

    @Test
    fun `an overnight service rolls over at closing time not midnight`() {
        val p = profile(opening = "18:00", closing = "02:10")
        // 01:00 is still the previous night's service.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 1, 0), p))
        // 02:10 is closing, but 60m settlement buffer (until 03:10) keeps table settlements on previous day.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 2, 10), p))
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 3, 9), p))
        // The settlement boundary (03:10) starts the next series.
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 3, 10), p))
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 23, 0), p))
    }

    @Test
    fun `midnight closing service gets 60m buffer until 01_00 AM`() {
        val p = profile(opening = "12:00", closing = "00:00")
        // 23:45 is on the previous day.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 9, 23, 45), p))
        // 00:30 AM is within 60m settlement buffer (until 01:00 AM) and belongs to Nov 09.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 0, 30), p))
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 0, 59), p))
        // At 01:00 AM, the shift rolls over to Nov 10.
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 1, 0), p))
    }

    @Test
    fun `late evening 23_30 closing gets 60m buffer until 00_30 AM`() {
        val p = profile(opening = "11:00", closing = "23:30")
        // 23:45 is on Nov 09.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 9, 23, 45), p))
        // 00:15 AM is within 60m buffer (until 00:30 AM) and belongs to Nov 09.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 0, 15), p))
        // At 00:30 AM, rolls over to Nov 10.
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 0, 30), p))
    }

    @Test
    fun `default 23_00 closing service rolls over at midnight`() {
        val p = profile(opening = "08:00", closing = "23:00")
        // 23:59 is on Nov 09.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 9, 23, 59), p))
        // 00:00 midnight rolls over to Nov 10.
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 0, 0), p))
    }

    @Test
    fun `late evening wrap applies even when opening is near midnight`() {
        val p = profile(opening = "00:10", closing = "23:50")
        // 23:50 + 60m buffer = 00:50 AM boundary.
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 0, 45), p))
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 0, 50), p))
    }

    @Test
    fun `a daytime service keeps the calendar day`() {
        val p = profile(opening = "08:10", closing = "14:20")
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 1, 0), p))
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 15, 0), p))
    }

    @Test
    fun `an activated cutoff still wins over the closing time`() {
        val p = profile(cutoff = "04:00", effectiveFrom = activatedAt4Am, opening = "18:00", closing = "02:10")
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 3, 59), p))
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 4, 0), p))
    }

    @Test
    fun `overnight windows are the exact inverse of the closing boundary`() {
        val p = profile(opening = "18:00", closing = "02:10")
        val day = LocalDate.of(2026, 11, 10)
        // With 60m settlement buffer, boundary is 03:10
        assertEquals(at(2026, 11, 10, 3, 10), BusinessDate.windowStart(day, p))
        assertEquals(at(2026, 11, 11, 3, 10), BusinessDate.windowEnd(day, p))
        assertEquals("2026-11-10", BusinessDate.businessDate(at(2026, 11, 10, 3, 10), p))
        assertEquals("2026-11-09", BusinessDate.businessDate(at(2026, 11, 10, 3, 9), p))
    }

    @Test
    fun `midnight closing windows are inverse of the 01_00 AM boundary`() {
        val p = profile(opening = "12:00", closing = "00:00")
        val day = LocalDate.of(2026, 11, 10)
        assertEquals(at(2026, 11, 10, 1, 0), BusinessDate.windowStart(day, p))
        assertEquals(at(2026, 11, 11, 1, 0), BusinessDate.windowEnd(day, p))
    }

    @Test
    fun `late evening closing windows are inverse of the 00_30 AM boundary`() {
        val p = profile(opening = "11:00", closing = "23:30")
        val day = LocalDate.of(2026, 11, 10)
        assertEquals(at(2026, 11, 10, 0, 30), BusinessDate.windowStart(day, p))
        assertEquals(at(2026, 11, 11, 0, 30), BusinessDate.windowEnd(day, p))
    }

    @Test
    fun `legacy windows stay midnight to midnight`() {
        val p = profile()
        val day = LocalDate.of(2026, 11, 6)
        assertEquals(at(2026, 11, 6, 0, 0), BusinessDate.windowStart(day, p))
        assertEquals(at(2026, 11, 7, 0, 0), BusinessDate.windowEnd(day, p))
    }

    @Test
    fun `closingBoundary returns null when no morning extension exists`() {
        // Default 23:00 has buffer ending at midnight -> null (calendar midnight rollover)
        org.junit.Assert.assertNull(BusinessDate.closingBoundary("08:00", "23:00"))
        // Daytime closing -> null
        org.junit.Assert.assertNull(BusinessDate.closingBoundary("08:00", "14:00"))
        // Overnight closing 02:10 -> 03:10
        assertEquals(java.time.LocalTime.of(3, 10), BusinessDate.closingBoundary("18:00", "02:10"))
        // Midnight closing 00:00 -> 01:00
        assertEquals(java.time.LocalTime.of(1, 0), BusinessDate.closingBoundary("12:00", "00:00"))
        // Late evening 23:30 -> 00:30
        assertEquals(java.time.LocalTime.of(0, 30), BusinessDate.closingBoundary("11:00", "23:30"))
    }
}
