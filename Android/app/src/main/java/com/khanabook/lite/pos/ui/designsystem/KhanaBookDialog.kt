package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextLight

@Composable
fun KhanaBookDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = dismissOnClickOutside,
            dismissOnBackPress = dismissOnBackPress
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(layout.dialogWidthFraction)
                .widthIn(max = layout.dialogMaxWidth),
            shape = RoundedCornerShape(20.dp),
            color = DarkBrown1,
            border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.mediumLarge, vertical = spacing.mediumLarge),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                horizontalAlignment = Alignment.Start
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        color = PrimaryGold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (message != null) {
                    Text(
                        text = message,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                content()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    actions()
                }
            }
        }
    }
}

@Preview
@Composable
private fun KhanaBookDialogPreview() {
    KhanaBookLiteTheme {
        KhanaBookDialog(
            onDismissRequest = {},
            title = "Preview Dialog",
            message = "Shared small dialog preview."
        ) {
            Text("Primary Action", color = PrimaryGold)
            Text("Secondary Action", color = TextLight)
        }
    }
}
