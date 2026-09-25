package com.khanabook.lite.pos.feature.printing.domain

import android.util.Log
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import com.khanabook.lite.pos.feature.printing.domain.PrinterConnectionType
import com.khanabook.lite.pos.feature.printing.domain.connectionTypeValue
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

interface PrinterTransport {
    val connectionType: PrinterConnectionType
    suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean
}

@Singleton
class BluetoothPrinterTransport @Inject constructor(
    private val printerManager: BluetoothPrinterManager
) : PrinterTransport {
    override val connectionType = PrinterConnectionType.BLUETOOTH

    override suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean {
        if (profile.macAddress.isBlank()) return false
        if (!printerManager.isConnectedTo(profile.macAddress) && !printerManager.connect(profile.macAddress)) {
            return false
        }
        return printerManager.printBytesTo(profile.macAddress, bytes)
    }
}

/**
 * Wi-Fi (TCP port 9100 raw ESC/POS) transport.
 *
 * Failure model (why this is chunked + watchdog-guarded):
 * Thermal printers have small input buffers. A single unbounded write() of a
 * full receipt (logo bitmap + item table, 5-25 KB) can flood the printer and
 * block forever on a slow/stalled Wi-Fi link — and Java blocking OutputStream
 * writes have NO timeout (soTimeout bounds reads only). Test prints survived
 * because the 45-byte strip never fills the buffer; live KOTs/receipts stalled.
 *
 * - Payload is written in 4 KB chunks with a flush per chunk so the printer's
 *   buffer is never flooded in one burst (same model as UsbPrinterTransport).
 * - A watchdog force-closes the socket after WRITE_DEADLINE_MS, which is the
 *   only reliable way to unblock a stuck write (cancellation cannot interrupt it).
 * - Failures are logged with endpoint + exception type instead of being swallowed.
 * - One retry on a fresh socket: Wi-Fi printer modules frequently recover right
 *   after a dropped connection. Mirrors the kitchen queue's reprint semantics.
 */
@Singleton
class WifiPrinterTransport @Inject constructor(
    private val networkPrinterScanner: NetworkPrinterScanner
) : PrinterTransport {
    override val connectionType = PrinterConnectionType.WIFI

    override suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean {
        val host = profile.host?.trim().orEmpty()
        val port = profile.port
        if (host.isBlank() || port !in 1..65535) return false

        if (withContext(Dispatchers.IO) { networkPrinterScanner.isSameSubnet(host) }.not()) {
            Log.w(TAG, "Wi-Fi print to $host:$port skipped — IP not on current local subnet")
            return false
        }

        if (withContext(Dispatchers.IO) { deliver(host, port, bytes) }) return true

        // One bounded retry on a fresh socket — never reuse a half-open connection.
        delay(RETRY_DELAY_MS)
        return withContext(Dispatchers.IO) { deliver(host, port, bytes) }
    }

    private fun deliver(host: String, port: Int, bytes: ByteArray): Boolean {
        val socket = Socket()
        val watchdog: ScheduledFuture<*> = writeWatchdog.schedule(
            {
                // Force-closes a stuck write: Socket.close() from another thread makes
                // the blocked OutputStream.write() throw SocketException immediately.
                runCatching { socket.close() }
            },
            WRITE_DEADLINE_MS,
            TimeUnit.MILLISECONDS
        )
        return try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.getOutputStream().use { output ->
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + CHUNK_SIZE, bytes.size)
                    output.write(bytes, offset, end - offset)
                    output.flush()
                    offset = end
                }
            }
            true
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Wi-Fi print to $host:$port failed (${e.javaClass.simpleName}: ${e.message})"
            )
            false
        } finally {
            watchdog.cancel(false)
            runCatching { socket.close() }
        }
    }

    private companion object {
        private const val TAG = "WifiPrinterTransport"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val WRITE_DEADLINE_MS = 10_000L
        private const val CHUNK_SIZE = 4_096
        private const val RETRY_DELAY_MS = 300L

        /** Shared daemon watchdog pool — one thread serves all Wi-Fi print attempts. */
        private val writeWatchdog = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "WifiPrinterWatchdog").apply { isDaemon = true }
        }
    }
}

@Singleton
class PrinterTransportDispatcher @Inject constructor(
    private val bluetooth: BluetoothPrinterTransport,
    private val wifi: WifiPrinterTransport,
    private val usb: UsbPrinterTransport
) {
    suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean =
        when (profile.connectionTypeValue()) {
            PrinterConnectionType.BLUETOOTH -> bluetooth.print(profile, bytes)
            PrinterConnectionType.WIFI -> wifi.print(profile, bytes)
            PrinterConnectionType.USB -> usb.print(profile, bytes)
        }
}
