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
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.KhanaShapes
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextMuted
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
            placeholder = placeholder?.let { { Text(it, color = TextMuted.copy(alpha = 0.7f)) } },
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
            shape = KhanaShapes.small, // Input shape (10.dp)
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBG,    // Background Card (#221F35)
                unfocusedContainerColor = CardBG,  // Background Card (#221F35)
                disabledContainerColor = CardBG,
                errorContainerColor = CardBG,
                focusedBorderColor = PrimaryGold,  // AccentPrimary (#7F77DD)
                unfocusedBorderColor = BorderGold, // BorderColor (#7F77DD1A)
                disabledBorderColor = BorderGold.copy(alpha = 0.5f),
                errorBorderColor = DangerRed,
                focusedLabelColor = PrimaryGold,
                unfocusedLabelColor = TextMuted,   // TextMuted (#9895B0)
                disabledLabelColor = TextMuted.copy(alpha = 0.55f),
                errorLabelColor = DangerRed,
                focusedTextColor = TextLight,      // TextPrimary (#F0EFF8)
                unfocusedTextColor = TextLight,
                disabledTextColor = TextLight.copy(alpha = 0.75f),
                errorTextColor = TextLight,
                cursorColor = PrimaryGold,
                focusedLeadingIconColor = PrimaryGold,
                unfocusedLeadingIconColor = TextMuted,
                disabledLeadingIconColor = TextMuted.copy(alpha = 0.55f),
                errorLeadingIconColor = DangerRed,
                focusedTrailingIconColor = PrimaryGold,
                unfocusedTrailingIconColor = TextMuted,
                disabledTrailingIconColor = TextMuted.copy(alpha = 0.55f),
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
