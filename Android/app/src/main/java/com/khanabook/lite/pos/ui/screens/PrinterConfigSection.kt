package com.khanabook.lite.pos.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.data.local.entity.PrinterProfileEntity
import com.khanabook.lite.pos.domain.model.PrinterRole
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("MissingPermission")
@Composable
fun PrinterConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val toastScope = rememberCoroutineScope()

    // Mode selection and drill down states
    var configMode by remember { mutableStateOf<String?>(null) } // null = ModeSelection, "manual" = drill down
    var pendingAction by remember { mutableStateOf<String?>(null) } // "add", "view", "edit"
    var activeCategory by remember { mutableStateOf<String?>(null) } // null = Category List, "customer", "kitchen", "prefs"

    val customerPrinter by viewModel.customerPrinter.collectAsStateWithLifecycle()
    val kitchenPrinter by viewModel.kitchenPrinter.collectAsStateWithLifecycle()
    var enabled by remember(profile?.printerEnabled) { mutableStateOf(profile?.printerEnabled ?: false) }
    var paper58 by remember(profile?.paperSize) { mutableStateOf((profile?.paperSize ?: "58mm") == "58mm") }
    var autoPrint by remember(profile?.autoPrintOnSuccess) { mutableStateOf(profile?.autoPrintOnSuccess ?: false) }
    var includeLogo by remember(profile?.includeLogoInPrint) { mutableStateOf(profile?.includeLogoInPrint ?: true) }
    var maskPhone by remember(profile?.maskCustomerPhone) { mutableStateOf(profile?.maskCustomerPhone ?: true) }
    var kitchenEnabled by remember(kitchenPrinter?.id, kitchenPrinter?.enabled) { mutableStateOf(kitchenPrinter?.enabled ?: false) }
    var kitchenPaper58 by remember(kitchenPrinter?.id, kitchenPrinter?.paperSize) { mutableStateOf((kitchenPrinter?.paperSize ?: "58mm") == "58mm") }

    var isBtActive by remember { mutableStateOf(viewModel.isBluetoothEnabled(context)) }
    var pendingRole by remember { mutableStateOf(PrinterRole.CUSTOMER) }

    val btDevices by viewModel.btDevices.collectAsStateWithLifecycle()
    val btIsScanning by viewModel.btIsScanning.collectAsStateWithLifecycle()
    val connectedPrinterMac by viewModel.connectedPrinterMac.collectAsStateWithLifecycle()
    val printerStatusRoles by viewModel.printerStatusRoles.collectAsStateWithLifecycle()
    val btIsConnecting by viewModel.btIsConnecting.collectAsStateWithLifecycle()
    val btConnectResult by viewModel.btConnectResult.collectAsStateWithLifecycle()

    var showScanDialog by remember { mutableStateOf(false) }
    var showConfigureDetailsDialog by remember { mutableStateOf(false) }
    var showPrefsDialog by remember { mutableStateOf(false) }
    var selectedDeviceForConfig by remember { mutableStateOf<BluetoothDevice?>(null) }

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isBtActive = true
            viewModel.startBluetoothScan(context)
            showScanDialog = true
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
                showScanDialog = true
            }
        } else {
            toastScope.launch { KhanaToast.show("Bluetooth permissions required to scan", ToastKind.Error) }
        }
    }

    val openPrinterScan: () -> Unit = {
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
            showScanDialog = true
        }
    }

    LaunchedEffect(btConnectResult) {
        btConnectResult?.let {
            if (it) {
                toastScope.launch { KhanaToast.show("Printer connected successfully!", ToastKind.Success) }
                showScanDialog = false
                showConfigureDetailsDialog = true
            } else {
                toastScope.launch { KhanaToast.show("Failed to connect printer", ToastKind.Error) }
            }
            viewModel.clearBtConnectResult()
        }
    }

    // Intercept back key
    BackHandler {
        when {
            activeCategory != null -> activeCategory = null
            configMode != null -> {
                configMode = null
                pendingAction = null
            }
            else -> onBack()
        }
    }

    // Handle "add" action trigger
    LaunchedEffect(pendingAction) {
        if (pendingAction == "add") {
            pendingRole = PrinterRole.CUSTOMER
            openPrinterScan()
            pendingAction = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Sub-header with back navigation and title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.kbHeaderGradient)
                    .statusBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            when {
                                activeCategory != null -> activeCategory = null
                                configMode != null -> {
                                    configMode = null
                                    pendingAction = null
                                }
                                else -> onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = when {
                            activeCategory == "customer" -> "Customer Receipt Printer"
                            activeCategory == "kitchen" -> "Kitchen Printer"
                            activeCategory == "prefs" -> "Print Settings"
                            configMode == "manual" -> "Printers Configuration"
                            else -> "Printer Settings"
                        },
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    
                    // Add Button when in Level 1 or 2 manual mode
                    if (configMode == "manual") {
                        IconButton(
                            onClick = {
                                if (activeCategory == "prefs") {
                                    showPrefsDialog = true
                                } else {
                                    pendingRole = if (activeCategory == "kitchen") PrinterRole.KITCHEN else PrinterRole.CUSTOMER
                                    openPrinterScan()
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }

            if (configMode == null) {
                // SCREEN 1: MODE SELECTION VIEW
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    // Dashboard Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        KhanaBookGlassCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Customer Printer", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = customerPrinter?.name?.take(8) ?: "Not Configured",
                                    color = if (customerPrinter != null) KbBrandSaffron else MaterialTheme.kbTextSecondary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        KhanaBookGlassCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Kitchen Printer", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = kitchenPrinter?.name?.take(8) ?: "Not Configured",
                                    color = if (kitchenPrinter != null) KbBrandSaffron else MaterialTheme.kbTextSecondary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.small))
                    Text("Select Printer Setup Flow", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    // Manual Config Card
                    KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(modifier = Modifier.padding(spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(KbBrandSaffron.copy(alpha = 0.1f), CircleShape)
                                        .border(1.dp, KbBrandSaffron.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Edit, null, tint = KbBrandSaffron, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(spacing.medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Manual Setup", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Select role (customer/kitchen) and configure details manually", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            HorizontalDivider(color = KbBrandSaffron.copy(alpha = 0.05f), modifier = Modifier.padding(horizontal = 20.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                SmartAIOption(
                                    icon = Icons.Default.Add,
                                    label = "Add",
                                    onClick = {
                                        pendingAction = "add"
                                        configMode = "manual"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SmartAIOption(
                                    icon = Icons.Default.Visibility,
                                    label = "View",
                                    onClick = {
                                        pendingAction = "view"
                                        configMode = "manual"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SmartAIOption(
                                    icon = Icons.Default.Edit,
                                    label = "Edit",
                                    onClick = {
                                        pendingAction = "edit"
                                        configMode = "manual"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Auto Scan Discovery Card
                    KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(spacing.medium)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(KbBrandVioletLight.copy(alpha = 0.1f), CircleShape)
                                        .border(1.5.dp, KbBrandVioletLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bluetooth, null, tint = KbBrandVioletLight, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(spacing.medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Auto Pair & Connect", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = KbBrandVioletLight, shape = RoundedCornerShape(4.dp)) {
                                            Text("PAIR", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                        }
                                    }
                                    Text("Scan for all active bluetooth receipt printers around", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            Button(
                                onClick = {
                                    pendingRole = PrinterRole.CUSTOMER
                                    openPrinterScan()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletLight)
                            ) {
                                Text("Auto Scan Printers", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                if (activeCategory == null) {
                    // SCREEN 2: LEVEL 1 - CATEGORY LIST
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.kbBgPrimary)
                    ) {
                        Text(
                            text = "PRINTER CHANNELS",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )

                        // 1. Customer Receipts Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "customer" },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                            border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFEFF6FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Receipt, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Customer Receipts", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = customerPrinter?.name ?: "No printer connected",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 2. Kitchen KDS Orders Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "kitchen" },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                            border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFFFF7ED), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.RestaurantMenu, null, tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Kitchen Orders (KDS)", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = kitchenPrinter?.name ?: "No kitchen printer set",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 3. Printing Preferences Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "prefs" },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                            border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFEFF6FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Print, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Print Preferences", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "Auto-print, print logo & phone masking settings",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }
                    }
                } else {
                    // SCREEN 3: LEVEL 2 - ITEM DETAILS LIST
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.kbBgPrimary)
                            .padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Text(
                            text = "${activeCategory?.uppercase()} HARDWARE",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        when (activeCategory) {
                            "customer" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Customer Receipt Printer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Status: ${if (enabled) "Enabled" else "Disabled"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Hardware Name: ${customerPrinter?.name ?: "None"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                if (customerPrinter != null) {
                                                    Text("Paper size: ${if (paper58) "58mm" else "80mm"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                            IconButton(onClick = {
                                                pendingRole = PrinterRole.CUSTOMER
                                                openPrinterScan()
                                            }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                        if (customerPrinter != null) {
                                            Spacer(modifier = Modifier.height(spacing.medium))
                                            Button(
                                                onClick = { viewModel.testPrint(PrinterRole.CUSTOMER) },
                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron)
                                            ) {
                                                Text("Print Test Receipt", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            "kitchen" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Kitchen Order Printer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Status: ${if (kitchenEnabled) "Enabled" else "Disabled"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Hardware Name: ${kitchenPrinter?.name ?: "None"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                if (kitchenPrinter != null) {
                                                    Text("Paper size: ${if (kitchenPaper58) "58mm" else "80mm"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                            IconButton(onClick = {
                                                pendingRole = PrinterRole.KITCHEN
                                                openPrinterScan()
                                            }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                        if (kitchenPrinter != null) {
                                            Spacer(modifier = Modifier.height(spacing.medium))
                                            Button(
                                                onClick = { viewModel.testPrint(PrinterRole.KITCHEN) },
                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron)
                                            ) {
                                                Text("Print Kitchen Test Page", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            "prefs" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Preferences & Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Auto Print Receipts: ${if (autoPrint) "Active" else "Disabled"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Include Shop Logo: ${if (includeLogo) "Active" else "Disabled"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Mask Customer Phone: ${if (maskPhone) "Active" else "Disabled"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showPrefsDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Actions (Save / Back)
                        Spacer(modifier = Modifier.weight(1f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
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
                                    toastScope.launch { KhanaToast.show("Printer configurations saved", ToastKind.Success) }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Save Configuration", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                            OutlinedButton(
                                onClick = { onBack() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.dp, MaterialTheme.kbSecondary.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.kbSecondary),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Back", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }

        // ── Primary Dialog: Bluetooth Scan Dialog ──
        if (showScanDialog) {
            KhanaBookDialog(
                onDismissRequest = {
                    viewModel.stopBluetoothScan()
                    showScanDialog = false
                },
                title = "Pair Bluetooth Printer",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Scanning for active bluetooth printers around...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.kbTextSecondary
                        )
                        if (btIsScanning) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = KbBrandSaffron)
                        }
                        
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            if (btDevices.isEmpty() && !btIsScanning) {
                                item {
                                    Text("No printers found. Tap Scan to search again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kbTextTertiary)
                                }
                            } else {
                                items(btDevices) { device ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedDeviceForConfig = device
                                                viewModel.connectToPrinter(
                                                    context = context,
                                                    device = device,
                                                    role = pendingRole,
                                                    paperSize = if (pendingRole == PrinterRole.CUSTOMER) (if (paper58) "58mm" else "80mm") else (if (kitchenPaper58) "58mm" else "80mm"),
                                                    includeLogo = if (pendingRole == PrinterRole.CUSTOMER) includeLogo else false
                                                )
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kbBgCard),
                                        border = BorderStroke(0.5.dp, MaterialTheme.kbOutlineSubtle)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Bluetooth, null, tint = KbBrandSaffron)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(device.name ?: "Unknown Printer", color = MaterialTheme.kbTextPrimary, fontWeight = FontWeight.Bold)
                                                Text(device.address, color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.stopBluetoothScan()
                            showScanDialog = false
                        }
                    ) {
                        Text("Cancel", color = MaterialTheme.kbTextSecondary)
                    }
                    TextButton(onClick = { viewModel.startBluetoothScan(context) }) {
                        Text("Scan Again", color = KbBrandSaffron)
                    }
                }
            )
        }

        // ── Secondary Dialog: Printer Details Configuration ──
        if (showConfigureDetailsDialog) {
            var tempEnabled by remember { mutableStateOf(if (pendingRole == PrinterRole.CUSTOMER) enabled else kitchenEnabled) }
            var tempPaper58 by remember { mutableStateOf(if (pendingRole == PrinterRole.CUSTOMER) paper58 else kitchenPaper58) }
            
            KhanaBookDialog(
                onDismissRequest = { showConfigureDetailsDialog = false },
                title = "Printer Alignment Preferences",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Set printing details for paired device: ${selectedDeviceForConfig?.name ?: "Connected Printer"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.kbTextSecondary
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Activate Printer for POS", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = tempEnabled, onCheckedChange = { tempEnabled = it }, checkedTrackColor = KbSuccess)
                        }
                        
                        Text("Paper Width", fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { tempPaper58 = true },
                                border = if (tempPaper58) BorderStroke(1.5.dp, KbBrandSaffron) else null
                            ) {
                                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("58mm Receipt", fontWeight = if (tempPaper58) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { tempPaper58 = false },
                                border = if (!tempPaper58) BorderStroke(1.5.dp, KbBrandSaffron) else null
                            ) {
                                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text("80mm Receipt", fontWeight = if (!tempPaper58) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { viewModel.testPrint(pendingRole) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                        ) {
                            Text("Print Test Alignment", color = Color.White)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showConfigureDetailsDialog = false }) { Text("Dismiss", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            if (pendingRole == PrinterRole.CUSTOMER) {
                                enabled = tempEnabled
                                paper58 = tempPaper58
                            } else {
                                kitchenEnabled = tempEnabled
                                kitchenPaper58 = tempPaper58
                            }
                            showConfigureDetailsDialog = false
                        }
                    ) {
                        Text("Apply Preferences", color = KbBrandSaffron)
                    }
                }
            )
        }

        // ── Primary Dialog: Printing Preferences Edit ──
        if (showPrefsDialog) {
            var tempAuto by remember { mutableStateOf(autoPrint) }
            var tempLogo by remember { mutableStateOf(includeLogo) }
            var tempMask by remember { mutableStateOf(maskPhone) }
            
            KhanaBookDialog(
                onDismissRequest = { showPrefsDialog = false },
                title = "Print Settings & Prefs",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Print on Success", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = tempAuto, onCheckedChange = { tempAuto = it }, checkedTrackColor = KbSuccess)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Include Shop Logo", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = tempLogo, onCheckedChange = { tempLogo = it }, checkedTrackColor = KbSuccess)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Mask Customer Phone No", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = tempMask, onCheckedChange = { tempMask = it }, checkedTrackColor = KbSuccess)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showPrefsDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            autoPrint = tempAuto
                            includeLogo = tempLogo
                            maskPhone = tempMask
                            showPrefsDialog = false
                        }
                    ) { Text("Apply", color = KbBrandSaffron) }
                }
            )
        }

        // Processing Overlay
        KhanaBookLoadingOverlay(
            visible = btIsConnecting,
            type = LoadingType.PROCESSING,
            message = "Connecting to printer hardware...",
            subtitle = "Configuring bluetooth serial link..."
        )
    }
}
