package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.khanabook.lite.pos.ui.theme.kbTextSecondary

/**
 * KhanaBook large (full-height) dialog — used for multi-step flows like
 * Forgot Password, KYC, and configuration sheets.
 *
 * Container colour is resolved from MaterialTheme so it renders correctly
 * in both dark and light mode.
 */
@Composable
fun KhanaBookLargeDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout  = KhanaBookTheme.layout

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        KhanaBookCard(
            modifier = modifier
                .fillMaxWidth(if (layout.isCompact) 0.94f else 0.88f)
                .widthIn(max = if (layout.isExpanded) 900.dp else 560.dp)
                .padding(spacing.medium),
            // Theme-aware surface colour
            colors    = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
            shape     = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.mediumLarge),
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = title,
                            color = MaterialTheme.kbSecondary,
                            style = MaterialTheme.typography.titleLarge
                        )
                        subtitle?.invoke()
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = MaterialTheme.kbTextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color     = MaterialTheme.kbOutlineSubtle,
                    thickness = 1.dp
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 640.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    content = content
                )

                if (actions != null) {
                    HorizontalDivider(
                        color     = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                    Column(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalArrangement   = Arrangement.spacedBy(spacing.small),
                        horizontalAlignment   = Alignment.End,
                        content               = actions
                    )
                }
            }
        }
    }
}

@Preview(name = "Dark",  showBackground = true, backgroundColor = 0xFF060604)
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun KhanaBookLargeDialogPreview() {
    KhanaBookLiteTheme {
        KhanaBookLargeDialog(
            title             = "Large Dialog",
            onDismissRequest  = {},
            subtitle          = { Text("Preview subtitle", color = MaterialTheme.kbSecondary.copy(alpha = 0.75f)) },
            actions           = { Text("Close", color = MaterialTheme.kbSecondary) }
        ) {
            Text("Large dialog content preview.")
        }
    }
}
