@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.navigation

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
import android.content.Intent
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.ui.MainActivity
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.ui.screens.*
import com.khanabook.lite.pos.ui.screens.auth.SignUpScreen
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import com.khanabook.lite.pos.ui.viewmodel.PaymentLinkViewModel

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
        startDestination = "branded_start",
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
        composable(
            "branded_start",
            // Appear instantly at the same center position as the system splash
            // icon so the handoff reads as one continuous branded startup, not a
            // second screen sliding in.
            enterTransition = { EnterTransition.None }
        ) {
            BrandedStartFrame()
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
            route = "easebuzz_payment/{restaurantId}/{billId}/{amount}",
            arguments = listOf(
                navArgument("restaurantId") { type = NavType.LongType },
                navArgument("billId") { type = NavType.LongType },
                navArgument("amount") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            EasebuzzPaymentScreen(
                onBack = { navController.popBackStack() },
                onPaymentComplete = { gatewayTxnId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("gatewayTxnId", gatewayTxnId)

                    // Bring MainActivity to the front and close PWECheckoutActivity
                    // or any Custom Tab left on top
                    val clearTopIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(clearTopIntent)

                    navController.popBackStack()
                }
            )
        }
        composable(
            route = "payment_link/{restaurantId}",
            arguments = listOf(navArgument("restaurantId") { type = NavType.LongType })
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getLong("restaurantId") ?: 0L
            val handle = backStackEntry.savedStateHandle
            handle.set("restaurantId", restaurantId)
            PaymentLinkScreen(
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel<PaymentLinkViewModel>(backStackEntry)
            )
        }
        composable("easebuzz_onboarding") {
            com.khanabook.lite.pos.ui.screens.EasebuzzOnboardingScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
