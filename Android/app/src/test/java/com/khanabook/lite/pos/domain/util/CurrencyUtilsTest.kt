package com.khanabook.lite.pos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun `formatPrice with Double works correctly`() {
        val result = CurrencyUtils.formatPrice(123.456, "₹")
        assertEquals("₹ 123.46", result)
    }

    @Test
    fun `formatPrice with valid String works correctly`() {
        val result = CurrencyUtils.formatPrice("123.456", "₹")
        assertEquals("₹ 123.46", result)
    }

    @Test
    fun `formatPrice with invalid String returns zero formatted`() {
        val result = CurrencyUtils.formatPrice("invalid", "₹")
        assertEquals("₹ 0.00", result)
    }

    @Test
    fun `formatPrice with null String returns zero formatted`() {
        val result = CurrencyUtils.formatPrice(null as String?, "₹")
        assertEquals("₹ 0.00", result)
    }

    @Test
    fun `formatPrice with empty String returns zero formatted`() {
        val result = CurrencyUtils.formatPrice("", "₹")
        assertEquals("₹ 0.00", result)
    }
}
