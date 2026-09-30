package com.khanabook.lite.pos.feature.billing.data

import androidx.work.WorkManager
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The "KOT Pending" tile is rendered inside the same "Today's Summary" card as
 * Orders / Avg Order, both of which are day- and scope-bounded. These tests pin the
 * scope/terminal routing that makes the KOT counter honour the same bounds, so a
 * future refactor cannot silently fall back to the queue's all-time count (which stays
 * non-zero forever for a stale un-dispatched ticket) or to a scope-blind count.
 *
 * Query semantics (COUNT DISTINCT bill_id, day window, status enumeration) live in the
 * Room @Query itself; this suite guards which query gets called with which arguments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KotPendingCountRoutingTest {

    private lateinit var billDao: BillDao
    private lateinit var repository: BillRepository

    private val restaurantId = 42L
    private val terminalId = "TERM-1"
    private val dayStart = 1_700_000_000_000L
    private val dayEnd = 1_700_086_399_999L

    @Before
    fun setUp() {
        billDao = mockk(relaxed = true)
        repository = BillRepository(
            billDao = billDao,
            restaurantDao = mockk<RestaurantDao>(relaxed = true),
            workManager = mockk<WorkManager>(relaxed = true),
            kotEventDao = mockk<KotEventDao>(relaxed = true),
            sessionManager = mockk<SessionManager>(relaxed = true)
        )
    }

    private fun count(allTerminals: Boolean, resolvedTerminal: String = terminalId): Int =
        kotlinx.coroutines.runBlocking {
            repository.countBillsWithPendingKdsForDay(
                restaurantId = restaurantId,
                terminalId = resolvedTerminal,
                startMillis = dayStart,
                endMillis = dayEnd,
                allTerminals = allTerminals
            ).first()
        }

    @Test
    fun `THIS_COUNTER scope counts only the current terminal`() {
        every {
            billDao.countBillsWithPendingKdsForDay(restaurantId, terminalId, dayStart, dayEnd)
        } returns MutableStateFlow(3)
        every {
            billDao.countBillsWithPendingKdsForDayAllTerminals(restaurantId, dayStart, dayEnd)
        } returns MutableStateFlow(99)

        assertEquals(3, count(allTerminals = false))

        verify(exactly = 1) {
            billDao.countBillsWithPendingKdsForDay(restaurantId, terminalId, dayStart, dayEnd)
        }
        verify(exactly = 0) {
            billDao.countBillsWithPendingKdsForDayAllTerminals(any(), any(), any())
        }
    }

    @Test
    fun `SHOP_TOTAL scope ignores terminal but still bounds the day`() {
        every {
            billDao.countBillsWithPendingKdsForDayAllTerminals(restaurantId, dayStart, dayEnd)
        } returns MutableStateFlow(7)
        every {
            billDao.countBillsWithPendingKdsForDay(restaurantId, terminalId, dayStart, dayEnd)
        } returns MutableStateFlow(3)

        assertEquals(7, count(allTerminals = true))

        verify(exactly = 1) {
            billDao.countBillsWithPendingKdsForDayAllTerminals(restaurantId, dayStart, dayEnd)
        }
        verify(exactly = 0) {
            billDao.countBillsWithPendingKdsForDay(any(), any(), any(), any())
        }
    }

    @Test
    fun `day bounds are passed through to the query rather than defaulted`() {
        every {
            billDao.countBillsWithPendingKdsForDay(restaurantId, terminalId, any(), any())
        } returns MutableStateFlow(0)

        count(allTerminals = false)

        verify(exactly = 1) {
            billDao.countBillsWithPendingKdsForDay(restaurantId, terminalId, dayStart, dayEnd)
        }
    }

    @Test
    fun `unresolved terminal scope yields zero instead of counting every terminal`() {
        assertEquals(0, count(allTerminals = false, resolvedTerminal = ""))

        // Critically it must NOT fall back to the shop-wide query: that would leak
        // other terminals' pending KOTs into this counter's summary card.
        verify(exactly = 0) {
            billDao.countBillsWithPendingKdsForDay(any(), any(), any(), any())
        }
        verify(exactly = 0) {
            billDao.countBillsWithPendingKdsForDayAllTerminals(any(), any(), any())
        }
    }

    @Test
    fun `SHOP_TOTAL still works when the local terminal scope is unresolved`() {
        every {
            billDao.countBillsWithPendingKdsForDayAllTerminals(restaurantId, dayStart, dayEnd)
        } returns MutableStateFlow(4)

        assertEquals(4, count(allTerminals = true, resolvedTerminal = ""))
    }

    @Test
    fun `terminal series is honoured as a fallback scope`() = runTest {
        every {
            billDao.countBillsWithPendingKdsForDay(restaurantId, "SERIES-9", dayStart, dayEnd)
        } returns MutableStateFlow(6)

        val result = repository.countBillsWithPendingKdsForDay(
            restaurantId = restaurantId,
            terminalId = "SERIES-9",
            startMillis = dayStart,
            endMillis = dayEnd,
            allTerminals = false
        ).first()

        assertEquals(6, result)
    }
}
