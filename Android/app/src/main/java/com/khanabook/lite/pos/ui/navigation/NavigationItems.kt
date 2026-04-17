package com.khanabook.lite.pos.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.ui.graphics.vector.ImageVector

data class TabItem(val label: String, val icon: ImageVector, val originalIndex: Int)

object NavigationUtils {
    private val allTabs = listOf(
        TabItem("Home", Icons.Default.Home, 0),
        TabItem("Reports", Icons.Default.Assessment, 2),
        TabItem("Orders", Icons.AutoMirrored.Filled.List, 3),
        TabItem("Profile", Icons.Default.AccountCircle, 4)
    )

    fun getVisibleTabs(): List<TabItem> {
        return allTabs
    }
}
