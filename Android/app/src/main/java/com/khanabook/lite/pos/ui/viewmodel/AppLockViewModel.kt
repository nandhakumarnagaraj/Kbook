package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.domain.manager.AuthManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun appendDigit(digit: String) {
        if (_enteredPin.value.length < 4) {
            _enteredPin.value += digit
            _errorMessage.value = null
        }
    }

    fun deleteDigit() {
        val current = _enteredPin.value
        if (current.isNotEmpty()) _enteredPin.value = current.dropLast(1)
    }

    fun clearPin() {
        _enteredPin.value = ""
        _errorMessage.value = null
    }

    fun verifyPin(onSuccess: () -> Unit) {
        val pin = _enteredPin.value
        if (pin.length != 4) return
        viewModelScope.launch {
            val hash = sessionManager.getPinHash() ?: run {
                onSuccess(); return@launch
            }
            val valid = authManager.verifyPassword(pin, hash)
            if (valid) {
                _errorMessage.value = null
                onSuccess()
            } else {
                _enteredPin.value = ""
                _errorMessage.value = "Incorrect PIN. Try again."
            }
        }
    }

    fun setupPin(pin: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val hash = authManager.hashPassword(pin)
            sessionManager.savePinHash(hash)
            sessionManager.setPinLockEnabled(true)
            onComplete()
        }
    }

    fun disablePin(currentPin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val hash = sessionManager.getPinHash() ?: run { onError(); return@launch }
            val valid = authManager.verifyPassword(currentPin, hash)
            if (valid) { sessionManager.clearPin(); onSuccess() } else onError()
        }
    }

    fun changePin(currentPin: String, newPin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val hash = sessionManager.getPinHash() ?: run { onError(); return@launch }
            val valid = authManager.verifyPassword(currentPin, hash)
            if (valid) {
                val newHash = authManager.hashPassword(newPin)
                sessionManager.savePinHash(newHash)
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun isPinEnabled(): Boolean =
        sessionManager.isPinLockEnabled() && sessionManager.getPinHash() != null

    fun hasBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}
