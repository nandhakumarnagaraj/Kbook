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
    data class WarningOfflineData(val count: Int) : LogoutState()
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

    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val logoutState: StateFlow<LogoutState> = _logoutState.asStateFlow()

    fun initiateLogout() {
        viewModelScope.launch {
            _logoutState.value = LogoutState.AttemptingPush

            try {
                val initialCount = appDatabase.billDao().getUnsyncedCountOnce()
                if (initialCount > 0) {
                    syncManager.pushUnsyncedDataImmediately()
                    val remainingCount = appDatabase.billDao().getUnsyncedCountOnce()
                    if (remainingCount == 0) {
                        performHardLogout()
                    } else {
                        _logoutState.value = LogoutState.WarningOfflineData(remainingCount)
                    }
                } else {
                    performHardLogout()
                }
            } catch (e: Exception) {
                val remainingCount = appDatabase.billDao().getUnsyncedCountOnce()
                if (remainingCount > 0) {
                    _logoutState.value = LogoutState.WarningOfflineData(remainingCount)
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
}
