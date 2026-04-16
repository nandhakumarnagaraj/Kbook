package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
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
    data class Error(val message: String) : InitialSyncState()
}

@HiltViewModel
class InitialSyncViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager
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
                val result = syncManager.performMasterPull()

                if (result.isSuccess) {
                    sessionManager.setInitialSyncCompleted(true)
                    _syncState.value = InitialSyncState.Success
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("InitialSyncViewModel", "Master pull failed", error)
                    if (error is HttpException && error.code() == 401) {
                        sessionManager.clearSession()
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
            } catch (e: Exception) {
                Log.e("InitialSyncViewModel", "Unexpected error during initial sync", e)
                if (e is HttpException && e.code() == 401) {
                    sessionManager.clearSession()
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
}
