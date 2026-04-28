package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.TrustedExternalAppReturn
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
            val isTrustedExternalReturn = TrustedExternalAppReturn.consume(context)

            val chosen = when {
                token == null -> SplashState.NavigateToLogin
                !isSyncCompleted -> SplashState.NavigateToInitialSync
                sessionManager.isPinLockEnabled() && !isTrustedExternalReturn -> {
                    sessionManager.clearBackgroundTime()
                    SplashState.NavigateToAppLock
                }
                else -> {
                    sessionManager.clearBackgroundTime()
                    SplashState.NavigateToMain
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(debugTag, "Splash → ${chosen::class.simpleName} tokenPresent=${token != null} syncDone=$isSyncCompleted")
            }
            _state.value = chosen
        }
    }
}
