package com.khanabook.lite.pos

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.khanabook.lite.pos.data.repository.BillRepository
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.GlobalCrashHandler
import com.khanabook.lite.pos.worker.MasterSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KhanaBookApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var billRepository: BillRepository
    @Inject lateinit var sessionManager: com.khanabook.lite.pos.domain.manager.SessionManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    companion object {
        private var sqlCipherLoaded = false
        init {
            try {
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.e("KhanaBookApp", "SQLCipher native library failed to load", e)
            }
        }
    }

    override fun onCreate() {
        if (!sqlCipherLoaded) {
            Log.w("KhanaBookApp", "SQLCipher not available — app may crash on DB access")
        }
        super.onCreate()

        GlobalCrashHandler.initialize(this)
        AppAssetStore.initialize(this)

        // WorkManager is configured via Configuration.Provider above.
        // The auto-init ContentProvider is disabled in the manifest, so
        // WorkManager.getInstance() called inside schedule() triggers
        // deferred initialization using the HiltWorkerFactory.
        MasterSyncWorker.schedule(this)

        // ── Self-healing: Log stale pending drafts on startup ───────────────
        // Logs any DRAFT+PENDING bills from a prior session so unresolved UPI
        // payments are visible in the logs for diagnosis. Does NOT auto-cancel
        // because active dine-in drafts (table orders) also appear as DRAFT+PENDING
        // and must not be silently removed. Stale drafts are handled by the
        // existing cancelStalePendingOnlineDrafts() call inside createDraftOnlineBill()
        // when a new online payment attempt begins.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(10_000) // Wait 10s for Hilt + DB init
                if (sessionManager.canUsePos() && sessionManager.getRestaurantId() > 0L) {
                    val count = billRepository.getPendingOnlineBillsFlow()
                        .first()
                        .size
                    if (count > 0) {
                        Log.i("KhanaBookApp", "Self-heal: $count pending draft(s) found on startup (will be cleaned on next payment attempt)")
                    }
                }
            } catch (e: Exception) {
                Log.w("KhanaBookApp", "Self-heal startup: delayed check failed (not critical)", e)
            }
        }
    }
}
