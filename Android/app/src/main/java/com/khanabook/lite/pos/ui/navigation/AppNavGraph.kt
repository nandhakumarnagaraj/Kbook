@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.ui.screens.*
import com.khanabook.lite.pos.ui.screens.auth.SignUpScreen
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel

@Composable
internal fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    menuViewModel: MenuViewModel,
    sessionManager: SessionManager,
    context: android.app.Activity,
    authenticatedStartDestination: () -> String
) {
    NavHost(
        navController = navController,
        startDestination = "splash",
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
        composable("splash") {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                },
                onNavigateToMain = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onNavigateToInitialSync = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onNavigateToAppLock = {
                    navController.navigate("app_lock") { popUpTo("splash") { inclusive = true } }
                }
            )
        }
        composable("app_lock") {
            AppLockScreen(
                onUnlock = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(authenticatedStartDestination()) {
                            popUpTo("app_lock") { inclusive = true }
                        }
                    }
                },
                onRecoverAccount = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("role_access") {
            RoleAccessScreen(
                role = sessionManager.getActiveUserRole(),
                onSignOut = {
                    authViewModel.logout()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("initial_sync") {
            InitialSyncScreen(
                onSyncCompleteNavigateToMain = {
                    // If this is a fresh restaurant with no menu, show quick start wizard
                    val destination = if (!sessionManager.isQuickStartCompleted()) {
                        "quick_start"
                    } else {
                        authenticatedStartDestination()
                    }
                    navController.navigate(destination) {
                        popUpTo("initial_sync") { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate("login") { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable("quick_start") {
            QuickStartScreen(
                onComplete = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("quick_start") { inclusive = true }
                    }
                },
                onSkip = {
                    sessionManager.setQuickStartCompleted(true)
                    sessionManager.setInitialSyncCompleted(true)
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("quick_start") { inclusive = true }
                    }
                }
            )
        }
        composable("background_reliability") {
            BackgroundReliabilityScreen(
                onDone = {
                    sessionManager.setBackgroundReliabilityPromptShown(true)
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("background_reliability") { inclusive = true }
                    }
                }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onSignUpClick = { navController.navigate("signup") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(authenticatedStartDestination()) {
                        popUpTo("signup") { inclusive = true }
                    }
                },
                onLoginClick = { navController.popBackStack() }
            )
        }
        composable(
            route = "main/{tab}?source={source}&highlightBillId={highlightBillId}&section={section}",
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
                onNewBill = { navController.navigate("new_bill") },
                onActiveOrder = { navController.navigate("active_orders") },
                onOpenActiveOrder = { draftBillId ->
                    navController.navigate("active_order_detail/$draftBillId")
                },
                onResumePendingPayment = { navController.navigate("new_bill?resumePayment=true") },
                onOpenSyncCenter = { navController.navigate("main/3?section=sync_center") },
                onOpenPrinterSettings = { navController.navigate("main/3?section=printer") },
                onSearchBill = { navController.navigate("search_bill") },
                onReprintKds = { navController.navigate("reprint_kds") },
                onCallCustomer = { navController.navigate("call_customer") },
                menuViewModel = menuViewModel,
                onScanClick = { categoryName ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("ocr_category_name", categoryName)
                    navController.navigate("ocr_scanner/menu_config")
                }
            )
        }
        composable(
            route = "new_bill?resumePayment={resumePayment}&draftBillId={draftBillId}&targetStep={targetStep}",
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
        composable("ocr_scanner/{source}") { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: "menu_config"
            val isBarcodeScan = source == "billing"
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
        composable("search_bill") {
            SearchScreen(
                title = context.getString(R.string.search_bill),
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable("active_orders") {
            ActiveOrdersScreen(
                onBack = { navController.popBackStack() },
                onOpenActiveOrder = { draftBillId ->
                    navController.navigate("active_order_detail/$draftBillId")
                },
                onCollectPayment = { draftBillId ->
                    navController.navigate("new_bill?draftBillId=$draftBillId&targetStep=3")
                }
            )
        }
        composable(
            route = "active_order_detail/{billId}",
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) {
            ActiveOrderDetailScreen(
                onBack = { navController.popBackStack() },
                onAddItems = { draftBillId ->
                    navController.navigate("new_bill?draftBillId=$draftBillId&targetStep=2")
                },
                onCollectPayment = { draftBillId ->
                    navController.navigate("new_bill?draftBillId=$draftBillId&targetStep=3")
                }
            )
        }
        composable("order_status") {
            SearchScreen(
                title = context.getString(R.string.check_order_status),
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable("call_customer") {
            CallCustomerScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable("reprint_kds") {
            ReprintKdsScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable("notifications") {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable("staff_permissions") {
            StaffPermissionScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("marketplace_orders") {
            MarketplaceOrdersScreen(
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(
            route = "easebuzz_payment/{billId}/{restaurantId}",
            arguments = listOf(
                navArgument("billId") { type = NavType.LongType },
                navArgument("restaurantId") { type = NavType.LongType }
            )
        ) {
            EasebuzzPaymentScreen(
                onBack = { navController.popBackStack() },
                onPaymentComplete = {
                    navController.navigate("main/0") {
                        popUpTo("easebuzz_payment/{billId}/{restaurantId}") { inclusive = true }
                    }
                }
            )
        }
    }
}
