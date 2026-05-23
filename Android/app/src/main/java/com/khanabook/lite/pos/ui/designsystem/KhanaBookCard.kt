package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme

@Composable
fun KhanaBookCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp), // Card shape (16.dp)
    colors: CardColors = CardDefaults.cardColors(containerColor = CardBG), // Card BG (#221F35)
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // Elevation (0.dp)
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Micro-interaction: Scale down slightly when pressed
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "card_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .border(width = 0.5.dp, color = BorderGold, shape = shape) // 0.5.dp BorderColor stroke
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // We handle visual feedback via scale
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        colors = colors,
        elevation = elevation,
        content = content
    )
}

@Preview
@Composable
fun KhanaBookCardPreview() {
    KhanaBookLiteTheme {
        KhanaBookCard(
            modifier = Modifier.padding(16.dp),
            onClick = {}
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hello Preview", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
