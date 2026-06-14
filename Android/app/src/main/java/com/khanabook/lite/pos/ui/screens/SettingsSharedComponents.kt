package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import java.text.SimpleDateFormat

/**
 * A single grouped card that wraps multiple [SettingsGroupItem] rows separated by [HorizontalDivider].
 * All items in one section (e.g., CONFIGURATION) share this one card, matching the design mockup.
 */
data class SettingsItemInfo(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val iconBg: Color,
    val iconTint: Color,
    val badgeText: String? = null,
    // When [checked] is non-null the row renders a trailing switch instead of a
    // chevron, and tapping the row (or the switch) calls [onCheckedChange].
    val checked: Boolean? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null
)

/**
 * A single grouped card that wraps multiple [SettingsGroupItem] rows separated by [HorizontalDivider].
 * All items in one section (e.g., CONFIGURATION) share this one card, matching the design mockup.
 */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    items: List<SettingsItemInfo>,
    onItemClick: (Int) -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
        shape = KbShape.Large,
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle)
    ) {
        Column {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            when (index) {
                                0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                                items.lastIndex -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                        )
                        .clickable {
                            if (item.checked != null) item.onCheckedChange?.invoke(!item.checked)
                            else onItemClick(index)
                        }
                        .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Icon container with custom bg color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(item.iconBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(spacing.medium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = item.subtitle,
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (item.badgeText != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(KbAccentRedSurface, KbShape.ExtraSmall)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.badgeText,
                                        color = KbAccentRed,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (item.checked != null) {
                        KhanaBookSwitch(
                            checked = item.checked,
                            onCheckedChange = { item.onCheckedChange?.invoke(it) },
                            checkedTrackColor = KbBrandSaffron
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.kbTextSecondary.copy(alpha = KbOpacity.Overlay),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp), // align with text, skip icon
                        thickness = 0.5.dp,
                        color = MaterialTheme.kbOutlineSubtle
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileCard(
    user: UserEntity?,
    profile: RestaurantProfileEntity?,
    lastSyncTimestamp: Long = 0L,
    onEditClick: () -> Unit
) {
    val displayName = profile?.shopName?.takeIf { it.isNotBlank() } ?: user?.name?.takeIf { it.isNotBlank() } ?: "Guest"
    val displayPhone = profile?.whatsappNumber?.takeIf { it.isNotBlank() } ?: user?.whatsappNumber ?: ""
    val spacing = KhanaBookTheme.spacing
    val syncLabel = remember(lastSyncTimestamp) {
        if (lastSyncTimestamp > 0L) {
            "Synced " + SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTimestamp))
        } else "Not synced yet"
    }

    val context = LocalContext.current
    val logoModel = remember(profile) {
        profile?.logoUrl?.takeIf { it.isNotBlank() }
            ?: com.khanabook.lite.pos.domain.util.AppAssetStore.resolveAssetPath(profile?.logoPath)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.medium, vertical = spacing.smallMedium),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            // Rounded square avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KbBrandSaffron),
                contentAlignment = Alignment.Center
            ) {
                if (!logoModel.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
                            .data(logoModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Shop Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (displayPhone.isNotBlank()) {
                    Text(
                        text = displayPhone,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
                        color = KbSuccess.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Edit button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onEditClick() }
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Edit",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
internal fun SettingsItem(icon: ImageVector, text: String, subtitle: String? = null, iconColor: Color = MaterialTheme.kbSecondary, onClick: () -> Unit) {
    val spacing = KhanaBookTheme.spacing

    // Neutral settings card matching the Profile screen (kbBgCard + subtle outline),
    // with a per-item coloured icon instead of an all-saffron glass card.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall)
            .clip(KbShape.Large)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
        shape = KbShape.Large
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
                        .background(iconColor.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.size(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text,
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = MaterialTheme.kbTextSecondary,
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
    iconColor: Color = MaterialTheme.kbSecondary,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    // Neutral toggle card matching the Profile screen, with a per-item coloured icon.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.extraSmall),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle),
        shape = KbShape.Large
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
                        .background(iconColor.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.size(spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text,
                        color = MaterialTheme.kbTextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            KhanaBookSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                checkedTrackColor = KbBrandSaffron
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
        shape = KbShape.Large,
        border = BorderStroke(1.dp, MaterialTheme.kbOutlineSubtle)
    ) {
        Column(modifier = Modifier.padding(spacing.medium)) { content() }
    }
}
