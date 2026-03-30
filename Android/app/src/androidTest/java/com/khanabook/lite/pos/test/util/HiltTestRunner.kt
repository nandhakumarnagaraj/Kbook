package com.khanabook.lite.pos.test.util

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, "com.khanabook.lite.pos.test.util.HiltTestApp", context)
    }
}
