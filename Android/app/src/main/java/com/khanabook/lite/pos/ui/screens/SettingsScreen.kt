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
