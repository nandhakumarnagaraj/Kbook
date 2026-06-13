package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.*

@Composable
fun KhanaBookScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleStyleCompact: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall,
    titleStyleExpanded: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    logo: @Composable (() -> Unit)? = null,
    headerTrailing: @Composable (() -> Unit)? = null,
    headerContent: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgPrimary)
    ) {
        // Theme-aware Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.kbHeaderGradient)
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    KhanaBookBackButton(onClick = onBack)
                } else if (logo != null) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        logo()
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = (if (layout.isCompact) titleStyleCompact else titleStyleExpanded).copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier.width(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    headerTrailing?.invoke()
                }
            }

            if (headerContent != null) {
                Spacer(modifier = Modifier.height(12.dp))
                headerContent()
            }
        }

        // White/Surface Sheet Overlay (rounded top corners)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
fun KhanaBookBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

