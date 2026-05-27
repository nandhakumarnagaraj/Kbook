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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.KhanaShapes
import com.khanabook.lite.pos.ui.theme.kbOutlineBold
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbPrimary
import com.khanabook.lite.pos.ui.theme.kbBgCard
import com.khanabook.lite.pos.ui.theme.kbTextPrimary
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
import com.khanabook.lite.pos.ui.theme.kbTextDisabled

/**
 * KhanaBook design-system input field.
 *
 * All colours are resolved from MaterialTheme.colorScheme so the field
 * renders correctly in both dark and light mode without any hardcoded values.
 */
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
    val primary      = MaterialTheme.kbPrimary
    val containerBg  = MaterialTheme.kbBgCard          // surface — white in light, dark brown in dark
    val textPrimary  = MaterialTheme.kbTextPrimary
    val textMuted    = MaterialTheme.kbTextSecondary
    val textDisabled = MaterialTheme.kbTextDisabled
    val borderFocus  = MaterialTheme.kbOutlineBold
    val borderIdle   = MaterialTheme.kbOutlineSubtle

    val selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = primary,
        backgroundColor = primary.copy(alpha = 0.35f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = textMuted.copy(alpha = 0.7f)) } },
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
            shape = KhanaShapes.small, // 10.dp rounded corners
            colors = OutlinedTextFieldDefaults.colors(
                // Container — theme-aware surface colour
                focusedContainerColor   = containerBg,
                unfocusedContainerColor = containerBg,
                disabledContainerColor  = containerBg,
                errorContainerColor     = containerBg,
                // Border
                focusedBorderColor   = primary,
                unfocusedBorderColor = borderIdle,
                disabledBorderColor  = borderIdle.copy(alpha = 0.5f),
                errorBorderColor     = DangerRed,
                // Label
                focusedLabelColor   = primary,
                unfocusedLabelColor = textMuted,
                disabledLabelColor  = textDisabled,
                errorLabelColor     = DangerRed,
                // Text
                focusedTextColor   = textPrimary,
                unfocusedTextColor = textPrimary,
                disabledTextColor  = textDisabled,
                errorTextColor     = textPrimary,
                // Cursor
                cursorColor = primary,
                // Leading icon
                focusedLeadingIconColor   = primary,
                unfocusedLeadingIconColor = textMuted,
                disabledLeadingIconColor  = textDisabled,
                errorLeadingIconColor     = DangerRed,
                // Trailing icon
                focusedTrailingIconColor   = primary,
                unfocusedTrailingIconColor = textMuted,
                disabledTrailingIconColor  = textDisabled,
                errorTrailingIconColor     = DangerRed
            )
        )
    }
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF060604)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
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
