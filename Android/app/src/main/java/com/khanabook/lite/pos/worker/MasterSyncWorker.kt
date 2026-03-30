package com.khanabook.lite.pos.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.domain.manager.MasterSyncProcessor
import com.khanabook.lite.pos.domain.manager.SessionManager
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
        private val api: KhanaBookApi,
        private val masterSyncProcessor: MasterSyncProcessor
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
                      ExistingPeriodicWorkPolicy.KEEP,
                      syncRequest
              )
    }
  }

  override suspend fun doWork(): Result =
          withContext(Dispatchers.IO) {
            val token = sessionManager.getAuthToken()

            if (token.isNullOrBlank() || token == "null" || token.split(".").size != 3) {
              Log.w("MasterSyncWorker", "Aborting sync: No valid session token found.")
              return@withContext Result.success()
            }

            val deviceId = sessionManager.getDeviceId() ?: return@withContext Result.failure()
            val lastSyncTimestamp = sessionManager.getLastSyncTimestamp()

              try {
              Log.d("MasterSyncWorker", "Starting sync.")

              
              masterSyncProcessor.pushAll()

              
              Log.d("MasterSyncWorker", "Performing Master Pull")
              val masterData = api.pullMasterSync(lastSyncTimestamp, deviceId)

              
              masterSyncProcessor.insertMasterData(masterData)

              if (masterData.serverTimestamp > 0) {
                  sessionManager.saveLastSyncTimestamp(masterData.serverTimestamp)
              } else {
                  sessionManager.saveLastSyncTimestamp(System.currentTimeMillis())
              }
              Log.d("MasterSyncWorker", "Sync completed successfully")

              Result.success()
            } catch (e: Exception) {
              Log.e("MasterSyncWorker", "Sync failed: ${e.message}", e)
              
              
              if (e is HttpException) {
                  if (e.code() == 401 || e.code() == 403) {
                      return@withContext Result.failure()
                  }
              }
              
              if (runAttemptCount > 3) {
                  return@withContext Result.failure()
              }
              Result.retry()
            }
          }
}
