package com.khanabook.lite.pos.ui.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.khanabook.lite.pos.ui.theme.*

/**
 * Dialog shown when a user tries to access a feature restricted to the shop owner
 * (such as master-data edits, settings changes, or unauthorized operations).
 * Simple, dismiss-only "OK" dialog with no request-access or remote approval loop.
 */
@Composable
fun PermissionBlockedDialog(
    permissionDisplayName: String,
    onDismiss: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkBrown2,
        shape = KhanaRadii.modal,
        icon = {
            Surface(shape = KhanaRadii.pill, color = PrimaryGold.copy(alpha = 0.14f)) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = PrimaryGold,
                    modifier = Modifier.padding(spacing.medium)
                )
            }
        },
        title = {
            Text(
                text = "Access Restricted",
                style = MaterialTheme.typography.titleLarge,
                color = TextLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Text(
                    text = permissionDisplayName.ifBlank { "Only the restaurant owner can perform this operation." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGold,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = KhanaRadii.button
            ) {
                Text("OK", color = DarkBrown1)
            }
        }
    )
}

