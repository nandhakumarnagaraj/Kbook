package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateRangePickerDialog(
    state: DateRangePickerState,
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    var attemptedConfirm by remember { mutableStateOf(false) }
    val validationMessage = when {
        start == null && end == null -> "Select start and end date"
        start == null -> "Select start date"
        end == null -> "Select end date"
        end < start -> "End date must be after start date"
        else -> null
    }
    val isValid = validationMessage == null && start != null && end != null

    LaunchedEffect(validationMessage, attemptedConfirm) {
        if (attemptedConfirm && validationMessage != null) {
            KhanaToast.show(validationMessage, ToastKind.Warning)
        }
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onConfirm(start!!, end!!)
                        onDismiss()
                    } else {
                        attemptedConfirm = true
                    }
                }
            ) {
                Text("OK", color = PrimaryGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PrimaryGold)
            }
        },
        colors = DatePickerDefaults.colors(containerColor = DarkBrown2)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .widthIn(max = 900.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                showModeToggle = false,
                title = {
                    Text(
                        text = "Select Custom Range",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.medium, bottom = spacing.small),
                        textAlign = TextAlign.Center,
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                headline = {
                    Text(
                        text = formatCustomDateRangeHeadline(start, end),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = spacing.medium),
                        textAlign = TextAlign.Center,
                        color = PrimaryGold,
                        style = MaterialTheme.typography.headlineSmall
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
                    todayContentColor = PrimaryGold
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large, vertical = spacing.small),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = validationMessage ?: "Date range selected",
                    color = if (validationMessage == null) PrimaryGold else if (attemptedConfirm) MaterialTheme.colorScheme.error else TextGold,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatCustomDateRangeHeadline(startMillis: Long?, endMillis: Long?): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val start = startMillis?.let { formatter.format(Date(it)) } ?: "Start date"
    val end = endMillis?.let { formatter.format(Date(it)) } ?: "End date"
    return "$start - $end"
}
