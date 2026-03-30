
@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    OutlinedTextField(
        value = selectedDate,
        onValueChange = { },
        readOnly = true,
        label = { Text(label, color = TextGold) },
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        enabled = false, 
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = TextLight,
            disabledBorderColor = BorderGold,
            disabledLabelColor = TextGold,
            disabledLeadingIconColor = PrimaryGold,
            disabledTrailingIconColor = PrimaryGold
        ),
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryGold)
            }
        },
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
                    todayContentColor = PrimaryGold,
                    selectedDayContainerColor = PrimaryGold,
                    selectedDayContentColor = DarkBrown1,
                    titleContentColor = TextLight,
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
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, color = TextGold) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, color = DangerRed, fontSize = 11.sp) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BorderGold,
            unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
            focusedTextColor = TextLight,
            unfocusedTextColor = TextLight,
            focusedLabelColor = TextGold,
            unfocusedLabelColor = TextGold.copy(alpha = 0.7f),
            cursorColor = PrimaryGold,
            errorBorderColor = DangerRed,
            errorLabelColor = DangerRed,
            errorCursorColor = DangerRed
        ),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions
    )
}


