package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.kbSecondary
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
            // ── Inline Profile Card (no card background) ──────────────────────
            ProfileCard(currentUser, profile, lastSyncTimestamp)

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Section: CONFIGURATION ────────────────────────────────────────
            SectionLabel("CONFIGURATION")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            SettingsItem(
                icon = Icons.Outlined.Store,
                text = "Restaurant Configuration",
                subtitle = "Shop name, address & branding",
                onClick = { onSectionSelected("shop") }
            )
            SettingsItem(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                text = "Menu Configuration",
                subtitle = "Items, categories & pricing",
                onClick = { onSectionSelected("menu_config") }
            )
            SettingsItem(
                icon = Icons.Outlined.CreditCard,
                text = "Payment Configuration",
                subtitle = "UPI, cards & online payments",
                onClick = { onSectionSelected("payment") }
            )
            SettingsItem(
                icon = Icons.Outlined.Print,
                text = "Printer Configuration",
                subtitle = "Bluetooth & thermal printer",
                onClick = { onSectionSelected("printer") }
            )
            SettingsItem(
                icon = Icons.Outlined.Percent,
                text = "Tax Configuration",
                subtitle = "GST, FSSAI & tax settings",
                onClick = { onSectionSelected("tax") }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Section: PREFERENCES ──────────────────────────────────────────
            SectionLabel("PREFERENCES")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            SettingsItem(
                icon = Icons.Outlined.Tune,
                text = "Settings",
                subtitle = "Display, security & dark mode",
                onClick = { onSectionSelected("security") }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Section: ACCOUNT SESSION ──────────────────────────────────────
            SectionLabel("ACCOUNT SESSION")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            KhanaBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraSmall),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = RoundedCornerShape(10.dp)
            ) {
                LogoutSection(logoutViewModel)
            }
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
