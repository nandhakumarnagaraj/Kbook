package com.khanabook.lite.pos

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.GlobalCrashHandler
import com.khanabook.lite.pos.util.FlashlightInitializer
import com.khanabook.lite.pos.worker.MasterSyncWorker
import com.khanabook.lite.pos.worker.EasebuzzPaymentRecoveryWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KhanaBookApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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

        FlashlightInitializer.initialize(this)
        GlobalCrashHandler.initialize(this)
        AppAssetStore.initialize(this)

        // Initialize notification channels for background pushes
        com.khanabook.lite.pos.worker.NotificationHelper.createChannels(this)

        MasterSyncWorker.schedule(this)
        EasebuzzPaymentRecoveryWorker.schedule(this)
    }
}
