package com.khanabook.lite.pos.ui.screens

import com.khanabook.lite.pos.ui.theme.KhanaRadii

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.screens.applock.SettingsGroupLabel
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme

@Composable
fun SettingsListView(
    onSelectItem: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        SettingsGroupLabel("Security")
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.Filled.Lock,
                text = "App Lock",
                onClick = { onSelectItem("app_lock") }
            )
        }

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.Filled.Password,
                text = "Change Password",
                onClick = { onSelectItem("change_password") }
            )
        }

        SettingsGroupLabel("Appearance")
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.Filled.TextIncrease,
                text = "Display",
                onClick = { onSelectItem("ui_scale") }
            )
        }

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                text = "Interaction Feedback",
                onClick = { onSelectItem("interaction_feedback") }
            )
        }

        SettingsGroupLabel("Data")
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.Filled.SyncProblem,
                text = "Sync Center",
                onClick = { onSelectItem("sync_center") }
            )
        }

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.Notifications,
                text = "Notifications",
                onClick = { onSelectItem("notifications") }
            )
        }

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = RoundedCornerShape(12.dp)
        ) {
            SettingsItem(
                icon = Icons.Default.ReceiptLong,
                text = "Marketplace Orders",
                onClick = { onSelectItem("marketplace_orders") }
            )
        }

        SettingsGroupLabel("About")
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                text = "Help & Support",
                onClick = { onSelectItem("help_support") }
            )
        }

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            SettingsItem(
                icon = Icons.Filled.Info,
                text = "About App",
                onClick = { onSelectItem("about_app") }
            )
        }
    }
}
