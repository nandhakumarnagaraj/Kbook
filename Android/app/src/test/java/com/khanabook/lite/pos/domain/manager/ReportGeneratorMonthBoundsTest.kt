package com.khanabook.lite.pos.domain.manager

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportGeneratorMonthBoundsTest {

    @Test
    fun `february non-leap ends on 28 not 31`() {
        val (start, end) = ReportGenerator.monthRangeBounds(2026, 2)
        assertEquals("2026-02-01 00:00:00", start)
        assertEquals("2026-02-28 23:59:59", end)
    }

    @Test
    fun `february leap year ends on 29`() {
        val (_, end) = ReportGenerator.monthRangeBounds(2028, 2)
        assertEquals("2028-02-29 23:59:59", end)
    }

    @Test
    fun `thirty-day months end on 30`() {
        assertEquals("2026-04-30 23:59:59", ReportGenerator.monthRangeBounds(2026, 4).second)
        assertEquals("2026-06-30 23:59:59", ReportGenerator.monthRangeBounds(2026, 6).second)
        assertEquals("2026-09-30 23:59:59", ReportGenerator.monthRangeBounds(2026, 9).second)
        assertEquals("2026-11-30 23:59:59", ReportGenerator.monthRangeBounds(2026, 11).second)
    }

    @Test
    fun `thirty-one-day months end on 31`() {
        assertEquals("2026-01-31 23:59:59", ReportGenerator.monthRangeBounds(2026, 1).second)
        assertEquals("2026-12-31 23:59:59", ReportGenerator.monthRangeBounds(2026, 12).second)
    }

    @Test
    fun `month is zero padded`() {
        val (start, _) = ReportGenerator.monthRangeBounds(2026, 3)
        assertEquals("2026-03-01 00:00:00", start)
    }
}
