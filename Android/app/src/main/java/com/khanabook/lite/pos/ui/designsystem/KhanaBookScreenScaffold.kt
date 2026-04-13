package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold

@Composable
fun KhanaBookScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleStyleCompact: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall,
    titleStyleExpanded: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                }
            } else {
                Spacer(modifier = Modifier.width(spacing.huge))
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = PrimaryGold,
                style = if (layout.isCompact) titleStyleCompact else titleStyleExpanded,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier.width(spacing.huge),
                contentAlignment = Alignment.Center
            ) {
                headerTrailing?.invoke()
            }
        }
        content()
    }
}
