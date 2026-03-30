package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KhanaBookApi,
    private val masterSyncProcessor: MasterSyncProcessor
) {

    suspend fun performMasterPull(): Result<Unit> {
        val deviceId = sessionManager.getDeviceId() ?: return Result.failure(Exception("No device ID"))
        val lastSyncTimestamp = sessionManager.getLastSyncTimestamp()

        return try {
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
    
    suspend fun pushUnsyncedDataImmediately(): Boolean {
        return masterSyncProcessor.pushAll()
    }
}
