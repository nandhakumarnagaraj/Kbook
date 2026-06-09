package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import com.khanabook.lite.pos.ui.designsystem.KhanaBookGlassCard
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*

import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KbBrandSaffron
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.kbBgCard
import com.khanabook.lite.pos.ui.theme.kbOutlineSubtle
import com.khanabook.lite.pos.ui.theme.kbSecondary
import java.text.SimpleDateFormat

@Composable
fun ProfileCard(user: UserEntity?, profile: RestaurantProfileEntity?, lastSyncTimestamp: Long = 0L) {
    val displayName = profile?.shopName?.takeIf { it.isNotBlank() } ?: user?.name?.takeIf { it.isNotBlank() } ?: "Guest"
    val displayPhone = profile?.whatsappNumber?.takeIf { it.isNotBlank() } ?: user?.whatsappNumber ?: ""
    val spacing = KhanaBookTheme.spacing
    val syncLabel = remember(lastSyncTimestamp) {
        if (lastSyncTimestamp > 0L) {
            "Last sync: " + SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTimestamp))
        } else "Not synced yet"
    }

    // Inline profile — no card, matches mockup with avatar + info + green sync dot
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large circle avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(KbBrandSaffron, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(modifier = Modifier.size(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = TextLight,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (displayPhone.isNotBlank()) {
                Text(
                    text = displayPhone,
                    color = BrandPurple.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Sync indicator with green dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(KbSuccess, CircleShape)
                )
                Text(
                    text = syncLabel,
                    color = KbSuccess.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
internal fun SettingsItem(icon: ImageVector, text: String, subtitle: String? = null, iconBg: Color = KbBrandSaffron.copy(alpha = 0.12f), iconTint: Color = MaterialTheme.kbSecondary, onClick: () -> Unit) {
    val spacing = KhanaBookTheme.spacing

    // Premium Glassmorphism Settings Item with optional subtitle
    KhanaBookGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular icon container with soft color bg
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.size(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = TextGold.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.kbTextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


@Composable
internal fun SettingsToggleItem(
    icon: ImageVector,
    text: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    // Glassmorphism toggle item with optional subtitle
    KhanaBookGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular icon container
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.kbPrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = MaterialTheme.kbSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.size(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text,
                        color = TextLight,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = TextGold.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            KhanaBookSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                checkedTrackColor = MaterialTheme.kbPrimary
            )
        }
    }
}

@Composable
fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.smallMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) { content() }
    }
}
