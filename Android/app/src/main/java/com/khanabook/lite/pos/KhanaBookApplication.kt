package com.khanabook.lite.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.khanabook.lite.pos.di.SessionManagerEntryPoint
import com.khanabook.lite.pos.domain.manager.KitchenPrintQueueManager
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.GlobalCrashHandler
import com.khanabook.lite.pos.worker.MasterSyncWorker
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KhanaBookApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var kitchenPrintQueueManager: KitchenPrintQueueManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        // Load SQLCipher native library BEFORE super.onCreate() since Hilt injection
        // opens the database during super.onCreate() and needs native libs available
        System.loadLibrary("sqlcipher")

        super.onCreate()

        GlobalCrashHandler.initialize(this)
        AppAssetStore.initialize(this)

        // Eagerly generate and persist the device ID so sync workers always have it
        EntryPointAccessors.fromApplication(this, SessionManagerEntryPoint::class.java)
            .sessionManager()
            .getDeviceId()

        kitchenPrintQueueManager.initialize()
        MasterSyncWorker.schedule(this)
    }
}
