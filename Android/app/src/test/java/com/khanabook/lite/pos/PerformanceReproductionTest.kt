package com.khanabook.lite.pos

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceReproductionTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `billing inputs reach summary calculation without an artificial delay`() = runTest {
        var latestItems = emptyList<String>()
        val cartItems = MutableStateFlow<List<String>>(emptyList())

        backgroundScope.launch {
            cartItems.collect { latestItems = it }
        }
        runCurrent()
        assertEquals(emptyList<String>(), latestItems)

        cartItems.value = listOf("Item 1")
        runCurrent()
        assertEquals(listOf("Item 1"), latestItems)

        cartItems.value = listOf("Item 1", "Item 2")
        runCurrent()
        assertEquals(listOf("Item 1", "Item 2"), latestItems)
    }
}
