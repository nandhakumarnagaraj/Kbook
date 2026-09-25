@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.core.navigation
import com.khanabook.lite.pos.feature.notifications.ui.NotificationsScreen
import com.khanabook.lite.pos.feature.settings.ui.QuickStartScreen
import com.khanabook.lite.pos.feature.staff.ui.StaffPermissionScreen
import com.khanabook.lite.pos.feature.printing.ui.KitchenDisplayScreen
import com.khanabook.lite.pos.feature.printing.ui.ReprintKdsScreen
import com.khanabook.lite.pos.feature.sync.ui.InitialSyncScreen
import com.khanabook.lite.pos.feature.auth.ui.AppLockScreen
import com.khanabook.lite.pos.feature.auth.ui.RoleAccessScreen
import com.khanabook.lite.pos.feature.auth.ui.BackgroundReliabilityScreen
import com.khanabook.lite.pos.feature.auth.ui.LoginScreen
import com.khanabook.lite.pos.feature.billing.ui.SearchScreen
import com.khanabook.lite.pos.feature.billing.ui.CallCustomerScreen
import com.khanabook.lite.pos.feature.billing.ui.ActiveOrderDetailScreen
import com.khanabook.lite.pos.feature.billing.ui.ActiveOrderScreen
import com.khanabook.lite.pos.feature.billing.ui.ActiveOrdersScreen
import com.khanabook.lite.pos.feature.billing.ui.NewBillScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.ui.screens.*
import com.khanabook.lite.pos.feature.auth.ui.SignUpScreen
import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import com.khanabook.lite.pos.feature.menu.viewmodel.MenuViewModel
import com.khanabook.lite.pos.feature.menu.ui.OcrScannerScreen

/**
 * App navigation graph. All destinations are registered against [Routes] constants;
 * navigate() calls from screens use the same constants so the graph and the callers
 * can never drift apart.
 */
@Composable
internal fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    menuViewModel: MenuViewModel,
    sessionManager: SessionManager,
    context: android.app.Activity,
    authenticatedStartDestination: () -> String,
    startDestination: String = Routes.LOGIN
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 5 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 5 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 5 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 5 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
    ) {
        composable(Routes.APP_LOCK) {
            AppLockScreen(
                onUnlock = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(authenticatedStartDestination()) {
                            popUpTo(Routes.APP_LOCK) { inclusive = true }
                        }
                    }
                },
                onRecoverAccount = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ROLE_ACCESS) {
            RoleAccessScreen(
                role = sessionManager.getActiveUserRole(),
                onSignOut = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.INITIAL_SYNC) {
            InitialSyncScreen(
                onSyncCompleteNavigateToMain = {
                    // If this is a fresh restaurant with no menu, show quick start wizard
                    val destination = if (!sessionManager.isQuickStartCompleted()) {
                        Routes.QUICK_START
                    } else {
                        authenticatedStartDestination()
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.INITIAL_SYNC) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(Routes.QUICK_START) {
            QuickStartScreen(
                onComplete = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo(Routes.QUICK_START) { inclusive = true }
                    }
                },
                onSkip = {
                    sessionManager.setQuickStartCompleted(true)
                    sessionManager.setInitialSyncCompleted(true)
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo(Routes.QUICK_START) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.BACKGROUND_RELIABILITY) {
            BackgroundReliabilityScreen(
                onDone = {
                    sessionManager.setBackgroundReliabilityPromptShown(true)
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo(Routes.BACKGROUND_RELIABILITY) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onSignUpClick = { navController.navigate(Routes.SIGNUP) }
            )
        }
        composable(Routes.SIGNUP) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                },
                onLoginClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.MAIN_PATTERN,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "0"
                },
                navArgument("source") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("highlightBillId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("section") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val selectedTab = backStackEntry.arguments?.getString("tab")?.toIntOrNull() ?: 0
            val source = backStackEntry.arguments?.getString("source")
            val highlightBillId = backStackEntry.arguments?.getLong("highlightBillId")
                ?.takeIf { it > 0L }
            val section = backStackEntry.arguments?.getString("section")
            MainScreen(
                initialTab = selectedTab,
                initialSource = source,
                initialHighlightBillId = highlightBillId,
                initialSettingsSection = section,
                navController = navController,
                onNewBill = { navController.navigate(Routes.newBill()) },
                onActiveOrder = { navController.navigate(Routes.ACTIVE_ORDERS) },
                onOpenActiveOrder = { draftBillId ->
                    navController.navigate(Routes.activeOrderDetail(draftBillId))
                },
                onResumePendingPayment = { navController.navigate(Routes.newBill(resumePayment = true)) },
                onOpenSyncCenter = { navController.navigate(Routes.main(NavigationTabs.TAB_PROFILE, section = "sync_center")) },
                onOpenPrinterSettings = { navController.navigate(Routes.main(NavigationTabs.TAB_PROFILE, section = "printer")) },
                onSearchBill = { navController.navigate(Routes.SEARCH_BILL) },
                onReprintKds = { navController.navigate(Routes.REPRINT_KDS) },
                onCallCustomer = { navController.navigate(Routes.CALL_CUSTOMER) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                menuViewModel = menuViewModel,
                onScanClick = { categoryName ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("ocr_category_name", categoryName)
                    navController.navigate(Routes.ocrScanner(Routes.OCR_SOURCE_MENU))
                }
            )
        }
        composable(
            route = Routes.NEW_BILL_PATTERN,
            arguments = listOf(
                navArgument("resumePayment") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("draftBillId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("targetStep") {
                    type = NavType.IntType
                    defaultValue = 1
                }
            )
        ) { backStackEntry ->
            val resumePayment = backStackEntry.arguments?.getBoolean("resumePayment") == true
            val draftBillId = backStackEntry.arguments?.getLong("draftBillId") ?: -1L
            val targetStep = backStackEntry.arguments?.getInt("targetStep") ?: 1
            NewBillScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                resumePendingPayment = resumePayment,
                draftBillId = if (draftBillId == -1L) null else draftBillId,
                initialStep = targetStep
            )
        }
        composable(Routes.OCR_SCANNER_PATTERN) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: Routes.OCR_SOURCE_MENU
            val isBarcodeScan = source == Routes.OCR_SOURCE_BILLING
            val selectedCategoryName = if (!isBarcodeScan) {
                navController.previousBackStackEntry?.savedStateHandle?.get<String>("ocr_category_name")
            } else null
            
            OcrScannerScreen(
                selectedCategoryName = selectedCategoryName,
                viewModel = menuViewModel,
                navController = navController,
                returnBarcode = isBarcodeScan,
                onBack = {
                    if (!isBarcodeScan) {
                        navController.previousBackStackEntry?.savedStateHandle?.remove<String>("ocr_category_name")
                    }
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.SEARCH_BILL) {
            SearchScreen(
                title = context.getString(R.string.search_bill),
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(
            route = Routes.ACTIVE_ORDERS,
            deepLinks = listOf(navDeepLink { uriPattern = "khanabook://active_orders" })
        ) {
            ActiveOrdersScreen(
                onBack = { navController.popBackStack() },
                onOpenActiveOrder = { draftBillId ->
                    navController.navigate(Routes.activeOrderDetail(draftBillId))
                },
                onCollectPayment = { draftBillId ->
                    navController.navigate(Routes.newBill(draftBillId = draftBillId, targetStep = 3))
                }
            )
        }
        composable(
            route = Routes.ACTIVE_ORDER_DETAIL_PATTERN,
            arguments = listOf(navArgument("billId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "khanabook://bill/{billId}" })
        ) {
            ActiveOrderDetailScreen(
                onBack = { navController.popBackStack() },
                onAddItems = { draftBillId ->
                    navController.navigate(Routes.newBill(draftBillId = draftBillId, targetStep = 2))
                },
                onCollectPayment = { draftBillId ->
                    navController.navigate(Routes.newBill(draftBillId = draftBillId, targetStep = 3))
                }
            )
        }
        composable(Routes.CALL_CUSTOMER) {
            CallCustomerScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(Routes.REPRINT_KDS) {
            ReprintKdsScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(Routes.KITCHEN_DISPLAY) {
            KitchenDisplayScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(Routes.NOTIFICATIONS, deepLinks = listOf(navDeepLink { uriPattern = "khanabook://notifications" })) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(Routes.STAFF_PERMISSIONS) {
            StaffPermissionScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.EASEBUZZ_ONBOARDING,
            deepLinks = listOf(navDeepLink { uriPattern = "khanabook://easebuzz_onboarding" })
        ) {
            com.khanabook.lite.pos.feature.payments.ui.EasebuzzOnboardingScreen(
                onBack = { navController.popBackStack() },
                onOpenComplianceDocs = { navController.navigate(Routes.COMPLIANCE_DOCUMENTS) }
            )
        }
        composable(Routes.COMPLIANCE_DOCUMENTS) {
            com.khanabook.lite.pos.feature.onboarding.ui.ComplianceDocumentsScreen(
                onBack = { navController.popBackStack() },
                onOpenAgreement = { navController.navigate(Routes.MERCHANT_AGREEMENT) }
            )
        }
        composable(Routes.MERCHANT_AGREEMENT) {
            com.khanabook.lite.pos.feature.onboarding.ui.MerchantAgreementScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PAYMENT_LINK_PATTERN,
            arguments = listOf(
                navArgument("restaurantId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) {
            com.khanabook.lite.pos.feature.payments.ui.PaymentLinkScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
