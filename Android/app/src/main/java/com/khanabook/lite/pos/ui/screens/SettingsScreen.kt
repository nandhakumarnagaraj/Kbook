@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.domain.util.ValidationUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.domain.model.PrinterRole
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy

import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navController: NavController,
    onScanClick: (String?) -> Unit = {},
    menuViewModel: MenuViewModel,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    logoutViewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("menu") }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600
    val spacing = KhanaBookTheme.spacing

    // Standard staggered entry animation
    var screenVisible by remember { mutableStateOf(false) }
    val enterSpec = fadeIn(tween(350)) + slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = tween(350, easing = FastOutSlowInEasing)
    )
    val exitSpec = fadeOut(tween(200))
    LaunchedEffect(section) {
        screenVisible = false
        kotlinx.coroutines.delay(30)
        screenVisible = true
    }

    BackHandler {
        if (section != "menu") {
            section = "menu"
        } else {
            onBack()
        }
    }

    DisposableEffect(section) {
        onBottomBarVisibilityChange(section != "menu_config")
        onDispose {
            if (section == "menu_config") {
                onBottomBarVisibilityChange(true)
            }
        }
    }

    if (section == "menu_config") {
        MenuConfigurationScreen(
            navController = navController,
            onBackClick = { section = "menu" },
            viewModel = menuViewModel
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2)))
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (section == "menu") onBack() else section = "menu" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryGold)
                }
                Text(
                    text = when (section) {
                        "shop" -> "Shop Configuration"
                        "payment" -> "Payment Configuration"
                        "printer" -> "Printer Configuration"
                        "tax" -> "Tax Configuration"
                        "security" -> "App Lock"
                        "menu" -> "Settings"
                        else -> "Settings"
                    },
                    modifier = Modifier.weight(1f),
                    color = PrimaryGold,
                    style = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(spacing.huge))
            }

            Box(modifier = Modifier.weight(1f)) {
                val lastSyncTs = remember { viewModel.getLastSyncTimestamp() }
                when (section) {
                    "menu" -> {
                        SettingsHomeSection(
                            currentUser = currentUser,
                            profile = profile,
                            lastSyncTimestamp = lastSyncTs,
                            isWideScreen = isWideScreen,
                            screenVisible = screenVisible,
                            enterSpec = enterSpec,
                            exitSpec = exitSpec,
                            logoutViewModel = logoutViewModel,
                            onSectionSelected = { section = it }
                        )
                    }
                    "shop" -> {
                        ShopConfigView(profile, viewModel, authViewModel) { section = "menu" }
                    }
                    "payment" -> {
                        val ctx = LocalContext.current
                        PaymentConfigView(profile, onSave = { viewModel.saveProfile(it); Toast.makeText(ctx, "Saved Payment Config", Toast.LENGTH_SHORT).show(); section = "menu" }, onBack = { section = "menu" })
                    }
                    "printer" -> {
                        val ctx = LocalContext.current
                        PrinterConfigView(profile, onSave = { viewModel.saveProfile(it); Toast.makeText(ctx, "Saved Printer Config", Toast.LENGTH_SHORT).show(); section = "menu" }, onBack = { section = "menu" }, viewModel = viewModel)
                    }
                    "tax" -> {
                        val ctx = LocalContext.current
                        TaxConfigView(profile, onSave = { viewModel.saveProfile(it); Toast.makeText(ctx, "Saved Tax Config", Toast.LENGTH_SHORT).show(); section = "menu" }, onBack = { section = "menu" })
                    }
                    "security" -> {
                        AppLockConfigView(onBack = { section = "menu" })
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyLogoutSectionUnused(viewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val logoutState by viewModel.logoutState.collectAsStateWithLifecycle()
    val appLockViewModel: com.khanabook.lite.pos.ui.viewmodel.AppLockViewModel = hiltViewModel()
    val enteredPin by appLockViewModel.enteredPin.collectAsStateWithLifecycle()
    val pinError by appLockViewModel.errorMessage.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val isPinEnabled = remember(logoutState) { appLockViewModel.isPinEnabled() }

    LaunchedEffect(logoutState) { if (logoutState is com.khanabook.lite.pos.ui.viewmodel.LogoutState.LoggedOut) Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show() }

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
        AlertDialog(
            onDismissRequest = { viewModel.cancelLogout() },
            title = { Text("Unsynced Data Warning", color = DangerRed) },
            text = {
                Text(
                    if (isPinEnabled) {
                        "You have ${warning.totalCount} records not synced. Signing out now will remove them from this device. Enter your app PIN to continue."
                    } else {
                        "You have ${warning.totalCount} records not synced. Signing out now will remove them from this device."
                    }
                )
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        appLockViewModel.clearPin()
                        viewModel.cancelLogout()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                appLockViewModel.clearPin()
            },
            title = { Text("Enter App PIN", color = TextLight) },
            text = {
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
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        appLockViewModel.clearPin()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = DarkBrown2,
            title = { Text("Sign Out?", color = TextLight, style = MaterialTheme.typography.titleLarge) },
            text = { Text("You will be signed out of this device.", color = TextGold.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showConfirmDialog = false; viewModel.initiateLogout() }) {
                    Text("Sign Out", color = DangerRed, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    val iconSize = KhanaBookTheme.iconSize
    Column(modifier = Modifier.fillMaxWidth().padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        Text("Account Session", color = TextLight, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { showConfirmDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = DangerRed), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(iconSize.small))
            Spacer(modifier = Modifier.width(spacing.small))
            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ─── App Lock Configuration ──────────────────────────────────────────────────

@Composable
private fun LegacyAppLockConfigViewUnused(
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
            shape = RoundedCornerShape(12.dp)
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
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enable ->
                        if (enable) viewModel.startEnablePin()
                        else viewModel.startDisablePin()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SuccessGreen,
                        checkedTrackColor = SuccessGreen.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextGold.copy(alpha = 0.5f),
                        uncheckedTrackColor = DarkBrown1
                    )
                )
            }
        }

        if (isEnabled && setupState is com.khanabook.lite.pos.ui.viewmodel.PinSetupState.Idle) {
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("PIN Options", color = TextLight, style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { viewModel.startChangePin() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, BorderGold),
                        shape = RoundedCornerShape(8.dp)
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
