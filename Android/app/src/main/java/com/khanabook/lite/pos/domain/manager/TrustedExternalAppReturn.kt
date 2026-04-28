package com.khanabook.lite.pos.domain.manager

import android.content.Context

object TrustedExternalAppReturn {
    private const val PREFS_NAME = "trusted_external_return"
    private const val KEY_PENDING = "pending"

    fun mark(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .apply()
    }

    fun consume(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_PENDING, false)
        if (pending) {
            prefs.edit().remove(KEY_PENDING).apply()
        }
        return pending
    }
}
