package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.Brown500
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.DarkBrownSheet
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.Green800
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.theme.VegGreen
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val spacing = KhanaBookTheme.spacing
    val customerPrinter by viewModel.customerPrinter.collectAsStateWithLifecycle()
    val kitchenPrinter by viewModel.kitchenPrinter.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(profile?.printerEnabled ?: false) }
    var paper58 by remember { mutableStateOf((profile?.paperSize ?: "58mm") == "58mm") }
    var autoPrint by remember { mutableStateOf(profile?.autoPrintOnSuccess ?: false) }
    var includeLogo by remember { mutableStateOf(profile?.includeLogoInPrint ?: true) }
    var maskPhone by remember { mutableStateOf(profile?.maskCustomerPhone ?: true) }
    var kitchenEnabled by remember(kitchenPrinter?.id, kitchenPrinter?.enabled) { mutableStateOf(kitchenPrinter?.enabled ?: false) }
    var kitchenPaper58 by remember(kitchenPrinter?.id, kitchenPrinter?.paperSize) { mutableStateOf((kitchenPrinter?.paperSize ?: "58mm") == "58mm") }
    val context = LocalContext.current
    var isBtActive by remember { mutableStateOf(viewModel.isBluetoothEnabled(context)) }
    var pendingRole by remember { mutableStateOf(PrinterRole.CUSTOMER) }

    val btDevices by viewModel.btDevices.collectAsStateWithLifecycle()
    val btIsScanning by viewModel.btIsScanning.collectAsStateWithLifecycle()
    val btIsConnected by viewModel.btIsConnected.collectAsStateWithLifecycle()
    val btIsConnecting by viewModel.btIsConnecting.collectAsStateWithLifecycle()
    val btConnectResult by viewModel.btConnectResult.collectAsStateWithLifecycle()
    var showBtSheet by remember { mutableStateOf(false) }
    var snackbarMessageRes by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isBtActive = true
            viewModel.startBluetoothScan(context)
            showBtSheet = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_CONNECT] == true && perms[Manifest.permission.BLUETOOTH_SCAN] == true
        } else {
            perms[Manifest.permission.BLUETOOTH] == true && perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }
        if (ok) {
            if (!viewModel.isBluetoothEnabled(context)) {
                bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                viewModel.startBluetoothScan(context)
                showBtSheet = true
            }
        } else {
            snackbarMessageRes = R.string.toast_permissions_required
        }
    }

    LaunchedEffect(btConnectResult) {
        btConnectResult?.let {
            snackbarMessageRes = if (it) R.string.toast_printer_connected else R.string.toast_printer_connect_failed
            if (it) showBtSheet = false
            viewModel.clearBtConnectResult()
        }
    }

    LaunchedEffect(snackbarMessageRes) {
        snackbarMessageRes?.let {
            snackbarHostState.showSnackbar(context.getString(it))
            snackbarMessageRes = null
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(spacing.medium)
        ) {
            ConfigCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bluetooth Printers", color = TextGold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(spacing.medium))
                PrinterTargetCard(
                    title = "Customer Receipt Printer",
                    printerName = customerPrinter?.name ?: "No Printer",
                    macAddress = customerPrinter?.macAddress,
                    enabled = enabled,
                    autoPrint = autoPrint,
                    showAutoPrintToggle = true,
                    paper58 = paper58,
                    includeLogo = includeLogo,
                    showLogoToggle = true,
                    isConnected = btIsConnected && customerPrinter?.macAddress == profile?.printerMac,
                    onEnabledChange = { enabled = it },
                    onAutoPrintChange = { autoPrint = it },
                    onPaperSizeChange = { paper58 = it },
                    onIncludeLogoChange = { includeLogo = it },
                    helperText = null,
                    onSelectPrinter = {
                        pendingRole = PrinterRole.CUSTOMER
                        if (!viewModel.hasBluetoothPermissions(context)) {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                            } else {
                                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            permissionLauncher.launch(perms)
                        } else if (!viewModel.isBluetoothEnabled(context)) {
                            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } else {
                            viewModel.startBluetoothScan(context)
                            showBtSheet = true
                        }
                    },
                    onTestPrint = { viewModel.testPrint(PrinterRole.CUSTOMER) }
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                PrinterTargetCard(
                    title = "Kitchen Ticket Printer",
                    printerName = kitchenPrinter?.name ?: "No Printer",
                    macAddress = kitchenPrinter?.macAddress,
                    enabled = kitchenEnabled,
                    autoPrint = true,
                    showAutoPrintToggle = false,
                    paper58 = kitchenPaper58,
                    includeLogo = false,
                    showLogoToggle = false,
                    isConnected = false,
                    onEnabledChange = { kitchenEnabled = it },
                    onAutoPrintChange = {},
                    onPaperSizeChange = { kitchenPaper58 = it },
                    onIncludeLogoChange = {},
                    helperText = "Used only for first-time billing. Reprint stays customer receipt only.",
                    onSelectPrinter = {
                        pendingRole = PrinterRole.KITCHEN
                        if (!viewModel.hasBluetoothPermissions(context)) {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                            } else {
                                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                            permissionLauncher.launch(perms)
                        } else if (!viewModel.isBluetoothEnabled(context)) {
                            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } else {
                            viewModel.startBluetoothScan(context)
                            showBtSheet = true
                        }
                    },
                    onTestPrint = { viewModel.testPrint(PrinterRole.KITCHEN) }
                )
            }
            ConfigCard {
                Text("Print Options", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                PrinterOptionRow("Mask Customer Phone", maskPhone) { maskPhone = it }
            }
            Spacer(modifier = Modifier.height(spacing.large))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        profile?.copy(
                            printerEnabled = enabled,
                            paperSize = if (paper58) "58mm" else "80mm",
                            autoPrintOnSuccess = autoPrint,
                            includeLogoInPrint = includeLogo,
                            maskCustomerPhone = maskPhone,
                            isSynced = false,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                        customerPrinter?.let {
                            viewModel.updatePrinterProfile(
                                role = PrinterRole.CUSTOMER,
                                enabled = enabled,
                                autoPrint = autoPrint,
                                paperSize = if (paper58) "58mm" else "80mm",
                                includeLogo = includeLogo
                            )
                        }
                        kitchenPrinter?.let {
                            viewModel.updatePrinterProfile(
                                role = PrinterRole.KITCHEN,
                                enabled = kitchenEnabled,
                                autoPrint = true,
                                paperSize = if (kitchenPaper58) "58mm" else "80mm",
                                includeLogo = false
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green800),
                    shape = RoundedCornerShape(28.dp)
                ) { Text("Save", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    border = BorderStroke(1.dp, PrimaryGold.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Back", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = spacing.medium, vertical = spacing.small)
        )
    }

    if (showBtSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.stopBluetoothScan()
                showBtSheet = false
            },
            sheetState = sheetState,
            containerColor = DarkBrownSheet
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(spacing.large)
                    .padding(bottom = spacing.large)
            ) {
                Text(
                    "Select ${if (pendingRole == PrinterRole.CUSTOMER) "Customer" else "Kitchen"} Printer",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (btIsScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
                        color = PrimaryGold
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), contentPadding = PaddingValues(bottom = spacing.small)) {
                    items(btDevices) { device ->
                        val selectedMac = if (pendingRole == PrinterRole.CUSTOMER) customerPrinter?.macAddress else kitchenPrinter?.macAddress
                        DeviceRow(
                            device = device,
                            isConnecting = btIsConnecting,
                            isSelected = device.address == selectedMac,
                            isConnected = device.address == selectedMac && btIsConnected
                        ) {
                            viewModel.connectToPrinter(
                                context = context,
                                device = device,
                                role = pendingRole,
                                paperSize = if (pendingRole == PrinterRole.CUSTOMER) {
                                    if (paper58) "58mm" else "80mm"
                                } else {
                                    if (kitchenPaper58) "58mm" else "80mm"
                                },
                                includeLogo = pendingRole == PrinterRole.CUSTOMER && includeLogo
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterTargetCard(
    title: String,
    printerName: String,
    macAddress: String?,
    enabled: Boolean,
    autoPrint: Boolean,
    showAutoPrintToggle: Boolean,
    paper58: Boolean,
    includeLogo: Boolean,
    showLogoToggle: Boolean,
    isConnected: Boolean,
    helperText: String?,
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
            .border(1.dp, BorderGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(spacing.medium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = onEnabledChange, colors = SwitchDefaults.colors(checkedTrackColor = VegGreen))
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(printerName, color = TextLight, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(spacing.small))
                    Box(modifier = Modifier.size(8.dp).background(if (isConnected) SuccessGreen else DangerRed, CircleShape))
                }
                Text("MAC: ${macAddress ?: "---"}", color = TextGold, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = if (macAddress.isNullOrBlank()) "Status: No printer selected" else if (isConnected) "Status: Connected" else "Status: Ready to test",
                    color = if (macAddress.isNullOrBlank()) TextGold.copy(alpha = 0.7f) else if (isConnected) SuccessGreen else TextGold,
                    style = MaterialTheme.typography.labelSmall
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = paper58, onClick = { onPaperSizeChange(true) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                    Text("58mm", color = TextGold)
                    Spacer(modifier = Modifier.width(spacing.large))
                    RadioButton(selected = !paper58, onClick = { onPaperSizeChange(false) }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
                    Text("80mm", color = TextGold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Button(onClick = onSelectPrinter, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Brown500)) {
                        Text("Select Printer")
                    }
                    Button(
                        onClick = onTestPrint,
                        enabled = !macAddress.isNullOrBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, disabledContainerColor = PrimaryGold.copy(alpha = 0.35f))
                    ) {
                        Text("Test Printer", color = DarkBrown1)
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
        shape = RoundedCornerShape(8.dp)
    ) {
        if (border != null) {
            Modifier.border(border, RoundedCornerShape(8.dp))
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
private fun PrinterOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = PrimaryGold))
        Text(label, color = TextGold, style = MaterialTheme.typography.bodyMedium)
    }
}
