package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 6.dp,
    color: Color = Color(0xFFF97316).copy(alpha = 0.5f) // Subtle premium saffron color
): Modifier = this.drawWithContent {
    drawContent()
    val viewportHeight = size.height
    val totalContentHeight = scrollState.maxValue.toFloat() + viewportHeight
    
    // Draw the scrollbar only if the content actually overflows and is scrollable
    if (scrollState.maxValue > 0 && totalContentHeight > 0f) {
        val scrollbarHeight = (viewportHeight / totalContentHeight) * viewportHeight
        val scrollbarOffset = (scrollState.value.toFloat() / totalContentHeight) * viewportHeight
        
        // Place the scrollbar inside the container, slightly offset from the absolute right edge
        val xOffset = size.width - width.toPx() - 6.dp.toPx()
        
        drawRoundRect(
            color = color,
            topLeft = Offset(xOffset, scrollbarOffset),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}
