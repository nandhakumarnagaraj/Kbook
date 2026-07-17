package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.util.TerminalPendingApprovalException
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import retrofit2.HttpException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InitialSyncState {
    object Idle : InitialSyncState()
    object Syncing : InitialSyncState()
    object Success : InitialSyncState()
    object SessionExpired : InitialSyncState()
    data class PendingApproval(val requestId: Long?) : InitialSyncState()
    data class Error(val message: String) : InitialSyncState()
}

@HiltViewModel
class InitialSyncViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager,
    private val api: com.khanabook.lite.pos.data.remote.api.KhanaBookApi
) : ViewModel() {

    private val _syncState = MutableStateFlow<InitialSyncState>(InitialSyncState.Idle)
    val syncState: StateFlow<InitialSyncState> = _syncState.asStateFlow()

    init {
        startInitialSync()
    }

    fun startInitialSync() {
        viewModelScope.launch {
            _syncState.value = InitialSyncState.Syncing
            try {
                val result = syncManager.performFullSync()

                if (result.isSuccess) {
                    sessionManager.setInitialSyncCompleted(true)
                    _syncState.value = InitialSyncState.Success
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("InitialSyncViewModel", "Master pull failed", error)
                    if (error is TerminalPendingApprovalException) {
                        _syncState.value = InitialSyncState.PendingApproval(error.requestId)
                    } else if (error is HttpException && error.code() == 401) {
                        sessionManager.invalidateAuthSession()
                        _syncState.value = InitialSyncState.SessionExpired
                    } else if (error is android.database.sqlite.SQLiteException) {
                        _syncState.value = InitialSyncState.Error(
                            "Setup failed. Please clear app data and try again."
                        )
                    } else {
                        _syncState.value = InitialSyncState.Error(
                            UserMessageSanitizer.sanitize(error, "Setup failed. Please check your connection and try again.")
                        )
                    }
                }
            } catch (e: TerminalPendingApprovalException) {
                Log.w("InitialSyncViewModel", "Terminal pending approval: requestId=${e.requestId}")
                _syncState.value = InitialSyncState.PendingApproval(e.requestId)
            } catch (e: Exception) {
                Log.e("InitialSyncViewModel", "Unexpected error during initial sync", e)
                if (e is HttpException && e.code() == 401) {
                    sessionManager.invalidateAuthSession()
                    _syncState.value = InitialSyncState.SessionExpired
                } else if (e is android.database.sqlite.SQLiteException) {
                    _syncState.value = InitialSyncState.Error(
                        "Setup failed. Please clear app data and try again."
                    )
                } else {
                    _syncState.value = InitialSyncState.Error(
                        UserMessageSanitizer.sanitize(e, "Setup failed. Please check your connection and try again.")
                    )
                }
            }
        }
    }

    /**
     * Polls the request status endpoint using the stored requestId.
     * Does NOT create a new activation request — only checks existing request status.
     * If approved, calls complete-activation to securely obtain terminal credentials.
     * If rejected, shows rejection state.
     */
    fun pollRequestStatus() {
        val state = _syncState.value
        if (state !is InitialSyncState.PendingApproval) return
        val requestId = state.requestId ?: return

        viewModelScope.launch {
            try {
                val response = api.getTerminalRequestStatus(requestId)
                val body = response.body()?.string()
                if (body.isNullOrBlank()) return@launch

                val gson = com.google.gson.Gson()
                val pendingResponse = gson.fromJson(body,
                    com.khanabook.lite.pos.data.remote.api.TerminalPendingResponse::class.java)

                when (pendingResponse.status) {
                    "APPROVED" -> {
                        // Approved — securely obtain credentials via complete-activation
                        completeActivation(requestId)
                    }
                    "REJECTED" -> {
                        _syncState.value = InitialSyncState.Error(
                            pendingResponse.message ?: "Device registration was rejected by admin"
                        )
                    }
                    "EXPIRED" -> {
                        _syncState.value = InitialSyncState.Error(
                            "Registration request has expired. Please try again."
                        )
                    }
                    "PENDING" -> {
                        // Still pending — no state change needed
                    }
                }
            } catch (e: Exception) {
                Log.w("InitialSyncViewModel", "Poll request status failed", e)
                // Don't change state on network errors during polling — just retry next cycle
            }
        }
    }

    /**
     * Securely completes activation by calling POST /complete-activation with
     * the request ID and this device's ID. This proves the caller is the same
     * installation that submitted the original request.
     */
    private suspend fun completeActivation(requestId: Long) {
        try {
            val deviceId = sessionManager.getDeviceId()
            val completeRequest = com.khanabook.lite.pos.data.remote.api.CompleteActivationRequest(
                requestId = requestId,
                deviceId = deviceId
            )
            val response = api.completeActivation(completeRequest)
            val body = response.body()?.string()

            if (response.isSuccessful && !body.isNullOrBlank()) {
                val gson = com.google.gson.Gson()
                val terminalResponse = gson.fromJson(body,
                    com.khanabook.lite.pos.data.remote.api.TerminalActivationResponse::class.java)
                if (terminalResponse.terminalToken != null) {
                    val terminalId = terminalResponse.terminalId?.takeIf { it.isNotBlank() }
                        ?: terminalResponse.terminalSeries
                    sessionManager.saveTerminalIdentity(
                        com.khanabook.lite.pos.domain.model.TerminalIdentity(
                            restaurantId = sessionManager.getRestaurantId(),
                            terminalId = terminalId,
                            deviceId = deviceId,
                            terminalName = terminalResponse.terminalName,
                            terminalSeries = terminalResponse.terminalSeries,
                            isActive = terminalResponse.isActive ?: true,
                            registeredAt = terminalResponse.registeredAt,
                            lastVerifiedAt = terminalResponse.lastVerifiedAt ?: System.currentTimeMillis(),
                            terminalToken = terminalResponse.terminalToken
                        )
                    )
                    // Terminal credentials obtained — proceed to full sync
                    startInitialSync()
                    return
                }
            }

            // Completion failed — show error
            _syncState.value = InitialSyncState.Error(
                "Failed to complete terminal activation. Please try again."
            )
        } catch (e: Exception) {
            Log.e("InitialSyncViewModel", "Complete activation failed", e)
            _syncState.value = InitialSyncState.Error(
                "Failed to complete terminal activation. Please try again."
            )
        }
    }
}
