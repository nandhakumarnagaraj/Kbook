package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

data class SelectionDialogOption<T>(
    val value: T,
    val title: String,
    val subtitle: String? = null,
    val selectedAccent: Color = PrimaryGold,
    val onSelect: (() -> Unit)? = null
)

@Composable
fun <T> KhanaBookSelectionDialog(
    title: String,
    onDismissRequest: () -> Unit,
    options: List<SelectionDialogOption<T>>,
    selectedValue: T? = null,
    modifier: Modifier = Modifier,
    message: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    cancelLabel: String = "Cancel",
    actions: @Composable (() -> Unit)? = null,
    onOptionSelected: (T) -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    KhanaBookDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title,
        message = message,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                options.forEach { option ->
                    val isSelected = selectedValue == option.value
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                option.onSelect?.invoke()
                                onOptionSelected(option.value)
                            },
                        color = if (isSelected) option.selectedAccent.copy(alpha = 0.14f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) option.selectedAccent else BorderGold.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.medium, vertical = spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    color = if (isSelected) option.selectedAccent else TextLight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                option.subtitle?.let {
                                    Text(
                                        text = it,
                                        color = TextGold.copy(alpha = 0.75f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                trailingContent?.invoke()
            }
        }
    ) {
        TextButton(onClick = onDismissRequest) {
            Text(cancelLabel, color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
        }
        actions?.invoke()
    }
}

@Preview
@Composable
private fun KhanaBookSelectionDialogPreview() {
    KhanaBookLiteTheme {
        KhanaBookSelectionDialog(
            title = "Pick One",
            message = "Selection dialog preview",
            onDismissRequest = {},
            options = listOf(
                SelectionDialogOption("one", "Option One", "First option"),
                SelectionDialogOption("two", "Option Two", "Second option")
            ),
            selectedValue = "one",
            onOptionSelected = {}
        )
    }
}
