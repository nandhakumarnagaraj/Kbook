package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.domain.util.SyncConflictException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

@Singleton
class SyncManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KhanaBookApi,
    private val masterSyncProcessor: MasterSyncProcessor
) {
    // Single mutex for ALL sync operations — full sync AND bill-only pushes.
    // Using separate mutexes caused pushBillOnly() and performFullSync() to race,
    // pushing the same bill items concurrently and triggering HTTP 409 on the server.
    private val syncMutex = Mutex()
    private val tag = "SyncManager"

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

    suspend fun performMasterPull(): Result<Unit> {
        return syncMutex.withLock {
            try {
                pullAndPersistMasterData(
                    lastSyncTimestamp = sessionManager.getLastSyncTimestamp(),
                    deviceId = sessionManager.getDeviceId()
                )
                Result.success(Unit)
            } catch (e: Exception) {
                logError("Master pull failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun performFullSync(): Result<Unit> {
        return syncMutex.withLock {
            val deviceId = sessionManager.getDeviceId()

            // ── Timestamp Race Fix (#2) ────────────────────────────────────────────
            // Capture the checkpoint BEFORE the sync cycle begins. This timestamp
            // is NOT written to persistent storage until the ENTIRE push+pull cycle
            // succeeds atomically in pullAndPersistMasterData(). If the push succeeds
            // but the pull fails, the checkpoint stays at the old value and the next
            // cycle re-pulls the missed window — no records are permanently lost.
            val syncCheckpointTimestamp = sessionManager.getLastSyncTimestamp()

            try {
                val pushSucceeded = masterSyncProcessor.pushAll()
                if (!pushSucceeded) {
                    val error = IllegalStateException("Push phase aborted before completion")
                    logWarn(error.message ?: "Push phase aborted")
                    return@withLock Result.failure(error)
                }

                // Only if pull fully succeeds is the checkpoint committed.
                pullAndPersistMasterData(syncCheckpointTimestamp, deviceId)
                Result.success(Unit)
            } catch (e: SyncConflictException) {
                logWarn("Push conflict detected; pulling latest server data before resolving", e)
                // Recovery pull uses the pre-sync checkpoint so we don't miss the conflicted window.
                val recoverySucceeded = runCatching { pullAndPersistMasterData(syncCheckpointTimestamp, deviceId) }
                    .onFailure { pullError -> logError("Conflict recovery pull failed", pullError) }
                    .isSuccess
                Result.failure(SyncConflictException(e, recoverySucceeded))
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 409) {
                    logWarn("HTTP 409 during sync; treating as conflict", e)
                    val recoverySucceeded = runCatching { pullAndPersistMasterData(syncCheckpointTimestamp, deviceId) }
                        .onFailure { pullError -> logError("Conflict recovery pull failed", pullError) }
                        .isSuccess
                    return@withLock Result.failure(SyncConflictException(e, recoverySucceeded))
                }
                logError("Full sync failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun pushBillOnly(billLocalId: Long): Result<Unit> {
        return syncMutex.withLock {
            try {
                masterSyncProcessor.pushSingleBill(billLocalId)
                Result.success(Unit)
            } catch (e: Exception) {
                logError("pushBillOnly failed for billId=$billLocalId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun pushUnsyncedDataImmediately(): Boolean {
        return performFullSync().isSuccess
    }

    suspend fun pushUnsyncedDataWithResult(): Result<Unit> {
        return performFullSync()
    }

    /**
     * Pulls all master data since [lastSyncTimestamp] and persists it atomically.
     * The [sessionManager.saveLastSyncTimestamp] call happens LAST — after Room
     * has fully committed all pulled records — ensuring the checkpoint only advances
     * when we have a complete, consistent local state.
     */
    private suspend fun pullAndPersistMasterData(lastSyncTimestamp: Long, deviceId: String) {
        val mergedResponse = mergeMasterSyncPages(pullMasterSyncPages(lastSyncTimestamp, deviceId))
        // Persist all pulled records into Room (wrapped in a DB transaction in MasterSyncProcessor).
        masterSyncProcessor.insertMasterData(mergedResponse)
        // Commit the new checkpoint ONLY after Room write fully succeeds.
        if (mergedResponse.serverTimestamp > 0) {
            sessionManager.saveLastSyncTimestamp(mergedResponse.serverTimestamp)
        } else {
            throw IllegalStateException("Master sync response missing server timestamp")
        }
    }

    // Default page size for paginated master sync pulls.
    // A larger limit reduces round-trips for medium-sized restaurants;
    // the server enforces this as a cap to prevent unbounded response sizes.
    private companion object {
        private const val PAGE_SIZE = 5000
        private const val MAX_PAGES = 20 // safety limit: 5000 * 20 = 100k records per entity type
    }

    private suspend fun pullMasterSyncPages(
        lastSyncTimestamp: Long,
        deviceId: String
    ): List<MasterSyncResponse> {
        val pages = mutableListOf<MasterSyncResponse>()
        var offset = 0
        var pageCount = 0
        do {
            val page = api.pullMasterSync(
                lastSyncTimestamp = lastSyncTimestamp,
                deviceId = deviceId,
                limit = PAGE_SIZE,
                offset = offset
            )
            pages.add(page)
            offset += PAGE_SIZE
            pageCount++
        } while (page.hasMore && pageCount < MAX_PAGES)
        if (pageCount >= MAX_PAGES) {
            logWarn("Master sync pagination reached MAX_PAGES=$MAX_PAGES; data may be truncated")
        }
        return pages
    }

    private fun mergeMasterSyncPages(pages: List<MasterSyncResponse>): MasterSyncResponse {
        return pages.fold(MasterSyncResponse()) { acc, page ->
            MasterSyncResponse(
                serverTimestamp = maxOf(acc.serverTimestamp, page.serverTimestamp),
                hasMore = false, // merged response is complete, no more pages needed
                profiles = acc.profiles + page.profiles,
                users = acc.users + page.users,
                categories = acc.categories + page.categories,
                menuItems = acc.menuItems + page.menuItems,
                itemVariants = acc.itemVariants + page.itemVariants,
                stockLogs = acc.stockLogs + page.stockLogs,
                bills = acc.bills + page.bills,
                billItems = acc.billItems + page.billItems,
                billPayments = acc.billPayments + page.billPayments
            )
        }
    }
}
