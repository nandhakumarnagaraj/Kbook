package com.khanabook.lite.pos.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.data.repository.NotificationRepository
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        private val authManager: AuthManager,
        private val notificationRepository: NotificationRepository
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

    private val _changePasswordStatus = MutableStateFlow<ChangePasswordResult?>(null)
    val changePasswordStatus: StateFlow<ChangePasswordResult?> = _changePasswordStatus
    private val _changePasswordFieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val changePasswordFieldErrors: StateFlow<Map<String, String>> = _changePasswordFieldErrors

    private val _otpVerificationStatus = MutableStateFlow<OtpVerificationResult?>(null)
    val otpVerificationStatus: StateFlow<OtpVerificationResult?> = _otpVerificationStatus
    private val _otpFieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val otpFieldErrors: StateFlow<Map<String, String>> = _otpFieldErrors

    private val _isUserChecking = MutableStateFlow(false)
    val isUserChecking: StateFlow<Boolean> = _isUserChecking

    private val _userExistsError = MutableStateFlow<String?>(null)
    val userExistsError: StateFlow<String?> = _userExistsError

    // ── Signup: error when account ALREADY exists ─────────────────────────
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

    // ── Login: error when account does NOT exist ──────────────────────────
    private val _isLoginUserChecking = MutableStateFlow(false)
    val isLoginUserChecking: StateFlow<Boolean> = _isLoginUserChecking

    private val _loginUserCheckError = MutableStateFlow<String?>(null)
    val loginUserCheckError: StateFlow<String?> = _loginUserCheckError

    /** Returns true if the account exists (login can proceed), false if not. */
    fun checkUserExistsForLogin(phoneNumber: String) {
        if (!phoneNumber.matches(Regex("^\\d{10}$"))) return
        viewModelScope.launch {
            _isLoginUserChecking.value = true
            _loginUserCheckError.value = null
            try {
                val exists = userRepository.checkUserExistsRemotely(phoneNumber)
                if (!exists) {
                    _loginUserCheckError.value = "No account found with this number."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login user check failed", e)
                // Silently fail — don't block login if check fails
            } finally {
                _isLoginUserChecking.value = false
            }
        }
    }

    fun clearLoginUserCheck() {
        _loginUserCheckError.value = null
        _isLoginUserChecking.value = false
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
        viewModelScope.launch { registerDeviceTokenAfterLogin() }
        return Result.success(Unit)
    }

    private suspend fun registerDeviceTokenAfterLogin() {
        try {
            val token = suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            cont.resume(task.result)
                        } else {
                            cont.resume(null)
                        }
                    }
            }
            if (token != null) {
                notificationRepository.registerDeviceToken(token)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token after login: ${e.message}")
        }
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

    fun signUp(shopName: String, ownerName: String, phoneNumber: String, otp: String, password: String) {
        viewModelScope.launch {
            _signUpFieldErrors.value = emptyMap()
            _signUpStatus.value = SignUpResult.Loading
            try {

                val result = userRepository.remoteSignup(ownerName, phoneNumber, otp, password)
                result.onSuccess {
                    
                    val currentProfile = restaurantRepository.getProfile()
                    val updatedProfile =
                            if (currentProfile != null) {
                                currentProfile.copy(
                                        shopName = shopName,
                                        whatsappNumber = phoneNumber,
                                        upiMobile = phoneNumber
                                )
                            } else {
                                RestaurantProfileEntity(
                                        id = 1,
                                        shopName = shopName,
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
        _changePasswordStatus.value = null
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _changePasswordStatus.value = ChangePasswordResult.Loading
            _changePasswordFieldErrors.value = emptyMap()
            try {
                userRepository.remoteChangePassword(currentPassword, newPassword)
                _changePasswordStatus.value = ChangePasswordResult.Success
            } catch (e: java.lang.Exception) {
                val sanitized = UserMessageSanitizer.sanitizeWithDetails(e, "Failed to change password")
                _changePasswordFieldErrors.value = sanitized.fieldErrors
                _changePasswordStatus.value = ChangePasswordResult.Error(sanitized.message)
            }
        }
    }

    fun clearChangePasswordStatus() {
        _changePasswordStatus.value = null
        _changePasswordFieldErrors.value = emptyMap()
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
                val statusCode = when (e) {
                    is retrofit2.HttpException -> e.code()
                    is BackendException -> e.details.statusCode
                    else -> null
                }
                val message = if (statusCode == 429) {
                    "Too many attempts. Please wait a moment and try again."
                } else {
                    UserMessageSanitizer.sanitize(e, "Google sign-in failed. Please try again.")
                }
                _loginStatus.value = loginError(message, LoginErrorCode.GOOGLE_SYNC_FAILED)
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

    sealed class ChangePasswordResult {
        object Loading : ChangePasswordResult()
        object Success : ChangePasswordResult()
        data class Error(val message: String) : ChangePasswordResult()
    }

    sealed class OtpVerificationResult {
        object Success : OtpVerificationResult()
        object OtpSent : OtpVerificationResult()
        data class Error(val message: String) : OtpVerificationResult()
    }
}
