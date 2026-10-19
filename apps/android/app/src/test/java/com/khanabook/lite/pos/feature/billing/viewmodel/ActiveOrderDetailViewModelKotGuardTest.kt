package com.khanabook.lite.pos.feature.billing.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.khanabook.lite.pos.core.feedback.UiMessage
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.printing.domain.PrintRouter
import com.khanabook.lite.pos.domain.model.OrderStatus
import com.khanabook.lite.pos.domain.model.PaymentStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveOrderDetailViewModelKotGuardTest {

    private lateinit var billRepository: BillRepository
    private lateinit var printRouter: PrintRouter
    private lateinit var viewModel: ActiveOrderDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        billRepository = mockk(relaxed = true)
        printRouter = mockk(relaxed = true)

        val bill = mockk<BillEntity>(relaxed = true)
        every { bill.orderStatus } returns OrderStatus.DRAFT.dbValue
        every { bill.paymentStatus } returns PaymentStatus.PENDING.dbValue
        every { bill.publicToken } returns TOKEN

        // An item that looks unsent: the guard's first check passes, so only the event check stops the print.
        val item = mockk<BillItemEntity>(relaxed = true)
        every { item.sentToKot } returns false

        val billWithItems = mockk<BillWithItems>(relaxed = true)
        every { billWithItems.bill } returns bill
        every { billWithItems.items } returns listOf(item)
        coEvery { billRepository.getBillWithItemsById(BILL_ID) } returns billWithItems

        viewModel = ActiveOrderDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("billId" to BILL_ID)),
            billRepository = billRepository,
            restaurantRepository = mockk<RestaurantRepository>(relaxed = true),
            printRouter = printRouter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `updateKot does not print when no unprinted KOT event exists`() {
        coEvery { billRepository.hasUnprintedKotEvents(TOKEN) } returns false
        val messages = CopyOnWriteArrayList<UiMessage>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { viewModel.message.collect { messages += it } }

        viewModel.updateKot()
        awaitMessage(messages)

        coVerify(exactly = 0) { printRouter.printBill(any(), any(), any()) }
        assertEquals("Kitchen ticket already printed", messages.single().message)
        job.cancel()
    }

    /** updateKot runs on Dispatchers.IO, so poll briefly for its emitted message. */
    private fun awaitMessage(messages: List<UiMessage>) {
        val deadline = System.currentTimeMillis() + 5_000
        while (messages.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("Expected a warning message from updateKot", messages.isNotEmpty())
    }

    private companion object {
        const val BILL_ID = 1L
        const val TOKEN = "bill-token"
    }
}
