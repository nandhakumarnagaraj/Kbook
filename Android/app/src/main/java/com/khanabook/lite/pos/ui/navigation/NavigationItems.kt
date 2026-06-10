package com.khanabook.lite.pos.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.vector.ImageVector

data class TabItem(val label: String, val icon: ImageVector, val originalIndex: Int)

object NavigationUtils {
    private val allTabs = listOf(
        TabItem("Home", Icons.Default.Home, 0),
        TabItem("Orders", Icons.AutoMirrored.Filled.List, 1),
        TabItem("Menu", Icons.Default.Store, 2),
        TabItem("Settings", Icons.Default.Settings, 3)
    )

    fun getVisibleTabs(): List<TabItem> {
        return allTabs
    }
}
