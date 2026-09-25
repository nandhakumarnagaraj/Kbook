@file:OptIn(ExperimentalMaterial3Api::class)

package com.khanabook.lite.pos.feature.printing.ui
import com.khanabook.lite.pos.feature.settings.ui.ConfigActionButtons
import com.khanabook.lite.pos.feature.settings.ui.ConfigCard
import com.khanabook.lite.pos.feature.printing.ui.*

import com.khanabook.lite.pos.core.theme.KhanaRadii
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.CardBG

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.feature.auth.data.RestaurantProfileEntity
import com.khanabook.lite.pos.feature.printing.domain.PrinterConnectionType
import com.khanabook.lite.pos.feature.printing.domain.PrinterRole
import com.khanabook.lite.pos.feature.printing.domain.connectionTargetKey
import com.khanabook.lite.pos.feature.printing.domain.connectionTypeValue
import com.khanabook.lite.pos.core.designsystem.KhanaButtonRow
import com.khanabook.lite.pos.core.designsystem.KhanaBookCard
import com.khanabook.lite.pos.core.designsystem.KhanaBookDialog
import com.khanabook.lite.pos.core.designsystem.KhanaBookInputField
import com.khanabook.lite.pos.core.designsystem.KhanaBookSwitch
import com.khanabook.lite.pos.core.designsystem.KhanaPrimaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaSecondaryButton
import com.khanabook.lite.pos.core.designsystem.KhanaStatusBadge
import com.khanabook.lite.pos.core.designsystem.KhanaStatusKind
import com.khanabook.lite.pos.core.designsystem.KhanaToast
import com.khanabook.lite.pos.core.designsystem.ToastKind
import com.khanabook.lite.pos.core.theme.BorderGold
import com.khanabook.lite.pos.core.theme.Brown500
import com.khanabook.lite.pos.core.theme.DarkBrown1
import com.khanabook.lite.pos.core.theme.CardBG
import com.khanabook.lite.pos.core.theme.DarkBrownSheet
import com.khanabook.lite.pos.core.theme.DangerRed
import com.khanabook.lite.pos.core.theme.KhanaBookTheme
import com.khanabook.lite.pos.core.theme.PrimaryGold
import com.khanabook.lite.pos.core.theme.SuccessGreen
import com.khanabook.lite.pos.core.theme.TextGold
import com.khanabook.lite.pos.core.theme.TextLight
import com.khanabook.lite.pos.feature.settings.viewmodel.SettingsViewModel
import com.khanabook.lite.pos.feature.settings.viewmodel.PrinterUiEvent

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun PrinterConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    isSaving: Boolean = false
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
    val focusManager = LocalFocusManager.current
    // Re-probe Wi-Fi printers every time this screen is shown so the status dot
    // reflects current reachability rather than a stale result.
    LaunchedEffect(Unit) { viewModel.refreshWifiReachability() }
    var isBtActive by remember { mutableStateOf(viewModel.isBluetoothEnabled(context)) }
    var pendingRole by remember { mutableStateOf(PrinterRole.CUSTOMER) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var wifiPrinterName by remember { mutableStateOf("Customer Receipt Wi-Fi Printer") }
    var wifiHost by remember { mutableStateOf("") }
    var wifiPort by remember { mutableStateOf("9100") }

    val isScanningWifi by viewModel.isScanningWifiPrinters.collectAsStateWithLifecycle()
    val discoveredWifiPrinters by viewModel.discoveredWifiPrinters.collectAsStateWithLifecycle()
    val isSavingWifi by viewModel.isSavingWifiPrinter.collectAsStateWithLifecycle()
    val wifiSubnetPrefix by viewModel.wifiSubnetPrefix.collectAsStateWithLifecycle()
    // Auto subnet mask: prefill the IP field with the device's own subnet prefix
    // the first time the dialog opens, so the user only types the last octet.
    LaunchedEffect(wifiSubnetPrefix, showWifiDialog) {
        val prefix = wifiSubnetPrefix ?: return@LaunchedEffect
        if (showWifiDialog && wifiHost.isBlank()) wifiHost = prefix
    }
    // Android 10: DhcpInfo/LinkProperties can settle AFTER the ViewModel init
    // snapshot (late DHCP lease on boot/roam) — re-detect when the dialog opens
    // instead of showing an empty IP field and making the user type it all.
    LaunchedEffect(showWifiDialog) {
        if (showWifiDialog) viewModel.refreshWifiSubnetPrefix()
    }

    val btDevices by viewModel.btDevices.collectAsStateWithLifecycle()
    val btIsScanning by viewModel.btIsScanning.collectAsStateWithLifecycle()
    val connectedPrinterMac by viewModel.connectedPrinterMac.collectAsStateWithLifecycle()
    val printerStatusRoles by viewModel.printerStatusRoles.collectAsStateWithLifecycle()
    val printerHealthMap by viewModel.printerHealth.collectAsStateWithLifecycle()
    val wifiPrinterNetworkMismatch by viewModel.wifiPrinterNetworkMismatch.collectAsStateWithLifecycle()
    val btIsConnecting by viewModel.btIsConnecting.collectAsStateWithLifecycle()
    var showBtSheet by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showUsbSheet by remember { mutableStateOf(false) }
    var usbDevices by remember { mutableStateOf<List<Triple<String, String, Boolean>>>(emptyList()) }
    val usbScope = rememberCoroutineScope()
    val toastScope = rememberCoroutineScope()
    var snackbarMessageRes by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isBtActive = true
            viewModel.startBluetoothScan(context)
            showBtSheet = true
        }
    }

    // Android 8-11: classic discovery silently returns nothing when the device
    // Location toggle is OFF. Deep-link to system Location Settings and resume
    // the scan automatically when the user comes back with it enabled.
    val locationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            viewModel.isDeviceLocationEnabled() &&
            viewModel.isBluetoothEnabled(context)
        ) {
            viewModel.startBluetoothScan(context)
            showBtSheet = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_CONNECT] == true && perms[Manifest.permission.BLUETOOTH_SCAN] == true
        } else {                        perms[Manifest.permission.BLUETOOTH] == true && perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
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
                    viewModel.refreshWifiReachability()
                    context.getString(R.string.toast_printer_connected) to ToastKind.Success
                }
                PrinterUiEvent.ConnectionFailed ->
                    context.getString(R.string.toast_printer_connect_failed) to ToastKind.Error
                PrinterUiEvent.WifiSaved -> {
                    // Confirm the save without exposing internal write timing.
                    showWifiDialog = false
                    "Wi-Fi printer saved" to ToastKind.Success
                }
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
                PrinterUiEvent.UsbPrinterSaved ->
                    "USB printer saved" to ToastKind.Success
                PrinterUiEvent.UsbPermissionDenied ->
                    "USB permission denied. Allow access to use this printer." to ToastKind.Error
                PrinterUiEvent.NoUsbPrintersFound ->
                    "No USB printers found. Connect one via OTG cable." to ToastKind.Warning
            }
            // Fire-and-forget: showing must not suspend the events collector,
            // or a follow-up event (e.g. Test print) is delayed/dropped for the
            // snackbar's whole 4s lifecycle while this one is on screen.
            toastScope.launch { KhanaToast.show(message, kind) }
        }
    }

    LaunchedEffect(snackbarMessageRes) {
        snackbarMessageRes?.let {
            toastScope.launch {
                KhanaToast.show(
                    message = context.getString(it),
                    kind = when (it) {
                        R.string.toast_printer_connected -> ToastKind.Success
                        R.string.toast_printer_connect_failed -> ToastKind.Error
                        else -> ToastKind.Warning
                    }
                )
            }
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
                            PrinterConnectionType.USB -> printer.macAddress
                                .takeIf { it.startsWith("usb:") }
                                ?.let { "USB · ${it.removePrefix("usb:")}" }
                        }
                    },
enabled = enabled,
                    autoPrint = autoPrint,
                    showAutoPrintToggle = true,
                    paper58 = paper58,
                    includeLogo = includeLogo,
                    showLogoToggle = true,
                    isConnected = printerStatusRoles.contains(PrinterRole.CUSTOMER.name),
                    health = customerPrinter?.let { printerHealthMap[it.connectionTargetKey()] },
                    networkMismatch = customerPrinter?.let {
                        it.connectionTypeValue() == PrinterConnectionType.WIFI &&
                        wifiPrinterNetworkMismatch[it.connectionTargetKey()] == true
                    } ?: false,
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
                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !viewModel.isDeviceLocationEnabled()) {
                            // Pre-12: discovery needs device Location ON — route user
                            // to system settings instead of scanning into an empty list.
                            showLocationDialog = true
                        } else {
                            viewModel.startBluetoothScan(context)
                            showBtSheet = true
                        }
                    },
                    onTestPrint = { viewModel.testPrint(PrinterRole.CUSTOMER) },
                    onSelectUsb = {
                        pendingRole = PrinterRole.CUSTOMER
                        val found = viewModel.listUsbPrinters()
                        if (found.isEmpty()) {
                            usbScope.launch {
                                KhanaToast.show(
                                    "No USB printers found. Connect one via OTG cable.",
                                    ToastKind.Warning
                                )
                            }
                        } else {
                            usbDevices = found
                            showUsbSheet = true
                        }
                    }
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
                            PrinterConnectionType.USB -> printer.macAddress
                                .takeIf { it.startsWith("usb:") }
                                ?.let { "USB · ${it.removePrefix("usb:")}" }
                        }
                    },
                    enabled = kitchenEnabled,
                    autoPrint = true,
                    showAutoPrintToggle = false,
                    paper58 = kitchenPaper58,
                    includeLogo = false,
                    showLogoToggle = false,
                    isConnected = printerStatusRoles.contains(PrinterRole.KITCHEN.name),
                    health = kitchenPrinter?.let { printerHealthMap[it.connectionTargetKey()] },
                    networkMismatch = kitchenPrinter?.let {
                        it.connectionTypeValue() == PrinterConnectionType.WIFI &&
                        wifiPrinterNetworkMismatch[it.connectionTargetKey()] == true
                    } ?: false,
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
                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !viewModel.isDeviceLocationEnabled()) {
                            showLocationDialog = true
                        } else {
                            viewModel.startBluetoothScan(context)
                            showBtSheet = true
                        }
                    },
                    onTestPrint = { viewModel.testPrint(PrinterRole.KITCHEN) },
                    onSelectUsb = {
                        pendingRole = PrinterRole.KITCHEN
                        val found = viewModel.listUsbPrinters()
                        if (found.isEmpty()) {
                            usbScope.launch {
                                KhanaToast.show(
                                    "No USB printers found. Connect one via OTG cable.",
                                    ToastKind.Warning
                                )
                            }
                        } else {
                            usbDevices = found
                            showUsbSheet = true
                        }
                    }
                )
            }
            ConfigCard {
                Text("Print Options", color = PrimaryGold, style = MaterialTheme.typography.titleMedium)
                PrinterOptionRow("Mask Customer Phone", maskPhone) { maskPhone = it }
                Spacer(modifier = Modifier.height(spacing.extraLarge))
                ConfigActionButtons(
                    // isSaving disables the button while the settings save is in flight;
                    // profile != null prevents the silent no-op save (profile?.copy below)
                    // when the restaurant profile hasn't loaded yet — that no-op was the
                    // "first tap does nothing" on this screen.
                    isSaving = isSaving,
                    saveEnabled = profile != null,
                    onSave = {
                        focusManager.clearFocus()
                        profile?.copy(
                            printerEnabled = enabled,
                            paperSize = if (paper58) "58mm" else "80mm",
                            autoPrintOnSuccess = autoPrint,
                            includeLogoInPrint = includeLogo,
                            maskCustomerPhone = maskPhone,
                            isSynced = true,
                            updatedAt = System.currentTimeMillis()
                        )?.let { onSave(it) }
                        viewModel.updatePrinterProfile(
                            role = PrinterRole.CUSTOMER,
                            enabled = enabled,
                            autoPrint = autoPrint,
                            paperSize = if (paper58) "58mm" else "80mm",
                            includeLogo = includeLogo
                        )
                        viewModel.updatePrinterProfile(
                            role = PrinterRole.KITCHEN,
                            enabled = kitchenEnabled,
                            autoPrint = true,
                            paperSize = if (kitchenPaper58) "58mm" else "80mm",
                            includeLogo = false
                        )
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

    // Live USB list while the picker is open: attaching the printer AFTER opening
    // the sheet used to leave it empty until the sheet was closed and reopened.
    if (showUsbSheet) {
        DisposableEffect(Unit) {
            val usbListReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED,
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> usbDevices = viewModel.listUsbPrinters()
                    }
                }
            }
            val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbListReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(usbListReceiver, filter)
            }
            onDispose {
                runCatching { context.unregisterReceiver(usbListReceiver) }
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showUsbSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                    "Select ${if (pendingRole == PrinterRole.CUSTOMER) "Customer" else "Kitchen"} USB Printer",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    "Connect the printer with an OTG cable, then pick it below.",
                    color = TextGold.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                usbDevices.forEach { (key, label, hasPermission) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (hasPermission) {
                                    usbScope.launch {
                                        val currentKey = viewModel.currentUsbPrinterKey(key) ?: key
                                        viewModel.saveUsbPrinter(
                                            role = pendingRole,
                                            deviceKey = currentKey,
                                            label = label,
                                            paperSize = if (pendingRole == PrinterRole.CUSTOMER) {
                                                if (paper58) "58mm" else "80mm"
                                            } else {
                                                if (kitchenPaper58) "58mm" else "80mm"
                                            }
                                        )
                                        showUsbSheet = false
                                    }
                                } else {
                                    usbScope.launch {
                                        val granted = viewModel.requestUsbPermission(key)
                                        // Re-snapshot immediately after the grant so rows never
                                        // show a stale "Tap to allow" if the save fails or the
                                        // sheet stays open. After the grant the device key may
                                        // change (serial becomes readable) — recompute it.
                                        usbDevices = viewModel.listUsbPrinters()
                                        if (granted) {
                                            val currentKey = viewModel.currentUsbPrinterKey(key) ?: key
                                            viewModel.saveUsbPrinter(
                                                role = pendingRole,
                                                deviceKey = currentKey,
                                                label = label,
                                                paperSize = if (pendingRole == PrinterRole.CUSTOMER) {
                                                    if (paper58) "58mm" else "80mm"
                                                } else {
                                                    if (kitchenPaper58) "58mm" else "80mm"
                                                }
                                            )
                                            showUsbSheet = false
                                        } else {
                                            KhanaToast.show(
                                                "USB permission denied. Allow access to use this printer.",
                                                ToastKind.Error
                                            )
                                        }
                                    }
                                }
                            }
                            .padding(vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Usb,
                            contentDescription = null,
                            tint = if (hasPermission) SuccessGreen else TextGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.medium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = TextLight, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                key.removePrefix("usb:"),
                                color = TextGold.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            if (hasPermission) "Ready" else "Tap to allow",
                            color = if (hasPermission) SuccessGreen else PrimaryGold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }

    if (showLocationDialog) {
        KhanaBookDialog(
            onDismissRequest = { showLocationDialog = false },
            title = "Turn on Location",
            message = "On this Android version, Location must be switched on to find nearby Bluetooth printers. KhanaBook never uses your location — this is an Android requirement for device discovery.",
            actions = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Not now", color = TextGold)
                }
                TextButton(onClick = {
                    showLocationDialog = false
                    runCatching {
                        locationSettingsLauncher.launch(
                            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        )
                    }
                }) {
                    Text("Open Settings", color = PrimaryGold)
                }
            }
        )
    }

    if (showWifiDialog) {
        val parsedPort = wifiPort.toIntOrNull()
        // A subnet-prefix prefill ("192.168.1.") or partial IP must not enable Save —
        // only a complete, well-formed IPv4 address does.
        val isValidHost = isValidIpv4(wifiHost.trim())
        val isValid = isValidHost && parsedPort != null && parsedPort in 1..65535
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

                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.scanForWifiPrinters()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.small),
                    enabled = !isScanningWifi,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold)
                ) {
                    if (isScanningWifi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = PrimaryGold,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Scanning local network...", color = PrimaryGold)
                    } else {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text("Auto-Find Wi-Fi Printers", color = PrimaryGold)
                    }
                }

                if (discoveredWifiPrinters.isNotEmpty()) {
                    Text(
                        "Found ${discoveredWifiPrinters.size} Printer(s) (Tap to select):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGold,
                        modifier = Modifier.padding(bottom = spacing.extraSmall)
                    )
                    discoveredWifiPrinters.forEach { printer ->
                        val isSelected = wifiHost == printer.ip && (wifiPort == printer.port.toString() || wifiPort.isEmpty())
                        Surface(
                            shape = KhanaRadii.md,
                            color = if (isSelected) PrimaryGold.copy(alpha = 0.2f) else CardBG,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryGold else BorderGold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    focusManager.clearFocus()
                                    wifiHost = printer.ip
                                    wifiPort = printer.port.toString()
                                    wifiPrinterName = printer.name
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Print,
                                    contentDescription = null,
                                    tint = if (isSelected) PrimaryGold else TextGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(spacing.small))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        printer.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) PrimaryGold else Color.White
                                    )
                                    Text(
                                        "${printer.ip}:${printer.port}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGold.copy(alpha = 0.7f)
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = PrimaryGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(spacing.small))
                }

                KhanaBookInputField(
                    value = wifiHost,
                    onValueChange = { wifiHost = it.trim().take(253) },
                    label = "IP address or host",
                    placeholder = "192.168.1.50",
                    isError = wifiHost.isNotBlank() && !isValidHost,
                    supportingText = if (wifiHost.isNotBlank() && !isValidHost) {
                        { Text("Complete the address — type the last number after the dot", color = TextGold.copy(alpha = 0.7f)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
                TextButton(onClick = {
                    focusManager.clearFocus()
                    showWifiDialog = false
                }) {
                    Text("Cancel", color = TextGold)
                }
                TextButton(
                    enabled = isValid && !isSavingWifi,
                    onClick = {
                        focusManager.clearFocus()
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
                    if (isSavingWifi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = PrimaryGold,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                        Text("Saving...", color = PrimaryGold)
                    } else {
                        Text("Save", color = PrimaryGold)
                    }
                }
            }
        )
    }
}

/** Accepts only a complete dotted-quad IPv4 (each octet 0-255). A bare subnet
 *  prefix like "192.168.1." from the auto-prefill does NOT pass — the user must
 *  type the final octet before saving. */
private fun isValidIpv4(host: String): Boolean {
    if (host.isEmpty() || host.endsWith(".")) return false
    val octets = host.split(".")
    if (octets.size != 4) return false
    return octets.all { octet ->
        octet.isNotEmpty() && octet.length <= 3 && octet.all(Char::isDigit) && octet.toInt() in 0..255
    }
}
