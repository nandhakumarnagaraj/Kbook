package com.khanabook.lite.pos.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.ui.screens.*
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    private var lastBackPressTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge for better gesture navigation support
        enableEdgeToEdge()

        setContent {
            KhanaBookLiteTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val menuViewModel: MenuViewModel = hiltViewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val context = this

                // Root back handling (Double Back to Exit from Home)
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route
                
                // Only intercept back if we are on the Home screen or root MainScreen
                // This ensures system gestures work for sub-screens
                val isAtRoot = currentRoute == "main/{tab}" || currentRoute == "main/0"
                
                androidx.activity.compose.BackHandler(enabled = isAtRoot) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000) {
                        finish() 
                    } else {
                        Toast.makeText(context, context.getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                        lastBackPressTime = currentTime
                    }
                }

                // Authentication Observer
                LaunchedEffect(currentUser) {
                    val dest = navController.currentDestination?.route
                    if (currentUser == null && dest != null && dest != "login" && dest != "splash" && dest != "signup") {
                        navController.navigate("login") { 
                            popUpTo(0) { inclusive = true } 
                        }
                    }
                }

                NavHost(
                    navController = navController, 
                    startDestination = "splash",
                    enterTransition = { 
                        fadeIn(tween(400)) + slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth }, 
                            animationSpec = tween(400)
                        ) 
                    },
                    exitTransition = { 
                        fadeOut(tween(400)) + slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth }, 
                            animationSpec = tween(400)
                        ) 
                    },
                    popEnterTransition = { 
                        fadeIn(tween(400)) + slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth }, 
                            animationSpec = tween(400)
                        ) 
                    },
                    popExitTransition = { 
                        fadeOut(tween(400)) + slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(400)
                        ) 
                    }
                ) {
                    composable("splash") {
                        SplashScreen(
                            onNavigateToLogin = {
                                navController.navigate("login") { popUpTo("splash") { inclusive = true } }
                            },
                            onNavigateToMain = {
                                navController.navigate("main/0") { popUpTo("splash") { inclusive = true } }
                            },
                            onNavigateToInitialSync = {
                                navController.navigate("initial_sync") { popUpTo("splash") { inclusive = true } }
                            }
                        )
                    }
                    composable("initial_sync") {
                        InitialSyncScreen(
                            onSyncCompleteNavigateToMain = {
                                navController.navigate("main/0") { popUpTo("initial_sync") { inclusive = true } }
                            }
                        )
                    }
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                if (sessionManager.isInitialSyncCompleted()) {
                                    navController.navigate("main/0") { popUpTo("login") { inclusive = true } }
                                } else {
                                    navController.navigate("initial_sync") { popUpTo("login") { inclusive = true } }
                                }
                            },
                            onSignUpClick = { navController.navigate("signup") }
                        )
                    }
                    composable("signup") {
                        SignUpScreen(
                            onSignUpSuccess = {
                                if (sessionManager.isInitialSyncCompleted()) {
                                    navController.navigate("main/0") { popUpTo("signup") { inclusive = true } }
                                } else {
                                    navController.navigate("initial_sync") { popUpTo("signup") { inclusive = true } }
                                }
                            },
                            onLoginClick = { navController.popBackStack() }
                        )
                    }
                    composable("main/{tab}") { backStackEntry ->
                        val selectedTab = backStackEntry.arguments?.getString("tab")?.toIntOrNull() ?: 0
                        MainScreen(
                            initialTab = selectedTab,
                            navController = navController,
                            onNewBill = { navController.navigate("new_bill") },
                            onSearchBill = { navController.navigate("search_bill") },
                            onOrderStatus = { navController.navigate("order_status") },
                            onCallCustomer = { navController.navigate("call_customer") },
                            menuViewModel = menuViewModel,
                            onScanClick = { categoryName ->
                                navController.currentBackStackEntry?.savedStateHandle?.set("ocr_category_name", categoryName)
                                navController.navigate("ocr_scanner/menu_config")
                            }
                        )
                    }
                    composable("new_bill") {
                        NewBillScreen(
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize(),
                            navController = navController
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
                }
            }
        }
    }
}
