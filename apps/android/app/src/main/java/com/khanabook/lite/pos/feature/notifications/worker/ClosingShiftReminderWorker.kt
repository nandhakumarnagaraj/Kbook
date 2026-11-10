package com.khanabook.lite.pos.feature.notifications.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.khanabook.lite.pos.core.app.MainActivity
import com.khanabook.lite.pos.feature.auth.data.RestaurantDao
import com.khanabook.lite.pos.feature.notifications.domain.ClosingAlertCalculator
import com.khanabook.lite.pos.feature.notifications.domain.NotificationRouteManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that triggers on-device pre-closing notifications for the cashier.
 *
 * Runs 100% offline via Android WorkManager without requiring any server round-trip or internet connection.
 * Alerts cashiers at 30 minutes and 15 minutes before closing, and at store closing.
 */
@HiltWorker
class ClosingShiftReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val restaurantDao: RestaurantDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val profile = restaurantDao.getProfile()
            if (profile == null || profile.closingTime.isBlank()) {
                return Result.success()
            }

            val alert = ClosingAlertCalculator.calculate(
                closingTime = profile.closingTime,
                timezone = profile.timezone
            )

            if (alert != null) {
                NotificationHelper.createChannels(context)

                val id = CLOSING_NOTIFICATION_ID
                val tapIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra(
                        NotificationRouteManager.EXTRA_NOTIFICATION_TYPE,
                        "shift_closing"
                    )
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, id, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val title = if (alert.isPastClosing) {
                    "Shift Closing In Progress"
                } else if (alert.minutesUntilClosing == 0) {
                    "Store Closing Time Reached"
                } else {
                    "Store Closing in ${alert.minutesUntilClosing}m"
                }

                val body = if (alert.isPastClosing) {
                    "Store is ${alert.minutesPastClosing}m past closing (${alert.closingTimeFormatted}). Settle remaining open tables before daily cutoff."
                } else if (alert.minutesUntilClosing == 0) {
                    "Closing time (${alert.closingTimeFormatted}) reached. Complete table settlements and cash drawer reconciliation."
                } else {
                    "Closing at ${alert.closingTimeFormatted} (${alert.minutesUntilClosing}m remaining). Settle active bills and prepare shift close."
                }

                val notification = NotificationHelper.buildNotification(
                    context = context,
                    id = id.toLong(),
                    channelId = NotificationHelper.CHANNEL_SHIFT,
                    title = title,
                    body = body
                ).apply {
                    setContentIntent(pendingIntent)
                    setColor(0xFFF59E0B.toInt()) // Amber warning
                }.build()

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(id, notification)
                Log.i(TAG, "Closing shift alert posted: $title - $body")
            }

            // Always reschedule for the next upcoming milestone
            ClosingShiftScheduler.scheduleNext(context, profile)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating closing shift reminder", e)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "ClosingShiftReminder"
        const val UNIQUE_WORK_NAME = "closing_shift_reminder_work"
        const val CLOSING_NOTIFICATION_ID = 990_002
    }
}
