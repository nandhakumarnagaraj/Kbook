@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.applock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.screens.InlinePinEntry
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel

@Composable
internal fun SettingsGroupLabel(text: String) {
    val spacing = KhanaBookTheme.spacing
    Text(
        text = text.uppercase(),
        color = PrimaryGold.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall)
    )
}

@Composable
fun AppLockView(
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize

    val setupState by viewModel.pinSetupState.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(viewModel.isPinEnabled()) }
    val showBiometric = remember { viewModel.hasBiometric(context) }
    val showPinOptions = isEnabled && (
        setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle ||
            setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success
        )

    LaunchedEffect(setupState) {
        if (setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Success) {
            isEnabled = viewModel.isPinEnabled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large, vertical = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.large)
    ) {
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.xl
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            if (isEnabled) SuccessGreen.copy(alpha = 0.15f) else TextGold.copy(alpha = 0.1f),
                            CircleShape
                        )
                        .border(
                            2.dp,
                            if (isEnabled) SuccessGreen.copy(alpha = 0.4f) else BorderGold.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.5f),
                        modifier = Modifier.size(KhanaBookTheme.iconSize.xlarge)
                    )
                }
                Text(
                    "App Lock",
                    color = TextLight,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isEnabled) "PIN lock is active" else "Disabled — anyone can open the app",
                    color = if (isEnabled) SuccessGreen else TextGold.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
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

        if (showPinOptions) {
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
                            "Fingerprint, face, or device screen lock is available on this device and will be used alongside your PIN.",
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
    }
}
