package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.feedback.rememberMenuFeedbackPreferences
import com.khanabook.lite.pos.ui.feedback.rememberMenuFeedbackSettings
import com.khanabook.lite.pos.ui.feedback.rememberMenuItemAddFeedback
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.VegGreen

@Composable
fun InteractionFeedbackView() {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    val preferences = rememberMenuFeedbackPreferences()
    val settings by rememberMenuFeedbackSettings(preferences)
    val playPreview = rememberMenuItemAddFeedback(settings)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.card
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FeedbackPreferenceRow(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(iconSize.medium)
                        )
                    },
                    title = "Menu item sound",
                    description = "Play a short soft pop when an item is added",
                    checked = settings.soundEnabled,
                    onCheckedChange = preferences::setSoundEnabled
                )

                HorizontalDivider(color = BorderGold.copy(alpha = 0.25f))

                FeedbackPreferenceRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(iconSize.medium)
                        )
                    },
                    title = "Touch feedback",
                    description = "Add a subtle tap vibration with the sound",
                    checked = settings.hapticEnabled,
                    onCheckedChange = preferences::setHapticEnabled
                )
            }
        }

        Text(
            text = "The item-add cue is intentionally different from payment and KOT confirmations.",
            color = TextGold.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = spacing.small)
        )

        OutlinedButton(
            onClick = playPreview,
            enabled = settings.soundEnabled || settings.hapticEnabled,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, BorderGold),
            shape = KhanaRadii.button
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = PrimaryGold,
                modifier = Modifier.size(iconSize.small)
            )
            Spacer(modifier = Modifier.width(spacing.small))
            Text(
                text = "Test enabled feedback",
                color = TextLight,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun FeedbackPreferenceRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val spacing = KhanaBookTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            Text(
                text = title,
                color = TextLight,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                color = TextGold.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(spacing.small))
        KhanaBookSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            checkedTrackColor = VegGreen
        )
    }
}
