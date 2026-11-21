package com.khanabook.lite.pos.core.touch

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Drop-in touch indicator for surfaces that live in their own window (Compose `Dialog`,
 * `AlertDialog`, `ModalBottomSheet`) and therefore never receive events through
 * [TouchIndicatorHost].
 *
 * PLACEMENT: add as the last child of a `Box` that wraps the dialog content, e.g.
 * `TouchIndicatorOverlay(Modifier.matchParentSize())`. The dot is local to that composition and
 * sits above the dialog content.
 *
 * PASS-THROUGH: the observer uses the Initial pass and never calls `PointerInputChange.consume()`.
 * Compose propagates pointer events through every hit composable, and consumption is the only
 * mechanism that asks later handlers to ignore an event - observing in the Initial pass without
 * consuming cannot block taps, scrolling or gestures behind the overlay.
 *
 * PERFORMANCE: the draw target is an Android [TouchIndicatorView], not Compose state, so a
 * high-frequency ACTION_MOVE stream never triggers recomposition of the dialog or its siblings.
 * Only the on/off preference is read as Compose state, so flipping the Settings switch appears
 * in an already-open dialog too.
 */
@Composable
fun TouchIndicatorOverlay(modifier: Modifier = Modifier) {
    if (!TouchIndicatorConfig.enabledState.value) return

    val context = LocalContext.current
    val indicator = remember { TouchIndicatorView(context) }
    DisposableEffect(indicator) {
        onDispose { indicator.shutdown() }
    }

    // The caller controls sizing (typically Modifier.matchParentSize() inside a Box). The
    // indicator View is then measured to this same box, so pointer coordinates and drawing share
    // one local coordinate space.
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    indicator.onComposePointerEvent(awaitPointerEvent(PointerEventPass.Initial))
                }
            }
        }
    ) {
        AndroidView(factory = { indicator }, modifier = Modifier.matchParentSize())
    }
}
