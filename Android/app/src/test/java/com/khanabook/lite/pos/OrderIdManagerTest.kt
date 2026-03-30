package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderIdManagerTest {

    @Test
    fun `isResetNeeded should return true if lastResetDate is different from today`() {
        val profile = RestaurantProfileEntity(
            shopName = "Test",
            shopAddress = "Test",
            whatsappNumber = "123",
            lastResetDate = "2026-02-28" 
        )
        val today = "2026-03-01"
        
        assertTrue(OrderIdManager.isResetNeeded(profile, today))
    }

    @Test
    fun `isResetNeeded should return false if lastResetDate is same as today`() {
        val profile = RestaurantProfileEntity(
            shopName = "Test",
            shopAddress = "Test",
            whatsappNumber = "123",
            lastResetDate = "2026-03-01"
        )
        val today = "2026-03-01"
        
        assertFalse(OrderIdManager.isResetNeeded(profile, today))
    }

    @Test
    fun `getNextDailyCounter should return 1 if reset is needed`() {
        val profile = RestaurantProfileEntity(
            shopName = "Test",
            shopAddress = "Test",
            whatsappNumber = "123",
            dailyOrderCounter = 10,
            lastResetDate = "2026-02-28"
        )
        val today = "2026-03-01"
        
        assertEquals(1, OrderIdManager.getNextDailyCounter(profile, today))
    }

    @Test
    fun `getNextDailyCounter should return counter plus 1 if reset is not needed`() {
        val profile = RestaurantProfileEntity(
            shopName = "Test",
            shopAddress = "Test",
            whatsappNumber = "123",
            dailyOrderCounter = 10,
            lastResetDate = "2026-03-01"
        )
        val today = "2026-03-01"
        
        assertEquals(11, OrderIdManager.getNextDailyCounter(profile, today))
    }

    @Test
    fun `getDailyOrderDisplay should format counter with leading zeros`() {
        assertEquals("001", OrderIdManager.getDailyOrderDisplay("2026-03-01", 1))
        assertEquals("010", OrderIdManager.getDailyOrderDisplay("2026-03-01", 10))
        assertEquals("100", OrderIdManager.getDailyOrderDisplay("2026-03-01", 100))
    }
}

