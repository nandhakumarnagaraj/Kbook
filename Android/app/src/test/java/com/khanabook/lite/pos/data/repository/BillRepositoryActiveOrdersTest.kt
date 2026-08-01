package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.local.relation.BillWithItems
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillRepositoryActiveOrdersTest {
    @Test
    fun `active orders rebind when recovered terminal identity changes`() = runTest {
        val restaurantId = MutableStateFlow(44L)
        val terminalScope = MutableStateFlow<String?>("terminal-before-recovery")
        val firstRows = listOf(mockk<BillWithItems>())
        val recoveredRows = listOf(mockk<BillWithItems>())

        val emissions = mutableListOf<List<BillWithItems>>()
        val collection = launch(UnconfinedTestDispatcher(testScheduler)) {
            terminalScopedFlow(restaurantId, terminalScope) { _, terminalId ->
                flowOf(if (terminalId == "terminal-before-recovery") firstRows else recoveredRows)
            }.take(2).toList(emissions)
        }

        terminalScope.value = "terminal-recovered"
        collection.join()

        assertEquals(listOf(firstRows, recoveredRows), emissions)
    }
}
