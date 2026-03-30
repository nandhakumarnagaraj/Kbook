package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    }

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            
            kotlinx.coroutines.delay(1000)
            
            val token = sessionManager.getAuthToken()
            val isSyncCompleted = sessionManager.isInitialSyncCompleted()
            val activeUserId = sessionManager.getActiveUserId()

            val chosen = when {
                token == null -> SplashState.NavigateToLogin
                !isSyncCompleted -> SplashState.NavigateToInitialSync
                else -> SplashState.NavigateToMain
            }

            Log.d(
                debugTag,
                "Splash navigation chosen=${chosen::class.simpleName} tokenPresent=${token != null} isSyncCompleted=$isSyncCompleted activeUserId=$activeUserId"
            )
            _state.value = chosen
        }
    }
}
