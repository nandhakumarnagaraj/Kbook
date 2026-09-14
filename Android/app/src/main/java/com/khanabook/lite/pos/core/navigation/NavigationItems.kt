package com.khanabook.lite.pos.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.graphics.vector.ImageVector

data class TabItem(
    val label: String,
    val icon: ImageVector,
    val originalIndex: Int,
    /** Optional Lottie animation (res/raw) played once when the tab is selected.
     *  When null, the vector icon gets a springy bounce instead. */
    val lottieRes: Int? = null
)

object NavigationTabs {
    const val TAB_HOME = 0
    const val TAB_REPORTS = 1
    const val TAB_ORDERS = 2
    const val TAB_PROFILE = 3
}

object NavigationUtils {
    private val allTabs = listOf(
        TabItem("Home", Icons.Default.Home, NavigationTabs.TAB_HOME),
        TabItem("Reports", Icons.Default.Assessment, NavigationTabs.TAB_REPORTS),
        TabItem("Orders", Icons.AutoMirrored.Filled.List, NavigationTabs.TAB_ORDERS),
        TabItem("Profile", Icons.Default.AccountCircle, NavigationTabs.TAB_PROFILE)
    )

    fun getVisibleTabs(): List<TabItem> {
        return allTabs
    }
}
