package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.DarkBrown2
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

@Composable
fun KhanaBookInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = PrimaryGold,
        backgroundColor = PrimaryGold.copy(alpha = 0.35f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = TextGold.copy(alpha = 0.7f)) } },
            modifier = modifier,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            singleLine = singleLine,
            readOnly = readOnly,
            enabled = enabled,
            isError = isError,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            textStyle = textStyle,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBrown2,
                unfocusedContainerColor = DarkBrown2,
                disabledContainerColor = DarkBrown2,
                errorContainerColor = DarkBrown2,
                focusedBorderColor = PrimaryGold,
                unfocusedBorderColor = BorderGold.copy(alpha = 0.5f),
                disabledBorderColor = BorderGold.copy(alpha = 0.35f),
                errorBorderColor = DangerRed,
                focusedLabelColor = PrimaryGold,
                unfocusedLabelColor = TextGold.copy(alpha = 0.7f),
                disabledLabelColor = TextGold.copy(alpha = 0.55f),
                errorLabelColor = DangerRed,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                disabledTextColor = TextLight.copy(alpha = 0.75f),
                errorTextColor = TextLight,
                cursorColor = PrimaryGold,
                focusedLeadingIconColor = PrimaryGold,
                unfocusedLeadingIconColor = TextGold,
                disabledLeadingIconColor = TextGold.copy(alpha = 0.55f),
                errorLeadingIconColor = DangerRed,
                focusedTrailingIconColor = PrimaryGold,
                unfocusedTrailingIconColor = TextGold,
                disabledTrailingIconColor = TextGold.copy(alpha = 0.55f),
                errorTrailingIconColor = DangerRed
            )
        )
    }
}

@Preview
@Composable
private fun KhanaBookInputFieldPreview() {
    KhanaBookLiteTheme {
        KhanaBookInputField(
            value = "",
            onValueChange = {},
            label = "Preview Field",
            placeholder = "Type here"
        )
    }
}
