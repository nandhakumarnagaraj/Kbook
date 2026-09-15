package com.khanabook.lite.pos.feature.notifications.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.khanabook.lite.pos.feature.notifications.data.NotificationDao
import com.khanabook.lite.pos.feature.notifications.data.NotificationEntity
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationCounts(
    val unreadCount: Int = 0
)

@OptIn(DelicateCoroutinesApi::class)
@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    private val _activeBanner = MutableStateFlow<NotificationEntity?>(null)

    /** The in-app (LinkedIn-style) banner currently on screen, if any. */
    val activeBanner = _activeBanner.asStateFlow()

    private companion object {
        const val KEY_LAST_REGISTERED_TOKEN = "fcm_last_registered_token"
        const val KEY_PENDING_READ_IDS = "pending_read_ids"
        const val KEY_PENDING_MARK_ALL = "pending_mark_all_read"

        /** Highest notification id already shown as an in-app banner. */
        const val KEY_LAST_BANNER_ID = "last_banner_id"

        /** Mirror of the server's 90-day notification retention. */
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
    }

    fun getNotifications(): Flow<List<NotificationEntity>> =
        notificationDao.getNotifications()

    fun getUnreadCount(): Flow<Int> =
        notificationDao.getUnreadCount()

    suspend fun markAsRead(id: Long) {
        notificationDao.markAsRead(id)
        try {
            val response = api.markNotificationRead(id)
            if (!response.isSuccessful) {
                android.util.Log.w("NotifRepo", "markAsRead API failed for id=$id")
                addPendingReadId(id)
            }
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "markAsRead API error: ${e.message}")
            addPendingReadId(id)
        }
    }

    suspend fun markAllAsRead() {
        notificationDao.markAllAsRead()
        try {
            val response = api.markAllNotificationsRead()
            if (response.isSuccessful) {
                clearPendingReadState()
            } else {
                markAllReadPending()
            }
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "markAllAsRead API error: ${e.message}")
            markAllReadPending()
        }
    }

    /**
     * Re-pushes read state that could not reach the server while offline. Called
     * on reconnect and before every server refresh, so a notification read
     * offline eventually stops showing as unread on other devices.
     */
    suspend fun flushPendingReadState() {
        // A pending "mark all" supersedes any individual pending IDs.
        if (prefs.getBoolean(KEY_PENDING_MARK_ALL, false)) {
            try {
                if (api.markAllNotificationsRead().isSuccessful) {
                    clearPendingReadState()
                }
            } catch (e: Exception) {
                android.util.Log.w("NotifRepo", "flush markAll error: ${e.message}")
            }
            return
        }

        val ids = prefs.getStringSet(KEY_PENDING_READ_IDS, emptySet())?.toSet().orEmpty()
        if (ids.isEmpty()) return

        val synced = mutableSetOf<String>()
        for (raw in ids) {
            val id = raw.toLongOrNull() ?: continue
            try {
                if (api.markNotificationRead(id).isSuccessful) synced.add(raw)
            } catch (e: Exception) {
                android.util.Log.w("NotifRepo", "flush read id=$id error: ${e.message}")
            }
        }
        if (synced.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_PENDING_READ_IDS, ids - synced).apply()
        }
    }

    private fun addPendingReadId(id: Long) {
        val current = prefs.getStringSet(KEY_PENDING_READ_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id.toString())
        prefs.edit().putStringSet(KEY_PENDING_READ_IDS, current).apply()
    }

    private fun markAllReadPending() {
        prefs.edit().putBoolean(KEY_PENDING_MARK_ALL, true).apply()
    }

    private fun clearPendingReadState() {
        prefs.edit()
            .putBoolean(KEY_PENDING_MARK_ALL, false)
            .putStringSet(KEY_PENDING_READ_IDS, emptySet())
            .apply()
    }

    suspend fun refreshFromServer() {
        // Push any read state that failed to sync while offline before pulling,
        // so the server's copy is not stale in the other direction.
        flushPendingReadState()
        try {
            val response = api.getNotifications(limit = 50)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body["status"] == "success") {
                    @Suppress("UNCHECKED_CAST")
                    val notifs = body["notifications"] as? List<Map<String, Any>> ?: return

                    val entities = notifs.map { map ->
                        val serverId = (map["id"] as? Number)?.toLong() ?: 0L
                        NotificationEntity(
                            id = serverId,
                            serverId = serverId,
                            notificationType = map["notificationType"] as? String ?: "",
                            title = map["title"] as? String ?: "",
                            message = map["message"] as? String,
                            referenceId = map["referenceId"] as? String,
                            referenceType = map["referenceType"] as? String,
                            amount = map["amount"]?.toString(),
                            isRead = map["isRead"] as? Boolean ?: false,
                            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        )
                    }

                    if (entities.isNotEmpty()) {
                        // A notification read while offline is still marked unread on
                        // the server. insertAll upserts with REPLACE, which would flip
                        // it back to unread — so re-apply any local read flags first.
                        val locallyRead = notificationDao.getReadIds(entities.map { it.id }).toSet()
                        val merged = if (locallyRead.isEmpty()) {
                            entities
                        } else {
                            entities.map { if (it.id in locallyRead) it.copy(isRead = true) else it }
                        }
                        notificationDao.insertAll(merged)
                        notificationDao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "refreshFromServer error: ${e.message}")
        }
    }

    suspend fun insertLocal(entity: NotificationEntity) {
        notificationDao.insert(entity)
    }

    /**
     * Watches the local inbox and promotes newly inserted notifications into the
     * in-app banner state while the app is running. The cursor is the highest
     * notification id already seen (persisted in prefs), so existing inbox
     * contents never pop on first launch and an item is not re-bannered when the
     * table re-emits (e.g. after marking a notification read).
     */
    suspend fun watchForNoticeBanners() {
        notificationDao.getNotifications()
            .catch { e -> android.util.Log.w("NotifRepo", "banner watch error: ${e.message}") }
            .collect { list ->
                if (list.isEmpty()) return@collect
                var baseline = prefs.getLong(KEY_LAST_BANNER_ID, -1L)
                if (baseline < 0) {
                    prefs.edit().putLong(KEY_LAST_BANNER_ID, list.maxOfOrNull { it.id } ?: 0L).apply()
                    return@collect
                }
                val unseen = list.filter { it.id > baseline }
                if (unseen.isEmpty()) return@collect
                val newest = unseen.maxByOrNull { it.id } ?: return@collect
                prefs.edit().putLong(KEY_LAST_BANNER_ID, newest.id).apply()
                _activeBanner.value = newest
            }
    }

    /** Clears the in-app banner after it has been shown or tapped. */
    fun consumeActiveBanner() {
        _activeBanner.value = null
    }

    suspend fun registerDeviceToken(token: String) {
        // Skip when this exact token is already registered server-side — the app
        // calls this on every start/token refresh and each redundant call used to
        // trigger a duplicate "Welcome back!" broadcast to all restaurant devices.
        if (prefs.getString(KEY_LAST_REGISTERED_TOKEN, null) == token) return
        try {
            api.registerDeviceToken(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "deviceId" to sessionManager.getDeviceId()
                )
            )
            prefs.edit().putString(KEY_LAST_REGISTERED_TOKEN, token).apply()
        } catch (e: Exception) {
            android.util.Log.e("NotifRepo", "Failed to register device token: ${e.message}")
            // Schedule retry for failed registration
            scheduleTokenRegistrationRetry(token)
        }
    }

    private fun scheduleTokenRegistrationRetry(token: String) {
        GlobalScope.launch(Dispatchers.IO) {
            delay(5000)
            try {
                api.registerDeviceToken(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "deviceId" to sessionManager.getDeviceId()
                    )
                )
                prefs.edit().putString(KEY_LAST_REGISTERED_TOKEN, token).apply()
                Log.d("NotifRepo", "Device token registered successfully after retry")
            } catch (e: Exception) {
                Log.e("NotifRepo", "Device token registration retry failed: ${e.message}")
                if (shouldKeepRetryingToken(token)) {
                    scheduleTokenRegistrationRetry(token)
                }
            }
        }
    }

    private fun shouldKeepRetryingToken(token: String): Boolean {
        val retryCount = prefs.getInt("fcm_retry_count_$token", 0)
        incrementRetryCount(token)
        return retryCount < 5
    }

    private fun incrementRetryCount(token: String) {
        val currentCount = prefs.getInt("fcm_retry_count_$token", 0)
        prefs.edit().putInt("fcm_retry_count_$token", currentCount + 1).apply()
    }

    suspend fun registerCurrentDeviceToken() {
        try {
            val token = suspendCancellableCoroutine<String> { continuation ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        if (continuation.isActive) continuation.resume(token)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.w("NotifRepo", "Failed to fetch FCM token: ${e.message}")
                        if (continuation.isActive) continuation.resume("")
                    }
            }
            if (token.isNotBlank()) {
                registerDeviceToken(token)
            }
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "Failed to sync current device token: ${e.message}")
        }
    }

    fun registerCurrentDeviceTokenInBackground() {
        GlobalScope.launch(Dispatchers.IO) {
            registerCurrentDeviceToken()
        }
    }

    suspend fun unregisterDeviceToken() {
        prefs.edit().remove(KEY_LAST_REGISTERED_TOKEN).apply()
        try {
            api.unregisterDeviceToken(sessionManager.getDeviceId())
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "Failed to unregister device token: ${e.message}")
        }
    }
}
