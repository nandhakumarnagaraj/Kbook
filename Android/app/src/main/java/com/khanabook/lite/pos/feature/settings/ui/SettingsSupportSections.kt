package com.khanabook.lite.pos.feature.settings.ui
import com.khanabook.lite.pos.feature.auth.ui.InlinePinEntry

import com.khanabook.lite.pos.core.theme.KhanaRadii

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
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
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.core.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.ToastKind
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight

@Composable
internal fun AppInfoSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val toastScope = rememberCoroutineScope()
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
                data = Uri.parse("mailto:kbook@pcts.tech")
                putExtra(Intent.EXTRA_SUBJECT, "KhanaBook Lite Support")
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                toastScope.launch {
                    KhanaToast.show("No email app is available", ToastKind.Error)
                }
            }
        }) {
            Text("Contact Support", color = PrimaryGold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun LogoutSection(viewModel: com.khanabook.lite.pos.feature.auth.viewmodel.LogoutViewModel) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val context = androidx.compose.ui.platform.LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val appLockViewModel: com.khanabook.lite.pos.feature.auth.viewmodel.AppLockViewModel = hiltViewModel()
    val enteredPin by appLockViewModel.enteredPin.collectAsStateWithLifecycle()
    val pinError by appLockViewModel.errorMessage.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinPurpose by remember { mutableStateOf<String?>(null) }
    val isPinEnabled = remember(logoutState) { appLockViewModel.isPinEnabled() }

    val toastScope = rememberCoroutineScope()
    LaunchedEffect(logoutState) {
        if (logoutState is com.khanabook.lite.pos.feature.auth.viewmodel.LogoutState.LoggedOut) {
            toastScope.launch { KhanaToast.show(context.getString(R.string.toast_signed_out), ToastKind.Success) }
        }
    }

    LaunchedEffect(enteredPin, showPinDialog) {
        if (showPinDialog && enteredPin.length == 4) {
            appLockViewModel.verifyPin(
                onSuccess = {
                    appLockViewModel.clearPin()
                    showPinDialog = false
                    when (pinPurpose) {
                        "start_logout" -> viewModel.initiateLogout()
                        "force_logout" -> viewModel.forceLogoutDespiteWarning()
                    }
                    pinPurpose = null
                }
            )
        }
    }

    val isLoading = logoutState is com.khanabook.lite.pos.feature.auth.viewmodel.LogoutState.AttemptingPush ||
        logoutState is com.khanabook.lite.pos.feature.auth.viewmodel.LogoutState.ClearingData

    if (isLoading) {
        val loadingMessage = if (logoutState is com.khanabook.lite.pos.feature.auth.viewmodel.LogoutState.ClearingData) {
            "Clearing data..."
        } else {
            "Syncing data before sign out..."
        }
        KhanaBookDialog(
            onDismissRequest = {},
            title = "Signing Out",
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize.medium),
                        color = PrimaryGold,
                        strokeWidth = 3.dp
                    )
                    Text(loadingMessage, color = TextLight, style = MaterialTheme.typography.bodyMedium)
                }
            }
        ) {}
    }

    if (logoutState is com.khanabook.lite.pos.feature.auth.viewmodel.LogoutState.WarningOfflineData) {
        val warning = logoutState as com.khanabook.lite.pos.feature.auth.viewmodel.LogoutState.WarningOfflineData
        KhanaBookDialog(
            onDismissRequest = { viewModel.cancelLogout() },
            title = "Unsynced Data Warning",
            content = {
                Text(
                    buildString {
                        append("${warning.totalCount} records (${warning.summary}) are not yet synced to the server.\n\n")
                        append("Your data is safe — it will stay on this device and sync automatically after you log back in.")
                        if (isPinEnabled) append("\n\nEnter your app PIN to continue.")
                    },
                    color = TextLight,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        ) {
            TextButton(
                onClick = {
                    showPinDialog = false
                    appLockViewModel.clearPin()
                    viewModel.cancelLogout()
                }
            ) {
                Text("Cancel", color = PrimaryGold)
            }
            TextButton(
                onClick = {
                    if (isPinEnabled) {
                        appLockViewModel.clearPin()
                        pinPurpose = "force_logout"
                        showPinDialog = true
                    } else {
                        viewModel.forceLogoutDespiteWarning()
                    }
                }
            ) {
                Text(if (isPinEnabled) "Enter PIN" else "Logout Anyway", color = DangerRed)
            }
        }
    }

    if (showPinDialog) {
        KhanaBookDialog(
            onDismissRequest = {
                showPinDialog = false
                pinPurpose = null
                appLockViewModel.clearPin()
            },
            title = "Enter App PIN",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text(
                        "Unsynced data will stay on this device and sync after you sign in again.",
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
                    pinPurpose = null
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
            TextButton(onClick = { showConfirmDialog = false }) {
                Text("Cancel", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = {
                    showConfirmDialog = false
                    if (isPinEnabled) {
                        appLockViewModel.clearPin()
                        pinPurpose = "start_logout"
                        showPinDialog = true
                    } else {
                        viewModel.initiateLogout()
                    }
                }
            ) {
                Text("Sign Out", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    // Secondary visual weight (design review #11): an outlined destructive action
    // instead of a filled button competing with primary gold CTAs. Accidental sign-out
    // is still guarded by the confirmation dialog above.
    OutlinedButton(
        onClick = { if (!isLoading) showConfirmDialog = true },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(KhanaBookTheme.spacing.buttonHeightCompact),
        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DangerRed,
            disabledContentColor = DangerRed.copy(alpha = 0.45f)
        ),
        shape = KhanaRadii.lg
    ) {
        Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(iconSize.small))
        Spacer(modifier = Modifier.width(spacing.small))
        Text("Sign Out", style = MaterialTheme.typography.labelLarge)
    }
}
