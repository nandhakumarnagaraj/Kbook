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
 * Full-screen Notification Center with a back header and a scrollable notification
 * list that provides the "Mark all read" action and empty state.
 * Refreshes automatically from the server on entry.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val layout = KhanaBookTheme.layout
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
            NotificationListPanel(
                notifications = notifications,
                unreadCount = unreadCount,
                onNotificationClick = { viewModel.markAsRead(it.id) },
                onMarkAllRead = { viewModel.markAllAsRead() },
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = layout.maxContentWidth)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = KhanaBookTheme.spacing.large)
            )
        }
    }
}
