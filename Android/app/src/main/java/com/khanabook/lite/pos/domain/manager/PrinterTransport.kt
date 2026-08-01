package com.khanabook.lite.pos.domain.manager

import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.domain.model.PrinterConnectionType
import com.khanabook.lite.pos.domain.model.connectionTypeValue
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
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

@Singleton
class WifiPrinterTransport @Inject constructor() : PrinterTransport {
    override val connectionType = PrinterConnectionType.WIFI

    override suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val host = profile.host?.trim().orEmpty()
            if (host.isBlank() || profile.port !in 1..65535) return@withContext false
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, profile.port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = WRITE_TIMEOUT_MS
                    socket.getOutputStream().use { output ->
                        output.write(bytes)
                        output.flush()
                    }
                }
                true
            }.getOrDefault(false)
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val WRITE_TIMEOUT_MS = 8_000
    }
}

@Singleton
class PrinterTransportDispatcher @Inject constructor(
    private val bluetooth: BluetoothPrinterTransport,
    private val wifi: WifiPrinterTransport
) {
    suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean =
        when (profile.connectionTypeValue()) {
            PrinterConnectionType.BLUETOOTH -> bluetooth.print(profile, bytes)
            PrinterConnectionType.WIFI -> wifi.print(profile, bytes)
        }
}
