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
import com.khanabook.lite.pos.feature.settings.ui.TaxConfigView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    navController: NavController,
    initialSection: String = "menu",
    deepEntry: Boolean = false,
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
    val logoUploadSuccess by viewModel.logoUploadSuccess.collectAsStateWithLifecycle()
    val logoUploadError by viewModel.logoUploadError.collectAsStateWithLifecycle()
    var section by rememberSaveable(initialSection) { mutableStateOf(initialSection) }
    // The section the user actually came from, not a hard-coded "security": a
    // section opened from Settings home returns to Settings home, one opened from
    // the App Settings list returns to that list (BUG-020).
    var backTarget by rememberSaveable { mutableStateOf("menu") }
    fun selectSection(value: String) {
        if (value != section) backTarget = section
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
            // A logo upload has its own inline success/error panel on the shop screen.
            // Navigating away immediately would hide it before the owner can read it.
            if (savedSection == "shop" && (logoUploadSuccess != null || logoUploadError != null)) {
                return@LaunchedEffect
            }
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
    var shopDirty by remember { mutableStateOf(false) }
    var showShopDiscardDialog by remember { mutableStateOf(false) }

    fun goBack() {
        when {
            section == "menu" -> onBack()
            // ShopConfigView owns the dirty flag, but this composable renders the
            // header arrow, so both back paths must ask the same question (BUG-019).
            section == "shop" && shopDirty -> showShopDiscardDialog = true
            // Entered straight from another tab (Home's sync/printer cards): leave
            // Settings instead of walking through screens the user skipped.
            deepEntry && section == initialSection -> {
                onSectionChanged("menu")
                onBack()
            }
            else -> {
                section = backTarget
                onSectionChanged(backTarget)
            }
        }
    }

    BackHandler { goBack() }
    DisposableEffect(section) {
        onBottomBarVisibilityChange(section != "menu_config" && section != "shop")
        onDispose {
            if (section == "menu_config" || section == "shop") {
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
    // Header-arrow counterpart of ShopConfigView's own BackHandler dialog, so
    // discarding from the arrow is never silent (BUG-019).
    if (showShopDiscardDialog) {
        KhanaBookDialog(
            onDismissRequest = { showShopDiscardDialog = false },
            title = "Unsaved Changes",
            message = "You have unsaved changes. Discard them?"
        ) {
            TextButton(onClick = { showShopDiscardDialog = false }) {
                Text("Keep Editing", color = PrimaryGold, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = {
                showShopDiscardDialog = false
                shopDirty = false
                selectSection("menu")
            }) {
                Text("Discard", color = DangerRed, style = MaterialTheme.typography.labelLarge)
            }
        }
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
            onBack = { goBack() },
            titleStyleCompact = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            titleStyleExpanded = if (section == "menu") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            titleAlignment = if (section == "shop") TextAlign.Start else TextAlign.Center
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
                        ShopConfigView(profile, viewModel, authViewModel, onBack = { shopDirty = false; selectSection("menu") }, readOnly = !isOwner, onDirtyChange = { shopDirty = it }, onSaved = {
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
                            viewModel.saveProfile(it, confirmEasebuzzToggle = true)
                        }, onBack = { selectSection("menu") },
                            onNavigateToOnboarding = { navController.navigate(Routes.ONLINE_PAYMENTS_SETUP) },
                            onSectionSelected = ::selectSection,
                            readOnly = !isOwner
                        )
                    }
                    "printer" -> {
                        // Every option on this screen writes through SettingsViewModel as it is
                        // changed, so there is no Save/Cancel action to wire up here.
                        PrinterConfigView(profile, viewModel = viewModel)
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
                                // Preferences only here — the full Notification Center
                                // (with the list) stays on the Home bell icon.
                                navController.navigate(Routes.NOTIFICATIONS_PREFERENCES)
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
                        AboutAppView(
                            onWatchTutorials = { navController.navigate(Routes.TUTORIALS) }
                        )
                    }
                    "compliance_documents" -> {
                        com.khanabook.lite.pos.feature.onboarding.ui.ComplianceDocumentsScreen(
                            onBack = { selectSection("menu") }
                        )
                    }
                }
            }
        }
    }
}
