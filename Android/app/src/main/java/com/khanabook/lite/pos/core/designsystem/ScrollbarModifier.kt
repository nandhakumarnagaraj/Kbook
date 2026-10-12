package com.khanabook.lite.pos.core.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.core.theme.PrimaryGold

/**
 * Attaches a vertical scrollbar indicator to any scrollable Compose layout.
 *
 * Automatically tracks the scroll state and renders a proportional thumb.
 * Only renders when the content actually overflows the viewport (maxValue > 0).
 */
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 4.dp,
    paddingEnd: Dp = 2.dp,
    color: Color = PrimaryGold.copy(alpha = 0.65f),
    trackColor: Color = Color.White.copy(alpha = 0.08f)
): Modifier = this.drawWithContent {
    drawContent()

    val viewportHeight = size.height
    val maxScroll = scrollState.maxValue.toFloat()

    // Draw the scrollbar only if the content actually overflows and is scrollable
    if (maxScroll > 0f && viewportHeight > 0f) {
        val totalContentHeight = maxScroll + viewportHeight
        val minThumbHeight = 36.dp.toPx()
        val rawThumbHeight = (viewportHeight / totalContentHeight) * viewportHeight
        val thumbHeight = rawThumbHeight.coerceIn(minThumbHeight, viewportHeight)

        val thumbOffset = (scrollState.value.toFloat() / maxScroll) * (viewportHeight - thumbHeight)
        val xOffset = size.width - width.toPx() - paddingEnd.toPx()

        // Subtle track background for high visibility
        if (trackColor.alpha > 0f) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(xOffset, 0f),
                size = Size(width.toPx(), viewportHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }

        // Animated / positioned thumb
        drawRoundRect(
            color = color,
            topLeft = Offset(xOffset, thumbOffset),
            size = Size(width.toPx(), thumbHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}

/**
 * Attaches a vertical scrollbar indicator to any LazyColumn via [LazyListState].
 */
fun Modifier.verticalScrollbar(
    lazyListState: LazyListState,
    width: Dp = 4.dp,
    paddingEnd: Dp = 2.dp,
    color: Color = PrimaryGold.copy(alpha = 0.65f),
    trackColor: Color = Color.White.copy(alpha = 0.08f)
): Modifier = this.drawWithContent {
    drawContent()

    val layoutInfo = lazyListState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewportHeight = size.height

    if (totalItems > 0 && visibleItems.isNotEmpty() && viewportHeight > 0f) {
        val firstVisible = visibleItems.first().index
        val visibleCount = visibleItems.size
        if (visibleCount < totalItems) {
            val thumbHeight = (viewportHeight * (visibleCount.toFloat() / totalItems)).coerceIn(36.dp.toPx(), viewportHeight)
            val scrollRange = totalItems - visibleCount
            val scrollFraction = (firstVisible.toFloat() / scrollRange.coerceAtLeast(1)).coerceIn(0f, 1f)
            val thumbOffset = scrollFraction * (viewportHeight - thumbHeight)
            val xOffset = size.width - width.toPx() - paddingEnd.toPx()

            if (trackColor.alpha > 0f) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(xOffset, 0f),
                    size = Size(width.toPx(), viewportHeight),
                    cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
                )
            }

            drawRoundRect(
                color = color,
                topLeft = Offset(xOffset, thumbOffset),
                size = Size(width.toPx(), thumbHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
            )
        }
    }
}
