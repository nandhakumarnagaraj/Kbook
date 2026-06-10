package com.khanabook.lite.pos.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

data class TabItem(val label: String, val icon: ImageVector, val originalIndex: Int)

object NavigationUtils {
    private val allTabs = listOf(
        TabItem("Home", Icons.Default.Home, 0),
        TabItem("Orders", Icons.Default.List, 1),
        TabItem("Reports", Icons.Default.TrendingUp, 2),
        TabItem("Profile", Icons.Default.Person, 3)
    )

    fun getVisibleTabs(): List<TabItem> {
        return allTabs
    }
}
