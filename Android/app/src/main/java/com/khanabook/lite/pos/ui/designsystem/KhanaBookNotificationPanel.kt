package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.NotificationEntity
import com.khanabook.lite.pos.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Notification icon with unread badge — placed in the Home header to show unread count.
 */
@Composable
fun NotificationBellIcon(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(DarkBrown2)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = PrimaryGold,
                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
            )
        }
        if (unreadCount > 0) {
            val badgeSize: Dp = if (unreadCount > 99) 22.dp else if (unreadCount > 9) 20.dp else 18.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp)
                    .size(badgeSize)
                    .background(DangerRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = if (unreadCount > 9) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Single notification row — icon, title, message, time, and read/unread state.
 */
@Composable
fun NotificationRow(
    notification: NotificationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconColor) = when (notification.notificationType) {
        "payment_received" -> Icons.Default.Payment to SuccessGreen
        "refund" -> Icons.Default.Replay to DangerRed
        "kyc" -> Icons.Default.VerifiedUser to BrandPurple
        "settlement" -> Icons.Default.AccountBalance to LightGold
        "fssai_expiry" -> Icons.Default.WarningAmber to WarningYellow
        else -> Icons.Default.Notifications to PrimaryGold
    }

    val timeText = remember(notification.createdAt) {
        formatNotificationTime(notification.createdAt)
    }

    val unreadContainer = if (notification.notificationType == "fssai_expiry") {
        WarningYellow.copy(alpha = 0.06f)
    } else {
        PrimaryGold.copy(alpha = 0.06f)
    }
    val unreadBorder = if (notification.notificationType == "fssai_expiry") {
        WarningYellow.copy(alpha = 0.25f)
    } else {
        PrimaryGold.copy(alpha = 0.25f)
    }

    KhanaBookCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = KhanaBookTheme.spacing.extraSmall),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) unreadContainer else DarkBrown2
        ),
        shape = KhanaRadii.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KhanaBookTheme.spacing.medium),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.medium)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        color = TextLight,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = timeText,
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (!notification.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (notification.notificationType == "fssai_expiry") WarningYellow else PrimaryGold,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.extraSmall))

                notification.message?.let { msg ->
                    Text(
                        text = msg,
                        color = TextGold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                notification.amount?.let { amt ->
                    Spacer(modifier = Modifier.height(KhanaBookTheme.spacing.extraSmall))
                    Surface(
                        shape = KhanaRadii.pill,
                        color = SuccessGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "₹$amt",
                            color = SuccessGreen,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full notification list panel — push notifications with header, mark-all-read,
 * empty state, and refresh action.
 */
@Composable
fun NotificationListPanel(
    notifications: List<NotificationEntity>,
    unreadCount: Int,
    onNotificationClick: (NotificationEntity) -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KhanaBookTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Notifications",
                    color = TextLight,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount unread",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.extraSmall)) {
                if (unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Mark all read", color = PrimaryGold)
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                shape = KhanaRadii.card
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(KhanaBookTheme.iconSize.large)
                    )
                    Text(
                        text = "No notifications yet",
                        color = TextGold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Order alerts, payment confirmations, and license reminders will appear here",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(KhanaBookTheme.spacing.extraSmall))
                        Text("Refresh")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.extraSmall)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = { onNotificationClick(notification) }
                    )
                }
                item {
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(KhanaBookTheme.spacing.extraSmall))
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────

private fun formatNotificationTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
