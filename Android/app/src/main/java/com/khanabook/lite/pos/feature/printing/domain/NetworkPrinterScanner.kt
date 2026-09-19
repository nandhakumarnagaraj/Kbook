package com.khanabook.lite.pos.feature.printing.domain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredPrinter(
    val ip: String,
    val port: Int = 9100,
    val name: String = "Thermal Printer ($ip)"
)

@Singleton
class NetworkPrinterScanner @Inject constructor() {

    fun getLocalSubnetPrefix(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var siteLocalFallback: String? = null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                // Wi-Fi/Ethernet interfaces first. Taking the first non-loopback
                // address used to grab cellular (rmnet) or VPN (tun) prefixes on
                // dual-active devices — useless for reaching a LAN printer.
                val isLanInterface = intf.name.lowercase().let { it.startsWith("wlan") || it.startsWith("eth") || it.startsWith("ap") }
                val addresses = intf.inetAddresses
                for (addr in addresses) {
                    if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("127.")) continue
                    val prefix = host.substringBeforeLast(".") + "."
                    if (isLanInterface) return prefix
                    if (addr.isSiteLocalAddress && siteLocalFallback == null) {
                        siteLocalFallback = prefix
                    }
                }
            }
            siteLocalFallback
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving local network subnet", e)
            null
        }
    }

    suspend fun scanSubnet(
        port: Int = 9100,
        timeoutMs: Int = 250,
        onPrinterFound: ((DiscoveredPrinter) -> Unit)? = null
    ): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val prefix = getLocalSubnetPrefix() ?: return@withContext emptyList()
        val discovered = ConcurrentLinkedQueue<DiscoveredPrinter>()
        val limitedDispatcher = Dispatchers.IO.limitedParallelism(32)

        val scanJobs = (1..254).map { hostIndex ->
            async(limitedDispatcher) {
                val targetIp = "$prefix$hostIndex"
                if (probePort(targetIp, port, timeoutMs)) {
                    val printer = DiscoveredPrinter(ip = targetIp, port = port)
                    discovered.add(printer)
                    onPrinterFound?.invoke(printer)
                }
            }
        }
        scanJobs.awaitAll()
        discovered.toList().sortedBy { it.ip }
    }

    private fun probePort(ip: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "NetworkPrinterScanner"
    }
}
