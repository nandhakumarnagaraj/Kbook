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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream
import java.util.UUID

/**
 * Manages Bluetooth printer discovery, connection, and communication.
 * Implements a Singleton-like behavior when used via Hilt.
 */
class BluetoothPrinterManager(private val context: Context) {

    companion object {
        // Standard SPP UUID for Bluetooth Serial Port Profile
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var activeSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

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
                
                if (device?.address == activeSocket?.remoteDevice?.address) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> _isConnected.value = true
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> disconnect()
                    }
                }
            }
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
        ContextCompat.registerReceiver(context, connectionReceiver, filter, flags)
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Checks if all required Bluetooth and Location permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Retrieves the list of currently paired (bonded) devices.
     */
    @Suppress("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasRequiredPermissions()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Receiver for Bluetooth device discovery.
     */
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
                            // Update existing device (might have received a name)
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

    /**
     * Checks if Location services are enabled (required for BT discovery on older Android).
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
    }

    /**
     * Starts scanning for new Bluetooth devices.
     */
    @Suppress("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions() || !isBluetoothEnabled()) return
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
             android.widget.Toast.makeText(context, "Please turn on Location/GPS to find new devices", android.widget.Toast.LENGTH_LONG).show()
        }

        // Initialize scan list with paired devices
        val paired = getPairedDevices()
        _scannedDevices.value = paired.toMutableList()
        _isScanning.value = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        
        try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
        ContextCompat.registerReceiver(context, discoveryReceiver, filter, flags)

        bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.startDiscovery()
    }

    /**
     * Stops the ongoing Bluetooth discovery.
     */
    @Suppress("MissingPermission")
    fun stopScan() {
        bluetoothAdapter?.cancelDiscovery()
        _isScanning.value = false
        try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
    }

    /**
     * Connects to a Bluetooth device using standard and insecure fallbacks.
     */
    @Suppress("MissingPermission")
    fun connect(device: BluetoothDevice): Boolean {
        disconnect()
        _isConnecting.value = true
        return try {
            bluetoothAdapter?.cancelDiscovery()
            
            var socket: BluetoothSocket? = null
            try {
                // Try standard secure connection first
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e: Exception) {
                socket?.close()
                // Fallback to insecure connection (common for thermal printers)
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            }

            activeSocket = socket
            outputStream = socket?.outputStream
            _isConnected.value = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
            false
        } finally {
            _isConnecting.value = false
        }
    }

    fun connect(address: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return false
        return connect(device)
    }

    /**
     * Sends raw bytes to the connected printer.
     */
    fun printBytes(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Disconnects the active Bluetooth socket.
     */
    fun disconnect() {
        try {
            outputStream?.close()
            activeSocket?.close()
        } catch (_: Exception) {}
        outputStream = null
        activeSocket = null
        _isConnected.value = false
    }

    fun isConnected(): Boolean = activeSocket?.isConnected == true

    @Suppress("MissingPermission")
    fun deviceName(device: BluetoothDevice): String =
        try { device.name ?: "Unknown Device" } catch (_: Exception) { "Unknown Device" }
}
