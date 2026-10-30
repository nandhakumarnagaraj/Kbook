package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.printing.domain.BluetoothPrinterTransport
import com.khanabook.lite.pos.feature.printing.domain.PrinterConnectionType
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import com.khanabook.lite.pos.feature.printing.domain.PrinterTransportDispatcher
import com.khanabook.lite.pos.feature.printing.domain.UsbPrinterTransport
import com.khanabook.lite.pos.feature.printing.domain.WifiPrinterTransport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ESC/POS has no framing: two payloads written concurrently to one physical
 * printer interleave and the paper comes out garbled. The dispatcher must
 * serialise per endpoint while keeping different endpoints parallel — this is
 * what makes one-printer mode (receipt + KOT on a single device) safe.
 */
class PrinterTransportDispatcherTest {

    private val bluetooth: BluetoothPrinterTransport = mockk()
    private val wifi: WifiPrinterTransport = mockk()
    private val usb: UsbPrinterTransport = mockk()

    private val dispatcher = PrinterTransportDispatcher(bluetooth, wifi, usb)

    private val payload = byteArrayOf(0x1b, 0x40, 0x0a)

    private fun wifiPrinter(role: PrinterRole, host: String, port: Int) = PrinterProfileEntity(
        role = role.name,
        name = "Wi-Fi Printer",
        macAddress = "",
        connectionType = PrinterConnectionType.WIFI.name,
        host = host,
        port = port
    )

    @Test
    fun `prints to the same endpoint are serialised`() = runTest {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        coEvery { wifi.print(any(), any()) } coAnswers {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, now) }
            delay(50)
            inFlight.decrementAndGet()
            true
        }

        // Both roles on one device is exactly the one-printer setup.
        val profile = wifiPrinter(PrinterRole.CUSTOMER, "192.168.1.50", 9100)
        val receipt = async { dispatcher.print(profile, payload) }
        val kot = async { dispatcher.print(profile.copy(role = PrinterRole.KITCHEN.name), payload) }

        assertTrue(receipt.await())
        assertTrue(kot.await())
        assertEquals("two payloads entered the printer at once", 1, maxInFlight.get())
        coVerify(exactly = 2) { wifi.print(any(), any()) }
    }

    @Test
    fun `prints to different endpoints stay parallel`() = runTest {
        val started = AtomicInteger(0)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { wifi.print(any(), any()) } coAnswers {
            started.incrementAndGet()
            release.await()
            true
        }

        val first = async { dispatcher.print(wifiPrinter(PrinterRole.CUSTOMER, "192.168.1.50", 9100), payload) }
        val second = async { dispatcher.print(wifiPrinter(PrinterRole.KITCHEN, "192.168.1.60", 9100), payload) }

        var spins = 0
        while (started.get() < 2 && spins++ < 100) yield()

        assertEquals("different printers must not block each other", 2, started.get())
        release.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
    }

    @Test
    fun `routes to the transport that matches the connection type`() = runTest {
        coEvery { bluetooth.print(any(), any()) } returns true
        coEvery { wifi.print(any(), any()) } returns true
        coEvery { usb.print(any(), any()) } returns true

        dispatcher.print(
            PrinterProfileEntity(
                role = PrinterRole.CUSTOMER.name,
                name = "BT",
                macAddress = "AA:BB:CC:DD:EE:01",
                connectionType = PrinterConnectionType.BLUETOOTH.name
            ),
            payload
        )
        dispatcher.print(wifiPrinter(PrinterRole.CUSTOMER, "192.168.1.50", 9100), payload)
        dispatcher.print(
            PrinterProfileEntity(
                role = PrinterRole.KITCHEN.name,
                name = "USB",
                macAddress = "usb:0483:5740",
                connectionType = PrinterConnectionType.USB.name
            ),
            payload
        )

        coVerify(exactly = 1) { bluetooth.print(any(), any()) }
        coVerify(exactly = 1) { wifi.print(any(), any()) }
        coVerify(exactly = 1) { usb.print(any(), any()) }
    }

    @Test
    fun `same USB endpoint is serialised too`() = runTest {
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        coEvery { usb.print(any(), any()) } coAnswers {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, now) }
            delay(50)
            inFlight.decrementAndGet()
            true
        }

        val profile = PrinterProfileEntity(
            role = PrinterRole.CUSTOMER.name,
            name = "USB",
            macAddress = "usb:0483:5740",
            connectionType = PrinterConnectionType.USB.name
        )
        val receipt = async { dispatcher.print(profile, payload) }
        val kot = async { dispatcher.print(profile.copy(role = PrinterRole.KITCHEN.name), payload) }

        assertTrue(receipt.await())
        assertTrue(kot.await())
        assertEquals(1, maxInFlight.get())
    }
}
