package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.AuthManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.util.BackendException
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "AuthViewModel"


private const val MAX_FAILED_ATTEMPTS = 5

@HiltViewModel
class AuthViewModel
@Inject
constructor(
        private val userRepository: UserRepository,
        private val restaurantRepository: RestaurantRepository,
        private val syncManager: SyncManager,
        private val sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager,
        private val authManager: AuthManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            userRepository.loadPersistedUser()
        }
    }

    val currentUser: StateFlow<UserEntity?> = userRepository.currentUser

    private val _loginStatus = MutableStateFlow<LoginResult?>(null)
    val loginStatus: StateFlow<LoginResult?> = _loginStatus

    private fun loginError(message: String, code: LoginErrorCode) =
        LoginResult.Error(message, code)

    private val _signUpStatus = MutableStateFlow<SignUpResult?>(null)
    val signUpStatus: StateFlow<SignUpResult?> = _signUpStatus
    private val _signUpFieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val signUpFieldErrors: StateFlow<Map<String, String>> = _signUpFieldErrors

    private val _resetPasswordStatus = MutableStateFlow<ResetPasswordResult?>(null)
    val resetPasswordStatus: StateFlow<ResetPasswordResult?> = _resetPasswordStatus
    private val _resetPasswordFieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val resetPasswordFieldErrors: StateFlow<Map<String, String>> = _resetPasswordFieldErrors

    private val _otpVerificationStatus = MutableStateFlow<OtpVerificationResult?>(null)
    val otpVerificationStatus: StateFlow<OtpVerificationResult?> = _otpVerificationStatus
    private val _otpFieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val otpFieldErrors: StateFlow<Map<String, String>> = _otpFieldErrors

    private val _isUserChecking = MutableStateFlow(false)
    val isUserChecking: StateFlow<Boolean> = _isUserChecking

    private val _userExistsError = MutableStateFlow<String?>(null)
    val userExistsError: StateFlow<String?> = _userExistsError

    fun checkUserExists(phoneNumber: String) {
        if (!phoneNumber.matches(Regex("^\\d{10}$"))) return
        
        viewModelScope.launch {
            _isUserChecking.value = true
            _userExistsError.value = null
            try {
                val exists = userRepository.checkUserExistsRemotely(phoneNumber)
                if (exists) {
                    _userExistsError.value = "An account with this number already exists."
                }
            } catch (e: Exception) {
                // Silently fail or log, don't block user if check fails
                Log.e(TAG, "User check failed", e)
            } finally {
                _isUserChecking.value = false
            }
        }
    }

    fun clearUserCheck() {
        _userExistsError.value = null
        _isUserChecking.value = false
    }

    fun login(loginId: String, password: String) {
        _signUpFieldErrors.value = emptyMap()
        _resetPasswordFieldErrors.value = emptyMap()
        _otpFieldErrors.value = emptyMap()
        val now = System.currentTimeMillis()
        val lockoutUntilMs = sessionManager.getLockoutUntilMs()
        if (now < lockoutUntilMs) {
            val remainingSeconds = (lockoutUntilMs - now) / 1000
            _loginStatus.value =
                    loginError(
                            "Too many failed attempts. Try again in $remainingSeconds seconds.",
                            LoginErrorCode.LOCKED_OUT
                    )
            return
        }

        viewModelScope.launch {
            _loginStatus.value = LoginResult.Loading 
            performLogin(loginId, password)
        }
    }

    private suspend fun performLogin(loginId: String, password: String) {
        val result = userRepository.remoteLogin(loginId, password)

        result.onSuccess { user ->
            val setupResult = handleLoginSuccess(user)
            if (setupResult.isSuccess) {
                _loginStatus.value = LoginResult.Success(user)
            } else {
                _loginStatus.value = loginError(
                    "Login successful but failed to restore your data. Please check your internet and try again.",
                    LoginErrorCode.GOOGLE_SYNC_FAILED
                )
            }
        }.onFailure { e ->
            Log.e(TAG, "Remote login failed: ${e.message}.", e)

            val statusCode = when (e) {
                is retrofit2.HttpException -> e.code()
                is BackendException -> e.details.statusCode
                else -> null
            }
            val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Login failed. Please try again.")

            if (statusCode == 401 || statusCode == 404) {
                _loginStatus.value = loginError(
                    "Incorrect login ID or password.",
                    LoginErrorCode.INCORRECT_PASSWORD
                )
                return@onFailure
            } else if (e is java.io.IOException) {
                _loginStatus.value = loginError(
                    "Server is offline. Please check your connection.",
                    LoginErrorCode.ACCOUNT_NOT_FOUND
                )
                return@onFailure
            }

            val user = userRepository.getUserByLoginId(loginId)
            if (user != null) {
                // Since we don't store password_hash locally anymore, 
                // we cannot verify password if offline.
                _loginStatus.value = loginError(
                    "Server is offline. Please connect to internet to login.",
                    LoginErrorCode.ACCOUNT_NOT_FOUND // Or a specific OFFLINE error
                )
            } else {
                _loginStatus.value = loginError(
                    sanitized.message.ifBlank { "No account found with this login ID or server is offline." },
                    LoginErrorCode.ACCOUNT_NOT_FOUND
                )
            }
        }
    }

    private suspend fun handleLoginSuccess(user: UserEntity): Result<Unit> {
        sessionManager.clearLockout()

        // Keep the existing sync cursor on routine logins. Resetting it on every login
        // forces a full pull and can overwrite newer local state with stale server data.
        return Result.success(Unit)
    }

    fun sendOtp(phoneNumber: String, purpose: String = "signup") {
        viewModelScope.launch {
            if (purpose == "signup") {
                _signUpFieldErrors.value = emptyMap()
                try {
                    userRepository.requestSignupOtp(phoneNumber)
                    _signUpStatus.value = SignUpResult.OtpSent
                } catch (e: Exception) {
                    val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Failed to send OTP. Please try again.")
                    _signUpFieldErrors.value = sanitized.fieldErrors
                    _signUpStatus.value =
                        SignUpResult.Error(
                            sanitized.message
                        )
                }
                return@launch
            }

            if (purpose == "reset") {
                _resetPasswordFieldErrors.value = emptyMap()
                _resetPasswordStatus.value = ResetPasswordResult.Loading
                val userExists = try {
                    userRepository.checkUserExistsRemotely(phoneNumber)
                } catch (e: Exception) {
                    _resetPasswordStatus.value =
                        ResetPasswordResult.Error("Unable to verify account. Check your connection.")
                    return@launch
                }

                if (!userExists) {
                    _resetPasswordStatus.value = ResetPasswordResult.Error("No account found with this number.")
                    return@launch
                }

                try {
                    userRepository.requestPasswordResetOtp(phoneNumber)
                    _resetPasswordStatus.value = ResetPasswordResult.OtpSent
                } catch (e: Exception) {
                    val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Failed to send OTP. Please try again.")
                    _resetPasswordFieldErrors.value = sanitized.fieldErrors
                    _resetPasswordStatus.value =
                        ResetPasswordResult.Error(
                            sanitized.message
                        )
                }
                return@launch
            }

            if (purpose == "update_whatsapp") {
                _otpFieldErrors.value = emptyMap()
                val result = userRepository.requestMobileNumberUpdateOtp(phoneNumber)
                result.onSuccess {
                    _otpVerificationStatus.value = OtpVerificationResult.OtpSent
                }.onFailure { e ->
                    val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Failed to send OTP. Please try again.")
                    _otpFieldErrors.value = sanitized.fieldErrors
                    _otpVerificationStatus.value =
                        OtpVerificationResult.Error(
                            sanitized.message
                        )
                }
                return@launch
            }
        }
    }

    fun confirmMobileNumberUpdate(phoneNumber: String, otp: String) {
        viewModelScope.launch {
            _otpFieldErrors.value = emptyMap()
            val result = userRepository.confirmMobileNumberUpdate(phoneNumber, otp)
            result.onSuccess {
                _otpVerificationStatus.value = OtpVerificationResult.Success
            }.onFailure { e ->
                val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Failed to verify OTP.")
                _otpFieldErrors.value = sanitized.fieldErrors
                _otpVerificationStatus.value =
                    OtpVerificationResult.Error(
                        sanitized.message
                    )
            }
        }
    }

    fun signUp(name: String, phoneNumber: String, otp: String, password: String) {
        viewModelScope.launch {
            _signUpFieldErrors.value = emptyMap()
            _signUpStatus.value = SignUpResult.Loading
            try {

                val result = userRepository.remoteSignup(name, phoneNumber, otp, password)
                result.onSuccess {
                    
                    val currentProfile = restaurantRepository.getProfile()
                    val updatedProfile =
                            if (currentProfile != null) {
                                currentProfile.copy(
                                        shopName = name,
                                        whatsappNumber = phoneNumber,
                                        upiMobile = phoneNumber
                                )
                            } else {
                                RestaurantProfileEntity(
                                        id = 1,
                                        shopName = name,
                                        shopAddress = "",
                                        whatsappNumber = phoneNumber,
                                        upiMobile = phoneNumber,
                                        lastResetDate =
                                                java.text.SimpleDateFormat(
                                                                "yyyy-MM-dd",
                                                                java.util.Locale.getDefault()
                                                        )
                                                        .format(java.util.Date())
                                )
                            }
                    restaurantRepository.saveProfile(updatedProfile)
                    
                    
                    performLogin(phoneNumber, password)
                    
                    
                    if (_loginStatus.value is LoginResult.Success) {
                        _signUpStatus.value = SignUpResult.Success
                    } else if (_loginStatus.value is LoginResult.Error) {
                        val error = _loginStatus.value as LoginResult.Error
                        _signUpStatus.value = SignUpResult.Error(
                            "Signup successful but login failed. Please sign in and try again."
                        )
                    }
                }.onFailure { e ->
                    val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Registration failed")
                    _signUpFieldErrors.value = sanitized.fieldErrors
                    _signUpStatus.value = SignUpResult.Error(
                        sanitized.message
                    )
                }
            } catch (e: Exception) {
                val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Registration failed")
                _signUpFieldErrors.value = sanitized.fieldErrors
                _signUpStatus.value = SignUpResult.Error(
                    sanitized.message
                )
            }
        }
    }

    fun resetPassword(phoneNumber: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            _resetPasswordFieldErrors.value = emptyMap()
            try {
                userRepository.remoteResetPassword(phoneNumber, otp, newPassword)
                
                _resetPasswordStatus.value = ResetPasswordResult.Success
            } catch (e: Exception) {
                val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Failed to reset password")
                _resetPasswordFieldErrors.value = sanitized.fieldErrors
                _resetPasswordStatus.value =
                        ResetPasswordResult.Error(
                            sanitized.message
                        )
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        userRepository.setCurrentUser(null)
        sessionManager.clearLockout()
        _loginStatus.value = null
        _signUpStatus.value = null
        _resetPasswordStatus.value = null
    }


    fun loginWithGoogleToken(idToken: String) {
        _loginStatus.value = LoginResult.Loading
        viewModelScope.launch {
            val result = userRepository.remoteGoogleLogin(idToken)
            result.onSuccess { user ->
                val setupResult = handleLoginSuccess(user)
                if (setupResult.isSuccess) {
                    _loginStatus.value = LoginResult.Success(user)
                } else {
                    _loginStatus.value = loginError(
                        "Login successful but failed to restore your data. Please check your internet and try again.",
                        LoginErrorCode.GOOGLE_SYNC_FAILED
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "Remote Google login failed: ${e.message}", e)
                _loginStatus.value = loginError(
                    UserMessageSanitizer.sanitize(e, "Google sign-in failed. Please try again."),
                    LoginErrorCode.GOOGLE_SYNC_FAILED
                )
            }
        }
    }

    fun setGoogleLoginError(message: String, code: LoginErrorCode = LoginErrorCode.GOOGLE_FAILED) {
        _loginStatus.value = loginError(message, code)
    }

    fun resetSignUpStatus() {
        _signUpStatus.value = null
        _signUpFieldErrors.value = emptyMap()
    }

    fun clearResetStatus() {
        _resetPasswordStatus.value = null
        _resetPasswordFieldErrors.value = emptyMap()
    }

    fun clearOtpStatus() {
        _otpVerificationStatus.value = null
        _otpFieldErrors.value = emptyMap()
    }

    sealed class LoginResult {        object Loading : LoginResult()
        data class Success(val user: UserEntity) : LoginResult()
        data class Error(val message: String, val code: LoginErrorCode) : LoginResult()
    }

    enum class LoginErrorCode {
        LOCKED_OUT,
        INCORRECT_PASSWORD,
        ACCOUNT_INACTIVE,
        ACCOUNT_NOT_FOUND,
        GOOGLE_CONTEXT_MISSING,
        GOOGLE_SYNC_FAILED,
        GOOGLE_UNEXPECTED_CREDENTIAL,
        GOOGLE_CANCELLED,
        GOOGLE_FAILED
    }

    sealed class SignUpResult {
        object Loading : SignUpResult()
        object Success : SignUpResult()
        
        object OtpSent : SignUpResult()
        data class Error(val message: String) : SignUpResult()
    }

    sealed class ResetPasswordResult {
        object Loading : ResetPasswordResult()
        object Success : ResetPasswordResult()
        
        object OtpSent : ResetPasswordResult()
        data class Error(val message: String) : ResetPasswordResult()
    }

    sealed class OtpVerificationResult {
        object Success : OtpVerificationResult()
        object OtpSent : OtpVerificationResult()
        data class Error(val message: String) : OtpVerificationResult()
    }
}
