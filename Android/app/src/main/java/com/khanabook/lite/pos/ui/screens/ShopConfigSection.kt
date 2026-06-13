package com.khanabook.lite.pos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.khanabook.lite.pos.R
import com.khanabook.lite.pos.data.local.entity.RestaurantProfileEntity
import com.khanabook.lite.pos.domain.util.AppAssetStore
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import com.khanabook.lite.pos.domain.util.ValidationUtils
import com.khanabook.lite.pos.ui.components.ParchmentTextField
import com.khanabook.lite.pos.ui.designsystem.*
import androidx.compose.ui.text.input.KeyboardType
import com.khanabook.lite.pos.ui.theme.*
import com.khanabook.lite.pos.ui.viewmodel.AuthViewModel
import com.khanabook.lite.pos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopConfigView(
    profile: RestaurantProfileEntity?,
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val toastScope = rememberCoroutineScope()
    val spacing = KhanaBookTheme.spacing

    // Mode selection and drill down states
    var configMode by remember { mutableStateOf<String?>(null) } // null = ModeSelection, "manual" = drill down
    var pendingAction by remember { mutableStateOf<String?>(null) } // "add", "view", "edit"
    var activeCategory by remember { mutableStateOf<String?>(null) } // null = Category List, "identity", "contact", "compliance", "receipt"

    // Form inputs state
    var name by remember(profile) { mutableStateOf(profile?.shopName ?: "") }
    var address by remember(profile) { mutableStateOf(profile?.shopAddress ?: "") }
    var whatsapp by remember(profile) { mutableStateOf(profile?.whatsappNumber ?: "") }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var consent by remember(profile) { mutableStateOf(profile?.emailInvoiceConsent ?: false) }
    var reviewUrl by remember(profile) { mutableStateOf(profile?.reviewUrl ?: "") }
    var invoiceFooter by remember(profile) { mutableStateOf(profile?.invoiceFooter ?: "") }
    var gstNumber by remember(profile) { mutableStateOf(profile?.gstin ?: "") }
    var fssaiNumber by remember(profile) { mutableStateOf(profile?.fssaiNumber ?: "") }
    var logoUpdateTrigger by remember { mutableStateOf(0L) }

    // Dialog trigger states
    var showIdentityDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showComplianceDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }

    // Nested Child Dialog trigger states
    var showLogoPreviewDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }

    val profileLogoUrl by viewModel.profile.collectAsStateWithLifecycle()
    val logoUrl = profileLogoUrl?.logoUrl
    val logoPath = profileLogoUrl?.logoPath

    val saveProfileLoading by viewModel.saveProfileLoading.collectAsState()
    val saveProfileError by viewModel.saveProfileError.collectAsState()
    val saveProfileSuccess by viewModel.saveProfileSuccess.collectAsState()
    val logoUploadLoading by viewModel.logoUploadLoading.collectAsState()
    val logoUploadError by viewModel.logoUploadError.collectAsState()
    val isUserChecking by viewModel.isUserChecking.collectAsStateWithLifecycle()
    val userExistsError by viewModel.userExistsError.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isGoogleAuth = currentUser?.authProvider.equals("GOOGLE", ignoreCase = true)

    val lookupLoading by viewModel.lookupLoading.collectAsState()
    val lookupError by viewModel.lookupError.collectAsState()
    val lookupResult by viewModel.lookupResult.collectAsState()

    val otpStatus by authViewModel.otpVerificationStatus.collectAsState()
    val otpFieldErrors by authViewModel.otpFieldErrors.collectAsState()
    var otpValue by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var otpTimer by remember { mutableStateOf(120) }
    var isOtpVerified by remember { mutableStateOf(false) }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewModel.uploadLogo(context, it) {
                logoUpdateTrigger = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(otpStatus) {
        when (otpStatus) {
            is AuthViewModel.OtpVerificationResult.OtpSent -> {
                otpSent = true
                isOtpVerified = false
                otpTimer = 120
                showOtpDialog = true
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_otp_sent), ToastKind.Info) }
            }
            is AuthViewModel.OtpVerificationResult.Success -> {
                isOtpVerified = true
                showOtpDialog = false
                toastScope.launch { KhanaToast.show(context.getString(R.string.toast_verified), ToastKind.Success) }
                authViewModel.clearOtpStatus()
            }
            is AuthViewModel.OtpVerificationResult.Error -> {
                val errorMsg = (otpStatus as? AuthViewModel.OtpVerificationResult.Error)?.message.orEmpty()
                isOtpVerified = false
                toastScope.launch {
                    KhanaToast.show(
                        UserMessageSanitizer.sanitizeBackendMessage(errorMsg, "Couldn't verify OTP. Please try again."),
                        ToastKind.Error,
                    )
                }
                authViewModel.clearOtpStatus()
            }
            else -> {}
        }
    }

    LaunchedEffect(otpSent, otpTimer) {
        if (otpSent && otpTimer > 0) {
            kotlinx.coroutines.delay(1000)
            otpTimer--
        }
    }

    val isDirty = remember(name, address, whatsapp, email, consent, reviewUrl, invoiceFooter, gstNumber, fssaiNumber, profile) {
        name != (profile?.shopName ?: "") ||
            address != (profile?.shopAddress ?: "") ||
            whatsapp != (profile?.whatsappNumber ?: "") ||
            email != (profile?.email ?: "") ||
            consent != (profile?.emailInvoiceConsent ?: false) ||
            reviewUrl != (profile?.reviewUrl ?: "") ||
            invoiceFooter != (profile?.invoiceFooter ?: "") ||
            gstNumber != (profile?.gstin ?: "") ||
            fssaiNumber != (profile?.fssaiNumber ?: "")
    }

    var showUnsavedDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty && !saveProfileLoading) {
        showUnsavedDialog = true
    }

    if (showUnsavedDialog) {
        KhanaBookDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = "Unsaved Changes",
            message = "You have unsaved changes. Discard them?"
        ) {
            TextButton(onClick = { showUnsavedDialog = false }) {
                Text("Keep Editing", color = MaterialTheme.kbTertiary, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = { showUnsavedDialog = false; onBack() }) {
                Text("Discard", color = KbError, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    // Lookup result alert (Government API fetch results)
    lookupResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearLookupResult() },
            title = { Text("Government Record Found", color = MaterialTheme.kbPrimary) },
            text = {
                Column {
                    result.businessName?.let { Text("Business Name: $it", style = MaterialTheme.typography.bodyMedium) }
                    result.address?.let { Text("Address: $it", style = MaterialTheme.typography.bodyMedium) }
                    result.gstin?.let { Text("GSTIN: $it", style = MaterialTheme.typography.bodyMedium) }
                    result.fssaiNo?.let { Text("FSSAI No: $it", style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        name = result.businessName ?: name
                        address = result.address ?: address
                        gstNumber = result.gstin ?: gstNumber
                        fssaiNumber = result.fssaiNo ?: fssaiNumber
                        viewModel.clearLookupResult()
                        toastScope.launch { KhanaToast.show(context.getString(R.string.toast_lookup_applied), ToastKind.Success) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KbSuccess)
                ) { Text("Apply", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.clearLookupResult() }) { Text("Dismiss") }
            }
        )
    }

    LaunchedEffect(saveProfileError) {
        saveProfileError?.let { error ->
            toastScope.launch {
                KhanaToast.show(
                    UserMessageSanitizer.sanitizeBackendMessage(error, "Couldn't save profile. Please try again."),
                    ToastKind.Error,
                )
            }
            viewModel.clearSaveProfileState()
        }
    }

    LaunchedEffect(logoUploadError) {
        logoUploadError?.let { error ->
            toastScope.launch { KhanaToast.show(error, ToastKind.Error) }
            viewModel.clearLogoUploadState()
        }
    }

    LaunchedEffect(saveProfileSuccess) {
        if (saveProfileSuccess) {
            toastScope.launch { KhanaToast.show(context.getString(R.string.toast_profile_saved), ToastKind.Success) }
            viewModel.clearSaveProfileState()
            authViewModel.clearOtpStatus()
            onBack()
        }
    }

    // Handle "add" action trigger
    LaunchedEffect(pendingAction) {
        if (pendingAction == "add") {
            activeCategory = "identity"
            showIdentityDialog = true
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
                            activeCategory == "identity" -> "Shop Identity"
                            activeCategory == "contact" -> "Contact Info"
                            activeCategory == "compliance" -> "Compliance registry"
                            activeCategory == "receipt" -> "Receipt Settings"
                            configMode == "manual" -> "Shop Settings"
                            else -> "Shop Configuration"
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
                                    "identity" -> showIdentityDialog = true
                                    "contact" -> showContactDialog = true
                                    "compliance" -> showComplianceDialog = true
                                    else -> showReceiptDialog = true
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
                    // Profile Completion Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        KhanaBookGlassCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Profile Status", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                val compPercent = if (name.isNotBlank() && address.isNotBlank() && logoUrl != null) "100%" else "75%"
                                Text(compPercent, color = KbBrandSaffron, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            }
                        }
                        KhanaBookGlassCard(modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Registered Name", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (name.length > 8) name.take(8) + "…" else name.ifEmpty { "KhanaBook" },
                                    color = KbBrandSaffron,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.small))
                    Text("Select Shop Setup Flow", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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
                                    Text("Edit name, logo, compliance & invoice options manually", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
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

                    // Cloud Sync Card
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
                                        Text("Merchant Sync", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = KbBrandVioletLight, shape = RoundedCornerShape(4.dp)) {
                                            Text("LIVE", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                        }
                                    }
                                    Text("Download registration info from cloud databases instantly", color = MaterialTheme.kbPrimary.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.medium))
                            
                            Button(
                                onClick = {
                                    toastScope.launch { KhanaToast.show("Syncing merchant details...", ToastKind.Success) }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletLight)
                            ) {
                                Text("Sync Merchant Profile", color = Color.White)
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
                            text = "PROFILE CATEGORIES",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )

                        // 1. Shop Identity Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "identity" },
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
                                    Text("Shop Identity", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Shop name, address & business logo", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 2. Contact Details Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "contact" },
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
                                    Icon(Icons.Default.Phone, null, tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Contact Details", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("WhatsApp number, email address & notification consents", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 3. Compliance ID Tile
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
                                        .background(Color(0xFFECFDF5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.ReceiptLong, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Compliance Identifiers", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Verify GSTIN & FSSAI licenses", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.kbTextTertiary)
                            }
                        }

                        // 4. Customizations Tile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { activeCategory = "receipt" },
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
                                    Icon(Icons.Default.Settings, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Billing Customizations", color = MaterialTheme.kbTextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Invoice footers, review links & print presets", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodySmall)
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
                            text = "${activeCategory?.uppercase()} FIELDS",
                            color = KbBrandVioletBright,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        when (activeCategory) {
                            "identity" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Shop Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Name: $name", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Address: $address", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showIdentityDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(spacing.medium))
                                        
                                        // Display current logo with quick preview trigger
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                            val logoModel = logoUrl?.takeIf { it.isNotBlank() }
                                                ?: AppAssetStore.resolveAssetPath(logoPath)
                                            if (!logoModel.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(logoModel)
                                                        .crossfade(true)
                                                        .memoryCacheKey("$logoModel:$logoUpdateTrigger")
                                                        .diskCachePolicy(CachePolicy.ENABLED)
                                                        .build(),
                                                    contentDescription = "Logo",
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .border(1.dp, MaterialTheme.kbOutlineSubtle)
                                                        .clickable { showLogoPreviewDialog = true }
                                                )
                                            }
                                            OutlinedButton(onClick = { logoLauncher.launch("image/*") }) {
                                                Text("Upload Brand Logo")
                                            }
                                        }
                                    }
                                }
                            }
                            "contact" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Contact Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("WhatsApp: $whatsapp", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Email: $email", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Consent: ${if (consent) "Active" else "Inactive"}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showContactDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                    }
                                }
                            }
                            "compliance" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Compliance Keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("GSTIN: ${gstNumber.ifEmpty { "Not Set" }}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("FSSAI Number: ${fssaiNumber.ifEmpty { "Not Set" }}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showComplianceDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                    }
                                }
                            }
                            "receipt" -> {
                                KhanaBookGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(spacing.medium)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Receipt Footers & Invites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.kbTextPrimary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Footer: $invoiceFooter", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                                Text("Review Link: ${reviewUrl.ifEmpty { "Not Set" }}", color = MaterialTheme.kbTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            IconButton(onClick = { showReceiptDialog = true }) {
                                                Icon(Icons.Default.Edit, "Edit", tint = KbBrandSaffron)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Actions (Save / Back)
                        Spacer(modifier = Modifier.weight(1f))
                        val saveButtonScale by animateFloatAsState(
                            targetValue = if (!saveProfileLoading) 1f else 0.97f,
                            animationSpec = tween(durationMillis = 250),
                            label = "save_scale"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Button(
                                onClick = {
                                    if (whatsapp != (profile?.whatsappNumber ?: "") && !isOtpVerified) {
                                        toastScope.launch {
                                            KhanaToast.show(context.getString(R.string.toast_verify_new_whatsapp), ToastKind.Warning)
                                        }
                                    } else {
                                        profile?.copy(
                                            shopName = name,
                                            shopAddress = address,
                                            whatsappNumber = whatsapp,
                                            email = email,
                                            logoPath = logoPath,
                                            logoUrl = logoUrl,
                                            emailInvoiceConsent = consent,
                                            reviewUrl = reviewUrl,
                                            invoiceFooter = invoiceFooter,
                                            gstin = gstNumber,
                                            fssaiNumber = fssaiNumber,
                                            isSynced = false,
                                            updatedAt = System.currentTimeMillis()
                                        )?.let { viewModel.saveProfile(it) }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .graphicsLayer {
                                        scaleX = saveButtonScale
                                        scaleY = saveButtonScale
                                    },
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandSaffron),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !saveProfileLoading
                            ) {
                                if (saveProfileLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("Save Changes", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                }
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

        // ── Primary Dialog: Shop Identity Edit ──
        if (showIdentityDialog) {
            var tempName by remember { mutableStateOf(name) }
            var tempAddress by remember { mutableStateOf(address) }
            
            KhanaBookDialog(
                onDismissRequest = { showIdentityDialog = false },
                title = "Edit Identity Details",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParchmentTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = "Shop Name *"
                        )
                        ParchmentTextField(
                            value = tempAddress,
                            onValueChange = { tempAddress = it },
                            label = "Shop Address *",
                            singleLine = false
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showIdentityDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            name = tempName
                            address = tempAddress
                            showIdentityDialog = false
                        },
                        enabled = tempName.isNotBlank() && tempAddress.isNotBlank()
                    ) { Text("Apply", color = KbBrandSaffron) }
                }
            )
        }

        // ── Primary Dialog: Contact Details Edit ──
        if (showContactDialog) {
            var tempWhatsapp by remember { mutableStateOf(whatsapp) }
            var tempEmail by remember { mutableStateOf(email) }
            var tempConsent by remember { mutableStateOf(consent) }
            
            KhanaBookDialog(
                onDismissRequest = { showContactDialog = false },
                title = "Edit Contact Information",
                content = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ParchmentTextField(
                            value = tempWhatsapp,
                            onValueChange = { tempWhatsapp = it.filter(Char::isDigit).take(10) },
                            label = "WhatsApp Phone Number *",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        
                        if (isGoogleAuth) {
                            Text("Email: $tempEmail (Bound to Google auth)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kbTextSecondary)
                        } else {
                            ParchmentTextField(
                                value = tempEmail,
                                onValueChange = { tempEmail = it },
                                label = "Email Address"
                            )
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Email Invoice Consent", color = MaterialTheme.kbTextPrimary)
                            KhanaBookSwitch(checked = tempConsent, onCheckedChange = { tempConsent = it }, checkedTrackColor = KbSuccess)
                        }

                        // Verify / Send OTP trigger (opens secondary Dialog)
                        val isPhoneValid = ValidationUtils.isValidPhone(tempWhatsapp)
                        val numChanged = tempWhatsapp != (profile?.whatsappNumber ?: "")
                        if (numChanged && isPhoneValid) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    authViewModel.sendOtp(tempWhatsapp, "update_whatsapp")
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                            ) {
                                Text("Send Verification OTP", color = Color.White)
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showContactDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            whatsapp = tempWhatsapp
                            email = tempEmail
                            consent = tempConsent
                            showContactDialog = false
                        },
                        enabled = tempWhatsapp.length == 10
                    ) { Text("Apply", color = KbBrandSaffron) }
                }
            )
        }

        // ── Primary Dialog: Compliance Identifiers Edit ──
        if (showComplianceDialog) {
            var tempGst by remember { mutableStateOf(gstNumber) }
            var tempFssai by remember { mutableStateOf(fssaiNumber) }
            
            KhanaBookDialog(
                onDismissRequest = { showComplianceDialog = false },
                title = "Compliance Details",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParchmentTextField(
                            value = tempGst,
                            onValueChange = { tempGst = it.uppercase() },
                            label = "GSTIN"
                        )
                        ParchmentTextField(
                            value = tempFssai,
                            onValueChange = { tempFssai = it.filter(Char::isDigit).take(14) },
                            label = "FSSAI license No (14 digits)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        
                        // Verification API trigger
                        if (tempGst.isNotEmpty() || tempFssai.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    if (tempGst.isNotEmpty() && tempFssai.isNotEmpty()) {
                                        viewModel.lookupBoth(tempGst, tempFssai)
                                    } else if (tempGst.isNotEmpty()) {
                                        viewModel.lookupGst(tempGst)
                                    } else {
                                        viewModel.lookupFssai(tempFssai)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = KbBrandVioletBright)
                            ) {
                                Text("Auto-Fetch compliance details", color = Color.White)
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showComplianceDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            gstNumber = tempGst
                            fssaiNumber = tempFssai
                            showComplianceDialog = false
                        }
                    ) { Text("Apply", color = KbBrandSaffron) }
                }
            )
        }

        // ── Primary Dialog: Receipt Settings Edit ──
        if (showReceiptDialog) {
            var tempFooter by remember { mutableStateOf(invoiceFooter) }
            var tempReview by remember { mutableStateOf(reviewUrl) }
            
            KhanaBookDialog(
                onDismissRequest = { showReceiptDialog = false },
                title = "Configure Receipt & Feedback",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParchmentTextField(
                            value = tempFooter,
                            onValueChange = { tempFooter = it },
                            label = "Invoice Footer Message",
                            singleLine = false
                        )
                        ParchmentTextField(
                            value = tempReview,
                            onValueChange = { tempReview = it },
                            label = "Google Review Link"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showReceiptDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                    TextButton(
                        onClick = {
                            invoiceFooter = tempFooter
                            reviewUrl = tempReview
                            showReceiptDialog = false
                        }
                    ) { Text("Apply", color = KbBrandSaffron) }
                }
            )
        }

        // ── Secondary Dialog: Logo Preview ──
        if (showLogoPreviewDialog) {
            val logoModel = logoUrl?.takeIf { it.isNotBlank() } ?: AppAssetStore.resolveAssetPath(logoPath)
            KhanaBookDialog(
                onDismissRequest = { showLogoPreviewDialog = false },
                title = "Logo Preview",
                content = {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        if (!logoModel.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(logoModel)
                                    .crossfade(true)
                                    .memoryCacheKey("$logoModel:$logoUpdateTrigger")
                                    .build(),
                                contentDescription = "Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showLogoPreviewDialog = false }) { Text("Close", color = MaterialTheme.kbTextSecondary) }
                }
            )
        }

        // ── Secondary Dialog: WhatsApp Verification OTP input ──
        if (showOtpDialog) {
            KhanaBookDialog(
                onDismissRequest = { showOtpDialog = false },
                title = "Enter WhatsApp Verification OTP",
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("We have sent a verification code to WhatsApp number $whatsapp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.kbTextSecondary)
                        ParchmentTextField(
                            value = otpValue,
                            onValueChange = {
                                if (it.length <= 6) {
                                    otpValue = it
                                    if (it.length == 6) {
                                        authViewModel.confirmMobileNumberUpdate(whatsapp, it)
                                    }
                                }
                            },
                            label = "6-Digit Verification Code",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (otpTimer > 0) {
                            Text("Resend code in ${otpTimer}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.kbTextSecondary)
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showOtpDialog = false }) { Text("Cancel", color = MaterialTheme.kbTextSecondary) }
                }
            )
        }

        // Processing Overlay
        KhanaBookLoadingOverlay(
            visible = lookupLoading || logoUploadLoading,
            type = LoadingType.PROCESSING,
            message = "Updating profile properties...",
            subtitle = "Please wait..."
        )
    }
}
