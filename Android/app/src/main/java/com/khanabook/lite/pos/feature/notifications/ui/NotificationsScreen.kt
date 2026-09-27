package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.designsystem.NotificationListPanel
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.feature.notifications.viewmodel.NotificationViewModel

/**
 * Full-screen Notification Center — the notification list only: category filters
 * (All / Payments / Orders / Alerts), the "Mark all read" action and the empty state.
 * Sound & alert toggles live in the Notifications Preferences screen.
 * Refreshes automatically from the server on entry.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val layout = KhanaBookTheme.layout
    val spacing = KhanaBookTheme.spacing
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NotificationListPanel(
                    notifications = notifications,
                    unreadCount = unreadCount,
                    onNotificationClick = { viewModel.markAsRead(it.id) },
                    onMarkAllRead = { viewModel.markAllAsRead() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .widthIn(max = layout.maxContentWidth)
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = spacing.large)
                )
            }
        }
    }
}
