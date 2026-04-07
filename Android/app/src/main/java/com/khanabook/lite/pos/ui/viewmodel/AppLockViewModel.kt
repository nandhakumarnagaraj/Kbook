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

sealed class PinSetupState {
    object Idle : PinSetupState()
    data class EnterNew(val pin: String = "", val error: String? = null) : PinSetupState()
    data class ConfirmNew(val firstPin: String, val pin: String = "", val error: String? = null) : PinSetupState()
    data class EnterCurrent(val pin: String = "", val error: String? = null, val nextStep: PinSetupState? = null) : PinSetupState()
    data class Success(val message: String) : PinSetupState()
}

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authManager: AuthManager
) : ViewModel() {

    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _pinSetupState = MutableStateFlow<PinSetupState>(PinSetupState.Idle)
    val pinSetupState: StateFlow<PinSetupState> = _pinSetupState

    fun startEnablePin() {
        _pinSetupState.value = PinSetupState.EnterNew()
    }

    fun startDisablePin() {
        _pinSetupState.value = PinSetupState.EnterCurrent()
    }

    fun startChangePin() {
        _pinSetupState.value = PinSetupState.EnterCurrent(nextStep = PinSetupState.EnterNew())
    }

    fun onSetupDigit(digit: String) {
        when (val state = _pinSetupState.value) {
            is PinSetupState.EnterNew -> {
                if (state.pin.length < 4) {
                    val updated = state.pin + digit
                    if (updated.length == 4) {
                        _pinSetupState.value = PinSetupState.ConfirmNew(firstPin = updated)
                    } else {
                        _pinSetupState.value = state.copy(pin = updated, error = null)
                    }
                }
            }
            is PinSetupState.ConfirmNew -> {
                if (state.pin.length < 4) {
                    val updated = state.pin + digit
                    if (updated.length == 4) {
                        if (updated == state.firstPin) {
                            viewModelScope.launch {
                                val hash = authManager.hashPassword(updated)
                                sessionManager.savePinHash(hash)
                                sessionManager.setPinLockEnabled(true)
                                _pinSetupState.value = PinSetupState.Success("App Lock enabled")
                            }
                        } else {
                            _pinSetupState.value = PinSetupState.EnterNew(error = "PINs don't match. Try again.")
                        }
                    } else {
                        _pinSetupState.value = state.copy(pin = updated)
                    }
                }
            }
            is PinSetupState.EnterCurrent -> {
                if (state.pin.length < 4) {
                    val updated = state.pin + digit
                    if (updated.length == 4) {
                        viewModelScope.launch {
                            val hash = sessionManager.getPinHash()
                            val valid = hash != null && authManager.verifyPassword(updated, hash)
                            if (valid) {
                                if (state.nextStep != null) {
                                    _pinSetupState.value = state.nextStep
                                } else {
                                    sessionManager.clearPin()
                                    _pinSetupState.value = PinSetupState.Success("App Lock disabled")
                                }
                            } else {
                                _pinSetupState.value = state.copy(pin = "", error = "Incorrect PIN. Try again.")
                            }
                        }
                    } else {
                        _pinSetupState.value = state.copy(pin = updated, error = null)
                    }
                }
            }
            else -> {}
        }
    }

    fun onSetupDelete() {
        when (val state = _pinSetupState.value) {
            is PinSetupState.EnterNew -> if (state.pin.isNotEmpty())
                _pinSetupState.value = state.copy(pin = state.pin.dropLast(1))
            is PinSetupState.ConfirmNew -> if (state.pin.isNotEmpty())
                _pinSetupState.value = state.copy(pin = state.pin.dropLast(1))
            is PinSetupState.EnterCurrent -> if (state.pin.isNotEmpty())
                _pinSetupState.value = state.copy(pin = state.pin.dropLast(1))
            else -> {}
        }
    }

    fun resetSetupState() {
        _pinSetupState.value = PinSetupState.Idle
    }

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
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
