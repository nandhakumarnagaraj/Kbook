package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.feature.printing.domain.DiscoveredPrinter
import com.khanabook.lite.pos.feature.printing.domain.NetworkPrinterScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPrinterScannerTest {

    private val scanner = NetworkPrinterScanner(appContext = null)

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

    // ------------------------------------------------------------------
    // Netmask math — the /12 case that broke Android 10 Wi-Fi printing.
    // The scanner's IP helpers are private, so exercise the decision logic
    // through the LocalNetwork value semantics re-implemented below.
    // ------------------------------------------------------------------

    @Test
    fun `slash12 network matches hosts across the whole range`() {
        // Lenovo reality: device 10.176.2.11/12 → network 10.176.0.0, mask 0xFFF00000.
        // A printer at 10.176.9.250 IS on-link even though the 3rd octet differs.
        val mask = (-1 shl (32 - 12))
        val deviceIp = toInt(10, 176, 2, 11)
        val printerIp = toInt(10, 176, 9, 250)
        val foreignIp = toInt(192, 168, 1, 100)

        assertEquals("10.176.0.0", toIpString(deviceIp and mask))
        assertEquals(deviceIp and mask, printerIp and mask)   // same /12 → allow
        assertTrue(deviceIp and mask != foreignIp and mask)   // different network → block
    }

    @Test
    fun `slash24 network keeps classic third-octet boundary`() {
        val mask = (-1 shl (32 - 24))
        val deviceIp = toInt(192, 168, 1, 50)
        assertEquals(toInt(192, 168, 1, 0), deviceIp and mask)
        assertEquals(deviceIp and mask, toInt(192, 168, 1, 200) and mask)
        assertTrue(deviceIp and mask != toInt(192, 168, 2, 200) and mask)
    }

    @Test
    fun `slash23 and slash28 boundaries behave`() {
        // /23: 192.168.0.0/23 covers 192.168.0.x AND 192.168.1.x
        val mask23 = (-1 shl (32 - 23))
        assertEquals(toInt(192, 168, 0, 0), toInt(192, 168, 1, 77) and mask23)
        // /28: .16 block ends at .31
        val mask28 = (-1 shl (32 - 28))
        assertEquals(toInt(192, 168, 1, 16), toInt(192, 168, 1, 31) and mask28)
        assertTrue(toInt(192, 168, 1, 32) and mask28 != toInt(192, 168, 1, 16))
    }

    private fun toInt(a: Int, b: Int, c: Int, d: Int) = (a shl 24) or (b shl 16) or (c shl 8) or d

    private fun toIpString(ip: Int) =
        listOf((ip ushr 24) and 0xFF, (ip ushr 16) and 0xFF, (ip ushr 8) and 0xFF, ip and 0xFF)
            .joinToString(".")
}
