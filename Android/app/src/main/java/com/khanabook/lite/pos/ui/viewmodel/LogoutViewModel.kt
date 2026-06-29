package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.DatabaseProvider
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.util.cancelMasterSyncWork
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
import androidx.work.WorkManager
import javax.inject.Inject

class PendingSyncException(val unsyncedCount: Int) : Exception("Cannot logout: $unsyncedCount unsynced items remaining")

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
    private val databaseProvider: DatabaseProvider,
    private val billRepository: BillRepository,
    private val syncManager: SyncManager,
    private val userRepository: UserRepository,
    private val api: KhanaBookApi,
    private val workManager: WorkManager
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
                    try {
                        syncManager.pushUnsyncedDataImmediately()
                    } catch (e: Exception) {
                        Log.w(debugTag, "Immediate sync before logout failed; preserving local DB: ${e.message}")
                    }
                    val finalSummary = getUnsyncedDataSummary()
                    if (finalSummary.totalCount > 0) {
                        _logoutState.value = LogoutState.WarningOfflineData(
                            totalCount = finalSummary.totalCount,
                            summary = finalSummary.summary
                        )
                        throw PendingSyncException(finalSummary.totalCount)
                    } else {
                        performSoftLogout()
                    }
                } else {
                    performSoftLogout()
                }
            } catch (e: PendingSyncException) {
                Log.w(debugTag, "Logout aborted due to pending sync data: ${e.message}")
            } catch (e: Exception) {
                Log.w(debugTag, "Logout pre-check failed; preserving local DB: ${e.message}")
                performSoftLogout()
            }
        }
    }

    fun forceLogoutDespiteWarning() {
        performSoftLogout()
    }

    fun clearDeviceDataAndLogout() {
        performHardLogout()
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
                workManager.cancelMasterSyncWork()
                sessionManager.clearAuthOnly()
            }
            if (BuildConfig.DEBUG) Log.d(debugTag, "performSoftLogout: token cleared, local DB preserved for re-login sync")
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
                workManager.cancelMasterSyncWork()
                databaseProvider.closeDatabase()
                context.databaseList().forEach { dbName ->
                    if (dbName.startsWith("khanabook_lite_db")) {
                        val deleted = context.deleteDatabase(dbName)
                        if (BuildConfig.DEBUG) {
                            Log.d(debugTag, "Deleted database $dbName: $deleted")
                        }
                    }
                }
                sessionManager.clearSession()
            }
            if (BuildConfig.DEBUG) Log.d(debugTag, "performHardLogout: completed clearSession + cleared DB")
            _logoutState.value = LogoutState.LoggedOut
        }
    }

    private suspend fun getUnsyncedDataSummary(): UnsyncedDataSummary = coroutineScope {
        val activeRestaurantId = sessionManager.getRestaurantId()

        val database = databaseProvider.getDatabase()
        val profileCount = async { database.restaurantDao().getUnsyncedRestaurantProfiles().filter { it.restaurantId == activeRestaurantId }.size }
        val userCount = async { database.userDao().getUnsyncedUsers().filter { it.restaurantId == activeRestaurantId }.size }
        val categoryCount = async { database.categoryDao().getUnsyncedCategories(activeRestaurantId).size }
        val menuItemCount = async { database.menuDao().getUnsyncedMenuItems(activeRestaurantId).size }
        val variantCount = async { database.menuDao().getUnsyncedItemVariants(activeRestaurantId).size }
        val stockLogCount = async { database.inventoryDao().getUnsyncedStockLogs(activeRestaurantId).size }
        val billCount = async { database.billDao().getUnsyncedBills(activeRestaurantId).size }
        val billItemCount = async { database.billDao().getUnsyncedBillItems(activeRestaurantId).size }
        val billPaymentCount = async { database.billDao().getUnsyncedBillPayments(activeRestaurantId).size }

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
