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
 * Dialog shown when a user tries to access a feature they don't have permission for.
 * Provides a "Request Access" button that sends a permission request to the owner.
 */
@Composable
fun PermissionBlockedDialog(
    permissionDisplayName: String,
    isLoading: Boolean = false,
    requestSent: Boolean = false,
    onRequestAccess: () -> Unit,
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
                text = if (requestSent) "Request Sent" else "Access Required",
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
                    text = if (requestSent)
                        "Your request for \"$permissionDisplayName\" access has been sent to the shop owner. You'll get access once approved."
                    else
                        "You need \"$permissionDisplayName\" permission to use this feature. Request access from your shop owner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGold,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            if (requestSent) {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = PrimaryGold)
                }
            } else {
                Button(
                    onClick = onRequestAccess,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                    shape = KhanaRadii.button
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(spacing.medium),
                            color = DarkBrown1,
                            strokeWidth = spacing.hairline
                        )
                    } else {
                        Text("Request Access", color = DarkBrown1)
                    }
                }
            }
        },
        dismissButton = {
            if (!requestSent) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextGold)
                }
            }
        }
    )
}
