package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSnackbarHost
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.theme.kbTextSecondary
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
    val connectedPrinterMac by viewModel.connectedPrinterMac.collectAsStateWithLifecycle()
    val printerStatusRoles by viewModel.printerStatusRoles.collectAsStateWithLifecycle()
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

    val openBtSheet: () -> Unit = {
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
    }

    val isAnyBtConnected = printerStatusRoles.contains(PrinterRole.CUSTOMER.name) ||
        printerStatusRoles.contains(PrinterRole.KITCHEN.name)
    val connectedBtName = when {
        printerStatusRoles.contains(PrinterRole.CUSTOMER.name) -> customerPrinter?.name
        printerStatusRoles.contains(PrinterRole.KITCHEN.name) -> kitchenPrinter?.name
        else -> null
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(spacing.medium)
        ) {
            // ── Stitch: Printer Setup Card ──
            ConfigCard {
                // Header
                Text(
                    text = "Printer Setup",
                    color = MaterialTheme.kbTextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(spacing.smallMedium))

                // ── Bluetooth content ──
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
                    isConnected = printerStatusRoles.contains(PrinterRole.CUSTOMER.name),
                    onEnabledChange = { enabled = it },
                    onAutoPrintChange = { autoPrint = it },
                    onPaperSizeChange = { paper58 = it },
                    onIncludeLogoChange = { includeLogo = it },
                    helperText = null,
                    onSelectPrinter = {
                        pendingRole = PrinterRole.CUSTOMER
                        openBtSheet()
                    },
                    onTestPrint = { viewModel.testPrint(PrinterRole.CUSTOMER) }
                )
                Spacer(modifier = Modifier.height(spacing.smallMedium))
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
                    isConnected = printerStatusRoles.contains(PrinterRole.KITCHEN.name),
                    onEnabledChange = { kitchenEnabled = it },
                    onAutoPrintChange = {},
                    onPaperSizeChange = { kitchenPaper58 = it },
                    onIncludeLogoChange = {},
                    helperText = null,
                    onSelectPrinter = {
                        pendingRole = PrinterRole.KITCHEN
                        openBtSheet()
                    },
                    onTestPrint = { viewModel.testPrint(PrinterRole.KITCHEN) }
                )

                // Bluetooth connected state
                if (isAnyBtConnected) {
                    Spacer(modifier = Modifier.height(spacing.small))
                    ConnectedPrinterBanner(
                        printerName = connectedBtName ?: "Bluetooth Printer",
                        onDisconnect = {
                            // Business logic: reset connection state
                            // (actual disconnect handled by system; we just clear UI state)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(spacing.smallMedium))

                // ── Print Test Page button (Stitch: at bottom of card) ──
                Button(
                    onClick = {
                        if (printerStatusRoles.contains(PrinterRole.CUSTOMER.name)) {
                            viewModel.testPrint(PrinterRole.CUSTOMER)
                        } else if (printerStatusRoles.contains(PrinterRole.KITCHEN.name)) {
                            viewModel.testPrint(PrinterRole.KITCHEN)
                        }
                    },
                    enabled = isAnyBtConnected,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KbBrandSaffron,
                        disabledContainerColor = KbBrandSaffron.copy(alpha = KbOpacity.Disabled)
                    ),
                    shape = KbShape.Medium
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text("Print Test Page", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(spacing.smallMedium))

            // ── Print Options (keep existing) ──
            ConfigCard {
                Text(
                    "Print Options",
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
                PrinterOptionRow("Mask Customer Phone", maskPhone) { maskPhone = it }
            }

            Spacer(modifier = Modifier.height(spacing.smallMedium))

            // ── Save / Back buttons (keep existing) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
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
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KbSuccess),
                    shape = KbShape.Medium
                ) {
                    Text(
                        "Save",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    border = BorderStroke(1.dp, MaterialTheme.kbSecondary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbSecondary),
                    shape = KbShape.Medium
                ) {
                    Text("Back", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        KhanaBookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = spacing.medium, vertical = spacing.small)
        )
    }

    // ── Bluetooth bottom sheet (keep existing) ──
    if (showBtSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.stopBluetoothScan()
                showBtSheet = false
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.kbBgSecondary
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
                    color = MaterialTheme.kbSecondary,
                    style = MaterialTheme.typography.headlineSmall
                )
                if (btIsScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
                        color = MaterialTheme.kbSecondary
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp), contentPadding = PaddingValues(bottom = spacing.small)) {
                    items(btDevices) { device ->
                        val selectedMac = if (pendingRole == PrinterRole.CUSTOMER) customerPrinter?.macAddress else kitchenPrinter?.macAddress
                        DeviceRow(
                            device = device,
                            isConnecting = btIsConnecting,
                            isSelected = device.address == selectedMac,
                            isConnected = connectedPrinterMac == device.address
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
private fun ConnectedPrinterBanner(
    printerName: String,
    onDisconnect: () -> Unit
) {
    val spacing = KhanaBookTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                KbSuccess.copy(alpha = KbOpacity.StatusBg),
                KbShape.Small
            )
            .border(
                1.dp,
                KbSuccess.copy(alpha = KbOpacity.StatusBorder),
                KbShape.Small
            )
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = KbSuccess,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(spacing.small))
            Text(
                printerName,
                color = KbSuccess,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        TextButton(onClick = onDisconnect) {
            Text("Disconnect", color = MaterialTheme.kbTextSecondary)
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
            .border(1.dp, MaterialTheme.kbOutlineSubtle.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(spacing.smallMedium)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = MaterialTheme.kbSecondary, style = MaterialTheme.typography.titleMedium)
                KhanaBookSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(printerName, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(spacing.small))
                    Box(modifier = Modifier.size(8.dp).background(if (isConnected) KbSuccess else KbError, CircleShape))
                }
                Text("MAC: ${macAddress ?: "---"}", color = MaterialTheme.kbPrimary, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = if (macAddress.isNullOrBlank()) "Status: No printer selected" else if (isConnected) "Status: Connected" else "Status: Ready to test",
                    color = if (macAddress.isNullOrBlank()) MaterialTheme.kbPrimary.copy(alpha = 0.7f) else if (isConnected) KbSuccess else MaterialTheme.kbPrimary,
                    style = MaterialTheme.typography.labelSmall
                )
                helperText?.let {
                    Text(it, color = MaterialTheme.kbPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
                if (showAutoPrintToggle) {
                    PrinterOptionRow("Auto Print", autoPrint) { onAutoPrintChange(it) }
                }
                if (showLogoToggle) {
                    PrinterOptionRow("Include Logo", includeLogo) { onIncludeLogoChange(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = paper58, onClick = { onPaperSizeChange(true) }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.kbPrimary))
                    Text("58mm", color = MaterialTheme.kbPrimary)
                    Spacer(modifier = Modifier.width(spacing.large))
                    RadioButton(selected = !paper58, onClick = { onPaperSizeChange(false) }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.kbPrimary))
                    Text("80mm", color = MaterialTheme.kbPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Button(onClick = onSelectPrinter, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbOutlineSubtle)) {
                        Text("Select Printer")
                    }
                    Button(
                        onClick = onTestPrint,
                        enabled = !macAddress.isNullOrBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron, disabledContainerColor = KbBrandSaffron.copy(alpha = 0.35f))
                    ) {
                        Text("Test Printer", color = Color.White)
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
    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.kbPrimary) else null
    val backgroundColor = if (isSelected) MaterialTheme.kbBgSecondary else MaterialTheme.kbOutlineSubtle

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
                tint = if (isSelected) MaterialTheme.kbPrimary else MaterialTheme.kbPrimary,
                modifier = Modifier.size(iconSize.medium)
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium))
                Text(device.address, color = if (isSelected) MaterialTheme.kbPrimary.copy(alpha = 0.7f) else MaterialTheme.kbPrimary, style = MaterialTheme.typography.labelSmall)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.kbSecondary, strokeWidth = 2.dp)
            } else if (isConnected) {
                Box(modifier = Modifier.size(8.dp).background(KbSuccess, CircleShape))
            }
        }
    }
}

@Composable
private fun PrinterOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.kbPrimary))
        Text(label, color = MaterialTheme.kbPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
