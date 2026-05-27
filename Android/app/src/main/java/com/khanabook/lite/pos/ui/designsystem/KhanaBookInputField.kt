package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalMaterial3Api::class)
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

    val interactionSource = remember { MutableInteractionSource() }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = containerBg,
        unfocusedContainerColor = containerBg,
        disabledContainerColor  = containerBg,
        errorContainerColor     = containerBg,
        focusedBorderColor   = primary,
        unfocusedBorderColor = borderIdle,
        disabledBorderColor  = borderIdle.copy(alpha = 0.5f),
        errorBorderColor     = DangerRed,
        focusedLabelColor   = primary,
        unfocusedLabelColor = textMuted,
        disabledLabelColor  = textDisabled,
        errorLabelColor     = DangerRed,
        focusedTextColor   = textPrimary,
        unfocusedTextColor = textPrimary,
        disabledTextColor  = textDisabled,
        errorTextColor     = textPrimary,
        cursorColor = primary,
        focusedLeadingIconColor   = primary,
        unfocusedLeadingIconColor = textMuted,
        disabledLeadingIconColor  = textDisabled,
        errorLeadingIconColor     = DangerRed,
        focusedTrailingIconColor   = primary,
        unfocusedTrailingIconColor = textMuted,
        disabledTrailingIconColor  = textDisabled,
        errorTrailingIconColor     = DangerRed
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = if (singleLine) modifier.fillMaxWidth().height(48.dp) else modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = if (enabled) textPrimary else textDisabled),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(primary),
            decorationBox = @Composable { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = { Text(label) },
                    placeholder = placeholder?.let { { Text(it, color = textMuted.copy(alpha = 0.7f)) } },
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    supportingText = supportingText,
                    colors = colors,
                    contentPadding = if (singleLine) {
                        PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        )
                    } else {
                        OutlinedTextFieldDefaults.contentPadding()
                    },
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = KhanaShapes.small, // 10.dp rounded corners
                            focusedBorderThickness = 2.dp,
                            unfocusedBorderThickness = 1.dp
                        )
                    }
                )
            }
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
