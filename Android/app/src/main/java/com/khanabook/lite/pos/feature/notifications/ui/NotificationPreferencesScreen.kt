package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.khanabook.lite.pos.core.designsystem.KhanaBookScreenScaffold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.RichEspresso
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackPreferences
import com.khanabook.lite.pos.feature.menu.ui.rememberMenuFeedbackSettings

/**
 * Notifications Preferences (opened from App Settings → Notifications).
 * Shows ONLY the sound & alert toggles — the notification list itself stays on
 * the Home Notification Center. Both read/write the same shared
 * MenuFeedbackPreferences store, so the toggles stay in sync everywhere
 * (Home Notification Center, Interaction Feedback, and here).
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
