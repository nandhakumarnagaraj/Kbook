package com.khanabook.lite.pos.worker

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
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
    const val CHANNEL_PAYMENT = "khanabook_payment_v2"
    const val CHANNEL_REFUND = "khanabook_refund_v2"
    const val CHANNEL_KYC = "khanabook_kyc_v2"
    const val CHANNEL_SETTLEMENT = "khanabook_settlement_v2"
    const val CHANNEL_SYSTEM = "khanabook_system_v2"

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

        // Create notification channel groups first to avoid "NotificationChannelGroup doesn't exist" crash
        val paymentsGroup = NotificationChannelGroup(GROUP_PAYMENTS, "Payments & Transactions")
        val systemGroup = NotificationChannelGroup(GROUP_SYSTEM, "System & Security")
        manager.createNotificationChannelGroups(listOf(paymentsGroup, systemGroup))

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
            NotificationManager.IMPORTANCE_HIGH
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
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "KYC approval, rejection, and document alerts"
            enableVibration(true)
            setShowBadge(true)
            group = GROUP_SYSTEM
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
    private fun settlementChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_SETTLEMENT,
            "Settlements",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily settlement and payout notifications"
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
    private fun systemChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            CHANNEL_SYSTEM,
            "System Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Sync status, updates, and system notifications"
            enableVibration(true)
            setShowBadge(true)
            group = GROUP_SYSTEM
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

    /**
     * Generates a circular large icon bitmap with the specified background color
     * and a white-tinted icon in the center (LinkedIn-style).
     */
    fun getCircularLargeIcon(
        context: Context,
        backgroundColor: Int,
        iconResId: Int
    ): android.graphics.Bitmap? {
        return try {
            val size = (context.resources.displayMetrics.density * 48).toInt() // 48dp
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // Draw circular background
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = backgroundColor
                style = android.graphics.Paint.Style.FILL
            }
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)

            // Draw icon in the center
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconResId) ?: return bitmap
            drawable.mutate()
            drawable.colorFilter = android.graphics.PorterDuffColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)

            // Scale the drawable to fit nicely inside the circle (55% of the circle size)
            val iconSize = (size * 0.55f).toInt()
            val margin = (size - iconSize) / 2
            drawable.setBounds(margin, margin, margin + iconSize, margin + iconSize)
            drawable.draw(canvas)

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
