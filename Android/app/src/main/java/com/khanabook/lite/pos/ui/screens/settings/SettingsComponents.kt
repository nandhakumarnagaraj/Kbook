@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.screens.InlinePinEntry
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
internal fun DisplayScaleView(viewModel: SettingsViewModel) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val displayScale by viewModel.displayScaleState.collectAsStateWithLifecycle()
    val scaleLabels = listOf("Small", "Default", "Large", "X-Large")
    val scaleValues = listOf(0.85f, 1.0f, 1.15f, 1.3f)
    val sliderIndex = scaleValues.indexOfFirst { it >= displayScale }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(spacing.medium))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaShapes.medium
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text("Automatic Layout", color = TextLight, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    text = buildString {
                        append(if (layout.isWideListDetail) "Tablet" else "Phone")
                        append(" · ${layout.screenWidthDp} × ${layout.screenHeightDp} dp")
                        append(if (layout.isLandscape) " · Landscape" else " · Portrait")
                    },
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                if (layout.isCompactHeight) {
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        "Compact-height layouts are enabled automatically.",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text("UI Scale", color = TextLight, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    "Adjust the overall size of text and UI elements.",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(spacing.large))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    scaleLabels.forEachIndexed { i, label ->
                        Text(
                            text = label,
                            color = if (i == sliderIndex) PrimaryGold else TextGold.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (i == sliderIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Slider(
                    value = displayScale,
                    onValueChange = { viewModel.updateDisplayScale(it) },
                    valueRange = 0.80f..1.35f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryGold,
                        activeTrackColor = PrimaryGold,
                        inactiveTrackColor = BorderGold.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(spacing.small))

                Text(
                    text = "Preview",
                    color = TextGold,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(spacing.small))

                KhanaBookCard(
                    colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                    shape = KhanaRadii.md
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            "Sample Item",
                            color = TextLight,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(spacing.hairline))
                        Text(
                            "₹ 100.00",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "This is how text and cards will appear at the selected scale.",
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraLarge))
    }
}


@Composable
internal fun LegacyLogoutSectionUnused(viewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val appLockViewModel: com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel = hiltViewModel()
    val enteredPin by appLockViewModel.enteredPin.collectAsStateWithLifecycle()
    val pinError by appLockViewModel.errorMessage.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val isPinEnabled = remember(logoutState) { appLockViewModel.isPinEnabled() }

    val scope = rememberCoroutineScope()
    LaunchedEffect(logoutState) {
        if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.LoggedOut) {
            scope.launch { KhanaToast.show(context.getString(R.string.toast_signed_out), ToastKind.Success) }
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
            TextButton(onClick = { showConfirmDialog = false }) {
                Text("Cancel", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { showConfirmDialog = false; viewModel.initiateLogout() }) {
                Text("Sign Out", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }

    val iconSize = KhanaBookTheme.iconSize
    Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        Text("Account Session", color = TextLight, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { showConfirmDialog = true }, modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact), colors = ButtonDefaults.buttonColors(containerColor = DangerRed), shape = KhanaRadii.lg) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
        }
    }
}


// ─── App Lock Configuration ──────────────────────────────────────────────────

@Composable
internal fun LegacyAppLockConfigViewUnused(
    onBack: () -> Unit,
    viewModel: com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val setupState by viewModel.pinSetupState.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(viewModel.isPinEnabled()) }
    val showBiometric = remember { viewModel.hasBiometric(context) }

    LaunchedEffect(setupState) {
        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            isEnabled = viewModel.isPinEnabled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        // Status card
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.5f),
                    modifier = Modifier.size(iconSize.medium)
                )
                Spacer(modifier = Modifier.width(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text("App Lock", color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isEnabled) "PIN lock is active" else "Disabled — anyone can open the app",
                        color = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                KhanaBookSwitch(
                    checked = isEnabled,
                    onCheckedChange = { enable ->
                        if (enable) viewModel.startEnablePin()
                        else viewModel.startDisablePin()
                    },
                    checkedTrackColor = SuccessGreen
                )
            }
        }

        if (isEnabled && setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = KhanaRadii.lg
            ) {
                Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("PIN Options", color = TextLight, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { viewModel.startChangePin() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BorderGold),
                        shape = KhanaRadii.md
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = PrimaryGold, modifier = Modifier.size(iconSize.xsmall))
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Change PIN", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                    }
                    if (showBiometric) {
                        Text(
                            "Biometric unlock is available on this device and will be used alongside your PIN.",
                            color = TextGold.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            Text(
                (setupState as com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success).message,
                color = SuccessGreen,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // PIN entry steps driven by ViewModel state
        when (val state = setupState) {
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterNew -> {
                Text(
                    "Set a new 4-digit PIN",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.ConfirmNew -> {
                Text(
                    "Confirm your PIN",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.EnterCurrent -> {
                Text(
                    if (state.nextStep != null) "Enter current PIN to verify" else "Enter current PIN to disable",
                    color = TextLight,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                InlinePinEntry(
                    pin = state.pin,
                    onDigit = { viewModel.onSetupDigit(it) },
                    onDelete = { viewModel.onSetupDelete() },
                    errorMessage = state.error
                )
                TextButton(onClick = { viewModel.resetSetupState() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel", color = TextGold.copy(alpha = 0.6f))
                }
            }
            else -> {}
        }

        Spacer(modifier = Modifier.height(spacing.extraLarge))
    }
}
