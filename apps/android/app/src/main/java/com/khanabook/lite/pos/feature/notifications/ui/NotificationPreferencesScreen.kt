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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    // The per-channel switches are meaningless while Android blocks the app
    // outright, so the screen has to say so instead of showing five "on" rows.
    var notificationsEnabled by remember { mutableStateOf(!notificationsBlocked(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = !notificationsBlocked(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        KhanaBookScreenScaffold(
            // One line: the centered 24 sp default wraps this title on a 360 dp phone.
            // Start alignment drops the reserved trailing slot, and the 20 sp compact
            // style leaves headroom for large-font devices.
            title = "Notification Preferences",
            titleAlignment = TextAlign.Start,
            titleStyleCompact = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
            titleMaxLines = 1,
            titleOverflow = TextOverflow.Ellipsis,
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
                if (!notificationsEnabled) {
                    NotificationsBlockedCard(
                        onOpenSettings = { openAppNotificationSettings(context) }
                    )
                }
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

/** Shown when Android blocks the app entirely, making every switch below inert. */
@Composable
private fun NotificationsBlockedCard(onOpenSettings: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = KhanaRadii.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium)
        ) {
            Text(
                "Notifications are off",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Text(
                "Android is blocking every KhanaBook alert, so the switches below cannot " +
                    "deliver anything. Turn notifications back on in Android settings.",
                color = TextLight,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(spacing.small))
            TextButton(onClick = onOpenSettings) {
                Text("Enable in Settings", color = PrimaryGold)
            }
        }
    }
}

/**
 * `areNotificationsEnabled()` alone does not flip when the Android 13+ runtime
 * permission is revoked with `pm revoke`, so check the permission too.
 */
private fun notificationsBlocked(context: android.content.Context): Boolean {
    val permissionDenied =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    return permissionDenied ||
        !NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun openAppNotificationSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
