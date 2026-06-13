package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*

data class LookupUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val result: LookupResult? = null
)

data class LookupResult(
    val businessName: String?,
    val address: String?,
    val fssaiNo: String?,
    val gstin: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    lookupState: LookupUiState = LookupUiState(),
    onLookupGst: (String) -> Unit = {},
    onLookupFssai: (String) -> Unit = {},
    onLookupBoth: (String, String) -> Unit = { _, _ -> },
    onApplyLookup: (LookupResult) -> Unit = {},
    onClearLookup: () -> Unit = {}
) {
    val spacing = KhanaBookTheme.spacing

    // Mode selection and drill down states
    var configMode by remember { mutableStateOf<String?>(null) } // null = ModeSelection, "manual" = drill down
    var pendingAction by remember { mutableStateOf<String?>(null) } // "add", "view", "edit"
    var activeCategory by remember { mutableStateOf<String?>(null) } // null = Category List, "compliance" = FSSAI, "gst" = GST Slabs

    // Dialog trigger states
    var showFssaiDialog by remember { mutableStateOf(false) }
    var showGstDialog by remember { mutableStateOf(false) }
    var showSplitsDialog by remember { mutableStateOf(false) }

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
            activeCategory = "gst"
            showGstDialog = true
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
                            activeCategory == "compliance" -> "Licenses & Compliance"
                            activeCategory == "gst" -> "Tax Slabs & GST"
                            configMode == "manual" -> "Tax Settings"
                            else -> "Tax Configuration"
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
                                if (activeCategory == "compliance") {
                                    showFssaiDialog = true
                                } else {
                                    showGstDialog = true
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
                                Text("GST Status", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (profile?.gstEnabled == true) "Active" else "Inactive",
                                    color = if (profile?.gstEnabled == true) KbSuccess else MaterialTheme.kbTextSecondary,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        KhanaBookGlassCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Base Tax Rate", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${profile?.gstPercentage?.toInt() ?: 0}%",
                                    color = KbBrandSaffron,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.small))
                    Text("Choose Configuration Mode", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    // Manual Configuration Card
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
                                    Text("Manual Configuration", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Enter compliance & tax slabs one by one", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
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

                    // Automated Fetch Card
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
                                    Icon(Icons.Default.AutoAwesome, null, tint = KbBrandVioletLight, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(spacing.medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Automated API Fetch", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = KbBrandVioletLight, shape = RoundedCornerShape(4.dp)) {
                                            Text("QUICK", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                        }
                                    }
                                    Text("Verify GSTIN & FSSAI instantly via Government records", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            // Direct verification fields inside automated flow
                            var tempGst by remember { mutableStateOf(profile?.gstin ?: "") }
                            var tempFssai by remember { mutableStateOf(profile?.fssaiNumber ?: "") }
                            
                            ParchmentTextField(
                                value = tempGst,
                                onValueChange = { tempGst = it.uppercase() },
                                label = "Enter GSTIN to auto-fetch"
                            )
                            Spacer(modifier = Modifier.height(spacing.small))
                            ParchmentTextField(
                                value = tempFssai,
                                onValueChange = { tempFssai = it.filter(Char::isDigit).take(14) },
                                label = "Enter FSSAI to auto-fetch",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.small)
                            ) {
                                Button(
                                    onClick = { onLookupBoth(tempGst, tempFssai) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletLight)
                                ) {
                                    Text("Fetch Both", color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (tempGst.isNotEmpty()) onLookupGst(tempGst)
                                        else if (tempFssai.isNotEmpty()) onLookupFssai(tempFssai)
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    border = BorderStroke(1.dp, KbBrandVioletLight)
                                ) {
                                    Text("Fetch Single", color = KbBrandVioletLight)
                                }
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
                            text = "COMPLIANCE CATEGORIES",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )

                        // 1. Compliance Category Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "compliance" },
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
                                    Icon(Icons.Default.ReceiptLong, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Licenses & Compliance", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = if (profile?.fssaiNumber.isNullOrEmpty()) "FSSAI not configured" else "FSSAI Configured",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 2. Tax Slabs Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "gst" },
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
                                    Icon(Icons.Default.Percent, null, tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tax Slabs & GST", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = if (profile?.gstEnabled == true) "GST Enabled (${profile.gstPercentage}%)" else "GST Disabled",
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
                        if (activeCategory == "compliance") {
                            Text("LICENSE DETAILS", color = KbBrandVioletBright, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            
                            KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(spacing.medium)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("FSSAI License", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (profile?.fssaiNumber.isNullOrEmpty()) "Not Set" else profile?.fssaiNumber ?: "",
                                                color = MaterialTheme.kbTextSecondary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        IconButton(onClick = { showFssaiDialog = true }) {
                                            Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("ACTIVE TAX SLABS", color = KbBrandVioletBright, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(spacing.medium)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (profile?.gstEnabled == true) "GST Slabs (Enabled)" else "GST Slabs (Disabled)",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.kbTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Standard Base Tax: ${profile?.gstPercentage?.toInt() ?: 0}%",
                                                color = MaterialTheme.kbTextSecondary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (profile?.gstEnabled == true && !profile.gstin.isNullOrEmpty()) {
                                                Text(
                                                    text = "GSTIN: ${profile.gstin}",
                                                    color = MaterialTheme.kbTextTertiary,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        IconButton(onClick = { showGstDialog = true }) {
                                            Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Primary Dialog: FSSAI Edit Dialog ──
        if (showFssaiDialog) {
            var fssaiVal by remember { mutableStateOf(profile?.fssaiNumber ?: "") }
            
            KhanaBookDialog(
                onDismissRequest = { showFssaiDialog = false },
                title = "Edit FSSAI Compliance",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParchmentTextField(
                            value = fssaiVal,
                            onValueChange = { fssaiVal = it.filter(Char::isDigit).take(14) },
                            label = "FSSAI License (14 digits) *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Action to call Secondary Dialog/Fetch
                        Button(
                            onClick = { onLookupFssai(fssaiVal) },
                            enabled = fssaiVal.length == 14,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify License Details", color = Color.White)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showFssaiDialog = false }) {
                        Text("Cancel", color = MaterialTheme.kbTextSecondary)
                    }
                    TextButton(
                        onClick = {
                            profile?.copy(
                                fssaiNumber = fssaiVal,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )?.let { onSave(it) }
                            showFssaiDialog = false
                        },
                        enabled = fssaiVal.isEmpty() || fssaiVal.length == 14
                    ) {
                        Text("Save", color = KbBrandSaffron)
                    }
                }
            )
        }

        // ── Primary Dialog: GST Edit Dialog ──
        if (showGstDialog) {
            var gstReg by remember { mutableStateOf(profile?.gstEnabled ?: false) }
            var gstinVal by remember { mutableStateOf(profile?.gstin ?: "") }
            var gstRateVal by remember { mutableStateOf(profile?.gstPercentage?.toInt()?.toString() ?: "0") }
            
            KhanaBookDialog(
                onDismissRequest = { showGstDialog = false },
                title = "Edit GST & Tax Slabs",
                content = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("GST Registered", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(
                                checked = gstReg,
                                onCheckedChange = { gstReg = it },
                                checkedTrackColor = KbSuccess
                            )
                        }
                        
                        if (gstReg) {
                            ParchmentTextField(
                                value = gstinVal,
                                onValueChange = { gstinVal = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(15) },
                                label = "GSTIN *"
                            )
                            ParchmentTextField(
                                value = gstRateVal,
                                onValueChange = { gstRateVal = it.filter(Char::isDigit).take(3) },
                                label = "GST % *",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Action to call Secondary Dialogs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onLookupGst(gstinVal) },
                                    enabled = gstinVal.length == 15,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    border = BorderStroke(1.dp, KbBrandVioletBright),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Verify GSTIN", color = KbBrandVioletBright)
                                }
                                Button(
                                    onClick = { showSplitsDialog = true },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Define Splits", color = Color.White)
                                }
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showGstDialog = false }) {
                        Text("Cancel", color = MaterialTheme.kbTextSecondary)
                    }
                    TextButton(
                        onClick = {
                            profile?.copy(
                                gstEnabled = gstReg,
                                gstin = gstinVal,
                                gstPercentage = gstRateVal.toDoubleOrNull() ?: 0.0,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )?.let { onSave(it) }
                            showGstDialog = false
                        },
                        enabled = !gstReg || (gstinVal.length == 15 && gstRateVal.isNotEmpty())
                    ) {
                        Text("Save", color = KbBrandSaffron)
                    }
                }
            )
        }

        // ── Secondary Dialog: Tax Splits Dialog ──
        if (showSplitsDialog) {
            KhanaBookDialog(
                onDismissRequest = { showSplitsDialog = false },
                title = "Define Tax Splits",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Configure CGST / SGST split ratio of the slab.",
                            color = MaterialTheme.kbTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        var cgstVal by remember { mutableStateOf("50") }
                        var sgstVal by remember { mutableStateOf("50") }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ParchmentTextField(
                                value = cgstVal,
                                onValueChange = { cgstVal = it.filter(Char::isDigit) },
                                label = "CGST Ratio (%)",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            ParchmentTextField(
                                value = sgstVal,
                                onValueChange = { sgstVal = it.filter(Char::isDigit) },
                                label = "SGST Ratio (%)",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showSplitsDialog = false }) {
                        Text("Dismiss", color = MaterialTheme.kbTextSecondary)
                    }
                    TextButton(onClick = { showSplitsDialog = false }) {
                        Text("Apply Splits", color = KbBrandSaffron)
                    }
                }
            )
        }

        // ── Secondary Dialog: Auto Fetch Results (Government Registry) ──
        lookupState.result?.let { result ->
            AlertDialog(
                onDismissRequest = onClearLookup,
                title = { Text("Government Record Found", color = MaterialTheme.kbSecondary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.businessName?.let {
                            Text("Business Name: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kbTextPrimary)
                        }
                        result.address?.let {
                            Text("Registered Address: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kbTextSecondary)
                        }
                        result.gstin?.let {
                            Text("GSTIN: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kbTextSecondary)
                        }
                        result.fssaiNo?.let {
                            Text("FSSAI No: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kbTextSecondary)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onApplyLookup(result)
                            onClearLookup()
                            showGstDialog = false
                            showFssaiDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KbSuccess)
                    ) { Text("Apply Records", color = Color.White) }
                },
                dismissButton = {
                    OutlinedButton(onClick = onClearLookup) { Text("Dismiss") }
                }
            )
        }

        // Processing Overlay
        KhanaBookLoadingOverlay(
            visible = lookupState.loading,
            type = LoadingType.PROCESSING,
            message = "Querying government databases...",
            subtitle = "Checking GSTIN & FSSAI..."
        )
    }
}
