@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui.screens.settings


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
import com.khanabook.lite.pos.ui.screens.applock.AboutAppView
import com.khanabook.lite.pos.ui.screens.applock.AppLockView
import com.khanabook.lite.pos.ui.screens.applock.ChangePasswordView
import com.khanabook.lite.pos.ui.screens.applock.HelpSupportView
import com.khanabook.lite.pos.ui.screens.applock.SyncCenterView
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.ui.screens.InteractionFeedbackView
import com.khanabook.lite.pos.ui.screens.MenuConfigurationScreen
import com.khanabook.lite.pos.ui.screens.PaymentConfigView
import com.khanabook.lite.pos.ui.screens.PrinterConfigView
import com.khanabook.lite.pos.ui.screens.SettingsHomeSection
import com.khanabook.lite.pos.ui.screens.SettingsListView
import com.khanabook.lite.pos.ui.screens.ShopConfigView
import com.khanabook.lite.pos.ui.screens.InventoryScreen
import com.khanabook.lite.pos.ui.screens.StaffPermissionScreen
import com.khanabook.lite.pos.ui.screens.TaxConfigView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navController: NavController,
    initialSection: String = "menu",
    onScanClick: (String?) -> Unit = {},
    menuViewModel: MenuViewModel,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    logoutViewModel: com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val saveProfileSuccess by viewModel.saveProfileSuccess.collectAsStateWithLifecycle()
    val saveProfileError by viewModel.saveProfileError.collectAsStateWithLifecycle()
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    var pendingSaveSection by remember { mutableStateOf<String?>(null) }
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

    val ctx = LocalContext.current

    LaunchedEffect(saveProfileSuccess, pendingSaveSection) {
        val savedSection = pendingSaveSection
        if (saveProfileSuccess && savedSection != null) {
            val message = when (savedSection) {
                "payment" -> ctx.getString(R.string.toast_payment_settings_saved)
                "printer" -> ctx.getString(R.string.toast_printer_settings_saved)
                "tax" -> ctx.getString(R.string.toast_tax_settings_saved)
                else -> ctx.getString(R.string.toast_profile_saved)
            }
            KhanaToast.show(message, ToastKind.Success)
            viewModel.clearSaveProfileState()
            pendingSaveSection = null
            section = "menu"
        }
    }

    LaunchedEffect(saveProfileError, pendingSaveSection) {
        val error = saveProfileError
        if (error != null && pendingSaveSection != null) {
            KhanaToast.show(error, ToastKind.Error)
            viewModel.clearSaveProfileState()
            pendingSaveSection = null
        }
    }

    val settingsSubSections = setOf(
        "app_lock",
        "change_password",
        "interaction_feedback",
        "help_support",
        "about_app",
        "sync_center"
    )

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

    if (section == "staff_permissions") {
        StaffPermissionScreen(
            onBack = { section = "menu" }
        )
        return
    }

    if (section == "inventory") {
        InventoryScreen(
            onBack = { section = "menu" }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
            .imePadding()
    ) {
        KhanaBookScreenScaffold(
            title = when (section) {
                "shop" -> "Restaurant Configuration"
                "payment" -> "Payment Configuration"
                "printer" -> "Printer Configuration"
                "tax" -> "Tax Configuration"
                "ui_scale" -> "Display"
                "interaction_feedback" -> "Interaction Feedback"
                "security" -> "Settings"
                "app_lock" -> "App Lock"
                "change_password" -> "Change Password"
                "help_support" -> "Help & Support"
                "sync_center" -> "Sync Center"
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
                        val saveProfileLoading by viewModel.saveProfileLoading.collectAsStateWithLifecycle()
                        PaymentConfigView(profile, saveProfileLoading = saveProfileLoading, onSave = {
                            pendingSaveSection = "payment"
                            viewModel.saveProfile(it)
                        }, onBack = { section = "menu" },
                            onNavigateToOnboarding = { navController.navigate("easebuzz_onboarding") }
                        )
                    }
                    "printer" -> {
                        PrinterConfigView(profile, onSave = {
                            pendingSaveSection = "printer"
                            viewModel.saveProfile(it)
                        }, onBack = { section = "menu" }, viewModel = viewModel)
                    }
                    "tax" -> {
                        TaxConfigView(profile, onSave = {
                            pendingSaveSection = "tax"
                            viewModel.saveProfile(it)
                        }, onBack = { section = "menu" })
                    }
                    "ui_scale" -> {
                        DisplayScaleView(viewModel = viewModel)
                    }
                    "interaction_feedback" -> {
                        InteractionFeedbackView()
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
                        HelpSupportView(viewModel)
                    }
                    "sync_center" -> {
                        SyncCenterView(viewModel)
                    }
                    "about_app" -> {
                        AboutAppView()
                    }
                }
            }
        }
    }
}

