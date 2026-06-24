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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Singleton
class SyncManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val api: KhanaBookApi,
    private val masterSyncProcessor: MasterSyncProcessor
) {
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
            // ── NonCancellable Guard ───────────────────────────────────────────────
            // Wrap the entire push+pull cycle in NonCancellable so that lifecycle
            // cancellations (screen rotation, app backgrounded, ViewModel cleared)
            // cannot abort a transaction mid-flight. An interrupted push leaves the
            // server SyncTransaction uncommitted → next attempt gets HTTP 409.
            withContext(NonCancellable) {
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
                        return@withContext Result.failure(error)
                    }

                    // Only if pull fully succeeds is the checkpoint committed.
                    pullAndPersistMasterData(syncCheckpointTimestamp, deviceId)
                    Result.success(Unit)
                } catch (e: SyncConflictException) {
                    logWarn("Push conflict detected; pulling latest server data before resolving", e)
                    // ── Full Bootstrap Recovery Pull ───────────────────────────────────────
                    // CRITICAL: use timestamp=0 (bootstrap) instead of syncCheckpointTimestamp.
                    // The conflicting bills may have been synced BEFORE the checkpoint, so a
                    // delta pull would return an empty bill list → markBillsSyncedByLifetimeIds
                    // gets nothing to fix → the same bills get pushed again → infinite 409 loop.
                    // A full pull fetches ALL bills from the server so the orphaned local rows
                    // (isSynced=false but already on server) are correctly reconciled.
                    val recoverySucceeded = runCatching { pullAndPersistMasterData(0L, deviceId) }
                        .onFailure { pullError -> logError("Conflict recovery pull failed", pullError) }
                        .isSuccess
                    Result.failure(SyncConflictException(e, recoverySucceeded))
                } catch (e: Exception) {
                    if (e is HttpException && e.code() == 409) {
                        val errorBody = try { e.response()?.errorBody()?.string() } catch (ignored: Exception) { null }
                        logError("HTTP 409 Conflict body: $errorBody", e)
                        val recoverySucceeded = runCatching { pullAndPersistMasterData(0L, deviceId) }
                            .onFailure { pullError -> logError("Conflict recovery pull failed", pullError) }
                            .isSuccess
                        return@withContext Result.failure(SyncConflictException(e, recoverySucceeded))
                    }
                    logError("Full sync failed", e)
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun pushBillOnly(billLocalId: Long): Result<Unit> {
        return syncMutex.withLock {
            // NonCancellable: a single-bill push must complete atomically or not at all.
            // Partial pushes leave the server SyncTransaction open → 409 on next attempt.
            withContext(NonCancellable) {
                try {
                    masterSyncProcessor.pushSingleBill(billLocalId)
                    Result.success(Unit)
                } catch (e: Exception) {
                    logError("pushBillOnly failed for billId=$billLocalId", e)
                    Result.failure(e)
                }
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

    private suspend fun pullMasterSyncPages(
        lastSyncTimestamp: Long,
        deviceId: String
    ): List<MasterSyncResponse> {
        val isBootstrapPull = lastSyncTimestamp == 0L
        return listOf(api.pullMasterSync(lastSyncTimestamp, deviceId, ignoreDeviceId = isBootstrapPull))
    }

    private fun mergeMasterSyncPages(pages: List<MasterSyncResponse>): MasterSyncResponse {
        return pages.fold(MasterSyncResponse()) { acc, page ->
            MasterSyncResponse(
                serverTimestamp = maxOf(acc.serverTimestamp, page.serverTimestamp),
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
