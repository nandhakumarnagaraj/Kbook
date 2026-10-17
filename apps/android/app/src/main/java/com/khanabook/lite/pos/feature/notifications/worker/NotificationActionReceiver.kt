package com.khanabook.lite.pos.feature.notifications.worker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", -1)
        val fssaiNumber = intent.getStringExtra("fssai_number")

        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }

        if (action == "ACTION_REMIND_LATER") {
            // Actually schedule the reminder rather than just dismissing it.
            // REPLACE keeps a single pending reminder if the user snoozes twice.
            val request = OneTimeWorkRequestBuilder<FssaiReminderWorker>()
                .setInitialDelay(SNOOZE_HOURS, TimeUnit.HOURS)
                .setInputData(
                    Data.Builder()
                        .putString(FssaiReminderWorker.KEY_FSSAI_NUMBER, fssaiNumber)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                FssaiReminderWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            Toast.makeText(
                context,
                "FSSAI reminder snoozed for ${SNOOZE_HOURS}h",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private companion object {
        const val SNOOZE_HOURS = 24L
    }
}
