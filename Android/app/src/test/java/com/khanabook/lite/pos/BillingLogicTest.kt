package com.khanabook.lite.pos

import org.junit.Test
import org.junit.Assert.*

class BillingLogicTest {

    @Test
    fun testAddToCartStockLogic() {
        val itemName = "Burger"
        val stockQuantity = 5
        val lowStockThreshold = 2
        
        var cartQuantity = 0
        var errorMessage: String? = null
        
        fun addToCartSim(currentQty: Int) {
            if (currentQty >= stockQuantity) {
                errorMessage = "Reached maximum stock for $itemName"
                return
            }
            
            val remainingAfterAdd = stockQuantity - (currentQty + 1)
            if (remainingAfterAdd <= lowStockThreshold && remainingAfterAdd > 0) {
                errorMessage = "Running out of stock for $itemName"
            } else if (remainingAfterAdd == 0) {
                errorMessage = "Reached maximum stock for $itemName"
            } else {
                errorMessage = null
            }
            cartQuantity = currentQty + 1
        }
        
        
        addToCartSim(0)
        assertNull("Error at 1: $errorMessage", errorMessage)
        
        
        addToCartSim(1)
        assertNull("Error at 2: $errorMessage", errorMessage)
        
        
        addToCartSim(2)
        assertEquals("Running out of stock for Burger", errorMessage)
        
        
        addToCartSim(3)
        assertEquals("Running out of stock for Burger", errorMessage)
        
        
        addToCartSim(4)
        assertEquals("Reached maximum stock for Burger", errorMessage)
        
        
        addToCartSim(5)
        assertEquals("Reached maximum stock for Burger", errorMessage)
    }
}


