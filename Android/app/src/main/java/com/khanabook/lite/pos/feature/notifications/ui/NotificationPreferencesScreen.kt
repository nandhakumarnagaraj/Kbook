package com.khanabook.lite.pos.feature.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.notifications.NotificationChannelCatalog
import com.khanabook.lite.pos.feature.notifications.NotificationChannelGroup
import com.khanabook.lite.pos.feature.notifications.NotificationChannelSettings
import com.khanabook.lite.pos.feature.notifications.NotificationChannelSpec
import com.khanabook.lite.pos.feature.notifications.rememberNotificationChannelState

/**
 * Notifications Preferences (App Settings -> Data -> Notifications Preferences).
 *
 * Lists the six notification channels the app actually posts to, and hands each one to
 * Android's own channel settings. Notification *delivery* is controlled by the OS, not by
 * this app: the per-channel enable/disable APIs are hidden from apps on purpose, so a
 * switch drawn here could never be authoritative and would drift from the real state.
 * The notification list itself lives in the Home Notification Center.
 *
 * The sound, vibration and voice-announcement toggles that used to live here were moved:
 * sound and vibration are Interaction Feedback's (App Settings -> Appearance), and voice
 * announcements live with the payment flow in Payment Configuration. All three write to
 * the shared MenuFeedbackPreferences store, so showing them in two places meant flipping
 * one here silently moved one there.
 */
@Composable
fun NotificationPreferencesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val context = LocalContext.current
    val channelState = rememberNotificationChannelState(context)

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
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = layout.maxContentWidth)
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                NotificationChannelGroup.entries.forEach { group ->
                    val specs = NotificationChannelCatalog.channels(context)
                        .filter { it.group == group }
                    if (specs.isNotEmpty()) {
                        NotificationChannelCard(
                            groupLabel = group.label,
                            specs = specs,
                            isEnabled = channelState::isEnabled,
                            onToggle = channelState::setEnabled,
                            onOpen = { channelId -> NotificationChannelSettings.open(context, channelId) }
                        )
                    }
                }

                Text(
                    text = "Use the switch to turn an alert on or off. Tap an alert to fine-tune " +
                        "its sound and vibration in Android Settings.",
                    color = TextGold.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = spacing.small)
                )
            }
        }
    }
}

@Composable
private fun NotificationChannelCard(
    groupLabel: String,
    specs: List<NotificationChannelSpec>,
    isEnabled: (String) -> Boolean,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small)
        ) {
            Text(
                groupLabel,
                color = PrimaryGold,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            specs.forEachIndexed { index, spec ->
                if (index > 0) {
                    HorizontalDivider(color = BorderGold.copy(alpha = 0.25f))
                }
                NotificationChannelRow(
                    spec = spec,
                    enabled = isEnabled(spec.id),
                    onToggle = { onToggle(spec.id, it) },
                    onClick = { onOpen(spec.id) }
                )
            }
        }
    }
}

@Composable
private fun NotificationChannelRow(
    spec: NotificationChannelSpec,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacing.smallMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                spec.title,
                color = if (enabled) TextLight else TextGold.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                spec.description,
                color = TextGold.copy(alpha = if (enabled) 0.6f else 0.35f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(modifier = Modifier.width(spacing.small))
        KhanaBookSwitch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}
