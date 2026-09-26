package com.khanabook.lite.pos.feature.printing.domain

import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import com.khanabook.lite.pos.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
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

private fun prefixMask(prefixLength: Short): Int =
    if (prefixLength <= 0) 0 else (-1 shl (32 - prefixLength))

private fun intFromIp(ip: String): Int =
    ip.split(".").map { part -> part.toInt().also { require(it in 0..255) } }
        .fold(0) { acc: Int, octet: Int -> (acc shl 8) or octet }

private fun ipToString(ip: Int): String =
    listOf((ip ushr 24) and 0xFF, (ip ushr 16) and 0xFF, (ip ushr 8) and 0xFF, ip and 0xFF)
        .joinToString(".")

@Singleton
class NetworkPrinterScanner @Inject constructor(
    // Nullable, no default: kapt generates a phantom no-arg @Inject stub for
    // all-default constructors, which Dagger rejects as a duplicate constructor.
    // JVM unit tests pass null explicitly.
    @ApplicationContext private val appContext: Context?
) {

    /**
     * The device's active LAN identity: local IPv4 + the interface's REAL prefix
     * length. DHCP can hand out anything from /8 to /29 (enterprise APs, phone
     * hotspots, some routers use /12, /23, /28...) — assuming /24 breaks reachability
     * decisions on those networks (e.g. 10.176.2.11/12 cannot be string-prefix matched).
     *
     * [prefixLength] is null when the platform reported an implausible value.
     * Known quirks (verified against docs & issue trackers):
     *  - JDK-7107883: returns 0 on interfaces without a broadcast flag (loopback — filtered)
     *  - JDK-6707289: not always conformant for IPv4
     *  - Field reports: some Android devices return /64 for an IPv4 address
     * Unknown mask ⇒ isSameSubnet fails OPEN (never blocks a print) and the
     * scanner falls back to the classic /24 block.
     */
    data class LocalNetwork(
        val localIp: String,
        val prefixLength: Short?
    ) {
        /** e.g. "10.176.2.11" + /12 → "10.176.0.0"; null mask → null. */
        val networkAddress: String? by lazy {
            prefixLength?.let { pl ->
                intFromIp(localIp).let { ip -> ipToString(ip and prefixMask(pl)) }
            }
        }
    }

    private fun getActiveLanNetwork(): LocalNetwork? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallback: LocalNetwork? = null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val isLanInterface = intf.name.lowercase().let {
                    it.startsWith("wlan") || it.startsWith("eth") || it.startsWith("ap")
                }
                for (ia in intf.interfaceAddresses) {
                    val addr = ia.address ?: continue
                    if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("127.")) continue
                    // Trust the platform prefix ONLY when it is a plausible IPv4 value.
                    // Bogus reports (0, /64 on IPv4, negative) ⇒ null = "mask unknown".
                    val raw = ia.networkPrefixLength
                    val plausible = raw in 1..32
                    val network = LocalNetwork(host, if (plausible) raw else null)
                    if (isLanInterface) return network
                    if (addr.isSiteLocalAddress && fallback == null) {
                        fallback = network
                    }
                }
            }
            fallback
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving local network", e)
            null
        }
    }

    /**
     * Legacy /24-style prefix (e.g. "192.168.1.") for one-tap manual entry and the
     * scan list. Null when offline.
     */
    fun getLocalSubnetPrefix(): String? {
        val network = getActiveLanNetwork() ?: return null
        return network.localIp.substringBeforeLast(".") + "."
    }

    /**
     * Gateway IPv4 string via the platform's source of truth: the active
     * network's LinkProperties default route (RouteInfo.getGateway — the API the
     * docs point to since WifiManager.dhcpInfo was deprecated in API 31). Works
     * for Wi-Fi AND Ethernet. Falls back to DhcpInfo (still the working path on
     * API 26-30 devices where some OEM builds return empty LinkProperties
     * routes until the network is validated). Null when offline/unknown.
     */
    private fun getGatewayIp(): String? {
        val fromLinkProperties = getGatewayIpViaLinkProperties()
        if (fromLinkProperties != null) return fromLinkProperties
        return try {
            val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val gw = wifi?.dhcpInfo?.gateway ?: 0
            if (gw == 0) return null
            // DhcpInfo stores IPv4 as a little-endian int (same layout
            // android.text.format.Formatter.formatIpAddress expects).
            listOf(gw and 0xFF, (gw shr 8) and 0xFF, (gw shr 16) and 0xFF, (gw ushr 24) and 0xFF)
                .joinToString(".")
        } catch (e: Exception) {
            Log.w(TAG, "Gateway via DhcpInfo failed", e)
            null
        }
    }

    private fun getGatewayIpViaLinkProperties(): String? {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        // Try the active network first (what the OS actually routes through),
        // then any other network with an IPv4 link address (covers the brief
        // window where activeNetwork is still cellular during a Wi-Fi roam).
        val candidates = buildList {
            runCatching { cm.activeNetwork }.getOrNull()?.let { add(it) }
            runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList()).let { addAll(it) }
        }.distinct()
        for (network in candidates) {
            val gateway = runCatching {
                val lp = cm.getLinkProperties(network) ?: return@runCatching null
                // IPv4 gateway only — the scan/prefill logic is IPv4-only.
                val hasIpv4 = lp.linkAddresses.any { it.address is Inet4Address }
                if (!hasIpv4) return@runCatching null
                lp.routes.firstOrNull { it.hasGateway() }?.gateway?.hostAddress
                    ?.takeIf { it.contains(".") }
            }.getOrNull()
            if (gateway != null) return gateway
        }
        return null
    }

    /**
     * The /24 block containing the DHCP GATEWAY (e.g. "10.176.0." for gateway
     * 10.176.0.5). On wide-mask networks (DHCP handing /12s and scattering devices
     * across different /24 blocks — e.g. device 10.176.2.11/12, gateway 10.176.0.5),
     * infrastructure like printers almost always sits in the GATEWAY's block, not
     * the device's. Null when offline or the gateway is unknown.
     */
    fun getGatewayBlockPrefix(): String? {
        return try {
            val ip = getGatewayIp() ?: return null
            if (ip.startsWith("0.")) null else ip.substringBeforeLast(".") + "."
        } catch (e: Exception) {
            Log.w(TAG, "Gateway block lookup failed", e)
            null
        }
    }

    /**
     * Best prefix to auto-fill for manual entry: the gateway's block when the
     * network is wider than /24 (infrastructure lives there), else the device's
     * own block (identical on normal /24 networks — zero behavior change).
     */
    fun getAutoFillSubnetPrefix(): String? =
        getGatewayBlockPrefix() ?: getLocalSubnetPrefix()

    /**
     * True when [host] is reachable on-link per the interface's REAL netmask
     * (not a /24 string assumption). Unknown local network, unknown mask, or
     * malformed input → allow, so a detection failure never blocks a valid print.
     */
    fun isSameSubnet(host: String): Boolean {
        val local = getActiveLanNetwork() ?: return true
        val maskLength = local.prefixLength ?: return true // unknown mask → fail open
        val hostIp = try { intFromIp(host) } catch (e: Exception) {
            Log.w(TAG, "Cannot parse printer IP '$host'", e)
            return true // malformed input: let the socket layer decide
        }
        val mask = prefixMask(maskLength)
        return (intFromIp(local.localIp) and mask) == (hostIp and mask)
    }

    suspend fun scanSubnet(
        port: Int = 9100,
        timeoutMs: Int = 250,
        onPrinterFound: ((DiscoveredPrinter) -> Unit)? = null
    ): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val local = getActiveLanNetwork() ?: return@withContext emptyList()
        val discovered = ConcurrentLinkedQueue<DiscoveredPrinter>()
        val limitedDispatcher = Dispatchers.IO.limitedParallelism(32)

        // Probe the true network range, capped for sanity: a /12 is 1,048,574 hosts —
        // scanning it all would take minutes. Strategy per mask:
        //  - /22–/29: scan the exact on-link range (≤1022 hosts).
        //  - wider or unknown: scan OUR /24 block + the GATEWAY's /24 block.
        //    On wide-mask networks (e.g. DHCP /12 scattering devices across
        //    10.176.1.x / 10.176.2.x with gateway 10.176.0.5), printers live in the
        //    gateway's block — the device's own block alone misses them.
        val baseIp = intFromIp(local.localIp)
        val maskLength = local.prefixLength
        val networkInt = baseIp and (maskLength?.let { prefixMask(it) } ?: 0)
        val scanTargets: List<String> = when {
            maskLength != null && maskLength >= 22 -> {
                val hostCount = (1 shl (32 - maskLength)) - 2
                (1..hostCount).map { offset -> ipToString(networkInt + offset) }
            }
            else -> {
                val deviceBlock = baseIp and 0xFFFFFF00.toInt()
                val gatewayBlock = getGatewayBlockPrefix()
                    ?.let { prefix ->
                        runCatching { intFromIp(prefix + "1") and 0xFFFFFF00.toInt() }
                            .getOrNull()
                    }
                val blocks: List<Int> = if (gatewayBlock != null && gatewayBlock != deviceBlock) {
                    // Wide-mask LAN: devices AND infrastructure are scattered across
                    // /24 blocks. Real case (Lenovo TB-X505X on a /12): device in
                    // 10.176.2.x, gateway in 10.176.0.x, printer in 10.176.1.x — a
                    // two-block scan misses the intermediate one. Bridge gateway→device
                    // with a capped contiguous band (gateway side first: infrastructure
                    // bias), always including the device block.
                    val lo = minOf(deviceBlock, gatewayBlock)
                    val hi = maxOf(deviceBlock, gatewayBlock)
                    val span = (hi - lo) ushr 8
                    val steps = if (span <= MAX_SCAN_BLOCKS) 0..span else 0 until MAX_SCAN_BLOCKS
                    (steps.map { step -> lo + (step shl 8) } + deviceBlock).distinct()
                } else {
                    listOf(deviceBlock)
                }
                blocks.flatMap { block -> (1..254).map { h -> ipToString(block + h) } }
            }
        }

        val scanStarted = System.currentTimeMillis()
        // Failure-reason census: a clean-looking "0 found" must be explainable
        // (timeouts vs refusals vs other) — printers on wide-mask LANs have
        // historically failed here in ways only visible with this breakdown.
        val failureReasons = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val scanJobs = scanTargets.map { targetIp ->
            async(limitedDispatcher) {
                val probe = probePortCategorized(targetIp, port, timeoutMs)
                when (probe) {
                    ProbeResult.OPEN -> {
                        val printer = DiscoveredPrinter(ip = targetIp, port = port)
                        discovered.add(printer)
                        onPrinterFound?.invoke(printer)
                    }
                    ProbeResult.TIMEOUT -> failureReasons.merge("timeout", 1, Int::plus)
                    ProbeResult.REFUSED -> failureReasons.merge("refused", 1, Int::plus)
                    ProbeResult.ERROR -> failureReasons.merge("error", 1, Int::plus)
                }
            }
        }
        scanJobs.awaitAll()
        Log.i(
            TAG,
            "Subnet scan done: local=${local.localIp} mask=${local.prefixLength} " +
                "targets=${scanTargets.size} open=${discovered.size} " +
                "reasons=$failureReasons tookMs=${System.currentTimeMillis() - scanStarted}"
        )
        discovered.toList().sortedBy { it.ip }
    }

    private enum class ProbeResult { OPEN, TIMEOUT, REFUSED, ERROR }

    private fun probePortCategorized(ip: String, port: Int, timeoutMs: Int): ProbeResult {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            ProbeResult.OPEN
        } catch (e: java.net.SocketTimeoutException) {
            ProbeResult.TIMEOUT
        } catch (e: java.net.ConnectException) {
            ProbeResult.REFUSED
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Probe $ip:$port failed: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            }
            ProbeResult.ERROR
        }
    }

    private fun prefixMask(prefixLength: Short): Int =
        if (prefixLength <= 0) 0 else (-1 shl (32 - prefixLength))

    private fun intFromIp(ip: String): Int =
        ip.split(".").map { part -> part.toInt().also { require(it in 0..255) } }
            .fold(0) { acc, octet -> (acc shl 8) or octet }

    private fun ipToString(ip: Int): String =
        listOf((ip ushr 24) and 0xFF, (ip ushr 16) and 0xFF, (ip ushr 8) and 0xFF, ip and 0xFF)
            .joinToString(".")

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

        /** Max /24 blocks scanned when bridging gateway→device on wide-mask LANs. */
        private const val MAX_SCAN_BLOCKS = 8
    }
}
