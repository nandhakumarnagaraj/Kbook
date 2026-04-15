package com.khanabook.lite.pos.data.repository

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.data.local.dao.UserDao
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.worker.MasterSyncWorker
import com.khanabook.lite.pos.data.remote.ResetPasswordRequest
import com.khanabook.lite.pos.data.remote.PasswordResetOtpRequest
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.dto.UpdateMobileOtpRequest
import com.khanabook.lite.pos.data.remote.dto.UpdateMobileRequest
import com.khanabook.lite.pos.data.remote.api.LoginRequest
import com.khanabook.lite.pos.data.remote.api.GoogleLoginRequest
import com.khanabook.lite.pos.data.remote.api.SignupRequest
import com.khanabook.lite.pos.data.remote.api.SignupOtpRequest
import com.khanabook.lite.pos.domain.util.BackendErrorParser
import com.khanabook.lite.pos.domain.util.BackendException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException

class UserRepository(
        private val userDao: UserDao,
        private val sessionManager: SessionManager,
        private val workManager: WorkManager,
        private val api: KhanaBookApi
) {

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser

    private fun UserEntity.persistedLoginIdentity(): String = loginId?.takeIf { it.isNotBlank() } ?: email

    private suspend fun findLocalUser(loginId: String, userEmail: String?): UserEntity? {
        return userDao.getUserByLoginId(loginId)
            ?: userEmail?.takeIf { it.isNotBlank() }?.let { userDao.getUserByEmail(it) }
            ?: userDao.getUserByEmail(loginId)
    }

    suspend fun remoteLogin(loginIdInput: String, passwordPlain: String): Result<UserEntity> {
        return try {
            val deviceId = sessionManager.getDeviceId()
            val request = LoginRequest(loginIdInput, passwordPlain, deviceId)
            
            val response = api.login(request)
            val loginId = response.loginId?.takeIf { it.isNotBlank() } ?: loginIdInput
            val userEmail = response.userEmail?.takeIf { it.isNotBlank() } ?: loginId

            if (response.role != null && response.role != "OWNER") {
                return Result.failure(Exception("Access denied: This app is only for Restaurant Owners."))
            }

            sessionManager.saveAuthToken(response.token)
            sessionManager.saveRestaurantId(response.restaurantId)
            sessionManager.saveActiveUserRole(response.role ?: "OWNER")

            var localUser = findLocalUser(loginId, userEmail)
            if (localUser == null) {
                localUser = UserEntity(
                    name = response.userName,
                    email = userEmail,
                    loginId = loginId,
                    whatsappNumber = response.whatsappNumber ?: loginIdInput,
                    restaurantId = response.restaurantId,
                    role = response.role ?: "OWNER",
                    deviceId = deviceId,
                    isActive = true,
                    isSynced = true,
                    createdAt = System.currentTimeMillis()
                )
                val id = userDao.insertUser(localUser)
                localUser = localUser.copy(id = id)
            } else {
                localUser = localUser.copy(
                    name = response.userName,
                    email = userEmail,
                    loginId = loginId,
                    whatsappNumber = response.whatsappNumber ?: localUser.whatsappNumber,
                    restaurantId = response.restaurantId,
                    role = response.role ?: "OWNER",
                    isSynced = true
                )
                val id = userDao.insertUser(localUser)
                localUser = localUser.copy(id = id)
            }

            setCurrentUser(localUser)
            Result.success(localUser)
        } catch (e: Exception) {
            Result.failure(mapBackendException(e))
        }
    }

    suspend fun remoteSignup(name: String, phoneNumber: String, otp: String, passwordPlain: String): Result<UserEntity> {
        return try {
            val deviceId = sessionManager.getDeviceId()
            val request = SignupRequest(phoneNumber, name, passwordPlain, otp, deviceId)
            
            val response = api.signup(request)
            val loginId = response.loginId?.takeIf { it.isNotBlank() } ?: phoneNumber
            val userEmail = response.userEmail?.takeIf { it.isNotBlank() } ?: loginId

            if (response.role != null && response.role != "OWNER") {
                return Result.failure(Exception("Access denied: This app is only for Restaurant Owners."))
            }

            sessionManager.saveAuthToken(response.token)
            sessionManager.saveRestaurantId(response.restaurantId)
            sessionManager.saveActiveUserRole(response.role ?: "OWNER")

            var localUser = findLocalUser(loginId, userEmail)
            if (localUser == null) {
                localUser = UserEntity(
                    name = name,
                    email = userEmail,
                    loginId = loginId,
                    whatsappNumber = response.whatsappNumber ?: phoneNumber,
                    restaurantId = response.restaurantId,
                    role = response.role ?: "OWNER",
                    deviceId = deviceId,
                    isActive = true,
                    isSynced = true,
                    createdAt = System.currentTimeMillis()
                )
                val id = userDao.insertUser(localUser)
                localUser = localUser.copy(id = id)
            } else {
                localUser = localUser.copy(
                    name = response.userName,
                    email = userEmail,
                    loginId = loginId,
                    whatsappNumber = response.whatsappNumber ?: localUser.whatsappNumber,
                    restaurantId = response.restaurantId,
                    role = response.role ?: "OWNER",
                    isSynced = true
                )
                val id = userDao.insertUser(localUser)
                localUser = localUser.copy(id = id)
            }

            setCurrentUser(localUser)
            Result.success(localUser)
        } catch (e: Exception) {
            Result.failure(mapBackendException(e))
        }
    }

    suspend fun remoteGoogleLogin(idToken: String): Result<UserEntity> {
        return try {
            val deviceId = sessionManager.getDeviceId()
            val request = GoogleLoginRequest(idToken, deviceId)
            val response = api.loginWithGoogle(request)
            val loginId =
                response.loginId?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Auth response missing login identifier")
            val userEmail = response.userEmail?.takeIf { it.isNotBlank() } ?: loginId

            if (response.role != null && response.role != "OWNER") {
                return Result.failure(Exception("Access denied: This app is only for Restaurant Owners."))
            }

            sessionManager.saveAuthToken(response.token)
            sessionManager.saveRestaurantId(response.restaurantId)
            sessionManager.saveActiveUserRole(response.role ?: "OWNER")

            var localUser = findLocalUser(loginId, userEmail)
            if (localUser == null) {
                localUser = UserEntity(
                    name = response.userName,
                    email = userEmail,
                    loginId = loginId,
                    googleEmail = userEmail,
                    authProvider = "GOOGLE",
                    whatsappNumber = response.whatsappNumber,
                    restaurantId = response.restaurantId,
                    role = response.role ?: "OWNER",
                    isSynced = true,
                    createdAt = System.currentTimeMillis()
                )
                val id = userDao.insertUser(localUser)
                localUser = localUser.copy(id = id)
            } else {
                localUser = localUser.copy(
                    name = response.userName,
                    email = userEmail,
                    loginId = loginId,
                    googleEmail = userEmail,
                    authProvider = "GOOGLE",
                    whatsappNumber = response.whatsappNumber ?: localUser.whatsappNumber,
                    restaurantId = response.restaurantId,
                    role = response.role ?: "OWNER",
                    isSynced = true
                )
                val id = userDao.insertUser(localUser)
                localUser = localUser.copy(id = id)
            }

            setCurrentUser(localUser)
            Result.success(localUser)
        } catch (e: Exception) {
            Result.failure(mapBackendException(e))
        }
    }

    suspend fun loadPersistedUser() {
        val activeUserId = sessionManager.getActiveUserId()

        if (activeUserId != null) {
            val user = userDao.getUserById(activeUserId)
            _currentUser.value = user
            if (user != null) {
                sessionManager.savePersistedLoginId(user.persistedLoginIdentity())
            }
        } else {
            val loginId = sessionManager.getPersistedLoginId()
            if (loginId != null) {
                val user = userDao.getUserByLoginId(loginId) ?: userDao.getUserByEmail(loginId)
                _currentUser.value = user
                user?.let { sessionManager.saveActiveUserId(it.id); sessionManager.saveActiveUserRole(it.role) }
            }
        }
    }

    fun setCurrentUser(user: UserEntity?) {
        _currentUser.value = user
        if (user != null) {
            sessionManager.savePersistedLoginId(user.persistedLoginIdentity())
            sessionManager.saveActiveUserId(user.id)
            sessionManager.saveActiveUserRole(user.role)
            triggerBackgroundSync()
        } else {
            sessionManager.clearPersistedLoginId()
            sessionManager.clearLocalUserSession()
        }
    }

    suspend fun insertUser(user: UserEntity): Long {
        val enriched =
                user.copy(
                        restaurantId = sessionManager.getRestaurantId(),
                        deviceId = sessionManager.getDeviceId(),
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                )
        val id = userDao.insertUser(enriched)
        triggerBackgroundSync()
        return id
    }

    suspend fun getUserByLoginId(loginId: String): UserEntity? {
        return userDao.getUserByLoginId(loginId) ?: userDao.getUserByEmail(loginId)
    }

    suspend fun requestPasswordResetOtp(phoneNumber: String) {
        try {
            api.requestPasswordResetOtp(PasswordResetOtpRequest(phoneNumber))
        } catch (e: Exception) {
            throw mapBackendException(e)
        }
    }

    suspend fun requestSignupOtp(phoneNumber: String) {
        try {
            api.requestSignupOtp(SignupOtpRequest(phoneNumber))
        } catch (e: Exception) {
            throw mapBackendException(e)
        }
    }

    suspend fun remoteResetPassword(phoneNumber: String, otp: String, newPasswordPlain: String) {
        val request = ResetPasswordRequest(phoneNumber, otp, newPasswordPlain)
        try {
            api.resetPassword(request)
        } catch (e: Exception) {
            throw mapBackendException(e)
        }
    }

    suspend fun checkUserExistsRemotely(phoneNumber: String): Boolean {
        return api.checkUser(phoneNumber)
    }

    suspend fun requestMobileNumberUpdateOtp(newPhone: String): Result<Unit> {
        return try {
            val request = UpdateMobileOtpRequest(newPhone)
            val response = api.requestMobileNumberUpdateOtp(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(response.toBackendException("Failed to send OTP. Please try again."))
            }
        } catch (e: Exception) {
            Result.failure(mapBackendException(e))
        }
    }

    suspend fun confirmMobileNumberUpdate(newPhone: String, otp: String): Result<Unit> {
        return try {
            val request = UpdateMobileRequest(newPhone, otp)
            val response = api.updateMobileNumber(request)
            if (response.isSuccessful) {
                val current = currentUser.value ?: userDao.getAnyUser()
                if (current != null) {
                    val now = System.currentTimeMillis()
                    val isPhoneAuth = current.authProvider.equals("PHONE", ignoreCase = true)
                    val updatedUser = current.copy(
                        loginId = if (isPhoneAuth) newPhone else current.loginId,
                        email = if (isPhoneAuth) newPhone else current.email,
                        whatsappNumber = newPhone,
                        isSynced = true,
                        updatedAt = now,
                        serverUpdatedAt = now
                    )
                    userDao.updateIdentityAndWhatsappNumber(
                        userId = current.id,
                        newLoginId = updatedUser.loginId?.takeIf { it.isNotBlank() } ?: updatedUser.email,
                        newEmail = updatedUser.email,
                        newPhone = newPhone,
                        isSynced = true,
                        updatedAt = now,
                        serverUpdatedAt = now
                    )
                    setCurrentUser(updatedUser)
                }
                Result.success(Unit)
            } else {
                Result.failure(response.toBackendException("Failed to update mobile number."))
            }
        } catch (e: Exception) {
            Result.failure(mapBackendException(e))
        }
    }

    suspend fun updateWhatsappNumber(userId: Long, newPhone: String) {
        userDao.updateWhatsappNumber(userId, newPhone, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun updateAccountDetails(userId: Long, loginId: String, newPhone: String) {
        userDao.updateAccountDetails(userId, loginId, newPhone, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    fun getAllUsers(): Flow<List<UserEntity>> {
        return userDao.getAllUsers()
    }

    suspend fun setActivationStatus(userId: Long, isActive: Boolean) {
        userDao.setActivationStatus(userId, isActive, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    suspend fun deleteUser(user: UserEntity) {
        userDao.markDeleted(user.id, System.currentTimeMillis())
        triggerBackgroundSync()
    }

    private fun triggerBackgroundSync() {
        val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val syncWorkRequest =
                OneTimeWorkRequestBuilder<MasterSyncWorker>().setConstraints(constraints).build()
        workManager.enqueueUniqueWork(
            "ImmediateSync",
            ExistingWorkPolicy.KEEP,
            syncWorkRequest
        )
    }

    private fun mapBackendException(error: Throwable): Throwable {
        return when (error) {
            is BackendException -> error
            is HttpException -> BackendException(BackendErrorParser.fromHttpException(error), error)
            else -> error
        }
    }

    private fun retrofit2.Response<*>.toBackendException(fallback: String): BackendException {
        val parsed = BackendErrorParser.parse(
            errorBody = runCatching { errorBody()?.string() }.getOrNull(),
            statusCode = code()
        )
        return BackendException(
            if (parsed.message.isNullOrBlank() && parsed.fieldErrors.isEmpty()) {
                parsed.copy(message = fallback)
            } else {
                parsed
            }
        )
    }
}
