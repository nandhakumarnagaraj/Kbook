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

    fun getLastSyncTimestamp(): Long {
        val restaurantId = getRestaurantId()
        if (restaurantId <= 0L) return prefs.getLong("last_sync_timestamp", 0L)
        val scopedKey = "last_sync_timestamp_$restaurantId"
        return prefs.getLong(scopedKey, 0L)
    }

    fun saveLastSyncTimestamp(timestamp: Long) {
        val restaurantId = getRestaurantId()
        val editor = prefs.edit()
        if (restaurantId > 0L) {
            editor.putLong("last_sync_timestamp_$restaurantId", timestamp)
        } else {
            editor.putLong("last_sync_timestamp", timestamp)
        }
        editor.apply()
    }

    fun getRestaurantId(): Long = prefs.getLong("restaurant_id", 0L)

    fun saveRestaurantId(restaurantId: Long) {
        prefs.edit().putLong("restaurant_id", restaurantId).apply()
    }

    private fun scopedKey(baseKey: String): String {
        val restaurantId = getRestaurantId()
        return if (restaurantId > 0L) "${baseKey}_$restaurantId" else baseKey
    }

    fun setInitialSyncCompleted(isCompleted: Boolean) {
        val restaurantId = getRestaurantId()
        val editor = prefs.edit()
        if (restaurantId > 0L) {
            editor.putBoolean("initial_sync_completed_$restaurantId", isCompleted)
        } else {
            editor.putBoolean("initial_sync_completed", isCompleted)
        }
        editor.apply()
    }

    fun isInitialSyncCompleted(): Boolean {
        val restaurantId = getRestaurantId()
        if (restaurantId <= 0L) return prefs.getBoolean("initial_sync_completed", false)
        return prefs.getBoolean("initial_sync_completed_$restaurantId", false)
    }

    fun getActiveUserId(): Long? {
        val restaurantId = getRestaurantId()
        if (restaurantId <= 0L) {
            val legacyId = prefs.getLong("active_user_id", -1L)
            return if (legacyId != -1L) legacyId else null
        }
        val scoped = scopedKey("active_user_id")
        val id = prefs.getLong(scoped, -1L)
        return if (id != -1L) id else null
    }

    fun saveActiveUserId(userId: Long) {
        val editor = prefs.edit()
        if (getRestaurantId() > 0L) {
            editor.putLong(scopedKey("active_user_id"), userId)
        } else {
            editor.putLong("active_user_id", userId)
        }
        editor.apply()
    }

    fun getActiveUserRole(): String? {
        if (getRestaurantId() <= 0L) return prefs.getString("active_user_role", null)
        val scoped = scopedKey("active_user_role")
        return prefs.getString(scoped, null)
    }

    fun isOwner(): Boolean = getActiveUserRole() == "OWNER"
    fun isKbookAdmin(): Boolean = getActiveUserRole() == "KBOOK_ADMIN"

    fun saveActiveUserRole(role: String) {
        val editor = prefs.edit()
        if (getRestaurantId() > 0L) {
            editor.putString(scopedKey("active_user_role"), role)
        } else {
            editor.putString("active_user_role", role)
        }
        editor.apply()
    }

    fun clearLocalUserSession() {
        prefs.edit()
            .remove(scopedKey("active_user_id"))
            .remove(scopedKey("active_user_role"))
            .remove("active_user_id")
            .remove("active_user_role")
            .apply()
    }

    // Clears only the auth token and user identity — keeps device_id, last_sync_timestamp,
    // and restaurant_id intact so unsynced local data can be pushed after re-login.
    fun clearAuthOnly() {
        securePrefs.remove("auth_token")
        securePrefs.remove(scopedKey("persisted_login_id"))
        securePrefs.remove("persisted_login_id")
        clearPin()
        prefs.edit()
            .remove(scopedKey("active_user_id"))
            .remove(scopedKey("active_user_role"))
            .remove("active_user_id")
            .remove("active_user_role")
            .apply()
        _isSessionExpired.value = true
    }

    fun getDisplayScale(): Float = prefs.getFloat("display_scale", 1.0f)

    fun setDisplayScale(scale: Float) {
        prefs.edit().putFloat("display_scale", scale.coerceIn(0.8f, 1.3f)).apply()
    }

    fun getPersistedLoginId(): String? {
        if (getRestaurantId() <= 0L) return securePrefs.getString("persisted_login_id", null)
        return securePrefs.getString(scopedKey("persisted_login_id"), null)
    }

    fun savePersistedLoginId(loginId: String) {
        if (getRestaurantId() > 0L) {
            securePrefs.putString(scopedKey("persisted_login_id"), loginId)
        } else {
            securePrefs.putString("persisted_login_id", loginId)
        }
    }

    fun clearPersistedLoginId() {
        securePrefs.remove(scopedKey("persisted_login_id"))
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
        if (!isPinLockFlagEnabled()) return false
        
        val lastBackground = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        // If lastBackground is 0, it means the app was just started from scratch.
        // We don't lock on first launch to allow splash screen to handle navigation.
        if (lastBackground == 0L) return false
        
        return System.currentTimeMillis() - lastBackground >= appLockGracePeriodMs
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
