
@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.core.components
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.core.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.core.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun KhanaDatePickerField(
    label: String,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Internal state is yyyy-MM-dd (search APIs depend on it) but the field
    // displays the friendlier 26-Sep-2026 format.
    val displayDate = remember(selectedDate) {
        if (selectedDate.isBlank()) {
            ""
        } else {
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
                if (parsed != null) {
                    SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(parsed)
                } else {
                    selectedDate
                }
            } catch (e: Exception) {
                selectedDate
            }
        }
    }
    
    val calendar = Calendar.getInstance()
    if (selectedDate.isNotEmpty()) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)
            if (date != null) calendar.time = date
        } catch (e: Exception) {}
    }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = calendar.timeInMillis
    )

    KhanaBookInputField(
        value = displayDate,
        onValueChange = { },
        readOnly = true,
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        enabled = false,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryGold)
            }
        }
    )
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        onDateSelected(sdf.format(Date(it)))
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = PrimaryGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = PrimaryGold)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = DarkBrown2
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkBrown2,
                    todayContentColor = PrimaryGold,
                    selectedDayContainerColor = PrimaryGold,
                    selectedDayContentColor = DarkBrown1,
                    titleContentColor = PrimaryGold,
                    headlineContentColor = PrimaryGold,
                    weekdayContentColor = TextGold,
                    dayContentColor = TextLight
                )
            )
        }
    }
}

@Composable
fun ParchmentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false
) {
    KhanaBookInputField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, color = DangerRed, style = MaterialTheme.typography.labelSmall) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        readOnly = readOnly
    )
}

@Composable
fun KhanaTimePickerDialog(
    title: String = "Select Time",
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = KhanaRadii.xl,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(shape = KhanaRadii.xl, color = DarkBrown2),
            color = DarkBrown2
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryGold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                content()
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun KhanaTimePickerField(
    label: String,
    time: String,
    onTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showTimePicker by remember { mutableStateOf(false) }

    val parsedTime = remember(time) {
        try {
            val trimmed = time.trim()
            val isPm = trimmed.endsWith("PM", ignoreCase = true)
            val isAm = trimmed.endsWith("AM", ignoreCase = true)
            val clean = trimmed.replace("(?i)am|pm".toRegex(), "").trim()
            val parts = clean.split(":")
            var h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            Pair(h.coerceIn(0, 23), m.coerceIn(0, 59))
        } catch (e: Exception) {
            Pair(8, 0)
        }
    }

    val displayTime = remember(parsedTime) {
        val hour24 = parsedTime.first
        val minute = parsedTime.second
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (hour24) {
            0 -> 12
            in 1..12 -> hour24
            else -> hour24 - 12
        }
        String.format(Locale.ROOT, "%02d:%02d %s", hour12, minute, amPm)
    }

    Box(modifier = modifier) {
        ParchmentTextField(
            value = displayTime,
            onValueChange = {},
            readOnly = true,
            label = label,
            enabled = enabled,
            trailingIcon = {
                IconButton(onClick = { if (enabled) showTimePicker = true }, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Select $label",
                        tint = if (enabled) PrimaryGold else Color.Gray
                    )
                }
            }
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { showTimePicker = true }
            )
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = parsedTime.first,
            initialMinute = parsedTime.second,
            is24Hour = false
        )

        KhanaTimePickerDialog(
            title = label,
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = String.format(Locale.ROOT, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    onTimeSelected(formatted)
                    showTimePicker = false
                }) {
                    Text("OK", color = PrimaryGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel", color = PrimaryGold)
                }
            }
        ) {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = DarkBrown1,
                    clockDialSelectedContentColor = DarkBrown1,
                    clockDialUnselectedContentColor = TextLight,
                    selectorColor = PrimaryGold,
                    containerColor = DarkBrown2,
                    periodSelectorBorderColor = PrimaryGold,
                    periodSelectorSelectedContainerColor = PrimaryGold,
                    periodSelectorUnselectedContainerColor = DarkBrown1,
                    periodSelectorSelectedContentColor = DarkBrown1,
                    periodSelectorUnselectedContentColor = TextGold,
                    timeSelectorSelectedContainerColor = PrimaryGold.copy(alpha = 0.25f),
                    timeSelectorUnselectedContainerColor = DarkBrown1,
                    timeSelectorSelectedContentColor = PrimaryGold,
                    timeSelectorUnselectedContentColor = TextLight
                )
            )
        }
    }
}
