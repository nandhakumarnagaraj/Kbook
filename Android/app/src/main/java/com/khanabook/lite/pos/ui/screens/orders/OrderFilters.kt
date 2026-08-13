@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.orders

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.domain.model.OrderDetailRow
import com.khanabook.lite.pos.domain.model.PaymentMode
import com.khanabook.lite.pos.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PeriodTabs(selectedFilter: String, onTabSelected: (String) -> Unit) {
    val tabs = listOf("Daily", "Weekly", "Monthly", "Custom")
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        tabs.forEach { title ->
            OrderFilterChip(
                label = title,
                isSelected = selectedFilter == title,
                onClick = { onTabSelected(title) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun OrderFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) PrimaryGold else Color.Transparent,
        animationSpec = tween(200),
        label = "chip_container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) DarkBrown1 else TextLight,
        animationSpec = tween(200),
        label = "chip_content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = KhanaRadii.md,
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, BorderGold),
        contentColor = contentColor,
        enabled = enabled
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = if (isSelected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium
            )
        }
    }
}

internal fun OrderDetailRow.isOnlineOrder(): Boolean {
    return sourceChannel.isOnlineSource()
}

internal fun OrderDetailRow.isDineInOrder(): Boolean {
    return when (orderType.trim().lowercase()) {
        "dine_in", "dine-in" -> true
        else -> false
    }
}

internal fun OrderDetailRow.isTakeawayOrder(): Boolean {
    return when (orderType.trim().lowercase()) {
        "takeaway", "take_away" -> true
        else -> false
    }
}

internal fun String.isOnlineSource(): Boolean {
    return when (trim().lowercase()) {
        "zomato", "swiggy", "own_website", "own website" -> true
        else -> false
    }
}

internal fun OrderDetailRow.displaySourceOrModeLabel(): String {
    return when (sourceChannel.trim().lowercase()) {
        "zomato" -> "Zomato"
        "swiggy" -> "Swiggy"
        "own_website", "own website" -> "Own Website"
        else -> payMode.displayLabel
    }
}

internal fun getPayModeColor(mode: PaymentMode): Color {
    return when (mode) {
        PaymentMode.CASH -> SuccessGreen
        PaymentMode.UPI -> Brown500 
        PaymentMode.POS -> PrimaryGold
        else -> Brown500
    }
}

internal fun periodRange(tab: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    val start: Calendar = when (tab) {
        0 -> { 
            (cal.clone() as Calendar).apply { 
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0) 
                set(Calendar.MILLISECOND, 0)
            }
        }
        1 -> { 
            (cal.clone() as Calendar).apply { 
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0) 
                set(Calendar.MILLISECOND, 0)
            }
        }
        2 -> { 
            (cal.clone() as Calendar).apply { 
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0) 
                set(Calendar.MILLISECOND, 0)
            }
        }
        else -> cal
    }
    
    val end: Calendar = Calendar.getInstance().apply { 
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59) 
        set(Calendar.MILLISECOND, 999)
    }
    return start.timeInMillis to end.timeInMillis
}

internal fun formatDateRangeHeadline(startMillis: Long?, endMillis: Long?): String {
    val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val startText = startMillis?.let { formatter.format(Date(it)) } ?: "Start date"
    val endText = endMillis?.let { formatter.format(Date(it)) } ?: "End date"
    return "$startText - $endText"
}
