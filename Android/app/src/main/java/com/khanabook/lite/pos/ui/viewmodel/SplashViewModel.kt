package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    private val debugTag = "KhanaBookDebugAuth"

    sealed class SplashState {
        object Loading : SplashState()
        object NavigateToLogin : SplashState()
        object NavigateToMain : SplashState()
        object NavigateToInitialSync : SplashState()
        object NavigateToAppLock : SplashState()
    }

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            val token = sessionManager.getAuthToken()
            val isSyncCompleted = sessionManager.isInitialSyncCompleted()

            val isPinLocked = token != null && isSyncCompleted &&
                sessionManager.isPinLockEnabled()

            val chosen = when {
                token == null -> SplashState.NavigateToLogin
                !isSyncCompleted -> SplashState.NavigateToInitialSync
                isPinLocked -> {
                    sessionManager.clearBackgroundTime()
                    SplashState.NavigateToAppLock
                }
                else -> SplashState.NavigateToMain
            }

            if (BuildConfig.DEBUG) {
                Log.d(debugTag, "Splash → ${chosen::class.simpleName} tokenPresent=${token != null} syncDone=$isSyncCompleted")
            }
            _state.value = chosen
        }
    }
}
