@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens
import com.khanabook.lite.pos.feature.reports.ui.OrdersScreen
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.feature.auth.viewmodel.AuthViewModel
import com.khanabook.lite.pos.feature.menu.viewmodel.MenuViewModel
import com.khanabook.lite.pos.feature.reports.ui.HomeScreen
import com.khanabook.lite.pos.feature.settings.ui.SettingsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.core.navigation.TabItem
import com.khanabook.lite.pos.core.navigation.NavigationUtils
import com.khanabook.lite.pos.core.navigation.NavigationTabs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.navigation.NavController
import com.khanabook.lite.pos.core.navigation.horizontalNavigationSwipe
import com.khanabook.lite.pos.core.designsystem.rememberReducedMotion

/**
 * The tab shell is one nav destination. Screens pushed above it (Notification
 * Preferences) dispose and recreate this composable when popped, and the
 * remembered tab was resetting to the launch argument (tab=0). This holder keeps
 * the user's last tab/section across that pop; process death falls back to the
 * route argument because the holder is in-memory only.
 */
object MainTabState {
    var selectedTab: Int? = null
    var settingsSection: String? = null
}

@Composable
fun MainScreen(
    initialTab: Int = 0,
    initialSource: String? = null,
    initialHighlightBillId: Long? = null,
    initialSettingsSection: String? = null,
    navController: NavController,
    onNewBill: (Boolean?) -> Unit,
    onActiveOrder: () -> Unit,
    onOpenActiveOrder: (Long) -> Unit = {},
    onResumePendingPayment: () -> Unit,
    onSearchBill: () -> Unit,
    onReprintKot: () -> Unit,
    onCallCustomer: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    menuViewModel: MenuViewModel,
    onScanClick: (String?) -> Unit = {},
    onTabSelected: (Int) -> Unit = {},
    onSettingsSectionChanged: (String) -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val visibleTabs = remember { NavigationUtils.getVisibleTabs() }
    val haptic = LocalHapticFeedback.current
    val layout = KhanaBookTheme.layout
    val typeScale = KhanaBookTheme.typeScale
    val navigationSuiteType = NavigationSuiteType.NavigationBar

    val homeTabIndex = remember(visibleTabs) {
        visibleTabs.indexOfFirst { it.originalIndex == NavigationTabs.TAB_HOME }.coerceAtLeast(0)
    }

    var selectedTabIndex by rememberSaveable(initialTab, visibleTabs) { 
        val initialVisibleIndex = visibleTabs.indexOfFirst { it.originalIndex == initialTab }
            .let { idx ->
                if (idx != -1) idx
                else if (initialTab == 4) visibleTabs.indexOfFirst { it.originalIndex == NavigationTabs.TAB_SETTINGS }.takeIf { it != -1 } ?: homeTabIndex
                else homeTabIndex
            }
        mutableIntStateOf(initialVisibleIndex) 
    }
    LaunchedEffect(initialTab) {
        val targetIdx = visibleTabs.indexOfFirst { it.originalIndex == initialTab }
            .let { idx ->
                if (idx != -1) idx
                else if (initialTab == 4) visibleTabs.indexOfFirst { it.originalIndex == NavigationTabs.TAB_SETTINGS }.takeIf { it != -1 } ?: homeTabIndex
                else homeTabIndex
            }
        if (selectedTabIndex != targetIdx) {
            selectedTabIndex = targetIdx
            val original = visibleTabs.getOrNull(targetIdx)?.originalIndex ?: targetIdx
            MainTabState.selectedTab = original
            onTabSelected(original)
        }
    }
    var showBottomBar by rememberSaveable { mutableStateOf(true) }
    var settingsSection by rememberSaveable(initialSettingsSection) {
        mutableStateOf(MainTabState.settingsSection ?: initialSettingsSection ?: "menu")
    }
    LaunchedEffect(Unit) {
        MainTabState.selectedTab?.let { savedTab ->
            val idx = visibleTabs.indexOfFirst { it.originalIndex == savedTab }
            if (idx != -1 && selectedTabIndex != idx) selectedTabIndex = idx
        }
    }
    // True while Settings was opened straight at a section from another tab
    // (Home's sync/printer cards), so Back leaves Settings instead of
    // detouring through the App Settings list (BUG-020).
    var settingsDeepEntry by rememberSaveable { mutableStateOf(false) }
    val settingsTabIndex = remember(visibleTabs) {
        visibleTabs.indexOfFirst { it.originalIndex == NavigationTabs.TAB_SETTINGS }.coerceAtLeast(0)
    }
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(0, (visibleTabs.lastIndex).coerceAtLeast(0))
    val moveToTab: (Int) -> Unit = { index ->
        val target = index.coerceIn(0, (visibleTabs.lastIndex).coerceAtLeast(0))
        if (target != safeSelectedTabIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            selectedTabIndex = target
            // Store the stable tab identity: the restore below looks it up as an
            // originalIndex, while `target` is a position in visibleTabs.
            val original = visibleTabs.getOrNull(target)?.originalIndex ?: target
            MainTabState.selectedTab = original
            onTabSelected(original)
        }
    }

    val openSettingsSection: (String) -> Unit = { name ->
        settingsSection = name
        onSettingsSectionChanged(name)
        settingsDeepEntry = true
        moveToTab(settingsTabIndex)
    }

    // Intercept back gesture to return to Home tab if not already there
    BackHandler(enabled = safeSelectedTabIndex != homeTabIndex) {
        selectedTabIndex = homeTabIndex
        val original = visibleTabs.getOrNull(homeTabIndex)?.originalIndex ?: homeTabIndex
        MainTabState.selectedTab = original
        onTabSelected(original)
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        val backToHome = { selectedTabIndex = homeTabIndex }
        AnimatedContent(
            targetState = safeSelectedTabIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> direction * fullWidth / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing))) togetherWith
                (slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -direction * fullWidth / 5 },
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)))
            },
            label = "tab_content",
            modifier = modifier
        ) { tabIndex ->
            val currentTab = visibleTabs.getOrNull(tabIndex)
            if (currentTab == null) {
                Box(modifier = Modifier.fillMaxSize())
                return@AnimatedContent
            }
            when (currentTab.originalIndex) {
                NavigationTabs.TAB_HOME -> HomeScreen(
                    onNewBill = onNewBill,
                    onActiveOrder = onActiveOrder,
                    onOpenActiveOrder = onOpenActiveOrder,
                    onResumePendingPayment = onResumePendingPayment,
                    onOpenSyncCenter = { openSettingsSection("sync_center") },
                    onOpenPrinterSettings = { openSettingsSection("printer") },
                    onSearchBill = onSearchBill,
                    onReprintKot = onReprintKot,
                    onCallCustomer = onCallCustomer,
                    onOpenNotifications = onOpenNotifications
                )
                // Dispatch on the tab's stable identity, never its display label, so
                // renaming a tab can never silently reroute navigation.
                NavigationTabs.TAB_ORDERS -> OrdersScreen(onBack = backToHome)
                NavigationTabs.TAB_SETTINGS -> SettingsScreen(
                    onBack = backToHome,
                    navController = navController,
                    initialSection = settingsSection,
                    deepEntry = settingsDeepEntry,
                    onSectionChanged = {
                        settingsSection = it
                        MainTabState.settingsSection = it
                        settingsDeepEntry = false
                        onSettingsSectionChanged(it)
                    },
                    onScanClick = onScanClick,
                    menuViewModel = menuViewModel,
                    onBottomBarVisibilityChange = { visible -> showBottomBar = visible }
                )
            }
        }
    }

    val unselectedNavColor = TextLight.copy(alpha = 0.6f)
    val suiteItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = PrimaryGold,
            unselectedIconColor = unselectedNavColor,
            selectedTextColor = PrimaryGold,
            unselectedTextColor = unselectedNavColor,
            indicatorColor = PrimaryGold.copy(alpha = 0.1f)
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = PrimaryGold,
            unselectedIconColor = unselectedNavColor,
            selectedTextColor = PrimaryGold,
            unselectedTextColor = unselectedNavColor,
            indicatorColor = PrimaryGold.copy(alpha = 0.1f)
        )
    )
    val navIconSize = layout.navIconSize
    val navLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = layout.navLabelFontSize
    )

    val mainContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (!showBottomBar) Modifier.navigationBarsPadding() else Modifier)
                .horizontalNavigationSwipe(
                    enabled = showBottomBar && visibleTabs.size > 1,
                    onSwipeLeft = { moveToTab(safeSelectedTabIndex + 1) },
                    onSwipeRight = { moveToTab(safeSelectedTabIndex - 1) }
                )
        ) {
            content(Modifier.fillMaxSize())
        }
    }

    if (showBottomBar) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                visibleTabs.forEachIndexed { index, item ->
                    this.item(
                        selected = safeSelectedTabIndex == index,
                        onClick = { moveToTab(index) },
                        icon = {
                            AnimatedTabIcon(
                                item = item,
                                selected = safeSelectedTabIndex == index,
                                size = navIconSize
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = navLabelStyle.copy(
                                    fontWeight = if (safeSelectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        },
                        alwaysShowLabel = true,
                        colors = suiteItemColors
                    )
                }
            },
            layoutType = navigationSuiteType,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = DarkBrown1,
                navigationRailContainerColor = DarkBrown1
            ),
            containerColor = DarkBrown1,
            content = mainContent
        )
    } else {
        mainContent()
    }
}

@Composable
fun AnimatedTabIcon(item: TabItem, selected: Boolean, size: Dp) {
    // Lottie tab (Chennai One style): plays once on selection when a lottieRes is set.
    if (item.lottieRes != null) {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(item.lottieRes)
        )
        LottieAnimation(
            composition = composition,
            iterations = 1,
            isPlaying = selected,
            restartOnPlay = true,
            modifier = Modifier.size(size)
        )
        return
    }

    // Vector fallback: a short area-preserving press. X and Y move in opposite
    // directions so the icon's visual area stays roughly constant, and every leg
    // starts from the current value (no fixed-value snap that jumps mid-tap).
    val pressScaleX = remember { Animatable(1f) }
    val pressScaleY = remember { Animatable(1f) }
    val reducedMotion = rememberReducedMotion()
    LaunchedEffect(selected) {
        // Reduced motion relies on the selection colour/ripple only — no scale transform.
        if (selected && !reducedMotion) {
            pressScaleX.animateTo(1.08f, tween(120, easing = FastOutSlowInEasing))
            pressScaleY.animateTo(0.92f, tween(120, easing = FastOutSlowInEasing))
            pressScaleX.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
            pressScaleY.animateTo(1f, tween(120, easing = FastOutSlowInEasing))
        }
    }
    Icon(
        item.icon,
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = pressScaleX.value
                scaleY = pressScaleY.value
            }
    )
}

