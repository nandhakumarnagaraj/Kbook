package com.khanabook.lite.pos.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@HiltWorker
class MasterSyncWorker
@AssistedInject
constructor(
        @Assisted private val context: Context,
        @Assisted private val workerParams: WorkerParameters,
        private val sessionManager: SessionManager,
        private val syncManager: SyncManager
) : CoroutineWorker(context, workerParams) {

  companion object {
    private const val SYNC_WORK_NAME = "MasterSyncWorker"

    fun schedule(context: Context) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

      val syncRequest =
              PeriodicWorkRequestBuilder<MasterSyncWorker>(
                              15,
                              TimeUnit.MINUTES
                      )
                      .setConstraints(constraints)
                      .setBackoffCriteria(
                          androidx.work.BackoffPolicy.EXPONENTIAL,
                          1,
                          TimeUnit.MINUTES
                      )
                      .build()

      WorkManager.getInstance(context)
              .enqueueUniquePeriodicWork(
                      SYNC_WORK_NAME,
                      ExistingPeriodicWorkPolicy.UPDATE,
                      syncRequest
              )
    }
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val token = sessionManager.getAuthToken()

    if (token.isNullOrBlank()) {
      Log.w("MasterSyncWorker", "Aborting sync: No valid session token found.")
      return@withContext Result.success()
    }

    sessionManager.getDeviceId()

    syncManager.performFullSync().fold(
        onSuccess = {
          Log.i("MasterSyncWorker", "Sync completed successfully")
          Result.success()
        },
        onFailure = { e ->
          Log.e("MasterSyncWorker", "Sync failed: ${e.message}", e)

          if (e is HttpException) {
            if (e.code() == 401) {
              sessionManager.clearSession()
              return@withContext Result.failure()
            }
            if (e.code() == 403) {
              return@withContext Result.failure()
            }
          }

          if (runAttemptCount > 3) {
            return@withContext Result.failure()
          }
          Result.retry()
        }
    )
  }
}
