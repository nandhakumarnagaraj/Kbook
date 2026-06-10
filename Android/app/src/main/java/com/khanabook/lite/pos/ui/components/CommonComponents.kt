
@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.khanabook.lite.pos.ui.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.ui.theme.*
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
        value = selectedDate,
        onValueChange = { },
        readOnly = true,
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        enabled = false,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.kbSecondary)
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
                    Text("OK", color = MaterialTheme.kbPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = MaterialTheme.kbTextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.kbBgSecondary
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    todayContentColor = MaterialTheme.kbSecondary,
                    selectedDayContainerColor = MaterialTheme.kbPrimary,
                    selectedDayContentColor = MaterialTheme.kbTextPrimary,
                    titleContentColor = MaterialTheme.kbTextPrimary,
                    headlineContentColor = MaterialTheme.kbPrimary,
                    weekdayContentColor = MaterialTheme.kbTextSecondary,
                    dayContentColor = MaterialTheme.kbTextPrimary
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
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, color = KbError, style = MaterialTheme.typography.labelSmall) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        readOnly = readOnly
    )
}
