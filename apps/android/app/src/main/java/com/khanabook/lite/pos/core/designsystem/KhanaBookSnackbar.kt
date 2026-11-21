package com.khanabook.lite.pos.core.designsystem

import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ToastKind { Success, Error, Warning, Info }

// Base windows are deliberately longer than the previous 2 s / 3.5 s: the pill now
// carries a 16 sp message and a brand medallion, and a 3-line message cannot be read
// in 2 s. Longer messages extend the window in toastDurationMs(); accessibility
// services can extend it further at display time.
internal const val TOAST_SUCCESS_MS = 2_500L
internal const val TOAST_ERROR_MS = 4_000L
internal const val TOAST_WARNING_MS = 4_000L
internal const val TOAST_INFO_MS = 2_500L
internal const val TOAST_MAX_MS = 6_000L

/**
 * Display window for one notice.
 *
 * Base by kind plus 500 ms for every five words past eight, capped at six seconds.
 * The cap matters: a toast is transient feedback, not a reading surface, and an
 * error that needs more than six seconds should carry an action (which keeps it
 * on screen indefinitely) or be shown inline.
 */
internal fun toastDurationMs(kind: ToastKind, message: String = ""): Long {
    val base = when (kind) {
        ToastKind.Error, ToastKind.Warning -> TOAST_ERROR_MS
        ToastKind.Success, ToastKind.Info -> TOAST_SUCCESS_MS
    }
    val words = message.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    val extra = ((words - 8).coerceAtLeast(0) / 5) * 500L
    return (base + extra).coerceAtMost(TOAST_MAX_MS)
}

// Always Indefinite: Material3's SnackbarHost auto-dismisses Short at 4 s, which
// would truncate the length-aware window (up to 6 s) before KhanaBookSnackbar's
// timer runs. The host must not own the timeout; the LaunchedEffect below does.
private fun defaultDuration(kind: ToastKind, actionLabel: String?): SnackbarDuration =
    SnackbarDuration.Indefinite

class KhanaSnackbarVisuals(
    override val message: String,
    val kind: ToastKind = ToastKind.Info,
    override val actionLabel: String? = null,
    // Errors and warnings stay dismissible even without an action. A user must be
    // able to clear an error they have already read instead of waiting it out.
    override val withDismissAction: Boolean =
        actionLabel != null || kind == ToastKind.Error || kind == ToastKind.Warning,
    override val duration: SnackbarDuration = defaultDuration(kind, actionLabel),
) : SnackbarVisuals

object KhanaToast {
    private const val DUPLICATE_WINDOW_MS = 2_000L

    val host: SnackbarHostState = SnackbarHostState()
    private val requestMutex = Mutex()
    private var activeShowJob: Job? = null
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

            if (isVisibleDuplicate || isRecentDuplicate) {
                false
            } else {
                lastMessage = normalizedMessage
                lastKind = kind
                lastAcceptedAt = now
                true
            }
        }

        if (!accepted) return@withContext SnackbarResult.Dismissed

        // Keep one foreground message visible at a time. Cancel the previous
        // caller as well as dismissing the host item so SnackbarHostState does
        // not retain a queued message that could appear after this one.
        val currentJob = currentCoroutineContext()[Job]
        val previousJob = requestMutex.withLock {
            val previous = activeShowJob
            activeShowJob = currentJob
            previous
        }
        if (previousJob != null && previousJob !== currentJob) previousJob.cancel()
        host.currentSnackbarData?.dismiss()

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
                if (activeShowJob === currentJob) activeShowJob = null
            }
        }
    }
}

@Composable
fun KhanaBookSnackbar(data: SnackbarData) {
    val kind = (data.visuals as? KhanaSnackbarVisuals)?.kind ?: ToastKind.Info
    val message = data.visuals.message
    val actionLabel = data.visuals.actionLabel
    val hasAction = actionLabel != null

    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)
    }

    // Kind-specific window, extended by the platform when the user has asked for
    // longer timeouts. Action notices stay Indefinite and are exempt.
    LaunchedEffect(data, kind, message) {
        if (!hasAction) {
            val base = toastDurationMs(kind, message)
            val timeout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                accessibilityManager?.getRecommendedTimeoutMillis(
                    base.toInt(),
                    AccessibilityManager.FLAG_CONTENT_TEXT or
                        AccessibilityManager.FLAG_CONTENT_CONTROLS
                )?.toLong() ?: base
            } else {
                base
            }
            delay(timeout)
            data.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        KhanaNoticePill(
            kind = kind,
            message = message,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            actionLabel = actionLabel,
            onAction = if (hasAction) ({ data.performAction() }) else null,
            onDismiss = if (data.visuals.withDismissAction) ({ data.dismiss() }) else null,
            blockClicksBelow = true,
            compact = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KhanaBookSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomLift: Dp = 0.dp,
) {
    // union(navigationBars, ime) is deliberate: imePadding() alone would push the
    // host up by the IME but leave it behind the gesture bar when the keyboard is
    // closed, and stacking both would count the navigation bar twice.
    val imeVisible = WindowInsets.isImeVisible
    val lift = if (imeVisible) 0.dp else bottomLift

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(bottom = 16.dp + lift),
    ) { data ->
        KhanaBookSnackbar(data)
    }
}
