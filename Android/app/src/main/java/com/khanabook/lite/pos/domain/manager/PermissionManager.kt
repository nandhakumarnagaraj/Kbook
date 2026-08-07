package com.khanabook.lite.pos.domain.manager

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages granular permissions for the current user.
 *
 * Design principles:
 * - OWNER always has all permissions (no DB lookup needed)
 * - Permissions are synced from server via sync pull (grantedPermissions array)
 * - Cached in memory for instant UI checks (no DB read per check)
 * - Offline: uses last-known cached permissions (graceful degradation)
 * - Updated every sync cycle (15 min) or on demand
 */
@Singleton
class PermissionManager @Inject constructor(
    private val sessionManager: SessionManager
) {
    private val tag = "PermissionManager"

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions.asStateFlow()

    private val _permissionsLoaded = MutableStateFlow(false)
    val permissionsLoaded: StateFlow<Boolean> = _permissionsLoaded.asStateFlow()

    /**
     * Check if the current user has a specific permission.
     * OWNER always returns true. Other roles check the cached set.
     */
    fun hasPermission(permissionKey: String): Boolean {
        if (sessionManager.isOwner()) return true
        return _grantedPermissions.value.contains(permissionKey)
    }

    /**
     * Check multiple permissions at once. Returns true if ALL are granted.
     */
    fun hasAllPermissions(vararg keys: String): Boolean {
        if (sessionManager.isOwner()) return true
        return keys.all { _grantedPermissions.value.contains(it) }
    }

    /**
     * Check multiple permissions. Returns true if ANY is granted.
     */
    fun hasAnyPermission(vararg keys: String): Boolean {
        if (sessionManager.isOwner()) return true
        return keys.any { _grantedPermissions.value.contains(it) }
    }

    /**
     * Update the permission cache from sync pull response.
     * Called by MasterSyncProcessor after receiving grantedPermissions from server.
     */
    fun updateFromSync(permissions: List<String>?) {
        if (permissions == null) return
        val newSet = permissions.toSet()
        if (newSet != _grantedPermissions.value) {
            Log.i(tag, "Permissions updated: ${newSet.size} granted (was ${_grantedPermissions.value.size})")
            _grantedPermissions.value = newSet
        }
        _permissionsLoaded.value = true
    }

    /**
     * Clear permissions (on logout).
     */
    fun clear() {
        _grantedPermissions.value = emptySet()
        _permissionsLoaded.value = false
    }

    // ── Permission key constants (mirrors server PermissionKey enum) ──────────

    companion object {
        // Billing
        const val BILLING_CREATE = "billing.create"
        const val BILLING_EDIT = "billing.edit"
        const val BILLING_VOID = "billing.void"
        const val BILLING_DISCOUNT = "billing.discount"
        const val BILLING_REFUND = "billing.refund"
        const val BILLING_SETTLE = "billing.settle"

        // Menu
        const val MENU_VIEW = "menu.view"
        const val MENU_TOGGLE_AVAILABILITY = "menu.toggle_availability"
        const val MENU_EDIT_PRICE = "menu.edit_price"
        const val MENU_ADD_ITEM = "menu.add_item"
        const val MENU_DELETE_ITEM = "menu.delete_item"

        // Orders
        const val ORDERS_VIEW = "orders.view"
        const val ORDERS_KOT_VIEW = "orders.kot_view"
        const val ORDERS_KOT_READY = "orders.kot_ready"

        // Reports
        const val REPORTS_DAY_SUMMARY = "reports.day_summary"
        const val REPORTS_FULL = "reports.full"
        const val REPORTS_GST = "reports.gst"
        const val REPORTS_EXPORT = "reports.export"

        // Staff
        const val STAFF_VIEW = "staff.view"
        const val STAFF_ADD = "staff.add"
        const val STAFF_PERMISSIONS = "staff.permissions"

        // Settings
        const val SETTINGS_SHOP_PROFILE = "settings.shop_profile"
        const val SETTINGS_PAYMENT = "settings.payment"
        const val SETTINGS_PRINTER = "settings.printer"
        const val SETTINGS_TERMINAL = "settings.terminal"
    }
}
