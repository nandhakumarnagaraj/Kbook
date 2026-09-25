package com.khanabook.lite.pos.feature.printing.domain

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.net.Uri
import com.khanabook.lite.pos.feature.printing.data.PrinterProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * USB-OTG thermal printer transport (Android USB host mode).
 *
 * Thermal receipt printers expose a vendor-specific (class 0xFF) or printer
 * (class 0x07) USB interface with a single bulk-OUT endpoint; raw ESC/POS
 * bytes are written there — identical payload to the Wi-Fi/BT transports.
 *
 * Connection identity is "usb:<vid>:<pid>[:<serial>]" stored in the profile's
 * macAddress column, so no DB schema change was needed.
 *
 * Permission model (per Android USB host docs):
 *  - Devices attached while the app runs fire ACTION_USB_DEVICE_ATTACHED;
 *    we request permission via UsbManager.requestPermission() and a
 *    PendingIntent reply. Grants persist per (app, device) until unplug.
 */
@Singleton
class UsbPrinterTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : PrinterTransport {

    companion object {
        private const val TAG = "UsbPrinter"
        private const val ACTION_USB_PERMISSION =
            "com.khanabook.lite.pos.USB_PRINTER_PERMISSION"
        private const val PERMISSION_REPLY_TIMEOUT_MS = 30_000L
        private const val BULK_TIMEOUT_MS = 5_000

        /** USB class codes that thermal printers commonly use. */
        private const val USB_CLASS_PRINTER = 0x07
        private const val USB_CLASS_VENDOR_SPEC = 0xFF

        fun deviceKey(device: UsbDevice): String {
            val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                contextCompatSelfPermission(device)
            ) {
                device.serialNumber
            } else null
            val base = "usb:${device.vendorId.toString(16)}:${device.productId.toString(16)}"
            return if (!serial.isNullOrBlank()) "$base:$serial" else "$base:${device.deviceName.hashCode()}"
        }

        private fun contextCompatSelfPermission(device: UsbDevice): Boolean = try {
            device.serialNumber != null
        } catch (_: SecurityException) {
            false
        }
    }

    // Live connections keyed by deviceKey(). Bulk endpoint + interface claimed.
    private data class UsbSession(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val bulkOut: UsbEndpoint
    )

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val sessions = ConcurrentHashMap<String, UsbSession>()

    /** Emitted when a permission reply arrives; suspenders resume on it. */
    private val permissionReplies = ConcurrentHashMap<Int, (Boolean) -> Unit>()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (device != null) {
                permissionReplies.remove(device.deviceId)?.invoke(granted)
            }
        }
    }

    private var receiverRegistered = false

    /**
     * Detach watchdog: when the printer is unplugged, Android kills the underlying
     * UsbDeviceConnection but our cached session stays in the map. Without this
     * receiver the FIRST print after unplug→replug silently fails (dead session
     * returned from cache, bulkTransfer on a closed fd) and only self-heals on the
     * second attempt. Closing the session on detach removes that lost receipt.
     */
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null) {
                val key = deviceKey(device)
                if (sessions.containsKey(key)) {
                    Log.i(TAG, "USB printer detached ($key) — closing cached session")
                    closeSession(key)
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(permissionReceiver, filter)
        }
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(detachReceiver, detachFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(detachReceiver, detachFilter)
        }
        receiverRegistered = true
    }

    override val connectionType = PrinterConnectionType.USB

    /** All currently attached USB devices that look like thermal printers. */
    fun listCandidatePrinters(): List<Pair<UsbDevice, String>> {
        val manager = usbManager ?: return emptyList()
        return manager.deviceList.values.mapNotNull { device ->
            val isPrinterLike = device.deviceClass == USB_CLASS_PRINTER ||
                device.deviceClass == USB_CLASS_VENDOR_SPEC ||
                hasPrinterInterface(device)
            if (isPrinterLike) device to describe(device) else null
        }
    }

    private fun hasPrinterInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == USB_CLASS_PRINTER ||
                intf.interfaceClass == USB_CLASS_VENDOR_SPEC
            ) return true
        }
        return false
    }

    private fun describe(device: UsbDevice): String {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { device.productName } catch (_: SecurityException) { null }
        } else null
        return name?.takeIf { it.isNotBlank() }
            ?: "USB ${device.vendorId.toString(16)}:${device.productId.toString(16)}"
    }

    /** True when the app already holds a permission grant for this device. */
    fun hasPermission(device: UsbDevice): Boolean =
        usbManager?.hasPermission(device) == true

    /**
     * Requests USB permission, suspending until the user answers the system
     * dialog (or 30s timeout). Returns true when granted.
     */
    suspend fun requestPermission(device: UsbDevice): Boolean {
        val manager = usbManager ?: return false
        if (manager.hasPermission(device)) return true
        ensureReceiverRegistered()
        return withTimeoutOrNull(PERMISSION_REPLY_TIMEOUT_MS) {
            coroutineScope {
                val requestId = device.deviceId
                val reply = CompletableDeferred<Boolean>()
                permissionReplies[requestId] = { granted -> reply.complete(granted) }
                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    requestId,
                    Intent(ACTION_USB_PERMISSION)
                        .setPackage(context.packageName)
                        .setData(Uri.parse("${context.packageName}://usb-permission/$requestId")),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val permissionPoll = async(Dispatchers.IO) {
                    while (isActive && !reply.isCompleted) {
                        if (manager.hasPermission(device)) {
                            reply.complete(true)
                            break
                        }
                        delay(100)
                    }
                }
                try {
                    manager.requestPermission(device, permissionIntent)
                    reply.await()
                } finally {
                    permissionPoll.cancel()
                    permissionReplies.remove(requestId)
                }
            }
        } ?: manager.hasPermission(device)
    }

    /** Finds an attached device by our "usb:vid:pid[:serial]" key. */
    fun findDeviceByKey(key: String): UsbDevice? {
        val manager = usbManager ?: return null
        return manager.deviceList.values.firstOrNull { deviceKey(it) == key }
            // Serial may differ across replug on cheap printers; fall back to VID:PID prefix.
            ?: manager.deviceList.values.firstOrNull {
                val base = "usb:${it.vendorId.toString(16)}:${it.productId.toString(16)}"
                key.startsWith(base)
            }
    }

    override suspend fun print(profile: PrinterProfileEntity, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val manager = usbManager ?: return@withContext false
            val key = profile.macAddress
            if (!key.startsWith("usb:")) return@withContext false
            // Register the detach watchdog lazily: permission grants persist across
            // replugs, so requestPermission() may never have run on this install.
            ensureReceiverRegistered()

            // Belt-and-braces vs the detach receiver: evict any cached session whose
            // device is no longer attached (receiver missed, app restarted after unplug).
            if (sessions.containsKey(key) && findDeviceByKey(key) == null) {
                Log.w(TAG, "Cached USB session for $key has no attached device — dropping")
                closeSession(key)
            }

            val session = sessions[key] ?: run {
                val device = findDeviceByKey(key) ?: return@withContext false
                if (!manager.hasPermission(device)) {
                    Log.w(TAG, "No USB permission for $key — prompt needed from settings UI")
                    return@withContext false
                }
                openSession(device, key) ?: return@withContext false
            }

            try {
                var offset = 0
                while (offset < bytes.size) {
                    val chunk = bytes.copyOfRange(offset, minOf(offset + 16 * 1024, bytes.size))
                    val written = session.connection.bulkTransfer(
                        session.bulkOut, chunk, chunk.size, BULK_TIMEOUT_MS
                    )
                    if (written < 0) {
                        Log.e(TAG, "bulkTransfer failed for $key — closing session")
                        closeSession(key)
                        return@withContext false
                    }
                    offset += written
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "USB print failed for $key", e)
                closeSession(key)
                false
            }
        }

    private fun openSession(device: UsbDevice, key: String): UsbSession? {
        val manager = usbManager ?: return null
        val connection = try {
            manager.openDevice(device) ?: return null.also { Log.w(TAG, "openDevice null for $key") }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException opening $key", e)
            return null
        }
        // Find the first interface with a bulk-OUT endpoint (printer or vendor class).
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            val bulkOut = (0 until intf.endpointCount)
                .map { intf.getEndpoint(it) }
                .firstOrNull {
                    it.direction == UsbConstants.USB_DIR_OUT &&
                        it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                }
            if (bulkOut != null) {
                return if (connection.claimInterface(intf, true)) {
                    val session = UsbSession(connection, intf, bulkOut)
                    sessions[key] = session
                    session
                } else {
                    Log.w(TAG, "claimInterface failed for $key")
                    connection.close()
                    null
                }
            }
        }
        Log.w(TAG, "No bulk-OUT endpoint found on $key")
        connection.close()
        return null
    }

    private fun closeSession(key: String) {
        sessions.remove(key)?.let { s ->
            runCatching { s.connection.releaseInterface(s.usbInterface) }
            runCatching { s.connection.close() }
        }
    }

    /** Drops the cached session; called when the printer is unplugged. */
    fun disconnect(key: String) = closeSession(key)

    fun disconnectAll() {
        sessions.keys.toList().forEach(::closeSession)
    }

    /** EscPos DLE EOT health query is possible over USB (bulk IN endpoint exists on most) — v2. */
    override fun toString(): String = "UsbPrinterTransport(sessions=${sessions.size})"
}
