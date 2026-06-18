package com.khanabook.lite.pos.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.khanabook.lite.pos.data.local.dao.NotificationDao
import com.khanabook.lite.pos.data.local.entity.NotificationEntity
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
            }
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "markAsRead API error: ${e.message}")
        }
    }

    suspend fun markAllAsRead() {
        notificationDao.markAllAsRead()
        try {
            api.markAllNotificationsRead()
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "markAllAsRead API error: ${e.message}")
        }
    }

    suspend fun refreshFromServer() {
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
                        notificationDao.insertAll(entities)
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

    suspend fun registerDeviceToken(token: String) {
        try {
            api.registerDeviceToken(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "deviceId" to sessionManager.getDeviceId()
                )
            )
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

    suspend fun unregisterDeviceToken() {
        try {
            api.unregisterDeviceToken(sessionManager.getDeviceId())
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "Failed to unregister device token: ${e.message}")
        }
    }
}
