package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.BillEntity
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.relation.BillWithItems
import com.khanabook.lite.pos.data.repository.PrinterProfileRepository
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.util.InvoiceFormatter
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

/**
 * Tests the single-printer flow:
 *   - Only customer receipt printer connected → receipt prints, KDS goes to queue.
 *   - Only kitchen printer connected → KDS prints, customer receipt skipped.
 *   - Customer printer connected, kitchen configured but offline → receipt prints, KDS queued.
 */
class PrintRouterTest {

    private val printerProfileRepository: PrinterProfileRepository = mockk(relaxed = true)
    private val printerManager: BluetoothPrinterManager = mockk(relaxed = true)
    private val kitchenQueueManager: KitchenPrintQueueManager = mockk(relaxed = true)

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
        items = emptyList(),
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
        every { InvoiceFormatter.formatForThermalPrinter(any(), any()) } returns byteArrayOf(0x01)

        mockkObject(KitchenTicketFormatter)
        every { KitchenTicketFormatter.format(any(), any(), any()) } returns byteArrayOf(0x02)

        router = PrintRouter(printerProfileRepository, printerManager, kitchenQueueManager)
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
        coEvery { printerProfileRepository.getProfiles() } returns listOf(customerPrinter)
        coEvery { printerProfileRepository.getByRole(PrinterRole.KITCHEN.name) } returns null
        coEvery { printerManager.connect(customerMac) } returns true
        coEvery { printerManager.printBytes(any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        // Receipt printed successfully
        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))
        assertTrue(result.failures.isEmpty())

        // Customer printer connected and printed
        coVerify(exactly = 1) { printerManager.connect(customerMac) }
        coVerify(exactly = 1) { printerManager.printBytes(any()) }

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
        coEvery { printerManager.printBytes(any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        // Customer receipt succeeds
        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))

        // Kitchen failed → queued with kitchen MAC
        coVerify(exactly = 1) { kitchenQueueManager.enqueue(bill.bill.id, kitchenMac, any()) }

        // Customer connected and printed
        coVerify(exactly = 1) { printerManager.connect(customerMac) }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Only kitchen printer connected, no customer printer configured
    // -------------------------------------------------------------------------

    @Test
    fun `AUTO - only kitchen printer configured - KDS prints no receipt`() = runTest {
        coEvery { printerProfileRepository.getProfiles() } returns listOf(kitchenPrinter)
        coEvery { printerManager.connect(kitchenMac) } returns true
        coEvery { printerManager.printBytes(any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        // Kitchen ticket printed
        assertEquals(1, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.KITCHEN.name))

        // KDS queue pre-cleared before printing (normal flow)
        coVerify(exactly = 1) { kitchenQueueManager.markPrinted(bill.bill.id, kitchenMac) }

        // Kitchen connected and printed
        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
        coVerify(exactly = 1) { printerManager.printBytes(any()) }

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
        coEvery { printerManager.printBytes(any()) } returns true

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        assertEquals(2, result.succeeded)
        assertTrue(result.successTargets.contains(PrinterRole.KITCHEN.name))
        assertTrue(result.successTargets.contains(PrinterRole.CUSTOMER.name))
        assertTrue(result.failures.isEmpty())

        coVerify(exactly = 1) { printerManager.connect(kitchenMac) }
        coVerify(exactly = 1) { printerManager.connect(customerMac) }
        coVerify(exactly = 2) { printerManager.printBytes(any()) }
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
        coEvery { printerManager.printBytes(any()) } returns true

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
        coEvery { printerProfileRepository.getProfiles() } returns emptyList()
        coEvery { printerProfileRepository.getByRole(any()) } returns null

        val result = router.printBill(bill, restaurantProfile, PrintDispatchMode.AUTO)

        assertEquals(0, result.attempted)
        assertEquals(0, result.succeeded)
        coVerify(exactly = 0) { printerManager.connect(any<String>()) }
        coVerify(exactly = 0) { printerManager.printBytes(any()) }
        coVerify(exactly = 1) { kitchenQueueManager.enqueueUnassigned(bill.bill.id, any()) }
    }
}
