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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
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
    initialSection: String? = null,
    onInitialSectionConsumed: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    logoutViewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("menu") }
    // Honor a one-shot request to open a specific section (e.g. Home "Menu" → Menu Configuration).
    LaunchedEffect(initialSection) {
        if (initialSection != null) {
            section = initialSection
            onInitialSectionConsumed()
        }
    }
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
        onBottomBarVisibilityChange(section != "menu_config" && section != "ui_scale")
        onDispose {
            if (section == "menu_config" || section == "ui_scale") {
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

    if (section == "ui_scale" && !isWideScreen) {
        DisplaySettingsMobileScreen(
            viewModel = viewModel,
            onBack = { section = "menu" }
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
                "ui_scale" -> "Display Settings"
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
                if (section == "menu" && !isWideScreen) {
                    val lastSyncTs = remember(section) { viewModel.getLastSyncTimestamp() }
                    ProfileCard(
                        user = currentUser,
                        profile = profile,
                        lastSyncTimestamp = lastSyncTs,
                        onEditClick = { section = "shop" }
                    )
                } else if (section == "help_support") {
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
                                }, onBack = { }, onOpenEasebuzzKyc = { navController.navigate("easebuzz_kyc") }, onOpenMarketplaceOrders = { navController.navigate("marketplace_orders") })
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
                                DisplayScaleView(viewModel = viewModel, onBack = { section = "menu" })
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
                            }, onBack = { section = "menu" }, onOpenEasebuzzKyc = { navController.navigate("easebuzz_kyc") }, onOpenMarketplaceOrders = { navController.navigate("marketplace_orders") })
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
                            DisplayScaleView(viewModel = viewModel, onBack = { section = "menu" })
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
private fun DensityGridIcon(densityType: String, isSelected: Boolean) {
    val tint = if (isSelected) KbBrandSaffron else MaterialTheme.kbOutlineBold
    val spacing = when (densityType) {
        "compact" -> 6.dp
        "spacious" -> 2.dp
        else -> 4.dp
    }
    val squareSize = when (densityType) {
        "compact" -> 6.dp
        "spacious" -> 14.dp
        else -> 10.dp
    }
    
    Box(
        modifier = Modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                Box(modifier = Modifier.size(squareSize).background(tint, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.size(squareSize).background(tint, RoundedCornerShape(2.dp)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                Box(modifier = Modifier.size(squareSize).background(tint, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.size(squareSize).background(tint, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
fun DisplaySettingsMobileScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kbBgPrimary)
    ) {
        // Brand violet header (matches every other screen header)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.kbHeaderGradient)
                .statusBarsPadding()
                .padding(vertical = 16.dp, horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = KbOpacity.Pressed), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Text(
                text = "Display Settings",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Content
        DisplayScaleView(
            viewModel = viewModel,
            onBack = onBack,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun DisplayScaleView(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val currentScale by viewModel.displayScaleState.collectAsStateWithLifecycle()
    val currentDensity by viewModel.layoutDensityState.collectAsStateWithLifecycle()
    val currentShowAnimations by viewModel.showOrderAnimationsState.collectAsStateWithLifecycle()
    val currentHaptic by viewModel.hapticFeedbackEnabledState.collectAsStateWithLifecycle()
    val currentBold by viewModel.boldOrderNumbersState.collectAsStateWithLifecycle()

    var scale by remember(currentScale) { mutableStateOf(currentScale) }
    var density by remember(currentDensity) { mutableStateOf(currentDensity) }
    var showAnimations by remember(currentShowAnimations) { mutableStateOf(currentShowAnimations) }
    var haptic by remember(currentHaptic) { mutableStateOf(currentHaptic) }
    var bold by remember(currentBold) { mutableStateOf(currentBold) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: TEXT SIZE
        Text(
            text = "TEXT SIZE",
            color = KbBrandSaffron,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.kbBgCard
            ),
            shape = KbShape.Large,
            elevation = CardDefaults.cardElevation(defaultElevation = KbElevation.Low)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Text size selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scaleLabels = listOf("Small", "Default", "Large", "XL")
                    val scaleValues = listOf(0.85f, 1.0f, 1.15f, 1.3f)
                    
                    scaleValues.forEachIndexed { index, valScale ->
                        val isSelected = scale == valScale
                        Button(
                            onClick = { scale = valScale },
                            modifier = Modifier
                                .weight(1f)
                                .height(KbButtonSize.HeightSmall),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) KbBrandSaffron else MaterialTheme.kbBgSecondary,
                                contentColor = if (isSelected) Color.White else MaterialTheme.kbTextSecondary
                            ),
                            shape = KbShape.Small,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = scaleLabels[index],
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview Text Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(KbShape.Large)
                        .background(MaterialTheme.kbBgSecondary)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Preview text",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)) {
                                    append("Order #KB-001")
                                }
                                append(" · Table 4 · ₹240.00")
                            },
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (16 * scale).sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: LAYOUT DENSITY
        Text(
            text = "LAYOUT DENSITY",
            color = KbBrandSaffron,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val densities = listOf("compact", "default", "spacious")
            val densityLabels = listOf("Compact", "Default", "Spacious")
            
            densities.forEachIndexed { index, densityType ->
                val isSelected = density == densityType
                val activeThemeColor = KbBrandSaffron

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(108.dp)
                        .clickable { density = densityType },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.kbBgCard
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) activeThemeColor else MaterialTheme.kbOutlineSubtle
                    ),
                    shape = KbShape.Large,
                    elevation = CardDefaults.cardElevation(defaultElevation = KbElevation.Low)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        DensityGridIcon(densityType = densityType, isSelected = isSelected)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = densityLabels[index],
                            color = if (isSelected) activeThemeColor else MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: OTHER OPTIONS
        Text(
            text = "OTHER OPTIONS",
            color = KbBrandSaffron,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.kbBgCard
            ),
            shape = KbShape.Large,
            elevation = CardDefaults.cardElevation(defaultElevation = KbElevation.Low)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Show Order Animations
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Show Order Animations",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "Smooth transitions & effects",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    KhanaBookSwitch(
                        checked = showAnimations,
                        onCheckedChange = { showAnimations = it },
                        checkedTrackColor = KbBrandSaffron,
                        uncheckedTrackColor = MaterialTheme.kbOutlineBold
                    )
                }

                HorizontalDivider(color = MaterialTheme.kbOutlineSubtle)

                // Haptic Feedback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Haptic Feedback",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "Vibration on tap",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    KhanaBookSwitch(
                        checked = haptic,
                        onCheckedChange = { haptic = it },
                        checkedTrackColor = KbBrandSaffron,
                        uncheckedTrackColor = MaterialTheme.kbOutlineBold
                    )
                }

                HorizontalDivider(color = MaterialTheme.kbOutlineSubtle)

                // Bold Order Numbers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Bold Order Numbers",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "Easier to scan quickly",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    KhanaBookSwitch(
                        checked = bold,
                        onCheckedChange = { bold = it },
                        checkedTrackColor = KbBrandSaffron,
                        uncheckedTrackColor = MaterialTheme.kbOutlineBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // CTA BUTTON: Apply Settings
        PrimaryButton(
            text = "Apply Settings",
            onClick = {
                viewModel.updateDisplayScale(scale)
                viewModel.updateLayoutDensity(density)
                viewModel.updateShowOrderAnimations(showAnimations)
                viewModel.updateHapticFeedbackEnabled(haptic)
                viewModel.updateBoldOrderNumbers(bold)

                coroutineScope.launch {
                    KhanaToast.show("Settings applied successfully", ToastKind.Success)
                }
                onBack()
            }
        )

        Spacer(modifier = Modifier.height(36.dp))
    }
}


