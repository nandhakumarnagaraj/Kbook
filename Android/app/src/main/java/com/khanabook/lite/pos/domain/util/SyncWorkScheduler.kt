package com.khanabook.lite.pos.domain.util

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequest

fun WorkManager.enqueueMasterSyncOnce(request: OneTimeWorkRequest) {
    enqueueUniqueWork(
        SyncWorkNames.ONE_TIME,
        ExistingWorkPolicy.KEEP,
        request
    )
}

fun WorkManager.cancelMasterSyncWork() {
    cancelUniqueWork(SyncWorkNames.ONE_TIME)
    cancelUniqueWork(SyncWorkNames.PERIODIC)
}
