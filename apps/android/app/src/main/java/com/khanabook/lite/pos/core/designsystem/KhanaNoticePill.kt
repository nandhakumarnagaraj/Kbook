package com.khanabook.lite.pos.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.core.theme.BrandPurple
import com.khanabook.lite.pos.core.theme.NoticeClose
import com.khanabook.lite.pos.core.theme.NoticeDarkClose
import com.khanabook.lite.pos.core.theme.NoticeDarkError
import com.khanabook.lite.pos.core.theme.NoticeDarkInfo
import com.khanabook.lite.pos.core.theme.NoticeDarkSuccess
import com.khanabook.lite.pos.core.theme.NoticeDarkSurface
import com.khanabook.lite.pos.core.theme.NoticeDarkText
import com.khanabook.lite.pos.core.theme.NoticeDarkTextSecondary
import com.khanabook.lite.pos.core.theme.NoticeDarkWarning
import com.khanabook.lite.pos.core.theme.NoticeError
import com.khanabook.lite.pos.core.theme.NoticeInfo
import com.khanabook.lite.pos.core.theme.NoticeSuccess
import com.khanabook.lite.pos.core.theme.NoticeSurface
import com.khanabook.lite.pos.core.theme.NoticeText
import com.khanabook.lite.pos.core.theme.NoticeTextSecondary
import com.khanabook.lite.pos.core.theme.NoticeWarning

private const val NOTICE_CORNER_DP = 28
private const val NOTICE_COMPACT_CORNER_DP = 14
private const val NOTICE_ELEVATION_DP = 8
private const val NOTICE_COMPACT_ELEVATION_DP = 6
private const val NOTICE_MEDALLION_DP = 40
private const val NOTICE_COMPACT_MEDALLION_DP = 24
// The monochrome asset has ~8.6 dp horizontal and ~9.7 dp vertical transparent
// padding at this size, so 40 dp of image yields a ~23 x 21 dp glyph.
private const val NOTICE_MARK_DP = 40
private const val NOTICE_COMPACT_MARK_DP = 24
private const val NOTICE_MARKER_DP = 18
private const val NOTICE_COMPACT_MARKER_DP = 16

/**
 * White floating notice shared by the snackbar host and the in-app push banner.
 *
 * The medallion is the brand mark (the monochrome launcher asset, tinted white on
 * brand purple) rather than a status glyph, so every notice is identifiable as
 * KhanaBook at a glance. Kind is carried by the message plus a small shaped
 * marker for Error/Warning — never by colour alone.
 *
 * Colours come from the notice-only tokens in Color.kt, which are verified at
 * 4.5:1 or better on white. Do not substitute SuccessGreen/WarningYellow here:
 * those are tuned for the dark UI and fail on the white pill.
 *
 * @param blockClicksBelow true for toasts. A plain Surface is transparent to
 *   pointer input, so a toast drawn over the bottom navigation or a sticky CTA
 *   would let a tap land on the control the user cannot see. This consumes taps
 *   without adding click semantics (TalkBack must not announce a toast as a
 *   button).
 * @param compact shrinks the toast to a black WhatsApp-style bar: 48 dp tall,
 *   24 dp medallion, 14 sp white message and 14 dp corners. The push banner keeps
 *   the white, roomy variant because it carries a title and needs to be read, not
 *   glanced at.
 */
@Composable
internal fun KhanaNoticePill(
    kind: ToastKind,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    blockClicksBelow: Boolean = false,
    compact: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val shape = RoundedCornerShape(
        (if (compact) NOTICE_COMPACT_CORNER_DP else NOTICE_CORNER_DP).dp
    )
    val elevation = (if (compact) NOTICE_COMPACT_ELEVATION_DP else NOTICE_ELEVATION_DP).dp
    val surfaceColor = if (compact) NoticeDarkSurface else NoticeSurface
    val textColor = if (compact) NoticeDarkText else NoticeText
    val accent = noticeAccent(kind, dark = compact)
    // The app background is itself near-black, so a 1 dp hairline keeps the black
    // toast from reading as a hole rather than an elevated surface.
    val border = if (compact) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    } else {
        null
    }
    val showsMarker = kind == ToastKind.Error || kind == ToastKind.Warning
    val hasTrailing = showsMarker || (actionLabel != null && onAction != null) || onDismiss != null

    val surfaceModifier = modifier
        .shadow(elevation, shape)
        .then(
            if (blockClicksBelow && onClick == null) {
                Modifier.pointerInput(Unit) { detectTapGestures(onTap = {}) }
            } else {
                Modifier
            }
        )
        .semantics {
            liveRegion = if (kind == ToastKind.Error || kind == ToastKind.Warning) {
                LiveRegionMode.Assertive
            } else {
                LiveRegionMode.Polite
            }
        }

    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.heightIn(min = 48.dp) else Modifier)
                .padding(
                    start = 12.dp,
                    end = if (hasTrailing) 4.dp else if (compact) 14.dp else 16.dp,
                    top = if (compact) 0.dp else 12.dp,
                    bottom = if (compact) 0.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMedallion(compact = compact)
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
            MessageColumn(
                title = title,
                message = message,
                compact = compact,
                textColor = textColor,
                modifier = Modifier.weight(1f),
            )
            if (showsMarker) {
                Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                KindMarker(kind = kind, tint = accent, compact = compact)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = actionLabel,
                        color = accent,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_dismiss_message),
                        tint = if (compact) NoticeDarkClose else NoticeClose,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                    )
                }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            color = surfaceColor,
            contentColor = textColor,
            border = border,
            shadowElevation = 0.dp,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = shape,
            color = surfaceColor,
            contentColor = textColor,
            border = border,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

@Composable
private fun BrandMedallion(compact: Boolean = false) {
    Box(
        modifier = Modifier
            .size((if (compact) NOTICE_COMPACT_MEDALLION_DP else NOTICE_MEDALLION_DP).dp)
            .clip(CircleShape)
            .background(BrandPurple),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size((if (compact) NOTICE_COMPACT_MARK_DP else NOTICE_MARK_DP).dp),
        )
    }
}

@Composable
private fun MessageColumn(
    title: String?,
    message: String,
    compact: Boolean = false,
    textColor: Color = NoticeText,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Text(
                text = title,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (message.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message,
                    color = if (compact) NoticeDarkTextSecondary else NoticeTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = message,
                color = textColor,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = if (compact) 3 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun KindMarker(kind: ToastKind, tint: Color, compact: Boolean = false) {
    val icon: ImageVector = if (kind == ToastKind.Error) Icons.Default.Error else Icons.Default.Warning
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(
            (if (compact) NOTICE_COMPACT_MARKER_DP else NOTICE_MARKER_DP).dp
        ),
    )
}

internal fun noticeAccent(kind: ToastKind, dark: Boolean = false): Color = when (kind) {
    ToastKind.Success -> if (dark) NoticeDarkSuccess else NoticeSuccess
    ToastKind.Error -> if (dark) NoticeDarkError else NoticeError
    ToastKind.Warning -> if (dark) NoticeDarkWarning else NoticeWarning
    ToastKind.Info -> if (dark) NoticeDarkInfo else NoticeInfo
}
