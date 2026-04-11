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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
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

    KhanaBookCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(PrimaryGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = displayName.take(1).uppercase(), color = DarkBrown1, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.size(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayName, color = TextLight, style = MaterialTheme.typography.titleLarge)
                if (displayPhone.isNotBlank()) {
                    Text(text = displayPhone, color = TextGold, style = MaterialTheme.typography.bodySmall)
                }
                Text(text = syncLabel, color = TextGold.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun SettingsItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    KhanaBookCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.small - spacing.hairline),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PrimaryGold, modifier = Modifier.size(iconSize.medium))
                Spacer(modifier = Modifier.size(spacing.medium))
                Text(text, color = TextLight, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PrimaryGold)
        }
    }
}

@Composable
fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    val spacing = KhanaBookTheme.spacing
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.medium),
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGold.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(spacing.large)) { content() }
    }
}
