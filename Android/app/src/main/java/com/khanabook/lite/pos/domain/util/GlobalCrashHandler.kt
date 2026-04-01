package com.khanabook.lite.pos.domain.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.ui.MainActivity
import kotlin.system.exitProcess

class GlobalCrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

  private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    Log.e("KhanaBookCrash", "CRITICAL: Uncaught exception in thread ${thread.name}", throwable)

    // Only persist stack traces in debug builds — stack traces may contain internal paths
    if (BuildConfig.DEBUG) {
        try {
            saveCrashLog(Log.getStackTraceString(throwable))
        } catch (e: Exception) {
            Log.e("KhanaBookCrash", "Secondary error in saveCrashLog: ${e.message}")
        }
    }

    // Crash loop detection — fall back to system handler if crashing repeatedly
    try {
        val prefs = context.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCrashTime = prefs.getLong("last_crash_time", 0L)
        val crashCount = prefs.getInt("crash_count", 0)

        val newCrashCount = if (now - lastCrashTime < 10_000L) crashCount + 1 else 1

        prefs.edit().apply {
            putLong("last_crash_time", now)
            putInt("crash_count", newCrashCount)
            apply()
        }

        if (newCrashCount >= 3) {
            Log.e("KhanaBookCrash", "Crash loop detected — delegating to system handler.")
            prefs.edit().putInt("crash_count", 0).apply()
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }
    } catch (e: Exception) {
        Log.e("KhanaBookCrash", "Error in crash loop detection: ${e.message}")
        defaultHandler?.uncaughtException(thread, throwable)
        return
    }

    // OOM and SOE cannot be recovered from — let the system handle them
    if (throwable is OutOfMemoryError || throwable is StackOverflowError) {
        defaultHandler?.uncaughtException(thread, throwable)
        return
    }

    try {
      restartApp()
    } catch (e: Exception) {
      defaultHandler?.uncaughtException(thread, throwable)
    }
  }

  private fun saveCrashLog(stackTrace: String) {
    try {
      val prefs = context.getSharedPreferences("crash_reports", Context.MODE_PRIVATE)
      prefs.edit().apply {
        putString("last_crash_log", stackTrace)
        putLong("last_crash_time", System.currentTimeMillis())
        apply()
      }
    } catch (e: Exception) {
      Log.e("KhanaBookCrash", "Failed to save crash log", e)
    }
  }

  private fun restartApp() {
    val intent = Intent(context, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
    exitProcess(10)
  }

  companion object {
    fun initialize(context: Context) {
      Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(context))
    }
  }
}
