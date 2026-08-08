@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.menuconfig

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.khanabook.lite.pos.ui.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

@Composable
fun CategoryEditDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    KhanaBookDialog(
        onDismissRequest = onDismiss,
        title = title,
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name", color = TextGold) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGold,
                    unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        TextButton(
            onClick = onDismiss
        ) {
            Text("Cancel", color = TextGold)
        }
        TextButton(
            onClick = { if (name.isNotBlank()) onConfirm(name) },
            enabled = name.isNotBlank()
        ) {
            Text("Save", color = PrimaryGold)
        }
    }
}
