package com.khanabook.lite.pos.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePickerDialog(
    initialStartDateMillis: Long? = null,
    initialEndDateMillis: Long? = null,
    state: DateRangePickerState? = null,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    val defaultStart = initialStartDateMillis
        ?: state?.selectedStartDateMillis
        ?: getStartOfDayMillis()
    val defaultEnd = initialEndDateMillis
        ?: state?.selectedEndDateMillis
        ?: getEndOfDayMillis()

    var fromDateMillis by remember { mutableStateOf(defaultStart) }
    var toDateMillis by remember { mutableStateOf(defaultEnd) }

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val isValid = toDateMillis >= fromDateMillis
    val daysSelected = if (isValid) {
        val diff = toDateMillis - fromDateMillis
        (diff / (24 * 60 * 60 * 1000L)) + 1
    } else 0L

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .padding(vertical = spacing.medium),
            shape = KhanaRadii.lg,
            color = DarkBrown2,
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.medium))
                    Column {
                        Text(
                            text = "Select Date Range",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryGold
                        )
                        Text(
                            text = "Filter orders & reports by period",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGold.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // Quick Presets
                Text(
                    text = "QUICK PRESETS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextGold.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(spacing.extraSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    DatePresetChip("Today", modifier = Modifier.weight(1f)) {
                        fromDateMillis = getStartOfDayMillis()
                        toDateMillis = getEndOfDayMillis()
                    }
                    DatePresetChip("Yesterday", modifier = Modifier.weight(1f)) {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                        fromDateMillis = getStartOfDayMillis(cal)
                        toDateMillis = getEndOfDayMillis(cal)
                    }
                    DatePresetChip("Last 7D", modifier = Modifier.weight(1f)) {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                        fromDateMillis = getStartOfDayMillis(cal)
                        toDateMillis = getEndOfDayMillis()
                    }
                    DatePresetChip("This Month", modifier = Modifier.weight(1f)) {
                        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                        fromDateMillis = getStartOfDayMillis(cal)
                        toDateMillis = getEndOfDayMillis()
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // From Date Field
                DateFieldCard(
                    label = "From Date",
                    dateMillis = fromDateMillis,
                    onClick = { showFromPicker = true }
                )

                Spacer(modifier = Modifier.height(spacing.small))

                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = TextGold.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.height(spacing.small))

                // To Date Field
                DateFieldCard(
                    label = "To Date",
                    dateMillis = toDateMillis,
                    onClick = { showToPicker = true }
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                // Selection Summary / Validation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.small),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isValid) {
                        Text(
                            text = "To Date must be on or after From Date",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    } else {
                        Text(
                            text = "$daysSelected ${if (daysSelected == 1L) "day" else "days"} selected (${formatShortDate(fromDateMillis)} – ${formatShortDate(toDateMillis)})",
                            color = TextLight.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = KhanaRadii.md,
                        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = {
                            if (isValid) {
                                onConfirm(fromDateMillis, toDateMillis)
                                onDismiss()
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(46.dp),
                        shape = KhanaRadii.md,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGold,
                            contentColor = DarkBrown1,
                            disabledContainerColor = PrimaryGold.copy(alpha = 0.3f),
                            disabledContentColor = DarkBrown1.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            "Apply Filter",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }

    // Single Date Picker for "From Date"
    if (showFromPicker) {
        val fromPickerState = rememberDatePickerState(
            initialSelectedDateMillis = fromDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromPickerState.selectedDateMillis?.let { utcMillis ->
                        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                        val local = Calendar.getInstance().apply {
                            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                        }
                        fromDateMillis = getStartOfDayMillis(local)
                    }
                    showFromPicker = false
                }) {
                    Text("OK", color = PrimaryGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) {
                    Text("Cancel", color = TextGold)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkBrown2)
        ) {
            DatePicker(
                state = fromPickerState,
                title = {
                    Text(
                        text = "Select From Date",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = DarkBrown2,
                    titleContentColor = PrimaryGold,
                    headlineContentColor = PrimaryGold,
                    weekdayContentColor = TextGold,
                    dayContentColor = TextLight,
                    selectedDayContainerColor = PrimaryGold,
                    selectedDayContentColor = DarkBrown1,
                    todayContentColor = PrimaryGold,
                    todayDateBorderColor = PrimaryGold
                )
            )
        }
    }

    // Single Date Picker for "To Date"
    if (showToPicker) {
        val toPickerState = rememberDatePickerState(
            initialSelectedDateMillis = toDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    toPickerState.selectedDateMillis?.let { utcMillis ->
                        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                        val local = Calendar.getInstance().apply {
                            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                        }
                        toDateMillis = getEndOfDayMillis(local)
                    }
                    showToPicker = false
                }) {
                    Text("OK", color = PrimaryGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) {
                    Text("Cancel", color = TextGold)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkBrown2)
        ) {
            DatePicker(
                state = toPickerState,
                title = {
                    Text(
                        text = "Select To Date",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = DarkBrown2,
                    titleContentColor = PrimaryGold,
                    headlineContentColor = PrimaryGold,
                    weekdayContentColor = TextGold,
                    dayContentColor = TextLight,
                    selectedDayContainerColor = PrimaryGold,
                    selectedDayContentColor = DarkBrown1,
                    todayContentColor = PrimaryGold,
                    todayDateBorderColor = PrimaryGold
                )
            )
        }
    }
}

@Composable
private fun DatePresetChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(6.dp),
        color = DarkBrown1.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = TextLight,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DateFieldCard(
    label: String,
    dateMillis: Long,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = KhanaRadii.md,
        color = DarkBrown1.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatFullDate(dateMillis),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextLight
                )
            }
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = PrimaryGold
            )
        }
    }
}

private fun formatFullDate(millis: Long): String {
    val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatShortDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun getStartOfDayMillis(cal: Calendar = Calendar.getInstance()): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun getEndOfDayMillis(cal: Calendar = Calendar.getInstance()): Long {
    val c = cal.clone() as Calendar
    c.set(Calendar.HOUR_OF_DAY, 23)
    c.set(Calendar.MINUTE, 59)
    c.set(Calendar.SECOND, 59)
    c.set(Calendar.MILLISECOND, 999)
    return c.timeInMillis
}
