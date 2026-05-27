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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.*

@Composable
fun KhanaBookCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    // Default container colour is theme-aware: dark surface in dark mode, white in light mode.
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Micro-interaction: scale down slightly on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "card_scale"
    )

    // Border colour: theme-aware outline so it's visible in both modes
    val borderColor = MaterialTheme.kbOutlineSubtle

    Card(
        modifier = modifier
            .scale(scale)
            .border(width = 0.5.dp, color = borderColor, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null, // visual feedback via scale
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

@Composable
fun KhanaBookGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "glass_card_scale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        KbBrandSaffron.copy(alpha = 0.4f),
                        KbBrandSaffron.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = shape,
        // KbGlassSurface already adapts to dark/light via ThemeState.isDark
        colors = CardDefaults.cardColors(containerColor = KbGlassSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF060604)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun KhanaBookCardPreview() {
    KhanaBookLiteTheme {
        Column {
            KhanaBookCard(
                modifier = Modifier.padding(16.dp),
                onClick = {}
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Standard Card")
                }
            }

            KhanaBookGlassCard(
                modifier = Modifier.padding(16.dp),
                onClick = {}
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Glass Card")
                }
            }
        }
    }
}
