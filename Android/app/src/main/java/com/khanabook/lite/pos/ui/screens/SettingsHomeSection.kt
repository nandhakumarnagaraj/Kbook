package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.ui.theme.*
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

    AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.medium)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Inline Profile Card (only visible in wide screen pane) ──────────
            if (isWideScreen) {
                ProfileCard(
                    user = currentUser,
                    profile = profile,
                    lastSyncTimestamp = lastSyncTimestamp,
                    onEditClick = { onSectionSelected("shop") }
                )
                Spacer(modifier = Modifier.height(spacing.medium))
            }

            // ── Section: CONFIGURATION ─────────────────────────────────────────
            SectionLabel("CONFIGURATION")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            SettingsGroupCard(
                items = listOf(
                    SettingsItemInfo(
                        icon = Icons.Outlined.Store,
                        title = "Restaurant",
                        subtitle = "Shop name, address & branding",
                        iconBg = Color(0xFFEF4444).copy(alpha = 0.08f),
                        iconTint = Color(0xFFEF4444)
                    ),
                    SettingsItemInfo(
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        title = "Menu",
                        subtitle = "Items, categories & pricing",
                        iconBg = Color(0xFFF97316).copy(alpha = 0.08f),
                        iconTint = Color(0xFFF97316)
                    ),
                    SettingsItemInfo(
                        icon = Icons.Outlined.CreditCard,
                        title = "Payment",
                        subtitle = "UPI, Easebuzz & marketplace setup",
                        iconBg = Color(0xFF0284C7).copy(alpha = 0.08f),
                        iconTint = Color(0xFF0284C7),
                        badgeText = "Action needed"
                    ),
                    SettingsItemInfo(
                        icon = Icons.Outlined.Print,
                        title = "Printer",
                        subtitle = "Bluetooth & thermal printer",
                        iconBg = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                        iconTint = Color(0xFF8B5CF6)
                    ),
                    SettingsItemInfo(
                        icon = Icons.Outlined.Percent,
                        title = "Tax & GST",
                        subtitle = "GST, FSSAI & tax settings",
                        iconBg = Color(0xFF16A34A).copy(alpha = 0.08f),
                        iconTint = Color(0xFF16A34A)
                    )
                ),
                onItemClick = { index ->
                    val dest = when (index) {
                        0 -> "shop"
                        1 -> "menu_config"
                        2 -> "payment"
                        3 -> "printer"
                        4 -> "tax"
                        else -> "menu"
                    }
                    onSectionSelected(dest)
                }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Section: PREFERENCES ───────────────────────────────────────────
            SectionLabel("PREFERENCES")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            SettingsGroupCard(
                items = listOf(
                    SettingsItemInfo(
                        icon = Icons.Outlined.Settings,
                        title = "Display & Security",
                        subtitle = "Dark mode, PIN & notifications",
                        iconBg = Color(0xFF7C5CDB).copy(alpha = 0.08f),
                        iconTint = Color(0xFF7C5CDB)
                    )
                ),
                onItemClick = { onSectionSelected("security") }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            LogoutSection(logoutViewModel)
            Spacer(modifier = Modifier.height(spacing.large))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val spacing = KhanaBookTheme.spacing
    Text(
        text = text,
        color = MaterialTheme.kbSecondary,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(horizontal = spacing.extraSmall, vertical = spacing.extraSmall)
    )
}
