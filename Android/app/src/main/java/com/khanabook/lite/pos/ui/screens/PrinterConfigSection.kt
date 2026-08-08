@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.ui.screens

import com.khanabook.lite.pos.ui.theme.KhanaRadii

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.khanabook.lite.pos.domain.model.PrinterConnectionType
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.domain.model.connectionTypeValue
import com.khanabook.lite.pos.ui.designsystem.KhanaButtonRow
import com.khanabook.lite.pos.ui.designsystem.KhanaBookCard
import com.khanabook.lite.pos.ui.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.ui.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.ui.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.ui.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.ui.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.ui.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.ui.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.ui.designsystem.KhanaToast
import com.khanabook.lite.pos.ui.designsystem.ToastKind
import com.khanabook.lite.pos.ui.theme.BorderGold
import com.khanabook.lite.pos.ui.theme.Brown500
import com.khanabook.lite.pos.ui.theme.DarkBrown1
import com.khanabook.lite.pos.ui.theme.CardBG
import com.khanabook.lite.pos.ui.theme.DarkBrownSheet
import com.khanabook.lite.pos.ui.theme.DangerRed
import com.khanabook.lite.pos.ui.theme.KhanaBookTheme
import com.khanabook.lite.pos.ui.theme.PrimaryGold
import com.khanabook.lite.pos.ui.theme.SuccessGreen
import com.khanabook.lite.pos.ui.theme.TextGold
import com.khanabook.lite.pos.ui.theme.TextLight
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.ui.viewmodel.PrinterUiEvent
import com.khanabook.lite.pos.ui.screens.printerconfig.*

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    var enabled by remember(customerPrinter?.id, customerPrinter?.enabled, profile?.printerEnabled) {
        mutableStateOf(customerPrinter?.enabled ?: profile?.printerEnabled ?: false)
    }
    var paper58 by remember(customerPrinter?.id, customerPrinter?.paperSize, profile?.paperSize) {
        mutableStateOf((customerPrinter?.paperSize ?: profile?.paperSize ?: "58mm") == "58mm")
    }
    var autoPrint by remember(customerPrinter?.id, customerPrinter?.autoPrint, profile?.autoPrintOnSuccess) {
        mutableStateOf(customerPrinter?.autoPrint ?: profile?.autoPrintOnSuccess ?: false)
    }
    var includeLogo by remember(customerPrinter?.id, customerPrinter?.includeLogo, profile?.includeLogoInPrint) {
        mutableStateOf(customerPrinter?.includeLogo ?: profile?.includeLogoInPrint ?: true)
    }
    var maskPhone by remember { mutableStateOf(profile?.maskCustomerPhone ?: true) }
    var kitchenEnabled by remember(kitchenPrinter?.id, kitchenPrinter?.enabled) { mutableStateOf(kitchenPrinter?.enabled ?: false) }
    var kitchenPaper58 by remember(kitchenPrinter?.id, kitchenPrinter?.paperSize) { mutableStateOf((kitchenPrinter?.paperSize ?: "58mm") == "58mm") }
    val context = LocalContext.current
    var isBtActive by remember { mutableStateOf(viewModel.isBluetoothEnabled(context)) }
    var pendingRole by remember { mutableStateOf(PrinterRole.CUSTOMER) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var wifiPrinterName by remember { mutableStateOf("Customer Receipt Wi-Fi Printer") }
    var wifiHost by remember { mutableStateOf("") }
    var wifiPort by remember { mutableStateOf("9100") }

    val btDevices by viewModel.btDevices.collectAsStateWithLifecycle()
    val btIsScanning by viewModel.btIsScanning.collectAsStateWithLifecycle()
    val connectedPrinterMac by viewModel.connectedPrinterMac.collectAsStateWithLifecycle()
    val printerStatusRoles by viewModel.printerStatusRoles.collectAsStateWithLifecycle()
    val btIsConnecting by viewModel.btIsConnecting.collectAsStateWithLifecycle()
    var showBtSheet by remember { mutableStateOf(false) }
    var snackbarMessageRes by remember { mutableStateOf<Int?>(null) }
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

    LaunchedEffect(viewModel) {
        viewModel.printerEvents.collect { event ->
            val (message, kind) = when (event) {
                PrinterUiEvent.Connected -> {
                    showBtSheet = false
                    context.getString(R.string.toast_printer_connected) to ToastKind.Success
                }
                PrinterUiEvent.ConnectionFailed ->
                    context.getString(R.string.toast_printer_connect_failed) to ToastKind.Error
                PrinterUiEvent.WifiSaved ->
                    "Wi-Fi printer saved" to ToastKind.Success
                PrinterUiEvent.WifiSaveFailed ->
                    "Couldn't save Wi-Fi printer. Please try again." to ToastKind.Error
                PrinterUiEvent.TestPrintSent ->
                    "Test print sent" to ToastKind.Success
                PrinterUiEvent.TestPrintFailed ->
                    "Test print failed. Check the printer connection." to ToastKind.Error
                PrinterUiEvent.NotConfigured ->
                    "Configure this printer before testing" to ToastKind.Warning
                PrinterUiEvent.InvalidWifiAddress ->
                    "Enter a valid printer address and port" to ToastKind.Warning
            }
            KhanaToast.show(message, kind)
        }
    }

    LaunchedEffect(snackbarMessageRes) {
        snackbarMessageRes?.let {
            KhanaToast.show(
                message = context.getString(it),
                kind = when (it) {
                    R.string.toast_printer_connected -> ToastKind.Success
                    R.string.toast_printer_connect_failed -> ToastKind.Error
                    else -> ToastKind.Warning
                }
            )
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
                    Text("Receipt and Kitchen Printers", color = TextGold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(spacing.medium))
                PrinterTargetCard(
                    title = "Customer Receipt Printer",
                    printerName = customerPrinter?.name ?: "No Printer",
                    connectionDescription = customerPrinter?.let { printer ->
                        when (printer.connectionTypeValue()) {
                            PrinterConnectionType.BLUETOOTH -> printer.macAddress
                                .takeIf { it.isNotBlank() }
                                ?.let { "Bluetooth · $it" }
                            PrinterConnectionType.WIFI -> printer.host
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "Wi-Fi · $it:${printer.port}" }
                        }
                    },
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
                    onConfigureWifi = {
                        pendingRole = PrinterRole.CUSTOMER
                        wifiPrinterName = customerPrinter?.name ?: "Customer Receipt Wi-Fi Printer"
                        wifiHost = customerPrinter
                            ?.takeIf { it.connectionTypeValue() == PrinterConnectionType.WIFI }
                            ?.host
                            .orEmpty()
                        wifiPort = customerPrinter
                            ?.takeIf { it.connectionTypeValue() == PrinterConnectionType.WIFI }
                            ?.port
                            ?.toString()
                            ?: "9100"
                        showWifiDialog = true
                    },
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
                    connectionDescription = kitchenPrinter?.let { printer ->
                        when (printer.connectionTypeValue()) {
                            PrinterConnectionType.BLUETOOTH -> printer.macAddress
                                .takeIf { it.isNotBlank() }
                                ?.let { "Bluetooth · $it" }
                            PrinterConnectionType.WIFI -> printer.host
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "Wi-Fi · $it:${printer.port}" }
                        }
                    },
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
                    helperText = "Receives new and updated KOT items. Receipt printing remains on the customer printer.",
                    onConfigureWifi = {
                        pendingRole = PrinterRole.KITCHEN
                        wifiPrinterName = kitchenPrinter?.name ?: "Kitchen Wi-Fi Printer"
                        wifiHost = kitchenPrinter
                            ?.takeIf { it.connectionTypeValue() == PrinterConnectionType.WIFI }
                            ?.host
                            .orEmpty()
                        wifiPort = kitchenPrinter
                            ?.takeIf { it.connectionTypeValue() == PrinterConnectionType.WIFI }
                            ?.port
                            ?.toString()
                            ?: "9100"
                        showWifiDialog = true
                    },
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
                Spacer(modifier = Modifier.height(spacing.extraLarge))
                ConfigActionButtons(
                    onSave = {
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
                    onBack = onBack
                )
            }
        }
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

    if (showWifiDialog) {
        val parsedPort = wifiPort.toIntOrNull()
        val isValid = wifiHost.isNotBlank() && parsedPort != null && parsedPort in 1..65535
        val roleLabel = if (pendingRole == PrinterRole.CUSTOMER) "Customer Receipt" else "Kitchen Ticket"
        KhanaBookDialog(
            onDismissRequest = { showWifiDialog = false },
            title = "$roleLabel Wi-Fi Printer",
            message = "Enter the printer's local-network address and raw TCP port. Most thermal printers use port 9100.",
            content = {
                KhanaBookInputField(
                    value = wifiPrinterName,
                    onValueChange = { wifiPrinterName = it },
                    label = "Printer name",
                    modifier = Modifier.fillMaxWidth()
                )
                KhanaBookInputField(
                    value = wifiHost,
                    onValueChange = { wifiHost = it.trim().take(253) },
                    label = "IP address or host",
                    placeholder = "192.168.1.50",
                    modifier = Modifier.fillMaxWidth()
                )
                KhanaBookInputField(
                    value = wifiPort,
                    onValueChange = { value -> wifiPort = value.filter(Char::isDigit).take(5) },
                    label = "Port",
                    modifier = Modifier.fillMaxWidth(),
                    isError = wifiPort.isNotEmpty() && parsedPort !in 1..65535,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            actions = {
                TextButton(onClick = { showWifiDialog = false }) {
                    Text("Cancel", color = TextGold)
                }
                TextButton(
                    enabled = isValid,
                    onClick = {
                        viewModel.saveWifiPrinter(
                            role = pendingRole,
                            name = wifiPrinterName,
                            host = wifiHost,
                            port = parsedPort ?: 9100,
                            autoPrint = pendingRole != PrinterRole.CUSTOMER || autoPrint,
                            paperSize = if (pendingRole == PrinterRole.CUSTOMER) {
                                if (paper58) "58mm" else "80mm"
                            } else {
                                if (kitchenPaper58) "58mm" else "80mm"
                            },
                            includeLogo = pendingRole == PrinterRole.CUSTOMER && includeLogo
                        )
                        showWifiDialog = false
                    }
                ) {
                    Text("Save", color = PrimaryGold)
                }
            }
        )
    }
}
