package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.kbBgCard
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbSecondary
import com.khanabook.lite.pos.ui.theme.kbTextPrimary

/**
 * KhanaBook standard dialog.
 *
 * Surface colour and border are resolved from MaterialTheme so the dialog
 * renders correctly in both dark and light mode.
 */
@Composable
fun KhanaBookDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout  = KhanaBookTheme.layout

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = dismissOnClickOutside,
            dismissOnBackPress      = dismissOnBackPress
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(layout.dialogWidthFraction)
                .widthIn(max = layout.dialogMaxWidth),
            shape  = RoundedCornerShape(20.dp),
            // Theme-aware: white surface in light mode, dark surface in dark mode
            color  = MaterialTheme.kbBgCard,
            border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.mediumLarge, vertical = spacing.mediumLarge),
                verticalArrangement   = Arrangement.spacedBy(spacing.medium),
                horizontalAlignment   = Alignment.Start
            ) {
                if (title != null) {
                    Text(
                        text  = title,
                        color = MaterialTheme.kbSecondary,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (message != null) {
                    Text(
                        text  = message,
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                content()
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                    verticalAlignment     = Alignment.Top
                ) {
                    actions()
                }
            }
        }
    }
}

@Preview(name = "Dark",  showBackground = true, backgroundColor = 0xFF060604)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KhanaBookDialogPreview() {
    KhanaBookLiteTheme {
        KhanaBookDialog(
            onDismissRequest = {},
            title   = "Preview Dialog",
            message = "Shared small dialog preview."
        ) {
            Text("Primary Action",   color = MaterialTheme.kbSecondary)
            Text("Secondary Action", color = MaterialTheme.kbTextPrimary)
        }
    }
}
