package com.khanabook.lite.pos.ui.designsystem

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ToastKind { Success, Error, Warning, Info }

private fun defaultDuration(kind: ToastKind, actionLabel: String?): SnackbarDuration = when {
    actionLabel != null -> SnackbarDuration.Indefinite
    kind == ToastKind.Error || kind == ToastKind.Warning -> SnackbarDuration.Long
    else -> SnackbarDuration.Short
}

class KhanaSnackbarVisuals(
    override val message: String,
    val kind: ToastKind = ToastKind.Info,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = actionLabel != null,
    override val duration: SnackbarDuration = defaultDuration(kind, actionLabel),
) : SnackbarVisuals

object KhanaToast {
    private const val DUPLICATE_WINDOW_MS = 2_000L
    private const val MAX_ACTIVE_REQUESTS = 2

    val host: SnackbarHostState = SnackbarHostState()
    private val requestMutex = Mutex()
    private var activeRequests = 0
    private var lastMessage: String? = null
    private var lastKind: ToastKind? = null
    private var lastAcceptedAt = 0L

    suspend fun show(
        message: String,
        kind: ToastKind = ToastKind.Info,
        actionLabel: String? = null,
        duration: SnackbarDuration? = null,
    ): SnackbarResult = withContext(Dispatchers.Main.immediate) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) return@withContext SnackbarResult.Dismissed

        val now = SystemClock.elapsedRealtime()
        val currentVisuals = host.currentSnackbarData?.visuals as? KhanaSnackbarVisuals
        val isVisibleDuplicate =
            currentVisuals?.message == normalizedMessage && currentVisuals.kind == kind
        val accepted = requestMutex.withLock {
            val isRecentDuplicate =
                lastMessage == normalizedMessage &&
                    lastKind == kind &&
                    now - lastAcceptedAt < DUPLICATE_WINDOW_MS
            val requestLimit = if (kind == ToastKind.Error) {
                MAX_ACTIVE_REQUESTS + 1
            } else {
                MAX_ACTIVE_REQUESTS
            }
            val queueHasRoom = activeRequests < requestLimit

            if (isVisibleDuplicate || isRecentDuplicate || !queueHasRoom) {
                false
            } else {
                activeRequests += 1
                lastMessage = normalizedMessage
                lastKind = kind
                lastAcceptedAt = now
                true
            }
        }

        if (!accepted) return@withContext SnackbarResult.Dismissed

        if (kind == ToastKind.Error) {
            val currentKind = (host.currentSnackbarData?.visuals as? KhanaSnackbarVisuals)?.kind
            if (currentKind == ToastKind.Info || currentKind == ToastKind.Success) {
                host.currentSnackbarData?.dismiss()
            }
        }

        try {
            host.showSnackbar(
                KhanaSnackbarVisuals(
                    message = normalizedMessage,
                    kind = kind,
                    actionLabel = actionLabel,
                    duration = duration ?: defaultDuration(kind, actionLabel),
                )
            )
        } finally {
            requestMutex.withLock {
                activeRequests = (activeRequests - 1).coerceAtLeast(0)
            }
        }
    }
}

private data class KindStyle(
    val container: Color,
    val content: Color,
    val accent: Color,
    val icon: ImageVector,
)

private fun styleFor(kind: ToastKind): KindStyle = when (kind) {
    ToastKind.Success -> KindStyle(DarkBrown2, TextLight, SuccessGreen, Icons.Default.CheckCircle)
    ToastKind.Error -> KindStyle(DarkBrown2, TextLight, DangerRed, Icons.Default.Error)
    ToastKind.Warning -> KindStyle(DarkBrown2, TextLight, WarningYellow, Icons.Default.Warning)
    ToastKind.Info -> KindStyle(DarkBrown2, TextLight, PrimaryGold, Icons.Default.Info)
}

@Composable
fun KhanaBookSnackbar(data: SnackbarData) {
    val kind = (data.visuals as? KhanaSnackbarVisuals)?.kind ?: ToastKind.Info
    val style = styleFor(kind)
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val shape = KhanaRadii.card

    Surface(
        modifier = Modifier
            .padding(horizontal = spacing.medium, vertical = spacing.small)
            .shadow(spacing.small, shape)
            .clip(shape)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = style.container,
        border = BorderStroke(spacing.hairline / 2, style.accent.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .background(style.container)
                .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier.size(iconSize.medium),
            )
            Spacer(modifier = Modifier.width(spacing.smallMedium))
            Text(
                text = data.visuals.message,
                color = style.content,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            data.visuals.actionLabel?.let { label ->
                Spacer(modifier = Modifier.width(spacing.small))
                TextButton(onClick = { data.performAction() }) {
                    Text(label, color = style.accent, fontWeight = FontWeight.Bold)
                }
            }
            if (data.visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_dismiss_message),
                        tint = style.content,
                        modifier = Modifier.size(iconSize.medium),
                    )
                }
            }
        }
    }
}

@Composable
fun KhanaBookSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val spacing = KhanaBookTheme.spacing
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.navigationBarsPadding().padding(bottom = spacing.extraLarge),
    ) { data ->
        KhanaBookSnackbar(data)
    }
}
