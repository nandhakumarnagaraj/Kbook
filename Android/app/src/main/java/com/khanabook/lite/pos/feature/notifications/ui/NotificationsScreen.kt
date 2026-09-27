package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.designsystem.KhanaBookCard
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.designsystem.NotificationListPanel
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackPreferences
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackSettings
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
    val spacing = KhanaBookTheme.spacing
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()

    // User sound preferences live in the shared MenuFeedbackPreferences store,
    // so these toggles and the Interaction Feedback screen always stay in sync.
    val feedbackPrefs = rememberMenuFeedbackPreferences()
    val feedbackSettings by rememberMenuFeedbackSettings(feedbackPrefs)

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
                NotificationSoundPreferencesCard(
                    soundEnabled = feedbackSettings.soundEnabled,
                    hapticEnabled = feedbackSettings.hapticEnabled,
                    voiceEnabled = feedbackSettings.voiceAnnouncementEnabled,
                    onSoundChange = feedbackPrefs::setSoundEnabled,
                    onHapticChange = feedbackPrefs::setHapticEnabled,
                    onVoiceChange = feedbackPrefs::setVoiceAnnouncementEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.medium, vertical = spacing.small)
                )
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

@Composable
internal fun NotificationSoundPreferencesCard(
    soundEnabled: Boolean,
    hapticEnabled: Boolean,
    voiceEnabled: Boolean,
    onSoundChange: (Boolean) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onVoiceChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.card
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small)) {
            Text(
                "Sound & Alerts",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            NotificationSoundToggle(
                label = "Menu Selection Sound",
                description = "Soft pop when an item is added to the bill",
                checked = soundEnabled,
                onCheckedChange = onSoundChange
            )
            HorizontalDivider(color = BorderGold.copy(alpha = 0.25f))
            NotificationSoundToggle(
                label = "Vibration",
                description = "Subtle tap vibration with the sound",
                checked = hapticEnabled,
                onCheckedChange = onHapticChange
            )
            HorizontalDivider(color = BorderGold.copy(alpha = 0.25f))
            NotificationSoundToggle(
                label = "Voice Announcements",
                description = "Spoken alerts for payments and KOT confirmations",
                checked = voiceEnabled,
                onCheckedChange = onVoiceChange
            )
        }
    }
}

@Composable
private fun NotificationSoundToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextLight, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                color = TextGold.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(spacing.small))
        KhanaBookSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedTrackColor = com.khanabook.lite.pos.core.theme.SuccessGreen
        )
    }
}
