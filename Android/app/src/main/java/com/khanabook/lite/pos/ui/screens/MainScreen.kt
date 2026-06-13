@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.background
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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.khanabook.lite.pos.R

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
        val normalizedInitialTab = when (initialTab) {
            1 -> 0 // Billing is launched from actions now, so old tab links fall back to Home.
            2 -> 1 // Old Menu tab deep links now land on Orders, keeping route indices stable enough.
            3 -> 4 // Old Settings tab deep links still open Settings.
            else -> initialTab
        }
        val initialVisibleIndex = visibleTabs.indexOfFirst { it.originalIndex == normalizedInitialTab }
        mutableIntStateOf(if (initialVisibleIndex != -1) initialVisibleIndex else 0) 
    }
    var showBottomBar by rememberSaveable { mutableStateOf(true) }
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(0, (visibleTabs.lastIndex).coerceAtLeast(0))

    val layout = KhanaBookTheme.layout
    val isWideScreen = !layout.isCompact

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
                "Home" -> HomeScreen(
                    onNewBill = onNewBill,
                    onSearchBill = onSearchBill,
                    onReprintKds = onReprintKds,
                    onOrderStatus = onOrderStatus,
                    onCallCustomer = onCallCustomer,
                    onMarketplaceOrders = onMarketplaceOrders,
                    onMenuClick = {
                        val settingsIndex = visibleTabs.indexOfFirst { it.label == "Settings" }
                        if (settingsIndex != -1) {
                            selectedTabIndex = settingsIndex
                        }
                    }
                )
                "Orders" -> OrdersScreen(onBack = backToHome)
                "Reports" -> ReportsScreen(onBack = backToHome)
                "Settings" -> SettingsScreen(
                    onBack = backToHome,
                    navController = navController,
                    onScanClick = onScanClick,
                    menuViewModel = menuViewModel,
                    onBottomBarVisibilityChange = { visible -> showBottomBar = visible }
                )
            }
        }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showBottomBar) {
                AppNavigationRail(
                    visibleTabs = visibleTabs,
                    currentSelectedIndex = safeSelectedTabIndex,
                    onTabSelected = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabIndex = it
                    }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content(Modifier.fillMaxSize())
            }
        }
    } else {
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
                    .padding(bottom = if (showBottomBar && !isKeyboardOpen) padding.calculateBottomPadding() else 0.dp)
            ) {
                content(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun AppNavigationRail(
    visibleTabs: List<TabItem>,
    currentSelectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val activeColor = KbBrandSaffron
    val inactiveColor = MaterialTheme.kbTextDisabled

    NavigationRail(
        containerColor = MaterialTheme.kbBgCard.copy(alpha = 0.98f),
        modifier = Modifier
            .width(104.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.khanabook_logo),
                        contentDescription = "KhanaBook",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text(
                    text = "POS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = activeColor
                )
            }
        }
    ) {
        visibleTabs.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = currentSelectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = activeColor,
                    unselectedIconColor = inactiveColor,
                    selectedTextColor = activeColor,
                    unselectedTextColor = inactiveColor,
                    indicatorColor = activeColor.copy(alpha = KbOpacity.StatusBg)
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun AppBottomBar(
    visibleTabs: List<TabItem>,
    currentSelectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val activeColor = KbBrandSaffron
    val inactiveColor = MaterialTheme.kbTextDisabled

    NavigationBar(
        containerColor = MaterialTheme.kbBgCard.copy(alpha = 0.98f),
        modifier = Modifier.navigationBarsPadding(),
        tonalElevation = 3.dp
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
                    indicatorColor = activeColor.copy(alpha = KbOpacity.StatusBg)
                )
            )
        }
    }
}
