package com.khanabook.lite.pos.ui.feedback

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class MenuFeedbackSettings(
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = false
)

class MenuFeedbackPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun read(): MenuFeedbackSettings = MenuFeedbackSettings(
        soundEnabled = preferences.getBoolean(KEY_SOUND_ENABLED, true),
        hapticEnabled = preferences.getBoolean(KEY_HAPTIC_ENABLED, false)
    )

    fun setSoundEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun setHapticEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    internal fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    internal fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val PREFERENCES_NAME = "menu_feedback_preferences"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    }
}

@Composable
fun rememberMenuFeedbackPreferences(): MenuFeedbackPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        MenuFeedbackPreferences(context)
    }
}

@Composable
fun rememberMenuFeedbackSettings(
    preferences: MenuFeedbackPreferences
): State<MenuFeedbackSettings> {
    val settings = remember(preferences) {
        mutableStateOf(preferences.read())
    }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings.value = preferences.read()
        }
        preferences.registerListener(listener)
        onDispose {
            preferences.unregisterListener(listener)
        }
    }

    return settings
}
