package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.printing.domain.BluetoothPrinterManager
import com.khanabook.lite.pos.feature.printing.domain.KitchenPrintQueueManager

import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import com.khanabook.lite.pos.feature.printing.data.KotEventEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueEntity
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.billing.data.BillRepository
import com.khanabook.lite.pos.feature.printing.data.KitchenPrintQueueRepository
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileRepository
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import com.khanabook.lite.pos.feature.printing.domain.PrinterTransportDispatcher
import com.khanabook.lite.pos.feature.printing.domain.BluetoothPrinterTransport
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KitchenPrintQueueManagerTest {

    private val queueRepository: KitchenPrintQueueRepository = mockk(relaxed = true)
    private val billRepository: BillRepository = mockk(relaxed = true)
    private val restaurantRepository: RestaurantRepository = mockk(relaxed = true)
    private val printerProfileRepository: PrinterProfileRepository = mockk(relaxed = true)
    private val printerManager: BluetoothPrinterManager = mockk(relaxed = true)
    private val printerTransport = PrinterTransportDispatcher(
        BluetoothPrinterTransport(printerManager),
        mockk(relaxed = true),
        mockk(relaxed = true)
    )
    private val kotEventDao: KotEventDao = mockk(relaxed = true)
    private val connectedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private lateinit var manager: KitchenPrintQueueManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { printerManager.connectedDeviceEvents } returns connectedEvents
        every { printerManager.connectedDeviceMac } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        every { printerManager.isConnectedTo(any()) } returns false
        manager = KitchenPrintQueueManager(
            queueRepository = queueRepository,
            billRepository = billRepository,
            restaurantRepository = restaurantRepository,
            printerProfileRepository = printerProfileRepository,
            printerManager = printerManager,
            printerTransport = printerTransport,
            kotEventDao = kotEventDao
        )
    }

    @After
    fun tearDown() {
        manager.destroy()
        Thread.sleep(100)
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
            items = listOf(
                BillItemEntity(
                    id = 1L,
                    billId = 42L,
                    menuItemId = 20L,
                    itemName = "Tea",
                    price = "100.0",
                    quantity = 1,
                    itemTotal = "100.0",
                    sentToKot = false
                )
            ),
            payments = emptyList()
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { queueRepository.getPendingForPrinter(connectedMac) } returns listOf(queuedJob)
        coEvery { queueRepository.getByBillAndPrinter(queuedJob.billId, connectedMac) } returns queuedJob
        coEvery { queueRepository.claimPendingForRetry(queuedJob.id) } returns true
        coEvery { restaurantRepository.getProfile() } returns RestaurantProfileEntity(shopName = "KhanaBook")
        coEvery { billRepository.getBillWithItemsById(queuedJob.billId) } returns bill
        coEvery { printerManager.connect(connectedMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        manager.flushPendingForPrinter(connectedMac)

        coVerify(exactly = 1) { printerManager.connect(connectedMac) }
        coVerify(exactly = 1) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 1) { queueRepository.markSent(queuedJob.id) }
        coVerify(exactly = 0) { printerManager.disconnect() }
    }

    // -------------------------------------------------------------------------
    // Reconnect scenario: KDS queued with kitchen MAC → kitchen reconnects → prints
    // -------------------------------------------------------------------------

    @Test
    fun `queued KDS job prints automatically when kitchen printer reconnects`() = runTest {
        val kitchenMac = "AA:BB:CC:DD:EE:02"
        val queuedJob = KitchenPrintQueueEntity(id = 20L, billId = 55L, printerMac = kitchenMac)
        val kitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = kitchenMac,
            enabled = true
        )
        val bill = BillWithItems(
            bill = BillEntity(
                id = 55L,
                dailyOrderId = 3,
                dailyOrderDisplay = "2026-04-17-03",
                lifetimeOrderId = 55L,
                subtotal = "200.0",
                totalAmount = "200.0",
                paymentMode = "cash",
                paymentStatus = "paid",
                orderStatus = "completed"
            ),
            items = listOf(
                BillItemEntity(
                    id = 2L,
                    billId = 55L,
                    menuItemId = 21L,
                    itemName = "Coffee",
                    price = "200.0",
                    quantity = 1,
                    itemTotal = "200.0",
                    sentToKot = false
                )
            ),
            payments = emptyList()
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { queueRepository.getPendingForPrinter(kitchenMac) } returns listOf(queuedJob)
        coEvery { queueRepository.getByBillAndPrinter(queuedJob.billId, queuedJob.printerMac) } returns queuedJob
        coEvery { queueRepository.claimPendingForRetry(queuedJob.id) } returns true
        coEvery { restaurantRepository.getProfile() } returns RestaurantProfileEntity(shopName = "KhanaBook")
        coEvery { billRepository.getBillWithItemsById(queuedJob.billId) } returns bill
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        // Simulate kitchen printer reconnecting — fires connectedDeviceEvents
        manager.flushPendingForPrinter(kitchenMac)

        // Queue was flushed: connected, printed, entry removed
        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
        coVerify(exactly = 1) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 1) { queueRepository.markSent(queuedJob.id) }
        // Connection stays open (no disconnect called)
        coVerify(exactly = 0) { printerManager.disconnect() }
    }

    @Test
    fun `queued KDS job stays in queue when kitchen printer reconnect fails`() = runTest {
        val kitchenMac = "AA:BB:CC:DD:EE:02"
        val queuedJob = KitchenPrintQueueEntity(id = 21L, billId = 56L, printerMac = kitchenMac)
        val kitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = kitchenMac,
            enabled = true
        )
        val bill = BillWithItems(
            bill = BillEntity(
                id = 56L,
                dailyOrderId = 4,
                dailyOrderDisplay = "2026-04-17-04",
                lifetimeOrderId = 56L,
                subtotal = "150.0",
                totalAmount = "150.0",
                paymentMode = "cash",
                paymentStatus = "paid",
                orderStatus = "completed"
            ),
            items = listOf(
                BillItemEntity(
                    id = 3L,
                    billId = 56L,
                    menuItemId = 22L,
                    itemName = "Soup",
                    price = "150.0",
                    quantity = 1,
                    itemTotal = "150.0",
                    sentToKot = false
                )
            ),
            payments = emptyList()
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { queueRepository.getPendingForPrinter(kitchenMac) } returns listOf(queuedJob)
        coEvery { queueRepository.getByBillAndPrinter(queuedJob.billId, queuedJob.printerMac) } returns queuedJob
        coEvery { queueRepository.claimPendingForRetry(queuedJob.id) } returns true
        coEvery { restaurantRepository.getProfile() } returns RestaurantProfileEntity(shopName = "KhanaBook")
        coEvery { billRepository.getBillWithItemsById(queuedJob.billId) } returns bill
        coEvery { printerManager.connect(kitchenMac) } returns false  // connection fails

        manager.flushPendingForPrinter(kitchenMac)

        // Connect attempted but failed → job re-enqueued, not deleted
        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
        coVerify(exactly = 0) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 0) { queueRepository.deleteById(any()) }
        coVerify(exactly = 1) { queueRepository.markPending(queuedJob.id, any()) }
    }

    @Test
    fun `customer printer reconnect does NOT trigger KDS flush`() = runTest {
        val customerMac = "AA:BB:CC:DD:EE:01"
        val customerPrinter = PrinterProfileEntity(
            role = PrinterRole.CUSTOMER.name,
            name = "Customer Printer",
            macAddress = customerMac,
            enabled = true
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(customerPrinter)

        // Customer printer reconnects — should NOT flush KDS queue
        connectedEvents.emit(customerMac)
        Thread.sleep(100)
        advanceUntilIdle()

        coVerify(exactly = 0) { printerManager.connect(any<String>()) }
        coVerify(exactly = 0) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 0) { queueRepository.deleteById(any()) }
    }

    @Test
    fun `customer printer reconnect does NOT claim unassigned KDS queue`() = runTest {
        val customerMac = "AA:BB:CC:DD:EE:01"
        val queuedJob = KitchenPrintQueueEntity(id = 22L, billId = 57L, printerMac = "")
        val customerPrinter = PrinterProfileEntity(
            role = PrinterRole.CUSTOMER.name,
            name = "Customer Printer",
            macAddress = customerMac,
            enabled = true
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(customerPrinter)
        coEvery { queueRepository.getPendingForPrinter(customerMac) } returns listOf(queuedJob)

        connectedEvents.emit(customerMac)
        Thread.sleep(100)
        advanceUntilIdle()

        coVerify(exactly = 0) { queueRepository.getPendingForPrinter(customerMac) }
        coVerify(exactly = 0) { printerManager.connect(any<String>()) }
        coVerify(exactly = 0) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 0) { queueRepository.deleteById(any()) }
    }

    // -------------------------------------------------------------------------
    // Stale job: the events it points at were already printed by a direct
    // dispatch — flush must ACK the job, never re-print the ticket.
    // -------------------------------------------------------------------------

    @Test
    fun `flush acks stale job whose events already printed - no re-print`() = runTest {
        val kitchenMac = "AA:BB:CC:DD:EE:02"
        val queuedJob = KitchenPrintQueueEntity(
            id = 30L,
            billId = 70L,
            printerMac = "",
            publicToken = "pt-70",
            kotRevision = "4"
        )
        val kitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = kitchenMac,
            enabled = true
        )
        val bill = BillWithItems(
            bill = BillEntity(
                id = 70L,
                dailyOrderId = 7,
                dailyOrderDisplay = "2026-09-25-70",
                lifetimeOrderId = null,
                subtotal = "100.0",
                totalAmount = "100.0",
                paymentMode = "cash",
                paymentStatus = "pending",
                orderStatus = "draft",
                publicToken = "pt-70"
            ),
            items = emptyList(),
            payments = emptyList()
        )
        val printedEvent = KotEventEntity(
            publicToken = "pt-70",
            kotRevision = "4",
            eventType = KotEventType.VOID,
            itemSnapshotJson = "[{\"id\":1,\"quantity\":1,\"itemName\":\"Tea\"}]",
            originatingDeviceId = "D1",
            isPrinted = true
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { queueRepository.getPendingForPrinter(kitchenMac) } returns listOf(queuedJob)
        coEvery { queueRepository.claimPendingForRetry(queuedJob.id) } returns true
        coEvery { restaurantRepository.getProfile() } returns RestaurantProfileEntity(shopName = "KhanaBook")
        coEvery { billRepository.getBillWithItemsById(queuedJob.billId) } returns bill
        coEvery { kotEventDao.getEvent("pt-70", "4") } returns printedEvent
        coEvery { kotEventDao.getUnprintedEventsForBill("pt-70") } returns emptyList()

        manager.flushPendingForPrinter(kitchenMac)

        coVerify(exactly = 0) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 1) { queueRepository.markSent(queuedJob.id) }
        coVerify(exactly = 0) { queueRepository.markPending(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // CANCEL notice: bill status is already 'cancelled' when the job flushes —
    // the status gate must NOT drop it, the slip must reach paper.
    // -------------------------------------------------------------------------

    @Test
    fun `flush prints CANCEL notice even though bill status is cancelled`() = runTest {
        val kitchenMac = "AA:BB:CC:DD:EE:02"
        val queuedJob = KitchenPrintQueueEntity(
            id = 31L,
            billId = 71L,
            printerMac = kitchenMac,
            publicToken = "pt-71",
            kotRevision = "6"
        )
        val kitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = kitchenMac,
            enabled = true
        )
        val cancelledBill = BillWithItems(
            bill = BillEntity(
                id = 71L,
                dailyOrderId = 8,
                dailyOrderDisplay = "2026-09-25-71",
                lifetimeOrderId = null,
                subtotal = "150.0",
                totalAmount = "150.0",
                paymentMode = "cash",
                paymentStatus = "pending",
                orderStatus = "cancelled",
                cancelReason = "Guest left",
                publicToken = "pt-71"
            ),
            items = emptyList(),
            payments = emptyList()
        )
        val cancelEvent = KotEventEntity(
            publicToken = "pt-71",
            kotRevision = "6",
            eventType = KotEventType.CANCEL,
            itemSnapshotJson = "[{\"id\":1,\"quantity\":2,\"itemName\":\"Dosa\"}]",
            originatingDeviceId = "D1",
            isPrinted = false
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { queueRepository.getPendingForPrinter(kitchenMac) } returns listOf(queuedJob)
        coEvery { queueRepository.claimPendingForRetry(queuedJob.id) } returns true
        coEvery { restaurantRepository.getProfile() } returns RestaurantProfileEntity(shopName = "KhanaBook")
        coEvery { billRepository.getBillWithItemsById(queuedJob.billId) } returns cancelledBill
        coEvery { kotEventDao.getEvent("pt-71", "6") } returns cancelEvent
        coEvery { kotEventDao.getUnprintedEventsForBill("pt-71") } returns listOf(cancelEvent)
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        manager.flushPendingForPrinter(kitchenMac)

        coVerify(exactly = 1) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 1) { queueRepository.markSent(queuedJob.id) }
        coVerify(exactly = 0) { queueRepository.deleteById(any()) }
        coVerify(exactly = 1) { kotEventDao.markPrinted("pt-71", "6") }
    }
}