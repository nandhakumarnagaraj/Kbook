@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.screens.HomeScreen
import com.khanabook.lite.pos.ui.screens.ReportsScreen
import com.khanabook.lite.pos.ui.screens.OrdersScreen
import com.khanabook.lite.pos.ui.screens.SettingsScreen
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
    onOrderStatus: () -> Unit,
    onCallCustomer: () -> Unit,
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

    // Intercept back gesture to return to Home tab if not already there
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        val backToHome = { selectedTabIndex = 0 }
        AnimatedContent(
            targetState = selectedTabIndex,
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
            val currentTab = visibleTabs[tabIndex]
            when (currentTab.label) {
                "Home" -> HomeScreen(onNewBill, onSearchBill, onOrderStatus, onCallCustomer)
                "Reports" -> ReportsScreen(onBack = backToHome)
                "Orders" -> OrdersScreen(onBack = backToHome)
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

    if (showBottomBar) {
        Scaffold(
            containerColor = DarkBackground,
            bottomBar = {
                AppBottomBar(
                    visibleTabs = visibleTabs,
                    currentSelectedIndex = selectedTabIndex,
                    onTabSelected = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTabIndex = it
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                content(Modifier.fillMaxSize())
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
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
    HorizontalDivider(color = BorderGold.copy(alpha = 0.3f), thickness = 0.5.dp)
    NavigationBar(
        containerColor = DarkBrown1,
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
                    selectedIconColor = PrimaryGold,
                    unselectedIconColor = TextLight.copy(alpha = 0.6f),
                    selectedTextColor = PrimaryGold,
                    unselectedTextColor = TextLight.copy(alpha = 0.6f),
                    indicatorColor = PrimaryGold.copy(alpha = 0.1f)
                )
            )
        }
    }
}
