package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.AuthManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.util.findActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    private val _resetPasswordStatus = MutableStateFlow<ResetPasswordResult?>(null)
    val resetPasswordStatus: StateFlow<ResetPasswordResult?> = _resetPasswordStatus

    private val _otpVerificationStatus = MutableStateFlow<OtpVerificationResult?>(null)
    val otpVerificationStatus: StateFlow<OtpVerificationResult?> = _otpVerificationStatus

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

    fun login(email: String, password: String) {
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
            performLogin(email, password)
        }
    }

    private suspend fun performLogin(email: String, password: String) {
        val result = userRepository.remoteLogin(email, password)

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
            
            if (e is retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 404) {
                    _loginStatus.value = loginError(
                        "Incorrect mobile number or password.",
                        LoginErrorCode.INCORRECT_PASSWORD
                    )
                    return@onFailure
                }
            } else if (e is java.io.IOException) {
                _loginStatus.value = loginError(
                    "Server is offline. Please check your connection.",
                    LoginErrorCode.ACCOUNT_NOT_FOUND
                )
                return@onFailure
            }

            val user = userRepository.getUserByLoginId(email)
            if (user != null) {
                // Since we don't store password_hash locally anymore, 
                // we cannot verify password if offline.
                _loginStatus.value = loginError(
                    "Server is offline. Please connect to internet to login.",
                    LoginErrorCode.ACCOUNT_NOT_FOUND // Or a specific OFFLINE error
                )
            } else {
                _loginStatus.value = loginError(
                    "No account found with this number or server is offline.",
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
                try {
                    userRepository.requestSignupOtp(phoneNumber)
                    _signUpStatus.value = SignUpResult.OtpSent
                } catch (e: Exception) {
                    _signUpStatus.value =
                        SignUpResult.Error(e.message ?: "Failed to send OTP. Please try again.")
                }
                return@launch
            }

            if (purpose == "reset") {
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
                    _resetPasswordStatus.value =
                        ResetPasswordResult.Error(e.message ?: "Failed to send OTP. Please try again.")
                }
                return@launch
            }

            if (purpose == "update_whatsapp") {
                val result = userRepository.requestMobileNumberUpdateOtp(phoneNumber)
                result.onSuccess {
                    _otpVerificationStatus.value = OtpVerificationResult.OtpSent
                }.onFailure { e ->
                    _otpVerificationStatus.value =
                        OtpVerificationResult.Error(e.message ?: "Failed to send OTP. Please try again.")
                }
                return@launch
            }
        }
    }

    fun confirmMobileNumberUpdate(phoneNumber: String, otp: String) {
        viewModelScope.launch {
            val result = userRepository.confirmMobileNumberUpdate(phoneNumber, otp)
            result.onSuccess {
                _otpVerificationStatus.value = OtpVerificationResult.Success
            }.onFailure { e ->
                _otpVerificationStatus.value =
                    OtpVerificationResult.Error(e.message ?: "Failed to verify OTP.")
            }
        }
    }

    fun signUp(name: String, phoneNumber: String, otp: String, password: String) {
        viewModelScope.launch {
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
                        _signUpStatus.value = SignUpResult.Error("Signup successful but Login failed: ${error.message}")
                    }
                }.onFailure { e ->
                    _signUpStatus.value = SignUpResult.Error(e.message ?: "Registration failed")
                }
            } catch (e: Exception) {
                _signUpStatus.value = SignUpResult.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun resetPassword(phoneNumber: String, otp: String, newPassword: String) {
        viewModelScope.launch {
            try {
                userRepository.remoteResetPassword(phoneNumber, otp, newPassword)
                
                _resetPasswordStatus.value = ResetPasswordResult.Success
            } catch (e: Exception) {
                _resetPasswordStatus.value =
                        ResetPasswordResult.Error(e.message ?: "Failed to reset password")
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

    
    fun loginWithGoogle(context: Context) {
        _loginStatus.value = LoginResult.Loading
        val activity = context.findActivity()
        if (activity == null) {
            _loginStatus.value =
                    loginError(
                            "Google Sign-In: activity context not found",
                            LoginErrorCode.GOOGLE_CONTEXT_MISSING
                    )
            return
        }

        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activity)

                val googleIdOption =
                        GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false) 
                                .setServerClientId(
                                        activity.getString(R.string.default_web_client_id)
                                )
                                .setAutoSelectEnabled(false)
                                .build()

                val request =
                        GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

                val result =
                        credentialManager.getCredential(
                                context = activity,
                                request = request
                        )

                val credential = result.credential
                if (credential is CustomCredential &&
                                credential.type ==
                                        GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleCred.idToken

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
                        Log.e(TAG, "Remote Google login failed", e)
                        _loginStatus.value = loginError(
                            "Google sync failed: ${e.message}",
                            LoginErrorCode.GOOGLE_SYNC_FAILED
                        )
                    }
                } else {
                    _loginStatus.value =
                            loginError(
                                    "Google Sign-In: unexpected credential type",
                                    LoginErrorCode.GOOGLE_UNEXPECTED_CREDENTIAL
                            )
                }
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Google Sign-In cancelled or unavailable", e)
                _loginStatus.value = loginError(
                        "Google Sign-In cancelled. Try again.",
                        LoginErrorCode.GOOGLE_CANCELLED
                )
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                _loginStatus.value = loginError(
                        "Google Sign-In failed. Please try again.",
                        LoginErrorCode.GOOGLE_FAILED
                )
            }
        }
    }



    fun resetSignUpStatus() {
        _signUpStatus.value = null
    }

    fun clearResetStatus() {
        _resetPasswordStatus.value = null
    }

    fun clearOtpStatus() {
        _otpVerificationStatus.value = null
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
