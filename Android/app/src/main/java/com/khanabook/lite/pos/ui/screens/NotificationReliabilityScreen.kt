package com.khanabook.lite.pos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.util.OemHardeningHelper

@Composable
fun NotificationReliabilityView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = KhanaBookTheme.spacing

    // Refreshable state for battery optimization
    var isBatteryIgnored by remember {
        mutableStateOf(OemHardeningHelper.isBatteryOptimizationIgnored(context))
    }

    val isAggressiveOem = remember { OemHardeningHelper.isAggressiveOem() }

    // Automatically check on resume/start
    androidx.lifecycle.compose.LifecycleStartEffect(Unit) {
        isBatteryIgnored = OemHardeningHelper.isBatteryOptimizationIgnored(context)
        onStopOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.medium)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "BACKGROUND PROTECTION",
            color = KbBrandSaffron,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.kbBgCard
            ),
            shape = KbShape.Large,
            elevation = CardDefaults.cardElevation(defaultElevation = KbElevation.Low)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isBatteryIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isBatteryIgnored) KbSuccess else KbError,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (isBatteryIgnored) "Battery optimization is disabled" else "Battery optimization is active",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.kbTextPrimary
                        )
                        Text(
                            text = if (isBatteryIgnored) "Notifications will be delivered instantly." else "The OS may delay or block notifications to save battery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.kbTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "REQUIRED ACTIONS",
            color = KbBrandSaffron,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Action Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.kbBgCard
            ),
            shape = KbShape.Large,
            elevation = CardDefaults.cardElevation(defaultElevation = KbElevation.Low)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Action 1: Request ignore battery optimization
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Disable Battery Saver",
                            color = MaterialTheme.kbTextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "Allow KhanaBook to run in the background for reliable alerts.",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            OemHardeningHelper.requestIgnoreBatteryOptimization(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBatteryIgnored) MaterialTheme.kbBgSecondary else KbBrandSaffron,
                            contentColor = if (isBatteryIgnored) MaterialTheme.kbTextSecondary else Color.White
                        ),
                        shape = KbShape.Small,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = if (isBatteryIgnored) "Disabled" else "Disable",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (isAggressiveOem) {
                    HorizontalDivider(color = MaterialTheme.kbOutlineSubtle)

                    // Action 2: OEM Auto-Start settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable OEM Auto-Start",
                                color = MaterialTheme.kbTextPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "Grant permissions to auto-start on device reboot or wake up.",
                                color = MaterialTheme.kbTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                OemHardeningHelper.launchAutostartSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KbBrandSaffron,
                                contentColor = Color.White
                            ),
                            shape = KbShape.Small,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Info Tip Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.kbBgSecondary
            ),
            shape = KbShape.Medium
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Why is this needed?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.kbTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Indian mobile manufacturers (Redmi, Realme, Oppo, Vivo, etc.) enforce aggressive battery management rules which shut down background processes. Whitelisting the app ensures you never miss a real-time order or payment alert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.kbTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}
