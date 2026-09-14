package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.designsystem.NotificationListPanel
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.feature.notifications.viewmodel.NotificationViewModel

/**
 * Full-screen Notification Center — back arrow + "Mark all read" in the scaffold
 * header, scrollable list of push notifications with refresh + empty state.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshFromServer() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        KhanaBookScreenScaffold(
            title = "Notifications",
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
            headerTrailing = {
                if (unreadCount > 0) {
                    TextButton(onClick = { viewModel.markAllAsRead() }) {
                        Text(
                            text = "Mark all read",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        ) {
            NotificationListPanel(
                notifications = notifications,
                unreadCount = unreadCount,
                onNotificationClick = { viewModel.markAsRead(it.id) },
                onMarkAllRead = { viewModel.markAllAsRead() },
                onRefresh = { viewModel.refreshFromServer() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = KhanaBookTheme.spacing.large)
            )
        }
    }
}
