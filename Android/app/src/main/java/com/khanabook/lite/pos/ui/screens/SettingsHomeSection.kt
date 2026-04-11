package com.khanabook.lite.pos.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
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

    AnimatedVisibility(visible = screenVisible, enter = enterSpec, exit = exitSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.medium)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(spacing.small))
            ProfileCard(currentUser, profile, lastSyncTimestamp)
            Spacer(modifier = Modifier.height(spacing.medium))

            if (isWideScreen) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsItem(icon = Icons.Filled.Store, text = "Shop Configuration") {
                            onSectionSelected("shop")
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration") {
                            onSectionSelected("menu_config")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration") {
                            onSectionSelected("payment")
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration") {
                            onSectionSelected("printer")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsItem(icon = Icons.Filled.Settings, text = "Tax Configuration") {
                            onSectionSelected("tax")
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SettingsItem(icon = Icons.Filled.Lock, text = "App Lock (PIN / Biometric)") {
                            onSectionSelected("security")
                        }
                    }
                }
            } else {
                SettingsItem(icon = Icons.Filled.Store, text = "Shop/Restaurant Configuration") {
                    onSectionSelected("shop")
                }
                SettingsItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "Menu Configuration") {
                    onSectionSelected("menu_config")
                }
                SettingsItem(icon = Icons.Filled.CreditCard, text = "Payment Configuration") {
                    onSectionSelected("payment")
                }
                SettingsItem(icon = Icons.Filled.Print, text = "Printer Configuration") {
                    onSectionSelected("printer")
                }
                SettingsItem(icon = Icons.Filled.Settings, text = "Tax Configuration") {
                    onSectionSelected("tax")
                }
                SettingsItem(icon = Icons.Filled.Lock, text = "App Lock (PIN / Biometric)") {
                    onSectionSelected("security")
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            KhanaBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.small),
                colors = CardDefaults.cardColors(containerColor = CardBG),
                shape = RoundedCornerShape(12.dp)
            ) {
                LogoutSection(logoutViewModel)
            }
            AppInfoSection()
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }
    }
}
