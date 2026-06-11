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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    var helpSearchQuery by remember { mutableStateOf("") }
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
            titleStyleExpanded = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            headerContent = {
                if (section == "help_support") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.kbBgSecondary)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.kbTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        BasicTextField(
                            value = helpSearchQuery,
                            onValueChange = { helpSearchQuery = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.kbTextPrimary),
                            cursorBrush = SolidColor(KbBrandSaffron),
                            singleLine = true,
                            decorationBox = { inner ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (helpSearchQuery.isEmpty()) {
                                        Text(
                                            "Search help articles...",
                                            color = MaterialTheme.kbTextTertiary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (section == "change_password") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.kbBgSecondary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = KbBrandSaffron,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        ) {
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Pane: Navigation Menu (40% width)
                    Box(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                    ) {
                        val showSecurityListOnLeft = section in setOf("security", "app_lock", "change_password", "help_support", "about_app", "ui_scale")
                        val lastSyncTs = remember(section) { viewModel.getLastSyncTimestamp() }
                        if (showSecurityListOnLeft) {
                            SettingsListView(onSelectItem = { section = it })
                        } else {
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
                    }

                    // Vertical Divider
                    VerticalDivider(
                        color = MaterialTheme.kbOutlineSubtle,
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxHeight()
                    )

                    // Right Pane: Active Detail Section (60% width)
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    ) {
                        when (section) {
                            "menu" -> {
                                ShopConfigView(profile, viewModel, authViewModel) { }
                            }
                            "shop" -> {
                                ShopConfigView(profile, viewModel, authViewModel) { }
                            }
                            "payment" -> {
                                PaymentConfigView(profile, onSave = {
                                    viewModel.saveProfile(it)
                                    toastScope.launch { KhanaToast.show(ctx.getString(R.string.toast_payment_settings_saved), ToastKind.Success) }
                                }, onBack = { })
                            }
                            "printer" -> {
                                PrinterConfigView(profile, onSave = {
                                    viewModel.saveProfile(it)
                                    toastScope.launch { KhanaToast.show(ctx.getString(R.string.toast_printer_settings_saved), ToastKind.Success) }
                                }, onBack = { }, viewModel = viewModel)
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
                                    },
                                    onBack = { },
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
                                AppLockView()
                            }
                            "app_lock" -> {
                                AppLockView()
                            }
                            "change_password" -> {
                                ChangePasswordView(onBack = { section = "security" })
                            }
                            "help_support" -> {
                                HelpSupportView(helpSearchQuery)
                            }
                            "about_app" -> {
                                AboutAppView()
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
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
                            HelpSupportView(helpSearchQuery)
                        }
                        "about_app" -> {
                            AboutAppView()
                        }
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text("UI Scale", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    "Adjust the overall size of text and UI elements.",
                    color = MaterialTheme.kbSecondary.copy(alpha = 0.7f),
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
                            color = if (i == sliderIndex) MaterialTheme.kbPrimary else MaterialTheme.kbSecondary.copy(alpha = 0.5f),
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
                        inactiveTrackColor = MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(spacing.small))

                Text(
                    text = "Preview",
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(spacing.small))

                KhanaBookCard(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgSecondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            "Sample Item",
                            color = MaterialTheme.kbTextPrimary,
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
                            color = MaterialTheme.kbSecondary.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraLarge))
    }
}
