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
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.TypeScaleTier
import com.khanabook.lite.pos.feature.auth.viewmodel.LogoutViewModel

private data class SettingsEntry(
    val icon: ImageVector,
    val label: String,
    val section: String
)

// Single-column settings list (v1 design decision).
private val SettingsEntries = listOf(
    SettingsEntry(Icons.Filled.Store, "Restaurant Configuration", "shop"),
    SettingsEntry(Icons.AutoMirrored.Filled.ReceiptLong, "Menu Configuration", "menu_config"),
    SettingsEntry(Icons.Filled.CreditCard, "Payment Configuration", "payment"),
    SettingsEntry(Icons.Filled.Print, "Printer Configuration", "printer"),
    SettingsEntry(Icons.Filled.Percent, "Tax Configuration", "tax"),
    SettingsEntry(Icons.Filled.Tune, "App Settings", "security")
)

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
        Box(modifier = Modifier.fillMaxSize()) {
            if (layout.typeScaleTier == TypeScaleTier.Tablet) {
                // Tablet: Home-faithful rhythm. Cards are separate top-level children so
                // residual viewport height distributes evenly across ALL section gaps,
                // capped by maxSectionGap — cards keep natural heights, never stretch.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = layout.maxContentWidth)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = layout.contentPadding, vertical = spacing.medium),
                    verticalArrangement = remember(sectionSpacing, layout.maxSectionGap) {
                        BoundedVerticalSpaceBetween(sectionSpacing, layout.maxSectionGap)
                    }
                ) {
                    ProfileCard(currentUser, profile, lastSyncTimestamp)
                    SettingsEntries.forEach { entry ->
                        SettingsItem(
                            icon = entry.icon,
                            text = entry.label,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSectionSelected(entry.section) }
                        )
                    }
                }
            } else {
                // Phones (incl. compact heights): original controlled spacing —
                // no distribution of remaining height between cards. Scrollable so
                // content never clips (compact-height / landscape / large font scale).
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = layout.maxContentWidth)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = layout.contentPadding, vertical = spacing.small)
                        .padding(bottom = spacing.buttonHeightCompact + spacing.smallMedium),
                    verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                ) {
                    ProfileCard(currentUser, profile, lastSyncTimestamp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                    ) {
                        SettingsEntries.forEach { entry ->
                            SettingsItem(
                                icon = entry.icon,
                                text = entry.label,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onSectionSelected(entry.section) }
                            )
                        }
                    }
                }
            }

            // Sign Out pinned just above the bottom bar — it never sits stranded
            // in a residual void on tall windows, and stays reachable on phones.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = layout.maxContentWidth)
                    .align(Alignment.BottomStart)
                    .padding(horizontal = layout.contentPadding, vertical = spacing.smallMedium)
            ) {
                LogoutSection(logoutViewModel)
            }
        }
    }
}