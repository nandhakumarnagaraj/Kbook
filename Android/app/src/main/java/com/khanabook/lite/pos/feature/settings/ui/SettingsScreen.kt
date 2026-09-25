@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.feature.settings.ui
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.navigation.Routes
import com.khanabook.lite.pos.core.theme.*

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
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.auth.ui.AboutAppView
import com.khanabook.lite.pos.feature.auth.ui.AppLockView
import com.khanabook.lite.pos.feature.auth.ui.ChangePasswordView
import com.khanabook.lite.pos.feature.auth.ui.HelpSupportView
import com.khanabook.lite.pos.feature.sync.ui.SyncCenterView
import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import com.khanabook.lite.pos.feature.menu.viewmodel.MenuViewModel
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.feature.staff.ui.InteractionFeedbackView
import com.khanabook.lite.pos.feature.menu.ui.MenuConfigurationScreen
import com.khanabook.lite.pos.feature.payments.ui.PaymentConfigView
import com.khanabook.lite.pos.feature.printing.ui.PrinterConfigView
import com.khanabook.lite.pos.feature.settings.ui.SettingsHomeSection
import com.khanabook.lite.pos.feature.auth.ui.SettingsListView
import com.khanabook.lite.pos.feature.settings.ui.ShopConfigView
import com.khanabook.lite.pos.feature.inventory.ui.InventoryScreen
import com.khanabook.lite.pos.feature.settings.ui.TaxConfigView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navController: NavController,
    initialSection: String = "menu",
    onSectionChanged: (String) -> Unit = {},
    onScanClick: (String?) -> Unit = {},
    menuViewModel: MenuViewModel,
    onBottomBarVisibilityChange: (Boolean) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    logoutViewModel: com.khanabook.lite.pos.feature.auth.viewmodel.LogoutViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    // Configuration writes on the device are owner-only; staff read restaurant /
    // menu / payment / tax configuration but may write printer and app settings.
    val isOwner = currentUser?.role.equals("OWNER", ignoreCase = true)
    val saveProfileSuccess by viewModel.saveProfileSuccess.collectAsStateWithLifecycle()
    val saveProfileError by viewModel.saveProfileError.collectAsStateWithLifecycle()
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    fun selectSection(value: String) {
        section = value
        onSectionChanged(value)
    }
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
    val toastScope = rememberCoroutineScope()
    LaunchedEffect(saveProfileSuccess, pendingSaveSection) {
        val savedSection = pendingSaveSection
        if (saveProfileSuccess && savedSection != null) {
            val message = when (savedSection) {
                "payment" -> ctx.getString(R.string.toast_payment_settings_saved)
                "printer" -> ctx.getString(R.string.toast_printer_settings_saved)
                "tax" -> ctx.getString(R.string.toast_tax_settings_saved)
                else -> ctx.getString(R.string.toast_profile_saved)
            }
            // Fire-and-forget (competitor pattern): the toast must not gate the
            // navigation below. KhanaToast.show suspends for the snackbar's whole
            // 4s lifecycle, so awaiting it here delayed the return to menu until
            // after the toast disappeared.
            toastScope.launch { KhanaToast.show(message, ToastKind.Success) }
            viewModel.clearSaveProfileState()
            pendingSaveSection = null
            selectSection("menu")
        }
    }
    LaunchedEffect(saveProfileError, pendingSaveSection) {
        val error = saveProfileError
        if (error != null && pendingSaveSection != null) {
            toastScope.launch { KhanaToast.show(error, ToastKind.Error) }
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
            section in settingsSubSections -> selectSection("security")
            section != "menu" -> selectSection("menu")
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
            onBackClick = { selectSection("menu") },
            viewModel = menuViewModel
        )
        return
    }
    if (section == "inventory") {
        InventoryScreen(
            onBack = { selectSection("menu") }
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        KhanaBookScreenScaffold(
            title = when (section) {
                "shop" -> "Restaurant Configuration"
                "payment" -> "Payment Configuration"
                "printer" -> "Printer Configuration"
                "tax" -> "Tax Configuration"
                "ui_scale" -> "Display"
                "interaction_feedback" -> "Interaction Feedback"
                "security" -> "App Settings"
                "app_lock" -> "App Lock"
                "change_password" -> "Change Password"
                "help_support" -> "Help & Support"
                "sync_center" -> "Sync Center"
                "about_app" -> "About App"
                "menu" -> "Settings"
                else -> "Settings"
            },
            onBack = {
                when {
                    section in settingsSubSections -> selectSection("security")
                    section != "menu" -> selectSection("menu")
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
                            onSectionSelected = ::selectSection
                        )
                    }
                    "shop" -> {
                        ShopConfigView(profile, viewModel, authViewModel, onBack = { selectSection("menu") }, readOnly = !isOwner, onSaved = {
                            // Route the shop save through the same success flow as the other
                            // sections (toast + return to menu) instead of a private effect
                            // inside ShopConfigView, which could be disposed mid-save by the
                            // recomposition triggered when the save itself updates the profile
                            // (orderPaymentFlowMode re-keys remembered state).
                            authViewModel.clearOtpStatus()
                            pendingSaveSection = "shop"
                        })
                    }
                    "payment" -> {
                        val saveProfileLoading by viewModel.saveProfileLoading.collectAsStateWithLifecycle()
                        PaymentConfigView(profile, saveProfileLoading = saveProfileLoading, onSave = {
                            pendingSaveSection = "payment"
                            viewModel.saveProfile(it)
                        }, onBack = { selectSection("menu") },
                            onNavigateToOnboarding = { navController.navigate(Routes.EASEBUZZ_ONBOARDING) },
                            onSectionSelected = ::selectSection,
                            readOnly = !isOwner
                        )
                    }
                    "printer" -> {
                        val saveProfileLoading by viewModel.saveProfileLoading.collectAsStateWithLifecycle()
                        PrinterConfigView(profile, onSave = {
                            pendingSaveSection = "printer"
                            viewModel.savePrinterSettingsLocally(it)
                        }, onBack = { selectSection("menu") }, viewModel = viewModel, isSaving = saveProfileLoading)
                    }
                    "tax" -> {
                        val saveProfileLoading by viewModel.saveProfileLoading.collectAsStateWithLifecycle()
                        TaxConfigView(profile, onSave = {
                            pendingSaveSection = "tax"
                            viewModel.saveProfile(it)
                        }, onBack = { selectSection("menu") }, readOnly = !isOwner, isSaving = saveProfileLoading)
                    }
                    "ui_scale" -> {
                        DisplayScaleView(viewModel = viewModel)
                    }
                    "interaction_feedback" -> {
                        InteractionFeedbackView()
                    }
                    "security" -> {
                        SettingsListView(onSelectItem = { selectedItem ->
                            if (selectedItem == "notifications") {
                                navController.navigate(Routes.NOTIFICATIONS)
                            } else {
                                selectSection(selectedItem)
                            }
                        })
                    }
                    "app_lock" -> {
                        AppLockView()
                    }
                    "change_password" -> {
                        ChangePasswordView(onBack = { selectSection("security") })
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
                    "merchant_agreement" -> {
                        com.khanabook.lite.pos.feature.onboarding.ui.MerchantAgreementScreen(
                            onBack = { selectSection("menu") },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "compliance_documents" -> {
                        com.khanabook.lite.pos.feature.onboarding.ui.ComplianceDocumentsScreen(
                            onBack = { selectSection("menu") },
                            onOpenAgreement = { selectSection("merchant_agreement") }
                        )
                    }
                }
            }
        }
    }
}
