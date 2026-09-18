@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.khanabook.lite.pos.core.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import com.khanabook.lite.pos.core.designsystem.KhanaBookSnackbarHost
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.feature.menu.data.MenuRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.feature.payments.domain.PaymentReturnManager
import com.khanabook.lite.pos.feature.notifications.domain.NotificationRouteManager
import com.khanabook.lite.pos.feature.notifications.ui.InAppNotificationBanner
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.auth.domain.TrustedExternalAppReturn
import com.khanabook.lite.pos.feature.sync.domain.enqueueMasterSyncOnce
import com.khanabook.lite.pos.core.navigation.AppNavGraph
import com.khanabook.lite.pos.core.navigation.Routes
import com.khanabook.lite.pos.feature.auth.ui.BrandedStartFrame
import com.khanabook.lite.pos.core.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import com.khanabook.lite.pos.feature.menu.viewmodel.MenuViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var syncManager: com.khanabook.lite.pos.feature.sync.domain.SyncManager
    @Inject lateinit var networkMonitor: com.khanabook.lite.pos.feature.sync.domain.NetworkMonitor
    @Inject lateinit var databaseProvider: com.khanabook.lite.pos.core.database.DatabaseProvider
    @Inject lateinit var menuRepository: com.khanabook.lite.pos.feature.menu.data.MenuRepository
    @Inject lateinit var notificationRepository: com.khanabook.lite.pos.feature.notifications.data.NotificationRepository
    private var lastBackPressTime: Long = 0

    // Native splash is released at the first frame; the branded start frame
    // (BrandedStartFrame) is shown while the startup routing decision completes,
    // then we navigate to the decided destination. Only touched on main thread.
    private val startupDestination = MutableStateFlow<String?>(null)

    companion object {
        private const val UI_SCALE_TAG = "UI_SCALE_DEBUG"
    }

    private fun authenticatedStartDestination(): String {
        if (!sessionManager.canUsePos()) return Routes.ROLE_ACCESS
        if (!sessionManager.isInitialSyncCompleted()) return Routes.INITIAL_SYNC
        if (!sessionManager.isQuickStartCompleted()) return Routes.QUICK_START
        return Routes.main(0)
    }

    /**
     * Full startup routing decision (formerly SplashViewModel.checkSession()).
     * Runs before the first Compose frame so the native splash can be released
     * straight onto the correct destination.
     */
    private suspend fun computeStartupDestination(): String {
        val token = sessionManager.getAuthToken()
        val isSyncCompleted = sessionManager.isInitialSyncCompleted()
        val isTrustedExternalReturn = TrustedExternalAppReturn.consume(this)

        // Auto-complete quick start for existing users upgrading to this version
        if (isSyncCompleted && !sessionManager.isQuickStartCompleted()) {
            val existingItems = try {
                menuRepository.getAllMenuItemsOnce()
            } catch (_: Exception) {
                emptyList()
            }
            if (existingItems.isNotEmpty()) {
                sessionManager.setQuickStartCompleted(true)
            }
        }

        val chosen = when {
            token == null -> Routes.LOGIN
            !isSyncCompleted -> authenticatedStartDestination()
            sessionManager.isPinLockEnabled() && !isTrustedExternalReturn -> {
                sessionManager.clearBackgroundTime()
                Routes.APP_LOCK
            }
            else -> {
                sessionManager.clearBackgroundTime()
                authenticatedStartDestination()
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("KhanaBookDebugAuth", "Startup → $chosen tokenPresent=${token != null} syncDone=$isSyncCompleted")
        }
        return chosen
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

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            Log.i("MainActivity", "POST_NOTIFICATIONS granted=$granted")
        }

    /**
     * Android 13+ (API 33) requires runtime permission for notifications.
     * Without this, push notifications are silently blocked on fresh installs.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Release system splash as soon as first frame renders — the Compose
        // BrandedStartFrame takes over as the branded splash (Paytm-style).
        installSplashScreen()
        setTheme(R.style.Theme_KhanaBookLite)
        super.onCreate(savedInstanceState)
        PaymentReturnManager.handleIntent(intent)
        NotificationRouteManager.handleIntent(intent)
        if (BuildConfig.DEBUG) logWindowAndResources("onCreate")

        requestNotificationPermissionIfNeeded()

        val token = sessionManager.getAuthToken()
        if (!token.isNullOrBlank()) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                databaseProvider.warmUpDatabase()
            }
        }

        enableEdgeToEdge()

        // Startup routing decision — immediate dispatch for instant POS launch
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            startupDestination.value = computeStartupDestination()
        }

        lifecycleScope.launch {
            networkMonitor.status.collectLatest { status ->
                if (status == com.khanabook.lite.pos.feature.sync.domain.ConnectionStatus.Available) {
                    val tokenVal = sessionManager.getAuthToken()
                    if (!tokenVal.isNullOrBlank() && sessionManager.canUsePos()) {
                        Log.i("MainActivity", "Network reconnected. Enqueuing background sync.")
                        androidx.work.WorkManager.getInstance(this@MainActivity).enqueueMasterSyncOnce()
                        syncManager.triggerImmediateSync()
                        // Reconcile notifications pushed while offline and flush any
                        // read state that could not be synced.
                        notificationRepository.refreshFromServer()
                    }
                }
            }
        }

        // Promote newly arrived notifications to the in-app (LinkedIn-style)
        // banner while the app is running.
        lifecycleScope.launch {
            notificationRepository.watchForNoticeBanners()
        }

        setContent {
            val prefs = remember { applicationContext.getSharedPreferences("session_prefs", Context.MODE_PRIVATE) }
            var targetScale by remember { mutableStateOf(prefs.getFloat("display_scale", 1.0f)) }
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "display_scale") {
                        targetScale = prefs.getFloat("display_scale", 1.0f)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val displayScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
            KhanaBookLiteTheme(displayScale = displayScale) {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val menuViewModel: MenuViewModel = hiltViewModel()
                val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
                val context = this
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
                        currentDest != Routes.LOGIN &&
                        currentDest != Routes.SIGNUP &&
                        currentDest != Routes.APP_LOCK

                    if (!isInPrivateArea) return@LifecycleEventEffect

                    if (TrustedExternalAppReturn.consume(context)) {
                        sessionManager.clearBackgroundTime()
                    } else if (sessionManager.shouldShowAppLock()) {
                        sessionManager.clearBackgroundTime()
                        navController.navigate(Routes.APP_LOCK)
                    } else {
                        sessionManager.clearBackgroundTime()
                    }
                }


                // Root back handling (Double Back to Exit from Home)
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                LaunchedEffect(navController) {
                    PaymentReturnManager.latestEvent.collect { event ->
                        val currentRoute = navController.currentDestination?.route
                        if (sessionManager.canUsePos()
                            && event != null
                            && currentRoute?.startsWith("new_bill") != true
                        ) {
                            navController.navigate(Routes.newBill(resumePayment = true))
                        }
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

                val isSessionExpired by sessionManager.isSessionExpired.collectAsStateWithLifecycle()

                LaunchedEffect(isSessionExpired) {
                    if (isSessionExpired) {
                        val dest = navController.currentDestination?.route
                        if (dest != null && dest != Routes.LOGIN && dest != Routes.APP_LOCK && dest != Routes.SIGNUP) {
                            authViewModel.handleSessionExpiry()
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }

                // Authentication Observer — only acts after session has been fully
                // initialized (READY then back to null = genuine logout). Without this
                // gate, the initial null from MutableStateFlow races against
                // loadPersistedUser() and pushes authenticated users back to login.
                val sessionState by sessionManager.sessionState.collectAsStateWithLifecycle()
                val activeBanner by notificationRepository.activeBanner.collectAsStateWithLifecycle()
                val bannerScope = rememberCoroutineScope()

                LaunchedEffect(currentUser, sessionState) {
                    if (sessionState == SessionManager.SessionState.INACTIVE) return@LaunchedEffect
                    val dest = navController.currentDestination?.route
                    if (currentUser == null && dest != null && dest != Routes.LOGIN && dest != Routes.APP_LOCK && dest != Routes.SIGNUP) {
                        navController.navigate(Routes.LOGIN) { 
                            popUpTo(0) { inclusive = true } 
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val startDestination by startupDestination.collectAsStateWithLifecycle()

                    // App navigation mounts beneath splash when ready
                    if (startDestination != null) {
                        AppNavGraph(
                            navController = navController,
                            authViewModel = authViewModel,
                            menuViewModel = menuViewModel,
                            sessionManager = sessionManager,
                            context = context,
                            authenticatedStartDestination = ::authenticatedStartDestination,
                            startDestination = startDestination!!
                        )

                        // Deep-link a tapped notification once the NavHost exists.
                        // Consumed on apply so rotation cannot repeat it.
                        LaunchedEffect(Unit) {
                            NotificationRouteManager.pendingRoute.collect { route ->
                                if (route != null) {
                                    NotificationRouteManager.consume()
                                    if (sessionManager.canUsePos()) {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                }
                            }
                        }
                    }

                    // Seamless branded splash overlay: stays rock-solid while routing,
                    // then fades out smoothly over 200ms into the main app (no hard cuts, no flicker)
                    AnimatedVisibility(
                        visible = startDestination == null,
                        enter = EnterTransition.None,
                        exit = fadeOut(
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    ) {
                        BrandedStartFrame()
                    }

                    KhanaBookSnackbarHost(
                        hostState = KhanaToast.host,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    // LinkedIn-style in-app banner that slides in under the status
                    // bar when a notification arrives while the user is inside the
                    // app (not on login/setup/lock screens).
                    if (activeBanner != null && currentRoute != Routes.LOGIN && currentRoute != Routes.SIGNUP && currentRoute != Routes.APP_LOCK) {
                        val banner = activeBanner!!
                        InAppNotificationBanner(
                            notification = banner,
                            onDismiss = { notificationRepository.consumeActiveBanner() },
                            onOpen = {
                                notificationRepository.consumeActiveBanner()
                                bannerScope.launch { notificationRepository.markAsRead(banner.id) }
                                if (sessionManager.canUsePos()) {
                                    navController.navigate(
                                        NotificationRouteManager.routeForType(banner.notificationType)
                                    ) { launchSingleTop = true }
                                }
                            },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                } // end Box
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PaymentReturnManager.handleIntent(intent)
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
