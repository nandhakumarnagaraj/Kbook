package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.domain.util.BatteryOptimizationHelper
import com.khanabook.lite.pos.ui.theme.*

/**
 * One-time setup guidance so background bill sync keeps working on OEM ROMs
 * that aggressively kill background apps (MIUI, ColorOS, FuntouchOS, RealmeUI, EMUI).
 */
@Composable
fun BackgroundReliabilityScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing
    var batteryExempt by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    var autoStartOpened by remember { mutableStateOf(false) }
    val needsOemStep = remember { BatteryOptimizationHelper.requiresOemAutoStart() }
    val manufacturer = remember { BatteryOptimizationHelper.manufacturerLabel() }

    // Re-check exemption whenever the user returns from system settings.
    LaunchedEffect(Unit) {
        batteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DarkBrown1, DarkBrown2, RichEspresso)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            Text(
                text = "Keep bills syncing",
                style = MaterialTheme.typography.headlineSmall,
                color = PrimaryGold,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your phone may stop KhanaBook in the background, which pauses bill sync. " +
                    "Allow the two settings below so orders always reach your reports.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGold
            )

            SetupStepCard(
                title = "1. Allow background battery use",
                description = if (batteryExempt) {
                    "Already allowed. Sync can run in the background."
                } else {
                    "Choose \"Allow\" or \"Don't optimise\" on the next screen."
                },
                actionLabel = if (batteryExempt) "Allowed" else "Allow",
                actionEnabled = !batteryExempt,
                completed = batteryExempt,
                icon = Icons.Default.BatteryAlert,
                onAction = {
                    BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                }
            )

            if (needsOemStep) {
                SetupStepCard(
                    title = "2. Turn on auto-start ($manufacturer)",
                    description = "Find KhanaBook in the list and enable auto-start / background run.",
                    actionLabel = if (autoStartOpened) "Open again" else "Open settings",
                    actionEnabled = true,
                    completed = autoStartOpened,
                    icon = Icons.Default.PlayCircleOutline,
                    onAction = {
                        autoStartOpened = BatteryOptimizationHelper.openOemAutoStartSettings(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(spacing.small))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold),
                shape = KhanaRadii.button
            ) {
                Text("Continue", color = DarkBrown1, fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now", color = TextGold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SetupStepCard(
    title: String,
    description: String,
    actionLabel: String,
    actionEnabled: Boolean,
    completed: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Surface(
        shape = KhanaRadii.card,
        color = DarkBrown2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Icon(
                    imageVector = if (completed) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (completed) SuccessGreen else PrimaryGold,
                    modifier = Modifier.size(KhanaBookTheme.iconSize.medium)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextLight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextGold
            )
            OutlinedButton(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = KhanaRadii.button,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                border = androidx.compose.foundation.BorderStroke(
                    KhanaBookTheme.spacing.hairline,
                    if (actionEnabled) BorderGold else BorderGold.copy(alpha = 0.3f)
                )
            ) {
                Text(actionLabel)
            }
        }
    }
}
