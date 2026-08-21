@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens.printerconfig

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaButtonRow
import com.khanabook.lite.pos.ui.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.ui.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.ui.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.ui.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.KhanaRadii
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight

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
    helperText: String?,
    onConfigureWifi: (() -> Unit)?,
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
                    Box(modifier = Modifier.size(8.dp).background(if (isConnected) SuccessGreen else DangerRed, CircleShape))
                }
                Text("Connection: ${connectionDescription ?: "---"}", color = TextGold, style = MaterialTheme.typography.labelSmall)
                KhanaStatusBadge(
                    text = when {
                        connectionDescription.isNullOrBlank() -> "No printer"
                        isConnected -> "Connected"
                        else -> "Ready"
                    },
                    kind = when {
                        connectionDescription.isNullOrBlank() -> KhanaStatusKind.Neutral
                        isConnected -> KhanaStatusKind.Success
                        else -> KhanaStatusKind.Warning
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
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small)
                ) {
                    RadioButton(selected = paper58, onClick = { onPaperSizeChange(true) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                    Text("58mm", color = TextGold)
                    Spacer(modifier = Modifier.width(spacing.large))
                    RadioButton(selected = !paper58, onClick = { onPaperSizeChange(false) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                    Text("80mm", color = TextGold)
                }
                if (onConfigureWifi != null) {
                    KhanaButtonRow {
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
