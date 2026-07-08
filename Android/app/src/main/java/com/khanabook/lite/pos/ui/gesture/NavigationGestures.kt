package com.khanabook.lite.pos.ui.gesture

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.horizontalNavigationSwipe(
    enabled: Boolean = true,
    threshold: Dp = 72.dp,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
): Modifier = composed {
    if (!enabled) {
        this
    } else {
        val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
        var dragOffset by remember { mutableFloatStateOf(0f) }

        pointerInput(thresholdPx, onSwipeLeft, onSwipeRight) {
            detectHorizontalDragGestures(
                onDragStart = { dragOffset = 0f },
                onHorizontalDrag = { _, dragAmount ->
                    dragOffset += dragAmount
                },
                onDragEnd = {
                    when {
                        dragOffset <= -thresholdPx -> onSwipeLeft()
                        dragOffset >= thresholdPx -> onSwipeRight()
                    }
                    dragOffset = 0f
                },
                onDragCancel = {
                    dragOffset = 0f
                }
            )
        }
    }
}
