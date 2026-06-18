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
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker (network required).
 *
 * Runs every [REPEAT_MINUTES] min. Note: Android's WorkManager enforces a 15-minute floor on
 * periodic work, so anything smaller is silently clamped to 15 — we set 15 explicitly to match
 * reality. The 60-min auto-cancel timeout below is unaffected by the polling cadence.
 *
 * For every Easebuzz draft bill older than 2 minutes:
 *   - "paid"    → markBillCompleted + sync
 *   - "failed"  → cancelOrder("Payment failed") + sync
 *   - "pending" → if older than 60 min → cancelOrder("Payment timeout"), else skip
 */
@HiltWorker
class EasebuzzPaymentRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val billRepository: BillRepository,
    private val khanaBookApi: KhanaBookApi,
    private val sessionManager: SessionManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "EasebuzzRecovery"
        private const val WORK_NAME = "EasebuzzPaymentRecoveryWorker"

        /** Bills unpaid for >60 min are auto-cancelled. */
        private const val TIMEOUT_MINUTES = 60L

        /** WorkManager's minimum periodic interval; smaller values are clamped to this. */
        private const val REPEAT_MINUTES = 15L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<EasebuzzPaymentRecoveryWorker>(
                repeatInterval = REPEAT_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Scheduled payment recovery worker (every $REPEAT_MINUTES min)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val deviceId = sessionManager.getDeviceId()
            val pendingDrafts = billRepository.getPendingEasebuzzDrafts(olderThanMinutes = 2)

            if (pendingDrafts.isEmpty()) {
                Log.d(TAG, "No pending Easebuzz drafts to check")
                return Result.success()
            }

            Log.d(TAG, "Checking ${pendingDrafts.size} pending Easebuzz draft(s)")

            val timeoutMs = TIMEOUT_MINUTES * 60 * 1000L
            val now = System.currentTimeMillis()

            for (bill in pendingDrafts) {
                try {
                    val statusResponse = khanaBookApi.getEasebuzzPaymentStatus(
                        billId = bill.id,
                        deviceId = deviceId,
                        refresh = true
                    )
                    val status = statusResponse.paymentStatus?.lowercase() ?: "pending"

                    when {
                        status == "paid" || status == "success" || status == "completed" -> {
                            Log.i(TAG, "Bill #${bill.id} payment CONFIRMED — marking completed")
                            billRepository.markBillCompleted(bill.id)
                        }

                        status == "failed" || status == "cancelled" -> {
                            Log.w(TAG, "Bill #${bill.id} payment FAILED — cancelling")
                            billRepository.cancelOrder(bill.id, "Payment failed via Easebuzz")
                        }

                        (now - bill.createdAt) > timeoutMs -> {
                            Log.w(TAG, "Bill #${bill.id} timed out after $TIMEOUT_MINUTES min — cancelling")
                            billRepository.cancelOrder(bill.id, "Payment not completed within $TIMEOUT_MINUTES minutes")
                        }

                        else -> {
                            Log.d(TAG, "Bill #${bill.id} still pending (${(now - bill.createdAt) / 60000} min old) — will retry")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check bill #${bill.id}: ${e.message}")
                    // Don't fail entire worker for one bill
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Recovery worker failed: ${e.message}")
            Result.retry()
        }
    }
}
