package com.khanabook.lite.pos.domain.util

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.khanabook.lite.pos.worker.MasterSyncWorker
import java.util.concurrent.TimeUnit

fun WorkManager.enqueueMasterSyncOnce(request: OneTimeWorkRequest) {
    enqueueUniqueWork(
        SyncWorkNames.ONE_TIME,
        ExistingWorkPolicy.KEEP,
        request
    )
}

fun WorkManager.enqueueMasterSyncOnce(initialDelaySeconds: Long = 0) {
    enqueueMasterSyncOnce(defaultMasterSyncRequest(initialDelaySeconds))
}

fun defaultMasterSyncRequest(initialDelaySeconds: Long = 0): OneTimeWorkRequest {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val builder = OneTimeWorkRequestBuilder<MasterSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30,
            TimeUnit.SECONDS
        )

    if (initialDelaySeconds > 0) {
        builder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
    }

    return builder.build()
}

fun WorkManager.cancelMasterSyncWork() {
    cancelUniqueWork(SyncWorkNames.ONE_TIME)
    cancelUniqueWork(SyncWorkNames.PERIODIC)
}
