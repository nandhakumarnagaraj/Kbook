@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.navigation.TabItem
import com.khanabook.lite.pos.ui.navigation.NavigationUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController

@Composable
fun MainScreen(
    initialTab: Int = 0,
    navController: NavController,
    onNewBill: () -> Unit,
    onSearchBill: () -> Unit,
    onReprintKds: () -> Unit,
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit,
    onMarketplaceOrders: () -> Unit = {},
    menuViewModel: MenuViewModel,
    onScanClick: (String?) -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val visibleTabs = remember { NavigationUtils.getVisibleTabs() }
    val haptic = LocalHapticFeedback.current

    var selectedTabIndex by rememberSaveable(initialTab, visibleTabs) { 
        val initialVisibleIndex = visibleTabs.indexOfFirst { it.originalIndex == initialTab }
        mutableIntStateOf(if (initialVisibleIndex != -1) initialVisibleIndex else 0) 
    }
    var showBottomBar by rememberSaveable { mutableStateOf(true) }
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(0, (visibleTabs.lastIndex).coerceAtLeast(0))

    // Intercept back gesture to return to Home tab if not already there
    BackHandler(enabled = safeSelectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        val backToHome = { selectedTabIndex = 0 }
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
            when (currentTab.label) {
                "Home" -> HomeScreen(onNewBill, onSearchBill, onReprintKds, onOrderStatus, onCallCustomer, onMarketplaceOrders)
                "Reports" -> ReportsScreen(onBack = backToHome)
                "Orders" -> OrdersScreen(onBack = backToHome)
                "Profile" -> SettingsScreen(
                    onBack = backToHome,
                    navController = navController,
                    onScanClick = onScanClick,
                    menuViewModel = menuViewModel,
                    onBottomBarVisibilityChange = { visible -> showBottomBar = visible }
                )
            }
        }
    }

    Scaffold(
        // Opt out of automatic inset injection — safeDrawing can include gesture zones and
        // display cutouts that exceed the bottomBar height on Android 16, causing double-counting.
        // statusBarsPadding() on the content box and navigationBarsPadding() on the NavigationBar
        // handle all insets explicitly instead.
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    visibleTabs = visibleTabs,
                    currentSelectedIndex = safeSelectedTabIndex,
                    onTabSelected = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabIndex = it
                    }
                )
            }
        }
    ) { padding ->
        val density = LocalDensity.current
        val isKeyboardOpen = WindowInsets.ime.getBottom(density) > 0
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = if (showBottomBar && !isKeyboardOpen) padding.calculateBottomPadding() else 0.dp)
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun AppBottomBar(
    visibleTabs: List<TabItem>,
    currentSelectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    // reason: Styled to match the designer specification (#FFFFFF background, #E0D8D0 top border 0.5dp, active #8B3A0F, inactive #756E66 for contrast)
    // ⚠ override: Hardcoded color overrides applied to the NavigationBar for brand compliance.
    val activeColor = Color(0xFF8B3A0F)
    val inactiveColor = Color(0xFF756E66) // ⚠ override: Changed from #9E9890 to #756E66 to pass WCAG AA 4.5:1 contrast on #FAF7F4 / #FFFFFF
    val borderColor = Color(0xFFE0D8D0)
    
    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
    NavigationBar(
        containerColor = Color.White, // ⚠ override: pure white bottom nav
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 0.dp
    ) {
        visibleTabs.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = currentSelectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = activeColor,
                    unselectedIconColor = inactiveColor,
                    selectedTextColor = activeColor,
                    unselectedTextColor = inactiveColor,
                    indicatorColor = activeColor.copy(alpha = 0.1f)
                )
            )
        }
    }
}
