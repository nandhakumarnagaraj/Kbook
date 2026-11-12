package com.khanabook.lite.pos.feature.settings.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.feature.payments.domain.OrderPaymentFlowMode
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DarkBrown2
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.khanabook.lite.pos.core.util.BusinessDate

@Composable
fun ReadOnlyConfigNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = message,
            color = TextGold,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(spacing.medium))
    }
}

@Composable
internal fun RestaurantPaymentFlowSelector(
    selectedMode: OrderPaymentFlowMode,
    onModeSelected: (OrderPaymentFlowMode) -> Unit,
    enabled: Boolean = true
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f)), KhanaRadii.lg)
            .background(DarkBrown2.copy(alpha = 0.45f), KhanaRadii.lg)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Billing Mode",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        PaymentFlowToggleSwitch(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
            enabled = enabled
        )
    }
}

@Composable
internal fun PaymentFlowToggleSwitch(
    selectedMode: OrderPaymentFlowMode,
    onModeSelected: (OrderPaymentFlowMode) -> Unit,
    enabled: Boolean = true
) {
    val payAfterFood = selectedMode == OrderPaymentFlowMode.PAY_AFTER_FOOD
    val thumbSize = 44.dp
    val trackColor = if (enabled) PrimaryGold else PrimaryGold.copy(alpha = 0.45f)
    val thumbColor = if (enabled) DarkBrown1 else DarkBrown1.copy(alpha = 0.55f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, PrimaryGold.copy(alpha = 0.55f), CircleShape)
            .toggleable(
                value = payAfterFood,
                enabled = enabled,
                onValueChange = { checked ->
                    onModeSelected(
                        if (checked) OrderPaymentFlowMode.PAY_AFTER_FOOD
                        else OrderPaymentFlowMode.PAY_BEFORE_FOOD
                    )
                }
            )
            .padding(6.dp)
    ) {
        val travel = (maxWidth - thumbSize).coerceAtLeast(0.dp)
        val thumbOffset by animateDpAsState(
            targetValue = if (payAfterFood) travel else 0.dp,
            animationSpec = tween(durationMillis = 220),
            label = "paymentFlowThumbOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (payAfterFood) 0.dp else thumbSize,
                    end = if (payAfterFood) thumbSize else 0.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = selectedMode.displayLabel,
                color = thumbColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .background(thumbColor, CircleShape)
        )
    }
}

/**
 * Read-only view of when a bill's order number rolls over.
 *
 * The till cannot change this: the cutoff is server-owned (see [BusinessDate]) and is
 * activated from the web dashboard, so this block exists to answer the two questions a
 * shopkeeper actually asks — "when does my numbering restart?" and "why is it splitting
 * tonight's service?". The warning only appears for a genuinely overnight service,
 * because that is the case where the default midnight rollover silently produces two
 * series inside one shift.
 */
@Composable
internal fun BusinessDayInfoCard(
    cutoffTime: String?,
    cutoffEffectiveFrom: Long?,
    openingTime: String?,
    closingTime: String?
) {
    val spacing = KhanaBookTheme.spacing
    val cutoff = cutoffTime?.trim()?.take(5)?.takeIf { it.isNotBlank() } ?: DEFAULT_CUTOFF
    // Mirrors BusinessDate.isCutoffActive on this device: a 00:00 cutoff is the legacy
    // calendar rule, and an unset activation date means the cutoff is not in force yet.
    val cutoffActive = cutoffEffectiveFrom != null && cutoff != DEFAULT_CUTOFF
    val boundary = BusinessDate.closingBoundary(openingTime, closingTime)
    val hasBuffer = boundary != null
    // Must mirror BusinessDate.businessDate() exactly: an activated cutoff wins, otherwise
    // the closing boundary (with settlement buffer when crossing midnight), and a daytime shop at
    // midnight. If this drifts, the card tells the shopkeeper a lie.
    val rollover = when {
        cutoffActive -> cutoff
        boundary != null -> boundary.toString().take(5)
        else -> DEFAULT_CUTOFF
    }
    val rolloverLabel = formatClockTime(rollover)
    val closingLabel = formatClockTime(closingTime)
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f)), KhanaRadii.lg)
            .background(DarkBrown2.copy(alpha = 0.45f), KhanaRadii.lg)
            .clip(KhanaRadii.lg)
            .clickable { isExpanded = !isExpanded }
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Text(
                text = "Business Day",
                color = PrimaryGold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = PrimaryGold,
                modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
            )
        }
        Text(
            text = "Daily order number will reset to 01 at $rolloverLabel",
            color = TextLight,
            style = MaterialTheme.typography.bodyMedium
        )
        AnimatedVisibility(visible = isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.extraSmall)
                    .background(DarkBrown1.copy(alpha = 0.5f), KhanaRadii.md)
                    .border(BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.15f)), KhanaRadii.md)
                    .padding(spacing.small),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = TextGold.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Text(
                    text = when {
                        cutoffActive -> "Daily order number resets to 01 before $rolloverLabel (previous business day)."
                        hasBuffer -> "Includes a ${BusinessDate.SETTLEMENT_BUFFER_MINUTES}-minute settlement buffer after your $closingLabel closing time."
                        else -> "Daily order number will restart at $rolloverLabel."
                    },
                    color = TextGold.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Legacy calendar rule — see [BusinessDate.DEFAULT_CUTOFF]. */
private const val DEFAULT_CUTOFF = "00:00"

/** Minutes past local midnight for an `HH:mm` or `HH:mm:ss` string, or null if unparseable. */
private fun minutesOfDay(raw: String?): Int? {
    val parts = raw?.trim()?.split(":") ?: return null
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

/** `"02:10"` → `"2:10 AM"`; unparseable input is echoed instead of throwing. */
private fun formatClockTime(raw: String?): String {
    val minutes = minutesOfDay(raw) ?: return raw?.trim()?.take(5) ?: "--:--"
    val hour24 = minutes / 60
    val minute = minutes % 60
    val hour12 = when (hour24) {
        0 -> 12
        in 13..23 -> hour24 - 12
        else -> hour24
    }
    val suffix = if (hour24 < 12) "AM" else "PM"
    return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
}

@Composable
internal fun CustomerNumberCollectorSelector(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.25f)), KhanaRadii.lg)
            .background(DarkBrown2.copy(alpha = 0.45f), KhanaRadii.lg)
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryGold, modifier = Modifier.size(KhanaBookTheme.iconSize.small))
            Column(modifier = Modifier.weight(1f)) {
                Text("Collect Customer Number?", color = PrimaryGold, style = MaterialTheme.typography.titleSmall)
            }
            KhanaBookSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
