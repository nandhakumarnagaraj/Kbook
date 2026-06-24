package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

enum class ConnectionStatus {
    Available, Unavailable
}

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val status: Flow<ConnectionStatus> = callbackFlow {
        // Track which networks currently have validated internet.
        // CopyOnWriteArraySet is safe for concurrent add/remove from callbacks.
        val activeNetworks = CopyOnWriteArraySet<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activeNetworks.add(network)
                launch { send(ConnectionStatus.Available) }
            }

            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                // When both WiFi and mobile data are active, Android fires onLost()
                // for whichever interface is losing priority (e.g. mobile hands off to
                // WiFi). Only report Unavailable if NO validated network remains.
                launch {
                    if (activeNetworks.isEmpty()) {
                        send(ConnectionStatus.Unavailable)
                    }
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // Re-evaluate when capabilities change (e.g. captive portal resolved).
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasInternet) activeNetworks.add(network) else activeNetworks.remove(network)
                launch {
                    send(if (activeNetworks.isNotEmpty()) ConnectionStatus.Available else ConnectionStatus.Unavailable)
                }
            }
        }

        // NET_CAPABILITY_VALIDATED ensures we only track networks with confirmed
        // internet access — not just local Wi-Fi without a gateway/DNS.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Emit current state immediately on subscription.
        val initialStatus = if (isCurrentlyConnected()) ConnectionStatus.Available else ConnectionStatus.Unavailable
        trySend(initialStatus)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
            activeNetworks.clear()
        }
    }.distinctUntilChanged()

    /**
     * Synchronous check used for the initial emission only.
     * Uses getNetworkCapabilities on the active network — reliable for a one-shot
     * check without needing the deprecated allNetworks array.
     */
    private fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
