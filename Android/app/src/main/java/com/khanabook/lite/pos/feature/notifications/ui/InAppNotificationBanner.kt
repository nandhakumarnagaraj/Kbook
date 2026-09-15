package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.notifications.data.NotificationEntity
import kotlinx.coroutines.delay

/**
 * LinkedIn-style in-app notification banner: slides in under the status bar
 * when a new notification arrives while the app is in the foreground, auto
 * dismisses, and deep-links to the matching screen when tapped.
 */
@Composable
fun InAppNotificationBanner(
    notification: NotificationEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    // Entrance animation + auto-dismiss, mirroring the tray notification lifetime.
    LaunchedEffect(notification.id) {
        visible = true
        delay(AUTO_DISMISS_MS)
        onDismiss()
    }

    val accent = accentFor(notification.notificationType)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier.statusBarsPadding(),
    ) {
        androidx.compose.material3.Surface(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.medium)
                .shadow(6.dp, MaterialTheme.shapes.medium),
            shape = MaterialTheme.shapes.medium,
            color = DarkBrown2,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBrown2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(accent)
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.title,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    notification.message?.takeIf { it.isNotBlank() }?.let { message ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = message,
                            color = TextLight.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss notification",
                        tint = TextLight,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun accentFor(type: String): Color = when (type) {
    "payment_received", "qr_order" -> Color(0xFF16A34A) // Green, payments
    "refund" -> Color(0xFFEF4444) // Red, refunds
    "kyc" -> Color(0xFF8B5CF6) // Violet, KYC
    "settlement" -> Color(0xFF0284C7) // Blue, settlements
    "fssai_expiry" -> Color(0xFFF97316) // Saffron, FSSAI
    else -> Color(0xFF7C5CDB) // Purple, system defaults
}

private const val AUTO_DISMISS_MS = 6_000L