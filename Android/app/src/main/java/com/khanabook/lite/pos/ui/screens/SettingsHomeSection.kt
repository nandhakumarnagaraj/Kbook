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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
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
            // ── Inline Profile Card ────────────────────────────────────────────
            ProfileCard(currentUser, profile, lastSyncTimestamp)

            Spacer(modifier = Modifier.height(spacing.medium))

            PriorityActionsStrip(
                profile = profile,
                onSectionSelected = onSectionSelected
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Section: CONFIGURATION ─────────────────────────────────────────
            SectionLabel("CONFIGURATION")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            SettingsGroupCard(
                items = listOf(
                    Triple(Icons.Outlined.Store,          "Restaurant Configuration", "Shop name, address & branding"),
                    Triple(Icons.AutoMirrored.Outlined.ReceiptLong, "Menu Configuration",  "Items, categories & pricing"),
                    Triple(Icons.Outlined.CreditCard,     "Payment Configuration",  "UPI, Easebuzz & marketplace setup"),
                    Triple(Icons.Outlined.Print,          "Printer Configuration",  "Bluetooth & thermal printer"),
                    Triple(Icons.Outlined.Percent,        "Tax Configuration",      "GST, FSSAI & tax settings"),
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
                    Triple(Icons.Outlined.Tune, "Settings", "Display, security & dark mode"),
                ),
                onItemClick = { onSectionSelected("security") }
            )

            Spacer(modifier = Modifier.height(spacing.medium))

            // ── Section: ACCOUNT SESSION ───────────────────────────────────────
            SectionLabel("ACCOUNT SESSION")
            Spacer(modifier = Modifier.height(spacing.extraSmall))

            KhanaBookCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.extraSmall),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
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

@Composable
private fun PriorityActionsStrip(
    profile: RestaurantProfileEntity?,
    onSectionSelected: (String) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val paymentReady = profile?.upiEnabled == true || profile?.easebuzzEnabled == true
    val printerReady = profile?.printerEnabled == true
    val menuReady = !profile?.shopName.isNullOrBlank() && !profile?.shopAddress.isNullOrBlank()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        SectionLabel("PRIORITY TASKS")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            PriorityActionCard(
                modifier = Modifier.weight(1f),
                title = "Menu",
                subtitle = if (menuReady) "Branding looks ready" else "Finish shop details",
                icon = Icons.Outlined.Store,
                tone = if (menuReady) MaterialTheme.kbSecondary else KbBrandSaffron,
                onClick = { onSectionSelected("shop") }
            )
            PriorityActionCard(
                modifier = Modifier.weight(1f),
                title = "Payments",
                subtitle = if (paymentReady) "UPI or Easebuzz enabled" else "Connect payment methods",
                icon = Icons.Outlined.CreditCard,
                tone = if (paymentReady) KbSuccess else KbBrandSaffron,
                onClick = { onSectionSelected("payment") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            PriorityActionCard(
                modifier = Modifier.weight(1f),
                title = "Printing",
                subtitle = if (printerReady) "Printer is connected" else "Set up receipt printing",
                icon = Icons.Outlined.Print,
                tone = if (printerReady) KbSuccess else KbWarning,
                onClick = { onSectionSelected("printer") }
            )
            PriorityActionCard(
                modifier = Modifier.weight(1f),
                title = "Support",
                subtitle = "Help, recovery & sync tools",
                icon = Icons.Filled.Bolt,
                tone = MaterialTheme.kbSecondary,
                onClick = { onSectionSelected("help_support") }
            )
        }
    }
}

@Composable
private fun PriorityActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    KhanaBookCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard)
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(tone.copy(alpha = KbOpacity.StatusBg), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        color = MaterialTheme.kbTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
