package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.domain.model.PrinterConnectionType
import com.khanabook.lite.pos.domain.model.PrinterRole
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiPrinterTransportTest {
    @Test
    fun `wifi transport sends raw esc pos bytes to configured endpoint`() = runTest {
        val payload = byteArrayOf(0x1b, 0x40, 0x0a)
        ServerSocket(0).use { server ->
            val received = CompletableFuture<ByteArray>()
            val reader = Thread {
                server.accept().use { socket ->
                    received.complete(socket.getInputStream().readNBytes(payload.size))
                }
            }
            reader.start()

            val profile = PrinterProfileEntity(
                role = PrinterRole.CUSTOMER.name,
                name = "Customer Receipt Wi-Fi Printer",
                macAddress = "",
                connectionType = PrinterConnectionType.WIFI.name,
                host = "127.0.0.1",
                port = server.localPort
            )

            assertTrue(WifiPrinterTransport().print(profile, payload))
            assertArrayEquals(payload, received.get(2, TimeUnit.SECONDS))
            reader.join(2_000)
        }
    }

    @Test
    fun `wifi transport rejects an invalid endpoint`() = runTest {
        val profile = PrinterProfileEntity(
            role = PrinterRole.KITCHEN.name,
            name = "Invalid Printer",
            macAddress = "",
            connectionType = PrinterConnectionType.WIFI.name,
            host = "",
            port = 9100
        )

        assertFalse(WifiPrinterTransport().print(profile, byteArrayOf(0x0a)))
    }
}
