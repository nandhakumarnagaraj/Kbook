package com.khanabook.lite.pos.feature.notifications.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.core.designsystem.KhanaNoticePill
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.feature.notifications.data.NotificationEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground notification banner, rendered with the same white notice pill as the
 * snackbar so "something happened" always looks the same.
 *
 * Unlike a toast this banner is tappable (opens the matching screen) and carries the
 * push title. It stays long enough to be read: the window is extended by the platform
 * when the user has requested longer timeouts, and auto-dismiss is disabled entirely
 * while TalkBack touch exploration is active. Touching or focusing the banner also
 * restarts the window instead of letting it vanish under a finger.
 */
@Composable
fun InAppNotificationBanner(
    notification: NotificationEntity,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }
    val touchExplorationEnabled = accessibilityManager?.isTouchExplorationEnabled == true
    val timeoutMs = remember(accessibilityManager) { recommendedTimeout(accessibilityManager) }

    val kind = noticeKindFor(notification.notificationType)

    // The parent removes this composable as soon as onDismiss() runs. Waiting for the
    // exit transition before calling it is what makes the declared exit animation
    // reachable at all.
    fun hide() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            visible = false
            delay(EXIT_MS.toLong())
            onDismiss()
        }
    }

    LaunchedEffect(notification.id) {
        visible = true
        dismissing = false
    }

    val paused = pressed || hasFocus || touchExplorationEnabled || dismissing
    LaunchedEffect(notification.id, paused, timeoutMs) {
        if (paused) return@LaunchedEffect
        delay(timeoutMs.toLong())
        hide()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(ENTER_MS, easing = FastOutSlowInEasing),
            initialOffsetY = { -it / 2 },
        ) + fadeIn(tween(ENTER_MS)),
        exit = slideOutVertically(
            animationSpec = tween(EXIT_MS, easing = LinearOutSlowInEasing),
            targetOffsetY = { -it / 2 },
        ) + fadeOut(tween(EXIT_MS)),
        modifier = modifier.statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            KhanaNoticePill(
                kind = kind,
                message = notification.message.orEmpty(),
                title = notification.title,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .onFocusChanged { hasFocus = it.hasFocus },
                onDismiss = { hide() },
                onClick = onOpen,
                interactionSource = interactionSource,
            )
        }
    }
}

/**
 * Push type -> notice kind. Six tray accents collapse into four kinds on purpose:
 * several of the old per-type colours cannot carry a white glyph (white on
 * #F97316 is 2.80:1), and type is already stated by the title and message.
 * A refund/kyc/settlement update is information, not a failure, so it stays Info.
 */
internal fun noticeKindFor(type: String?): ToastKind = when (type) {
    "payment_received", "qr_order" -> ToastKind.Success
    "fssai_expiry", "inventory_low", "order_cancelled" -> ToastKind.Warning
    "refund", "kyc", "settlement" -> ToastKind.Info
    else -> ToastKind.Info
}

private fun recommendedTimeout(manager: AccessibilityManager?): Int {
    val base = AUTO_DISMISS_MS.toInt()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        manager?.getRecommendedTimeoutMillis(
            base,
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
        ) ?: base
    } else {
        base
    }
}

private const val AUTO_DISMISS_MS = 6_000L
private const val ENTER_MS = 180
private const val EXIT_MS = 140
