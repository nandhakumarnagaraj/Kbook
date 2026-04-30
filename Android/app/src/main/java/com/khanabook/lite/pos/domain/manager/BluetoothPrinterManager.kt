package com.khanabook.lite.pos.domain.manager

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.khanabook.lite.pos.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Bluetooth printer discovery, connection, and communication.
 * Supports simultaneous connections to multiple printers (e.g. customer + kitchen).
 *
 * Thread-safety design:
 *  - printerMutex guards map reads/writes (fast operations only — never held during blocking I/O).
 *  - Blocking socket.connect() and socket.close() run OUTSIDE printerMutex so an offline
 *    printer's 12-second timeout never stalls operations on other printers.
 *  - socketMutexes provides per-MAC serialisation to prevent two coroutines from
 *    connecting the same device simultaneously.
 */
class BluetoothPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothPrinter"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Guards map reads/writes only — never held during blocking I/O.
    private val printerMutex = Mutex()

    // Per-MAC mutex serialises connect/disconnect for the same device without
    // blocking operations on a different device.
    private val socketMutexes = ConcurrentHashMap<String, Mutex>()
    private fun socketMutex(mac: String): Mutex = socketMutexes.computeIfAbsent(mac) { Mutex() }

    // Per-MAC socket and stream maps.
    private val activeSockets = mutableMapOf<String, BluetoothSocket>()
    private val outputStreams = mutableMapOf<String, OutputStream>()

    // MAC of the most recently connected printer — used by printBytes() to target the right socket.
    private var lastConnectedMac: String? = null

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectedDeviceMac = MutableStateFlow<String?>(null)
    val connectedDeviceMac: StateFlow<String?> = _connectedDeviceMac

    private val _connectedDeviceMacs = MutableStateFlow<Set<String>>(emptySet())
    val connectedDeviceMacs: StateFlow<Set<String>> = _connectedDeviceMacs

    private val _connectedDeviceEvents = MutableSharedFlow<String>(extraBufferCapacity = 16, replay = 1)
    val connectedDeviceEvents: SharedFlow<String> = _connectedDeviceEvents

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                val deviceAddress = device?.address ?: return
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        _connectedDeviceMacs.value = _connectedDeviceMacs.value + deviceAddress
                        if (activeSockets.containsKey(deviceAddress)) {
                            _isConnected.value = true
                            _connectedDeviceMac.value = deviceAddress
                        }
                        _connectedDeviceEvents.tryEmit(deviceAddress)
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        _connectedDeviceMacs.value = _connectedDeviceMacs.value - deviceAddress
                        if (activeSockets.containsKey(deviceAddress)) {
                            activeSockets.remove(deviceAddress)
                            outputStreams.remove(deviceAddress)
                            if (_connectedDeviceMac.value == deviceAddress) {
                                _connectedDeviceMac.value = activeSockets.keys.firstOrNull()
                            }
                            if (lastConnectedMac == deviceAddress) {
                                lastConnectedMac = activeSockets.keys.firstOrNull()
                            }
                            _isConnected.value = activeSockets.isNotEmpty()
                        }
                    }
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
        ContextCompat.registerReceiver(context, connectionReceiver, filter, flags)
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Suppress("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasRequiredPermissions()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                    device?.let { found ->
                        val current = _scannedDevices.value.toMutableList()
                        val existingIndex = current.indexOfFirst { it.address == found.address }
                        if (existingIndex == -1) {
                            current.add(found)
                            _scannedDevices.value = current
                        } else {
                            current[existingIndex] = found
                            _scannedDevices.value = current
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }

    @Suppress("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions() || !isBluetoothEnabled()) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
             android.widget.Toast.makeText(context, context.getString(R.string.toast_turn_on_location), android.widget.Toast.LENGTH_LONG).show()
        }

        val paired = getPairedDevices()
        _scannedDevices.value = paired.toMutableList()
        _isScanning.value = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Discovery receiver was not registered before scan start")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
        ContextCompat.registerReceiver(context, discoveryReceiver, filter, flags)

        bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.startDiscovery()
    }

    @Suppress("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.cancelDiscovery()
        _isScanning.value = false
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Discovery receiver already unregistered on stopScan")
        }
    }

    /**
     * Connects to a Bluetooth printer. Reuses the existing socket if still live.
     *
     * The blocking [BluetoothSocket.connect] runs OUTSIDE [printerMutex] so an offline
     * printer's timeout does not stall operations on other printers. A per-MAC
     * [socketMutex] prevents double-connecting the same device.
     */
    @Suppress("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean {
        val mac = device.address

        // Phase 1 (fast, global mutex): check if socket is already live.
        var staleSocket: BluetoothSocket? = null
        var staleStream: OutputStream? = null
        val reuse = printerMutex.withLock {
            if (activeSockets[mac]?.isConnected == true) {
                lastConnectedMac = mac
                true
            } else {
                // Pull stale entry out of the map so we can close it outside the mutex.
                staleSocket = activeSockets.remove(mac)
                staleStream = outputStreams.remove(mac)
                _connectedDeviceMacs.value = _connectedDeviceMacs.value - mac
                if (_connectedDeviceMac.value == mac) _connectedDeviceMac.value = activeSockets.keys.firstOrNull()
                if (lastConnectedMac == mac) lastConnectedMac = activeSockets.keys.firstOrNull()
                _isConnected.value = activeSockets.isNotEmpty()
                false
            }
        }
        if (reuse) return true

        // Phase 2 (per-MAC mutex, OUTSIDE global mutex): close stale socket, open new one.
        return socketMutex(mac).withLock {
            // Double-check — another coroutine may have connected while we waited.
            val alreadyConnected = printerMutex.withLock { activeSockets[mac]?.isConnected == true }
            if (alreadyConnected) {
                printerMutex.withLock { lastConnectedMac = mac }
                return true
            }

            // Close stale resources (may block briefly — outside global mutex).
            try { staleStream?.close() } catch (_: Exception) {}
            try { staleSocket?.close() } catch (_: Exception) {}

            _isConnecting.value = true
            try {
                bluetoothAdapter?.cancelDiscovery()

                var socket: BluetoothSocket? = null
                try {
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e: Exception) {
                    socket?.close()
                    // Fallback to insecure RFCOMM — common for thermal printers.
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                }

                // Phase 3 (fast, global mutex): register the new socket.
                printerMutex.withLock {
                    activeSockets[mac] = socket!!
                    outputStreams[mac] = socket.outputStream
                    lastConnectedMac = mac
                    _isConnected.value = true
                    _connectedDeviceMac.value = mac
                    _connectedDeviceMacs.value = _connectedDeviceMacs.value + mac
                }
                _connectedDeviceEvents.tryEmit(mac)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to printer $mac", e)
                false
            } finally {
                _isConnecting.value = false
            }
        }
    }

    suspend fun connect(address: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
        return connect(device)
    }

    /**
     * Sends raw bytes to the most recently connected printer.
     * The actual write runs outside [printerMutex] so slow I/O does not block
     * concurrent connect calls on other printers.
     */
    suspend fun printBytes(data: ByteArray): Boolean {
        val mac = printerMutex.withLock { lastConnectedMac } ?: run {
            Log.e(TAG, "printBytes called but no printer is connected")
            return false
        }
        val stream = printerMutex.withLock { outputStreams[mac] } ?: run {
            Log.e(TAG, "No output stream for printer $mac")
            return false
        }
        return try {
            stream.write(data)
            stream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to printer $mac", e)
            false
        }
    }

    /**
     * Disconnects a specific printer by MAC, leaving other connections intact.
     */
    suspend fun disconnect(mac: String) {
        val (socket, stream) = printerMutex.withLock {
            val s = activeSockets.remove(mac)
            val str = outputStreams.remove(mac)
            _connectedDeviceMacs.value = _connectedDeviceMacs.value - mac
            if (_connectedDeviceMac.value == mac) _connectedDeviceMac.value = activeSockets.keys.firstOrNull()
            if (lastConnectedMac == mac) lastConnectedMac = activeSockets.keys.firstOrNull()
            _isConnected.value = activeSockets.isNotEmpty()
            Pair(s, str)
        }
        try { stream?.close() } catch (e: Exception) { Log.w(TAG, "Error closing stream for $mac", e) }
        try { socket?.close() } catch (e: Exception) { Log.w(TAG, "Error closing socket for $mac", e) }
    }

    /**
     * Disconnects all active printer connections.
     */
    suspend fun disconnect() {
        val macs = printerMutex.withLock { activeSockets.keys.toList() }
        macs.forEach { disconnect(it) }
        printerMutex.withLock { lastConnectedMac = null }
    }

    /** Returns true if any printer socket is currently live. */
    fun isConnected(): Boolean = activeSockets.values.any { it.isConnected }

    /** Returns true if the printer with the given MAC is currently connected. */
    fun isConnectedTo(mac: String): Boolean = activeSockets[mac]?.isConnected == true

    @Suppress("MissingPermission")
    fun deviceName(device: BluetoothDevice): String =
        try {
            device.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to read Bluetooth device name", e)
            "Unknown Device"
        }
}
