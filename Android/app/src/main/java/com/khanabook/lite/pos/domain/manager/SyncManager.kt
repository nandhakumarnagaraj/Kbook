package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SyncManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KhanaBookApi,
    private val masterSyncProcessor: MasterSyncProcessor
) {
    private val syncMutex = Mutex()

    suspend fun performMasterPull(): Result<Unit> {
        return syncMutex.withLock {
            val deviceId = sessionManager.getDeviceId()
            val lastSyncTimestamp = sessionManager.getLastSyncTimestamp()

            try {
                val masterData = api.pullMasterSync(lastSyncTimestamp, deviceId)
                masterSyncProcessor.insertMasterData(masterData)
                if (masterData.serverTimestamp > 0) {
                    sessionManager.saveLastSyncTimestamp(masterData.serverTimestamp)
                } else {
                    sessionManager.saveLastSyncTimestamp(System.currentTimeMillis())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SyncManager", "Master pull failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun performFullSync(): Result<Unit> {
        return syncMutex.withLock {
            val deviceId = sessionManager.getDeviceId()
            val lastSyncTimestamp = sessionManager.getLastSyncTimestamp()

            try {
                val pushSucceeded = masterSyncProcessor.pushAll()
                if (!pushSucceeded) {
                    val error = IllegalStateException("Push phase aborted before completion")
                    Log.w("SyncManager", error.message ?: "Push phase aborted")
                    return@withLock Result.failure(error)
                }

                val masterData = api.pullMasterSync(lastSyncTimestamp, deviceId)
                masterSyncProcessor.insertMasterData(masterData)
                if (masterData.serverTimestamp > 0) {
                    sessionManager.saveLastSyncTimestamp(masterData.serverTimestamp)
                } else {
                    sessionManager.saveLastSyncTimestamp(System.currentTimeMillis())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("SyncManager", "Full sync failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun pushUnsyncedDataImmediately(): Boolean {
        return performFullSync().isSuccess
    }
}
