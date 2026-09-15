package com.khanabook.lite.pos.feature.notifications.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.core.app.MainActivity

/**
 * Re-posts an FSSAI renewal reminder after the user chose "Remind Me Later".
 *
 * Before this existed, the snooze action only cancelled the notification and
 * showed a toast, so the reminder was silently dropped â€” the button promised
 * something the app never did.
 */
class FssaiReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val fssaiNumber = inputData.getString(KEY_FSSAI_NUMBER)
        val context = applicationContext

        // Channels are normally created by the FCM service; ensure they exist in
        // case this worker runs before any push has been received this install.
        NotificationHelper.createChannels(context)

        val id = REMINDER_NOTIFICATION_ID
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(
                com.khanabook.lite.pos.feature.notifications.domain.NotificationRouteManager.EXTRA_NOTIFICATION_TYPE,
                "fssai_expiry"
            )
            putExtra("fssai_number", fssaiNumber)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (fssaiNumber.isNullOrBlank()) {
            "Your FSSAI licence renewal is still pending."
        } else {
            "FSSAI licence $fssaiNumber renewal is still pending."
        }

        val notification = NotificationHelper.buildNotification(
            context = context,
            id = id.toLong(),
            channelId = NotificationHelper.CHANNEL_SYSTEM,
            title = "FSSAI renewal reminder",
            body = body
        ).apply {
            setContentIntent(pendingIntent)
            setColor(0xFFF97316.toInt()) // Saffron, matching the original alert
        }.build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
        return Result.success()
    }

    companion object {
        const val KEY_FSSAI_NUMBER = "fssai_number"
        const val UNIQUE_WORK_NAME = "fssai_renewal_reminder"

        /** Fixed ID so a repeated snooze replaces rather than stacks reminders. */
        private const val REMINDER_NOTIFICATION_ID = 990_001
    }
}
