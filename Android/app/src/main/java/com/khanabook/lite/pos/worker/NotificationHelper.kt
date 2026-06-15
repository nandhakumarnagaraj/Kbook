package com.khanabook.lite.pos.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.khanabook.lite.pos.R

/**
 * Notification channels and helper for KhanaBook POS notifications,
 * styled with Easebuzz ePOS-inspired design using Premium Saffron palette.
 */
object NotificationHelper {

    // ── Channel IDs ──────────────────────────────────────────────
    const val CHANNEL_PAYMENT = "khanabook_payment"
    const val CHANNEL_REFUND = "khanabook_refund"
    const val CHANNEL_KYC = "khanabook_kyc"
    const val CHANNEL_SETTLEMENT = "khanabook_settlement"
    const val CHANNEL_SYSTEM = "khanabook_system"

    private const val GROUP_PAYMENTS = "khanabook_group_payments"
    private const val GROUP_SYSTEM = "khanabook_group_system"

    /**
     * Create all notification channels for API 26+.
     * Must be called once, e.g. from FirebaseMessagingService.onCreate()
     * or Application.onCreate().
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            paymentChannel(context)
        )
        manager.createNotificationChannel(
            refundChannel(context)
        )
        manager.createNotificationChannel(
            kycChannel(context)
        )
        manager.createNotificationChannel(
            settlementChannel(context)
        )
        manager.createNotificationChannel(
            systemChannel(context)
        )
    }

    // ── Channel Definitions ─────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    private fun paymentChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_PAYMENT,
            "Payment Received",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time payment and transaction alerts"
            enableVibration(true)
            setShowBadge(true)
            group = GROUP_PAYMENTS
            // Saffron accent — use brand color for light indicator
            // Easebuzz ePOS uses #F97316 (saffron) for payment alerts
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        return channel
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refundChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_REFUND,
            "Refund Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Refund status updates and confirmations"
            enableVibration(true)
            setShowBadge(true)
            group = GROUP_PAYMENTS
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        return channel
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun kycChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_KYC,
            "KYC Verification",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "KYC approval, rejection, and document alerts"
            enableVibration(true)
            setShowBadge(true)
            group = GROUP_SYSTEM
        }
        return channel
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun settlementChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_SETTLEMENT,
            "Settlements",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily settlement and payout notifications"
            enableVibration(true)
            setShowBadge(true)
            group = GROUP_PAYMENTS
        }
        return channel
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun systemChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_SYSTEM,
            "System Alerts",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Sync status, updates, and system notifications"
            setShowBadge(false)
        }
        return channel
    }

    // ── Build a standard notification ─────────────────────────

    fun buildNotification(
        context: Context,
        id: Long,
        channelId: String,
        title: String,
        body: String,
        priority: Int = NotificationCompat.PRIORITY_HIGH
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setGroup(channelId) // group by channel
    }
}
