@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.khanabook.lite.pos.feature.printing.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.core.designsystem.KhanaBookCard
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.designsystem.KhanaButtonRow
import com.khanabook.lite.pos.core.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.core.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.core.theme.KhanaBookLiteTheme
import com.khanabook.lite.pos.core.theme.WarningYellow

@Composable
fun PrinterTargetCard(
    title: String,
    printerName: String,
    connectionDescription: String?,
    enabled: Boolean,
    autoPrint: Boolean,
    showAutoPrintToggle: Boolean,
    paper58: Boolean,
    includeLogo: Boolean,
    showLogoToggle: Boolean,
    isConnected: Boolean,
    health: com.khanabook.lite.pos.feature.printing.domain.PrinterHealth? = null,
    helperText: String?,
    onConfigureWifi: (() -> Unit)?,
    onSelectUsb: (() -> Unit)? = null,
    onEnabledChange: (Boolean) -> Unit,
    onAutoPrintChange: (Boolean) -> Unit,
    onPaperSizeChange: (Boolean) -> Unit,
    onIncludeLogoChange: (Boolean) -> Unit,
    onSelectPrinter: () -> Unit,
    onTestPrint: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGold.copy(alpha = 0.3f), KhanaRadii.md)
            .padding(spacing.medium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                KhanaBookSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(printerName, color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(spacing.small))
                    Box(modifier = Modifier.size(8.dp).background(dotColor(isConnected, health), CircleShape))
                }
                Text("Connection: ${connectionDescription ?: "---"}", color = TextGold, style = MaterialTheme.typography.labelSmall)
                KhanaStatusBadge(
                    text = statusText(connectionDescription, isConnected, health),
                    kind = when {
                        connectionDescription.isNullOrBlank() -> KhanaStatusKind.Neutral
                        !isConnected -> KhanaStatusKind.Warning
                        health == com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.PAPER_OUT ->
                            KhanaStatusKind.Danger
                        health == com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.PAPER_LOW ||
                            health == com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.ERROR ->
                            KhanaStatusKind.Warning
                        else -> KhanaStatusKind.Success
                    },
                    filled = false
                )
                helperText?.let {
                    Text(it, color = TextGold.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
                if (showAutoPrintToggle) {
                    PrinterOptionRow("Auto Print", autoPrint) { onAutoPrintChange(it) }
                }
                if (showLogoToggle) {
                    PrinterOptionRow("Include Logo", includeLogo) { onIncludeLogoChange(it) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
                ) {
                    Text("Paper Size", color = TextGold, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            RadioButton(selected = paper58, onClick = { onPaperSizeChange(true) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                            Text("58mm", color = TextGold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                        ) {
                            RadioButton(selected = !paper58, onClick = { onPaperSizeChange(false) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                            Text("80mm", color = TextGold)
                        }
                    }
                }
                if (onConfigureWifi != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalArrangement = Arrangement.spacedBy(spacing.small)
                        // In portrait, when there isn't enough width for all three, USB wraps to its
                        // own line below BT + Wi-Fi instead of squeezing.
                    ) {
                        KhanaSecondaryButton(
                            text = "BT",
                            onClick = onSelectPrinter,
                            leadingIcon = Icons.Default.Bluetooth,
                            modifier = Modifier.weight(1f)
                        )
                        KhanaSecondaryButton(
                            text = "Wi-Fi",
                            onClick = onConfigureWifi,
                            leadingIcon = Icons.Default.Wifi,
                            modifier = Modifier.weight(1f)
                        )
                        if (onSelectUsb != null) {
                            KhanaSecondaryButton(
                                text = "USB",
                                onClick = onSelectUsb,
                                leadingIcon = Icons.Default.Usb,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    KhanaPrimaryButton(
                        text = "Test Printer",
                        onClick = onTestPrint,
                        enabled = !connectionDescription.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    KhanaButtonRow {
                        KhanaSecondaryButton(
                            text = "Select Printer",
                            onClick = onSelectPrinter,
                            modifier = Modifier.weight(1f)
                        )
                        KhanaPrimaryButton(
                            text = "Test Printer",
                            onClick = onTestPrint,
                            enabled = !connectionDescription.isNullOrBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Dot color combining connection state with ESC/POS health (paper/cover/error). */
private fun dotColor(
    isConnected: Boolean,
    health: com.khanabook.lite.pos.feature.printing.domain.PrinterHealth?
): Color = when {
    !isConnected -> DangerRed
    health == null -> SuccessGreen
    else -> when (health) {
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.HEALTHY -> SuccessGreen
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.UNKNOWN -> SuccessGreen
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.PAPER_LOW -> WarningYellow
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.PAPER_OUT -> DangerRed
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.ERROR -> WarningYellow
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.UNREACHABLE -> DangerRed
    }
}

/** Badge text combining connection state with ESC/POS health. */
private fun statusText(
    connectionDescription: String?,
    isConnected: Boolean,
    health: com.khanabook.lite.pos.feature.printing.domain.PrinterHealth?
): String = when {
    connectionDescription.isNullOrBlank() -> "No printer"
    !isConnected -> "Ready"
    health == null -> "Connected"
    else -> when (health) {
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.HEALTHY -> "Connected"
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.UNKNOWN -> "Connected"
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.PAPER_LOW -> "Paper low"
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.PAPER_OUT -> "Paper out"
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.ERROR -> "Printer error"
        com.khanabook.lite.pos.feature.printing.domain.PrinterHealth.UNREACHABLE -> "Offline"
    }
}

@Composable
fun DeviceRow(
    device: BluetoothDevice,
    isConnecting: Boolean,
    isSelected: Boolean = false,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    val iconSize = KhanaBookTheme.iconSize
    @Suppress("MissingPermission")
    val name = device.name ?: "Unknown"
    val border = if (isSelected) BorderStroke(2.dp, PrimaryGold) else null
    val backgroundColor = if (isSelected) DarkBrown1 else DarkBrown1.copy(alpha = 0.5f)

    KhanaBookCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
        onClick = if (!isConnecting) onClick else null,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = KhanaRadii.md
    ) {
        if (border != null) {
            Modifier.border(border, KhanaRadii.md)
        }
        Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                null,
                tint = if (isSelected) PrimaryGold else TextGold,
                modifier = Modifier.size(iconSize.medium)
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = TextLight, style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium))
                Text(device.address, color = if (isSelected) PrimaryGold.copy(alpha = 0.7f) else TextGold, style = MaterialTheme.typography.labelSmall)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryGold, strokeWidth = 2.dp)
            } else if (isConnected) {
                Box(modifier = Modifier.size(8.dp).background(SuccessGreen, CircleShape))
            }
        }
    }
}

@Composable
fun PrinterOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(KhanaBookTheme.spacing.buttonHeightCompact),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextGold, style = MaterialTheme.typography.bodyMedium)
        KhanaBookSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Preview(name = "Printer Card - Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Printer Card - Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Printer Card - Tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "Printer Card - Desktop", device = Devices.DESKTOP, showBackground = true)
@Composable
private fun PrinterTargetCardPreview() {
    KhanaBookLiteTheme {
        PrinterTargetCard(
            title = "Customer Receipt Printer",
            printerName = "ESC/POS BT 58mm",
            connectionDescription = "Connected",
            enabled = true,
            autoPrint = true,
            showAutoPrintToggle = true,
            paper58 = true,
            includeLogo = true,
            showLogoToggle = true,
            isConnected = true,
            helperText = "Receives orders automatically when enabled.",
            onConfigureWifi = {},
            onSelectUsb = {},
            onEnabledChange = {},
            onAutoPrintChange = {},
            onPaperSizeChange = {},
            onIncludeLogoChange = {},
            onSelectPrinter = {},
            onTestPrint = {}
        )
    }
}
