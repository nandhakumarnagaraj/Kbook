package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.local.entity.PermissionCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * - Persisted to Room (permission_cache) so the granted set + authorization
 *   revision survive process death while offline (P1)
 * - Offline: uses last-known cached permissions (graceful degradation)
 * - Updated every sync cycle (15 min) or on demand
 */
@Singleton
class PermissionManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val databaseProvider: DatabaseProvider
) {
    private val tag = "PermissionManager"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _grantedPermissions = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissions: StateFlow<Set<String>> = _grantedPermissions.asStateFlow()

    private val _permissionsLoaded = MutableStateFlow(false)
    val permissionsLoaded: StateFlow<Boolean> = _permissionsLoaded.asStateFlow()

    /**
     * The acting user's monotonic authorization revision (P1). Stamped onto
     * locally-created menu edits so the server can run Decision-A-strict
     * revalidation. 0 until the first sync (or restore) supplies a value.
     */
    private val _permissionRevision = MutableStateFlow(0L)
    val permissionRevision: StateFlow<Long> = _permissionRevision.asStateFlow()

    /** Current authorization revision for stamping local edits. */
    fun currentRevision(): Long = _permissionRevision.value

    /**
     * Restore the cached granted set + revision from Room on cold start, so an
     * offline non-owner keeps working before the first successful pull. Safe no-op
     * on failure (offline-first: absence of cache just means "no cached grants yet").
     */
    fun restoreFromCache() {
        ioScope.launch {
            try {
                val userId = sessionManager.getActiveUserId()
                val dao = databaseProvider.getDatabase().permissionCacheDao()
                val cached = (if (userId != null && userId > 0) dao.getForUser(userId) else null)
                    ?: dao.getMostRecent()
                if (cached != null) {
                    val set = cached.grantedCsv.split(",").filter { it.isNotBlank() }.toSet()
                    _grantedPermissions.value = set
                    _permissionRevision.value = cached.permissionRevision
                    _permissionsLoaded.value = true
                    Log.i(tag, "Restored ${set.size} cached permissions, revision=${cached.permissionRevision}")
                }
            } catch (e: Exception) {
                Log.w(tag, "Permission cache restore failed (non-fatal)", e)
            }
        }
    }

    /**
     * Check if the current user has a specific permission.
     * OWNER always returns true. Other roles check the cached set.
     *
     * Menu implication (mirrors server MenuChangeType.satisfies): holding
     * menu.edit_full satisfies menu.edit_price and menu.toggle_availability.
     */
    fun hasPermission(permissionKey: String): Boolean {
        if (sessionManager.isOwner()) return true
        val granted = _grantedPermissions.value
        if (granted.contains(permissionKey)) return true
        if ((permissionKey == MENU_EDIT_PRICE || permissionKey == MENU_TOGGLE_AVAILABILITY)
            && granted.contains(MENU_EDIT_FULL)) {
            return true
        }
        return false
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
        updateFromSync(permissions, null)
    }

    /**
     * Update the permission cache + authorization revision from sync pull response.
     * Persists both to Room so they survive process death while offline (P1).
     */
    fun updateFromSync(permissions: List<String>?, revision: Long?) {
        if (permissions == null && revision == null) return
        var changed = false
        if (permissions != null) {
            val newSet = permissions.toSet()
            if (newSet != _grantedPermissions.value) {
                Log.i(tag, "Permissions updated: ${newSet.size} granted (was ${_grantedPermissions.value.size})")
                _grantedPermissions.value = newSet
                changed = true
            }
            _permissionsLoaded.value = true
        }
        if (revision != null && revision != _permissionRevision.value) {
            _permissionRevision.value = revision
            changed = true
        }
        if (changed) persistCache()
    }

    private fun persistCache() {
        val csv = _grantedPermissions.value.joinToString(",")
        val revision = _permissionRevision.value
        ioScope.launch {
            try {
                val userId = sessionManager.getActiveUserId()
                if (userId == null || userId <= 0) return@launch
                databaseProvider.getDatabase().permissionCacheDao().upsert(
                    PermissionCacheEntity(
                        userId = userId,
                        grantedCsv = csv,
                        permissionRevision = revision
                    )
                )
            } catch (e: Exception) {
                Log.w(tag, "Permission cache persist failed (non-fatal)", e)
            }
        }
    }

    /**
     * Clear permissions (on logout).
     */
    fun clear() {
        _grantedPermissions.value = emptySet()
        _permissionsLoaded.value = false
        _permissionRevision.value = 0L
        ioScope.launch {
            try {
                databaseProvider.getDatabase().permissionCacheDao().clear()
            } catch (e: Exception) {
                Log.w(tag, "Permission cache clear failed (non-fatal)", e)
            }
        }
    }

    // ── Request Access Flow ──────────────────────────────────────────────────

    private val _requestInFlight = MutableStateFlow(false)
    val requestInFlight: StateFlow<Boolean> = _requestInFlight.asStateFlow()

    private val _lastRequestResult = MutableStateFlow<RequestResult?>(null)
    val lastRequestResult: StateFlow<RequestResult?> = _lastRequestResult.asStateFlow()

    /**
     * Submit a permission request to the server.
     * Returns success/failure via lastRequestResult StateFlow.
     */
    suspend fun requestAccess(api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi, permissionKey: String, reason: String? = null) {
        _requestInFlight.value = true
        try {
            val body = com.khanabook.lite.pos.data.remote.api.PermissionRequestBody(permissionKey, reason)
            val response = api.requestPermission(body)
            val requestId = (response["requestId"] as? Number)?.toLong()
            _lastRequestResult.value = RequestResult.Success(permissionKey, requestId)
            Log.i(tag, "Permission request submitted: $permissionKey → requestId=$requestId")
        } catch (e: Exception) {
            Log.e(tag, "Permission request failed: $permissionKey", e)
            val message = when {
                e.message?.contains("already have") == true -> "You already have this permission"
                e.message?.contains("already pending") == true -> "A request is already pending"
                else -> "Request failed. Try again later."
            }
            _lastRequestResult.value = RequestResult.Error(permissionKey, message)
        } finally {
            _requestInFlight.value = false
        }
    }

    fun clearRequestResult() {
        _lastRequestResult.value = null
    }

    /**
     * Get display name for a permission key (for UI).
     */
    fun getDisplayName(permissionKey: String): String {
        return PERMISSION_DISPLAY_NAMES[permissionKey] ?: permissionKey
    }

    sealed class RequestResult {
        data class Success(val permissionKey: String, val requestId: Long?) : RequestResult()
        data class Error(val permissionKey: String, val message: String) : RequestResult()
    }

    private val PERMISSION_DISPLAY_NAMES = mapOf(
        BILLING_CREATE to "Create Bills",
        BILLING_EDIT to "Edit Bills",
        BILLING_VOID to "Cancel/Void Bills",
        BILLING_DISCOUNT to "Apply Discounts",
        BILLING_REFUND to "Process Refunds",
        BILLING_SETTLE to "Mark Payment Received",
        MENU_VIEW to "View Menu",
        MENU_TOGGLE_AVAILABILITY to "Toggle Availability",
        MENU_EDIT_PRICE to "Change Prices",
        MENU_EDIT_FULL to "Full Menu Editing",
        MENU_ADD_ITEM to "Add Items",
        MENU_DELETE_ITEM to "Remove Items",
        ORDERS_VIEW to "View Orders",
        ORDERS_KOT_VIEW to "Kitchen Queue",
        ORDERS_KOT_READY to "Mark Ready",
        ORDERS_KOT_VOID to "Void KOT Items",
        REPORTS_DAY_SUMMARY to "Day Summary",
        REPORTS_FULL to "Full Reports",
        REPORTS_GST to "GST Reports",
        REPORTS_EXPORT to "Export Data",
        STAFF_VIEW to "View Staff",
        STAFF_ADD to "Add Staff",
        STAFF_EDIT to "Edit Staff",
        STAFF_REMOVE to "Deactivate Staff",
        STAFF_PERMISSIONS to "Manage Permissions",
        SETTINGS_SHOP_PROFILE to "Shop Profile",
        SETTINGS_PAYMENT to "Payment Settings",
        SETTINGS_PRINTER to "Printer Settings",
        SETTINGS_TERMINAL to "Manage Devices",
        SETTINGS_GST to "GST/FSSAI Settings"
    )

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
        const val MENU_EDIT_FULL = "menu.edit_full"
        const val MENU_ADD_ITEM = "menu.add_item"
        const val MENU_DELETE_ITEM = "menu.delete_item"

        // Orders
        const val ORDERS_VIEW = "orders.view"
        const val ORDERS_KOT_VIEW = "orders.kot_view"
        const val ORDERS_KOT_READY = "orders.kot_ready"
        const val ORDERS_KOT_VOID = "orders.kot_void"

        // Reports
        const val REPORTS_DAY_SUMMARY = "reports.day_summary"
        const val REPORTS_FULL = "reports.full"
        const val REPORTS_GST = "reports.gst"
        const val REPORTS_EXPORT = "reports.export"

        // Staff
        const val STAFF_VIEW = "staff.view"
        const val STAFF_ADD = "staff.add"
        const val STAFF_EDIT = "staff.edit"
        const val STAFF_REMOVE = "staff.remove"
        const val STAFF_PERMISSIONS = "staff.permissions"

        // Settings
        const val SETTINGS_SHOP_PROFILE = "settings.shop_profile"
        const val SETTINGS_PAYMENT = "settings.payment"
        const val SETTINGS_PRINTER = "settings.printer"
        const val SETTINGS_TERMINAL = "settings.terminal"
        const val SETTINGS_GST = "settings.gst"


    }
}
