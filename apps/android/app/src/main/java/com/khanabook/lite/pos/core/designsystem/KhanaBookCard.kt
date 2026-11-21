package com.khanabook.lite.pos.core.designsystem

import com.khanabook.lite.pos.core.theme.KhanaBookTheme

import com.khanabook.lite.pos.core.theme.KhanaRadii

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookLiteTheme

@Composable
fun KhanaBookCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = KhanaRadii.lg,
    colors: CardColors = CardDefaults.cardColors(containerColor = DarkBrown2),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Feedback is the theme's standard ripple. A whole-card scale was removed
    // because it also shrank the text and numbers inside every card.
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        content = content
    )
}

@Preview
@Composable
fun KhanaBookCardPreview() {
    KhanaBookLiteTheme {
        KhanaBookCard(
            modifier = Modifier.padding(KhanaBookTheme.spacing.medium),
            onClick = {}
        ) {
            Column(modifier = Modifier.padding(KhanaBookTheme.spacing.medium)) {
                Text("Hello Preview", color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
