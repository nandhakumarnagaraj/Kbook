package com.khanabook.lite.pos.domain.manager
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.printing.domain.PrintDispatchMode
import com.khanabook.lite.pos.feature.printing.domain.BluetoothPrinterManager
import com.khanabook.lite.pos.feature.printing.domain.KitchenPrintQueueManager
import com.khanabook.lite.pos.feature.printing.domain.KitchenTicketFormatter
import com.khanabook.lite.pos.feature.printing.domain.PrintRouter

import android.content.Context
import com.khanabook.lite.pos.feature.billing.data.BillDao
import com.khanabook.lite.pos.feature.printing.data.KotEventDao
import com.khanabook.lite.pos.feature.billing.data.BillEntity
import com.khanabook.lite.pos.feature.billing.data.BillItemEntity
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.billing.data.BillWithItems
import com.khanabook.lite.pos.feature.printing.data.KotEventEntity
import com.khanabook.lite.pos.feature.printing.data.KotEventType
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileRepository
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import com.khanabook.lite.pos.feature.billing.domain.InvoiceFormatter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests the single-printer flow:
 *   - Only customer receipt printer connected → receipt prints, KDS goes to queue.
 *   - Only kitchen printer connected → KDS prints, customer receipt skipped.
 *   - Customer printer connected, kitchen configured but offline → receipt prints, KDS queued.
 */
class PrintRouterTest {

    private val context: Context = mockk(relaxed = true)
    private val printerProfileRepository: PrinterProfileRepository = mockk(relaxed = true)
    private val printerManager: BluetoothPrinterManager = mockk(relaxed = true)
    private val kitchenQueueManager: KitchenPrintQueueManager = mockk(relaxed = true)
    private val billDao: BillDao = mockk(relaxed = true)
    private val kotEventDao: KotEventDao = mockk(relaxed = true)
    private val sessionManager: SessionManager = mock()

    private lateinit var router: PrintRouter

    private val customerMac = "AA:BB:CC:DD:EE:01"
    private val kitchenMac  = "AA:BB:CC:DD:EE:02"

    private val restaurantProfile = RestaurantProfileEntity(shopName = "TestShop")

    private val bill = BillWithItems(
        bill = BillEntity(
            id = 1L,
            dailyOrderId = 1,
            dailyOrderDisplay = "2026-04-17-01",
            lifetimeOrderId = 1L,
            subtotal = "100.0",
            totalAmount = "100.0",
            paymentMode = "cash",
            paymentStatus = "paid",
            orderStatus = "completed"
        ),
        items = listOf(
            BillItemEntity(
                id = 10L,
                billId = 1L,
                menuItemId = 20L,
                itemName = "Tea",
                price = "100.0",
                quantity = 1,
                itemTotal = "100.0"
            )
        ),
        payments = emptyList()
    )

    private val customerPrinter = PrinterProfileEntity(
        role = PrinterRole.CUSTOMER.name,
        name = "Customer Printer",
        macAddress = customerMac,
        enabled = true,
        autoPrint = true
    )

    private val kitchenPrinter = PrinterProfileEntity(
        role = PrinterRole.KITCHEN.name,
        name = "Kitchen Printer",
        macAddress = kitchenMac,
        enabled = true,
        autoPrint = false
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockkObject(InvoiceFormatter)
        every { InvoiceFormatter.formatForThermalPrinter(any(), any(), any()) } returns byteArrayOf(0x01)

        mockkObject(KitchenTicketFormatter)
        every { KitchenTicketFormatter.format(any(), any(), any(), any()) } returns byteArrayOf(0x02)
        every {
            KitchenTicketFormatter.formatCombinedTicket(any(), any(), any(), any<List<com.khanabook.lite.pos.feature.printing.domain.KotTicketSection>>())
        } returns byteArrayOf(0x03)

        every { printerManager.connectedDeviceEvents } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { printerManager.connectedDeviceMac } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        whenever(sessionManager.getDeviceId()).thenReturn("")

        router = PrintRouter(context, printerProfileRepository, printerManager, kitchenQueueManager, billDao, kotEventDao, sessionManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Only customer printer connected, no kitchen printer configured
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - only customer printer configured - receipt prints and KDS queued as unassigned`() = runTest {
        val blankKitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = "",
            enabled = true,
            autoPrint = false
        )
        coEvery { printerProfileRepository.getProfiles() } returns listOf(customerPrinter)
        coEvery { printerProfileRepository.getByRole(PrinterRole.KITCHEN.name) } returns blankKitchenPrinter
        coEvery { printerManager.connect(customerMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        // Receipt printed successfully
        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))
        assertTrue(result.failures.isEmpty())

        // Customer printer connected and printed
        coVerify(exactly = 1) { printerManager.connect(customerMac) }
        coVerify(exactly = 1) { printerManager.printBytesTo(any(), any()) }

        // No kitchen printer configured → unassigned queue entry
        coVerify(exactly = 1) { kitchenQueueManager.enqueueUnassigned(bill.bill.id, any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Customer printer connected, kitchen configured but OFFLINE
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - customer online kitchen offline - receipt prints and KDS queued for kitchen MAC`() = runTest {
        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter, customerPrinter)
        coEvery { printerManager.connect(kitchenMac) } returns false   // kitchen offline
        coEvery { printerManager.connect(customerMac) } returns true   // customer online
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        // Customer receipt succeeds
        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))

        // Kitchen failed → queued with kitchen MAC
        coVerify(exactly = 1) { kitchenQueueManager.enqueue(bill.bill.id, kitchenMac, any(), true) }

        // Customer connected and printed, kitchen connection was attempted but failed
        coVerify(exactly = 1) { printerManager.connect(customerMac) }
        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Only kitchen printer connected, no customer printer configured
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - only kitchen printer configured - KDS prints no receipt`() = runTest {
        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        // Kitchen ticket printed
        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.KITCHEN.name))

        // KDS queue pre-cleared before printing (normal flow)
        coVerify(exactly = 1) { kitchenQueueManager.markPrinted(bill.bill.id, kitchenMac) }

        // Kitchen connected and printed
        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
        coVerify(exactly = 1) { printerManager.printBytesTo(any(), any()) }

        // No customer printer → no customer print attempted
        coVerify(exactly = 0) { printerManager.connect(customerMac) }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Both printers connected (happy path)
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - both printers connected - receipt and KDS both print`() = runTest {
        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter, customerPrinter)
        coEvery { printerManager.connect(any<String>()) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        assertEquals(2, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.KITCHEN.name))
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))
        assertTrue(result.failures.isEmpty())

        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
        coVerify(exactly = 1) { printerManager.connect(customerMac) }
        coVerify(exactly = 2) { printerManager.printBytesTo(any(), any()) }
        // No queue entry because kitchen printed directly
        coVerify(exactly = 0) { kitchenQueueManager.enqueue(any(), any(), any()) }
        coVerify(exactly = 0) { kitchenQueueManager.enqueueUnassigned(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 5: MANUAL_RECEIPT_ONLY — only customer prints regardless of kitchen
    // -------------------------------------------------------------------------

    @Test
    fun `MANUAL_RECEIPT_ONLY - only customer printer is targeted`() = runTest {
        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter, customerPrinter)
        coEvery { printerManager.connect(customerMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.MANUAL_RECEIPT_ONLY)

        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))

        coVerify(exactly = 1) { printerManager.connect(customerMac) }
        coVerify(exactly = 0) { printerManager.connect(kitchenMac) }
        // No queue logic in manual mode
        coVerify(exactly = 0) { kitchenQueueManager.enqueue(any(), any(), any()) }
        coVerify(exactly = 0) { kitchenQueueManager.enqueueUnassigned(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 6: No printer configured — nothing prints, result is empty
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - no printer configured - nothing attempted`() = runTest {
        val blankKitchenPrinter = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Kitchen Printer",
            macAddress = "",
            enabled = true,
            autoPrint = false
        )
        coEvery { printerProfileRepository.getProfiles() } returns emptyList()
        coEvery { printerProfileRepository.getByRole(PrinterRole.KITCHEN.name) } returns blankKitchenPrinter

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        assertEquals(0, result.attempted)
        assertEquals(0, result.succeeded)
        coVerify(exactly = 0) { printerManager.connect(any<String>()) }
        coVerify(exactly = 0) { printerManager.printBytesTo(any(), any()) }
        coVerify(exactly = 1) { kitchenQueueManager.enqueueUnassigned(bill.bill.id, any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 7: KOT ownership guards for auto-printing
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - bill originating from this device prints kitchen ticket`() = runTest {
        whenever(sessionManager.getDeviceId()).thenReturn("DEVICE_1")
        whenever(sessionManager.getTerminalId()).thenReturn("TERM_LOCAL")
        // Mock bill originating from TERM_LOCAL (matching our sessionManager mock)
        val ownBill = bill.copy(
            bill = bill.bill.copy(deviceId = "DEVICE_1", currentOwnerTerminalId = "TERM_LOCAL")
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(ownBill, restaurantProfile, PrintDispatchMode.AUTO)

        // Prints because the bill belongs to this TERMINAL
        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
    }

    @Test
    fun `AUTO - bill owned by a different terminal does NOT print kitchen ticket`() = runTest {
        whenever(sessionManager.getDeviceId()).thenReturn("DEVICE_1")
        whenever(sessionManager.getTerminalId()).thenReturn("TERM_LOCAL")
        // Mock bill owned by TERM_OTHER (different terminal from this device's)
        val otherBill = bill.copy(
            bill = bill.bill.copy(deviceId = "DEVICE_OTHER", currentOwnerTerminalId = "TERM_OTHER")
        )

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)

        val result = router.printBill(otherBill, restaurantProfile, PrintDispatchMode.AUTO)

        // Skipped because the bill is owned by a different TERMINAL
        coVerify(exactly = 0) { printerManager.connect(kitchenMac) }
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Batched cart save (ADD + VOID sharing eventToken) → ONE combined
    // ticket; every event in the batch is marked printed after physical success.
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - batched add and void events print as one combined ticket`() = runTest {
        whenever(sessionManager.getDeviceId()).thenReturn("DEVICE_1")
        whenever(sessionManager.getTerminalId()).thenReturn("TERM_LOCAL")
        val ownBill = bill.copy(
            bill = bill.bill.copy(
                deviceId = "DEVICE_1",
                currentOwnerTerminalId = "TERM_LOCAL",
                publicToken = "pt-1"
            )
        )
        val batchToken = "batch-77"
        val addEvent = kotEvent(revision = "2", eventType = KotEventType.ADD, token = batchToken)
        val voidEvent = kotEvent(revision = "3", eventType = KotEventType.VOID, token = batchToken)

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { kotEventDao.getUnprintedEventsForBill("pt-1") } returns listOf(addEvent, voidEvent)
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        val result = router.printBill(ownBill, restaurantProfile, PrintDispatchMode.AUTO)

        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.KITCHEN.name))

        // Combined ticket rendered (not the live-bill fallback)
        coVerify(exactly = 1) {
            KitchenTicketFormatter.formatCombinedTicket(any(), any(), any(), any<List<com.khanabook.lite.pos.feature.printing.domain.KotTicketSection>>())
        }
        // BOTH batch events marked printed — no orphaned unprinted ADD
        coVerify(exactly = 1) { kotEventDao.markPrinted("pt-1", "2") }
        coVerify(exactly = 1) { kotEventDao.markPrinted("pt-1", "3") }
    }

    @Test
    fun `AUTO - events without batch token keep single-event rendering`() = runTest {
        whenever(sessionManager.getDeviceId()).thenReturn("DEVICE_1")
        whenever(sessionManager.getTerminalId()).thenReturn("TERM_LOCAL")
        val ownBill = bill.copy(
            bill = bill.bill.copy(
                deviceId = "DEVICE_1",
                currentOwnerTerminalId = "TERM_LOCAL",
                publicToken = "pt-1"
            )
        )
        val legacyEvent = kotEvent(revision = "2", eventType = KotEventType.ADD, token = null)

        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { kotEventDao.getUnprintedEventsForBill("pt-1") } returns listOf(legacyEvent)
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytesTo(any(), any()) } returns true

        router.printBill(ownBill, restaurantProfile, PrintDispatchMode.AUTO)

        // Single (non-batched) event renders through the same combined renderer with
        // ONE section — output identical to the legacy single-event ticket.
        coVerify(exactly = 1) {
            KitchenTicketFormatter.formatCombinedTicket(
                any(), any(), any(),
                any<List<com.khanabook.lite.pos.feature.printing.domain.KotTicketSection>>()
            )
        }
        coVerify(exactly = 0) {
            KitchenTicketFormatter.formatEventTicket(any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) { kotEventDao.markPrinted("pt-1", "2") }
    }

    private fun kotEvent(
        revision: String,
        eventType: String,
        token: String?
    ) = KotEventEntity(
        publicToken = "pt-1",
        kotRevision = revision,
        eventType = eventType,
        itemSnapshotJson = "[{\"id\":10,\"quantity\":1,\"itemName\":\"Tea\"}]",
        originatingDeviceId = "DEVICE_1",
        eventToken = token,
        isPrinted = false,
        createdAt = 1780000000000L
    )
}