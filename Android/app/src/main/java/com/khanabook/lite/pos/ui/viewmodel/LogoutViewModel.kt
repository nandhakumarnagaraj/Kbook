package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class LogoutState {
    object Idle : LogoutState()
    object AttemptingPush : LogoutState()
    object ClearingData : LogoutState()
    data class WarningOfflineData(
        val totalCount: Int,
        val summary: String
    ) : LogoutState()
    object LoggedOut : LogoutState()
}

@HiltViewModel
class LogoutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val appDatabase: AppDatabase,
    private val billRepository: BillRepository,
    private val syncManager: SyncManager,
    private val userRepository: UserRepository,
    private val api: KhanaBookApi
) : ViewModel() {
    private val debugTag = "KhanaBookDebugAuth"

    private data class UnsyncedDataSummary(
        val totalCount: Int,
        val summary: String
    )

    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val logoutState: StateFlow<LogoutState> = _logoutState.asStateFlow()

    fun initiateLogout() {
        viewModelScope.launch {
            _logoutState.value = LogoutState.AttemptingPush

            try {
                val initialSummary = getUnsyncedDataSummary()
                if (initialSummary.totalCount > 0) {
                    syncManager.pushUnsyncedDataImmediately()
                    val remainingSummary = getUnsyncedDataSummary()
                    if (remainingSummary.totalCount == 0) {
                        performHardLogout()
                    } else {
                        _logoutState.value =
                            LogoutState.WarningOfflineData(
                                totalCount = remainingSummary.totalCount,
                                summary = remainingSummary.summary
                            )
                    }
                } else {
                    performHardLogout()
                }
            } catch (e: Exception) {
                val remainingSummary = getUnsyncedDataSummary()
                if (remainingSummary.totalCount > 0) {
                    _logoutState.value =
                        LogoutState.WarningOfflineData(
                            totalCount = remainingSummary.totalCount,
                            summary = remainingSummary.summary
                        )
                } else {
                    performHardLogout()
                }
            }
        }
    }

    fun forceLogoutDespiteWarning() {
        performSoftLogout()
    }

    fun cancelLogout() {
        _logoutState.value = LogoutState.Idle
    }

    // Soft logout: revokes token and locks the app but keeps the local Room DB intact.
    // Used when unsynced data exists — on re-login the data will be pushed to server.
    private fun performSoftLogout() {
        viewModelScope.launch {
            _logoutState.value = LogoutState.ClearingData
            try { api.logout() } catch (e: Exception) {
                Log.w(debugTag, "Server logout failed (continuing soft logout): ${e.message}")
            }
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(debugTag, "clearCredentialState failed (non-fatal): ${e.message}")
            }
            userRepository.setCurrentUser(null)
            withContext(Dispatchers.IO) {
                sessionManager.clearAuthOnly()
            }
            if (BuildConfig.DEBUG) Log.d(debugTag, "performSoftLogout: token cleared, local DB preserved for re-login sync")
            _logoutState.value = LogoutState.LoggedOut
        }
    }

    private fun performHardLogout() {
        viewModelScope.launch {
            _logoutState.value = LogoutState.ClearingData
            if (BuildConfig.DEBUG) Log.d(debugTag, "performHardLogout: starting server-side logout + clearing DB")
            // Revoke token server-side so it can't be replayed
            try {
                api.logout()
            } catch (e: Exception) {
                Log.w(debugTag, "Server logout failed (continuing local logout): ${e.message}")
            }
            // Clear Google Credential Manager state so the next Google sign-in
            // starts fresh instead of reusing a stale/revoked cached credential.
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(debugTag, "clearCredentialState failed (non-fatal): ${e.message}")
            }
            userRepository.setCurrentUser(null)
            withContext(Dispatchers.IO) {
                sessionManager.clearSession()
                appDatabase.clearAllTables()
            }
            if (BuildConfig.DEBUG) Log.d(debugTag, "performHardLogout: completed clearSession + cleared DB")
            _logoutState.value = LogoutState.LoggedOut
        }
    }

    private suspend fun getUnsyncedDataSummary(): UnsyncedDataSummary = coroutineScope {
        val profileCount = async { appDatabase.restaurantDao().getUnsyncedRestaurantProfiles().size }
        val userCount = async { appDatabase.userDao().getUnsyncedUsers().size }
        val categoryCount = async { appDatabase.categoryDao().getUnsyncedCategories().size }
        val menuItemCount = async { appDatabase.menuDao().getUnsyncedMenuItems().size }
        val variantCount = async { appDatabase.menuDao().getUnsyncedItemVariants().size }
        val stockLogCount = async { appDatabase.inventoryDao().getUnsyncedStockLogs().size }
        val billCount = async { appDatabase.billDao().getUnsyncedBills().size }
        val billItemCount = async { appDatabase.billDao().getUnsyncedBillItems().size }
        val billPaymentCount = async { appDatabase.billDao().getUnsyncedBillPayments().size }

        val pc = profileCount.await()
        val uc = userCount.await()
        val cc = categoryCount.await()
        val mic = menuItemCount.await()
        val vc = variantCount.await()
        val slc = stockLogCount.await()
        val bc = billCount.await()
        val bic = billItemCount.await()
        val bpc = billPaymentCount.await()

        val parts = buildList {
            if (pc > 0) add("$pc settings")
            if (uc > 0) add("$uc users")
            if (cc > 0) add("$cc categories")
            if (mic > 0) add("$mic menu items")
            if (vc > 0) add("$vc variants")
            if (slc > 0) add("$slc stock logs")
            if (bc > 0) add("$bc bills")
            if (bic > 0) add("$bic bill items")
            if (bpc > 0) add("$bpc bill payments")
        }

        UnsyncedDataSummary(
            totalCount = parts.sumOf {
                it.substringBefore(' ').toIntOrNull() ?: 0
            },
            summary = parts.joinToString(", ")
        )
    }
}
