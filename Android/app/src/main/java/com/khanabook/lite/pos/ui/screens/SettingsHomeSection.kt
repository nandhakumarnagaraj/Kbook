@file:OptIn(ExperimentalLayoutApi::class)
package com.khanabook.lite.pos.ui.screens

import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.screens.applock.SettingsGroupLabel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.ui.designsystem.BoundedVerticalSpaceBetween
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.viewmodel.LogoutViewModel

@Composable
fun SettingsHomeSection(
    currentUser: UserEntity?,
    profile: RestaurantProfileEntity?,
    lastSyncTimestamp: Long,
    isWideScreen: Boolean,
    screenVisible: Boolean,
    enterSpec: EnterTransition,
    exitSpec: ExitTransition,
    logoutViewModel: LogoutViewModel,
    onSectionSelected: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val sectionSpacing = layout.sectionSpacing

    // Single-column list for settings items (v1 design decision)
    val settingsColumns = 1

    AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = layout.maxContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.contentPadding, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
            // Section 1: Profile card
            ProfileCard(currentUser, profile, lastSyncTimestamp)

            // Section 2: Settings items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                    maxItemsInEachRow = settingsColumns
                ) {
                    val itemMod = Modifier.weight(1f)
                    SettingsItem(icon = Icons.Filled.Store, text = "Restaurant Configuration", modifier = itemMod) {
                        onSectionSelected("shop")
                    }
                    SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration", modifier = itemMod) {
                        onSectionSelected("menu_config")
                    }
                    SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration", modifier = itemMod) {
                        onSectionSelected("payment")
                    }
                    SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration", modifier = itemMod) {
                        onSectionSelected("printer")
                    }
                    SettingsItem(icon = Icons.Filled.Percent, text = "Tax Configuration", modifier = itemMod) {
                        onSectionSelected("tax")
                    }
                    SettingsItem(icon = Icons.Filled.Tune, text = "Settings", modifier = itemMod) {
                        onSectionSelected("security")
                    }
                    SettingsItem(icon = Icons.Filled.People, text = "Staff Permissions", modifier = itemMod) {
                        onSectionSelected("staff_permissions")
                    }
                    // TODO: re-enable for next version
                    // SettingsItem(icon = Icons.Filled.Inventory2, text = "Inventory & Insights", modifier = itemMod) {
                    //     onSectionSelected("inventory")
                    // }
                }
            }

            // Section 3: Logout
            KhanaBookCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = KhanaRadii.lg
            ) {
                LogoutSection(logoutViewModel)
            }
        }
    }
}
