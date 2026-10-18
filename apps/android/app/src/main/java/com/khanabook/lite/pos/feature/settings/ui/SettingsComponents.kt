@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.khanabook.lite.pos.feature.settings.ui
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.core.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.core.theme.*
import com.khanabook.lite.pos.core.designsystem.*
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel

@Composable
internal fun DisplayScaleView(viewModel: SettingsViewModel) {
    val spacing = KhanaBookTheme.spacing
    val layout = KhanaBookTheme.layout
    val displayScale by viewModel.displayScaleState.collectAsStateWithLifecycle()
    val scaleLabels = listOf("Small", "Default", "Large", "X-Large")
    val scaleValues = listOf(0.85f, 1.0f, 1.15f, 1.3f)
    val sliderIndex = scaleValues.indexOfFirst { it >= displayScale }.coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(spacing.medium))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaShapes.medium
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text("Automatic Layout", color = TextLight, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    text = buildString {
                        append(if (layout.isWideListDetail) "Tablet" else "Phone")
                        append(" · ${layout.screenWidthDp} × ${layout.screenHeightDp} dp")
                        append(if (layout.isLandscape) " · Landscape" else " · Portrait")
                    },
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                if (layout.isCompactHeight) {
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Text(
                        "Compact-height layouts are enabled automatically.",
                        color = PrimaryGold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        KhanaBookCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBG),
            shape = KhanaRadii.lg
        ) {
            Column(modifier = Modifier.padding(spacing.large)) {
                Text("UI Scale", color = TextLight, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    "Adjust the overall size of text and UI elements.",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(spacing.large))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    scaleLabels.forEachIndexed { i, label ->
                        Text(
                            text = label,
                            color = if (i == sliderIndex) PrimaryGold else TextGold.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (i == sliderIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Slider(
                    value = displayScale,
                    onValueChange = { viewModel.updateDisplayScale(it) },
                    valueRange = 0.80f..1.35f,
                    steps = 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryGold,
                        activeTrackColor = PrimaryGold,
                        inactiveTrackColor = BorderGold.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(spacing.small))

                Text(
                    text = "Preview",
                    color = TextGold,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(spacing.small))

                KhanaBookCard(
                    colors = CardDefaults.cardColors(containerColor = DarkBrown2),
                    shape = KhanaRadii.md
                ) {
                    Column(modifier = Modifier.padding(spacing.medium)) {
                        Text(
                            "Sample Item",
                            color = TextLight,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(spacing.hairline))
                        Text(
                            "₹ 100.00",
                            color = PrimaryGold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "This is how text and cards will appear at the selected scale.",
                            color = TextGold.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.extraLarge))
    }
}


