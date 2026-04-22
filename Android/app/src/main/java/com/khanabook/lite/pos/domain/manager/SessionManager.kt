package com.khanabook.lite.pos.domain.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.domain.util.KeystoreBackedPreferences
import com.khanabook.lite.pos.domain.util.LegacyEncryptedPrefsMigration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


private const val PREFS_NAME = "session_prefs"
private const val SECURE_PREFS_NAME = "secure_session_prefs"
private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"

@Singleton
class SessionManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val debugTag = "KhanaBookDebugAuth"
    private val appLockGracePeriodMs = 30_000L

    private val securePrefs = KeystoreBackedPreferences(context, SECURE_PREFS_NAME)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Session is only expired by explicit logout — no inactivity timeout (GPay style).
    private val _isSessionExpired = MutableStateFlow(false)
    val isSessionExpired: StateFlow<Boolean> = _isSessionExpired

    init {
        migrateLegacySecurePrefsIfNeeded()
    }

    fun getAuthToken(): String? {
        val token = securePrefs.getString("auth_token", null)
        if (BuildConfig.DEBUG) {
            Log.d(debugTag, "getAuthToken present=${!token.isNullOrBlank()}")
        }
        return token
    }

    fun saveAuthToken(token: String) {
        securePrefs.putString("auth_token", token)
        _isSessionExpired.value = false
    }

    fun getDeviceId(): String {
        synchronized(this) {
            var deviceId = securePrefs.getString("device_id", null)
            if (deviceId == null) {
                deviceId = java.util.UUID.randomUUID().toString()
                saveDeviceId(deviceId)
            }
            return deviceId
        }
    }

    fun saveDeviceId(deviceId: String) {
        securePrefs.putString("device_id", deviceId)
    }

    fun getLastSyncTimestamp(): Long = prefs.getLong("last_sync_timestamp", 0L)

    fun saveLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    fun getRestaurantId(): Long = prefs.getLong("restaurant_id", 0L)

    fun saveRestaurantId(restaurantId: Long) {
        prefs.edit().putLong("restaurant_id", restaurantId).apply()
    }

    fun setInitialSyncCompleted(isCompleted: Boolean) {
        prefs.edit().putBoolean("initial_sync_completed", isCompleted).apply()
    }

    fun isInitialSyncCompleted(): Boolean = prefs.getBoolean("initial_sync_completed", false)

    fun getActiveUserId(): Long? {
        val id = prefs.getLong("active_user_id", -1L)
        return if (id != -1L) id else null
    }

    fun saveActiveUserId(userId: Long) {
        prefs.edit().putLong("active_user_id", userId).apply()
    }

    fun getActiveUserRole(): String? = prefs.getString("active_user_role", null)

    fun isOwner(): Boolean = getActiveUserRole() == "OWNER"
    fun isKbookAdmin(): Boolean = getActiveUserRole() == "KBOOK_ADMIN"

    fun saveActiveUserRole(role: String) {
        prefs.edit().putString("active_user_role", role).apply()
    }

    fun clearLocalUserSession() {
        prefs.edit().remove("active_user_id").remove("active_user_role").apply()
    }

    // Clears only the auth token and user identity — keeps device_id, last_sync_timestamp,
    // and restaurant_id intact so unsynced local data can be pushed after re-login.
    fun clearAuthOnly() {
        securePrefs.remove("auth_token")
        securePrefs.remove("persisted_login_id")
        prefs.edit()
            .remove("active_user_id")
            .remove("active_user_role")
            .apply()
        _isSessionExpired.value = true
    }

    fun getPersistedLoginId(): String? = securePrefs.getString("persisted_login_id", null)

    fun savePersistedLoginId(loginId: String) {
        securePrefs.putString("persisted_login_id", loginId)
    }

    fun clearPersistedLoginId() {
        securePrefs.remove("persisted_login_id")
    }

    fun getFailedLoginAttempts(): Int = prefs.getInt("failed_login_attempts", 0)

    fun setFailedLoginAttempts(count: Int) {
        prefs.edit().putInt("failed_login_attempts", count).apply()
    }

    fun getLockoutUntilMs(): Long = prefs.getLong("lockout_until_ms", 0L)

    fun setLockoutUntilMs(ms: Long) {
        prefs.edit().putLong("lockout_until_ms", ms).apply()
    }

    fun clearLockout() {
        prefs.edit().remove("failed_login_attempts").remove("lockout_until_ms").apply()
    }

    fun isPinLockEnabled(): Boolean = prefs.getBoolean("pin_lock_enabled", false)

    fun isPinLockFlagEnabled(): Boolean = prefs.getBoolean("pin_lock_enabled", false)

    fun setPinLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pin_lock_enabled", enabled).apply()
    }

    fun getPinHash(): String? = securePrefs.getString("pin_hash", null)

    fun savePinHash(hash: String) {
        securePrefs.putString("pin_hash", hash)
    }

    fun clearPin() {
        securePrefs.remove("pin_hash")
        prefs.edit().putBoolean("pin_lock_enabled", false).apply()
    }

    fun onAppBackgrounded() {
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis()).apply()
    }

    fun shouldShowAppLock(): Boolean {
        // If the flag is set, we SHOULD show the lock. 
        // If getPinHash() is null but flag is true, it means keystore is corrupted/lost.
        // We still show the lock screen, and the lock screen will handle the "hash missing" state
        // (e.g. by forcing re-login or clear).
        if (!isPinLockFlagEnabled()) return false
        
        val lastBackground = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        if (lastBackground == 0L) return false
        return System.currentTimeMillis() - lastBackground > appLockGracePeriodMs
    }

    fun clearBackgroundTime() {
        prefs.edit().remove(KEY_LAST_BACKGROUND_TIME).apply()
    }

    fun invalidateAuthSession() {
        if (BuildConfig.DEBUG) {
            Log.d(debugTag, "invalidateAuthSession")
        }
        securePrefs.remove("auth_token")
        clearLocalUserSession()
        _isSessionExpired.value = true
    }

    /**
     * Clears all session data, including the app lock state.
     * This is used for hard logouts.
     */
    fun clearSession() {
        if (BuildConfig.DEBUG) {
            Log.d(debugTag, "clearSession")
        }
        securePrefs.remove("auth_token")
        prefs.edit().clear().apply()
        _isSessionExpired.value = true
    }

    private fun migrateLegacySecurePrefsIfNeeded() {
        val legacyPrefs = LegacyEncryptedPrefsMigration.open(context, SECURE_PREFS_NAME) ?: return
        migrateKeyIfMissing(legacyPrefs, "auth_token")
        migrateKeyIfMissing(legacyPrefs, "device_id")
        migrateKeyIfMissing(legacyPrefs, "persisted_login_id")
        migrateKeyIfMissing(legacyPrefs, "pin_hash")
    }

    private fun migrateKeyIfMissing(legacyPrefs: SharedPreferences, key: String) {
        if (securePrefs.contains(key)) return
        val value = legacyPrefs.getString(key, null) ?: return
        securePrefs.putString(key, value)
    }
}
