package com.khanabook.lite.pos.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TypeScaleTier

@Composable
fun StatItem(
    label: String, 
    value: String, 
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    large: Boolean = false
) {
    val spacing = KhanaBookTheme.spacing
    // `large` stat values are the hero numbers of a summary card: keep titleMedium on
    // CompactPhone (where three values share one row) but scale up on roomier tiers
    // so the money figures outrank the section title above them.
    val largeStatStyle = if (!large || KhanaBookTheme.typeScale == TypeScaleTier.CompactPhone) {
        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    } else {
        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    }
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = value,
            color = PrimaryGold,
            style = if (large) largeStatStyle else {
                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = TextGold,
            style = if (large) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelSmall
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = spacing.extraSmall)
        )
    }
}
