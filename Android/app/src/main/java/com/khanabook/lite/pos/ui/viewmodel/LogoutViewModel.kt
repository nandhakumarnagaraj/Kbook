package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.AppDatabase
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LogoutState {
    object Idle : LogoutState()
    object AttemptingPush : LogoutState()
    data class WarningOfflineData(
        val totalCount: Int,
        val summary: String
    ) : LogoutState()
    object LoggedOut : LogoutState()
}

@HiltViewModel
class LogoutViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val appDatabase: AppDatabase,
    private val billRepository: BillRepository,
    private val syncManager: SyncManager,
    private val userRepository: UserRepository
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
        performHardLogout()
    }

    fun cancelLogout() {
        _logoutState.value = LogoutState.Idle
    }

    private fun performHardLogout() {
        viewModelScope.launch {
            Log.d(debugTag, "performHardLogout: starting clearSession + clearing DB")
            sessionManager.clearSession()
            
            appDatabase.clearAllTables()
            userRepository.setCurrentUser(null)
            Log.d(debugTag, "performHardLogout: completed clearSession + cleared DB")
            _logoutState.value = LogoutState.LoggedOut
        }
    }

    private suspend fun getUnsyncedDataSummary(): UnsyncedDataSummary {
        val profileCount = appDatabase.restaurantDao().getUnsyncedRestaurantProfiles().size
        val userCount = appDatabase.userDao().getUnsyncedUsers().size
        val categoryCount = appDatabase.categoryDao().getUnsyncedCategories().size
        val menuItemCount = appDatabase.menuDao().getUnsyncedMenuItems().size
        val variantCount = appDatabase.menuDao().getUnsyncedItemVariants().size
        val stockLogCount = appDatabase.inventoryDao().getUnsyncedStockLogs().size
        val billCount = appDatabase.billDao().getUnsyncedBills().size
        val billItemCount = appDatabase.billDao().getUnsyncedBillItems().size
        val billPaymentCount = appDatabase.billDao().getUnsyncedBillPayments().size

        val parts = buildList {
            if (profileCount > 0) add("$profileCount settings")
            if (userCount > 0) add("$userCount users")
            if (categoryCount > 0) add("$categoryCount categories")
            if (menuItemCount > 0) add("$menuItemCount menu items")
            if (variantCount > 0) add("$variantCount variants")
            if (stockLogCount > 0) add("$stockLogCount stock logs")
            if (billCount > 0) add("$billCount bills")
            if (billItemCount > 0) add("$billItemCount bill items")
            if (billPaymentCount > 0) add("$billPaymentCount bill payments")
        }

        return UnsyncedDataSummary(
            totalCount = parts.sumOf {
                it.substringBefore(' ').toIntOrNull() ?: 0
            },
            summary = parts.joinToString(", ")
        )
    }
}
