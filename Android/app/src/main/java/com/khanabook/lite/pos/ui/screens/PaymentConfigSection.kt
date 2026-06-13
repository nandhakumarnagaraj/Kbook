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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storefront
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
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.*
import com.khanabook.lite.pos.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentConfigView(
    profile: RestaurantProfileEntity?,
    onSave: (RestaurantProfileEntity) -> Unit,
    onBack: () -> Unit,
    onOpenEasebuzzKyc: () -> Unit = {},
    onOpenMarketplaceOrders: () -> Unit = {}
) {
    val spacing = KhanaBookTheme.spacing
    val context = LocalContext.current
    val toastScope = rememberCoroutineScope()

    // Mode selection and drill down states
    var configMode by remember { mutableStateOf<String?>(null) } // null = ModeSelection, "manual" = drill down
    var pendingAction by remember { mutableStateOf<String?>(null) } // "add", "view", "edit"
    var activeCategory by remember { mutableStateOf<String?>(null) } // null = Category List, "store" = Cash/POS, "qr" = UPI, "settle" = Easebuzz, "aggregators" = Delivery

    // Dialog trigger states
    var showStoreDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showSettleDialog by remember { mutableStateOf(false) }
    var showAggregatorsDialog by remember { mutableStateOf(false) }

    // Nested Child Dialog trigger states
    var showQrCustomDialog by remember { mutableStateOf(false) }
    var showSplitLabelsDialog by remember { mutableStateOf(false) }
    var showAggregatorSplitsDialog by remember { mutableStateOf(false) }

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
            activeCategory = "qr"
            showQrDialog = true
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
                            activeCategory == "store" -> "Store Checkout"
                            activeCategory == "qr" -> "Digital QR Pay"
                            activeCategory == "settle" -> "Online Settlements"
                            activeCategory == "aggregators" -> "Aggregator Portals"
                            configMode == "manual" -> "Payment Configuration"
                            else -> "Payment Settings"
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
                                when (activeCategory) {
                                    "store" -> showStoreDialog = true
                                    "qr" -> showQrDialog = true
                                    "settle" -> showSettleDialog = true
                                    else -> showAggregatorsDialog = true
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
                                Text("Active Methods", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                val activeCount = listOf(
                                    profile?.cashEnabled ?: true,
                                    profile?.posEnabled ?: false,
                                    profile?.upiEnabled ?: false,
                                    profile?.easebuzzEnabled ?: false
                                ).count { it }
                                Text("$activeCount Methods", color = KbBrandSaffron, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            }
                        }
                        KhanaBookGlassCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Currency", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = profile?.currency ?: "INR",
                                    color = KbBrandSaffron,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.small))
                    Text("Select Payment Flow", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    // Manual Setup Card
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
                                    Text("Configure terminal options & payment integrations manually", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
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

                    // Auto Sync Card
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
                                    Icon(Icons.Default.CloudSync, null, tint = KbBrandVioletLight, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(spacing.medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Cloud Dashboard Sync", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = KbBrandVioletLight, shape = RoundedCornerShape(4.dp)) {
                                            Text("AUTO", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                        }
                                    }
                                    Text("Pull payout accounts & credentials from central admin portal", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            Button(
                                onClick = {
                                    toastScope.launch {
                                        KhanaToast.show("Syncing cloud payment profiles...", ToastKind.Success)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletLight)
                            ) {
                                Text("Sync Settings Now", color = Color.White)
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
                            text = "PAYMENT CATEGORIES",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )

                        // 1. Store Checkout Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "store" },
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
                                    Icon(Icons.Default.Storefront, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Store Checkout", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "Cash & POS Terminal configurations",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 2. Digital QR Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "qr" },
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
                                    Icon(Icons.Default.QrCode, null, tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Digital QR Pay", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = if (profile?.upiEnabled == true) "UPI QR Active" else "UPI QR Disabled",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 3. Online Settlement Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "settle" },
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
                                        .background(Color(0xFFECFDF5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Security, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Online Settlements", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "Easebuzz API settings & KYC verification",
                                        color = MaterialTheme.kbTextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 4. Aggregators Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "aggregators" },
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
                                        .background(Color(0xFFF5F3FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.LocalShipping, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Aggregator Portals", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = "Zomato, Swiggy & Website triggers",
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
                            text = "${activeCategory?.uppercase()} OPTIONS",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        when (activeCategory) {
                            "store" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Store Checkouts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Cash Mode: ${if (profile?.cashEnabled != false) "ON" else "OFF"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("POS Terminal: ${if (profile?.posEnabled == true) "ON" else "OFF"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showStoreDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                    }
                                }
                            }
                            "qr" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("UPI QR Checkout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Status: ${if (profile?.upiEnabled == true) "Enabled" else "Disabled"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                if (profile?.upiEnabled == true && !profile.upiHandle.isNullOrEmpty()) {
                                                    Text("VPA Address: ${profile.upiHandle}", color = MaterialTheme.kbTextTertiary, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                            IconButton(onClick = { showQrDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                    }
                                }
                            }
                            "settle" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Easebuzz Settlement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Onboarding Enabled: ${if (profile?.easebuzzEnabled == true) "YES" else "NO"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showSettleDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                        if (profile?.easebuzzEnabled == true) {
                                            Spacer(modifier = Modifier.height(spacing.medium))
                                            Button(
                                                onClick = onOpenEasebuzzKyc,
                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron)
                                            ) {
                                                Icon(Icons.Outlined.Security, null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Open KYC Progress", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                            "aggregators" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Delivery Platforms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Own Website: ${if (profile?.ownWebsiteEnabled == true) "YES" else "NO"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Zomato Outlet: ${if (profile?.zomatoEnabled == true) "YES" else "NO"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Swiggy Outlet: ${if (profile?.swiggyEnabled == true) "YES" else "NO"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showAggregatorsDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(spacing.medium))
                                        Button(
                                            onClick = onOpenMarketplaceOrders,
                                            modifier = Modifier.fillMaxWidth().height(44.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.kbSecondary)
                                        ) {
                                            Icon(Icons.Outlined.Storefront, null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Marketplace Dashboard", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Primary Dialog: Store Checkout Config ──
        if (showStoreDialog) {
            var cashVal by remember { mutableStateOf(profile?.cashEnabled ?: true) }
            var posVal by remember { mutableStateOf(profile?.posEnabled ?: false) }
            
            KhanaBookDialog(
                onDismissRequest = { showStoreDialog = false },
                title = "Configure Store Checkout",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Accept Cash Payments", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = cashVal, onCheckedChange = { cashVal = it }, checkedTrackColor = KbSuccess)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Accept POS Terminal", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = posVal, onCheckedChange = { posVal = it }, checkedTrackColor = KbSuccess)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showStoreDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            profile?.copy(
                                cashEnabled = cashVal,
                                posEnabled = posVal,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )?.let { onSave(it) }
                            showStoreDialog = false
                        }
                    ) { Text("Save", color = KbBrandSaffron) }
                }
            )
        }

        // ── Primary Dialog: UPI QR Config ──
        if (showQrDialog) {
            var upiVal by remember { mutableStateOf(profile?.upiEnabled ?: false) }
            var upiHandleVal by remember { mutableStateOf(profile?.upiHandle ?: "") }
            
            KhanaBookDialog(
                onDismissRequest = { showQrDialog = false },
                title = "Configure UPI QR",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable Offline UPI QR", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = upiVal, onCheckedChange = { upiVal = it }, checkedTrackColor = KbSuccess)
                        }
                        if (upiVal) {
                            ParchmentTextField(
                                value = upiHandleVal,
                                onValueChange = { upiHandleVal = it.trim() },
                                label = "Merchant UPI VPA ID (e.g., name@okaxis) *"
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Trigger Secondary Dialog
                            Button(
                                onClick = { showQrCustomDialog = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                            ) {
                                Text("Configure QR Layout", color = Color.White)
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showQrDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            if (upiVal && upiHandleVal.isBlank()) {
                                toastScope.launch { KhanaToast.show("VPA ID is required", ToastKind.Error) }
                                return@TextButton
                            }
                            profile?.copy(
                                upiEnabled = upiVal,
                                upiHandle = upiHandleVal,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )?.let { onSave(it) }
                            showQrDialog = false
                        }
                    ) { Text("Save", color = KbBrandSaffron) }
                }
            )
        }

        // ── Primary Dialog: Online Settlement (Easebuzz) Config ──
        if (showSettleDialog) {
            var ebVal by remember { mutableStateOf(profile?.easebuzzEnabled ?: false) }
            
            KhanaBookDialog(
                onDismissRequest = { showSettleDialog = false },
                title = "Easebuzz Integration",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Enable Easebuzz Payouts", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = ebVal, onCheckedChange = { ebVal = it }, checkedTrackColor = KbSuccess)
                        }
                        if (ebVal) {
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Trigger Secondary Dialog
                            Button(
                                onClick = { showSplitLabelsDialog = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                            ) {
                                Text("UPI Settlement splits", color = Color.White)
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showSettleDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            profile?.copy(
                                easebuzzEnabled = ebVal,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )?.let { onSave(it) }
                            showSettleDialog = false
                        }
                    ) { Text("Save", color = KbBrandSaffron) }
                }
            )
        }

        // ── Primary Dialog: Marketplace Aggregators ──
        if (showAggregatorsDialog) {
            var websiteVal by remember { mutableStateOf(profile?.ownWebsiteEnabled ?: false) }
            var zomatoVal by remember { mutableStateOf(profile?.zomatoEnabled ?: false) }
            var swiggyVal by remember { mutableStateOf(profile?.swiggyEnabled ?: false) }
            
            KhanaBookDialog(
                onDismissRequest = { showAggregatorsDialog = false },
                title = "Aggregator Integrations",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Own Website", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = websiteVal, onCheckedChange = { websiteVal = it }, checkedTrackColor = KbSuccess)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Zomato Outlet integration", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = zomatoVal, onCheckedChange = { zomatoVal = it }, checkedTrackColor = KbSuccess)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Swiggy Outlet integration", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = swiggyVal, onCheckedChange = { swiggyVal = it }, checkedTrackColor = KbSuccess)
                        }
                        if (zomatoVal || swiggyVal) {
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Trigger Secondary Dialog
                            Button(
                                onClick = { showAggregatorSplitsDialog = true },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                            ) {
                                Text("Marketplace Split Rates", color = Color.White)
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showAggregatorsDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            profile?.copy(
                                ownWebsiteEnabled = websiteVal,
                                zomatoEnabled = zomatoVal,
                                swiggyEnabled = swiggyVal,
                                isSynced = false,
                                updatedAt = System.currentTimeMillis()
                            )?.let { onSave(it) }
                            showAggregatorsDialog = false
                        }
                    ) { Text("Save", color = KbBrandSaffron) }
                }
            )
        }

        // ── Secondary Dialog: UPI QR Custom Layout ──
        if (showQrCustomDialog) {
            KhanaBookDialog(
                onDismissRequest = { showQrCustomDialog = false },
                title = "QR Code Templates",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select a visual style for the printable checkout QR code card:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kbTextSecondary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(modifier = Modifier.weight(1f).clickable { showQrCustomDialog = false }, border = BorderStroke(2.dp, KbBrandSaffron)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AspectRatio, null, tint = KbBrandSaffron)
                                    Text("Minimal Card", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Card(modifier = Modifier.weight(1f).clickable { showQrCustomDialog = false }) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Style, null, tint = MaterialTheme.kbTextSecondary)
                                    Text("Full Brand Banner", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showQrCustomDialog = false }) { Text("Close", color = MaterialTheme.kbTextSecondary) }
                }
            )
        }

        // ── Secondary Dialog: UPI Settlement Splits (Easebuzz) ──
        if (showSplitLabelsDialog) {
            KhanaBookDialog(
                onDismissRequest = { showSplitLabelsDialog = false },
                title = "Settlement Splits",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Define split labels for automated routing to multiple sub-merchants:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kbTextSecondary)
                        ParchmentTextField(value = "Platform Fee", onValueChange = {}, label = "Primary Sub-merchant Label")
                        ParchmentTextField(value = "2.5%", onValueChange = {}, label = "Split rate (%)")
                    }
                },
                actions = {
                    TextButton(onClick = { showSplitLabelsDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(onClick = { showSplitLabelsDialog = false }) { Text("Apply Splits", color = KbBrandSaffron) }
                }
            )
        }

        // ── Secondary Dialog: Marketplace Split Rates ──
        if (showAggregatorSplitsDialog) {
            KhanaBookDialog(
                onDismissRequest = { showAggregatorSplitsDialog = false },
                title = "Aggregator Deduction Rates",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Simulate payout rates after marketplace deductions:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kbTextSecondary)
                        ParchmentTextField(value = "18%", onValueChange = {}, label = "Zomato Commission Simulation")
                        ParchmentTextField(value = "22%", onValueChange = {}, label = "Swiggy Commission Simulation")
                    }
                },
                actions = {
                    TextButton(onClick = { showAggregatorSplitsDialog = false }) { Text("Close", color = MaterialTheme.kbTextSecondary) }
                }
            )
        }
    }
}
