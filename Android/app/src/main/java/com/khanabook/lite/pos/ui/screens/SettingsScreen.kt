@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens


import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel

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
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val isWideScreen = !layout.isCompact

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

    val toastScope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val settingsSubSections = setOf("app_lock", "change_password", "help_support", "about_app")

    BackHandler {
        when {
            section in settingsSubSections -> section = "security"
            section != "menu" -> section = "menu"
            else -> onBack()
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
            .background(MaterialTheme.kbBgGradient)
            .navigationBarsPadding()
            .imePadding()
    ) {
        KhanaBookScreenScaffold(
            title = when (section) {
                "shop" -> "Shop Configuration"
                "payment" -> "Payment Configuration"
                "printer" -> "Printer Configuration"
                "tax" -> "Tax Configuration"
                "ui_scale" -> "Display"
                "security" -> "Settings"
                "app_lock" -> "App Lock"
                "change_password" -> "Change Password"
                "help_support" -> "Help & Support"
                "about_app" -> "About App"
                "menu" -> "Profile"
                else -> "Profile"
            },
            onBack = {
                when {
                    section in settingsSubSections -> section = "security"
                    section != "menu" -> section = "menu"
                    else -> onBack()
                }
            },
            titleStyleCompact = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            titleStyleExpanded = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
        ) {
            Box(modifier = Modifier.weight(1f)) {
                // Re-read timestamp each time we return to the settings home (section changes back to "menu")
                // so the user sees an up-to-date timestamp after a sync completes.
                val lastSyncTs = remember(section) { viewModel.getLastSyncTimestamp() }
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
                        PaymentConfigView(profile, onSave = {
                            viewModel.saveProfile(it)
                            toastScope.launch { KhanaToast.show(ctx.getString(R.string.toast_payment_settings_saved), ToastKind.Success) }
                            section = "menu"
                        }, onBack = { section = "menu" })
                    }
                    "printer" -> {
                        PrinterConfigView(profile, onSave = {
                            viewModel.saveProfile(it)
                            toastScope.launch { KhanaToast.show(ctx.getString(R.string.toast_printer_settings_saved), ToastKind.Success) }
                            section = "menu"
                        }, onBack = { section = "menu" }, viewModel = viewModel)
                    }
                    "tax" -> {
                        val lookupState by viewModel.lookupLoading.collectAsState()
                        val lookupError by viewModel.lookupError.collectAsState()
                        val lookupResult by viewModel.lookupResult.collectAsState()
                        TaxConfigView(
                            profile = profile,
                            onSave = {
                                viewModel.saveProfile(it)
                                toastScope.launch { KhanaToast.show(ctx.getString(R.string.toast_tax_settings_saved), ToastKind.Success) }
                                section = "menu"
                            },
                            onBack = { section = "menu" },
                            lookupState = LookupUiState(
                                loading = lookupState,
                                error = lookupError,
                                result = lookupResult?.let { LookupResult(it.businessName, it.address, it.fssaiNo, it.gstin) }
                            ),
                            onLookupGst = { viewModel.lookupGst(it) },
                            onLookupFssai = { viewModel.lookupFssai(it) },
                            onLookupBoth = { gst, fssai -> viewModel.lookupBoth(gst, fssai) },
                            onApplyLookup = { result ->
                                val current = profile ?: return@TaxConfigView
                                viewModel.saveProfile(
                                    current.copy(
                                        shopName = result.businessName ?: current.shopName,
                                        shopAddress = result.address ?: current.shopAddress,
                                        gstin = result.gstin ?: current.gstin,
                                        fssaiNumber = result.fssaiNo ?: current.fssaiNumber,
                                        isSynced = false,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                toastScope.launch { KhanaToast.show(ctx.getString(R.string.toast_lookup_applied), ToastKind.Success) }
                            },
                            onClearLookup = { viewModel.clearLookupResult() }
                        )
                    }
                    "ui_scale" -> {
                        DisplayScaleView(viewModel = viewModel)
                    }
                    "security" -> {
                        SettingsListView(onSelectItem = { section = it })
                    }
                    "app_lock" -> {
                        AppLockView()
                    }
                    "change_password" -> {
                        ChangePasswordView(onBack = { section = "security" })
                    }
                    "help_support" -> {
                        HelpSupportView()
                    }
                    "about_app" -> {
                        AboutAppView()
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayScaleView(viewModel: SettingsViewModel) {
    val spacing = KhanaBookTheme.spacing
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
            shape = RoundedCornerShape(12.dp)
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
                            color = if (i == sliderIndex) MaterialTheme.kbPrimary else TextGold.copy(alpha = 0.5f),
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
                        thumbColor = MaterialTheme.kbPrimary,
                        activeTrackColor = MaterialTheme.kbPrimary,
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
                    shape = RoundedCornerShape(8.dp)
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
                            color = MaterialTheme.kbSecondary,
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
