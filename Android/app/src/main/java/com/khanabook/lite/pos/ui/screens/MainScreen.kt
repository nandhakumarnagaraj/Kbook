package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.khanabook.lite.pos.ui.screens.HomeScreen
import com.khanabook.lite.pos.ui.screens.ReportsScreen
import com.khanabook.lite.pos.ui.screens.OrdersScreen
import com.khanabook.lite.pos.ui.screens.SettingsScreen
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextLight

import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.MenuViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.khanabook.lite.pos.ui.navigation.TabItem
import com.khanabook.lite.pos.ui.navigation.NavigationUtils
import androidx.activity.compose.BackHandler

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

    // Intercept back gesture to return to Home tab if not already there
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }
    
    Scaffold(
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
            val currentTab = visibleTabs[selectedTabIndex]
            val backToHome = { selectedTabIndex = 0 }
            when (currentTab.label) {
                "Home" -> HomeScreen(onNewBill, onSearchBill, onOrderStatus, onCallCustomer)
                "Reports" -> ReportsScreen(onBack = backToHome)
                "Orders" -> OrdersScreen(onBack = backToHome)
                "Settings" -> SettingsScreen(
                    onBack = backToHome,
                    navController = navController,
                    onScanClick = onScanClick,
                    menuViewModel = menuViewModel
                )
            }
        }
    }
}

@Composable
fun AppBottomBar(
    visibleTabs: List<TabItem>,
    currentSelectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = DarkBrown1,
        modifier = Modifier.navigationBarsPadding()
    ) {
        visibleTabs.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = currentSelectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryGold,
                    unselectedIconColor = TextLight,
                    selectedTextColor = PrimaryGold,
                    unselectedTextColor = TextLight,
                    indicatorColor = DarkBrown1
                )
            )
        }
    }
}
