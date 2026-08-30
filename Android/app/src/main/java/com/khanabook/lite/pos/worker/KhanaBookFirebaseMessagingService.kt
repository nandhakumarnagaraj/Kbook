package com.khanabook.lite.pos.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.NotificationEntity
import com.khanabook.lite.pos.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KhanaBookFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationRepository: com.khanabook.lite.pos.data.repository.NotificationRepository

    override fun onCreate() {
        super.onCreate()
        // Create notification channels on first boot
        NotificationHelper.createChannels(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        kotlinx.coroutines.runBlocking {
            try {
                notificationRepository.registerDeviceToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register new FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: data=${message.data}, notification=${message.notification}")

        val title = message.notification?.title ?: message.data["title"] ?: "KhanaBook"
        val body = message.notification?.body ?: message.data["message"] ?: ""
        val type = message.data["type"] ?: "system"
        val referenceId = message.data["referenceId"]
        val referenceType = message.data["referenceType"]
        val amount = message.data["amount"]
        val notificationId = (message.data["notificationId"]?.toLongOrNull()
            ?: System.currentTimeMillis())

        // Save to local Room database synchronously using runBlocking
        // to prevent process termination before the write finishes.
        kotlinx.coroutines.runBlocking {
            try {
                val entity = NotificationEntity(
                    id = notificationId,
                    serverId = notificationId,
                    notificationType = type,
                    title = title,
                    message = body,
                    referenceId = referenceId,
                    referenceType = referenceType,
                    amount = amount,
                    createdAt = System.currentTimeMillis()
                )
                notificationRepository.insertLocal(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert local notification in background", e)
            }
        }

        // Show Android system notification
        showNotification(notificationId, title, body, type, referenceId)
    }

    private fun showNotification(id: Long, title: String, body: String, type: String, referenceId: String?) {
        val channelId = when (type) {
            "payment_received", "qr_order" -> NotificationHelper.CHANNEL_PAYMENT
            "refund" -> NotificationHelper.CHANNEL_REFUND
            "kyc" -> NotificationHelper.CHANNEL_KYC
            "settlement" -> NotificationHelper.CHANNEL_SETTLEMENT
            "inventory_low" -> NotificationHelper.CHANNEL_INVENTORY
            "permission_request", "permission_approved", "permission_rejected" ->
                NotificationHelper.CHANNEL_PERMISSIONS
            else -> NotificationHelper.CHANNEL_SYSTEM
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_type", type)
            putExtra("notification_id", id)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Parse HTML formatting (e.g. bolding key variables like order IDs, amounts, dates)
        val formattedTitle: CharSequence = androidx.core.text.HtmlCompat.fromHtml(
            title,
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        val formattedBody: CharSequence = androidx.core.text.HtmlCompat.fromHtml(
            body,
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        // Select color based on notification type:
        // Saffron for FSSAI/warning, green for payments, red for refunds, violet for KYC, blue for settlements
        val colorInt = when (type) {
            "payment_received" -> 0xFF16A34A.toInt() // Green
            "refund" -> 0xFFEF4444.toInt() // Red
            "kyc" -> 0xFF8B5CF6.toInt() // Violet
            "settlement" -> 0xFF0284C7.toInt() // Blue
            "fssai_expiry" -> 0xFFF97316.toInt() // Saffron
            else -> 0xFF7C5CDB.toInt() // Purple (system default)
        }

        // Real brand logo as the large icon; fall back to the synthetic bell
        // circle only if the resource fails to decode.
        val largeIcon = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.khanabook_logo)
        } catch (_: Exception) {
            null
        } ?: NotificationHelper.getCircularLargeIcon(this, colorInt, R.drawable.ic_notification_bell)

        val builder = NotificationHelper.buildNotification(
            context = this,
            id = id,
            channelId = channelId,
            title = title,
            body = body
        ).apply {
            setContentIntent(pendingIntent)
            setContentTitle(formattedTitle)
            setContentText(formattedBody)
            setStyle(NotificationCompat.BigTextStyle().bigText(formattedBody))
            largeIcon?.let { setLargeIcon(it) }
            setColor(colorInt)
            // Bundle children under one expandable group header; only the summary alerts.
            setGroup(channelId)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

            if (type == "fssai_expiry" && !referenceId.isNullOrBlank()) {
                val payIntent = Intent(this@KhanaBookFirebaseMessagingService, MainActivity::class.java).apply {
                    action = "ACTION_PAY_FSSAI"
                    putExtra("fssai_number", referenceId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val payPendingIntent = PendingIntent.getActivity(
                    this@KhanaBookFirebaseMessagingService, id.toInt() + 1000, payIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val remindIntent = Intent(this@KhanaBookFirebaseMessagingService, NotificationActionReceiver::class.java).apply {
                    action = "ACTION_REMIND_LATER"
                    putExtra("fssai_number", referenceId)
                    putExtra("notification_id", id.toInt())
                }
                val remindPendingIntent = PendingIntent.getBroadcast(
                    this@KhanaBookFirebaseMessagingService, id.toInt() + 2000, remindIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                addAction(R.drawable.ic_notification_bell, "Pay Now", payPendingIntent)
                addAction(R.drawable.ic_notification_bell, "Remind Me Later", remindPendingIntent)
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id.toInt(), builder.build())
        trackGroupChildAndPostSummary(notificationManager, channelId, type, id.toInt())
    }

    /**
     * Telegram-style grouping bookkeeping: per group key we keep the live child
     * IDs (capped) and a count of pushes since process start, then post/repost a
     * single group-summary notification ("N new payment alerts"). The summary is
     * what Android shows collapsed; expanding it reveals the individual children.
     * Counts are best-effort in-memory state and reset on process death.
     */
    private fun trackGroupChildAndPostSummary(
        notificationManager: NotificationManager,
        groupKey: String,
        type: String,
        childId: Int
    ) {
        val children = groupChildren.getOrPut(groupKey) { ArrayDeque() }
        synchronized(children) {
            children.addLast(childId)
            while (children.size > MAX_CHILDREN_PER_GROUP) {
                notificationManager.cancel(children.removeFirst())
            }
        }
        val count = (groupPushCounts[groupKey] ?: 0) + 1
        groupPushCounts[groupKey] = count

        val summary = NotificationCompat.Builder(this, groupKey)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("KhanaBook")
            .setContentText("$count new ${domainLabel(type)}${if (count > 1) "s" else ""}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$count new ${domainLabel(type)}${if (count > 1) "s" else ""} — tap to view"))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setColor(0xFF7C5CDB.toInt())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(summaryId(groupKey), summary)
    }

    private fun domainLabel(type: String): String = when (type) {
        "payment_received", "qr_order" -> "payment alert"
        "refund" -> "refund update"
        "kyc" -> "KYC update"
        "settlement" -> "settlement update"
        "inventory_low" -> "stock alert"
        "permission_request", "permission_approved", "permission_rejected" -> "permission update"
        "terminal" -> "device update"
        else -> "alert"
    }

    private fun summaryId(groupKey: String): Int =
        SUMMARY_ID_OFFSET + (groupKey.hashCode() and 0x7FFFFF)

    companion object {
        private const val TAG = "KBFirebaseMsg"

        /** Max individual notifications kept visible per group; older ones are cancelled. */
        private const val MAX_CHILDREN_PER_GROUP = 6

        /** Offset keeps summary IDs away from server-notification-ID child IDs. */
        private const val SUMMARY_ID_OFFSET = 1_000_000

        private val groupChildren = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<Int>>()
        private val groupPushCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    }
}
