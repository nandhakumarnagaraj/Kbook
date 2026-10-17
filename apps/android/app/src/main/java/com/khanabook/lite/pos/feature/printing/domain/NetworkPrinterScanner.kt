package com.khanabook.lite.pos.feature.printing.domain

import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Looper
import android.util.Log
import com.khanabook.lite.pos.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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

/**
 * P0.5: outcome census for one subnet scan, surfaced in Settings so a
 * clean-looking "0 found" is debuggable without adb. Reasons keys follow the
 * log-census vocabulary ("timeout"/"refused"/"error") with per-fallback-port
 * variants ("port631:timeout", ...).
 */
data class ScanCensus(
    val localIp: String,
    val prefixLength: Short?,
    val targets: Int,
    val portsAttempted: List<Int>,
    val openCount: Int,
    val reasons: Map<String, Int>,
    val durationMs: Long
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
            // P0.2 triangulation: WifiManager's connection IP is the OS-level
            // source of truth for "which LAN IP Wi-Fi is actually using right now".
            // When it disagrees with the enumerated interface IP, the
            // NetworkInterface list is stale (pre-roam / pre-reconnect) — distrust
            // the reported prefix so the guard fails OPEN instead of scanning a
            // wrong block or blocking a valid print.
            val wifiIp = getWifiConnectedIp()
            // P0.3 cross-validation: the DHCP netmask is the mask the router
            // actually assigned — prefer it over the platform prefix when both
            // are known but disagree (OEMs have reported wrong-but-plausible
            // prefixes, e.g. /32 for an IPv4 address).
            val dhcpPrefix = getDhcpPrefix()
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
                    // Trust the platform prefix ONLY when it is a plausible IPv4
                    // value. Bogus reports (0, /64 on IPv4, negative) ⇒ null =
                    // "mask unknown". When DHCP disagrees, trust the DHCP mask.
                    val raw = ia.networkPrefixLength
                    val plausible = raw in 1..32
                    val prefix = when {
                        !plausible -> null
                        dhcpPrefix != null && dhcpPrefix != raw -> dhcpPrefix
                        else -> raw
                    }
                    // P0.2: interface IP stale vs WifiManager → distrust prefix.
                    val trustedPrefix =
                        if (wifiIp != null && wifiIp != host) null else prefix
                    val network = LocalNetwork(host, trustedPrefix)
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
     * The Wi-Fi connection's current IPv4 as reported by
     * WifiManager.getConnectionInfo().ipAddress. Deprecated since API 31 but
     * still working on Android 10 AND 15 (the same legacy path the DhcpInfo
     * fallback uses). Null when Wi-Fi is off or the address is unknown.
     */
    private fun getWifiConnectedIp(): String? {
        val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val ip = runCatching { wifi.connectionInfo?.ipAddress ?: 0 }.getOrDefault(0)
        if (ip == 0) return null
        return legacyDhcpIntToIp(ip)
    }

    /**
     * The netmask actually assigned over DHCP as a prefix length, or null when
     * Wi-Fi is off / the mask is not a valid contiguous IPv4 mask. DhcpInfo
     * stores its ints in the same little-endian layout as its gateway field;
     * cross-validates the prefix from NetworkInterface.interfaceAddresses,
     * which a device/OEM can report wrong-but-plausibly (/32, /64-on-IPv4).
     */
    private fun getDhcpPrefix(): Short? {
        val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val mask = runCatching { wifi.dhcpInfo?.netmask ?: 0 }.getOrDefault(0)
        if (mask == 0) return null
        return prefixLengthFromNetmask(Integer.reverseBytes(mask))
    }

    /** Contiguous 32-bit netmask → prefix length, or null when not a valid mask. */
    private fun prefixLengthFromNetmask(netmask: Int): Short? {
        val value = netmask.toLong() and 0xFFFFFFFFL
        val trailingZeros = java.lang.Long.numberOfTrailingZeros(value)
        val prefixLength = 32 - trailingZeros
        val expected = (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        return if (expected == value) prefixLength.toShort() else null
    }

    /** Converts DhcpInfo's little-endian int IPv4 into dotted decimal string. */
    private fun legacyDhcpIntToIp(value: Int): String =
        listOf(value and 0xFF, (value shr 8) and 0xFF, (value shr 16) and 0xFF, (value ushr 24) and 0xFF)
            .joinToString(".")

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
     *
     * When BOTH sourcing legs come back empty, logs a diagnostic breadcrumb
     * splitting the possible causes — missing IPv4 route, absent/zero DHCP
     * gateway, or no enumerable interface with a usable mask — so a
     * "works here, broken there" report can be attributed to a specific OEM
     * network-stack state without an adb connection.
     */
    fun getAutoFillSubnetPrefix(): String? {
        val gatewayBlock = getGatewayBlockPrefix()
        if (gatewayBlock != null) return gatewayBlock

        val localPrefix = getLocalSubnetPrefix()
        if (localPrefix != null) return localPrefix

        // Breadcrumb: routeGw is the modern LinkProperties default-route gateway,
        // dhcpGw the legacy WifiManager.dhcpInfo gateway (same little-endian
        // layout as getGatewayIp). One null + one set isolates which leg failed;
        // both null ⇒ offline / route-less. A present lan entry with a null mask
        // logs the implausible-prefix case the strict-guard path otherwise hides.
        val routeGw = getGatewayIpViaLinkProperties()
        val dhcpGw = runCatching {
            (appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.dhcpInfo?.gateway ?: 0
        }.getOrDefault(0)
        val lan = getActiveLanNetwork()
        Log.w(
            TAG,
            "Auto-fill subnet prefix unavailable: routeGw=${routeGw ?: "null"} " +
                "dhcpGw=${if (dhcpGw != 0) legacyDhcpIntToIp(dhcpGw) else "null"} " +
                "lanIp=${lan?.localIp ?: "null"} lanPrefix=${lan?.prefixLength?.toString() ?: "null"}"
        )
        return null
    }

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

    /**
     * Subnet sweep for printers. [deepScan] selects the port set:
     *  - quick (default): RAW 9100 across all targets, then a fallback pass of
     *    631 (IPP) + 515 (LPD) on hosts that are ALIVE but refused 9100.
     *  - deep: the same primary pass plus 9101/9102/721 fallbacks (6 ports).
     * The fallback pass only touches refused hosts (refusals resolve in
     * microseconds), so total scan time stays roughly flat. [onCensus] receives
     * the outcome census for the Settings diagnostics surface (P0.5).
     */
    suspend fun scanSubnet(
        port: Int = 9100,
        timeoutMs: Int = 250,
        deepScan: Boolean = false,
        onPrinterFound: ((DiscoveredPrinter) -> Unit)? = null,
        onCensus: ((ScanCensus) -> Unit)? = null
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
        val fallbackPorts = if (deepScan) DEEP_FALLBACK_PORTS else FALLBACK_PORTS

        // Phase 1 — primary port across every target (RAW 9100 by default).
        val primaryOpen = ConcurrentLinkedQueue<String>()
        val primaryRefused = ConcurrentLinkedQueue<String>()
        val phase1Jobs = scanTargets.map { targetIp ->
            async(limitedDispatcher) {
                when (probePortCategorized(targetIp, port, timeoutMs)) {
                    ProbeResult.OPEN -> primaryOpen.add(targetIp)
                    ProbeResult.TIMEOUT -> failureReasons.merge("timeout", 1, Int::plus)
                    ProbeResult.REFUSED -> primaryRefused.add(targetIp)
                    ProbeResult.ERROR -> failureReasons.merge("error", 1, Int::plus)
                }
            }
        }
        phase1Jobs.awaitAll()
        primaryOpen.forEach { targetIp ->
            val printer = DiscoveredPrinter(ip = targetIp, port = port)
            discovered.add(printer)
            onPrinterFound?.invoke(printer)
        }

        // Phase 2 — P1.6 fallback pass: hosts alive but closed on the primary
        // port get IPP (631) + LPD (515) probed (competitor-benchmarked; catches
        // printers where RAW 9100 is disabled). Deep scan adds 9101/9102/721.
        if (primaryRefused.isNotEmpty() && fallbackPorts.isNotEmpty()) {
            val phase2Jobs = primaryRefused.map { targetIp ->
                async(limitedDispatcher) {
                    for (fp in fallbackPorts) {
                        when (probePortCategorized(targetIp, fp, timeoutMs)) {
                            ProbeResult.OPEN -> {
                                val printer = DiscoveredPrinter(ip = targetIp, port = fp)
                                discovered.add(printer)
                                onPrinterFound?.invoke(printer)
                                return@async
                            }
                            ProbeResult.TIMEOUT -> failureReasons.merge("port$fp:timeout", 1, Int::plus)
                            ProbeResult.REFUSED -> failureReasons.merge("port$fp:refused", 1, Int::plus)
                            ProbeResult.ERROR -> failureReasons.merge("port$fp:error", 1, Int::plus)
                        }
                    }
                }
            }
            phase2Jobs.awaitAll()
        }

        val census = ScanCensus(
            localIp = local.localIp,
            prefixLength = local.prefixLength,
            targets = scanTargets.size,
            portsAttempted = (listOf(port) + fallbackPorts).distinct(),
            openCount = discovered.size,
            reasons = failureReasons.toMap(),
            durationMs = System.currentTimeMillis() - scanStarted
        )
        onCensus?.invoke(census)
        Log.i(
            TAG,
            "Subnet scan done: local=${local.localIp} mask=${local.prefixLength} " +
                "deep=$deepScan ports=$census.portsAttempted targets=${scanTargets.size} " +
                "open=${discovered.size} reasons=$failureReasons tookMs=${census.durationMs}"
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

    /**
     * P1.7: mDNS /.local hostname pre-pass.
     *
     * 1. NsdManager discovery on `_printer._tcp` / `_ipp._tcp` (the documented
     *    Android zero-permission path — no location, no Wi-Fi scan permission).
     *    Each discovered service is resolved to its host IPv4 + port.
     * 2. Optional explicit hostname pass resolving e.g. "printer.local" via
     *    InetAddress (OS-level mDNS) and probing the standard ports.
     *
     * Cheap and quick (single service-resolution round-trip per printer), so
     * this runs BEFORE the subnet sweep and typically finds the printer fast on
     * normal LANs. Returns [] when NsdManager is unavailable (tests/emulator).
     */
    suspend fun scanMdns(
        discoverTimeoutMs: Long = 2_000L,
        hostnameHints: List<String> = emptyList(),
        onPrinterFound: ((DiscoveredPrinter) -> Unit)? = null
    ): List<DiscoveredPrinter> = withContext(Dispatchers.Main) {
        val context = appContext ?: return@withContext emptyList()
        val nsdManager = runCatching {
            context.getSystemService(Context.NSD_SERVICE) as NsdManager
        }.getOrNull()
        val found = java.util.Collections.synchronizedList(mutableListOf<DiscoveredPrinter>())
        val handler = android.os.Handler(Looper.getMainLooper())
        val seenKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        fun addIfNew(printer: DiscoveredPrinter): Boolean {
            val key = "${printer.ip}:${printer.port}"
            return if (seenKeys.add(key)) {
                found.add(printer)
                onPrinterFound?.invoke(printer)
                true
            } else false
        }

        nsdManager?.let { nsd ->
            val listeners = mutableListOf<NsdManager.DiscoveryListener>()
            val resolveListener = object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress?.takeIf { it.contains(".") } ?: return
                    val port = info.port.takeIf { it in 1..65535 } ?: 9100
                    addIfNew(
                        DiscoveredPrinter(
                            ip = host,
                            port = port,
                            name = friendlyNameFromService(info.serviceName, host)
                        )
                    )
                }

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            val start: (String) -> Unit = { serviceType ->
                try {
                    val listener = object : NsdManager.DiscoveryListener {
                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            // Only IPv4-backed services are useful to our TCP path;
                            // skip .local-only entries and resolve the rest.
                            if (!serviceInfo.serviceName.endsWith(MDNS_SUFFIX)) {
                                runCatching { nsd.resolveService(serviceInfo, resolveListener) }
                            }
                        }

                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

                        override fun onDiscoveryStarted(serviceType: String) {}

                        override fun onDiscoveryStopped(serviceType: String) {}

                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}

                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    }
                    listeners.add(listener)
                    nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "mDNS discovery unavailable for $serviceType: ${e.message}")
                    }
                }
            }
            start("_printer._tcp")
            start("_ipp._tcp")

            // Keep the coroutine alive long enough for onServiceFound →
            // onResolved round-trips, then tear down the listeners.
            delay(discoverTimeoutMs)
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }

        for (hint in hostnameHints) {
            // Optional explicit .local hostname, e.g. "printer.local" or "POS-58".
            val normalized = if (hint.endsWith(".local")) hint else "$hint.local"
            val ip = runCatching { java.net.InetAddress.getByName(normalized).hostAddress }
                .getOrNull()?.takeIf { it.contains(".") }
            if (ip != null) {
                for (port in MDNS_FALLBACK_PORTS) {
                    if (probePort(ip, port, 500)) {
                        addIfNew(DiscoveredPrinter(ip = ip, port = port, name = friendlyNameFromService(hint, ip)))
                        break
                    }
                }
            }
        }
        found.distinctBy { "${it.ip}:${it.port}" }
    }

    /**
     * P1.9: derive a friendly, vendor-style printer name from an mDNS service
     * name or hostname (e.g. "KPC307-UEWB._printer._tcp.local" →
     * "KPC307-UEWB"; "EPSON TM-T88VI" → itself). Falls back to the IP-derived
     * default when nothing usable is present.
     */
    private fun friendlyNameFromService(serviceName: String, ip: String): String {
        val candidate = serviceName
            .substringBefore("._")
            .substringBefore(".local")
            .replace('_', ' ')
            .trim()
        return when {
            candidate.isBlank() || candidate.equals(ip, ignoreCase = true) ->
                "Thermal Printer ($ip)"
            else -> candidate.take(48)
        }
    }

    companion object {
        private const val TAG = "NetworkPrinterScanner"

        /** Max /24 blocks scanned when bridging gateway→device on wide-mask LANs. */
        private const val MAX_SCAN_BLOCKS = 8

        /** P1.6: fallback ports probed after a REFUSED 9100 — IPP (631) + LPD (515). */
        private val FALLBACK_PORTS = listOf(631, 515)

        /** P1.8: deep-scan adds raw alternates (9101/9102) + LPD (721) to the set. */
        private val DEEP_FALLBACK_PORTS = listOf(631, 515, 9101, 9102, 721)

        /** P1.7: service types advertised by network printers. */
        private const val MDNS_SUFFIX = "._printer._tcp.local"

        /** P1.7: ports tried when resolving an explicit .local hostname. */
        private val MDNS_FALLBACK_PORTS = listOf(9100, 631, 515)
    }
}
