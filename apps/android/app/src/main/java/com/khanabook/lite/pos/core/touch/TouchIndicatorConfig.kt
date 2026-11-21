package com.khanabook.lite.pos.core.touch

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * Visual and runtime configuration for the in-app "Show Taps" indicator.
 *
 * WHY: Screen recordings and demos need a touch visualiser that works without Developer Options
 * or SYSTEM_ALERT_WINDOW. The flag is persisted in `session_prefs`, the same file the Activity
 * already uses for `display_scale`, so no new storage abstraction is introduced.
 *
 * LIFECYCLE: [initialize] runs once from [TouchIndicatorHost.attach] before the first touch is
 * forwarded. It also registers a SharedPreferences listener so a programmatic preference change
 * is mirrored here immediately.
 *
 * TWO VIEWS OF THE FLAG:
 * - [isEnabled] is a volatile JVM field read on every ACTION_MOVE; it deliberately stays outside
 *   Compose so the high-frequency touch path never schedules a recomposition.
 * - [enabledState] is a Compose `State` used by dialog overlays, so a preference change updates
 *   any already-open dialog without a restart.
 *
 * DEFAULT: off in every build. There is deliberately no UI switch; a demo enables it by calling
 * [setEnabled], which is the only writer of the preference key.
 */
object TouchIndicatorConfig {

    private const val PREFS_NAME = "session_prefs"
    const val PREF_KEY_SHOW_TAPS = "show_touch_indicator"
    private const val DEFAULT_ENABLED = false

    @Volatile
    var isEnabled: Boolean = false
        private set

    private val _enabledState = mutableStateOf(false)
    val enabledState: State<Boolean> get() = _enabledState

    /** Mutable so a demo can change the look at runtime; the View reads it during onDraw. */
    var style: TouchDotStyle = TouchDotStyle()

    private var registeredPrefs: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /**
     * Loads the persisted toggle. Defaults to false so ordinary use never shows touch dots; a demo
     * opts in explicitly via [setEnabled].
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        applyValue(prefs.getBoolean(PREF_KEY_SHOW_TAPS, DEFAULT_ENABLED))
        registerListener(prefs)
    }

    /**
     * Toggles the indicator and persists the choice. Takes effect on the next touch because the
     * host checks [isEnabled] before forwarding each event, and on open dialogs immediately via
     * [enabledState].
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        applyValue(enabled)
        prefs(context).edit().putBoolean(PREF_KEY_SHOW_TAPS, enabled).apply()
    }

    private fun applyValue(value: Boolean) {
        isEnabled = value
        _enabledState.value = value
    }

    /** One listener for the application lifetime; `attach()` may run again after recreation. */
    private fun registerListener(prefs: SharedPreferences) {
        if (registeredPrefs === prefs && preferenceListener != null) return
        preferenceListener?.let { registeredPrefs?.unregisterOnSharedPreferenceChangeListener(it) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == null || key == PREF_KEY_SHOW_TAPS) {
                applyValue(changedPrefs.getBoolean(PREF_KEY_SHOW_TAPS, DEFAULT_ENABLED))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        registeredPrefs = prefs
        preferenceListener = listener
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Styling for one touch dot. All sizes are in **dp**; the View converts them once using the
 * current display density.
 *
 * @property radiusDp radius of the solid dot.
 * @property fillArgb ARGB fill, e.g. `0x66F0C040` for translucent KhanaBook LightGold.
 * @property borderArgb ARGB stroke colour; set [borderWidthDp] to 0 to disable the stroke.
 * @property borderWidthDp stroke width in dp.
 * @property glowArgb ARGB colour of the larger, fainter halo drawn behind the dot.
 * @property glowScale halo radius as a multiple of [radiusDp].
 * @property fadeDurationMs time from release to fully transparent.
 */
data class TouchDotStyle(
    val radiusDp: Float = 18f,
    val fillArgb: Int = 0x66F0C040,
    val borderArgb: Int = 0x99FFFFFF.toInt(),
    val borderWidthDp: Float = 1.5f,
    val glowArgb: Int = 0x22F0C040,
    val glowScale: Float = 1.6f,
    val fadeDurationMs: Long = 220L,
)
