package com.khanabook.lite.pos.feature.notifications.domain

import android.content.Intent
import com.khanabook.lite.pos.core.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns a tapped notification into an in-app destination.
 *
 * The FCM service stamps every notification intent with `notification_type`.
 * Without this router those extras were never read, so tapping a notification
 * only opened the app at whatever screen it was last on â€” even though the
 * grouped summary invites the user to "tap to view".
 *
 * Mirrors [PaymentReturnManager]: MainActivity feeds intents in from onCreate /
 * onNewIntent, and a LaunchedEffect collects [pendingRoute] once the NavHost
 * exists. Routes are consumed exactly once so a rotation cannot re-navigate.
 */
object NotificationRouteManager {

    const val EXTRA_NOTIFICATION_TYPE = "notification_type"

    private val _pendingRoute = MutableStateFlow<String?>(null)

    /** Emits the route to open, or null when there is nothing pending. */
    val pendingRoute = _pendingRoute.asStateFlow()

    /**
     * Inspects a launch intent and records the destination it implies.
     * Safe to call with any intent; non-notification intents are ignored.
     */
    fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val type = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: return
        _pendingRoute.value = routeForType(type)
    }

    /** Clears the pending route after navigation so it is applied only once. */
    fun consume() {
        _pendingRoute.value = null
    }

    /**
     * Maps a server notification type to an existing nav route. Anything without
     * a more specific home lands on the notifications inbox, which always shows
     * the full message.
     */
    fun routeForType(type: String): String = when (type) {
        "payment_received", "qr_order" -> Routes.ACTIVE_ORDERS
        "kyc" -> Routes.EASEBUZZ_ONBOARDING
        "fssai_expiry" -> Routes.COMPLIANCE_DOCUMENTS
        // refund, settlement, inventory_low, terminal, system and anything new
        else -> Routes.NOTIFICATIONS
    }
}
