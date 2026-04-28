package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.VegGreen

@Composable
fun KhanaBookSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedTrackColor: Color = VegGreen,
    uncheckedTrackColor: Color = Color(0xFF5C5668),
    checkedThumbColor: Color = Color.Black,
    uncheckedThumbColor: Color = Color(0xFFD6D1DE)
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) checkedTrackColor else uncheckedTrackColor,
        label = "switchTrackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) checkedThumbColor else uncheckedThumbColor,
        label = "switchThumbColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "switchThumbOffset"
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .background(trackColor, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(16.dp)
                .background(thumbColor, CircleShape)
        )
    }
}
