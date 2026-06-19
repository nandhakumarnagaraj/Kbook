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

    companion object {
        private const val TAG = "KBFirebaseMsg"
    }

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
        CoroutineScope(Dispatchers.IO).launch {
            notificationRepository.registerDeviceToken(token)
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

        // Save to local Room database and show system notification
        CoroutineScope(Dispatchers.IO).launch {
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
        }

        // Show Android system notification
        showNotification(notificationId, title, body, type, referenceId)
    }

    private fun showNotification(id: Long, title: String, body: String, type: String, referenceId: String?) {
        val channelId = when (type) {
            "payment_received" -> NotificationHelper.CHANNEL_PAYMENT
            "refund" -> NotificationHelper.CHANNEL_REFUND
            "kyc" -> NotificationHelper.CHANNEL_KYC
            "settlement" -> NotificationHelper.CHANNEL_SETTLEMENT
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

        val builder = NotificationHelper.buildNotification(
            context = this,
            id = id,
            channelId = channelId,
            title = title,
            body = body
        ).apply {
            setContentIntent(pendingIntent)

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
    }
}
