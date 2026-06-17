package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.domain.util.ConnectionStatus
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.HomeViewModel
import com.khanabook.lite.pos.ui.viewmodel.NotificationViewModel

/**
 * Dedicated full-screen Notification Center (replaces the former modal bottom sheet).
 * Brand-violet top bar with back + "Mark all read", scrollable body reusing
 * [NotificationCenterSheet] (header suppressed since this screen provides its own).
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNewBill: () -> Unit,
    onOrderStatus: () -> Unit,
    onMarketplaceOrders: () -> Unit,
    onReprintKds: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
) {
    val stats by viewModel.todayStats.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val unsyncedCount by viewModel.unsyncedCount.collectAsState()
    val marketplacePendingCount by viewModel.marketplacePendingCount.collectAsState()
    val complianceAlerts by viewModel.complianceAlerts.collectAsState()
    val pushNotifications by notificationViewModel.notifications.collectAsState()
    val pushUnreadCount by notificationViewModel.unreadCount.collectAsState()
    val dismissedAlerts = remember { mutableStateListOf<String>() }

    // Pull the latest push notifications from the server when the screen opens.
    LaunchedEffect(Unit) { notificationViewModel.refreshFromServer() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgPrimary)
    ) {
        // Brand-violet top bar (matches the home header the bell lives in).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.kbHeaderGradient)
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notifications",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (pushUnreadCount > 0) "$pushUnreadCount unread" else "Alerts and shortcuts",
                        color = Color.White.copy(alpha = KbOpacity.Muted),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (pushUnreadCount > 0) {
                    TextButton(onClick = { notificationViewModel.markAllAsRead() }) {
                        Text("Mark all read", color = Color.White)
                    }
                }
            }
        }

        // Scrollable body — reuses the operational + push notification content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            NotificationCenterSheet(
                showHeader = false,
                unsyncedCount = unsyncedCount,
                marketplacePendingCount = marketplacePendingCount,
                kdsPendingCount = stats.kdsPendingCount,
                complianceAlerts = complianceAlerts.filter { it.label !in dismissedAlerts },
                isOnline = connectionStatus != ConnectionStatus.Unavailable,
                pushNotifications = pushNotifications,
                pushUnreadCount = pushUnreadCount,
                onNotificationClick = { notif -> notificationViewModel.markAsRead(notif.id) },
                onMarkAllNotificationsRead = { notificationViewModel.markAllAsRead() },
                onRefreshNotifications = { notificationViewModel.refreshFromServer() },
                onMarketplaceOrders = { onMarketplaceOrders() },
                onReprintKds = { onReprintKds() },
                onOrderStatus = { onOrderStatus() },
                onNewBill = { onNewBill() },
                onDismiss = onBack
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
