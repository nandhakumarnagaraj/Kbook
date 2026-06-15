package com.khanabook.lite.pos.data.repository

import com.khanabook.lite.pos.data.local.dao.NotificationDao
import com.khanabook.lite.pos.data.local.entity.NotificationEntity
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.domain.manager.SessionManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationCounts(
    val unreadCount: Int = 0
)

@Singleton
class NotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
    private val api: KhanaBookApi,
    private val sessionManager: SessionManager
) {
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
        }
    }

    suspend fun unregisterDeviceToken() {
        try {
            api.unregisterDeviceToken(sessionManager.getDeviceId())
        } catch (e: Exception) {
            android.util.Log.w("NotifRepo", "Failed to unregister device token: ${e.message}")
        }
    }
}
