package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.khanabook.lite.pos.core.designsystem.KhanaBookCard
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackPreferences
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackSettings

/**
 * Notifications Preferences (opened from App Settings → Notifications).
 * Holds the sound & alert toggles — the notification list itself lives in the
 * Home Notification Center. Shares the MenuFeedbackPreferences store with
 * Interaction Feedback and the Payment voice toggle, so all of them stay in sync.
 */
@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    // Same shared preference store as the Home Notification Center —
    // changing a toggle here updates there too (and vice versa).
    val feedbackPrefs = rememberMenuFeedbackPreferences()
    val feedbackSettings by rememberMenuFeedbackSettings(feedbackPrefs)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        KhanaBookScreenScaffold(
            title = "Notifications Preferences",
            onBack = onBack,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = layout.maxContentWidth)
                    .align(Alignment.CenterHorizontally)
            ) {
                NotificationSoundPreferencesCard(
                    soundEnabled = feedbackSettings.soundEnabled,
                    hapticEnabled = feedbackSettings.hapticEnabled,
                    voiceEnabled = feedbackSettings.voiceAnnouncementEnabled,
                    onSoundChange = feedbackPrefs::setSoundEnabled,
                    onHapticChange = feedbackPrefs::setHapticEnabled,
                    onVoiceChange = feedbackPrefs::setVoiceAnnouncementEnabled,
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small)
                )
            }
        }
    }
}

@Composable
private fun NotificationSoundPreferencesCard(
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
            checkedTrackColor = SuccessGreen
        )
    }
}
