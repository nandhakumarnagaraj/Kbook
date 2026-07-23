package com.khanabook.lite.pos.test.util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.WorkManager

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, dagger.hilt.android.testing.HiltTestApplication::class.java.name, context)
    }

    override fun callApplicationOnCreate(app: Application) {
        System.loadLibrary("sqlcipher")
        try {
            WorkManager.getInstance(app)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(app, Configuration.Builder().build())
        }
        super.callApplicationOnCreate(app)
    }
}
