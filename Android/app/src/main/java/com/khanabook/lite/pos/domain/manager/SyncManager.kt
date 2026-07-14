package com.khanabook.lite.pos.domain.manager

import android.util.Log
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.data.local.dao.*
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.remote.api.MasterSyncResponse
import com.khanabook.lite.pos.domain.util.SyncConflictException
import com.khanabook.lite.pos.domain.model.TerminalIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val stateMutex = Mutex()
    private var isSyncing = false
    private var hasPendingSync = false

    fun triggerImmediateSync() {
        syncScope.launch {
            performFullSync()
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

    private suspend fun ensureTerminalActivated() {
        if (sessionManager.getRestaurantId() <= 0L) return
        if (!sessionManager.isTerminalReady()) {
            try {
                Log.i(tag, "Activating terminal series for device...")
                val deviceId = sessionManager.getDeviceId()
                val response = api.activateTerminal(com.khanabook.lite.pos.data.remote.api.TerminalActivationRequest(deviceId))
                val terminalId = response.terminalId?.takeIf { it.isNotBlank() }
                    ?: response.terminalSeries
                sessionManager.saveTerminalIdentity(
                    TerminalIdentity(
                        restaurantId = sessionManager.getRestaurantId(),
                        terminalId = terminalId,
                        deviceId = deviceId,
                        terminalName = response.terminalName,
                        terminalSeries = response.terminalSeries,
                        isActive = response.isActive ?: true,
                        registeredAt = response.registeredAt,
                        lastVerifiedAt = response.lastVerifiedAt ?: System.currentTimeMillis(),
                        terminalToken = response.terminalToken
                    )
                )
                Log.i(tag, "Terminal activated with series: ${response.terminalSeries}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to activate terminal series", e)
                throw e
            }
        }
    }

    suspend fun performMasterPull(): Result<Unit> {
        return syncMutex.withLock {
            try {
                ensureTerminalActivated()
                // Re-label this terminal's own synced bills (corrects the v59→60 migration's
                // conservative backfill now that the authoritative terminal is known).
                masterSyncProcessor.reconcileLocalBillScope()
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
        stateMutex.withLock {
            if (isSyncing) {
                hasPendingSync = true
                Log.d(tag, "Sync already in progress. Queued a follow-up run.")
                return Result.success(Unit)
            }
            isSyncing = true
        }

        try {
            var result: Result<Unit> = Result.success(Unit)
            do {
                stateMutex.withLock {
                    hasPendingSync = false
                }
                result = syncMutex.withLock {
                    runSyncCycle()
                }
            } while (stateMutex.withLock { hasPendingSync })
            return result
        } finally {
            stateMutex.withLock {
                isSyncing = false
            }
        }
    }

    private suspend fun runSyncCycle(): Result<Unit> {
        // ── NonCancellable Guard ───────────────────────────────────────────────
        // Wrap the entire push+pull cycle in NonCancellable so that lifecycle
        // cancellations (screen rotation, app backgrounded, ViewModel cleared)
        // cannot abort a transaction mid-flight. An interrupted push leaves the
        // server SyncTransaction uncommitted → next attempt gets HTTP 409.
        return withContext(NonCancellable) {
            val deviceId = sessionManager.getDeviceId()
            val startedAt = System.currentTimeMillis()

            // ── Timestamp Race Fix (#2) ────────────────────────────────────────────
            // Capture the checkpoint BEFORE the sync cycle begins. This timestamp
            // is NOT written to persistent storage until the ENTIRE push+pull cycle
            // succeeds atomically in pullAndPersistMasterData(). If the push succeeds
            // but the pull fails, the checkpoint stays at the old value and the next
            // cycle re-pulls the missed window — no records are permanently lost.
            val syncCheckpointTimestamp = sessionManager.getLastSyncTimestamp()

            try {
                ensureTerminalActivated()
                val pushSucceeded = masterSyncProcessor.pushAll()
                if (!pushSucceeded) {
                    val error = IllegalStateException("Push phase aborted before completion")
                    logWarn(error.message ?: "Push phase aborted")
                    return@withContext Result.failure(error)
                }

                // Only if pull fully succeeds is the checkpoint committed.
                pullAndPersistMasterData(syncCheckpointTimestamp, deviceId)
                Log.i(tag, "Full sync completed in ${System.currentTimeMillis() - startedAt}ms")
                Result.success(Unit)
            } catch (e: SyncConflictException) {
                logWarn("Push conflict detected; pulling latest server data before resolving", e)
                handleRecoveredConflict(deviceId, e)
            } catch (e: Exception) {
                if (e is HttpException && e.code() == 409) {
                    val errorBody = try { e.response()?.errorBody()?.string() } catch (ignored: Exception) { null }
                    logError("HTTP 409 Conflict body: $errorBody", e)
                    return@withContext handleRecoveredConflict(deviceId, SyncConflictException(e).withRecoveryStatus(false))
                }
                logError("Full sync failed after ${System.currentTimeMillis() - startedAt}ms", e)
                Result.failure(e)
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
        val pages = pullMasterSyncPages(lastSyncTimestamp, deviceId)
        val mergedResponse = mergeMasterSyncPages(pages)
        Log.i(
            tag,
            "Master pull received pages=${pages.size}, payloadRecords=${countPayloadRecords(mergedResponse)}, serverTimestamp=${mergedResponse.serverTimestamp}"
        )
        // Persist all pulled records into Room (wrapped in a DB transaction in MasterSyncProcessor).
        masterSyncProcessor.insertMasterData(mergedResponse)
        // Commit the new checkpoint ONLY after Room write fully succeeds.
        if (mergedResponse.serverTimestamp > 0) {
            sessionManager.saveLastSyncTimestamp(mergedResponse.serverTimestamp)
        } else {
            throw IllegalStateException("Master sync response missing server timestamp")
        }
    }

    private suspend fun recoverFromSyncConflict(deviceId: String): Boolean {
        // Use timestamp=0 so already-saved server rows are pulled back even if they
        // were written before the current local checkpoint. This is what breaks
        // repeated 409 / failedLocalIds push loops.
        return runCatching { pullAndPersistMasterData(0L, deviceId) }
            .onFailure { pullError -> logError("Conflict recovery pull failed", pullError) }
            .isSuccess
    }

    private suspend fun handleRecoveredConflict(
        deviceId: String,
        exception: SyncConflictException
    ): Result<Unit> {
        if (!recoverFromSyncConflict(deviceId)) {
            return Result.failure(exception.withRecoveryStatus(false))
        }

        return runCatching { masterSyncProcessor.pushAll() }
            .fold(
                onSuccess = { pushSucceeded ->
                    if (pushSucceeded) Result.success(Unit)
                    else Result.failure(IllegalStateException("Post-recovery push returned false"))
                },
                onFailure = { retryError ->
                    logError("Post-recovery push retry failed", retryError)
                    if (retryError is SyncConflictException) {
                        val quarantined = masterSyncProcessor.quarantineFailedSyncRecords(retryError)
                        Log.w(tag, "Post-recovery quarantine count=$quarantined")
                        if (quarantined > 0) {
                            return Result.success(Unit)
                        }
                        return Result.failure(retryError.withRecoveryStatus(true))
                    }
                    Result.failure(retryError)
                }
            )
    }

    private suspend fun pullMasterSyncPages(
        lastSyncTimestamp: Long,
        deviceId: String
    ): List<MasterSyncResponse> {
        val terminalId = sessionManager.getTerminalId() ?: sessionManager.getTerminalSeries()
        val pages = mutableListOf<MasterSyncResponse>()
        var page = 0
        var hasMore = true
        while (hasMore) {
            val response = api.pullMasterSync(
                lastSyncTimestamp = lastSyncTimestamp,
                deviceId = deviceId,
                terminalId = terminalId,
                ignoreDeviceId = false,
                page = page,
                size = 500
            )
            pages.add(response)
            hasMore = response.hasMore ?: false
            page++
        }
        return pages
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

    private fun countPayloadRecords(response: MasterSyncResponse): Int =
        response.profiles.size +
            response.users.size +
            response.categories.size +
            response.menuItems.size +
            response.itemVariants.size +
            response.stockLogs.size +
            response.bills.size +
            response.billItems.size +
            response.billPayments.size

    private fun SyncConflictException.withRecoveryStatus(recovered: Boolean): SyncConflictException {
        return SyncConflictException(
            this,
            recoverySucceeded = recovered,
            failedLocalIds = failedLocalIds,
            failedReasons = failedReasons,
            syncEntityLabel = syncEntityLabel
        )
    }
}

enum class SyncStep(val displayName: String) {
    PushProfiles("Uploading Profiles..."),
    PushUsers("Uploading Staff Users..."),
    PushCategories("Uploading Categories..."),
    PushMenuItems("Uploading Menu Items..."),
    PushItemVariants("Uploading Item Variants..."),
    PushStockLogs("Uploading Stock Logs..."),
    PushBills("Uploading Bills..."),
    PushBillItems("Uploading Bill Items..."),
    PushBillPayments("Uploading Payments..."),
    PullMasterData("Downloading Restaurant Data...")
}
