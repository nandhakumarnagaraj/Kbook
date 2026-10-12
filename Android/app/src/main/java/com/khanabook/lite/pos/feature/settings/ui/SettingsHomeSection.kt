package com.khanabook.lite.pos.feature.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.auth.data.UserEntity
import com.khanabook.lite.pos.core.designsystem.BoundedVerticalSpaceBetween
import com.khanabook.lite.pos.core.designsystem.verticalScrollbar
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.TypeScaleTier
import com.khanabook.lite.pos.feature.auth.ui.SettingsGroupLabel
import com.khanabook.lite.pos.feature.auth.viewmodel.LogoutViewModel

private data class SettingsEntry(
    val icon: ImageVector,
    val label: String,
    val section: String,
    val group: String
)

// Single-column settings list (v1 design decision), grouped for scale
// (design review #8): Restaurant / Payments & Billing / App.
private val GroupOrder = listOf("Restaurant", "Payments & Billing", "App")

private val SettingsEntries = listOf(
    SettingsEntry(Icons.Filled.Store, "Restaurant Configuration", "shop", "Restaurant"),
    SettingsEntry(Icons.AutoMirrored.Filled.ReceiptLong, "Menu Configuration", "menu_config", "Restaurant"),
    SettingsEntry(Icons.Filled.Percent, "Tax Configuration", "tax", "Restaurant"),
    SettingsEntry(Icons.Filled.CreditCard, "Payment Configuration", "payment", "Payments & Billing"),
    SettingsEntry(Icons.Filled.Print, "Printer Configuration", "printer", "Payments & Billing"),
    SettingsEntry(Icons.Filled.Tune, "App Settings", "security", "App")
)

/** One labeled group of settings cards. */
@Composable
private fun SettingsGroup(
    group: String,
    onSectionSelected: (String) -> Unit
) {
    SettingsGroupLabel(group)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KhanaBookTheme.spacing.small)
    ) {
        SettingsEntries.filter { it.group == group }.forEach { entry ->
            SettingsItem(
                icon = entry.icon,
                text = entry.label,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSectionSelected(entry.section) }
            )
        }
    }
}

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

    AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = layout.maxContentWidth)
                    .align(Alignment.TopCenter)
                    .verticalScroll(scrollState)
                    .padding(horizontal = layout.contentPadding)
                    .padding(
                        top = if (layout.typeScaleTier == TypeScaleTier.Tablet) spacing.medium else spacing.small,
                        bottom = spacing.medium
                    ),
                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
            ) {
                ProfileCard(
                    user = currentUser,
                    profile = profile,
                    lastSyncTimestamp = lastSyncTimestamp,
                    onClick = { onSectionSelected("shop") }
                )
                GroupOrder.forEach { group -> SettingsGroup(group, onSectionSelected) }
                LogoutSection(logoutViewModel)
            }
        }
    }
}
