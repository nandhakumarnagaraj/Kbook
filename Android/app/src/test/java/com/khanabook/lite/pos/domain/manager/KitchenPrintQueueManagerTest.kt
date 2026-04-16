package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.KitchenPrintQueueEntity
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.KitchenPrintQueueRepository
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.model.PrinterRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class KitchenPrintQueueManagerTest {

    private val queueRepository: KitchenPrintQueueRepository = mockk(relaxed = true)
    private val billRepository: BillRepository = mockk(relaxed = true)
    private val restaurantRepository: RestaurantRepository = mockk(relaxed = true)
    private val printerProfileRepository: PrinterProfileRepository = mockk(relaxed = true)
    private val printerManager: BluetoothPrinterManager = mockk(relaxed = true)
    private val connectedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private lateinit var manager: KitchenPrintQueueManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { printerManager.connectedDeviceEvents } returns connectedEvents
        manager = KitchenPrintQueueManager(
            queueRepository = queueRepository,
            billRepository = billRepository,
            restaurantRepository = restaurantRepository,
            printerProfileRepository = printerProfileRepository,
            printerManager = printerManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `flushPendingForPrinter prints unassigned queued job when configured kitchen printer reconnects`() = runTest {
        val connectedMac = "AA:BB:CC:DD:EE:FF"
        val queuedJob = KitchenPrintQueueEntity(
            id = 11L,
            billId = 42L,
            printerMac = ""
        )
        val kitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = connectedMac,
            enabled = true
        )
        val bill = BillWithItems(
            bill = BillEntity(
                id = 42L,
                dailyOrderId = 7,
                dailyOrderDisplay = "2026-04-16-07",
                lifetimeOrderId = 99L,
                subtotal = "100.0",
                totalAmount = "100.0",
                paymentMode = "cash",
                paymentStatus = "paid",
                orderStatus = "completed"
            ),
            items = emptyList(),
            payments = emptyList()
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { queueRepository.getPendingForPrinter(connectedMac) } returns listOf(queuedJob)
        coEvery { queueRepository.getByBillAndPrinter(queuedJob.billId, queuedJob.printerMac) } returns queuedJob
        coEvery { restaurantRepository.getProfile() } returns RestaurantProfileEntity(shopName = "KhanaBook")
        coEvery { billRepository.getBillWithItemsById(queuedJob.billId) } returns bill
        coEvery { printerManager.connect(connectedMac) } returns true
        coEvery { printerManager.printBytes(any()) } returns true

        manager.flushPendingForPrinter(connectedMac)

        coVerify(exactly = 1) { queueRepository.getByBillAndPrinter(queuedJob.billId, "") }
        coVerify(exactly = 1) { printerManager.connect(connectedMac) }
        coVerify(exactly = 1) { printerManager.printBytes(any()) }
        coVerify(exactly = 1) { queueRepository.deleteById(queuedJob.id) }
        coVerify(exactly = 1) { printerManager.disconnect() }
    }
}
