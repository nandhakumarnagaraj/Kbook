package com.khanabook.lite.pos.core.designsystem

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/** Length of the fade used in place of transforms and loops when motion is reduced. */
const val REDUCED_MOTION_MS = 120

/**
 * True when Android is set to remove animations (Developer options → animation and
 * transition duration scale = 0). The settings are observed, so toggling the OS
 * option while the app is open takes effect on the next recomposition.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val reduced = remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = readReducedMotion(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE), false, observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced.value
}

private fun readReducedMotion(resolver: ContentResolver): Boolean {
    val animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val transitionScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    return animatorScale == 0f || transitionScale == 0f
}

/** Collapses a normal duration to the shared fade length when motion is reduced. */
@Composable
fun motionDuration(normalMs: Int): Int = if (rememberReducedMotion()) REDUCED_MOTION_MS else normalMs

/**
 * Short, origin-linked entrance for dialog surfaces: a 0.94 → 1 scale and fade over
 * 180 ms, with the pivot supplied by the caller (the row that opened the dialog).
 *
 * With reduced motion the scale is dropped and only the 120 ms fade remains.
 */
@Composable
fun Modifier.dialogEntrance(
    origin: TransformOrigin = TransformOrigin.Center
): Modifier {
    val reduced = rememberReducedMotion()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduced) REDUCED_MOTION_MS else 180,
            easing = FastOutSlowInEasing
        ),
        label = "dialog_entrance"
    )
    return graphicsLayer {
        alpha = progress
        scaleX = if (reduced) 1f else 0.94f + 0.06f * progress
        scaleY = if (reduced) 1f else 0.94f + 0.06f * progress
        transformOrigin = origin
    }
}
