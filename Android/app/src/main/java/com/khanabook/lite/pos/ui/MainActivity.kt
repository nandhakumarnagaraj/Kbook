@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSnackbarHost
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.domain.manager.PaymentReturnManager
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.manager.TrustedExternalAppReturn
import com.khanabook.lite.pos.ui.screens.*
import com.khanabook.lite.pos.ui.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.ui.theme.globalIsDark
import com.khanabook.lite.pos.ui.theme.KbMotion
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var easebuzzSdkPaymentRepository: com.khanabook.lite.pos.data.repository.EasebuzzSdkPaymentRepository
    @Inject lateinit var easebuzzPaymentRepository: com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
    private var lastBackPressTime: Long = 0

    companion object {
        private const val UI_SCALE_TAG = "UI_SCALE_DEBUG"
        // Shared flow so onNewIntent can push deep link routes into Compose
        private val _pendingNavRoute = MutableStateFlow<String?>(null)
        val pendingNavRoute: StateFlow<String?> = _pendingNavRoute.asStateFlow()
    }

    /**
     * Pin fontScale to 1.0 at the Context level so that:
     *  1. Compose's root LocalDensity is initialised with fontScale=1f (not the system value).
     *  2. Any View-based component (dialogs, popups, EditText) that reads
     *     context.resources.configuration.fontScale also sees 1f.
     *  3. Android 16's compatibility-mode container cannot inject a non-1 fontScale
     *     for portrait-locked apps, which was the root cause of fonts appearing bigger.
     *
     * The LocalDensity override in KhanaBookLiteTheme remains as a belt-and-braces guard
     * for nested Dialog composables that create their own composition context.
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = if (Build.VERSION.SDK_INT >= 36) 1f else config.fontScale
        if (BuildConfig.DEBUG) {
            val metrics = newBase.resources.displayMetrics
            Log.d(
                UI_SCALE_TAG,
                "attachBaseContext before override: " +
                    "fontScale=${newBase.resources.configuration.fontScale}, " +
                    "density=${metrics.density}, scaledDensity=${metrics.scaledDensity}, " +
                    "densityDpi=${metrics.densityDpi}, " +
                    "screenWidthDp=${newBase.resources.configuration.screenWidthDp}, " +
                    "screenHeightDp=${newBase.resources.configuration.screenHeightDp}, " +
                    "smallestWidthDp=${newBase.resources.configuration.smallestScreenWidthDp}"
            )
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /** Route a notification tap to the correct screen. Called after nav is set up. */
    private fun extractNotificationRoute(intent: Intent?): String? {
        val type = intent?.getStringExtra("notification_type") ?: return null
        return when (type) {
            "payment_received", "refund" -> "main/1"   // Orders tab
            "marketplace_order"          -> "marketplace_orders"
            "kyc", "settlement", "system" -> "notifications"
            else                          -> "notifications"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        setTheme(R.style.Theme_KhanaBookLite)
        super.onCreate(savedInstanceState)
        PaymentReturnManager.handleIntent(intent)
        if (BuildConfig.DEBUG) logWindowAndResources("onCreate")

        val initialPrefs = getSharedPreferences("session_prefs", android.content.Context.MODE_PRIVATE)
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        globalIsDark = initialPrefs.getBoolean("is_dark_theme", isSystemDark)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (globalIsDark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val prefs = remember { applicationContext.getSharedPreferences("session_prefs", Context.MODE_PRIVATE) }
            var targetScale by remember { mutableStateOf(prefs.getFloat("display_scale", 1.0f)) }
            // "compact" was renamed to "horizontal"; migrate any legacy stored value.
            fun readDensity(): String {
                val stored = prefs.getString("layout_density", "default") ?: "default"
                return if (stored == "compact") "horizontal" else stored
            }
            var layoutDensity by remember { mutableStateOf(readDensity()) }
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "display_scale" -> targetScale = prefs.getFloat("display_scale", 1.0f)
                        "layout_density" -> layoutDensity = readDensity()
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val displayScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
            KhanaBookLiteTheme(displayScale = displayScale, layoutDensity = layoutDensity) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val menuViewModel: MenuViewModel = hiltViewModel()
                val currentUser by authViewModel.currentUser.collectAsState()
                val context = this
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(currentUser?.id) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        currentUser != null &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                val toastScope = rememberCoroutineScope()

                // Background lock observer. Returns from trusted external apps
                // (WhatsApp, dialer, payment apps) get one bypass; normal reopen
                // still asks for PIN after the grace period.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    sessionManager.onAppBackgrounded()
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    val currentDest = navController.currentDestination?.route
                    val isInPrivateArea = currentDest != null &&
                        currentDest != "splash" &&
                        currentDest != "login" &&
                        currentDest != "signup" &&
                        currentDest != "app_lock"

                    if (!isInPrivateArea) return@LifecycleEventEffect

                    if (TrustedExternalAppReturn.consume(context)) {
                        sessionManager.clearBackgroundTime()
                    } else if (sessionManager.shouldShowAppLock()) {
                        sessionManager.clearBackgroundTime()
                        navController.navigate("app_lock")
                    } else {
                        sessionManager.clearBackgroundTime()
                    }
                }


                // Root back handling (Double Back to Exit from Home)
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                // Central status bar controller based on navigation route
                val windowInsetsController = remember {
                    WindowCompat.getInsetsController(window, window.decorView)
                }
                LaunchedEffect(currentRoute) {
                    // Always show status bar to prevent black overlay/strip above content
                    windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    
                    val isMainRoute = currentRoute?.startsWith("main/") == true || currentRoute == "main/{tab}"
                    val isSettingsTab = isMainRoute && (currentBackStackEntry?.arguments?.getString("tab") == "3")
                    
                    if (isSettingsTab) {
                        // Standard theme-dependent behavior for Settings tab
                        windowInsetsController.isAppearanceLightStatusBars = !globalIsDark
                    } else {
                        // All other screens have dark headers or backgrounds, ensure light (white) icons
                        windowInsetsController.isAppearanceLightStatusBars = false
                    }
                }

                LaunchedEffect(navController) {
                    PaymentReturnManager.latestEvent.collect { event ->
                        val currentRoute = navController.currentDestination?.route
                        if (event != null && 
                            currentRoute?.startsWith("new_bill") != true && 
                            currentRoute?.startsWith("easebuzz_payment") != true) {
                            navController.navigate("new_bill?resumePayment=true")
                        }
                    }
                }

                // Deep link: handle notification tap that launched/resumed the app
                LaunchedEffect(navController) {
                    val route = extractNotificationRoute(intent)
                    if (route != null && sessionManager.getAuthToken() != null) {
                        // Wait until we're past splash/login before navigating
                        navController.addOnDestinationChangedListener { _, dest, _ ->
                            val d = dest.route
                            if (d != null && d != "splash" && d != "login" && d != "signup" && d != "initial_sync" && d != "app_lock") {
                                navController.removeOnDestinationChangedListener { _, _, _ -> }
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }

                // Handle onNewIntent deep links (app already open when notification tapped)
                val pendingRoute by MainActivity.pendingNavRoute.collectAsState()
                LaunchedEffect(pendingRoute) {
                    val route = pendingRoute ?: return@LaunchedEffect
                    if (sessionManager.getAuthToken() != null) {
                        navController.navigate(route) { launchSingleTop = true }
                        _pendingNavRoute.value = null  // consume
                    }
                }


                // Only intercept back if we are on the MainScreen (any tab)
                // Sub-screens handle their own back navigation through NavHost
                val isAtRoot = currentRoute?.startsWith("main/") == true
                
                androidx.activity.compose.BackHandler(enabled = isAtRoot) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime < 2000) {
                        finish()
                    } else {
                        toastScope.launch {
                            KhanaToast.show(
                                context.getString(R.string.press_back_again_to_exit),
                                ToastKind.Info,
                            )
                        }
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

                Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    enterTransition = {
                        fadeIn(animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)) +
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth / 5 },
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        ) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(KbMotion.MotionDuration.Fast, easing = KbMotion.EasingEmphasized)) +
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 5 },
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        ) +
                        scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        )
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)) +
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth / 5 },
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        ) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        )
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(KbMotion.MotionDuration.Fast, easing = KbMotion.EasingEmphasized)) +
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth / 5 },
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
                        ) +
                        scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(KbMotion.MotionDuration.Medium, easing = KbMotion.EasingEmphasized)
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
                                    navController.navigate("main/0") { popUpTo("app_lock") { inclusive = true } }
                                }
                            }
                        )
                    }
                    composable("initial_sync") {
                        InitialSyncScreen(
                            onSyncCompleteNavigateToMain = {
                                navController.navigate("main/0") { popUpTo("initial_sync") { inclusive = true } }
                            },
                            onNavigateToLogin = {
                                navController.navigate("login") { popUpTo(0) { inclusive = true } }
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
                            onReprintKds = { navController.navigate("reprint_kds") },
                            onOrderStatus = { navController.navigate("order_status") },
                            onCallCustomer = { navController.navigate("call_customer") },
                            onMarketplaceOrders = { navController.navigate("marketplace_orders") },
                            menuViewModel = menuViewModel,
                            onScanClick = { categoryName ->
                                navController.currentBackStackEntry?.savedStateHandle?.set("ocr_category_name", categoryName)
                                navController.navigate("ocr_scanner/menu_config")
                            }
                        )
                    }
                    composable("notifications") {
                        NotificationsScreen(
                            onBack = { navController.popBackStack() },
                            onNewBill = { navController.navigate("new_bill") },
                            onOrderStatus = { navController.navigate("order_status") },
                            onMarketplaceOrders = { navController.navigate("marketplace_orders") },
                            onReprintKds = { navController.navigate("reprint_kds") }
                        )
                    }
                    composable(
                        route = "new_bill?resumePayment={resumePayment}",
                        arguments = listOf(
                            navArgument("resumePayment") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val resumePayment = backStackEntry.arguments?.getBoolean("resumePayment") == true
                        NewBillScreen(
                            onBack = { navController.popBackStack() },
                            modifier = Modifier.fillMaxSize(),
                            navController = navController,
                            resumePendingPayment = resumePayment
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
                    composable("reprint_kds") {
                        ReprintKdsScreen(
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
                        val restaurantId = backStackEntry.arguments?.getLong("restaurantId") ?: 0L
                        val billId = backStackEntry.arguments?.getLong("billId") ?: 0L
                        val amountStr = backStackEntry.arguments?.getString("amount") ?: "0.00"
                    EasebuzzPaymentScreen(
                        restaurantId = restaurantId,
                        billId = billId,
                        amount = java.math.BigDecimal(amountStr),
                        paymentRepository = easebuzzSdkPaymentRepository,
                        sessionManager = sessionManager,
                        onBack = { navController.popBackStack() },
                        onPaymentComplete = { gatewayTxnId ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("gatewayTxnId", gatewayTxnId)
                            
                            // Bring MainActivity to the front and close PWECheckoutActivity or any Custom Tab
                            val clearTopIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(clearTopIntent)
                            
                            navController.popBackStack()
                        }
                    )
                    }
                    composable("marketplace_orders") {
                        MarketplaceOrdersScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("easebuzz_kyc") {
                        EasebuzzKycScreen(
                            paymentRepository = easebuzzPaymentRepository,
                            onBack = { navController.popBackStack() },
                            onStartOnboarding = { navController.navigate("easebuzz_onboarding?resubmit=false") },
                            onResubmit = { navController.navigate("easebuzz_onboarding?resubmit=true") }
                        )
                    }
                    composable(
                        route = "easebuzz_onboarding?resubmit={resubmit}",
                        arguments = listOf(
                            navArgument("resubmit") {
                                type = NavType.BoolType
                                defaultValue = false
                            }
                        )
                    ) { backStackEntry ->
                        val isResubmit = backStackEntry.arguments?.getBoolean("resubmit") ?: false
                        EasebuzzOnboardingScreen(
                            isResubmit = isResubmit,
                            onSubmitted = {
                                navController.navigate("easebuzz_kyc") {
                                    popUpTo("easebuzz_onboarding?resubmit={resubmit}") { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                KhanaBookSnackbarHost(
                    hostState = KhanaToast.host,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                } // end Box
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PaymentReturnManager.handleIntent(intent)
        // App already open: push route into StateFlow so Compose navigates immediately
        val route = extractNotificationRoute(intent)
        if (route != null) {
            _pendingNavRoute.value = route
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) logWindowAndResources("onResume")
    }

    private fun logWindowAndResources(stage: String) {
        val configuration = resources.configuration
        val metrics = resources.displayMetrics
        val windowBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.toShortString()
        } else {
            "unavailable"
        }
        Log.d(
            UI_SCALE_TAG,
            "$stage activity resources: " +
                "sdk=${Build.VERSION.SDK_INT}, " +
                "fontScale=${configuration.fontScale}, " +
                "density=${metrics.density}, scaledDensity=${metrics.scaledDensity}, " +
                "densityDpi=${metrics.densityDpi}, " +
                "screenWidthDp=${configuration.screenWidthDp}, " +
                "screenHeightDp=${configuration.screenHeightDp}, " +
                "smallestWidthDp=${configuration.smallestScreenWidthDp}, " +
                "orientation=${configuration.orientation}, " +
                "windowBounds=$windowBounds, " +
                "isInMultiWindow=$isInMultiWindowMode"
        )
    }
}
