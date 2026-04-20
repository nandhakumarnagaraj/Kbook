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
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.SyncManager
import com.khanabook.lite.pos.domain.util.SyncConflictException
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
  private val tag = "MasterSyncWorker"

  private fun logInfo(message: String) {
    if (BuildConfig.DEBUG) {
      Log.i(tag, message)
    }
  }

  private fun logWarn(message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) {
      if (throwable != null) {
        Log.w(tag, message, throwable)
      } else {
        Log.w(tag, message)
      }
    } else {
      Log.w(tag, message)
    }
  }

  private fun logError(message: String, throwable: Throwable? = null) {
    if (BuildConfig.DEBUG) {
      if (throwable != null) {
        Log.e(tag, message, throwable)
      } else {
        Log.e(tag, message)
      }
    } else {
      Log.e(tag, message)
    }
  }

  companion object {
    private const val SYNC_WORK_NAME = "MasterSyncWorker"

    fun schedule(context: Context) {
      val constraints = Constraints.Builder()
              .setRequiredNetworkType(NetworkType.CONNECTED)
              .setRequiresBatteryNotLow(true)
              .build()

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
      logWarn("Aborting sync: No valid session token found.")
      return@withContext Result.success()
    }

    sessionManager.getDeviceId()

    syncManager.performFullSync().fold(
        onSuccess = {
          logInfo("Sync completed successfully")
          Result.success()
        },
        onFailure = { e ->
          logError("Sync failed", e)

          if (e is HttpException) {
            if (e.code() == 401) {
              sessionManager.invalidateAuthSession()
              return@withContext Result.failure()
            }
            if (e.code() == 403) {
              return@withContext Result.failure()
            }
          }

          if (e is SyncConflictException) {
            return@withContext Result.failure()
          }

          if (runAttemptCount > 3) {
            return@withContext Result.failure()
          }
          Result.retry()
        }
    )
  }
}
