package com.khanabook.lite.pos.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.ui.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

@Composable
internal fun AppInfoSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val spacing = KhanaBookTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        Text(
            "KBook v${BuildConfig.VERSION_NAME}",
            color = TextGold.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@khanabook.com")
                putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Lite Support")
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }) {
            Text("Contact Support", color = PrimaryGold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun LogoutSection(viewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel) {
    val spacing = KhanaBookTheme.spacing
    val context = androidx.compose.ui.platform.LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val appLockViewModel: com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel = hiltViewModel()
    val enteredPin by appLockViewModel.enteredPin.collectAsStateWithLifecycle()
    val pinError by appLockViewModel.errorMessage.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val isPinEnabled = remember(logoutState) { appLockViewModel.isPinEnabled() }

    LaunchedEffect(logoutState) {
        if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.LoggedOut) {
            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(enteredPin, showPinDialog) {
        if (showPinDialog && enteredPin.length == 4) {
            appLockViewModel.verifyPin(
                onSuccess = {
                    appLockViewModel.clearPin()
                    showPinDialog = false
                    viewModel.forceLogoutDespiteWarning()
                }
            )
        }
    }

    if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData) {
        val warning = logoutState as com.khanabook.lite.pos.ui.viewmodel.LogoutState.WarningOfflineData
        KhanaBookDialog(
            onDismissRequest = { viewModel.cancelLogout() },
            title = "Unsynced Data Warning",
            content = {
                Text(
                    if (isPinEnabled) {
                        "You have ${warning.totalCount} records not synced. Signing out now will remove them from this device. Enter your app PIN to continue."
                    } else {
                        "You have ${warning.totalCount} records not synced. Signing out now will remove them from this device."
                    },
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        ) {
            TextButton(
                onClick = {
                    if (isPinEnabled) {
                        appLockViewModel.clearPin()
                        showPinDialog = true
                    } else {
                        viewModel.forceLogoutDespiteWarning()
                    }
                }
            ) {
                Text(if (isPinEnabled) "Enter PIN" else "Logout Anyway", color = DangerRed)
            }
            TextButton(
                onClick = {
                    showPinDialog = false
                    appLockViewModel.clearPin()
                    viewModel.cancelLogout()
                }
            ) {
                Text("Cancel", color = PrimaryGold)
            }
        }
    }

    if (showPinDialog) {
        KhanaBookDialog(
            onDismissRequest = {
                showPinDialog = false
                appLockViewModel.clearPin()
            },
            title = "Enter App PIN",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text(
                        "Unsynced data will be removed from this device after sign out.",
                        color = TextGold.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    InlinePinEntry(
                        pin = enteredPin,
                        onDigit = { appLockViewModel.appendDigit(it) },
                        onDelete = { appLockViewModel.deleteDigit() },
                        errorMessage = pinError
                    )
                }
            }
        ) {
            TextButton(
                onClick = {
                    showPinDialog = false
                    appLockViewModel.clearPin()
                }
            ) {
                Text("Cancel", color = PrimaryGold)
            }
        }
        }

    if (showConfirmDialog) {
        KhanaBookDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = "Sign Out?",
            message = "You will be signed out of this device."
        ) {
            TextButton(onClick = { showConfirmDialog = false; viewModel.initiateLogout() }) {
                Text("Sign Out", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
            TextButton(onClick = { showConfirmDialog = false }) {
                Text("Cancel", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    val iconSize = KhanaBookTheme.iconSize
    Column(
        modifier = Modifier.fillMaxWidth().padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text("Account Session", color = TextLight, style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { showConfirmDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
        }
    }
}
