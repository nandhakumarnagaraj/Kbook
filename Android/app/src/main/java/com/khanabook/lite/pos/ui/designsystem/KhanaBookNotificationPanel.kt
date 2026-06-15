package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Notification icon with unread badge — styled like Easebuzz ePOS bell icon.
 * Place this in the header to show unread notification count.
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
                .background(Color.Black.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        // Badge — larger when count > 9
        if (unreadCount > 0) {
            val badgeSize: Dp = if (unreadCount > 99) 22.dp else if (unreadCount > 9) 20.dp else 18.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 0.dp)
                    .size(badgeSize)
                    .background(KbAccentRed, CircleShape),
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
 * Single notification row — displays icon, title, message, time, and read/unread state.
 * Matches Easebuzz ePOS notification drawer style with saffron accent.
 */
@Composable
fun NotificationRow(
    notification: NotificationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconColor) = when (notification.notificationType) {
        "payment_received" -> Icons.Default.Payment to KbSuccess
        "refund" -> Icons.Default.Replay to KbBrandSaffron
        "kyc" -> Icons.Default.VerifiedUser to KbAccentViolet
        "settlement" -> Icons.Default.AccountBalance to KbAccentEmerald
        else -> Icons.Default.Notifications to KbAccentBlue
    }

    val timeText = remember(notification.createdAt) {
        formatNotificationTime(notification.createdAt)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (!notification.isRead)
            KbBrandSaffron.copy(alpha = 0.06f)
        else
            Color.Transparent,
        shape = KbShape.Small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(iconColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
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
                        color = if (notification.isRead)
                            MaterialTheme.kbTextPrimary.copy(alpha = 0.7f)
                        else
                            MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!notification.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(KbBrandSaffron, CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                notification.message?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.kbTextTertiary,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = timeText,
                        color = MaterialTheme.kbTextTertiary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                    )

                    notification.amount?.let { amt ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = KbBrandSaffron.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "₹$amt",
                                color = KbBrandSaffron,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full notification drawer panel — lists push notifications with a header,
 * mark-all-read action, and empty state. Designed for use in ModalBottomSheet.
 */
@Composable
fun NotificationListPanel(
    notifications: List<NotificationEntity>,
    unreadCount: Int,
    onNotificationClick: (NotificationEntity) -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Notifications",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount unread",
                        color = KbBrandSaffron,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) {
                        Text("Mark all read", color = KbBrandSaffron)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = MaterialTheme.kbTextSecondary)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.kbOutlineSubtle)

        // Notification list
        if (notifications.isEmpty()) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                shape = KbShape.Medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.kbTextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No notifications yet",
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Payment alerts, KYC updates, and settlement notifications will appear here",
                        color = MaterialTheme.kbTextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = { onNotificationClick(notification) }
                    )
                }
            }

            // Refresh button
            TextButton(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Refresh")
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
