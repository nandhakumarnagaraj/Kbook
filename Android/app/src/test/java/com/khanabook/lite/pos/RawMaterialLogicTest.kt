package com.khanabook.lite.pos

import org.junit.Assert.*
import org.junit.Test

class RawMaterialLogicTest {

    private fun getStatus(current: Double, threshold: Double): String {
        return when {
            current <= 0 -> "OUT"
            current <= threshold -> "LOW"
            else -> "HEALTHY"
        }
    }

    @Test
    fun `test stock status boundaries`() {
        val threshold = 5.0

        assertEquals("OUT", getStatus(0.0, threshold))
        assertEquals("OUT", getStatus(-1.0, threshold))

        assertEquals("LOW", getStatus(0.1, threshold))
        assertEquals("LOW", getStatus(5.0, threshold))

        assertEquals("HEALTHY", getStatus(5.1, threshold))
        assertEquals("HEALTHY", getStatus(100.0, threshold))
    }

    @Test
    fun `test decimal precision adjustments`() {
        var stock = 10.0
        val consumption = 0.55

        stock -= consumption
        assertEquals(9.45, stock, 0.001)
    }
}
