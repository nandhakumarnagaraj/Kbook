package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.KbGray300
import com.khanabook.lite.pos.ui.theme.KbGray500
import com.khanabook.lite.pos.ui.theme.KbSuccess

/**
 * KhanaBook custom switch.
 *
 * Uses [toggleable] with [Role.Switch] so TalkBack correctly announces
 * the checked state ("on" / "off") and the component role ("Switch").
 *
 * Touch target: the outer Box is 48×48 dp (WCAG 2.5.5 minimum) even though
 * the visible track is 44×26 dp, achieved via the invisible padding wrapper.
 */
@Composable
fun KhanaBookSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedTrackColor: Color = KbSuccess,
    uncheckedTrackColor: Color = KbGray500,
    checkedThumbColor: Color = Color.White,
    uncheckedThumbColor: Color = KbGray300
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

    // 48×48 dp touch target wrapper (invisible padding around the 44×26 dp track)
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .toggleable(
                value    = checked,
                enabled  = enabled,
                role     = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        // Visible track
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .background(trackColor, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Thumb
            Box(
                modifier = Modifier
                    .padding(start = thumbOffset)
                    .size(16.dp)
                    .background(thumbColor, CircleShape)
            )
        }
    }
}
