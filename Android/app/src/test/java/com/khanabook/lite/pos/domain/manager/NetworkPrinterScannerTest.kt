package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.feature.printing.domain.DiscoveredPrinter
import com.khanabook.lite.pos.feature.printing.domain.NetworkPrinterScanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPrinterScannerTest {

    private val scanner = NetworkPrinterScanner()

    @Test
    fun discoveredPrinter_hasCorrectDefaultValues() {
        val printer = DiscoveredPrinter(ip = "192.168.1.100")
        assertEquals("192.168.1.100", printer.ip)
        assertEquals(9100, printer.port)
        assertEquals("Thermal Printer (192.168.1.100)", printer.name)
    }

    @Test
    fun discoveredPrinter_customPortAndName() {
        val printer = DiscoveredPrinter(ip = "192.168.1.200", port = 9101, name = "Kitchen KOT Printer")
        assertEquals("192.168.1.200", printer.ip)
        assertEquals(9101, printer.port)
        assertEquals("Kitchen KOT Printer", printer.name)
    }

    @Test
    fun getLocalSubnetPrefix_returnsValidSubnetOrNull() {
        val prefix = scanner.getLocalSubnetPrefix()
        if (prefix != null) {
            assertTrue("Prefix should end with dot", prefix.endsWith("."))
            val parts = prefix.split(".").filter { it.isNotEmpty() }
            assertEquals("IPv4 prefix should have 3 segments", 3, parts.size)
        }
    }
}
