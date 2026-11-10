package com.khanabook.lite.pos.feature.notifications.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.notifications.domain.ClosingAlertCalculator
import java.util.concurrent.TimeUnit

/**
 * Helper to schedule on-device pre-closing milestone alerts.
 * Operates offline via WorkManager without network constraints.
 */
object ClosingShiftScheduler {

    private const val TAG = "ClosingShiftScheduler"

    /**
     * Schedules the next local pre-closing milestone check.
     * Guaranteed to work 100% offline.
     */
    fun scheduleNext(context: Context, profile: RestaurantProfileEntity?) {
        val closingTime = profile?.closingTime
        if (closingTime.isNullOrBlank()) {
            cancel(context)
            return
        }

        val delayMs = ClosingAlertCalculator.getNextMilestoneDelayMillis(
            closingTime = closingTime,
            timezone = profile.timezone
        )

        if (delayMs == null) {
            cancel(context)
            return
        }

        val request = OneTimeWorkRequestBuilder<ClosingShiftReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag(ClosingShiftReminderWorker.TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ClosingShiftReminderWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        val delayMinutes = delayMs / (60 * 1000L)
        Log.i(TAG, "Scheduled next closing reminder in ${delayMinutes}m for closingTime=$closingTime")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ClosingShiftReminderWorker.UNIQUE_WORK_NAME)
    }
}
