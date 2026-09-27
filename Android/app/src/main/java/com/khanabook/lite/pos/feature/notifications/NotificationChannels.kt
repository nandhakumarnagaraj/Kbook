package com.khanabook.lite.pos.feature.notifications

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.khanabook.lite.pos.feature.notifications.worker.NotificationHelper

enum class NotificationChannelGroup(val label: String) {
    PAYMENTS("Payments & Transactions"),
    SYSTEM("System & Security"),
    OPERATIONS("Operations")
}

/** One notification channel as shown in the app's notifications preferences screen. */
data class NotificationChannelSpec(
    val id: String,
    val title: String,
    val description: String,
    val group: NotificationChannelGroup
)

/**
 * The channels shown in Notifications Preferences, read from the channels the app actually
 * registers so the names here can never drift from what Android Settings displays.
 */
object NotificationChannelCatalog {

    fun channels(context: Context): List<NotificationChannelSpec> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        return NotificationHelper.registeredChannels(context).map { channel ->
            NotificationChannelSpec(
                id = channel.id,
                title = channel.name?.toString().orEmpty(),
                description = channel.description.orEmpty(),
                group = groupOf(channel.group)
            )
        }
    }

    private fun groupOf(osGroupId: String?): NotificationChannelGroup = when (osGroupId) {
        "khanabook_group_system" -> NotificationChannelGroup.SYSTEM
        "khanabook_group_operations" -> NotificationChannelGroup.OPERATIONS
        else -> NotificationChannelGroup.PAYMENTS
    }
}

/** Live on/off state of every notification channel, driven by [NotificationHelper]. */
class NotificationChannelState(private val context: Context) {

    private val enabled: MutableState<Map<String, Boolean>> = mutableStateOf(
        NotificationChannelCatalog.channels(context)
            .associate { it.id to NotificationHelper.isChannelEnabled(context, it.id) }
    )

    fun isEnabled(channelId: String): Boolean = enabled.value[channelId] ?: true

    fun setEnabled(channelId: String, value: Boolean) {
        NotificationHelper.setChannelEnabled(context, channelId, value)
        enabled.value = enabled.value + (channelId to value)
    }
}

@Composable
fun rememberNotificationChannelState(context: Context): NotificationChannelState =
    remember(context) { NotificationChannelState(context) }

/** Opens Android's own per-channel settings, for sound, vibration and full control. */
object NotificationChannelSettings {

    fun open(context: Context, channelId: String) {
        val appContext = context.applicationContext
        val perChannel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { appContext.startActivity(perChannel) }.isSuccess) return

        // Fall back to the app-wide notification settings if the device does not resolve
        // the per-channel screen.
        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
