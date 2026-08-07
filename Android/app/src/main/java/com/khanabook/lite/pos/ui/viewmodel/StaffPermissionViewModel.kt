package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.PermissionGrantBody
import com.khanabook.lite.pos.data.remote.api.PermissionRequestDto
import com.khanabook.lite.pos.data.remote.api.PermissionResolveBody
import com.khanabook.lite.pos.data.remote.api.PermissionRevokeBody
import com.khanabook.lite.pos.domain.manager.PermissionManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaffPermissionUiState(
    val isLoading: Boolean = true,
    val staffList: List<StaffMemberPermissions> = emptyList(),
    val pendingRequests: List<PermissionRequestDto> = emptyList(),
    val error: String? = null,
    val actionInFlight: Boolean = false
)

data class StaffMemberPermissions(
    val userId: Long,
    val name: String,
    val role: String,
    val permissions: List<PermissionItem>
)

data class PermissionItem(
    val key: String,
    val displayName: String,
    val category: String,
    val granted: Boolean
)

@HiltViewModel
class StaffPermissionViewModel @Inject constructor(
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager,
    private val permissionManager: PermissionManager
) : ViewModel() {

    private val tag = "StaffPermissionVM"

    private val _uiState = MutableStateFlow(StaffPermissionUiState())
    val uiState: StateFlow<StaffPermissionUiState> = _uiState.asStateFlow()

    // Permission categories for the toggle grid
    val permissionCategories = listOf(
        "BILLING" to listOf(
            PermissionManager.BILLING_CREATE to "Create Bills",
            PermissionManager.BILLING_EDIT to "Edit Bills",
            PermissionManager.BILLING_VOID to "Void Bills",
            PermissionManager.BILLING_DISCOUNT to "Apply Discounts",
            PermissionManager.BILLING_REFUND to "Refunds",
            PermissionManager.BILLING_SETTLE to "Settle Payments"
        ),
        "MENU" to listOf(
            PermissionManager.MENU_VIEW to "View Menu",
            PermissionManager.MENU_TOGGLE_AVAILABILITY to "Toggle Availability",
            PermissionManager.MENU_EDIT_PRICE to "Edit Prices",
            PermissionManager.MENU_ADD_ITEM to "Add Items",
            PermissionManager.MENU_DELETE_ITEM to "Delete Items"
        ),
        "ORDERS" to listOf(
            PermissionManager.ORDERS_VIEW to "View Orders",
            PermissionManager.ORDERS_KOT_VIEW to "Kitchen Queue",
            PermissionManager.ORDERS_KOT_READY to "Mark Ready"
        ),
        "REPORTS" to listOf(
            PermissionManager.REPORTS_DAY_SUMMARY to "Day Summary",
            PermissionManager.REPORTS_FULL to "Full Reports",
            PermissionManager.REPORTS_GST to "GST Reports",
            PermissionManager.REPORTS_EXPORT to "Export Data"
        ),
        "SETTINGS" to listOf(
            PermissionManager.SETTINGS_SHOP_PROFILE to "Shop Profile",
            PermissionManager.SETTINGS_PAYMENT to "Payment Settings",
            PermissionManager.SETTINGS_PRINTER to "Printer Config",
            PermissionManager.SETTINGS_TERMINAL to "Devices"
        )
    )

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val pendingRequests = try { api.getPendingPermissionRequests() } catch (e: Exception) { emptyList() }

                // Load each staff member's permissions
                // For now we use the business staff list + individual permission queries
                val staffResponse = api.getStaffList()
                val staffPermissions = staffResponse
                    .filter { it.role != "OWNER" }
                    .map { staff ->
                        val perms = try {
                            val response = api.getUserPermissions(staff.userId)
                            response.permissions.map { p ->
                                PermissionItem(p.permissionKey, p.displayName, p.category, p.granted)
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to load permissions for user ${staff.userId}", e)
                            emptyList()
                        }
                        StaffMemberPermissions(
                            userId = staff.userId,
                            name = staff.name,
                            role = staff.role,
                            permissions = perms
                        )
                    }

                _uiState.value = StaffPermissionUiState(
                    isLoading = false,
                    staffList = staffPermissions,
                    pendingRequests = pendingRequests
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to load permission data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load staff permissions. Check your connection."
                )
            }
        }
    }

    fun togglePermission(userId: Long, permissionKey: String, currentlyGranted: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                if (currentlyGranted) {
                    api.revokePermission(PermissionRevokeBody(userId, permissionKey))
                } else {
                    api.grantPermission(PermissionGrantBody(userId, permissionKey))
                }
                // Update local state optimistically
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    staffList = _uiState.value.staffList.map { staff ->
                        if (staff.userId == userId) {
                            staff.copy(permissions = staff.permissions.map { p ->
                                if (p.key == permissionKey) p.copy(granted = !currentlyGranted) else p
                            })
                        } else staff
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to toggle permission", e)
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    error = "Failed to update permission. Try again."
                )
            }
        }
    }

    fun approveRequest(requestId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                api.resolvePermissionRequest(requestId, PermissionResolveBody("APPROVE"))
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    pendingRequests = _uiState.value.pendingRequests.filter { it.id != requestId }
                )
                loadData() // Refresh to show updated permissions
            } catch (e: Exception) {
                Log.e(tag, "Failed to approve request", e)
                _uiState.value = _uiState.value.copy(actionInFlight = false, error = "Failed to approve. Try again.")
            }
        }
    }

    fun rejectRequest(requestId: Long, reason: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInFlight = true)
            try {
                api.resolvePermissionRequest(requestId, PermissionResolveBody("REJECT", reason))
                _uiState.value = _uiState.value.copy(
                    actionInFlight = false,
                    pendingRequests = _uiState.value.pendingRequests.filter { it.id != requestId }
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to reject request", e)
                _uiState.value = _uiState.value.copy(actionInFlight = false, error = "Failed to reject. Try again.")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
