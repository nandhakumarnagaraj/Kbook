package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.TextMuted
import com.khanabook.lite.pos.ui.theme.WarningYellow

enum class KhanaStatusKind { Success, Danger, Warning, Neutral, Info }

private fun statusColor(kind: KhanaStatusKind): Color = when (kind) {
    KhanaStatusKind.Success -> SuccessGreen
    KhanaStatusKind.Danger -> DangerRed
    KhanaStatusKind.Warning -> WarningYellow
    KhanaStatusKind.Neutral -> TextMuted
    KhanaStatusKind.Info -> PrimaryGold
}

private fun statusContentColor(kind: KhanaStatusKind): Color = when (kind) {
    KhanaStatusKind.Warning,
    KhanaStatusKind.Info -> DarkBrown1
    else -> TextLight
}

@Composable
fun KhanaStatusBadge(
    text: String,
    kind: KhanaStatusKind,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val spacing = KhanaBookTheme.spacing
    val container = statusColor(kind)
    val content = statusContentColor(kind)
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.padding(horizontal = spacing.hairline),
        color = if (filled) container else container.copy(alpha = 0.16f),
        contentColor = if (filled) content else container,
        border = if (filled) null else BorderStroke(1.dp, container.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = if (filled) content else container,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            lineHeight = 10.sp,
            modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall)
        )
    }
}

@Composable
fun KhanaInlineLoader(
    modifier: Modifier = Modifier,
    color: Color = DarkBrown1
) {
    CircularProgressIndicator(
        modifier = modifier.size(20.dp),
        strokeWidth = 2.dp,
        color = color
    )
}

@Composable
fun KhanaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    height: androidx.compose.ui.unit.Dp = 56.dp
) {
    val spacing = KhanaBookTheme.spacing
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(height),
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryGold,
            contentColor = DarkBrown1,
            disabledContainerColor = PrimaryGold.copy(alpha = 0.35f),
            disabledContentColor = DarkBrown1.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            KhanaInlineLoader(color = DarkBrown1)
            Spacer(modifier = Modifier.width(spacing.small))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun KhanaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val spacing = KhanaBookTheme.spacing
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryGold,
            disabledContentColor = TextGold.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun KhanaDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val spacing = KhanaBookTheme.spacing
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DangerRed,
            contentColor = TextLight,
            disabledContainerColor = DangerRed.copy(alpha = 0.35f),
            disabledContentColor = TextLight.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun KhanaButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun KhanaEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Default.Description
) {
    val spacing = KhanaBookTheme.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.extraLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextGold.copy(alpha = 0.25f),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = title,
                color = TextGold.copy(alpha = 0.75f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = TextGold.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
