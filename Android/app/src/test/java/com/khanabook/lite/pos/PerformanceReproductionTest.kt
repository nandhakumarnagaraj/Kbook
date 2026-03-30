package com.khanabook.lite.pos

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceReproductionTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testUpdateSummaryDebouncing() = runTest {
        var summaryUpdateCount = 0
        val cartItems = MutableStateFlow<List<String>>(emptyList())

        
        backgroundScope.launch {
            cartItems
                .debounce(300)
                .onEach { 
                    summaryUpdateCount++ 
                }
                .collect()
        }

        
        cartItems.value = listOf("Item 1")
        advanceTimeBy(100)
        cartItems.value = listOf("Item 1", "Item 2")
        advanceTimeBy(100)
        cartItems.value = listOf("Item 1", "Item 2", "Item 3")
        
        
        assertEquals(0, summaryUpdateCount)

        
        advanceTimeBy(301)
        
        
        assertEquals(1, summaryUpdateCount)
        
        
        advanceTimeBy(1000)
        cartItems.value = listOf("Item 1", "Item 2", "Item 3", "Item 4")
        advanceTimeBy(301)
        
        
        assertEquals(2, summaryUpdateCount)
    }
}
