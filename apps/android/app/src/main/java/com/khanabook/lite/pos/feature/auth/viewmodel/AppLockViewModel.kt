package com.khanabook.lite.pos.feature.auth.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.auth.domain.AuthManager
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
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

    /**
     * Verifies the 4-digit app-lock PIN, with rate limiting.
     *
     * The PIN is the only barrier to a logged-in POS session, and BCrypt cost 12 makes each guess
     * cheap enough that the whole 10,000-value space is reachable in ~40 minutes of unattended
     * tapping. After [MAX_PIN_ATTEMPTS] failures the screen is locked for a delay that doubles per
     * further block of failures (1, 2, 4, 8, then 16 minutes), persisted in prefs so a process
     * restart does not reset it (BUG-046). The counter is cleared by a correct PIN, by an elapsed
     * lockout window, and when the PIN is set or changed, so it never outlives the failures it
     * counts.
     */
    fun verifyPin(onSuccess: () -> Unit) {
        val pin = _enteredPin.value
        if (pin.length != 4) return

        val now = System.currentTimeMillis()
        val lockoutUntil = sessionManager.getPinLockoutUntilMs()
        if (now < lockoutUntil) {
            val seconds = ((lockoutUntil - now + 999) / 1000).coerceAtLeast(1)
            _enteredPin.value = ""
            _errorMessage.value = "Too many attempts. Try again in ${seconds}s."
            return
        }
        // A finished lockout starts a fresh count. Without this the stored count only ever grew,
        // so one typo weeks later re-applied a full lockout instead of a plain "incorrect PIN".
        if (lockoutUntil != 0L) {
            sessionManager.clearPinLockout()
        }

        viewModelScope.launch {
            val hash = sessionManager.getPinHash() ?: run {
                _errorMessage.value = "Security key lost. Please log in again."
                return@launch
            }
            val valid = authManager.verifyPassword(pin, hash)
            if (valid) {
                sessionManager.clearPinLockout()
                _errorMessage.value = null
                onSuccess()
            } else {
                _enteredPin.value = ""
                val failures = sessionManager.getPinFailedAttempts() + 1
                sessionManager.setPinFailedAttempts(failures)
                if (failures >= MAX_PIN_ATTEMPTS) {
                    val delayMs = pinLockoutDelayMs(failures)
                    sessionManager.setPinLockoutUntilMs(System.currentTimeMillis() + delayMs)
                    _errorMessage.value = "Too many attempts. Try again in ${delayMs / 1000}s."
                } else {
                    val left = MAX_PIN_ATTEMPTS - failures
                    _errorMessage.value = "Incorrect PIN. $left attempt${if (left == 1) "" else "s"} left."
                }
            }
        }
    }

    /** Exponential back-off after each block of [MAX_PIN_ATTEMPTS] failures, capped at 16 minutes. */
    private fun pinLockoutDelayMs(failures: Int): Long {
        val blocks = (failures / MAX_PIN_ATTEMPTS).coerceIn(1, 5)
        return (1L shl (blocks - 1)) * 60_000L
    }

    fun setupPin(pin: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val hash = authManager.hashPassword(pin)
            sessionManager.savePinHash(hash)
            sessionManager.setPinLockEnabled(true)
            // A new PIN must not inherit the failures of the old one.
            sessionManager.clearPinLockout()
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
                sessionManager.clearPinLockout()
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun isPinEnabled(): Boolean =
        sessionManager.isPinLockEnabled()

    private companion object {
        const val MAX_PIN_ATTEMPTS = 5
    }

    fun allowedAuthenticators(context: Context): Int {
        val manager = BiometricManager.from(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val strongOrCredential =
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (manager.canAuthenticate(strongOrCredential) == BiometricManager.BIOMETRIC_SUCCESS) {
                return strongOrCredential
            }

            val weakOrCredential =
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (manager.canAuthenticate(weakOrCredential) == BiometricManager.BIOMETRIC_SUCCESS) {
                return weakOrCredential
            }

            val credentialOnly = BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (manager.canAuthenticate(credentialOnly) == BiometricManager.BIOMETRIC_SUCCESS) {
                return credentialOnly
            }
        }

        return BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
    }

    fun hasBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val authenticators = allowedAuthenticators(context)
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
